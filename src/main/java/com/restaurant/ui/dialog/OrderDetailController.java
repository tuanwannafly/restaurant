package com.restaurant.ui.dialog;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.restaurant.dao.OrderDAO;
import com.restaurant.model.Order;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;
import com.restaurant.ui.InlineErrorBarFx;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;              // ← import thiếu
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller cho OrderDetailDialog.fxml.
 *
 * <p>Hiển thị thông tin chi tiết đơn hàng và danh sách món.
 * Phiên bản này bổ sung tính năng <b>Huỷ đơn</b>:
 * <ul>
 *   <li>Nút {@code btnCancelOrder} chỉ hiện khi user có quyền
 *       {@link Permission#CANCEL_ORDER} VÀ trạng thái đơn là
 *       {@code PENDING} hoặc {@code ACCEPTED}.</li>
 *   <li>Sau khi huỷ thành công, gọi {@code onCancelledCallback} để
 *       controller cha ({@code OrderController}) tự refresh danh sách.</li>
 * </ul>
 * </p>
 */
public class OrderDetailController {

    // ── FXML injections ───────────────────────────────────────────────────────
    @FXML private Label lblTitle;
    @FXML private Label lblOrderId;
    @FXML private Label lblTableName;
    @FXML private Label lblStatus;
    @FXML private Label lblCreatedAt;
    @FXML private Label lblNote;
    @FXML private Label lblTotal;

    // Order.OrderItem ← inner static class, KHÔNG import riêng
    @FXML private TableView<Order.OrderItem>              tableItems;
    @FXML private TableColumn<Order.OrderItem, String>    colItemName;
    @FXML private TableColumn<Order.OrderItem, Integer>   colQuantity;
    @FXML private TableColumn<Order.OrderItem, Double>    colUnitPrice;
    @FXML private TableColumn<Order.OrderItem, Double>    colSubtotal;

    // ── [THÊM MỚI] ───────────────────────────────────────────────────────────
    @FXML private Button btnCancelOrder;
    @FXML private Button btnClose;
    @FXML private VBox   contentVBox;

    // ── State ─────────────────────────────────────────────────────────────────
    private Order            currentOrder;
    private Runnable         onCancelledCallback;
    private InlineErrorBarFx errorBar;

    private static final NumberFormat CURRENCY_FMT =
            NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // ── Cấu hình TableView ───────────────────────────────────────────────
        // Order.OrderItem.getMenuItemName() → property name = "menuItemName"
        colItemName .setCellValueFactory(new PropertyValueFactory<>("menuItemName"));
        colQuantity .setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colUnitPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colSubtotal .setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        colUnitPrice.setCellFactory(col -> new TableCell<Order.OrderItem, Double>() {
            @Override
            protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : CURRENCY_FMT.format(v));
            }
        });
        colSubtotal.setCellFactory(col -> new TableCell<Order.OrderItem, Double>() {
            @Override
            protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : CURRENCY_FMT.format(v));
            }
        });

        // ── [THÊM MỚI] Kiểm tra quyền CANCEL_ORDER ──────────────────────────
        boolean hasPermission = RbacGuard.getInstance().can(Permission.CANCEL_ORDER);
        if (!hasPermission) {
            btnCancelOrder.setVisible(false);
            btnCancelOrder.setManaged(false);
        }

        // ── [THÊM MỚI] Khởi tạo InlineErrorBarFx ────────────────────────────
        errorBar = new InlineErrorBarFx();
        contentVBox.getChildren().add(0, errorBar);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Truyền đơn hàng cần hiển thị. Phải gọi trước khi dialog được show.
     */
    public void setOrder(Order order) {
        this.currentOrder = order;

        lblTitle    .setText("Chi tiết đơn #" + order.getId());
        lblOrderId  .setText(order.getId());
        lblTableName.setText(order.getTableName());
        lblStatus   .setText(order.getStatusDisplay());           // ← getStatusDisplay()
        lblCreatedAt.setText(order.getCreatedTime() != null       // ← getCreatedTime() trả String
                             ? order.getCreatedTime() : "—");
        lblNote.setText("—");                                     // ← Order không có getNote()

        List<Order.OrderItem> allItems = order.getItems();

        // [FIX] Loại bỏ món CANCELLED khỏi danh sách hiển thị và tổng tiền
        // Chỉ hiển thị + tính tiền những món chưa bị hủy
        List<Order.OrderItem> activeItems = allItems == null
                ? List.of()
                : allItems.stream()
                        .filter(i -> i.getItemStatus() != Order.OrderItem.ItemStatus.CANCELLED)
                        .collect(Collectors.toList());

        tableItems.setItems(FXCollections.observableArrayList(activeItems));

        // [FIX] Chỉ cộng tổng những món không bị hủy
        double total = activeItems.stream()
                .mapToDouble(Order.OrderItem::getSubtotal)
                .sum();
        lblTotal.setText(CURRENCY_FMT.format(total));

        // ── [THÊM MỚI] Visibility btnCancelOrder ─────────────────────────────
        boolean hasPermission = RbacGuard.getInstance().can(Permission.CANCEL_ORDER);
        boolean isAdminOrAbove = RbacGuard.getInstance().isManagerOrAbove();

        boolean cancellable;
        if (isAdminOrAbove) {
            // ADMIN được huỷ mọi trạng thái trừ COMPLETED và CANCELLED
            cancellable = hasPermission
                    && order.getStatus() != Order.Status.COMPLETED
                    && order.getStatus() != Order.Status.CANCELLED;
        } else {
            // WAITER chỉ được huỷ PENDING
            cancellable = hasPermission
                    && order.getStatus() == Order.Status.PENDING;
        }
        btnCancelOrder.setVisible(cancellable);
        btnCancelOrder.setManaged(cancellable);
        btnCancelOrder.setManaged(cancellable);
    }

    /**
     * [THÊM MỚI] Callback để OrderController tự refresh sau khi huỷ thành công.
     * <pre>
     *   ctrl.setOnCancelledCallback(this::loadData);
     * </pre>
     */
    public void setOnCancelledCallback(Runnable callback) {
        this.onCancelledCallback = callback;
    }

    // ── FXML Handlers ─────────────────────────────────────────────────────────

    /**
     * [THÊM MỚI] Xử lý huỷ đơn:
     * xác nhận → nhập lý do → gọi DAO → đóng / hiện lỗi.
     */
    @FXML
    private void handleCancelOrder() {
        if (currentOrder == null) return;

        // Bước 1: Xác nhận
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận huỷ đơn");
        confirm.setHeaderText("Bạn có chắc chắn muốn huỷ đơn " + currentOrder.getId() + "?");
        confirm.setContentText("Hành động này không thể hoàn tác.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        // Bước 2: Lý do (required, không để trống)
        String reason = promptForReason();
        if (reason == null) return;

        // Bước 3: Gọi DAO trên background thread
        String finalReason = reason;
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return new OrderDAO().cancelOrder(currentOrder.getId(), finalReason);
            }
        };

        task.setOnSucceeded(e -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                closeDialog();
                if (onCancelledCallback != null) onCancelledCallback.run();
            } else {
                errorBar.show("Không thể huỷ đơn ở trạng thái hiện tại");
            }
        });

        task.setOnFailed(e -> {
            System.err.println("[OrderDetailController] cancelOrder lỗi: "
                    + task.getException().getMessage());
            errorBar.show("Không thể huỷ đơn ở trạng thái hiện tại");
        });

        new Thread(task, "cancel-order-thread").start();
    }

    @FXML
    private void onClose() {
        closeDialog();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * [THÊM MỚI] TextInputDialog nhập lý do huỷ; lặp lại nếu bỏ trống.
     * @return lý do đã nhập, hoặc {@code null} nếu user bấm Cancel.
     */
    private String promptForReason() {
        while (true) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Lý do huỷ đơn");
            dialog.setHeaderText("Nhập lý do huỷ đơn " + currentOrder.getId() + ":");
            dialog.setContentText("Lý do:");

            Optional<String> input = dialog.showAndWait();
            if (input.isEmpty()) return null;

            String trimmed = input.get().trim();
            if (!trimmed.isEmpty()) return trimmed;

            Alert warn = new Alert(Alert.AlertType.WARNING);
            warn.setTitle("Thiếu thông tin");
            warn.setHeaderText(null);
            warn.setContentText("Vui lòng nhập lý do huỷ đơn.");
            warn.showAndWait();
        }
    }

    private void closeDialog() {
        Stage stage = (Stage) lblTitle.getScene().getWindow();
        stage.close();
    }
}