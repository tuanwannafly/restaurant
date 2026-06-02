package com.restaurant.ui.fx.controller;

// 📁 VỊ TRÍ: src/main/java/com/restaurant/ui/fx/controller/AdminStatsController.java

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import com.restaurant.dao.StatsDAO;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.StringConverter;

/**
 * Controller cho AdminStatsView.fxml.
 * <p>
 * Tương đương {@code AdminStatsPanel} (Swing) — dành cho SUPER_ADMIN.
 * <ul>
 *   <li>3 summary cards: tổng nhà hàng, doanh thu, đơn hàng.</li>
 *   <li>BarChart doanh thu / đơn theo tháng với filter loại + khoảng tháng.</li>
 * </ul>
 */
public class AdminStatsController {

    // ── FXML injections ───────────────────────────────────────────────────────

    // Summary date filter
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Button     btnFilter;

    // Summary cards
    @FXML private Label lblTotalRestaurants;
    @FXML private Label lblTotalRevenue;
    @FXML private Label lblTotalOrders;

    // Chart controls
    @FXML private ComboBox<String> cboType;
    @FXML private ComboBox<String> cboChartFrom;
    @FXML private ComboBox<String> cboChartTo;
    @FXML private Button           btnDrawChart;

    // Chart + hint overlay
    @FXML private BarChart<String, Number>  barChart;
    @FXML private CategoryAxis              xAxis;
    @FXML private NumberAxis                yAxis;
    @FXML private StackPane                 chartHint;

    // Export
    @FXML private Button btnExport;

    // ── DAO ───────────────────────────────────────────────────────────────────

    private final StatsDAO dao = new StatsDAO();

    // ── Formatter ─────────────────────────────────────────────────────────────

    private static final NumberFormat VND_FMT =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private static final DateTimeFormatter CSV_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    // ═════════════════════════════════════════════════════════════════════════
    // Khởi tạo
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        // ── Mặc định ngày lọc: đầu tháng → hôm nay ──────────────────────────
        LocalDate today = LocalDate.now();
        dpFrom.setValue(today.withDayOfMonth(1));
        dpTo.setValue(today);

        // ── Bộ lọc biểu đồ ───────────────────────────────────────────────────
        cboType.setItems(FXCollections.observableArrayList("Doanh thu", "Đơn hàng"));
        cboType.getSelectionModel().selectFirst();

        String[] months = buildMonthOptions(today.getYear());
        cboChartFrom.setItems(FXCollections.observableArrayList(months));
        cboChartTo.setItems(FXCollections.observableArrayList(months));
        cboChartFrom.getSelectionModel().selectFirst();
        cboChartTo.getSelectionModel().selectLast();

        // ── BarChart mặc định ─────────────────────────────────────────────────
        barChart.setTitle(null);
        barChart.setAnimated(true);
        yAxis.setLabel("");

        // Hiển thị hint khi chưa có dữ liệu
        showHint(true);

        // ── Load summary ngay khi mở ──────────────────────────────────────────
        loadStats();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FXML event handlers
    // ═════════════════════════════════════════════════════════════════════════

    /** Nút 🔍 Lọc: tải lại 3 summary cards theo khoảng ngày. */
    @FXML
    private void onFilter() {
        loadStats();
    }

