package com.restaurant.ui;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.restaurant.dao.MenuItemDAO;
import com.restaurant.dao.OrderDAO;
import com.restaurant.model.MenuItem;
import com.restaurant.model.Order;
import com.restaurant.session.AppSession;
import com.restaurant.ui.fx.controller.BasePageController;
import com.restaurant.ui.fx.controller.CartPageController;
import com.restaurant.ui.fx.controller.MenuPageController;
import com.restaurant.ui.fx.controller.PaymentPageController;
import com.restaurant.ui.fx.controller.StatusPageController;
import com.restaurant.ui.fx.controller.WaitingPageController;
import com.restaurant.ui.fx.util.PollManagerFx;
import com.restaurant.websocket.RestaurantEventClient;
import com.restaurant.websocket.RestaurantEventServer;
import com.restaurant.websocket.WsEvent;
import com.restaurant.websocket.WsTopic;

import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * TableOrderStage — Phase 15: JavaFX Stage thay thế Swing TableOrderFrame.
 *
 * <p>StackPane chứa 5 page FXML, điều hướng bằng cách show/hide node.
 * Mỗi page có Controller riêng; Stage đóng vai trò coordinator và cung cấp
 * shared state (tableId, orderId, cartItems, currentRound).
 *
 * <h3>Pages</h3>
 * <ul>
 *   <li>{@code menu}    — Chọn món: search + filter + grid cards</li>
 *   <li>{@code cart}    — Giỏ hàng: TableView + inline edit + gửi món</li>
 *   <li>{@code status}  — Trạng thái đơn: TableView auto-refresh mỗi 5s</li>
 *   <li>{@code payment} — Thanh toán: chuyển khoản / tiền mặt</li>
 *   <li>{@code waiting} — Chờ xử lý: animation + auto-close khi done</li>
 * </ul>
 */
public class TableOrderStage extends Stage {

    // ── Page keys ──────────────────────────────────────────────────────────────
    public static final String PAGE_MENU    = "menu";
    public static final String PAGE_CART    = "cart";
    public static final String PAGE_STATUS  = "status";
    public static final String PAGE_PAYMENT = "payment";
    public static final String PAGE_WAITING = "waiting";

    // ── Shared state (read by controllers via getStage()) ──────────────────────
    private final String tableId;
    private final String orderId;
    private final String tableName;
    private final String restaurantName;

    /** Current navigation page key. */
    private String currentPage = PAGE_MENU;

    /** Cờ đánh dấu thanh toán đã hoàn tất — ảnh hưởng trạng thái bàn khi đóng. */
    private boolean paymentCompleted = false;

    /** Gọi từ WaitingPageController khi xác nhận thanh toán hoàn tất. */
    public void markPaymentCompleted() {
        this.paymentCompleted = true;
    }

    /** Cancel-token từ addEventHandler — để huỷ đăng ký khi đóng stage. */
    private Runnable cancelWsHandler;

    /**
     * WS topic riêng cho bàn này — tính một lần từ tableId.
     * Dùng cho subscribe/filter trong {@link #setupWsSubscription()}.
     */
    private final String wsTableTopic;

    /** Round counter — tăng mỗi lần gửi order thành công. */
    private int currentRound = 1;

    /** Giỏ hàng dùng chung giữa MenuPage và CartPage. */
    private final List<CartEntry> cartItems = new ArrayList<>();

    // ── JavaFX nodes ──────────────────────────────────────────────────────────
    private StackPane rootPane;

    /** Map page key → loaded FXML root. */
    private final Map<String, Parent> pages = new LinkedHashMap<>();

    /** Map page key → Controller instance. */
    private final Map<String, BasePageController> controllers = new HashMap<>();

    // ── DAO (dùng chung, khởi tạo một lần) ────────────────────────────────────
    private final OrderDAO   orderDAO   = new OrderDAO();
    private final MenuItemDAO menuItemDAO = new MenuItemDAO();

    // ── Formatting ─────────────────────────────────────────────────────────────
    public static final NumberFormat PRICE_FMT =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    // ── Inner record CartEntry ─────────────────────────────────────────────────

