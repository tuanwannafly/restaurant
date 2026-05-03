package com.restaurant.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.restaurant.dao.StatsDAO;
import com.restaurant.session.AppSession;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Controller for {@code HomeView.fxml}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Populate header (greeting, date)</li>
 *   <li>Configure {@link StatCard} instances with icon / colour / title</li>
 *   <li>Fetch stats via {@link StatsDAO#getAdminDashboardStats()} on a daemon thread
 *       using a JavaFX {@link Task}</li>
 *   <li>Auto-refresh every 30 s with a {@link Timeline}</li>
 *   <li>Maintain a countdown label ("Cập nhật sau: Xs")</li>
 *   <li>Populate the recent-activity {@link ListView} using {@link ActivityListCell}</li>
 * </ul>
 *
 * <p>Call {@link #shutdown()} when navigating away to stop background timelines.
 */
public class HomeController {

    // ── FXML injections ────────────────────────────────────────────────────

    @FXML private Label  lblGreeting;
    @FXML private Label  lblSubtitle;
    @FXML private Label  lblRefreshDot;
    @FXML private Label  lblRefreshText;

    @FXML private StatCard cardActive;
    @FXML private StatCard cardNew;
    @FXML private StatCard cardRevenue;
    @FXML private StatCard cardOrders;

    @FXML private ListView<String> activityList;

    // ── Accent colours (matching Swing HomePanel) ─────────────────────────

    private static final Color C_BLUE  = Color.web("#3B82F6");
    private static final Color C_GREEN = Color.web("#10B981");
    private static final Color C_AMBER = Color.web("#F59E0B");
    private static final Color C_RED   = Color.web("#EF4444");

    // ── State ──────────────────────────────────────────────────────────────

    private Timeline                    refreshTimeline;
    private Timeline                    countdownTimeline;
    private int                         secondsLeft = 30;
    private final ObservableList<String> activityItems =
            FXCollections.observableArrayList();

    private static final int REFRESH_SECONDS = 30;

    // =========================================================================
    // Initialize
    // =========================================================================

    @FXML
    private void initialize() {
        // ── Header ────────────────────────────────────────────────────────
        String user  = AppSession.getInstance().getUserName();
        String today = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy, EEEE"));

        lblGreeting.setText("Xin chào, " + user);
        lblSubtitle.setText("Tổng quan hệ thống  —  " + today);

        // ── Stat card configuration ───────────────────────────────────────
        cardActive .configure(StatCard.CardIcon.STORE, "Nhà hàng hoạt động",   C_BLUE);
        cardNew    .configure(StatCard.CardIcon.PLUS,  "Nhà hàng mới hôm nay", C_GREEN);
        cardRevenue.configure(StatCard.CardIcon.COIN,  "Doanh thu hôm nay",    C_AMBER);
        cardOrders .configure(StatCard.CardIcon.BOX,   "Đơn hàng hôm nay",     C_RED);

        // ── Activity list ─────────────────────────────────────────────────
        activityList.setItems(activityItems);
        activityList.setCellFactory(lv -> new ActivityListCell());
        activityItems.add("Đang tải dữ liệu...");

        // ── First data load ───────────────────────────────────────────────
        refreshStats();

        // ── Auto-refresh every 30 s ───────────────────────────────────────
        refreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(REFRESH_SECONDS), e -> refreshStats()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();

        // ── Countdown ticker (1 s) ────────────────────────────────────────
        startCountdown();
    }

    // =========================================================================
    // Refresh
    // =========================================================================

    /**
     * Loads dashboard statistics on a daemon thread, then updates the UI on the
     * JavaFX Application Thread when the {@link Task} succeeds.
     *
     * <p>Also called by the host panel on navigation ("navigate back to home").
     */
    public void refreshStats() {
        setRefreshState(RefreshState.LOADING);

        Task<Map<String, Long>> task = new Task<>() {
            @Override
            protected Map<String, Long> call() {
                return new StatsDAO().getAdminDashboardStats();
            }
        };

        task.setOnSucceeded(event -> {
            Map<String, Long> s = task.getValue();

            cardActive .setValue(String.valueOf(s.get("active_restaurants")));
            cardNew    .setValue(String.valueOf(s.get("new_restaurants")));
            cardRevenue.setValue(formatVnd(s.get("revenue_today")));
            cardOrders .setValue(String.valueOf(s.get("orders_today")));

            populateActivityList(s);

            // Reset countdown after successful load
            secondsLeft = REFRESH_SECONDS;
            setRefreshState(RefreshState.OK);
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            System.err.println("[HomeController] refreshStats lỗi: "
                    + (ex != null ? ex.getMessage() : "unknown"));
            setRefreshState(RefreshState.ERROR);
        });

        Thread thread = new Thread(task, "home-stats-loader");
        thread.setDaemon(true);
        thread.start();
    }

    // =========================================================================
    // Activity list population
    // =========================================================================

    private void populateActivityList(Map<String, Long> s) {
        String ts      = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        long active    = s.getOrDefault("active_restaurants", 0L);
        long newR      = s.getOrDefault("new_restaurants",    0L);
        long orders    = s.getOrDefault("orders_today",       0L);
        long revenue   = s.getOrDefault("revenue_today",      0L);

        activityItems.clear();

        activityItems.add(ts + "  Hệ thống có " + active + " nhà hàng đang hoạt động");

        if (newR > 0) {
            activityItems.add(ts + "  " + newR + " nhà hàng mới được đăng ký hôm nay");
        }

        activityItems.add(ts + "  " + orders + " đơn hàng đã được tạo hôm nay");
        activityItems.add(ts + "  Doanh thu hôm nay: " + formatVnd(revenue));

        if (activityItems.isEmpty()) {
            activityItems.add("Chưa có hoạt động nào trong ngày hôm nay.");
        }
    }

    // =========================================================================
    // Countdown
    // =========================================================================

    private void startCountdown() {
        stopCountdown();
        secondsLeft = REFRESH_SECONDS;

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (secondsLeft > 0) secondsLeft--;
            if (lblRefreshText != null) {
                lblRefreshText.setText(secondsLeft > 0
                        ? "Cập nhật sau: " + secondsLeft + "s"
                        : "Đang tải...");
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    // =========================================================================
    // Refresh-state helpers
    // =========================================================================

    private enum RefreshState { LOADING, OK, ERROR }

    private void setRefreshState(RefreshState state) {
        switch (state) {
            case LOADING -> {
                lblRefreshDot .setStyle("-fx-text-fill: #F59E0B; -fx-font-size: 11;");
                lblRefreshText.setText("Đang tải...");
            }
            case OK -> {
                lblRefreshDot .setStyle("-fx-text-fill: #10B981; -fx-font-size: 11;");
                lblRefreshText.setText("Cập nhật sau: " + REFRESH_SECONDS + "s");
            }
            case ERROR -> {
                lblRefreshDot .setStyle("-fx-text-fill: #EF4444; -fx-font-size: 11;");
                lblRefreshText.setText("Lỗi tải dữ liệu");
            }
        }
    }

    // =========================================================================
    // Navigation hook (call from MainController when switching to home view)
    // =========================================================================

    /** Forces an immediate data refresh when the panel becomes visible. */
    public void refresh() {
        refreshStats();
        startCountdown();
    }

    // =========================================================================
    // Lifecycle (call when navigating away)
    // =========================================================================

    /**
     * Stops all background timelines.  Should be called from the host controller's
     * cleanup path (e.g., on scene change or window close).
     */
    public void shutdown() {
        if (refreshTimeline   != null) { refreshTimeline.stop();   refreshTimeline   = null; }
        stopCountdown();
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /** Formats a VND amount as "1,234,567 đ". */
    private static String formatVnd(long amount) {
        return String.format("%,d đ", amount);
    }
}