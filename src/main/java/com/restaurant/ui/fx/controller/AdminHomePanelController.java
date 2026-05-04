package com.restaurant.ui.fx.controller;

import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import com.restaurant.dao.StatsDAO;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

/**
 * AdminHomePanelController — Màn hình "Tổng quan" cho SUPER_ADMIN.
 *
 * <p>Hiển thị 4 chỉ số thống kê nhanh trong ngày, lấy từ
 * {@link StatsDAO#getAdminDashboardStats()}, chạy trên background thread
 * để không block UI.
 *
 * <p><b>FXML:</b> {@code AdminHomePanel.fxml}
 *
 * <p><b>Cách sử dụng từ MainController (ví dụ):</b>
 * <pre>{@code
 *   AdminHomePanelController ctrl = new AdminHomePanelController();
 *   Parent panel = FxUtils.loadFxml("AdminHomePanel.fxml", ctrl);
 *   contentArea.setCenter(panel);
 * }</pre>
 *
 * <p><b>Navigation callbacks:</b> Gán {@link #setOnNavigate(NavigateHandler)}
 * để MainController bắt sự kiện chuyển tab khi người dùng bấm nav-pill.
 */
public class AdminHomePanelController implements Initializable {

    // ─── FXML bindings ────────────────────────────────────────────────────────

    /** Hiển thị số doanh nghiệp đang ACTIVE. */
    @FXML private TextField fieldActiveRestaurants;

    /** Hiển thị số doanh nghiệp mới tạo hôm nay. */
    @FXML private TextField fieldNewRestaurants;

    /** Hiển thị tổng doanh thu hôm nay (định dạng có dấu phẩy). */
    @FXML private TextField fieldRevenueToday;

    /** Hiển thị số đơn hoàn tất hôm nay. */
    @FXML private TextField fieldOrdersToday;

    /** Nav pill — trang hiện tại, luôn active. */
    @FXML private Button btnHome;

    /** Nav pill — chuyển sang màn Quản lý doanh nghiệp. */
    @FXML private Button btnManage;

    /** Nav pill — chuyển sang màn Thống kê. */
    @FXML private Button btnStats;

    /** Nút logout góc dưới phải. */
    @FXML private Button btnLogout;

    // ─── Dependencies ─────────────────────────────────────────────────────────

    private final StatsDAO statsDAO = new StatsDAO();

    /**
     * Formatter VND — dùng Locale("vi","VN") để nhóm 3 chữ số bằng dấu phẩy.
     * Ví dụ: 10000000 → "10,000,000"
     */
    private static final NumberFormat VND_FMT =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // ─── Navigation callback ──────────────────────────────────────────────────

    /**
     * Functional interface cho sự kiện chuyển màn.
     * Tham số {@code target} là tên màn ("manage", "stats", v.v.).
     */
    @FunctionalInterface
    public interface NavigateHandler {
        void navigate(String target);
    }

    /** Được gán bởi MainController để nhận sự kiện chuyển tab. */
    private NavigateHandler onNavigate;

    /**
     * Đăng ký handler điều hướng từ bên ngoài (thường từ MainController).
     *
     * @param handler callback nhận tên màn đích ("manage" | "stats")
     */
    public void setOnNavigate(NavigateHandler handler) {
        this.onNavigate = handler;
    }

    /** Được gán bởi MainController để xử lý đăng xuất. */
    private Runnable onLogout;

    /**
     * Đăng ký handler đăng xuất từ bên ngoài.
     *
     * @param handler Runnable chạy khi người dùng bấm nút logout
     */
    public void setOnLogout(Runnable handler) {
        this.onLogout = handler;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Gọi ngay sau khi FXML được load. Đặt placeholder và bắt đầu load dữ liệu.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Đặt placeholder "..." trong khi chờ dữ liệu
        setPlaceholders();

        // Load stats bất đồng bộ
        loadDashboardStats();
    }

    // ─── FXML event handlers ──────────────────────────────────────────────────

    /** Người dùng bấm [🏠 Home] — đang ở trang này, không làm gì. */
    @FXML
    private void onHomeClicked() {
        // Đã ở trang Home — không navigate
    }

    /** Người dùng bấm [Quản lý doanh nghiệp]. */
    @FXML
    private void onManageClicked() {
        if (onNavigate != null) {
            onNavigate.navigate("manage");
        }
    }

    /** Người dùng bấm [Thống kê]. */
    @FXML
    private void onStatsClicked() {
        if (onNavigate != null) {
            onNavigate.navigate("stats");
        }
    }

    /** Người dùng bấm nút logout ⊖. */
    @FXML
    private void onLogoutClicked() {
        if (onLogout != null) {
            onLogout.run();
        }
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    /**
     * Chạy {@link StatsDAO#getAdminDashboardStats()} trên background thread.
     * Khi xong, cập nhật các TextField trên FX Application Thread.
     *
     * <p>Nếu xảy ra lỗi DB, các field hiển thị "—" thay vì crash.
     */
    private void loadDashboardStats() {
        Task<Map<String, Long>> task = new Task<>() {
            @Override
            protected Map<String, Long> call() {
                return statsDAO.getAdminDashboardStats();
            }
        };

        task.setOnSucceeded(e -> {
            Map<String, Long> stats = task.getValue();
            Platform.runLater(() -> populateFields(stats));
        });

        task.setOnFailed(e -> {
            Throwable err = task.getException();
            System.err.println("[AdminHomePanelController] loadDashboardStats lỗi: "
                    + (err != null ? err.getMessage() : "unknown"));
            Platform.runLater(this::setErrorState);
        });

        // Chạy trên daemon thread để không giữ JVM sống
        Thread thread = new Thread(task, "admin-home-stats-loader");
        thread.setDaemon(true);
        thread.start();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Hiển thị "..." trong tất cả các field trong khi chờ dữ liệu.
     */
    private void setPlaceholders() {
        fieldActiveRestaurants.setText("...");
        fieldNewRestaurants   .setText("...");
        fieldRevenueToday     .setText("...");
        fieldOrdersToday      .setText("...");
    }

    /**
     * Điền dữ liệu thực vào các TextField.
     *
     * @param stats Map trả về từ {@link StatsDAO#getAdminDashboardStats()}
     */
    private void populateFields(Map<String, Long> stats) {
        long active  = stats.getOrDefault("active_restaurants", 0L);
        long newR    = stats.getOrDefault("new_restaurants",    0L);
        long revenue = stats.getOrDefault("revenue_today",      0L);
        long orders  = stats.getOrDefault("orders_today",       0L);

        fieldActiveRestaurants.setText(String.valueOf(active));
        fieldNewRestaurants   .setText(String.valueOf(newR));
        fieldRevenueToday     .setText(VND_FMT.format(revenue));   // e.g. "10,000,000"
        fieldOrdersToday      .setText(String.valueOf(orders));
    }

    /**
     * Hiển thị "—" khi không thể tải dữ liệu từ DB.
     */
    private void setErrorState() {
        fieldActiveRestaurants.setText("—");
        fieldNewRestaurants   .setText("—");
        fieldRevenueToday     .setText("—");
        fieldOrdersToday      .setText("—");
    }
}