package com.restaurant.ui;

import java.util.HashMap;
import java.util.Map;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.OrderDAO;
import com.restaurant.session.AppSession;

/**
 * PollManager — Phase 7A: Quản lý tập trung tất cả javax.swing.Timer.
 *
 * <p>Singleton. Mọi panel/frame có polling đều register task vào đây thay vì
 * tự tạo Timer riêng. Khi {@link #stopAll()} được gọi (e.g. khi logout), toàn
 * bộ timer dừng ngay lập tức — không còn tình huống timer tiếp tục chạy sau
 * khi người dùng đã đăng xuất và gây lỗi DB / memory leak.
 *
 * <h3>Key convention</h3>
 * <pre>
 *   "kitchen"            – KitchenPanel
 *   "waiter"             – WaiterServicePanel
 *   "tableorder_{id}"    – TableOrderFrame cho bàn có id tương ứng
 * </pre>
 *
 * <h3>Thread safety</h3>
 * Tất cả public method phải gọi từ EDT (Swing Event Dispatch Thread).
 * Nếu gọi từ thread khác, dùng {@link SwingUtilities#invokeLater}.
 *
 * <h3>Phase 7A hardening</h3>
 * <ul>
 *   <li>{@link #register} bọc task trong try-catch — exception không làm
 *       dừng timer; chỉ log lỗi và tiếp tục poll lần sau.</li>
 *   <li>Guard "đã đăng ký rồi → bỏ qua" tránh double-register.</li>
 *   <li>Thêm {@link #unregister(String)} và {@link #activeCount()}.</li>
 * </ul>
 */
public final class PollManager {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static PollManager instance;
    private KitchenDAO kitchenDAO = new KitchenDAO();
    private OrderDAO   orderDAO   = new OrderDAO();
    private MainFrame  mainFrame;  // inject qua constructor hoặc getter

    private void refreshBadges() {
        long restaurantId = AppSession.getInstance().getRestaurantId();

        int pendingKitchen  = kitchenDAO.getPendingCount(restaurantId);
        int readyWaiter     = kitchenDAO.getReadyCount(restaurantId);
        int paymentRequests = orderDAO.getPaymentRequestedCount(restaurantId);

        SwingUtilities.invokeLater(() -> {
            if (mainFrame.getBtnKitchen()  != null) mainFrame.getBtnKitchen().setBadgeCount(pendingKitchen);
            if (mainFrame.getBtnWaiter()   != null) mainFrame.getBtnWaiter().setBadgeCount(readyWaiter);
            if (mainFrame.getBtnCashier()  != null) mainFrame.getBtnCashier().setBadgeCount(paymentRequests);
        });
    }

    // PollManager.getInstance().register("home_stats", () -> {
    //     loadDashboardData();  // logic cũ
    //     refreshBadges();      // thêm mới
    // }, 10_000);

    private PollManager() {}

    /** Trả về instance duy nhất của PollManager. */
    public static PollManager getInstance() {
        if (instance == null) {
            instance = new PollManager();
        }
        return instance;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Map từ key định danh → Timer đang chạy. */
    private final Map<String, Timer> timers = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Đăng ký một polling task với key định danh.
     *
     * <p><b>Phase 7A changes:</b>
     * <ul>
     *   <li>Nếu {@code key} đã tồn tại → bỏ qua (không tạo timer mới, không
     *       dừng timer cũ). Caller phải gọi {@link #unregister(String)} trước
     *       nếu muốn thay thế.</li>
     *   <li>Task được bọc trong try-catch: exception chỉ được log, timer vẫn
     *       tiếp tục chạy ở lần poll kế tiếp.</li>
     *   <li>{@code initialDelay = 0} → chạy ngay lần đầu không cần chờ.</li>
     * </ul>
     *
     * @param key        Định danh duy nhất (e.g. {@code "kitchen"})
     * @param task       Runnable chạy trên EDT mỗi {@code intervalMs} ms
     * @param intervalMs Khoảng thời gian giữa các lần chạy (milliseconds)
     */
    public void register(String key, Runnable task, int intervalMs) {
        assertEDT("register");

        // Guard: đã đăng ký rồi → bỏ qua, tránh double-register
        if (timers.containsKey(key)) {
            System.out.printf(
                "[PollManager] register('%s') bị bỏ qua — key đã tồn tại.%n", key);
            return;
        }

        Timer t = new Timer(intervalMs, e -> {
            try {
                task.run();
            } catch (Exception ex) {
                // Log lỗi nhưng KHÔNG dừng timer — tiếp tục poll lần sau
                System.err.printf(
                    "[PollManager] Task '%s' lỗi: %s – %s%n",
                    key, ex.getClass().getSimpleName(), ex.getMessage());
            }
        });
        t.setInitialDelay(0);   // chạy ngay lần đầu
        t.setRepeats(true);
        t.start();
        timers.put(key, t);

        System.out.printf("[PollManager] Registered timer '%s' every %dms%n", key, intervalMs);
    }

    /**
     * Dừng và huỷ timer theo {@code key}.
     * Không-op nếu key không tồn tại.
     *
     * @param key Định danh đã dùng khi {@link #register}
     */
    public void unregister(String key) {
        assertEDT("unregister");

        Timer t = timers.remove(key);
        if (t != null) {
            t.stop();
            System.out.printf("[PollManager] Unregistered timer '%s'%n", key);
        }
    }

    /**
     * Dừng và huỷ <b>toàn bộ</b> timer đang đăng ký.
     *
     * <p>Được gọi từ {@code MainFrame.handleLogout()} ngay khi phiên kết thúc.
     * Sau khi gọi, map sẽ rỗng — sẵn sàng cho phiên đăng nhập tiếp theo.
     */
    public void stopAll() {
        assertEDT("stopAll");

        int count = timers.size();
        timers.values().forEach(t -> {
            if (t.isRunning()) t.stop();
        });
        timers.clear();

        System.out.printf("[PollManager] stopAll — đã dừng %d timer(s)%n", count);
    }

    /**
     * Trả về số lượng timer đang chạy.
     * Hữu ích cho unit test và debug.
     */
    public int activeCount() {
        return (int) timers.values().stream().filter(Timer::isRunning).count();
    }

    /**
     * Trả về {@code true} nếu key đang được đăng ký và timer đang chạy.
     */
    public boolean isRunning(String key) {
        Timer t = timers.get(key);
        return t != null && t.isRunning();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Cảnh báo nếu không gọi từ EDT (không throw — chỉ log để không crash
     * production, nhưng giúp dev phát hiện lỗi sớm).
     */
    private static void assertEDT(String method) {
        if (!SwingUtilities.isEventDispatchThread()) {
            System.err.printf(
                "[PollManager] WARN: %s() được gọi ngoài EDT! " +
                "Hãy dùng SwingUtilities.invokeLater().%n", method);
        }
    }

    public static void setInstance(PollManager instance) {
        PollManager.instance = instance;
    }

    public KitchenDAO getKitchenDAO() {
        return kitchenDAO;
    }

    public void setKitchenDAO(KitchenDAO kitchenDAO) {
        this.kitchenDAO = kitchenDAO;
    }

    public OrderDAO getOrderDAO() {
        return orderDAO;
    }

    public void setOrderDAO(OrderDAO orderDAO) {
        this.orderDAO = orderDAO;
    }

    public MainFrame getMainFrame() {
        return mainFrame;
    }

    public void setMainFrame(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    public Map<String, Timer> getTimers() {
        return timers;
    }
}