    /** Shared cart entry (giữa MenuPage ↔ CartPage). */
    public static class CartEntry {
        public final String menuItemId;
        public final String name;
        public final double unitPrice;
        public int    quantity;
        public String note;

        public CartEntry(String menuItemId, String name, double unitPrice) {
            this.menuItemId = menuItemId;
            this.name       = name;
            this.unitPrice  = unitPrice;
            this.quantity   = 1;
            this.note       = "";
        }

        public double subtotal() { return unitPrice * quantity; }

        @Override
        public String toString() {
            return quantity + "x " + name + "  " + TableOrderStage.formatPrice(subtotal()) + "đ";
        }
    }

    // ── Constructor ────────────────────────────────────────────────────────────

    public TableOrderStage(String tableId, String orderId, String tableName) {
        this.tableId        = tableId;
        this.orderId        = orderId;
        this.tableName      = tableName;
        this.restaurantName = loadRestaurantName();
        this.wsTableTopic   = WsTopic.forTable(Integer.parseInt(tableId));

        setTitle("Bàn " + tableName);
        setFullScreen(true);
        setFullScreenExitHint("");

        buildUI();
        setupCloseHandler();
    }

    // ── UI build ───────────────────────────────────────────────────────────────

    private void buildUI() {
        rootPane = new StackPane();

        // Load tất cả pages
        loadPage(PAGE_MENU,    "/fxml/TableOrderSingleView.fxml");
        loadPage(PAGE_STATUS,  "/fxml/StatusPageView.fxml");
        loadPage(PAGE_PAYMENT, "/fxml/PaymentPageView.fxml");
        loadPage(PAGE_WAITING, "/fxml/WaitingPageView.fxml");

        // Thêm vào StackPane, ẩn tất cả
        pages.values().forEach(p -> {
            p.setVisible(false);
            rootPane.getChildren().add(p);
        });

        Scene scene = new Scene(rootPane);
        scene.getStylesheets().add(
                Objects.requireNonNull(
                    getClass().getResource("/css/tableorder.css")).toExternalForm());
        setScene(scene);

        // Hiện trang đầu
        navigateTo(PAGE_MENU);

        // Thay polling order_status / order_waiting bằng WebSocket
        setupWsSubscription();
    }

    private void loadPage(String key, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            BasePageController ctrl = loader.getController();
            ctrl.setStage(this);
            pages.put(key, root);
            controllers.put(key, ctrl);
        } catch (IOException ex) {
            throw new RuntimeException("Không load được FXML: " + fxmlPath, ex);
        }
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    /**
     * Điều hướng đến page. Tự động dừng poll của page cũ, kích hoạt page mới.
     */
    public void navigateTo(String page) {
        // Dừng poll của page cũ
        if (PAGE_STATUS.equals(currentPage) && !PAGE_STATUS.equals(page)) {
            PollManagerFx.getInstance().unregister("order_status_" + tableId);
        }
        if (PAGE_WAITING.equals(currentPage) && !PAGE_WAITING.equals(page)) {
            PollManagerFx.getInstance().unregister("order_waiting_" + tableId);
        }

        currentPage = page;

        // Show/hide
        pages.forEach((k, node) -> node.setVisible(k.equals(page)));

        // Kích hoạt controller
        BasePageController ctrl = controllers.get(page);
        if (ctrl != null) ctrl.onNavigatedTo();

        // WS subscription (setupWsSubscription) đảm nhận refresh —
        // không cần PollManagerFx.register() cho order_status / order_waiting nữa.
    }

    // ── Window lifecycle ───────────────────────────────────────────────────────

    private void setupCloseHandler() {
        setOnCloseRequest(e -> closeWithCleanup());
    }

