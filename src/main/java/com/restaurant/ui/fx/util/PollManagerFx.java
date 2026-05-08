package com.restaurant.ui.fx.util;

import java.util.HashMap;
import java.util.Map;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.OrderDAO;
import com.restaurant.dao.RestaurantRequestDAO;
import com.restaurant.session.AppSession;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

/**
 * PollManagerFx — JavaFX replacement for the Swing {@code PollManager}.
 *
 * <h3>Why Timeline instead of javax.swing.Timer?</h3>
 * {@link javafx.animation.Timeline} fires its keyframes on the
 * <em>JavaFX Application Thread</em>, just as {@code javax.swing.Timer}
 * fires on the <em>Event Dispatch Thread</em>.  The semantics are
 * identical: all task {@link Runnable}s run on the UI thread, so
 * they can safely update {@code ObservableList}, {@code Property}, or
 * any other JavaFX node without an explicit {@link Platform#runLater}.
 *
 * <h3>API surface — identical to the Swing version</h3>
 * <pre>
 *   register(key, task, intervalMs)  // start polling
 *   unregister(key)                  // stop one timer
 *   stopAll()                        // stop all (called on logout)
 *   isRunning(key)                   // query
 *   activeCount()                    // debug / test
 * </pre>
 *
 * <h3>Key convention (active keys)</h3>
 * <pre>
 *   "waiter"             – WaiterServicePanel
 *   "home_stats"         – HomePanel stats
 *   "tableorder_{id}"    – TableOrderView for table {id}
 *
 *   ── Migrated to WebSocket push (no longer polled) ───────────────────────
 *   "kitchen"            – replaced by WsTopic.KITCHEN
 *   "badge_refresh"      – replaced by WsTopic.BADGE  (see registerBadgeRefresh)
 *   "request_list_refresh" – replaced by WsTopic.REQUEST_LIST
 * </pre>
 *
 * <h3>Thread contract</h3>
 * All public methods MUST be called on the FX Application Thread.
 * Task {@link Runnable}s are executed on the FX Application Thread.
 * For any task that touches the DB, wrap the DB call with
 * {@link FxUtils#runAsync} inside the task body to keep the UI
 * responsive:
 * <pre>{@code
 * PollManagerFx.getInstance().register("waiter", () ->
 *     FxUtils.runAsync(
 *         () -> waiterDAO.getPendingRequests(restaurantId),
 *         items -> waiterController.refresh(items)
 *     ), 5_000);
 * }</pre>
 *
 * <h3>Phase 7A hardening — carried forward from Swing version</h3>
 * <ul>
 *   <li>Tasks are wrapped in try-catch; exceptions are logged but do
 *       <b>not</b> stop the Timeline.</li>
 *   <li>Double-register guard: registering the same key twice is a
 *       no-op (log warning + return).</li>
 *   <li>Initial immediate fire: the task runs once immediately on
 *       registration (initialDelay = 0), then repeats every
 *       {@code intervalMs}.</li>
 * </ul>
 */
public final class PollManagerFx {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static PollManagerFx instance;

    /** Returns the process-wide singleton. */
    public static PollManagerFx getInstance() {
        if (instance == null) {
            instance = new PollManagerFx();
        }
        return instance;
    }

    // Package-private for unit testing (inject a mock)
    static void setInstance(PollManagerFx mock) {
        instance = mock;
    }

    private PollManagerFx() {}

    // ── State ─────────────────────────────────────────────────────────────────

    /** Live registry: key → running Timeline */
    private final Map<String, Timeline> timelines = new HashMap<>();

    // ── Injected collaborators (for badge refresh) ────────────────────────────

    private KitchenDAO kitchenDAO = new KitchenDAO();
    private OrderDAO   orderDAO   = new OrderDAO();
    private RestaurantRequestDAO requestDAO = new RestaurantRequestDAO();

    /**
     * Reference to the root controller that owns the nav badge labels.
     * Set once from {@code MainController} after scene creation.
     */
    private BadgeUpdater badgeUpdater;

