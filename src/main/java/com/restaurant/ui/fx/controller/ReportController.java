package com.restaurant.ui.fx.controller;

import com.restaurant.dao.ReportDAO;
import com.restaurant.model.Report;
import com.restaurant.model.Report.Status;
import com.restaurant.session.RbacGuard;
import com.restaurant.ui.dialog.ReportAddController;
import com.restaurant.ui.dialog.ReportDetailController;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller cho ReportView.fxml.
 *
 * <p>Hành vi phân quyền:
 * <ul>
 *   <li>SUPER_ADMIN   → cột "Nhà hàng"; ẩn nút "Gửi báo cáo"; hiện hint double-click</li>
 *   <li>MANAGER+      → cột "Người tạo"; hiện filter bar; thấy mọi báo cáo nhà hàng</li>
 *   <li>STAFF         → ẩn filter bar; ẩn cột extra; chỉ thấy báo cáo của mình</li>
 * </ul>
 */
public class ReportController implements Initializable {

    // ── FXML bindings ──────────────────────────────────────────────────────────
    @FXML private Label             lblTitle;
    @FXML private Button            btnAdd;
    @FXML private HBox              filterBar;
    @FXML private HBox              loadingBar;
    @FXML private Label             lblHint;

    @FXML private ComboBox<String>  cmbFilter;
    @FXML private TextField         searchField;

    @FXML private TableView<Report>              tableView;
    @FXML private TableColumn<Report, Long>      colId;
    @FXML private TableColumn<Report, String>    colTitle;
    @FXML private TableColumn<Report, String>    colType;
    @FXML private TableColumn<Report, String>    colSeverity;
    @FXML private TableColumn<Report, String>    colStatus;
    @FXML private TableColumn<Report, String>    colCreatedAt;
    @FXML private TableColumn<Report, String>    colExtra;

    // ── State ──────────────────────────────────────────────────────────────────
    private final ObservableList<Report> allReports  = FXCollections.observableArrayList();
    private       FilteredList<Report>   filteredList;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final boolean isManager    = RbacGuard.getInstance().isManagerOrAbove();
    private final boolean isSuperAdmin = RbacGuard.getInstance().isSuperAdmin();

