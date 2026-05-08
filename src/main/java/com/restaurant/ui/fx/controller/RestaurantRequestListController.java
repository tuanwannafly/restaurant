package com.restaurant.ui.fx.controller;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.restaurant.dao.RestaurantRequestDAO;
import com.restaurant.model.RestaurantRequest;
import com.restaurant.model.RestaurantRequest.RequestStatus;
import com.restaurant.websocket.RestaurantEventClient;
import com.restaurant.websocket.WsTopic;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Controller cho RestaurantRequestListView.fxml — Phase 3.
 *
 * <p>Chỉ SUPER_ADMIN mới nhìn thấy màn hình này.
 *
 * <p>Luồng:
 * <ol>
 *   <li>{@link #initialize()} → setup UI + {@link #loadData()}</li>
 *   <li>{@link #loadData()} → background Task → {@link RestaurantRequestDAO#findAll()}</li>
 *   <li>Filter + Search phía client (không round-trip DB)</li>
 *   <li>Pagination 10 items/trang — giống {@code RestaurantController}</li>
 *   <li>Refresh định kỳ 10 s qua {@link PollManagerFx} key {@code "request_list_refresh"}</li>
 *   <li>Nút "Xem chi tiết" → callback {@code onOpenDetail} → MainController navigate</li>
 * </ol>
 */
public class RestaurantRequestListController {

    // ── Formatter ─────────────────────────────────────────────────────────────
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ── FXML fields ───────────────────────────────────────────────────────────

    @FXML private TextField                         txtSearch;
    @FXML private Button                            btnSearch;
    @FXML private Button                            btnFilter;
    @FXML private Button                            btnRefresh;
    @FXML private ComboBox<String>                  cboStatus;

    @FXML private TableView<RestaurantRequest>              tableView;
    @FXML private TableColumn<RestaurantRequest, Long>      colId;
    @FXML private TableColumn<RestaurantRequest, String>    colRestaurant;
    @FXML private TableColumn<RestaurantRequest, String>    colOwner;
    @FXML private TableColumn<RestaurantRequest, String>    colEmail;
    @FXML private TableColumn<RestaurantRequest, String>    colDate;
    @FXML private TableColumn<RestaurantRequest, String>    colStatus;
    @FXML private TableColumn<RestaurantRequest, Void>      colAction;

    @FXML private StackPane tableArea;
    @FXML private VBox      emptyState;
    @FXML private Label     emptyHint;
    @FXML private HBox      paginationBox;
    @FXML private Label     lblTotal;
    @FXML private Label     lblPendingBadge;

    // ── DAO & data ────────────────────────────────────────────────────────────

    private final RestaurantRequestDAO dao = new RestaurantRequestDAO();

    private List<RestaurantRequest> allItems       = new ArrayList<>();
    private List<RestaurantRequest> displayedItems = new ArrayList<>();

    // ── Pagination ────────────────────────────────────────────────────────────

    private static final int PAGE_SIZE  = 10;
    private int currentPage = 1;
    private int totalPages  = 1;

    // ── Navigation callback (set từ MainController) ───────────────────────────

    /** Gọi khi user nhấn "Xem chi tiết" — caller chịu trách nhiệm populate + navigate. */
    private java.util.function.Consumer<RestaurantRequest> onOpenDetail;

    public void setOnOpenDetail(java.util.function.Consumer<RestaurantRequest> cb) {
        this.onOpenDetail = cb;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupComboBox();
        setupColumns();
        loadData();

        // Thay PollManagerFx 10 s bằng WebSocket push
        RestaurantEventClient ws = RestaurantEventClient.getInstance();
        ws.subscribe(WsTopic.REQUEST_LIST);
        ws.onEvent(event -> {
            if (event != null && WsTopic.REQUEST_LIST.equals(event.getTopic())) loadData();
        });
    }

    private void setupComboBox() {
        cboStatus.setItems(FXCollections.observableArrayList(
                "Tất cả", "Chờ duyệt", "Đã duyệt", "Từ chối"));
        cboStatus.getSelectionModel().selectFirst();
    }

    // ── Columns ────────────────────────────────────────────────────────────────

    private void setupColumns() {
        // ID — căn giữa
        colId.setCellValueFactory(cd ->
            new SimpleLongProperty(cd.getValue().getRequestId()).asObject());
        colId.setCellFactory(col -> centered(new TableCell<>() {
            @Override protected void updateItem(Long v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.valueOf(v));
            }
        }));

        // Tên nhà hàng
        colRestaurant.setCellValueFactory(cd ->
            new SimpleStringProperty(safe(cd.getValue().getRestaurantName())));

        // Chủ sở hữu
        colOwner.setCellValueFactory(cd ->
            new SimpleStringProperty(safe(cd.getValue().getOwnerName())));

        // Email chủ
        colEmail.setCellValueFactory(cd ->
            new SimpleStringProperty(safe(cd.getValue().getOwnerEmail())));

        // Ngày nộp
        colDate.setCellValueFactory(cd -> {
            var t = cd.getValue().getSubmittedAt();
            return new SimpleStringProperty(t != null ? t.format(DATE_FMT) : "—");
        });
        colDate.setCellFactory(col -> centered(new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty ? null : v);
            }
        }));

        // Trạng thái — badge pill
        colStatus.setCellValueFactory(cd ->
            new SimpleStringProperty(
                cd.getValue().getStatus() != null
                    ? cd.getValue().getStatus().name()
                    : "PENDING"));
        colStatus.setCellFactory(col -> new StatusBadgeCell());

        // Hành động — nút "Xem chi tiết"
        colAction.setCellFactory(col -> new ActionCell());
    }

    // ── Load data ──────────────────────────────────────────────────────────────

    /** Tải toàn bộ danh sách từ DB trên background thread. */
    public void loadData() {
        Task<List<RestaurantRequest>> task = new Task<>() {
            @Override protected List<RestaurantRequest> call() {
                return dao.findAll();
            }
        };
        task.setOnSucceeded(e -> {
            allItems       = task.getValue();
            displayedItems = new ArrayList<>(allItems);
            currentPage    = 1;
            updatePendingBadge();
            buildPagination();
            refreshTable();
        });
        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            System.err.println("[RequestListCtrl] loadData lỗi: "
                    + (cause != null ? cause.getMessage() : "unknown"));
        });
        new Thread(task, "RequestListCtrl-load").start();
    }

    // ── Refresh table ─────────────────────────────────────────────────────────

    private void refreshTable() {
        int from = (currentPage - 1) * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, displayedItems.size());
        tableView.setItems(FXCollections.observableArrayList(
                displayedItems.subList(from, to)));

        boolean empty = displayedItems.isEmpty();
        tableView.setVisible(!empty);
        tableView.setManaged(!empty);
        emptyState.setVisible(empty);
        emptyState.setManaged(empty);

        // hint text
        if (emptyHint != null) {
            String q      = txtSearch.getText().trim();
            String status = cboStatus.getSelectionModel().getSelectedItem();
            if (!q.isEmpty() || (status != null && !"Tất cả".equals(status))) {
                emptyHint.setText("Không tìm thấy đơn nào phù hợp với bộ lọc");
            } else {
                emptyHint.setText("Chưa có đơn đăng ký nào được nộp");
            }
        }

        // label tổng số
        if (lblTotal != null) {
            int total = displayedItems.size();
            String range = total == 0 ? "0" : (from + 1) + "–" + to;
            lblTotal.setText("Hiển thị " + range + " / " + total + " đơn đăng ký");
        }
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    @FXML private void handleSearch() { applyFilter(); }
    @FXML private void handleFilter() { applyFilter(); }
    @FXML private void handleRefresh() { loadData(); }

    private void applyFilter() {
        String query     = txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        String statusSel = cboStatus.getSelectionModel().getSelectedItem();

        displayedItems = allItems.stream()
            .filter(r -> {
                if (query.isEmpty()) return true;
                String name  = safe(r.getRestaurantName()).toLowerCase(Locale.ROOT);
                String email = safe(r.getOwnerEmail()).toLowerCase(Locale.ROOT);
                String owner = safe(r.getOwnerName()).toLowerCase(Locale.ROOT);
                return name.contains(query) || email.contains(query) || owner.contains(query);
            })
            .filter(r -> {
                if (statusSel == null || "Tất cả".equals(statusSel)) return true;
                return switch (statusSel) {
                    case "Chờ duyệt" -> r.getStatus() == RequestStatus.PENDING;
                    case "Đã duyệt"  -> r.getStatus() == RequestStatus.APPROVED;
                    case "Từ chối"   -> r.getStatus() == RequestStatus.REJECTED;
                    default          -> true;
                };
            })
            .collect(Collectors.toList());

        currentPage = 1;
        buildPagination();
        refreshTable();
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    private void buildPagination() {
        int total = displayedItems.size();
        totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / PAGE_SIZE);
        paginationBox.getChildren().removeIf(n -> n instanceof Button || n instanceof Label);

        String baseBtn   = "-fx-background-color: white; -fx-text-fill: #374151;"
                         + "-fx-border-color: #D1D5DB; -fx-border-radius: 6;"
                         + "-fx-background-radius: 6; -fx-cursor: hand;";
        String activeBtn = "-fx-background-color: #2563EB; -fx-text-fill: white;"
                         + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;";

        Button btnPrev = new Button("‹");
        btnPrev.setPrefWidth(34); btnPrev.setPrefHeight(32);
        btnPrev.setStyle(baseBtn);
        btnPrev.setDisable(currentPage <= 1);
        btnPrev.setOnAction(e -> { currentPage--; buildPagination(); refreshTable(); });
        paginationBox.getChildren().add(btnPrev);

        int startPage = Math.max(1, currentPage - 2);
        int endPage   = Math.min(totalPages, startPage + 4);
        if (endPage - startPage < 4) startPage = Math.max(1, endPage - 4);

        for (int p = startPage; p <= endPage; p++) {
            final int page = p;
            Button btn = new Button(String.valueOf(p));
            btn.setPrefWidth(36); btn.setPrefHeight(32);
            btn.setStyle(p == currentPage ? activeBtn : baseBtn);
            btn.setOnAction(e -> { currentPage = page; buildPagination(); refreshTable(); });
            paginationBox.getChildren().add(btn);
        }

        Button btnNext = new Button("›");
        btnNext.setPrefWidth(34); btnNext.setPrefHeight(32);
        btnNext.setStyle(baseBtn);
        btnNext.setDisable(currentPage >= totalPages);
        btnNext.setOnAction(e -> { currentPage++; buildPagination(); refreshTable(); });
        paginationBox.getChildren().add(btnNext);
    }

    // ── Pending badge (header) ─────────────────────────────────────────────────

    private void updatePendingBadge() {
        if (lblPendingBadge == null) return;
        long pending = allItems.stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING)
                .count();
        if (pending > 0) {
            lblPendingBadge.setText(pending + " chờ duyệt");
            lblPendingBadge.setVisible(true);
            lblPendingBadge.setManaged(true);
        } else {
            lblPendingBadge.setVisible(false);
            lblPendingBadge.setManaged(false);
        }
    }

    // ── WebSocket cleanup ─────────────────────────────────────────────────────

    /** Huỷ WS handler khi rời màn hình (gọi từ MainController nếu cần). */
    public void cleanup() {
        RestaurantEventClient.getInstance().onEvent(null);
    }

    // ── Inner TableCell classes ────────────────────────────────────────────────

    /** Badge pill màu theo trạng thái đơn đăng ký. */
    private static final class StatusBadgeCell
            extends TableCell<RestaurantRequest, String> {

        private final Label     badge = new Label();
        private final StackPane pill  = new StackPane(badge);

        StatusBadgeCell() {
            badge.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
            pill.setPadding(new Insets(3, 10, 3, 10));
            pill.setMaxWidth(110);
            pill.setAlignment(Pos.CENTER);
            setAlignment(Pos.CENTER);
        }

        @Override protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || value == null) { setGraphic(null); setText(null); return; }

            record Style(String text, String bg, String fg) {}
            Style s = switch (value) {
                case "APPROVED" -> new Style("✓ Đã duyệt",  "#D1FAE5", "#065F46");
                case "REJECTED" -> new Style("✗ Từ chối",   "#FEE2E2", "#991B1B");
                default         -> new Style("⏳ Chờ duyệt", "#FEF3C7", "#D97706");
            };

            badge.setText(s.text());
            badge.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + s.fg() + ";");
            pill.setStyle("-fx-background-color: " + s.bg() + "; -fx-background-radius: 10px;");
            setText(null);
            setGraphic(pill);
        }
    }

    /** Cell chứa nút "Xem chi tiết". */
    private final class ActionCell extends TableCell<RestaurantRequest, Void> {

        private final Button btn = new Button("Xem chi tiết");

        ActionCell() {
            btn.setStyle(
                "-fx-font-size: 12px; -fx-cursor: hand;"
                + "-fx-background-color: #EFF6FF; -fx-text-fill: #2563EB;"
                + "-fx-border-color: #BFDBFE; -fx-border-radius: 6;"
                + "-fx-background-radius: 6; -fx-padding: 4 10 4 10;");
            btn.setOnAction(e -> {
                RestaurantRequest item = getTableView().getItems().get(getIndex());
                if (item != null && onOpenDetail != null) onOpenDetail.accept(item);
            });
            setAlignment(Pos.CENTER);
        }

        @Override protected void updateItem(Void v, boolean empty) {
            super.updateItem(v, empty);
            setGraphic(empty ? null : btn);
            setText(null);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String safe(String s) { return s != null ? s : ""; }

    private static <T> TableCell<RestaurantRequest, T> centered(TableCell<RestaurantRequest, T> cell) {
        cell.setAlignment(Pos.CENTER);
        return cell;
    }
}