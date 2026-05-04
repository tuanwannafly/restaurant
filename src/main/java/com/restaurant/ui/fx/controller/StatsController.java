package com.restaurant.ui.fx.controller;

import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

import com.restaurant.dao.StatsDAO;
import com.restaurant.dao.StatsDAO.RevenueStats;
import com.restaurant.dao.StatsDAO.TableStats;
import com.restaurant.dao.StatsDAO.TopItem;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Controller cho StatsView.fxml.
 *
 * <p>Load 3 nhóm dữ liệu song song (revenue, topItems, tableStats) qua một Task
 * duy nhất để giảm số lần round-trip UI. Mọi DAO call nằm trên background thread;
 * cập nhật UI luôn chạy trên JavaFX Application Thread.
 */
public class StatsController implements Initializable {

    // ── FXML — Date filter ─────────────────────────────────────────────────────
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;

    // ── FXML — Revenue cards ───────────────────────────────────────────────────
    @FXML private Label lblRevenue;
    @FXML private Label lblOrderCount;
    @FXML private Label lblAvgOrder;

    // ── FXML — BarChart ────────────────────────────────────────────────────────
    @FXML private BarChart<String, Number>  barChart;
    @FXML private CategoryAxis              chartXAxis;
    @FXML private NumberAxis                chartYAxis;

    // ── FXML — Top items detail table ──────────────────────────────────────────
    @FXML private TableView<TopItem>           topItemsTable;
    @FXML private TableColumn<TopItem, Number> colRank;
    @FXML private TableColumn<TopItem, String> colItem;
    @FXML private TableColumn<TopItem, Number> colQty;
    @FXML private TableColumn<TopItem, String> colItemRev;

    // ── FXML — Table status ────────────────────────────────────────────────────
    @FXML private Label       lblAvailable;
    @FXML private Label       lblOccupied;
    @FXML private Label       lblReserved;
    @FXML private ProgressBar progressTable;
    @FXML private Label       lblOccupancyPct;

    // ── FXML — Misc ────────────────────────────────────────────────────────────
    @FXML private Label lblStatus;

    // ── State ──────────────────────────────────────────────────────────────────
    private LocalDate dateFrom = LocalDate.now().minusDays(30);
    private LocalDate dateTo   = LocalDate.now();

    private final StatsDAO                   dao        = new StatsDAO();
    private final ObservableList<TopItem>    topItems   = FXCollections.observableArrayList();
    private static final DecimalFormat       VND_FMT    = new DecimalFormat("#,###");

    // ─── Bundle all DAO results in one object to transfer across threads ───────
    private static class StatBundle {
        RevenueStats    revenue;
        List<TopItem>   items;
        TableStats      tables;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Initializable
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Date pickers
        dpFrom.setValue(dateFrom);
        dpTo.setValue(dateTo);

        // BarChart style
        barChart.setAnimated(false);
        barChart.setLegendVisible(false);
        barChart.setStyle("-fx-background-color: white;");
        chartXAxis.setTickLabelRotation(-25);

        // Top items table columns
        setupTopItemsTable();

        // Load data
        loadAll();
    }

    // ── Table setup ────────────────────────────────────────────────────────────

