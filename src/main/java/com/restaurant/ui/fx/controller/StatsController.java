package com.restaurant.ui.fx.controller;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

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
    @FXML private Button btnExport;

    // ── State ──────────────────────────────────────────────────────────────────
    private LocalDate dateFrom = LocalDate.now().minusDays(30);
    private LocalDate dateTo   = LocalDate.now();

    private final StatsDAO                   dao        = new StatsDAO();
    private final ObservableList<TopItem>    topItems   = FXCollections.observableArrayList();
    private static final DecimalFormat       VND_FMT    = new DecimalFormat("#,###");
    private static final DateTimeFormatter   CSV_DATE   =
            DateTimeFormatter.ofPattern("yyyyMMdd");

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
                { setAlignment(javafx.geometry.Pos.CENTER); }
                @Override
                protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) { setText(null); setStyle(""); return; }
                    setText(String.valueOf(getIndex() + 1));
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #6B7280;");
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
        colQty.setCellFactory(col -> {
            TableCell<TopItem, Number> cell = new TableCell<>() {
                @Override protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : String.valueOf(item.intValue()));
                }
            };
            cell.setAlignment(javafx.geometry.Pos.CENTER);
            return cell;
        });

        colItemRev.setCellValueFactory(c ->
            new SimpleStringProperty(formatVnd(c.getValue().totalRevenue)));
        colItemRev.setCellFactory(col -> {
            TableCell<TopItem, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            return cell;
        });
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
        if (lblStatus != null) {
            lblStatus.setText("⚠ " + msg);
            lblStatus.setStyle("-fx-text-fill: #e6a817;");
            lblStatus.setVisible(true);
            lblStatus.setManaged(true);
        }
    }

    private void showError(String msg) {
        if (lblStatus != null) {
            lblStatus.setText("⛔ " + msg);
            lblStatus.setStyle("-fx-text-fill: #e53935;");
            lblStatus.setVisible(true);
            lblStatus.setManaged(true);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CSV Export
    // ═════════════════════════════════════════════════════════════════════════

    /** Được gọi từ nút "⬇ Xuất CSV" trên FXML. */
    @FXML
    private void onExportCsv() {
        exportCsv();
    }

    /**
     * Mở FileChooser rồi xuất toàn bộ dữ liệu thống kê hiện tại ra file CSV
     * UTF-8 BOM (tương thích Excel).
     *
     * <p>File gồm 3 section:
     * <ol>
     *   <li>Tóm tắt doanh thu (tổng, số đơn, trung bình/đơn)</li>
     *   <li>Top 5 món bán chạy (tên, số lượng, doanh thu)</li>
     *   <li>Trạng thái bàn (trống, đang phục vụ, đặt trước, tổng)</li>
     * </ol>
     */
    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu Thống kê CSV");
        chooser.getExtensionFilters().add(new ExtensionFilter("CSV Files", "*.csv"));
        String dateTag = LocalDate.now().format(CSV_DATE);
        chooser.setInitialFileName("thong_ke_" + dateTag + ".csv");

        File file = chooser.showSaveDialog(lblStatus.getScene().getWindow());
        if (file == null) return;

        final File finalFile = file.getName().toLowerCase().endsWith(".csv")
                ? file : new File(file.getPath() + ".csv");

        if (btnExport != null) btnExport.setDisable(true);

        // Snapshot dữ liệu từ khoảng ngày hiện tại
        final LocalDate fFrom = dateFrom;
        final LocalDate fTo   = dateTo;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                StatsDAO.RevenueStats  revenue = dao.getRevenue(fFrom, fTo);
                List<TopItem>          items   = dao.getTopItems(fFrom, fTo, 5);
                StatsDAO.TableStats    tables  = dao.getTableStats();

                try (PrintWriter pw = new PrintWriter(
                        new FileWriter(finalFile, java.nio.charset.StandardCharsets.UTF_8))) {

                    // BOM — Excel nhận UTF-8 đúng
                    pw.print('\uFEFF');

                    // ── Section 1: Doanh thu ──────────────────────────────
                    pw.println("=== TỔNG QUAN DOANH THU ===");
                    pw.println("Từ ngày,Đến ngày,Tổng doanh thu (₫),Số đơn hoàn thành,Trung bình/đơn (₫)");
                    pw.printf("%s,%s,%d,%d,%d%n",
                            fFrom, fTo,
                            revenue.totalRevenue,
                            revenue.orderCount,
                            revenue.avgPerOrder);
                    pw.println();

                    // ── Section 2: Top 5 món ─────────────────────────────
                    pw.println("=== TOP 5 MÓN BÁN CHẠY ===");
                    pw.println("Hạng,Tên món,Số lượng,Doanh thu (₫)");
                    int rank = 1;
                    for (TopItem it : items) {
                        pw.printf("%d,%s,%d,%d%n",
                                rank++,
                                csvEscape(it.itemName),
                                it.totalQty,
                                it.totalRevenue);
                    }
                    pw.println();

                    // ── Section 3: Trạng thái bàn ────────────────────────
                    pw.println("=== TRẠNG THÁI BÀN ===");
                    pw.println("Bàn trống,Đang phục vụ,Đặt trước,Tổng");
                    pw.printf("%d,%d,%d,%d%n",
                            tables.available,
                            tables.occupied,
                            tables.reserved,
                            tables.total);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            if (btnExport != null) btnExport.setDisable(false);
            Alert a = new Alert(AlertType.INFORMATION,
                    "Xuất CSV thành công:\n" + finalFile.getAbsolutePath());
            a.setHeaderText("Thành công");
            a.showAndWait();
        });

        task.setOnFailed(e -> {
            if (btnExport != null) btnExport.setDisable(false);
            Alert a = new Alert(AlertType.ERROR,
                    "Lỗi xuất CSV: " + task.getException().getMessage());
            a.setHeaderText("Lỗi");
            a.showAndWait();
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /** Escape CSV field: bao trong ngoặc kép nếu chứa dấu phẩy, ngoặc, hoặc newline. */
    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}