    /**
     * Đóng màn hình tablet kèm dọn dẹp: cập nhật trạng thái bàn về AVAILABLE
     * và huỷ WS/poll để tránh memory leak.
     * <p>
     * Gọi trực tiếp để đóng programmatically (ví dụ: nút đóng ẩn dành cho nhân viên).
     * Cũng được gọi tự động khi nhấn nút X hoặc Alt+F4.
     */
    public void closeWithCleanup() {
        // 1. Cập nhật trạng thái bàn trên background thread
        //    - Thanh toán hoàn tất → DIRTY (cần dọn, nhân viên phục vụ sẽ thấy)
        //    - Đóng không qua thanh toán (bấm X) → AVAILABLE (Rảnh)
        final boolean wasPaid = paymentCompleted;
        new Thread(() -> {
            try {
                long restaurantId = AppSession.getInstance().getRestaurantId();
                com.restaurant.dao.TabletOrderDAO dao =
                        new com.restaurant.dao.TabletOrderDAO(restaurantId);
                if (wasPaid) {
                    dao.markTableDirty(tableId);   // Bàn cần dọn
                } else {
                    dao.markTableAvailable(tableId); // Tablet đóng không thanh toán
                }
            } catch (Exception e) {
                System.err.println("[TableOrderStage] closeWithCleanup updateStatus lỗi: "
                        + e.getMessage());
            }
        }, "tablet-close-cleanup").start();

        // 2. Huỷ WS handler và poll
        cleanupPolls();

        // 3. Đóng stage
        close();
    }

    public void cleanupPolls() {
        // Xoá WS handler của stage này để không nhận event sau khi đóng
        if (cancelWsHandler != null) { cancelWsHandler.run(); cancelWsHandler = null; }

        // Vẫn unregister PollManagerFx để tránh lỗi nếu còn reference cũ
        PollManagerFx.getInstance().unregister("order_status_"  + tableId);
        PollManagerFx.getInstance().unregister("order_waiting_" + tableId);
    }

    // ── WebSocket subscription (thay polling order_status / order_waiting) ─────

    /**
     * Subscribe WS topics {@link WsTopic#ORDERS} và topic bàn cụ thể
     * ({@code WsTopic.forTable(tableId)}) rồi đăng ký handler dispatch
     * đến đúng controller tuỳ theo page đang hiển thị.
     *
     * <ul>
     *   <li>STATUS page  → {@link StatusPageController#refreshTable()}</li>
     *   <li>WAITING page → {@link WaitingPageController#checkOrderCompleted()}</li>
     * </ul>
     *
     * <p>Handler luôn được gọi trên FX Application Thread (đảm bảo bởi
     * {@link RestaurantEventClient}) nên không cần thêm {@code Platform.runLater()}.</p>
     *
     * <p>Để huỷ đăng ký, gọi {@link #cleanupPolls()} — sẽ đặt handler về {@code null}.</p>
     */
    private void setupWsSubscription() {
        RestaurantEventClient ws = RestaurantEventClient.getInstance();

        // Subscribe ORDERS (trạng thái order_items) và topic riêng của bàn
        ws.subscribe(WsTopic.ORDERS, wsTableTopic);

        // Handler dispatch: chỉ xử lý event liên quan đến đơn/bàn này
        cancelWsHandler = ws.addEventHandler(event -> {
            if (event == null) return;
            String topic = event.getTopic();
            if (!WsTopic.ORDERS.equals(topic) && !wsTableTopic.equals(topic)) return;

            if (PAGE_STATUS.equals(currentPage)) {
                getStatusController().refreshTable();
            } else if (PAGE_WAITING.equals(currentPage)) {
                getWaitingController().checkOrderCompleted();
            }
        });
    }

    // ── Order logic (gọi từ CartPageController) ────────────────────────────────

