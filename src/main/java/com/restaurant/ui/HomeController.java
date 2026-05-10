package com.restaurant.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.restaurant.dao.StatsDAO;
import com.restaurant.session.AppSession;
import com.restaurant.session.RbacGuard;
import com.restaurant.websocket.RestaurantEventClient;
import com.restaurant.websocket.WsTopic;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Controller for {@code HomeView.fxml}.
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

    @FXML private ListView<String>          activityList;
    @FXML private BarChart<String, Number>  barChart;
    @FXML private CategoryAxis              chartXAxis;
    @FXML private NumberAxis                chartYAxis;

    // ── Accent colours ─────────────────────────────────────────────────────
    private static final Color C_BLUE  = Color.web("#3B82F6");
    private static final Color C_GREEN = Color.web("#10B981");
    private static final Color C_AMBER = Color.web("#F59E0B");
    private static final Color C_RED   = Color.web("#EF4444");

    // ── State ──────────────────────────────────────────────────────────────
    private Timeline                     refreshTimeline;
    private Timeline                     countdownTimeline;
    private Runnable                     cancelWsHandler;
    private int                          secondsLeft = 30;
    private final ObservableList<String> activityItems =
            FXCollections.observableArrayList();

    private static final int REFRESH_SECONDS = 30;

    /** WebSocket client — dùng để nhận push event real-time. */
    private RestaurantEventClient wsClient;

    // =========================================================================
    // Initialize
    // =========================================================================

    @FXML
    private void initialize() {
        // Header
        String user  = AppSession.getInstance().getUserName();
        String today = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy, EEEE"));
        lblGreeting.setText("Xin chao, " + user);

        boolean isSuperAdmin = RbacGuard.getInstance().isSuperAdmin();

        if (isSuperAdmin) {
            lblSubtitle.setText("Tong quan he thong  —  " + today);
            cardActive .configure(StatCard.CardIcon.STORE, "Nha hang hoat dong",   C_BLUE);
            cardNew    .configure(StatCard.CardIcon.PLUS,  "Nha hang moi hom nay", C_GREEN);
            cardRevenue.configure(StatCard.CardIcon.COIN,  "Doanh thu hom nay",    C_AMBER);
            cardOrders .configure(StatCard.CardIcon.BOX,   "Don hang hom nay",     C_RED);
        } else {
            lblSubtitle.setText("Tong quan nha hang  —  " + today);
            cardActive .configure(StatCard.CardIcon.STORE, "Ban dang co khach",  C_BLUE);
            cardNew    .configure(StatCard.CardIcon.BOX,   "Don hang hom nay",   C_GREEN);
            cardRevenue.configure(StatCard.CardIcon.COIN,  "Doanh thu hom nay",  C_AMBER);
            cardOrders .configure(StatCard.CardIcon.PLUS,  "Mon dang nau",       C_RED);
        }

        // Bar chart initial config
        if (barChart != null) {
            barChart.setTitle(null);
            chartYAxis.setLabel(null);
            chartYAxis.setMinorTickVisible(false);
            chartYAxis.setAnimated(false);
            chartXAxis.setAnimated(false);
        }

        // Activity list
        activityList.setItems(activityItems);
        activityList.setCellFactory(lv -> new ActivityListCell());
        activityItems.add("Dang tai du lieu...");

        // Initial load
        refreshStats();

        // ── WebSocket real-time push ──────────────────────────────────────────
        // Đăng ký handler: nhận event từ server → refresh ngay, reset countdown
        long myRestaurantId = AppSession.getInstance().getRestaurantId();
        wsClient = RestaurantEventClient.getInstance();
        cancelWsHandler = wsClient.addEventHandler(event -> {
            // SUPER_ADMIN nhận mọi event; RESTAURANT_ADMIN chỉ nhận event của nhà hàng mình
            boolean relevant = isSuperAdmin
                    || event.getRestaurantId() == myRestaurantId;
            if (relevant) {
                refreshStats();
                startCountdown(); // reset đồng hồ đếm ngược
            }
        });
        wsClient.subscribe(WsTopic.ORDERS, WsTopic.KITCHEN, WsTopic.BADGE);

        // Fallback polling 30s (bắt các thay đổi nếu WS tạm ngắt)
        refreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(REFRESH_SECONDS), e -> refreshStats()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();

        startCountdown();
    }

    // =========================================================================
    // Refresh
    // =========================================================================

    public void refreshStats() {
        setRefreshState(RefreshState.LOADING);

        boolean isSuperAdmin  = RbacGuard.getInstance().isSuperAdmin();
        long    restaurantId  = AppSession.getInstance().getRestaurantId();

        Task<Map<String, Long>> task = new Task<>() {
            @Override
            protected Map<String, Long> call() {
                if (isSuperAdmin) {
                    return new StatsDAO().getAdminDashboardStats();
                } else {
                    return new StatsDAO().getRestaurantHomeDashboardStats(restaurantId);
                }
            }
        };

        task.setOnSucceeded(event -> {
            Map<String, Long> s = task.getValue();

            if (isSuperAdmin) {
                cardActive .setValue(String.valueOf(s.getOrDefault("active_restaurants", 0L)));
                cardNew    .setValue(String.valueOf(s.getOrDefault("new_restaurants",    0L)));
                cardRevenue.setValue(formatVnd(   s.getOrDefault("revenue_today",       0L)));
                cardOrders .setValue(String.valueOf(s.getOrDefault("orders_today",       0L)));
                updateBarChart(
                    s.getOrDefault("active_restaurants", 0L),
                    s.getOrDefault("new_restaurants",    0L),
                    s.getOrDefault("orders_today",       0L),
                    isSuperAdmin
                );
            } else {
                cardActive .setValue(String.valueOf(s.getOrDefault("tables_occupied", 0L)));
                cardNew    .setValue(String.valueOf(s.getOrDefault("orders_today",    0L)));
                cardRevenue.setValue(formatVnd(   s.getOrDefault("revenue_today",    0L)));
                cardOrders .setValue(String.valueOf(s.getOrDefault("items_cooking",  0L)));
                updateBarChart(
                    s.getOrDefault("tables_occupied",  0L),
                    s.getOrDefault("tables_available", 0L),
                    s.getOrDefault("orders_today",     0L),
                    isSuperAdmin
                );
            }

            populateActivityList(s, isSuperAdmin);

            secondsLeft = REFRESH_SECONDS;
            setRefreshState(RefreshState.OK);
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            System.err.println("[HomeController] refreshStats loi: "
                    + (ex != null ? ex.getMessage() : "unknown"));
            setRefreshState(RefreshState.ERROR);
        });

        Thread thread = new Thread(task, "home-stats-loader");
        thread.setDaemon(true);
        thread.start();
    }

    // =========================================================================
    // Bar chart
    // =========================================================================

    private void updateBarChart(long a, long b, long c, boolean isSuperAdmin) {
        if (barChart == null) return;

        barChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        if (isSuperAdmin) {
            series.getData().add(new XYChart.Data<>("Nha hang HĐ", a));
            series.getData().add(new XYChart.Data<>("Moi hom nay",  b));
            series.getData().add(new XYChart.Data<>("Don hang",     c));
        } else {
            series.getData().add(new XYChart.Data<>("Ban co khach", a));
            series.getData().add(new XYChart.Data<>("Ban trong",    b));
            series.getData().add(new XYChart.Data<>("Don hom nay",  c));
        }
        barChart.getData().add(series);

        // Apply bar colours after nodes are attached to scene graph
        Platform.runLater(() -> applyBarColors());
    }

    private void applyBarColors() {
        if (barChart == null || barChart.getData().isEmpty()) return;
        List<String> colors = List.of("#3B82F6", "#10B981", "#EF4444");
        var dataList = barChart.getData().get(0).getData();
        for (int i = 0; i < dataList.size() && i < colors.size(); i++) {
            var node = dataList.get(i).getNode();
            if (node != null) {
                node.setStyle("-fx-bar-fill: " + colors.get(i) + ";");
            }
        }
    }

    // =========================================================================
    // Activity list
    // =========================================================================

    private void populateActivityList(Map<String, Long> s, boolean isSuperAdmin) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        activityItems.clear();

        if (isSuperAdmin) {
            long active  = s.getOrDefault("active_restaurants", 0L);
            long newR    = s.getOrDefault("new_restaurants",    0L);
            long orders  = s.getOrDefault("orders_today",       0L);
            long revenue = s.getOrDefault("revenue_today",      0L);

            activityItems.add(ts + "  He thong co " + active + " nha hang dang hoat dong");
            if (newR > 0)
                activityItems.add(ts + "  " + newR + " nha hang moi duoc dang ky hom nay");
            activityItems.add(ts + "  " + orders + " don hang da duoc tao hom nay");
            activityItems.add(ts + "  Doanh thu hom nay: " + formatVnd(revenue));
        } else {
            long occupied  = s.getOrDefault("tables_occupied",  0L);
            long available = s.getOrDefault("tables_available", 0L);
            long orders    = s.getOrDefault("orders_today",     0L);
            long revenue   = s.getOrDefault("revenue_today",    0L);
            long cooking   = s.getOrDefault("items_cooking",    0L);
            long reports   = s.getOrDefault("reports_pending",  0L);

            activityItems.add(ts + "  " + occupied + " ban dang co khach, " + available + " ban trong");
            activityItems.add(ts + "  " + orders + " don hang hoan thanh hom nay");
            if (cooking > 0)
                activityItems.add(ts + "  " + cooking + " mon dang duoc chuan bi trong bep");
            activityItems.add(ts + "  Doanh thu hom nay: " + formatVnd(revenue));
            if (reports > 0)
                activityItems.add(ts + "  " + reports + " bao cao chua duoc xu ly");
        }

        if (activityItems.isEmpty())
            activityItems.add("Chua co hoat dong nao trong ngay hom nay.");
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
                        ? "Cap nhat sau: " + secondsLeft + "s"
                        : "Dang tai...");
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void stopCountdown() {
        if (countdownTimeline != null) { countdownTimeline.stop(); countdownTimeline = null; }
    }

    // =========================================================================
    // Refresh state helpers
    // =========================================================================

    private enum RefreshState { LOADING, OK, ERROR }

    private void setRefreshState(RefreshState state) {
        switch (state) {
            case LOADING -> {
                if (lblRefreshDot  != null) lblRefreshDot .setStyle("-fx-text-fill: #F59E0B; -fx-font-size: 10;");
                if (lblRefreshText != null) lblRefreshText.setText("Dang tai...");
            }
            case OK -> {
                if (lblRefreshDot  != null) lblRefreshDot .setStyle("-fx-text-fill: #10B981; -fx-font-size: 10;");
                if (lblRefreshText != null) lblRefreshText.setText("Cap nhat sau: " + REFRESH_SECONDS + "s");
            }
            case ERROR -> {
                if (lblRefreshDot  != null) lblRefreshDot .setStyle("-fx-text-fill: #EF4444; -fx-font-size: 10;");
                if (lblRefreshText != null) lblRefreshText.setText("Loi tai du lieu");
            }
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public void refresh() { refreshStats(); startCountdown(); }

    public void shutdown() {
        if (refreshTimeline != null) { refreshTimeline.stop(); refreshTimeline = null; }
        stopCountdown();
        if (cancelWsHandler != null) { cancelWsHandler.run(); cancelWsHandler = null; }
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private static String formatVnd(long amount) {
        return String.format("%,d d", amount);
    }
}