    // ═════════════════════════════════════════════════════════════════════════
    // Initializable
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        applyRoleLayout();
        setupColumns();
        setupFilter();
        setupDoubleClick();
        loadData();
    }

    // ── Role-based layout ──────────────────────────────────────────────────────

    private void applyRoleLayout() {
        if (isSuperAdmin) {
            lblTitle.setText("Báo cáo từ các nhà hàng");
            btnAdd.setVisible(false);
            btnAdd.setManaged(false);
            lblHint.setVisible(true);
            lblHint.setManaged(true);
            colExtra.setText("Nhà hàng");
        } else if (isManager) {
            colExtra.setText("Người tạo");
        } else {
            // STAFF: ẩn filter bar và cột extra
            filterBar.setVisible(false);
            filterBar.setManaged(false);
            colExtra.setVisible(false);
            colExtra.setManaged(false);
        }
    }

    // ── Column setup ───────────────────────────────────────────────────────────

    private void setupColumns() {
        // ID
        colId.setCellValueFactory(new PropertyValueFactory<>("reportId"));
        colId.setStyle("-fx-alignment: CENTER;");

        // Tiêu đề
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));

        // Loại báo cáo — dùng display string
        colType.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getReportTypeDisplay()));
        colType.setStyle("-fx-alignment: CENTER;");

        // Mức độ — custom badge cell
        colSeverity.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getSeverityDisplay()));
        colSeverity.setCellFactory(col -> new SeverityBadgeCell());

        // Trạng thái — custom badge cell
        colStatus.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getStatusDisplay()));
        colStatus.setCellFactory(col -> new StatusBadgeCell());

        // Ngày tạo — format LocalDateTime
        colCreatedAt.setCellValueFactory(cell -> {
            var dt = cell.getValue().getCreatedAt();
            String txt = dt != null ? dt.format(FMT) : "";
            return new javafx.beans.property.SimpleStringProperty(txt);
        });
        colCreatedAt.setStyle("-fx-alignment: CENTER;");

        // Cột extra — nội dung phụ thuộc role
        colExtra.setCellValueFactory(cell -> {
            Report r = cell.getValue();
            if (isSuperAdmin) {
                String name = r.getRestaurantName() != null
                        ? r.getRestaurantName() : "Nhà hàng #" + r.getRestaurantId();
                return new javafx.beans.property.SimpleStringProperty(name);
            } else {
                return new javafx.beans.property.SimpleStringProperty("User #" + r.getCreatedBy());
            }
        });
    }

    // ── Filter setup ───────────────────────────────────────────────────────────

    private void setupFilter() {
        // Khởi tạo ComboBox với nhãn tiếng Việt
        cmbFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Đang mở", "Đang xử lý", "Đã giải quyết", "Đã đóng"));
        cmbFilter.setValue("Tất cả");

        // FilteredList bọc allReports
        filteredList = new FilteredList<>(allReports, r -> true);
        tableView.setItems(filteredList);

        // Listener ComboBox + TextField → cập nhật predicate
        cmbFilter.valueProperty().addListener((obs, o, n) -> applyPredicate());
        searchField.textProperty().addListener((obs, o, n) -> applyPredicate());
    }

    private void applyPredicate() {
        String statusFilter = cmbFilter.getValue();
        String search       = searchField.getText().trim().toLowerCase();
        String enumFilter   = toEnumName(statusFilter);

        filteredList.setPredicate(r -> {
            boolean matchStatus = enumFilter == null
                    || (r.getStatus() != null && r.getStatus().name().equals(enumFilter));
            boolean matchSearch = search.isEmpty()
                    || (r.getTitle() != null && r.getTitle().toLowerCase().contains(search));
            return matchStatus && matchSearch;
        });
    }

    /** "Đang mở" → "OPEN", "Tất cả" → null, v.v. */
    private String toEnumName(String display) {
        if (display == null) return null;
        return switch (display) {
            case "Đang mở"       -> "OPEN";
            case "Đang xử lý"    -> "IN_PROGRESS";
            case "Đã giải quyết" -> "RESOLVED";
            case "Đã đóng"       -> "CLOSED";
            default              -> null;
        };
    }

    // ── Double-click → detail dialog ──────────────────────────────────────────

    private void setupDoubleClick() {
        tableView.setRowFactory(tv -> {
            TableRow<Report> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openDetailDialog(row.getItem());
                }
            });
            return row;
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Data loading
    // ═════════════════════════════════════════════════════════════════════════

    public void loadData() {
        showLoading(true);

        Task<List<Report>> task = new Task<>() {
            @Override
            protected List<Report> call() {
                return new ReportDAO().findByCurrentUser();
            }
        };

        task.setOnSucceeded(e -> {
            allReports.setAll(task.getValue());
            applyPredicate();
            showLoading(false);
        });

        task.setOnFailed(e -> {
            showLoading(false);
            Throwable cause = task.getException();
            Platform.runLater(() ->
                showError("Lỗi tải dữ liệu: " + (cause != null ? cause.getMessage() : "unknown")));
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Dialog openers
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void openAddDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/dialog/ReportAddDialog.fxml"));
            Parent root = loader.load();

            ReportAddController ctrl = loader.getController();
            ctrl.setParentController(this);

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(getOwnerWindow());
            dialog.setTitle("Gửi báo cáo mới");
            dialog.setScene(new Scene(root));
            dialog.setResizable(false);
            dialog.showAndWait();
        } catch (Exception ex) {
            showError("Không thể mở dialog: " + ex.getMessage());
        }
    }

    private void openDetailDialog(Report report) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/ReportDetailDialog.fxml"));
            Parent root = loader.load();

            ReportDetailController ctrl = loader.getController();
            ctrl.setReport(report);
            ctrl.setParentController(this);

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(getOwnerWindow());
            dialog.setTitle("Chi tiết báo cáo #" + report.getReportId());
            dialog.setScene(new Scene(root));
            dialog.setResizable(false);
            dialog.showAndWait();
        } catch (Exception ex) {
            showError("Không thể mở dialog: " + ex.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private void showLoading(boolean show) {
        Platform.runLater(() -> {
            loadingBar.setVisible(show);
            loadingBar.setManaged(show);
        });
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle("Lỗi");
        alert.showAndWait();
    }

    private Window getOwnerWindow() {
        return tableView.getScene() != null ? tableView.getScene().getWindow() : null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Inner classes — custom TableCell renderers (badge-style)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Pill badge cell cho cột Trạng thái.
     * Màu nền theo status: OPEN=xám, IN_PROGRESS=xanh dương, RESOLVED=xanh lá, CLOSED=xám nhạt.
     */
    private static class StatusBadgeCell extends TableCell<Report, String> {
        private final StackPane badge = new StackPane();
        private final Label     lbl   = new Label();

        StatusBadgeCell() {
            badge.setPadding(new Insets(3, 10, 3, 10));
            badge.setMaxWidth(Double.MAX_VALUE);
            lbl.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 12px; -fx-font-weight: bold;");
            badge.getChildren().add(lbl);
            badge.setStyle("-fx-background-radius: 12;");
            setGraphic(badge);
            setAlignment(Pos.CENTER);
            setText(null);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            lbl.setText(item);
            String bg, fg;
            switch (item) {
                case "Mở"            -> { bg = "#F3F4F6"; fg = "#6B7280"; }
                case "Đang xử lý"    -> { bg = "#DBEAFE"; fg = "#1D4ED8"; }
                case "Đã giải quyết" -> { bg = "#D1FAE5"; fg = "#065F46"; }
                case "Đã đóng"       -> { bg = "#F3F4F6"; fg = "#9CA3AF"; }
                default              -> { bg = "#F3F4F6"; fg = "#374151"; }
            }
            badge.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 12;");
            lbl.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 12px; " +
                         "-fx-font-weight: bold; -fx-text-fill: " + fg + ";");
            setGraphic(badge);
        }
    }

    /**
     * Colored badge cell cho cột Mức độ.
     * Thêm emoji prefix + màu chữ theo severity.
     */
    private static class SeverityBadgeCell extends TableCell<Report, String> {
        private final Label lbl = new Label();

        SeverityBadgeCell() {
            lbl.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 12px; -fx-font-weight: bold;");
            setGraphic(lbl);
            setAlignment(Pos.CENTER);
            setText(null);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            String display, color;
            switch (item) {
                case "Nghiêm trọng" -> { display = "🔴 Nghiêm trọng"; color = "#E24B4A"; }
                case "Cao"          -> { display = "🟠 Cao";          color = "#FB923C"; }
                case "Trung bình"   -> { display = "🟡 Trung bình";   color = "#D97706"; }
                case "Thấp"         -> { display = "🟢 Thấp";         color = "#16A34A"; }
                default             -> { display = item;               color = "#374151"; }
            }
            lbl.setText(display);
            lbl.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 12px; " +
                         "-fx-font-weight: bold; -fx-text-fill: " + color + ";");
            setGraphic(lbl);
        }
    }
}