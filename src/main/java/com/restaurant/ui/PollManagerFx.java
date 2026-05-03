package com.restaurant.ui;

import javafx.application.Platform;

import java.util.concurrent.*;
import java.util.*;

/**
 * PollManagerFx — Phase 15: Phiên bản JavaFX của PollManager (Swing).
 *
 * <p>Thay thế {@code javax.swing.Timer} bằng {@link ScheduledExecutorService}.
 * Task luôn được chạy trên JavaFX Application Thread qua {@link Platform#runLater}.
 *
 * <h3>Key convention</h3>
 * <pre>
 *   "kitchen"              – KitchenController
 *   "waiter"               – WaiterController
 *   "order_status_{id}"    – StatusPageController cho bàn tương ứng
 *   "order_waiting_{id}"   – WaitingPageController
 * </pre>
 *
 * <h3>Thread safety</h3>
 * Tất cả methods là thread-safe. Task được bọc try-catch — exception không làm
 * dừng poll; chỉ log và tiếp tục lần sau.
 */
public final class PollManagerFx {

    // ── Singleton ──────────────────────────────────────────────────────────────
    private static volatile PollManagerFx instance;

    private PollManagerFx() {}

    public static PollManagerFx getInstance() {
        if (instance == null) {
            synchronized (PollManagerFx.class) {
                if (instance == null) instance = new PollManagerFx();
            }
        }
        return instance;
    }

    // ── State ──────────────────────────────────────────────────────────────────
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(6, r -> {
                Thread t = new Thread(r, "poll-fx");
                t.setDaemon(true);
                return t;
            });

    private final ConcurrentHashMap<String, ScheduledFuture<?>> tasks =
            new ConcurrentHashMap<>();

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Đăng ký polling task. Nếu key đã tồn tại → bỏ qua.
     *
     * @param key        Định danh duy nhất (e.g. {@code "order_status_42"})
     * @param task       Runnable chạy trên FX thread mỗi {@code intervalMs} ms
     * @param intervalMs Chu kỳ poll (milliseconds); initialDelay = 0
     */
    public void register(String key, Runnable task, long intervalMs) {
        if (tasks.containsKey(key)) {
            System.out.printf("[PollManagerFx] register('%s') bỏ qua — key đã tồn tại.%n", key);
            return;
        }

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                Platform.runLater(task);
            } catch (Exception ex) {
                System.err.printf("[PollManagerFx] Task '%s' lỗi: %s – %s%n",
                        key, ex.getClass().getSimpleName(), ex.getMessage());
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        tasks.put(key, future);
        System.out.printf("[PollManagerFx] Registered '%s' every %dms%n", key, intervalMs);
    }

    /**
     * Dừng và huỷ timer theo {@code key}. Không-op nếu key không tồn tại.
     */
    public void unregister(String key) {
        ScheduledFuture<?> f = tasks.remove(key);
        if (f != null) {
            f.cancel(false);
            System.out.printf("[PollManagerFx] Unregistered '%s'%n", key);
        }
    }

    /**
     * Dừng toàn bộ task — gọi khi logout hoặc ứng dụng đóng.
     */
    public void stopAll() {
        int count = tasks.size();
        tasks.values().forEach(f -> f.cancel(false));
        tasks.clear();
        System.out.printf("[PollManagerFx] stopAll — đã dừng %d task(s)%n", count);
    }

    /**
     * Số task đang đăng ký.
     */
    public int activeCount() {
        return (int) tasks.values().stream().filter(f -> !f.isDone()).count();
    }

    public boolean isRunning(String key) {
        ScheduledFuture<?> f = tasks.get(key);
        return f != null && !f.isDone();
    }

    /**
     * Shutdown scheduler — gọi một lần duy nhất khi app thoát hoàn toàn.
     */
    public void shutdown() {
        stopAll();
        scheduler.shutdownNow();
    }
}