    private void setupTopItemsTable() {
        topItemsTable.setItems(topItems);

        // Rank — derived from position in ObservableList
        colRank.setCellFactory(col -> {
            TableCell<TopItem, Number> cell = new TableCell<>() {
                @Override
                protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : String.valueOf(getIndex() + 1));
                    setStyle("-fx-alignment: CENTER; -fx-font-weight: bold; -fx-text-fill: #6B7280;");
                }
            };
            return cell;
        });
        // Dummy value factory — rank uses cell index above
        colRank.setCellValueFactory(c -> new SimpleIntegerProperty(0));

        colItem.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().itemName != null ? c.getValue().itemName : "—"));

        colQty.setCellValueFactory(c ->
            new SimpleIntegerProperty(c.getValue().totalQty));
        colQty.setStyle("-fx-alignment: CENTER;");

        colItemRev.setCellValueFactory(c ->
            new SimpleStringProperty(formatVnd(c.getValue().totalRevenue)));
        colItemRev.setStyle("-fx-alignment: RIGHT;");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Actions
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void applyDateFilter() {
        LocalDate from = dpFrom.getValue();
        LocalDate to   = dpTo.getValue();

        if (from == null || to == null) {
            showWarning("Vui lòng chọn đầy đủ ngày bắt đầu và kết thúc.");
            return;
        }
        if (from.isAfter(to)) {
            showWarning("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
            return;
        }
        dateFrom = from;
        dateTo   = to;
        loadAll();
    }

    @FXML
    public void loadAll() {
        showLoading(true);

        Task<StatBundle> task = new Task<>() {
            @Override
            protected StatBundle call() {
                StatBundle b = new StatBundle();
                b.revenue = dao.getRevenue(dateFrom, dateTo);
                b.items   = dao.getTopItems(dateFrom, dateTo, 5);
                b.tables  = dao.getTableStats();
                return b;
            }
        };

        task.setOnSucceeded(e -> {
            StatBundle b = task.getValue();
            updateRevenueUI(b.revenue);
            updateTopItemsUI(b.items);
            updateTableStatsUI(b.tables);
            showLoading(false);
        });

        task.setOnFailed(e -> {
            showLoading(false);
            Throwable cause = task.getException();
            String msg = cause instanceof SecurityException
                    ? cause.getMessage()
                    : "Lỗi tải thống kê: " + (cause != null ? cause.getMessage() : "unknown");
            Platform.runLater(() -> showError(msg));
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // EDT updaters
    // ═════════════════════════════════════════════════════════════════════════

    private void updateRevenueUI(RevenueStats stats) {
        lblRevenue   .setText(formatVnd(stats.totalRevenue) + " ₫");
        lblOrderCount.setText(stats.orderCount + " đơn");
        lblAvgOrder  .setText(formatVnd(stats.avgPerOrder) + " ₫");
    }

    private void updateTopItemsUI(List<TopItem> items) {
        // 1. TableView
        topItems.setAll(items);

        // 2. BarChart — clear and rebuild series
        barChart.getData().clear();
        if (items.isEmpty()) return;

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Số lượng");

        for (TopItem item : items) {
            String label = abbreviate(item.itemName, 14);
            XYChart.Data<String, Number> data = new XYChart.Data<>(label, item.totalQty);
            series.getData().add(data);
        }

        barChart.getData().add(series);

        // Style bars after chart renders on next pulse
        Platform.runLater(() -> styleBarChart(series));
    }

    private void updateTableStatsUI(TableStats ts) {
        lblAvailable.setText(String.valueOf(ts.available));
        lblOccupied .setText(String.valueOf(ts.occupied));
        lblReserved .setText(String.valueOf(ts.reserved));

        double pct = ts.total > 0 ? (double) ts.occupied / ts.total : 0.0;
        progressTable.setProgress(pct);
        lblOccupancyPct.setText(ts.occupied + " / " + ts.total + " bàn");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    /** Color bars in a gradient from blue (#3B82F6) → teal (#10B981). */
    private void styleBarChart(XYChart.Series<String, Number> series) {
        String[] colors = {"#3B82F6", "#2563EB", "#1D4ED8", "#4ADE80", "#10B981"};
        int idx = 0;
        for (XYChart.Data<String, Number> data : series.getData()) {
            if (data.getNode() != null) {
                String color = colors[Math.min(idx, colors.length - 1)];
                data.getNode().setStyle(
                    "-fx-bar-fill: " + color + "; -fx-background-radius: 4 4 0 0;");
            }
            idx++;
        }
    }

    private void showLoading(boolean show) {
        Platform.runLater(() ->
            lblStatus.setText(show ? "Đang tải..." : ""));
    }

    private static String formatVnd(long value) {
        return VND_FMT.format(value);
    }

    /** Truncate long item names for BarChart X-axis labels. */
    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        alert.setTitle("Cảnh báo");
        alert.showAndWait();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle("Lỗi");
        alert.showAndWait();
    }
}