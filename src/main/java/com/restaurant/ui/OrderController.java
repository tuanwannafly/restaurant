package com.restaurant.ui;

import com.restaurant.dao.OrderDAO;
import com.restaurant.model.Order;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;
import com.restaurant.ui.dialog.OrderDetailController;
import com.restaurant.ui.dialog.OrderStatController;

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
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class OrderController implements Initializable {

    // ─── FXML ────────────────────────────────────────────────────────────────

    @FXML private TableView<Order>          orderTable;
    @FXML private TableColumn<Order,String> colId;
    @FXML private TableColumn<Order,String> colTable;
    @FXML private TableColumn<Order,String> colTotal;
    @FXML private TableColumn<Order,String> colStatus;
    @FXML private TableColumn<Order,String> colTime;
    @FXML private TableColumn<Order,Void>   colAction;

    @FXML private TextField searchField;
    @FXML private Button    btnAddOrder;

    // ─── State ───────────────────────────────────────────────────────────────

    private final OrderDAO                dao     = new OrderDAO();
    private final ObservableList<Order>   allItems = FXCollections.observableArrayList();
    private       FilteredList<Order>     filtered;

    private boolean canDelete = false;

    private static final NumberFormat NF =
        NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // ─── Init ─────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        applyPermissions();
        setupColumns();
        loadData();
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    private void applyPermissions() {
        AppSession session = AppSession.getInstance();
        boolean canAdd = session.hasPermission(Permission.ADD_ORDER);
        canDelete       = session.hasPermission(Permission.DELETE_ORDER);
        btnAddOrder.setVisible(canAdd);
        btnAddOrder.setManaged(canAdd);
    }

    // ─── Column setup ─────────────────────────────────────────────────────────

    private void setupColumns() {
        colId.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getId()));

        colTable.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getTableName()));

        colTotal.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                NF.format((long) data.getValue().getTotalAmount()) + " đ"));

        // Colored status badge
        colStatus.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                data.getValue().getStatusDisplay()));
        colStatus.setCellFactory(col -> new StatusBadgeTableCell<>());

        colTime.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getCreatedTime()));

        // Action buttons cell
        colAction.setCellFactory(col -> new ActionCell());
    }

    // ─── Load data ────────────────────────────────────────────────────────────

    public void loadData() {
        Task<List<Order>> task = new Task<>() {
            @Override protected List<Order> call() { return dao.getAll(); }
        };
        task.setOnSucceeded(e -> {
            allItems.setAll(task.getValue());
            filtered = new FilteredList<>(allItems, o -> true);
            orderTable.setItems(filtered);
            applyFilter();
        });
        task.setOnFailed(e ->
            System.err.println("[OrderController] loadData lỗi: " + task.getException().getMessage()));
        new Thread(task, "order-load").start();
    }

    // ─── Search / filter ──────────────────────────────────────────────────────

    @FXML
    private void onSearch() {
        applyFilter();
    }

    private void applyFilter() {
        if (filtered == null) return;
        String q = searchField.getText().trim().toLowerCase();
        filtered.setPredicate(o ->
            q.isEmpty()
            || o.getId().toLowerCase().contains(q)
            || o.getTableName().toLowerCase().contains(q)
        );
    }

    // ─── Add order ────────────────────────────────────────────────────────────

    @FXML
    private void onAddOrder() {
        if (!AppSession.getInstance().hasPermission(Permission.ADD_ORDER)) {
            showAlert("Bạn không có quyền thực hiện thao tác này.");
            return;
        }
        showAlert("Chức năng tạo đơn mới đang phát triển.");
    }

    // ─── Stat dialog ──────────────────────────────────────────────────────────

    @FXML
    private void onOpenStat() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/dialog/OrderStatDialog.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Thống kê đơn hàng");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(getOwnerWindow());
            stage.setResizable(false);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ─── Action handlers ──────────────────────────────────────────────────────

    private void handleDelete(Order order) {
        if (!canDelete) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Xóa đơn hàng \"" + order.getId() + "\"?",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                Task<Void> task = new Task<>() {
                    @Override protected Void call() {
                        dao.delete(order.getId());
                        return null;
                    }
                };
                task.setOnSucceeded(e -> loadData());
                new Thread(task, "order-delete").start();
            }
        });
    }

    private void handleStatusUpdate(Order order) {
        String[] statuses = {"Đang phục vụ", "Hoàn thành", "Đã hủy"};
        ChoiceDialog<String> dialog = new ChoiceDialog<>(order.getStatusDisplay(), statuses);
        dialog.setTitle("Cập nhật trạng thái");
        dialog.setHeaderText(null);
        dialog.setContentText("Chọn trạng thái mới:");
        dialog.showAndWait().ifPresent(chosen -> {
            Order.Status newStatus = chosen.equals("Đang phục vụ") ? Order.Status.DANG_PHUC_VU
                : chosen.equals("Hoàn thành") ? Order.Status.HOAN_THANH : Order.Status.DA_HUY;
            order.setStatus(newStatus);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    dao.update(order);
                    return null;
                }
            };
            task.setOnSucceeded(e -> loadData());
            new Thread(task, "order-update").start();
        });
    }

    private void handleViewDetail(Order order) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/dialog/OrderDetailDialog.fxml"));
            Parent root = loader.load();
            OrderDetailController ctrl = loader.getController();
            ctrl.setOrder(order);

            Stage stage = new Stage();
            stage.setTitle("Chi tiết đơn hàng");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(getOwnerWindow());
            stage.setResizable(false);
            stage.setScene(new Scene(root, 560, 500));
            stage.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Window getOwnerWindow() {
        return orderTable.getScene() != null ? orderTable.getScene().getWindow() : null;
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Inner: Action cell
    // ═════════════════════════════════════════════════════════════════════════

    private class ActionCell extends TableCell<Order, Void> {

        private final HBox box = new HBox(6);

        ActionCell() {
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(4, 8, 4, 8));
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getIndex() >= getTableView().getItems().size()) {
                setGraphic(null);
                return;
            }
            Order order = getTableView().getItems().get(getIndex());
            box.getChildren().clear();

            if (canDelete) {
                Button btnDel = actionButton("🗑 Xóa", "btn-danger-sm");
                btnDel.setOnAction(e -> handleDelete(order));
                box.getChildren().add(btnDel);
            }

            Button btnUpd = actionButton("✏ Cập nhật", "btn-primary-sm");
            btnUpd.setOnAction(e -> handleStatusUpdate(order));

            Button btnView = actionButton("👁 Xem chi tiết", "btn-accent-sm");
            btnView.setOnAction(e -> handleViewDetail(order));

            box.getChildren().addAll(btnUpd, btnView);
            setGraphic(box);
        }

        private Button actionButton(String text, String style) {
            Button b = new Button(text);
            b.getStyleClass().addAll("action-btn", style);
            return b;
        }
    }
}