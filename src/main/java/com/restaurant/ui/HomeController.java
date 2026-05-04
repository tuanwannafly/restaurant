package com.restaurant.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.restaurant.dao.StatsDAO;
import com.restaurant.session.AppSession;

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
    private int                          secondsLeft = 30;
    private final ObservableList<String> activityItems =
            FXCollections.observableArrayList();

    private static final int REFRESH_SECONDS = 30;

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
        lblSubtitle.setText("Tong quan he thong  —  " + today);

        // Stat card icons + colours
        cardActive .configure(StatCard.CardIcon.STORE, "Nha hang hoat dong",   C_BLUE);
        cardNew    .configure(StatCard.CardIcon.PLUS,  "Nha hang moi hom nay", C_GREEN);
        cardRevenue.configure(StatCard.CardIcon.COIN,  "Doanh thu hom nay",    C_AMBER);
        cardOrders .configure(StatCard.CardIcon.BOX,   "Don hang hom nay",     C_RED);

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

        // Auto-refresh every 30 s
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

        Task<Map<String, Long>> task = new Task<>() {
            @Override
            protected Map<String, Long> call() {
                return new StatsDAO().getAdminDashboardStats();
            }
        };

        task.setOnSucceeded(event -> {
            Map<String, Long> s = task.getValue();

            cardActive .setValue(String.valueOf(s.getOrDefault("active_restaurants", 0L)));
            cardNew    .setValue(String.valueOf(s.getOrDefault("new_restaurants",    0L)));
            cardRevenue.setValue(formatVnd(s.getOrDefault("revenue_today",       0L)));
            cardOrders .setValue(String.valueOf(s.getOrDefault("orders_today",       0L)));

            updateBarChart(
                s.getOrDefault("active_restaurants", 0L),
                s.getOrDefault("new_restaurants",    0L),
                s.getOrDefault("orders_today",       0L)
            );

            populateActivityList(s);

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

    private void updateBarChart(long active, long newR, long orders) {
        if (barChart == null) return;

        barChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Nha hang HĐ", active));
        series.getData().add(new XYChart.Data<>("Moi hom nay",  newR));
        series.getData().add(new XYChart.Data<>("Don hang",     orders));
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

    private void populateActivityList(Map<String, Long> s) {
        String ts    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        long active  = s.getOrDefault("active_restaurants", 0L);
        long newR    = s.getOrDefault("new_restaurants",    0L);
        long orders  = s.getOrDefault("orders_today",       0L);
        long revenue = s.getOrDefault("revenue_today",      0L);

        activityItems.clear();
        activityItems.add(ts + "  He thong co " + active + " nha hang dang hoat dong");
        if (newR > 0)
            activityItems.add(ts + "  " + newR + " nha hang moi duoc dang ky hom nay");
        activityItems.add(ts + "  " + orders + " don hang da duoc tao hom nay");
        activityItems.add(ts + "  Doanh thu hom nay: " + formatVnd(revenue));
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
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private static String formatVnd(long amount) {
        return String.format("%,d d", amount);
    }
}