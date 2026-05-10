package com.restaurant.ui.fx.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.restaurant.dao.RestaurantDAO;
import com.restaurant.dao.UserDAO;
import com.restaurant.model.Restaurant;
import com.restaurant.model.Restaurant.Status;
import com.restaurant.ui.fx.controller.RestaurantDialogController.AdminChoice;

import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Controller cho RestaurantView.fxml.
 *
 * <p>Chỉ SUPER_ADMIN mới nhìn thấy màn hình này.
 * Mọi thao tác DAO được thực hiện trên background thread (Task)
 * để không block JavaFX Application Thread.
 *
 * <p>Callback {@link #setOnOpenDetail(Consumer)} phải được set từ MainController
 * sau khi load FXML, trước khi hiển thị view.
 */
public class RestaurantController {

    // ── FXML fields ───────────────────────────────────────────────────────────

    @FXML private TextField         txtSearch;
    @FXML private Button            btnSearch;
    @FXML private Button            btnAdd;
    @FXML private ComboBox<String>  cboStatus;
    @FXML private ComboBox<String>  cboSort;
    @FXML private Button            btnFilter;

    @FXML private TableView<Restaurant>              tableView;
    @FXML private TableColumn<Restaurant, Long>      colId;
    @FXML private TableColumn<Restaurant, String>    colName;
    @FXML private TableColumn<Restaurant, String>    colPhone;
    @FXML private TableColumn<Restaurant, String>    colEmail;
    @FXML private TableColumn<Restaurant, String>    colDate;
    @FXML private TableColumn<Restaurant, String>    colStatus;
    @FXML private TableColumn<Restaurant, Void>      colAction;

    @FXML private StackPane tableArea;
    @FXML private VBox      emptyState;
    @FXML private HBox      paginationBox;
    @FXML private Label     lblTotal;

    // ── DAO & data ────────────────────────────────────────────────────────────

    private final RestaurantDAO dao     = new RestaurantDAO();
    private final UserDAO       userDAO = new UserDAO();

    private List<Restaurant> allItems       = new ArrayList<>();
    private List<Restaurant> displayedItems = new ArrayList<>();

    // ── Pagination ────────────────────────────────────────────────────────────

    private static final int PAGE_SIZE   = 10;
    private int currentPage = 1;
    private int totalPages  = 1;

    // ── Navigation callback (set by MainController) ───────────────────────────

    /** Gọi khi user nhấn "Xem chi tiết" — caller chịu trách nhiệm navigate. */
    private Consumer<Restaurant> onOpenDetail;

    public void setOnOpenDetail(Consumer<Restaurant> callback) {
        this.onOpenDetail = callback;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupComboBoxes();
        setupColumns();
        setupRowDoubleClick();
        loadData();
    }

    private void setupComboBoxes() {
        cboStatus.setItems(FXCollections.observableArrayList("Tất cả", "Hoạt động", "Vô hiệu hóa"));
        cboStatus.getSelectionModel().selectFirst();

        cboSort.setItems(FXCollections.observableArrayList("Mới nhất", "Tên A-Z", "Tên Z-A"));
        cboSort.getSelectionModel().selectFirst();
    }

    private void setupColumns() {
        // ID — căn giữa qua cell factory (setStyle trên column chỉ affect header, không affect cell)
        colId.setCellValueFactory(cd ->
            new SimpleLongProperty(cd.getValue().getRestaurantId()).asObject());
        colId.setCellFactory(col -> {
            TableCell<Restaurant, Long> cell = new TableCell<>() {
                @Override protected void updateItem(Long item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : String.valueOf(item));
                }
            };
            cell.setAlignment(Pos.CENTER);
            return cell;
        });

        // Name
        colName.setCellValueFactory(cd ->
            new SimpleStringProperty(safe(cd.getValue().getName())));

        // Phone — căn giữa qua cell factory
        colPhone.setCellValueFactory(cd ->
            new SimpleStringProperty(safe(cd.getValue().getPhone())));
        colPhone.setCellFactory(col -> {
            TableCell<Restaurant, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setAlignment(Pos.CENTER);
            return cell;
        });

        // Email
        colEmail.setCellValueFactory(cd ->
            new SimpleStringProperty(safe(cd.getValue().getEmail())));

        // Ngày tạo — căn giữa qua cell factory
        colDate.setCellValueFactory(cd -> {
            Restaurant r = cd.getValue();
            String d = (r.getCreatedAt() != null)
                    ? r.getCreatedAt().toString().substring(0, 10)
                    : "";
            return new SimpleStringProperty(d);
        });
        colDate.setCellFactory(col -> {
            TableCell<Restaurant, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setAlignment(Pos.CENTER);
            return cell;
        });

        // Trạng thái — colored label
        colStatus.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getStatus() != null
                    ? cd.getValue().getStatus().label() : ""));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setAlignment(Pos.CENTER);
                if ("Hoạt động".equals(item)) {
                    setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                }
            }
        });

        // Hành động — 3 inline buttons
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnDelete = makeActionBtn("Xóa",         "#dc2626");
            private final Button btnEdit   = makeActionBtn("Cập nhật",    "#2563eb");
            private final Button btnDetail = makeActionBtn("Xem chi tiết","#6B7280");
            private final HBox   box       = new HBox(6, btnDelete, btnEdit, btnDetail);
            {
                box.setAlignment(Pos.CENTER);
                btnDelete.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
                btnEdit  .setOnAction(e -> handleEdit  (getTableView().getItems().get(getIndex())));
                btnDetail.setOnAction(e -> handleDetail(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tableView.setFixedCellSize(42);
    }

    /** Nút hành động nhỏ trong cột — outline style theo màu. */
    private Button makeActionBtn(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-font-size: 12px;"
            + "-fx-text-fill: " + color + ";"
            + "-fx-border-color: " + color + ";"
            + "-fx-border-radius: 4;"
            + "-fx-background-color: transparent;"
            + "-fx-background-radius: 4;"
            + "-fx-padding: 3 8 3 8;"
            + "-fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-font-size: 12px;"
            + "-fx-text-fill: white;"
            + "-fx-border-color: " + color + ";"
            + "-fx-border-radius: 4;"
            + "-fx-background-color: " + color + ";"
            + "-fx-background-radius: 4;"
            + "-fx-padding: 3 8 3 8;"
            + "-fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-font-size: 12px;"
            + "-fx-text-fill: " + color + ";"
            + "-fx-border-color: " + color + ";"
            + "-fx-border-radius: 4;"
            + "-fx-background-color: transparent;"
            + "-fx-background-radius: 4;"
            + "-fx-padding: 3 8 3 8;"
            + "-fx-cursor: hand;"));
        return btn;
    }

    private void setupRowDoubleClick() {
        tableView.setRowFactory(tv -> {
            TableRow<Restaurant> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) handleDetail(row.getItem());
            });
            return row;
        });
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    /** Tải toàn bộ danh sách nhà hàng trên background thread. */
    public void loadData() {
        Task<List<Restaurant>> task = new Task<>() {
            @Override protected List<Restaurant> call() { return dao.findAll(); }
        };
        task.setOnSucceeded(e -> {
            allItems       = task.getValue();
            displayedItems = new ArrayList<>(allItems);
            currentPage    = 1;
            buildPagination();
            refreshTable();
        });
        task.setOnFailed(e -> showError("Lỗi tải danh sách nhà hàng:\n"
                + rootMsg(task.getException())));
        new Thread(task, "RestaurantController-load").start();
    }

    private void refreshTable() {
        int from = (currentPage - 1) * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, displayedItems.size());
        List<Restaurant> page = displayedItems.subList(from, to);
        tableView.setItems(FXCollections.observableArrayList(page));

        boolean empty = displayedItems.isEmpty();
        tableView.setVisible(!empty);
        tableView.setManaged(!empty);
        emptyState.setVisible(empty);
        emptyState.setManaged(empty);

        // Cập nhật label tổng số
        if (lblTotal != null) {
            int total = displayedItems.size();
            String range = total == 0 ? "0" : (from + 1) + "–" + to;
            lblTotal.setText("Hiển thị " + range + " / " + total + " nhà hàng");
        }
    }

    // ── Filter / Sort ─────────────────────────────────────────────────────────

    @FXML private void handleSearch() { applyFilter(); }
    @FXML private void handleFilter() { applyFilter(); }

    private void applyFilter() {
        String query     = txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        String statusSel = cboStatus.getSelectionModel().getSelectedItem();
        String sortSel   = cboSort.getSelectionModel().getSelectedItem();

        List<Restaurant> result = allItems.stream()
            .filter(r -> {
                if (query.isEmpty()) return true;
                String name  = r.getName()  != null ? r.getName().toLowerCase(Locale.ROOT)  : "";
                String email = r.getEmail() != null ? r.getEmail().toLowerCase(Locale.ROOT) : "";
                return name.contains(query) || email.contains(query);
            })
            .filter(r -> {
                if ("Hoạt động".equals(statusSel))    return r.getStatus() == Status.ACTIVE;
                if ("Vô hiệu hóa".equals(statusSel)) return r.getStatus() == Status.INACTIVE;
                return true; // "Tất cả"
            })
            .collect(Collectors.toList());

        if ("Tên A-Z".equals(sortSel)) {
            result.sort(Comparator.comparing(r -> safe(r.getName())));
        } else if ("Tên Z-A".equals(sortSel)) {
            result.sort(Comparator.comparing((Restaurant r) -> safe(r.getName())).reversed());
        }
        // "Mới nhất" → giữ thứ tự từ DAO (giảm dần theo ID)

        displayedItems = result;
        currentPage    = 1;
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

        // Prev button
        Button btnPrev = new Button("‹");
        btnPrev.setPrefWidth(34); btnPrev.setPrefHeight(32);
        btnPrev.setStyle(baseBtn);
        btnPrev.setDisable(currentPage <= 1);
        btnPrev.setOnAction(e -> { currentPage--; buildPagination(); refreshTable(); });
        paginationBox.getChildren().add(btnPrev);

        // Page buttons (show up to 5)
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

        // Next button
        Button btnNext = new Button("›");
        btnNext.setPrefWidth(34); btnNext.setPrefHeight(32);
        btnNext.setStyle(baseBtn);
        btnNext.setDisable(currentPage >= totalPages);
        btnNext.setOnAction(e -> { currentPage++; buildPagination(); refreshTable(); });
        paginationBox.getChildren().add(btnNext);
    }

    // ── CRUD operations ───────────────────────────────────────────────────────

    @FXML
    private void handleAdd() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/dialog/RestaurantDialog.fxml"));
            Parent root = loader.load();
            RestaurantDialogController ctrl = loader.getController();
            ctrl.initCreate();

            Stage stage = dialogStage("Thêm nhà hàng", root);
            stage.showAndWait();

            if (!ctrl.isSaved()) return;

            Restaurant saved     = ctrl.getRestaurant();
            AdminChoice choice   = ctrl.getAdminChoice();

            Task<String> task = new Task<>() {
                @Override
                protected String call() throws Exception {
                    dao.add(saved);
                    long rid = saved.getRestaurantId();
                    return switch (choice.mode) {
                        case EXISTING -> {
                            userDAO.assignAdminToRestaurant(choice.existingUserId, rid);
                            yield "Nhà hàng đã được tạo và gán admin thành công!";
                        }
                        case NEW -> {
                            userDAO.registerRestaurantAdmin(
                                choice.newName, choice.newEmail, choice.newPassword, rid);
                            // Gửi email thông báo tài khoản cho admin mới
                            Thread emailThread = new Thread(() -> {
                                try {
                                    com.restaurant.email.EmailService.getInstance()
                                            .sendNewAdminAccountEmail(
                                                    choice.newEmail,
                                                    choice.newName,
                                                    saved.getName(),
                                                    choice.newEmail,
                                                    choice.newPassword);
                                } catch (Exception emailEx) {
                                    System.err.println(
                                            "[RestaurantController] Cảnh báo: gửi email admin mới thất bại: "
                                            + emailEx.getMessage());
                                }
                            });
                            emailThread.setDaemon(true);
                            emailThread.setName("email-new-admin-" + rid);
                            emailThread.start();
                            yield "Nhà hàng đã được tạo!\nTài khoản admin mới: " + choice.newEmail;
                        }
                        case SKIP -> "Nhà hàng đã được tạo. Nhớ gán admin sau!";
                    };
                }
            };
            task.setOnSucceeded(e -> { loadData(); showInfo(task.getValue()); });
            task.setOnFailed(e -> {
                loadData(); // nhà hàng đã tạo, chỉ gán admin lỗi
                showWarn("Nhà hàng đã tạo nhưng gán admin thất bại:\n"
                    + rootMsg(task.getException()) + "\n\nVui lòng gán admin sau.");
            });
            new Thread(task, "RestaurantController-add").start();

        } catch (IOException ex) {
            showError("Lỗi mở dialog thêm nhà hàng: " + ex.getMessage());
        }
    }

    private void handleEdit(Restaurant item) {
        // Fetch fresh data trước khi mở dialog
        Task<Restaurant> fetchTask = new Task<>() {
            @Override protected Restaurant call() { return dao.findById(item.getRestaurantId()); }
        };
        fetchTask.setOnSucceeded(e -> {
            Restaurant fresh = fetchTask.getValue();
            if (fresh == null) {
                showInfo("Không tìm thấy nhà hàng (id=" + item.getRestaurantId() + ")");
                loadData();
                return;
            }
            try {
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/dialog/RestaurantDialog.fxml"));
                Parent root = loader.load();
                RestaurantDialogController ctrl = loader.getController();
                ctrl.initEdit(fresh);

                Stage stage = dialogStage("Cập nhật nhà hàng", root);
                stage.showAndWait();

                if (!ctrl.isSaved()) return;

                Task<Void> saveTask = new Task<>() {
                    @Override protected Void call() { dao.update(ctrl.getRestaurant()); return null; }
                };
                saveTask.setOnSucceeded(ev -> loadData());
                saveTask.setOnFailed(ev -> showError("Lỗi cập nhật nhà hàng:\n"
                    + rootMsg(saveTask.getException())));
                new Thread(saveTask, "RestaurantController-update").start();

            } catch (IOException ex) {
                showError("Lỗi mở dialog sửa nhà hàng: " + ex.getMessage());
            }
        });
        fetchTask.setOnFailed(e -> showError("Lỗi tải nhà hàng: " + rootMsg(fetchTask.getException())));
        new Thread(fetchTask, "RestaurantController-fetch").start();
    }

    private void handleDetail(Restaurant item) {
        if (onOpenDetail != null) onOpenDetail.accept(item);
    }

    /**
     * Xóa nhà hàng — yêu cầu xác nhận bằng dialog.
     *
     * <p>TODO: thay {@code confirmDelete()} bằng
     * {@code ConfirmOperationDialog.show(stage, OperationType.DELETE_RESTAURANT, id)}
     * khi class đó được port sang JavaFX.
     */
    private void handleDelete(Restaurant item) {
        if (!confirmDelete(item)) return;

        Task<Void> task = new Task<>() {
            @Override protected Void call() { dao.delete(item.getRestaurantId()); return null; }
        };
        task.setOnSucceeded(e -> loadData());
        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            if (cause instanceof IllegalStateException) {
                // Còn nhân viên → warning (không phải lỗi)
                showWarn(cause.getMessage());
            } else {
                showError("Lỗi xóa nhà hàng:\n" + rootMsg(cause));
            }
        });
        new Thread(task, "RestaurantController-delete").start();
    }

    /**
     * Dialog xác nhận xóa.
     * Khi ConfirmOperationDialog JavaFX được tạo, thay thế phần này:
     * <pre>
     *   return ConfirmOperationDialog.show(
     *       (Stage) tableView.getScene().getWindow(),
     *       OperationType.DELETE_RESTAURANT,
     *       item.getRestaurantId());
     * </pre>
     */
    private boolean confirmDelete(Restaurant item) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận xóa nhà hàng");
        alert.setHeaderText("Xóa: " + item.getName());
        alert.setContentText("""
                Thao tác này không thể hoàn tác.
                Nhà hàng phải không còn nhân viên nào.

                Bạn có chắc chắn muốn xóa?""");
        alert.initOwner(tableView.getScene().getWindow());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Stage dialogStage(String title, Parent root) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(tableView.getScene().getWindow());
        stage.setScene(new Scene(root));
        stage.setResizable(false);
        return stage;
    }

    private String safe(String s) { return s != null ? s : ""; }

    private String rootMsg(Throwable t) {
        if (t == null) return "";
        Throwable cause = t.getCause();
        return cause != null ? cause.getMessage() : t.getMessage();
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            a.setTitle("Lỗi"); a.showAndWait();
        });
    }

    private void showInfo(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
            a.setTitle("Thành công"); a.showAndWait();
        });
    }

    private void showWarn(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
            a.setTitle("Cảnh báo"); a.showAndWait();
        });
    }
}