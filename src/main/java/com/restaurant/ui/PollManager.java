package com.restaurant.ui;

import java.util.HashMap;
import java.util.Map;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

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
 */
public final class PollManager {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static PollManager instance;

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
     * <p>Nếu {@code key} đã tồn tại, timer cũ sẽ bị dừng và thay thế bởi
     * timer mới — tránh chạy đôi. Timer mới được start ngay.
     *
     * @param key        Định danh duy nhất (e.g. {@code "kitchen"})
     * @param task       Runnable chạy trên EDT mỗi {@code intervalMs} ms
     * @param intervalMs Khoảng thời gian giữa các lần chạy (milliseconds)
     */
    public void register(String key, Runnable task, int intervalMs) {
        assertEDT("register");

        // Dừng timer cũ nếu tồn tại
        stopAndRemove(key);

        Timer t = new Timer(intervalMs, e -> task.run());
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

        if (stopAndRemove(key)) {
            System.out.printf("[PollManager] Unregistered timer '%s'%n", key);
        }
    }

    /**
     * Dừng và huỷ <b>toàn bộ</b> timer đang đăng ký.
     *
     * <p>Được gọi từ {@code MainFrame.onLogout()} ngay khi phiên kết thúc.
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
     * Trả về số lượng timer đang được quản lý.
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
     * Dừng và xóa timer theo key.
     * @return {@code true} nếu key tồn tại (và đã bị xóa)
     */
    private boolean stopAndRemove(String key) {
        Timer existing = timers.remove(key);
        if (existing != null) {
            if (existing.isRunning()) existing.stop();
            return true;
        }
        return false;
    }

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
}