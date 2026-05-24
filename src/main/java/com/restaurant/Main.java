package com.restaurant;

import java.util.Optional;

import com.restaurant.dao.TabletOrderDAO;
import com.restaurant.dao.UserDAO;
import com.restaurant.data.DataManager;
import com.restaurant.model.Order;
import com.restaurant.session.AppSession;
import com.restaurant.session.RefreshTokenService;
import com.restaurant.session.TabletSession;
import com.restaurant.session.TokenStorage;
import com.restaurant.ui.TableOrderStage;
import com.restaurant.ui.fx.controller.LoginController;
import com.restaurant.ui.fx.controller.MainController;
import com.restaurant.ui.fx.util.FxUtils;
import com.restaurant.ui.fx.util.PollManagerFx;
import com.restaurant.websocket.OracleDcnBridge;
import com.restaurant.websocket.RestaurantEventClient;
import com.restaurant.websocket.RestaurantEventServer;
import com.restaurant.websocket.WsTopic;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Main — JavaFX edition entry point for SmartRestaurant.
 *
 * <h3>Startup sequence (mirrors the Swing Main.java exactly)</h3>
 * <ol>
 *   <li>JavaFX {@link Application#init()} — cleanup expired tokens (off
 *       the FX thread, safe for blocking DB work).</li>
 *   <li>{@link Application#start(Stage)} — silent re-auth attempt using
 *       the saved refresh-token on disk.</li>
 *   <li>If silent login succeeds → open {@code MainView.fxml} directly.</li>
 *   <li>Otherwise → show {@code LoginView.fxml}; on success open
 *       {@code MainView.fxml}; on cancel exit.</li>
 * </ol>
 *
 * <h3>Thread model</h3>
 * <ul>
 *   <li>{@code init()} runs on the <em>JavaFX Launcher Thread</em> — may
 *       block (DB calls are fine here).</li>
 *   <li>{@code start()} and all subsequent scene operations run on the
 *       <em>FX Application Thread</em>.</li>
 *   <li>Silent re-auth performs its DB look-ups in {@code init()} so
 *       the result is ready before the FX thread starts painting.</li>
 * </ul>
 *
 * <h3>Phase WS — WebSocket push infrastructure</h3>
 * Sau khi scene hiển thị, {@link #openMainView(Stage)} khởi động:
 * <ol>
 *   <li>{@link RestaurantEventServer} — WebSocket server port 8025.</li>
 *   <li>{@link RestaurantEventClient} — kết nối và subscribe BADGE/KITCHEN/ORDERS/REQUEST_LIST.</li>
 *   <li>{@link OracleDcnBridge} — lắng nghe Oracle DCN, broadcast WsEvent khi DB thay đổi.</li>
 * </ol>
 * Badge refresh được kích hoạt ngay khi nhận push thay vì chờ poll 10 giây.
 * {@code stop()} dọn dẹp WS và DCN trước khi dừng {@link PollManagerFx}.
 */
public class Main extends Application {

    // Result of the silent re-auth attempted in init()
    // Written by the Launcher thread, read by the FX thread — volatile for visibility.
    private volatile boolean silentLoginOk = false;

    // =========================================================================
    // Application lifecycle
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Runs on the JavaFX Launcher thread (NOT the FX Application Thread).
     * Blocking DB operations are safe here.
     *
     * <p>Steps performed (matching Swing Main):
     * <ol>
     *   <li>Cleanup expired password-reset tokens.</li>
     *   <li>Cleanup expired refresh tokens.</li>
     *   <li>Attempt silent re-auth from disk-saved refresh token.</li>
     * </ol>
     */
    @Override
    public void init() {
        // ── 1. Dọn dẹp token hết hạn từ phiên trước ──────────────────────────
        try {
            DataManager.getInstance().cleanupExpiredResetTokens();
        } catch (Exception ignored) {
            // Không block khởi động nếu DB chưa sẵn sàng
        }
        try {
            RefreshTokenService.getInstance().cleanExpiredTokens();
        } catch (Exception ignored) {
            // Không block khởi động
        }

        // ── 2. Silent re-auth: kiểm tra refresh token đã lưu trên disk ────────
        try {
            Optional<String> savedToken = TokenStorage.getInstance().loadRefreshToken();
            if (savedToken.isPresent()) {
                Optional<Long> userIdOpt =
                        RefreshTokenService.getInstance().validateAndRotate(savedToken.get());
                if (userIdOpt.isPresent()) {
                    silentLoginOk = new UserDAO().loginByUserId(userIdOpt.get());
                } else {
                    // Token hết hạn hoặc bị revoke → xoá file
                    TokenStorage.getInstance().clearSavedToken();
                }
            }
        } catch (Exception silentEx) {
            System.err.println("[Main] Silent re-auth thất bại: " + silentEx.getMessage());
            TokenStorage.getInstance().clearSavedToken();
            silentLoginOk = false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Runs on the FX Application Thread.  Opens either the main window
     * (silent login) or the login screen, then wires up the primary
     * {@link Stage}.
     *
     * @param primaryStage the initial top-level stage provided by JavaFX
     */
    @Override
    public void start(Stage primaryStage) {
        configureStage(primaryStage);

        // ── Kiểm tra tablet mode trước ────────────────────────────────────────
        TabletSession tablet = TabletSession.getInstance();
        if (tablet.isValid()) {
            openTabletMode(primaryStage, tablet);
            return;
        }

        if (silentLoginOk && AppSession.getInstance().isLoggedIn()) {
            // ── Silent login succeeded → open MainView directly ────────────────
            try {
                openMainView(primaryStage);
            } catch (Exception e) {
                System.err.println("[Main] Silent login succeeded but MainView failed: "
                        + e.getMessage());
                e.printStackTrace();

                // Fallback: clear broken token and show login screen
                TokenStorage.getInstance().clearSavedToken();
                try { AppSession.getInstance().logout(); } catch (Exception ignored) {}

                openLoginView(primaryStage);
            }
        } else {
            // ── Normal path → show login screen ───────────────────────────────
            openLoginView(primaryStage);
        }
    }

    /**
     * Khởi động ứng dụng ở chế độ tablet khách.
     *
     * <p>Không yêu cầu đăng nhập — đọc tableId và restaurantId từ tablet.properties.
     * Tự động mở {@link TableOrderStage} full-screen và kết nối WebSocket.
     * Nếu không có order active cho bàn, tạo mới PENDING tự động.
     *
     * @param primaryStage stage chính của JavaFX
     * @param tablet       TabletSession đã validate
     */
    private void openTabletMode(Stage primaryStage, TabletSession tablet) {
        System.out.println("[Main] Starting in TABLET MODE — tableId="
                + tablet.getTableId() + ", restaurantId=" + tablet.getRestaurantId());

        // Set session tối thiểu để DAO không bị NPE khi gọi AppSession.rid()
        AppSession.getInstance().loginAsTablet(tablet.getRestaurantId());

        // Lấy hoặc tạo order active cho bàn
        TabletOrderDAO tabletDao = new TabletOrderDAO(tablet.getRestaurantId());
        Order order = tabletDao.getOrCreateActiveOrder(tablet.getTableId());

        if (order == null) {
            // Không kết nối được DB → hiện màn hình lỗi
            showTabletError(primaryStage,
                    "Không kết nối được cơ sở dữ liệu.\nVui lòng gọi nhân viên hỗ trợ.");
            return;
        }

        String tableName = tabletDao.getTableName(tablet.getTableId());

        // Khởi động WebSocket để nhận push từ server nhà hàng
        try {
            RestaurantEventServer.getInstance().start();
        } catch (Exception e) {
            System.err.println("[Main] WS server lỗi (tablet): " + e.getMessage());
        }
        RestaurantEventClient wsClient = RestaurantEventClient.getInstance();
        wsClient.connect();
        wsClient.subscribe(WsTopic.forTable(Integer.parseInt(tablet.getTableId())));
        // Lưu ý: admin side nhận thay đổi trạng thái bàn qua Oracle DCN → WS broadcast tự động

        // Mở màn hình đặt món full-screen
        TableOrderStage orderStage = new TableOrderStage(
                tablet.getTableId(),
                order.getId(),
                tableName
        );
        orderStage.setFullScreen(true);
        orderStage.setFullScreenExitHint("");
        // TableOrderStage.setupCloseHandler() xử lý đóng cửa sổ + cập nhật trạng thái bàn
        orderStage.show();

        primaryStage.close();
    }

    /**
     * Hiện màn hình lỗi đơn giản khi tablet không khởi động được.
     */
    private void showTabletError(Stage stage, String message) {
        javafx.scene.control.Label lbl = new javafx.scene.control.Label(message);
        lbl.setStyle("-fx-font-size: 22px; -fx-text-alignment: center; -fx-padding: 40px;");
        lbl.setWrapText(true);
        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(lbl);
        stage.setScene(new javafx.scene.Scene(root, 800, 600));
        stage.setTitle("Lỗi khởi động tablet");
        stage.show();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Called when the application is about to exit (last window closed or
     * {@link Platform#exit()} called).
     *
     * <h3>Shutdown order (Phase WS)</h3>
     * <ol>
     *   <li>Dừng {@link OracleDcnBridge} — không còn nhận DCN event từ Oracle.</li>
     *   <li>Disconnect {@link RestaurantEventClient} — tắt auto-reconnect.</li>
     *   <li>Dừng {@link RestaurantEventServer} — đóng cổng WebSocket.</li>
     *   <li>Dừng {@link PollManagerFx} — dừng mọi Timeline còn lại.</li>
     * </ol>
     * Thứ tự: nguồn push (DCN) → đường truyền (WS) → consumer (PollManager)
     * để tránh push rác sau khi app đã dọn dẹp state.
     */
    @Override
    public void stop() {
        // ── 1. Dừng nguồn push Oracle DCN ────────────────────────────────────
        try {
            OracleDcnBridge.getInstance().stop();
        } catch (Exception ignored) {}

        // ── 2. Ngắt WebSocket client (tắt auto-reconnect) ────────────────────
        try {
            RestaurantEventClient.getInstance().disconnect();
        } catch (Exception ignored) {}

        // ── 3. Dừng WebSocket server (giải phóng cổng) ───────────────────────
        try {
            RestaurantEventServer.getInstance().stop();
        } catch (Exception ignored) {}

        // ── 4. Dừng mọi JavaFX Timeline (phải trên FX thread) ────────────────
        Platform.runLater(() -> {
            try {
                PollManagerFx.getInstance().stopAll();
            } catch (Exception ignored) {}
        });

        try {
            AppSession.getInstance().logout();
        } catch (Exception ignored) {}

        System.out.println("[Main] Application stopped.");
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Applies common Stage configuration: title, icon, close-button behaviour.
     */
    private void configureStage(Stage stage) {
        stage.setTitle("SmartRestaurant");

        // App icon – placed at src/main/resources/images/icon.png
        try {
            Image icon = new Image(
                    Main.class.getResourceAsStream("/images/icon.png"));
            if (!icon.isError()) {
                stage.getIcons().add(icon);
            }
        } catch (Exception ignored) {
            // Icon is optional – no crash if missing
        }

        // Exit the JVM when the primary window is closed
        stage.setOnCloseRequest(e -> Platform.exit());
    }

    /**
     * Loads {@code MainView.fxml}, attaches the global stylesheet, and
     * shows the primary stage.
     *
     * <p>Equivalent to {@code new MainFrame(); frame.setVisible(true)} in the
     * Swing version.
     *
     * <h3>Phase WS additions (sau khi scene hiển thị)</h3>
     * <ol>
     *   <li>Inject {@link PollManagerFx.BadgeUpdater} vào PollManagerFx
     *       (giữ nguyên như cũ).</li>
     *   <li>Đặt {@link RestaurantEventServer#setOnStartCallback} để chỉ instance bind
     *       port thành công mới khởi động {@link OracleDcnBridge}.</li>
     *   <li>Khởi động {@link RestaurantEventServer} tại port 8025.</li>
     *   <li>Kết nối {@link RestaurantEventClient} và subscribe 4 topic:
     *       BADGE, KITCHEN, ORDERS, REQUEST_LIST.</li>
     *   <li>Đăng ký {@code onEvent} handler → gọi
     *       {@link PollManagerFx#refreshBadgesAsync()} ngay khi nhận push.</li>
     *   <li>Gọi {@link PollManagerFx#refreshBadgesAsync()} một lần ngay để load badge ban đầu.</li>
     * </ol>
     *
     * <p><b>Multi-instance:</b> nếu 2 instance cùng chạy, instance 2 không bind được port
     * → callback không chạy → DCN không start trên instance 2 → tránh hoàn toàn
     * race condition "publishToServer() gọi khi client chưa connect".
     */
    private void openMainView(Stage stage) {
        MainController controller = new MainController();
        javafx.scene.Parent root  = FxUtils.loadFxml("MainView.fxml", controller);

        Scene scene = new Scene(root, 1280, 780);
        FxUtils.loadCss(scene);

        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();

        // ── Badge updater: inject callback vào PollManagerFx (giữ nguyên) ────────
        // Callback chạy trên FX thread, cập nhật 4 BadgeLabel trên nav bar.
        PollManagerFx.getInstance().setBadgeUpdater((k, w, p, r) -> {
            if (controller.getKitchenBadge() != null) controller.getKitchenBadge().setCount(k);
            if (controller.getWaiterBadge()  != null) controller.getWaiterBadge() .setCount(w);
            if (controller.getCashierBadge() != null) controller.getCashierBadge().setCount(p);
            if (controller.getRequestBadge() != null) controller.getRequestBadge().setCount(r);
        });

        // ── Phase WS: WebSocket push thay thế polling badge mỗi 10 giây ────────
        //
        //  Luồng dữ liệu:
        //    Oracle DB thay đổi
        //      → OracleDcnBridge (DCN callback thread)
        //        → RestaurantEventServer.broadcast(WsEvent)
        //          → RestaurantEventClient.onMessage (WS thread)
        //            → Platform.runLater → onEvent handler (FX thread)
        //              → PollManagerFx.refreshBadgesAsync() → BadgeUpdater
        //
        //  (1) Đặt callback TRƯỚC khi start server.
        //      Oracle DCN chỉ khởi động nếu instance này bind được cổng 8025.
        //      Instance 2 (cổng bị chiếm) → onStart() không bao giờ được gọi
        //      → DCN không start → instance 2 chỉ là WS client thuần tuý.
        //
        //  Tại sao quan trọng:
        //    - Nếu cả 2 instance đều start DCN, cả 2 đều nhận callback từ Oracle.
        //    - Instance 2 sẽ gọi client.publishToServer() nhưng client chưa chắc
        //      đã connect xong (async) → event bị drop (race condition).
        //    - Với cách này: chỉ instance 1 (server) có DCN → broadcast đến mọi
        //      subscriber kể cả client của instance 2 → instance 2 nhận đủ event.
        RestaurantEventServer.getInstance().setOnStartCallback(() -> {
            System.out.println("[Main] WS server bind thành công — khởi động Oracle DCN.");
            try {
                OracleDcnBridge.getInstance().start();
            } catch (Exception dcnEx) {
                System.err.println("[Main] OracleDcnBridge không thể start: "
                        + dcnEx.getMessage());
            }
        });

        //  (2) Khởi động WebSocket server nội bộ.
        //      Nếu cổng bị chiếm (instance 2), lỗi được bắt và log — callback không chạy.
        try {
            RestaurantEventServer.getInstance().start();
        } catch (Exception wsServerEx) {
            System.err.println("[Main] WS server không thể start (cổng đã được dùng bởi "
                    + "instance khác — instance này chạy ở chế độ client-only): "
                    + wsServerEx.getMessage());
        }

        //  (3) Kết nối client và subscribe tất cả topic cần thiết.
        //      connect() non-blocking — onOpen() gửi SUB frames sau khi handshake xong.
        //      Cả 2 instance đều kết nối → instance 1's server nhận và relay event
        //      đến tất cả subscriber (kể cả client của instance 2).
        RestaurantEventClient wsClient = RestaurantEventClient.getInstance();
        wsClient.connect();
        wsClient.subscribe(
                WsTopic.BADGE,
                WsTopic.KITCHEN,
                WsTopic.ORDERS,
                WsTopic.REQUEST_LIST
        );

        //  (4) Mọi push event → kích hoạt badge refresh ngay lập tức.
        //      addEventHandler() trả về cancel token — không cần lưu vì badge refresh
        //      tồn tại suốt vòng đời app. Handler luôn chạy trên FX thread.
        wsClient.addEventHandler(event -> PollManagerFx.getInstance().refreshBadgesAsync());

        //  (5) Load badge ngay lập tức (không chờ push đầu tiên từ Oracle).
        PollManagerFx.getInstance().refreshBadgesAsync();
    }

    /**
     * Loads {@code LoginView.fxml} as a fixed-size modal-style window.
     * On successful login, replaces the scene with the main view.
     * On cancel, exits the application.
     *
     * <p>Equivalent to the Swing {@code LoginDialog} + follow-on
     * {@code MainFrame} construction.
     */
    /**
     * Mở màn hình đặt món sau khi tài khoản TABLET đăng nhập thành công.
     *
     * <p>Luồng:
     * <ol>
     *   <li>Lấy tableId từ AppSession (đã được UserDAO.login() set từ users.table_id).</li>
     *   <li>Tìm hoặc tạo order PENDING cho bàn đó.</li>
     *   <li>Khởi động WebSocket (subscribe topic riêng của bàn).</li>
     *   <li>Mở TableOrderStage full-screen, đóng màn hình login.</li>
     * </ol>
     *
     * @param loginStage stage màn hình login (sẽ được đóng)
     */
    private void openTabletAfterLogin(Stage loginStage) {
        AppSession session = AppSession.getInstance();
        String tableId = session.getTableId();

        if (tableId == null || tableId.isBlank()) {
            FxUtils.showToast(loginStage,
                    "Tài khoản tablet chưa được gán bàn.\nVui lòng liên hệ quản lý.",
                    FxUtils.ToastType.ERROR, 5000);
            return;
        }

        // Lấy hoặc tạo order active cho bàn
        TabletOrderDAO tabletDao = new TabletOrderDAO(session.getRestaurantId());
        Order order = tabletDao.getOrCreateActiveOrder(tableId);

        if (order == null) {
            FxUtils.showToast(loginStage,
                    "Không tạo được đơn hàng cho bàn.\nVui lòng thử lại.",
                    FxUtils.ToastType.ERROR, 5000);
            return;
        }

        String tableName = tabletDao.getTableName(tableId);

        // Khởi động WebSocket — subscribe topic riêng của bàn
        try {
            RestaurantEventServer.getInstance().start();
        } catch (Exception e) {
            System.err.println("[Main] WS server lỗi (tablet): " + e.getMessage());
        }
        RestaurantEventClient wsClient = RestaurantEventClient.getInstance();
        wsClient.connect();
        wsClient.subscribe(WsTopic.forTable(Integer.parseInt(tableId)));
        // Lưu ý: admin side nhận thay đổi trạng thái bàn qua Oracle DCN → WS broadcast tự động

        // Mở màn hình đặt món full-screen
        TableOrderStage orderStage = new TableOrderStage(tableId, order.getId(), tableName);
        orderStage.setFullScreen(true);
        orderStage.setFullScreenExitHint("");
        // TableOrderStage.setupCloseHandler() xử lý đóng cửa sổ + cập nhật trạng thái bàn
        orderStage.show();

        loginStage.close();
    }

    private void openLoginView(Stage stage) {
        LoginController loginController = new LoginController();

        // Callback: invoked by LoginController after successful authentication
        loginController.setOnLoginSuccess(() -> {
            try {
                String role = AppSession.getInstance().getUserRole();

                // ── Tài khoản TABLET → mở thẳng màn hình đặt món ─────────────
                if ("TABLET".equalsIgnoreCase(role)) {
                    openTabletAfterLogin(stage);
                    return;
                }

                // ── Tài khoản nhân viên thông thường → mở MainView ────────────
                openMainView(stage);
            } catch (Exception e) {
                System.err.println("[Main] MainView failed after login: " + e.getMessage());
                e.printStackTrace();

                FxUtils.showToast(
                    stage,
                    "Lỗi khởi tạo giao diện: " + e.getMessage()
                    + "\nVui lòng thử đăng nhập lại.",
                    FxUtils.ToastType.ERROR,
                    5000);
            }
        });

        // Callback: invoked when the user closes the login window without logging in
        loginController.setOnLoginCancelled(Platform::exit);

        javafx.scene.Parent root = FxUtils.loadFxml("LoginView.fxml", loginController);

        Scene scene = new Scene(root, 440, 560);
        FxUtils.loadCss(scene);

        stage.setScene(scene);
        stage.setResizable(false);
        stage.centerOnScreen();
        stage.show();
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    /**
     * JVM entry point.  Delegates to {@link Application#launch} which
     * bootstraps the JavaFX platform, calls {@link #init()} on the launcher
     * thread, then calls {@link #start(Stage)} on the FX Application Thread.
     *
     * <p>System properties mirror the Swing {@code Main.main()} for
     * rendering consistency across platforms.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        // Encoding
        System.setProperty("file.encoding", "UTF-8");

        // Sub-pixel font rendering
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // JavaFX: keep DPI scaling at 1× (JavaFX handles HiDPI natively)
        // Remove or adjust on macOS Retina / Windows HiDPI if needed.
        System.setProperty("glass.win.uiScale", "100%");
        System.setProperty("glass.gtk.uiScale", "100%");

        Application.launch(Main.class, args);
    }
}