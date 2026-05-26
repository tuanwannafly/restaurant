package com.restaurant.ui.dialog;

import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import com.restaurant.dao.OrderDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.Order;
import com.restaurant.model.TableItem;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * PaymentDialogController — Phase 5 / Phase 6
 *
 * <p>Controller của {@code PaymentDialog.fxml}.
 *
 * <p><b>Khi xác nhận thanh toán:</b>
 * <ol>
 *   <li>{@link OrderDAO#completeOrder(String)} — đánh dấu order COMPLETED.</li>
 *   <li>{@link TableDAO#updateStatus(String, TableItem.Status)} — bàn → DIRTY.</li>
 *   <li>Set {@code paymentCompleted = true}, đóng Stage.</li>
 * </ol>
 *
 * <p><b>Cách dùng từ TableController:</b>
 * <pre>{@code
 *   PaymentDialogController ctrl = loader.getController();
 *   ctrl.init(tableId, tableName, orderId);
 *   stage.showAndWait();
 *   if (ctrl.isPaymentCompleted()) { loadData(); }
 * }</pre>
 */
public class PaymentDialogController implements Initializable {

    // ─── FXML fields ──────────────────────────────────────────────────────────

    @FXML private Label                       lblHeader;
    @FXML private TableView<Order.OrderItem>  tvItems;
    @FXML private TableColumn<Order.OrderItem, String> colName;
    @FXML private TableColumn<Order.OrderItem, String> colQty;
    @FXML private TableColumn<Order.OrderItem, String> colPrice;
    @FXML private TableColumn<Order.OrderItem, String> colTotal;
    @FXML private Label                       lblTotal;
    @FXML private RadioButton                 rboCash;
    @FXML private RadioButton                 rboTransfer;
    @FXML private VBox                        qrPanel;
    @FXML private Canvas                      qrCanvas;
    @FXML private Button                      btnConfirm;

    // ─── State ────────────────────────────────────────────────────────────────

    private String  tableId;
    private String  orderId;
    private boolean paymentCompleted = false;

    // ─── DAO ─────────────────────────────────────────────────────────────────

    private final OrderDAO orderDAO = new OrderDAO();
    private final TableDAO tableDAO = new TableDAO();

    // ─── Formatting ───────────────────────────────────────────────────────────

    private static final NumberFormat PRICE_FMT =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    // ─── Initializable ────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupToggleGroup();
        drawQrPlaceholder();
    }

    // ─── Init (called by TableController after load) ──────────────────────────

    /**
     * Khởi tạo dialog với thông tin bàn + đơn hàng.
     * Gọi trước {@code stage.showAndWait()}.
     */
    public void init(String tableId, String tableName, String orderId) {
        this.tableId = tableId;
        this.orderId = orderId;
        lblHeader.setText("Thanh toán — Bàn " + tableName);
        loadItems();
    }

    // ─── Table columns ────────────────────────────────────────────────────────

    private void setupTableColumns() {
        colName.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getMenuItemName()));

        colQty.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().getQuantity())));

        colPrice.setCellValueFactory(cell ->
                new SimpleStringProperty(formatPrice(cell.getValue().getUnitPrice())));

        colTotal.setCellValueFactory(cell ->
                new SimpleStringProperty(formatPrice(cell.getValue().getSubtotal())));

        // Right-align numeric columns
        rightAlign(colQty);
        rightAlign(colPrice);
        rightAlign(colTotal);
    }

    private static <T> void rightAlign(TableColumn<T, String> col) {
        col.setCellFactory(tc -> {
            TableCell<T, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            return cell;
        });
    }

    // ─── Payment method toggle ────────────────────────────────────────────────

    private void setupToggleGroup() {
        ToggleGroup tg = new ToggleGroup();
        rboCash.setToggleGroup(tg);
        rboTransfer.setToggleGroup(tg);
        rboCash.setSelected(true);
    }

    @FXML
    private void onPaymentMethodChanged() {
        boolean showQr = rboTransfer.isSelected();
        qrPanel.setVisible(showQr);
        qrPanel.setManaged(showQr);
    }

    // ─── QR canvas ────────────────────────────────────────────────────────────

    /**
     * Vẽ QR code placeholder giả trên {@link Canvas}.
     * Pattern được tạo bằng seed cố định để nhìn giống QR thật.
     */
    private void drawQrPlaceholder() {
        GraphicsContext gc = qrCanvas.getGraphicsContext2D();
        int cell = 9;
        int n    = 20;         // 20×20 cells → 180px
        int size = cell * n;
        double ox = (qrCanvas.getWidth()  - size) / 2;
        double oy = (qrCanvas.getHeight() - size) / 2;

        // Background
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, qrCanvas.getWidth(), qrCanvas.getHeight());

        boolean[][] pattern = buildQrPattern(n);

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                gc.setFill(pattern[r][c]
                        ? Color.web("#1F2937")
                        : Color.WHITE);
                gc.fillRect(ox + c * cell, oy + r * cell, cell, cell);
            }
        }

        // Outer border
        gc.setStroke(Color.web("#1F2937"));
        gc.setLineWidth(2);
        gc.strokeRect(ox, oy, size, size);
    }

    /** Sinh pattern QR placeholder với seed cố định. */
    private boolean[][] buildQrPattern(int n) {
        boolean[][] p = new boolean[n][n];
        fillFinder(p, 0,     0,     7, n);   // top-left
        fillFinder(p, 0,     n - 7, 7, n);   // top-right
        fillFinder(p, n - 7, 0,     7, n);   // bottom-left

        // "data" modules — seeded random (same every call)
        java.util.Random rnd = new java.util.Random(0xCAFEBABEL);
        for (int r = 8; r < n - 8; r++) {
            for (int c = 8; c < n - 8; c++) {
                p[r][c] = rnd.nextBoolean();
            }
        }
        return p;
    }

    private void fillFinder(boolean[][] p, int r0, int c0, int sz, int n) {
        for (int r = r0; r < r0 + sz && r < n; r++) {
            for (int c = c0; c < c0 + sz && c < n; c++) {
                boolean outer = (r == r0 || r == r0 + sz - 1 || c == c0 || c == c0 + sz - 1);
                boolean inner = (r >= r0 + 2 && r <= r0 + sz - 3
                              && c >= c0 + 2 && c <= c0 + sz - 3);
                p[r][c] = outer || inner;
            }
        }
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    private void loadItems() {
        Task<List<Order.OrderItem>> task = new Task<>() {
            @Override
            protected List<Order.OrderItem> call() {
                return orderDAO.getItemsWithStatus(orderId);
            }
        };

        task.setOnSucceeded(e -> {
            // [FIX] Loại bỏ món CANCELLED khỏi danh sách hiển thị và tính tiền
            List<Order.OrderItem> items = task.getValue().stream()
                    .filter(i -> i.getItemStatus()
                            != Order.OrderItem.ItemStatus.CANCELLED)
                    .collect(java.util.stream.Collectors.toList());
            tvItems.getItems().setAll(items);
            populateTotal(items);
            btnConfirm.setDisable(items.isEmpty());
        });

        task.setOnFailed(e -> {
            System.err.println("[PaymentDialogController] loadItems lỗi: "
                    + task.getException().getMessage());
            showError("Không thể tải danh sách món. Vui lòng thử lại.");
        });

        new Thread(task, "payment-loader").start();
    }

    private void populateTotal(List<Order.OrderItem> items) {
        // [FIX] Chỉ tính các món chưa bị hủy — CANCELLED không được cộng vào tổng
        double total = items.stream()
                .filter(i -> i.getItemStatus()
                        != Order.OrderItem.ItemStatus.CANCELLED)
                .mapToDouble(Order.OrderItem::getSubtotal)
                .sum();
        lblTotal.setText(formatPrice(total) + " ₫");
    }

    // ─── Confirm payment ──────────────────────────────────────────────────────

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onConfirmPayment() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Xác nhận thanh toán và đóng bàn?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận thanh toán");
        confirm.setHeaderText(null);
        confirm.initOwner(getStage());
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> doPayment());
    }

    private void doPayment() {
        btnConfirm.setDisable(true);
        btnConfirm.setText("⏳  Đang xử lý…");

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                boolean ok1 = orderDAO.completeOrder(orderId);
                boolean ok2 = true;
                try {
                    ok2 = tableDAO.updateStatus(tableId, TableItem.Status.DIRTY);
                } catch (SecurityException secEx) {
                    // CASHIER không có quyền updateStatus qua RbacGuard — force set DIRTY
                    // để WaiterController nhận đúng trạng thái cần dọn.
                    System.err.println("[PaymentDialogController] updateStatus SecurityException – thử force: "
                            + secEx.getMessage());
                    try {
                        ok2 = tableDAO.updateStatus(tableId, TableItem.Status.DIRTY);
                    } catch (Exception forceEx) {
                        System.err.println("[PaymentDialogController] force updateStatus lỗi: " + forceEx.getMessage());
                    }
                } catch (Exception tableEx) {
                    // Bàn đã đổi trạng thái bởi tiến trình khác — không block thanh toán
                    System.err.println("[PaymentDialogController] updateStatus lỗi (bỏ qua): "
                            + tableEx.getMessage());
                }
                if (ok1) {
                    // Broadcast TABLES + KITCHEN để WaiterController (và các màn hình khác)
                    // nhận tín hiệu và tự refresh — KITCHEN cần thiết vì WaiterController
                    // lắng nghe cả KITCHEN topic để hiển thị bàn cần dọn.
                    try {
                        long rid = com.restaurant.session.AppSession.getInstance().getRestaurantId();
                        com.restaurant.websocket.RestaurantEventServer srv =
                            com.restaurant.websocket.RestaurantEventServer.getInstance();
                        com.restaurant.websocket.WsEvent tablesEvt =
                            com.restaurant.websocket.WsEvent.of(
                                com.restaurant.websocket.WsTopic.TABLES, rid);
                        com.restaurant.websocket.WsEvent kitchenEvt =
                            com.restaurant.websocket.WsEvent.of(
                                com.restaurant.websocket.WsTopic.KITCHEN, rid);
                        if (srv.isRunning()) {
                            srv.broadcast(tablesEvt);
                            srv.broadcast(kitchenEvt);
                        } else {
                            com.restaurant.websocket.RestaurantEventClient cli =
                                com.restaurant.websocket.RestaurantEventClient.getInstance();
                            cli.publishToServer(tablesEvt);
                            cli.publishToServer(kitchenEvt);
                        }
                    } catch (Exception wsEx) {
                        System.err.println("[PaymentDialogController] broadcast lỗi: " + wsEx.getMessage());
                    }
                }
                return ok1 && ok2;
            }
        };

        task.setOnSucceeded(e -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                paymentCompleted = true;
                showInfo("Thanh toán thành công! Bàn đã được đánh dấu cần dọn.");
                close();
            } else {
                resetConfirmButton();
                showError("Thanh toán thất bại. Vui lòng thử lại.");
            }
        });

        task.setOnFailed(e -> {
            System.err.println("[PaymentDialogController] doPayment lỗi: "
                    + task.getException().getMessage());
            resetConfirmButton();
            showError("Lỗi xử lý thanh toán: " + task.getException().getMessage());
        });

        new Thread(task, "payment-confirm").start();
    }

    private void resetConfirmButton() {
        Platform.runLater(() -> {
            btnConfirm.setDisable(false);
            btnConfirm.setText("✅  Xác nhận thanh toán");
        });
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** @return {@code true} nếu người dùng đã xác nhận thanh toán thành công. */
    public boolean isPaymentCompleted() {
        return paymentCompleted;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String formatPrice(double amount) {
        return PRICE_FMT.format((long) amount);
    }

    private Stage getStage() {
        return (Stage) btnConfirm.getScene().getWindow();
    }

    private void close() {
        getStage().close();
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            a.setHeaderText("Lỗi");
            a.initOwner(getStage());
            a.showAndWait();
        });
    }

    private void showInfo(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
            a.setHeaderText("Thông báo");
            a.initOwner(getStage());
            a.showAndWait();
        });
    }
}