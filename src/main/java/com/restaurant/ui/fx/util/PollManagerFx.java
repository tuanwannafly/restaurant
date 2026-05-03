package com.restaurant.ui.fx.util;

import java.util.HashMap;
import java.util.Map;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.OrderDAO;
import com.restaurant.session.AppSession;

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
 * <h3>Key convention (same as Swing PollManager)</h3>
 * <pre>
 *   "kitchen"            – KitchenPanel
 *   "waiter"             – WaiterServicePanel
 *   "home_stats"         – HomePanel + badge refresh
 *   "tableorder_{id}"    – TableOrderView for table {id}
 * </pre>
 *
 * <h3>Thread contract</h3>
 * All public methods MUST be called on the FX Application Thread.
 * Task {@link Runnable}s are executed on the FX Application Thread.
 * For any task that touches the DB, wrap the DB call with
 * {@link FxUtils#runAsync} inside the task body to keep the UI
 * responsive:
 * <pre>{@code
 * PollManagerFx.getInstance().register("kitchen", () ->
 *     FxUtils.runAsync(
 *         () -> kitchenDAO.getPendingItems(restaurantId),
 *         items -> kitchenController.refresh(items)
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
         * @param pendingKitchen  items waiting in kitchen queue
         * @param readyWaiter     dishes ready for the waiter to serve
         * @param paymentRequests tables requesting the bill
         */
        void update(int pendingKitchen, int readyWaiter, int paymentRequests);
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
     * @param key        unique identifier (e.g. {@code "kitchen"})
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
     * Registers a badge-refresh polling task for the main navigation bar.
     *
     * <p>Replaces the inline snippet that was commented out in the Swing
     * {@code PollManager}:
     * <pre>
     *   PollManager.getInstance().register("home_stats", () -> {
     *       loadDashboardData();
     *       refreshBadges();
     *   }, 10_000);
     * </pre>
     *
     * <p>DB work is dispatched to a background thread; badge labels are
     * updated back on the FX thread via the injected {@link BadgeUpdater}.
     *
     * @param intervalMs polling interval in milliseconds (suggest 10 000)
     */
    public void registerBadgeRefresh(int intervalMs) {
        register("badge_refresh", this::refreshBadgesAsync, intervalMs);
    }

    /**
     * Executes the DB queries on a background thread and delivers counts
     * to the BadgeUpdater on the FX Application Thread.
     */
    private void refreshBadgesAsync() {
        if (badgeUpdater == null) return;

        long restaurantId = AppSession.getInstance().getRestaurantId();

        FxUtils.runAsync(
            () -> {
                int pending  = kitchenDAO.getPendingCount(restaurantId);
                int ready    = kitchenDAO.getReadyCount(restaurantId);
                int payment  = orderDAO.getPaymentRequestedCount(restaurantId);
                return new int[]{ pending, ready, payment };
            },
            counts -> badgeUpdater.update(counts[0], counts[1], counts[2]),
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

    public BadgeUpdater getBadgeUpdater() { return badgeUpdater; }
    public void setBadgeUpdater(BadgeUpdater badgeUpdater) {
        this.badgeUpdater = badgeUpdater;
    }

    /** Exposed for unit tests only — read-only snapshot of the key set. */
    public java.util.Set<String> registeredKeys() {
        return java.util.Collections.unmodifiableSet(timelines.keySet());
    }
}