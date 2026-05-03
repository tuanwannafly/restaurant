package com.restaurant.ui;

import com.restaurant.dao.MenuItemDAO;
import com.restaurant.dao.OrderDAO;
import com.restaurant.model.MenuItem;
import com.restaurant.model.Order;
import com.restaurant.ui.fx.controller.*;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;

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
    }

    // ── Constructor ────────────────────────────────────────────────────────────

    public TableOrderStage(String tableId, String orderId, String tableName) {
        this.tableId        = tableId;
        this.orderId        = orderId;
        this.tableName      = tableName;
        this.restaurantName = loadRestaurantName();

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
        loadPage(PAGE_MENU,    "/com/restaurant/ui/fxml/MenuPageView.fxml");
        loadPage(PAGE_CART,    "/com/restaurant/ui/fxml/CartPageView.fxml");
        loadPage(PAGE_STATUS,  "/com/restaurant/ui/fxml/StatusPageView.fxml");
        loadPage(PAGE_PAYMENT, "/com/restaurant/ui/fxml/PaymentPageView.fxml");
        loadPage(PAGE_WAITING, "/com/restaurant/ui/fxml/WaitingPageView.fxml");

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

        // Bắt đầu poll cho page mới
        if (PAGE_STATUS.equals(page)) {
            PollManagerFx.getInstance().register(
                    "order_status_" + tableId,
                    () -> getStatusController().refreshTable(),
                    5_000);
        }
        if (PAGE_WAITING.equals(page)) {
            PollManagerFx.getInstance().register(
                    "order_waiting_" + tableId,
                    () -> getWaitingController().checkOrderCompleted(),
                    5_000);
        }
    }

    // ── Window lifecycle ───────────────────────────────────────────────────────

    private void setupCloseHandler() {
        setOnCloseRequest(e -> cleanupPolls());
    }

    public void cleanupPolls() {
        PollManagerFx.getInstance().unregister("order_status_"  + tableId);
        PollManagerFx.getInstance().unregister("order_waiting_" + tableId);
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
     * Gửi yêu cầu thanh toán (log + gọi DAO khi sẵn).
     */
    public void submitPaymentRequest(String method, String cashAmount) {
        System.out.printf("[TableOrderStage] Payment: orderId=%s method=%s%s%n",
                orderId, method,
                "cash".equals(method) ? " amount=" + cashAmount : "");
        // TODO: gọi orderDAO.requestPayment(orderId, method, cashAmount)
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