    /** Nút 📊 Vẽ biểu đồ: tải dữ liệu và cập nhật BarChart. */
    @FXML
    private void onDrawChart() {
        loadChartData();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Data loading — Summary cards
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Tải 3 chỉ số tổng quan (nhà hàng, doanh thu, đơn) chạy trên background thread.
     * Cập nhật UI trên JavaFX Application Thread.
     */
    public void loadStats() {
        LocalDate from = dpFrom.getValue();
        LocalDate to   = dpTo.getValue();
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to   == null) to   = LocalDate.now();

        // Placeholder loading state
        lblTotalRestaurants.setText("...");
        lblTotalRevenue.setText("...");
        lblTotalOrders.setText("...");
        btnFilter.setDisable(true);

        final LocalDate fFrom = from, fTo = to;

        Task<Map<String, Long>> task = new Task<>() {
            @Override
            protected Map<String, Long> call() {
                return dao.getSuperAdminStats(fFrom, fTo);
            }
        };

        task.setOnSucceeded(e -> {
            Map<String, Long> stats = task.getValue();
            lblTotalRestaurants.setText(
                String.valueOf(stats.getOrDefault("total_restaurants", 0L)));
            lblTotalRevenue.setText(
                VND_FMT.format(stats.getOrDefault("total_revenue", 0L)) + " ₫");
            lblTotalOrders.setText(
                String.valueOf(stats.getOrDefault("total_orders", 0L)));
            btnFilter.setDisable(false);
        });

        task.setOnFailed(e -> {
            lblTotalRestaurants.setText("–");
            lblTotalRevenue.setText("–");
            lblTotalOrders.setText("–");
            btnFilter.setDisable(false);
            System.err.println("[AdminStatsController] loadStats lỗi: "
                + task.getException().getMessage());
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Data loading — BarChart
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Tải dữ liệu monthly từ DAO và cập nhật BarChart.
     * Chạy trên daemon thread; update UI trên FX thread.
     */
    private void loadChartData() {
        String type    = cboType.getValue();
        String fromStr = cboChartFrom.getValue();
        String toStr   = cboChartTo.getValue();

        int[] fromP = parseMonthLabel(fromStr);
        int[] toP   = parseMonthLabel(toStr);
        if (fromP == null || toP == null) return;

        int fromMonth = fromP[0], toMonth = toP[0], year = fromP[1];
        // Đảm bảo from <= to
        if (fromMonth > toMonth) { int tmp = fromMonth; fromMonth = toMonth; toMonth = tmp; }

        final int fM = fromMonth, tM = toMonth, yr = year;
        final boolean isRevenue = "Doanh thu".equals(type);

        btnDrawChart.setDisable(true);
        btnDrawChart.setText("⏳ Đang tải...");

        Task<Map<String, Long>> task = new Task<>() {
            @Override
            protected Map<String, Long> call() {
                return isRevenue
                    ? dao.getMonthlyRevenue(yr, fM, tM)
                    : dao.getMonthlyOrders(yr, fM, tM);
            }
        };

        task.setOnSucceeded(e -> {
            Map<String, Long> data = task.getValue();

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(type);

            boolean hasData = false;
            for (int m = fM; m <= tM; m++) {
                String key = "T" + m + "/" + yr;
                long val = data.getOrDefault(key, 0L);
                series.getData().add(new XYChart.Data<>(key, val));
                if (val > 0) hasData = true;
            }

            barChart.getData().clear();
            barChart.getData().add(series);

            // Y-axis label
            yAxis.setLabel(isRevenue ? "Doanh thu (₫)" : "Số đơn");

            // Áp dụng màu bar qua CSS pseudo-class (hoặc setStyle trực tiếp)
            String barColor = isRevenue ? "#4F46E5" : "#10B981";
            applyBarColor(series, barColor);

            showHint(!hasData);
            btnDrawChart.setDisable(false);
            btnDrawChart.setText("📊 Vẽ biểu đồ");
        });

        task.setOnFailed(e -> {
            btnDrawChart.setDisable(false);
            btnDrawChart.setText("📊 Vẽ biểu đồ");
            System.err.println("[AdminStatsController] loadChartData lỗi: "
                + task.getException().getMessage());
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    /** Ẩn/hiện hint overlay và BarChart. */
    private void showHint(boolean show) {
        chartHint.setVisible(show);
        chartHint.setManaged(show);
        barChart.setVisible(!show);
        barChart.setManaged(!show);
    }

    /**
     * Tô màu từng cột trong series bằng setStyle().
     * Cách đơn giản nhất không cần sửa CSS file.
     */
    private void applyBarColor(XYChart.Series<String, Number> series, String hexColor) {
        // Cần chạy sau khi scene đã render để node tồn tại
        Platform.runLater(() -> {
            for (XYChart.Data<String, Number> item : series.getData()) {
                if (item.getNode() != null) {
                    item.getNode().setStyle(
                        "-fx-bar-fill: " + hexColor + ";");
                }
            }
        });
    }

    /**
     * Tạo mảng tháng cho năm hiện tại: ["T1/2026", ..., "T12/2026"].
     */
    private String[] buildMonthOptions(int year) {
        String[] opts = new String[12];
        for (int i = 0; i < 12; i++) opts[i] = "T" + (i + 1) + "/" + year;
        return opts;
    }

    /**
     * Parse "T3/2026" → {3, 2026}. Null nếu format sai.
     */
    private int[] parseMonthLabel(String label) {
        if (label == null) return null;
        try {
            String s = label.startsWith("T") ? label.substring(1) : label;
            String[] parts = s.split("/");
            return new int[]{
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim())
            };
        } catch (Exception e) {
            return null;
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
     * Mở FileChooser rồi xuất dữ liệu thống kê admin ra CSV UTF-8 BOM.
     *
     * <p>File gồm 2 section:
     * <ol>
     *   <li>Tóm tắt (tổng nhà hàng, doanh thu, số đơn) theo khoảng ngày đang lọc.</li>
     *   <li>Dữ liệu biểu đồ tháng đang chọn (cột tháng + giá trị doanh thu hoặc đơn hàng).</li>
     * </ol>
     */
    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu Thống kê Admin CSV");
        chooser.getExtensionFilters().add(new ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("thong_ke_admin_" + LocalDate.now().format(CSV_DATE) + ".csv");

        File file = chooser.showSaveDialog(btnFilter.getScene().getWindow());
        if (file == null) return;

        final File finalFile = file.getName().toLowerCase().endsWith(".csv")
                ? file : new File(file.getPath() + ".csv");

        if (btnExport != null) btnExport.setDisable(true);

        // Snapshot bộ lọc summary
        LocalDate sumFrom = dpFrom.getValue();
        LocalDate sumTo   = dpTo.getValue();
        if (sumFrom == null) sumFrom = LocalDate.now().withDayOfMonth(1);
        if (sumTo   == null) sumTo   = LocalDate.now();
        final LocalDate fSumFrom = sumFrom;
        final LocalDate fSumTo   = sumTo;

        // Snapshot bộ lọc biểu đồ
        String type    = cboType.getValue();
        String fromStr = cboChartFrom.getValue();
        String toStr   = cboChartTo.getValue();
        int[] fromP = parseMonthLabel(fromStr);
        int[] toP   = parseMonthLabel(toStr);
        final boolean isRevenue = "Doanh thu".equals(type);

        // Nếu chưa chọn range biểu đồ thì vẫn export summary
        final int chartFromMonth = (fromP != null) ? fromP[0] : 1;
        final int chartToMonth   = (toP   != null) ? toP[0]   :
                                    (fromP != null) ? fromP[0] : 12;
        final int chartYear      = (fromP != null) ? fromP[1] : LocalDate.now().getYear();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Map<String, Long> summary = dao.getSuperAdminStats(fSumFrom, fSumTo);
                Map<String, Long> chartData = isRevenue
                        ? dao.getMonthlyRevenue(chartYear, chartFromMonth, chartToMonth)
                        : dao.getMonthlyOrders (chartYear, chartFromMonth, chartToMonth);

                try (PrintWriter pw = new PrintWriter(
                        new FileWriter(finalFile, java.nio.charset.StandardCharsets.UTF_8))) {

                    pw.print('\uFEFF');  // UTF-8 BOM

                    // ── Section 1: Tóm tắt ───────────────────────────────
                    pw.println("=== TÓM TẮT THỐNG KÊ ADMIN ===");
                    pw.println("Từ ngày,Đến ngày,Tổng nhà hàng,Tổng doanh thu (₫),Tổng đơn hàng");
                    pw.printf("%s,%s,%d,%d,%d%n",
                            fSumFrom, fSumTo,
                            summary.getOrDefault("total_restaurants", 0L),
                            summary.getOrDefault("total_revenue",     0L),
                            summary.getOrDefault("total_orders",      0L));
                    pw.println();

                    // ── Section 2: Biểu đồ tháng ─────────────────────────
                    String chartLabel = isRevenue ? "Doanh thu (₫)" : "Số đơn hàng";
                    pw.println("=== BIỂU ĐỒ THEO THÁNG: " + chartLabel.toUpperCase() + " ===");
                    pw.println("Tháng," + chartLabel);
                    for (int m = chartFromMonth; m <= chartToMonth; m++) {
                        String key = "T" + m + "/" + chartYear;
                        pw.printf("%s,%d%n", key, chartData.getOrDefault(key, 0L));
                    }
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

    /** Escape CSV field. */
    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