    /**
     * Callback interface so PollManagerFx does not depend on a concrete
     * controller class — keeps the dependency direction clean.
     */
    @FunctionalInterface
    public interface BadgeUpdater {
        /**
         * @param pendingKitchen   items waiting in kitchen queue
         * @param readyWaiter      dishes ready for the waiter to serve
         * @param paymentRequests  tables requesting the bill
         * @param pendingRequests  restaurant registration requests awaiting review (SUPER_ADMIN)
         */
        void update(int pendingKitchen, int readyWaiter, int paymentRequests, int pendingRequests);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Registers a polling task identified by {@code key}.
     *
     * <p>The task executes immediately (delay = 0), then every
     * {@code intervalMs} milliseconds on the FX Application Thread.
     *
     * <p>If {@code key} is already registered, the call is ignored (no-op).
     * Call {@link #unregister(String)} first to replace a task.
     *
     * @param key        unique identifier (e.g. {@code "home_stats"})
     * @param task       runnable executed on the FX Application Thread
     * @param intervalMs repeat interval in milliseconds (≥ 1 000 recommended)
     */
    public void register(String key, Runnable task, int intervalMs) {
        assertFxThread("register");

        if (timelines.containsKey(key)) {
            System.out.printf(
                "[PollManagerFx] register('%s') ignored — key already active.%n", key);
            return;
        }

        // Wrap task in try-catch so a crash doesn't kill the Timeline
        Runnable safeTask = () -> {
            try {
                task.run();
            } catch (Exception ex) {
                System.err.printf(
                    "[PollManagerFx] Task '%s' threw %s: %s%n",
                    key, ex.getClass().getSimpleName(), ex.getMessage());
            }
        };

        // Build an INDEFINITE Timeline that fires every intervalMs
        Timeline tl = new Timeline(
            new KeyFrame(Duration.millis(intervalMs), e -> safeTask.run())
        );
        tl.setCycleCount(Timeline.INDEFINITE);

        // Fire immediately (initial delay = 0): run the task right now on EDT
        // then let the Timeline handle subsequent firings.
        safeTask.run();

        tl.play();
        timelines.put(key, tl);

        System.out.printf(
            "[PollManagerFx] Registered '%s' every %d ms%n", key, intervalMs);
    }

    /**
     * Stops and removes the Timeline for {@code key}.
     * No-op if key is not registered.
     *
     * @param key identifier used in {@link #register}
     */
    public void unregister(String key) {
        assertFxThread("unregister");

        Timeline tl = timelines.remove(key);
        if (tl != null) {
            tl.stop();
            System.out.printf("[PollManagerFx] Unregistered '%s'%n", key);
        }
    }

    /**
     * Stops <b>all</b> registered timelines and clears the registry.
     *
     * <p>Called from {@code MainController.handleLogout()} — equivalent to
     * the Swing {@code PollManager.stopAll()}.  After this call the
     * instance is ready to accept new registrations for the next login
     * session.
     */
    public void stopAll() {
        assertFxThread("stopAll");

        int count = timelines.size();
        timelines.values().forEach(Timeline::stop);
        timelines.clear();
        System.out.printf("[PollManagerFx] stopAll — stopped %d timeline(s)%n", count);
    }

    /**
     * Returns the number of currently running timelines.
     * Useful in tests and debug tooling.
     */
    public int activeCount() {
        return (int) timelines.values().stream()
            .filter(tl -> tl.getStatus() == Timeline.Status.RUNNING)
            .count();
    }

    /**
     * Returns {@code true} if the given key is registered and the
     * associated Timeline is currently running.
     */
    public boolean isRunning(String key) {
        Timeline tl = timelines.get(key);
        return tl != null && tl.getStatus() == Timeline.Status.RUNNING;
    }

    // =========================================================================
    // Badge refresh (mirrors PollManager.refreshBadges)
    // =========================================================================

    /**
     * Registers a periodic badge-refresh poll as a <b>safety-net fallback</b>
     * when WebSocket / Oracle DCN is unavailable.
     *
     * <p><b>Deprecated:</b> Under normal operation badges are driven by
     * {@link com.restaurant.websocket.WsTopic#BADGE} WebSocket push events and
     * this method is never called.  It exists solely as a fallback invoked by
     * {@link com.restaurant.websocket.OracleDcnBridge} when DCN cannot start
     * (missing {@code CHANGE NOTIFICATION} privilege or Oracle &lt; 11g).</p>
     *
     * <p>Double-register is guarded — calling this more than once is a no-op.</p>
     *
     * @param intervalMs polling interval in milliseconds (suggest {@code 30_000}
     *                   when used as a DCN fallback)
     */
    @Deprecated
    public void registerBadgeRefresh(int intervalMs) {
        // Only register if not already running — safe to call from Platform.runLater()
        if (!timelines.containsKey("badge_refresh")) {
            register("badge_refresh", this::refreshBadgesAsync, intervalMs);
            System.out.printf(
                "[PollManagerFx] Fallback: badge_refresh poll started every %d ms " +
                "(WebSocket/DCN unavailable).%n", intervalMs);
        }
    }

    /**
     * Executes the badge DB queries on a background thread and delivers counts
     * to the {@link BadgeUpdater} on the FX Application Thread.
     *
     * <p>Phase WS: visibility elevated từ {@code private} → {@code public} để
     * {@code Main.openMainView()} gọi trực tiếp khi nhận WebSocket push event,
     * thay thế cơ chế polling định kỳ của {@link #registerBadgeRefresh(int)}.
     *
     * <p>Thread-safe: an toàn khi gọi từ FX Application Thread, hoặc từ callback
     * của {@link com.restaurant.websocket.RestaurantEventClient#onEvent} vốn đã
     * được dispatch về FX thread qua {@code Platform.runLater}.
     */
    public void refreshBadgesAsync() {
        if (badgeUpdater == null) return;

        long restaurantId = AppSession.getInstance().getRestaurantId();
        boolean isSuperAdmin = com.restaurant.session.RbacGuard.getInstance().isSuperAdmin();

        FxUtils.runAsync(
            () -> {
                int pending  = kitchenDAO.getPendingCount(restaurantId);
                int ready    = kitchenDAO.getReadyCount(restaurantId);
                int payment  = orderDAO.getPaymentRequestedCount(restaurantId);
                // Chỉ query pending requests nếu là SUPER_ADMIN (tránh lỗi SecurityException)
                int pendingReq = 0;
                if (isSuperAdmin) {
                    try {
                        pendingReq = requestDAO.countByStatus("PENDING");
                    } catch (Exception ex) {
                        System.err.println("[PollManagerFx] pendingRequests query error: "
                                + ex.getMessage());
                    }
                }
                return new int[]{ pending, ready, payment, pendingReq };
            },
            counts -> badgeUpdater.update(counts[0], counts[1], counts[2], counts[3]),
            err -> System.err.println("[PollManagerFx] Badge refresh error: "
                    + err.getMessage())
        );
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static void assertFxThread(String method) {
        if (!Platform.isFxApplicationThread()) {
            System.err.printf(
                "[PollManagerFx] WARN: %s() called from non-FX thread (%s). " +
                "Use Platform.runLater().%n",
                method, Thread.currentThread().getName());
        }
    }

    // =========================================================================
    // Getters / Setters (for testing and dependency injection)
    // =========================================================================

    public KitchenDAO getKitchenDAO() { return kitchenDAO; }
    public void setKitchenDAO(KitchenDAO kitchenDAO) { this.kitchenDAO = kitchenDAO; }

    public OrderDAO getOrderDAO() { return orderDAO; }
    public void setOrderDAO(OrderDAO orderDAO) { this.orderDAO = orderDAO; }

    public RestaurantRequestDAO getRequestDAO() { return requestDAO; }
    public void setRequestDAO(RestaurantRequestDAO requestDAO) { this.requestDAO = requestDAO; }

    public BadgeUpdater getBadgeUpdater() { return badgeUpdater; }
    public void setBadgeUpdater(BadgeUpdater badgeUpdater) {
        this.badgeUpdater = badgeUpdater;
    }

    /** Exposed for unit tests only — read-only snapshot of the key set. */
    public java.util.Set<String> registeredKeys() {
        return java.util.Collections.unmodifiableSet(timelines.keySet());
    }
}