    /**
     * Gửi giỏ hàng lên server. Chạy DAO trên background thread, callback
     * kết quả trên FX thread.
     */
    public void sendOrder(Runnable onSuccess, Runnable onError) {
        final List<CartEntry>     snapshot = new ArrayList<>(cartItems);
        final int                 round    = currentRound;

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                List<OrderDAO.CartEntry> entries = new ArrayList<>();
                for (CartEntry ci : snapshot) {
                    entries.add(new OrderDAO.CartEntry(
                            ci.menuItemId, ci.quantity, ci.unitPrice));
                }
                return orderDAO.addOrderItems(orderId, entries, round);
            }
        };

        task.setOnSucceeded(e -> {
            boolean ok = task.getValue();
            if (ok) {
                currentRound++;
                cartItems.clear();
                com.restaurant.session.AuditLogger.getInstance()
                    .logSendOrder(orderId, tableId, snapshot.size(), round);

                // Broadcast push đến tất cả màn hình nhân viên trong nhà hàng.
                //
                // Multi-instance: nếu đây là tablet (Instance 2), WS server không
                // bind được port → srv.isRunning() == false → srv.broadcast() sẽ
                // gửi vào local server không có subscriber → không ai nhận.
                // Cần relay qua client đến Instance 1's server (publishToServer).
                try {
                    long rid = AppSession.getInstance().getRestaurantId();
                    RestaurantEventServer srv = RestaurantEventServer.getInstance();
                    RestaurantEventClient cli = RestaurantEventClient.getInstance();
                    final boolean serverDown  = !srv.isRunning();

                    WsEvent kitchenEvt = WsEvent.of(WsTopic.KITCHEN, rid);
                    WsEvent ordersEvt  = WsEvent.of(WsTopic.ORDERS,  rid);
                    WsEvent badgeEvt   = WsEvent.of(WsTopic.BADGE,   rid);
                    WsEvent tableEvt   = WsEvent.of(
                            WsTopic.forTable(Integer.parseInt(tableId)), rid);

                    // Broadcast locally (hiệu quả khi instance này là server)
                    srv.broadcast(kitchenEvt);
                    srv.broadcast(ordersEvt);
                    srv.broadcast(badgeEvt);
                    srv.broadcast(tableEvt);

                    // Relay sang server thật nếu instance này không phải server
                    if (serverDown) {
                        cli.publishToServer(kitchenEvt);
                        cli.publishToServer(ordersEvt);
                        cli.publishToServer(badgeEvt);
                        cli.publishToServer(tableEvt);
                    }
                } catch (Exception wsEx) {
                    System.err.println("[TableOrderStage] Broadcast lỗi: " + wsEx.getMessage());
                }

                onSuccess.run();
            } else {
                onError.run();
            }
        });

        task.setOnFailed(e -> {
            System.err.println("[TableOrderStage] sendOrder lỗi: "
                    + task.getException().getMessage());
            onError.run();
        });

        new Thread(task, "send-order").start();
    }

    /**
     * Gửi yêu cầu thanh toán từ tablet — chuyển đơn sang PAYMENT_REQUESTED.
     * Chạy trên background thread để không block JavaFX thread.
     *
     * @param method     "cash" | "transfer" | "card" | "momo" | "vnpay"
     * @param cashAmount số tiền khách đưa (chỉ có nghĩa khi method="cash"); có thể rỗng
     */
    public void submitPaymentRequest(String method, String cashAmount) {
        // Chuẩn hoá method → DB format
        String dbMethod;
        switch (method == null ? "" : method.toLowerCase()) {
            case "cash":     dbMethod = "CASH";          break;
            case "transfer": dbMethod = "BANK_TRANSFER"; break;
            case "card":     dbMethod = "CARD";          break;
            case "momo":     dbMethod = "MOMO";          break;
            case "vnpay":    dbMethod = "VNPAY";         break;
            default:         dbMethod = "CASH";
        }

        final String finalMethod = dbMethod;
        final String oid = orderId;

        javafx.concurrent.Task<Boolean> task = new javafx.concurrent.Task<>() {
            @Override protected Boolean call() {
                return orderDAO.requestPayment(oid, finalMethod);
            }
        };
        task.setOnSucceeded(e -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                com.restaurant.session.AuditLogger.getInstance()
                    .logRequestPayment(oid, tableId, finalMethod);
                // Broadcast để CashierController nhận ngay yêu cầu thanh toán mới
                try {
                    long rid = AppSession.getInstance().getRestaurantId();
                    RestaurantEventServer srv = RestaurantEventServer.getInstance();
                    RestaurantEventClient cli = RestaurantEventClient.getInstance();
                    WsEvent ordersEvt = WsEvent.of(WsTopic.ORDERS, rid);
                    WsEvent badgeEvt  = WsEvent.of(WsTopic.BADGE,  rid);
                    if (srv.isRunning()) {
                        srv.broadcast(ordersEvt);
                        srv.broadcast(badgeEvt);
                    } else {
                        cli.publishToServer(ordersEvt);
                        cli.publishToServer(badgeEvt);
                    }
                } catch (Exception wsEx) {
                    System.err.println("[TableOrderStage] broadcast payment-request lỗi: " + wsEx.getMessage());
                }
            } else {
                System.err.println("[TableOrderStage] requestPayment không update được row — orderId=" + oid);
            }
        });
        task.setOnFailed(e ->
            System.err.println("[TableOrderStage] submitPaymentRequest lỗi: "
                + task.getException().getMessage())
        );
        new Thread(task, "request-payment").start();
    }

    /**
     * Load danh sách món ăn từ DB (background thread).
     */
    public void loadMenuItems(java.util.function.Consumer<List<MenuItem>> onDone,
                              Runnable onError) {
        Task<List<MenuItem>> task = new Task<>() {
            @Override protected List<MenuItem> call() {
                return menuItemDAO.getAll();
            }
        };
        task.setOnSucceeded(e -> onDone.accept(task.getValue()));
        task.setOnFailed(e -> {
            System.err.println("[TableOrderStage] loadMenuItems lỗi: "
                    + task.getException().getMessage());
            onError.run();
        });
        new Thread(task, "load-menu").start();
    }

    /**
     * Load trạng thái các món của đơn hiện tại.
     */
    public void loadOrderItems(java.util.function.Consumer<List<Order.OrderItem>> onDone) {
        Task<List<Order.OrderItem>> task = new Task<>() {
            @Override protected List<Order.OrderItem> call() {
                return orderDAO.getItemsWithStatus(orderId);
            }
        };
        task.setOnSucceeded(e -> onDone.accept(task.getValue()));
        task.setOnFailed(e ->
            System.err.println("[TableOrderStage] loadOrderItems lỗi: "
                    + task.getException().getMessage()));
        new Thread(task, "load-status").start();
    }

    /**
     * Kiểm tra đơn đã hoàn tất chưa (dùng trong WaitingPage).
     */
    public void checkOrderActive(java.util.function.Consumer<Boolean> onDone) {
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() {
                Order active = orderDAO.getActiveOrderByTable(tableId);
                return active == null; // true = đã hoàn tất
            }
        };
        task.setOnSucceeded(e -> onDone.accept(task.getValue()));
        task.setOnFailed(e ->
            System.err.println("[TableOrderStage] checkOrderActive lỗi: "
                    + task.getException().getMessage()));
        new Thread(task, "check-order").start();
    }

    // ── Getters (controllers cần) ──────────────────────────────────────────────

    public String getTableId()       { return tableId;        }
    public String getOrderId()       { return orderId;        }
    public String getTableName()     { return tableName;      }
    public String getRestaurantName(){ return restaurantName; }
    public String getCurrentPage()   { return currentPage;    }
    public List<CartEntry> getCartItems() { return cartItems; }

    public static String formatPrice(double v) {
        return PRICE_FMT.format((long) v);
    }

    @SuppressWarnings("unchecked")
    private <T extends BasePageController> T ctrl(String key) {
        return (T) controllers.get(key);
    }

    public MenuPageController    getMenuController()    { return ctrl(PAGE_MENU);    }
    public CartPageController    getCartController()    { return ctrl(PAGE_CART);    }
    public StatusPageController  getStatusController()  { return ctrl(PAGE_STATUS);  }
    public PaymentPageController getPaymentController() { return ctrl(PAGE_PAYMENT); }
    public WaitingPageController getWaitingController() { return ctrl(PAGE_WAITING); }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String loadRestaurantName() {
        try {
            var r = com.restaurant.data.DataManager.getInstance().getMyRestaurant();
            return (r != null && r.getName() != null && !r.getName().isBlank())
                    ? r.getName() : "Nhà hàng";
        } catch (Exception e) {
            return "Nhà hàng";
        }
    }
}