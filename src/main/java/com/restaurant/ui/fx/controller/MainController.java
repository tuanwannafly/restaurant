package com.restaurant.ui.fx.controller;

import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

import com.restaurant.model.Restaurant;
import com.restaurant.session.AppSession;
import com.restaurant.session.AppSession.SessionListener;
import com.restaurant.session.TokenService;
import com.restaurant.ui.SidebarController;
import com.restaurant.ui.TopBarController;
import com.restaurant.ui.control.BadgeLabel;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * MainController
 * ──────────────
 * Root controller for {@code MainView.fxml}.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Wire {@link SidebarController} and {@link TopBarController} after FXML load.</li>
 *   <li>Build and own the content {@link StackPane} — each page panel is
 *       added once and shown/hidden via {@link Node#setVisible}.</li>
 *   <li>Implement {@link SessionListener#onLogout()} — tear down and reopen
 *       {@code LoginDialog}, then create a new {@code MainStage} on success.</li>
 *   <li>Run a periodic session-validity timer (same 30-min cadence as Swing version).</li>
 * </ol>
 *
 * <h2>Navigation</h2>
 * <pre>{@code
 *   mainController.navigateTo("bep");   // switches content + updates sidebar active state
 * }</pre>
 *
 * <h2>Badge update</h2>
 * <pre>{@code
 *   mainController.getKitchenBadge().setCount(3);
 * }</pre>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>FXMLLoader loads {@code MainView.fxml} and calls {@link #initialize}.</li>
 *   <li>{@link #initialize} calls {@link SidebarController#build()} and wires
 *       the sidebar into the BorderPane.</li>
 *   <li>{@link #initPanels()} instantiates page panels and registers them with
 *       the content StackPane.</li>
 *   <li>{@link #navigateTo(String)} is called with {@code "home"} to show the
 *       initial page.</li>
 * </ol>
 */
public class MainController implements Initializable, SessionListener {

    // ── Session check interval — matches Swing version ────────────────────
    private static final int SESSION_CHECK_MINUTES = 30;

    // ── FXML injections ───────────────────────────────────────────────────
    @FXML private BorderPane rootPane;
    @FXML private VBox       sidebarPlaceholder;
    @FXML private StackPane  contentArea;

    // Injected by FXMLLoader for the <fx:include> of TopBarView.fxml:
    @FXML private TopBarController topBarController; // naming: <fxid>Controller

    // ── Child controllers ──────────────────────────────────────────────────
    private SidebarController sidebarCtrl;

    // ── Page registry ─────────────────────────────────────────────────────
    //   pageKey → Node (page panel); only one is visible at a time.
    private final Map<String, Node>     pages           = new LinkedHashMap<>();
    //   pageKey → controller object; used by callMethod() to invoke loadData() etc.
    private final Map<String, Object>   pageControllers = new HashMap<>();
    //   pageKey → Runnable called on navigateTo() to refresh the panel's data
    private final Map<String, Runnable> refreshHooks    = new HashMap<>();

    // ── Session timer ─────────────────────────────────────────────────────
    private Timeline sessionCheckTimeline;

    // ── Active page ───────────────────────────────────────────────────────
    private String activePage = "";

    // ── Initializable ─────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Register this controller as a session listener (WeakReference safe)
        AppSession.getInstance().addSessionListener(this);

        // Build sidebar and replace the placeholder VBox
        sidebarCtrl = new SidebarController(this::navigateTo);
        sidebarCtrl.build();
        rootPane.setLeft(sidebarCtrl.getRoot());

        // Init page panels
        initPanels();
        registerPages();

        // Navigate to home
        var guard = com.restaurant.session.RbacGuard.getInstance();
        navigateTo(guard.isChef()    ? "bep"    :
                   guard.isWaiter()  ? "phucvu" :
                   guard.isCashier() ? "thungan":
                                       "home");

        // Periodic session check
        startSessionCheckTimer();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Shows the page identified by {@code pageKey}, refreshes its data, and
     * updates the sidebar active state.
     *
     * @param pageKey one of the keys registered in {@link #registerPages()}
     */
    public void navigateTo(String pageKey) {
        if (Objects.equals(activePage, pageKey)) return;
        activePage = pageKey;

        // Show the target page, hide all others
        pages.forEach((key, node) -> {
            boolean show = key.equals(pageKey);
            node.setVisible(show);
            node.setManaged(show);
        });

        // Update sidebar active highlight
        sidebarCtrl.setActivePage(pageKey);

        // Trigger data refresh hook
        Runnable hook = refreshHooks.get(pageKey);
        if (hook != null) hook.run();
    }

    /** Convenience accessor for badge count updates from polling services. */
    public BadgeLabel getKitchenBadge() { return sidebarCtrl.getKitchenBadge(); }
    public BadgeLabel getWaiterBadge()  { return sidebarCtrl.getWaiterBadge();  }
    public BadgeLabel getCashierBadge() { return sidebarCtrl.getCashierBadge(); }
    public BadgeLabel getRequestBadge() { return sidebarCtrl.getRequestBadge(); }

    /**
     * Phase 4: Lấy controller chi tiết đơn đăng ký nhà hàng.
     * Dùng bởi RestaurantRequestListController để wire navigation "Xem chi tiết".
     *
     * @return controller hoặc {@code null} nếu chưa load (non-SUPER_ADMIN)
     */
    public RestaurantRequestDetailController getRequestDetailController() {
        Object ctrl = pageControllers.get("request_detail");
        return (ctrl instanceof RestaurantRequestDetailController rdc) ? rdc : null;
    }

    // ── SessionListener ────────────────────────────────────────────────────

    /**
     * Called on any thread when {@link AppSession#logout()} is invoked.
     * Tear-down + re-open LoginDialog on the FX thread.
     */
    @Override
    public void onLogout() {
        Platform.runLater(() -> {
            stopSessionCheckTimer();

            // Stop any background polling
            try {
                com.restaurant.ui.fx.util.PollManagerFx.getInstance().stopAll();
            } catch (Exception ex) {
                System.err.println("[MainController] PollManagerFx.stopAll error: " + ex.getMessage());
            }

            // Close the main window
            Stage mainStage = (Stage) rootPane.getScene().getWindow();
            mainStage.close();

            // Re-open login
            openLoginAndRespawn();
        });
    }

    // ── Private: page init ─────────────────────────────────────────────────

    /**
     * Instantiates all page panels that are relevant for the current session.
     * Panels are created once and reused (same pattern as Swing version).
     * <p>
     * <b>Pattern:</b> create panel → add to {@code contentArea} (invisible) →
     * register with {@link #pages} and {@link #refreshHooks}.
     * <p>
     * This method is the JavaFX equivalent of {@code MainFrame#initPanels()}
     * and {@code #wireContentArea()}.
     */
    private void initPanels() {
        var guard   = com.restaurant.session.RbacGuard.getInstance();
        boolean sup = guard.isSuperAdmin();
        boolean adm = guard.isRestaurantAdmin();

        // ── Always-present panels ────────────────────────────────────────
        addPanel("home",          createPanel("home",     "HomeView"),     null);
        addPanel("menu",          createPanel("menu",     "MenuView"),     () -> callMethod("menu",          "loadData"));
        addPanel("ban",           createPanel("ban",      "TableView"),    () -> callMethod("ban",           "loadData"));
        addPanel("nhanvien",      createPanel("nhanvien", "EmployeeView"), () -> callMethod("nhanvien",      "loadData"));
        addPanel("donhang",       createPanel("donhang",  "OrderView"),    () -> callMethod("donhang",       "loadData"));
        addPanel("chedomlamviec", createPlaceholder("Ca làm việc"),        null);
        addPanel("baocao",        createPanel("baocao",   "ReportView"),   () -> callMethod("baocao",        "loadData"));
        addPanel("thongke",       createPanel("thongke",  "StatsView"),    () -> callMethod("thongke",       "loadAll"));
        addPanel("bep",           createPanel("bep",      "KitchenView"),  () -> callMethod("bep",           "loadData"));
        addPanel("phucvu",        createPanel("phucvu",   "WaiterView"),   () -> callMethod("phucvu",        "loadData"));
        addPanel("thungan",       createPanel("thungan",  "CashierView"),  () -> callMethod("thungan",       "loadData"));

        // ── Super-admin-only ─────────────────────────────────────────────
        if (sup) {
            // ── Nhà hàng: load với explicit FXMLLoader để lấy controller và wire callbacks ──
            try {
                java.net.URL restaurantFxml = getClass().getResource("/fxml/RestaurantView.fxml");
                java.net.URL detailFxml     = getClass().getResource("/fxml/RestaurantDetailView.fxml");

                FXMLLoader restaurantLoader = new FXMLLoader(restaurantFxml);
                Node restaurantNode = restaurantLoader.load();
                RestaurantController restaurantCtrl = restaurantLoader.getController();

                FXMLLoader detailLoader = new FXMLLoader(detailFxml);
                Node detailNode = detailLoader.load();
                RestaurantDetailController detailCtrl = detailLoader.getController();

                // Wire "Xem chi tiết" → điền data + chuyển sang panel detail
                restaurantCtrl.setOnOpenDetail((Restaurant r) -> {
                    detailCtrl.populate(r);
                    navigateTo("restaurant_detail");
                });

                // Wire "Quay lại" → về danh sách
                detailCtrl.setOnBack(() -> navigateTo("nhahangs"));

                addPanel("nhahangs",         restaurantNode, restaurantCtrl::loadData);
                addPanel("restaurant_detail", detailNode,    null);

            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                System.err.println("[MainController] Lỗi load RestaurantView: " + cause.getMessage());
                addPanel("nhahangs",         createPlaceholder("Nhà hàng"), null);
                addPanel("restaurant_detail", createPlaceholder("Chi tiết nhà hàng"), null);
            }

            addPanel("baomat",     createPanel("baomat",     "AuditLogView"),   () -> callMethod("baomat",     "loadData"));
            addPanel("adminstats", createPanel("adminstats", "AdminStatsView"), () -> callMethod("adminstats", "loadStats"));

            // ── Phase 3: Danh sách đơn đăng ký nhà hàng ─────────────────────
            try {
                java.net.URL reqListFxml = getClass().getResource(
                        "/fxml/RestaurantRequestListView.fxml");
                FXMLLoader reqListLoader = new FXMLLoader(reqListFxml);
                Node reqListNode = reqListLoader.load();
                RestaurantRequestListController reqListCtrl = reqListLoader.getController();

                // Wire "Xem chi tiết" → populate detail + navigate
                // CODE MỚI - ĐÃ FIX
                reqListCtrl.setOnOpenDetail(req -> {
                    RestaurantRequestDetailController detailCtrl = getRequestDetailController();
                    if (detailCtrl != null) {
                        detailCtrl.populate(req);
                        navigateTo("request_detail");  // ← chuyển trang CHỈ KHI populate thành công
                    } else {
                        System.err.println("[MainController] request_detail controller chưa sẵn sàng");
                    }
                });

                addPanel("dondk", reqListNode, reqListCtrl::loadData);
                pageControllers.put("dondk", reqListCtrl);

            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                System.err.println("[MainController] Lỗi load RestaurantRequestListView: "
                        + cause.getMessage());
                addPanel("dondk", createPlaceholder("Đơn đăng ký"), null);
            }

            // ── Phase 4: Đơn đăng ký nhà hàng — chi tiết + phê duyệt ────────
            try {
                java.net.URL reqDetailFxml = getClass().getResource(
                        "/fxml/RestaurantRequestDetailView.fxml");
                FXMLLoader reqDetailLoader = new FXMLLoader(reqDetailFxml);
                Node reqDetailNode = reqDetailLoader.load();
                RestaurantRequestDetailController reqDetailCtrl = reqDetailLoader.getController();

                // Wire "Quay lại" → về danh sách đơn đăng ký + refresh
                reqDetailCtrl.setOnBack(() -> navigateTo("dondk"));

                addPanel("request_detail", reqDetailNode, null);

                // Expose detail controller để RestaurantRequestListController có thể wire
                pageControllers.put("request_detail", reqDetailCtrl);

            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                System.err.println("[MainController] Lỗi load RestaurantRequestDetailView: "
                        + cause.getMessage());
                addPanel("request_detail", createPlaceholder("Chi tiết đơn đăng ký"), null);
            }

        } else {
            addPanel("nhahangs",          createPlaceholder("Nhà hàng"),          null);
            addPanel("restaurant_detail", createPlaceholder("Chi tiết nhà hàng"), null);
            addPanel("baomat",            createPlaceholder("Bảo mật"),           null);
            addPanel("adminstats",        createPlaceholder("Thống kê Admin"),    null);
            addPanel("request_detail",    createPlaceholder("Chi tiết đơn đăng ký"), null);
            addPanel("dondk",             createPlaceholder("Đơn đăng ký"),       null);
        }

        // ── Restaurant-admin-only ────────────────────────────────────────
        if (adm) {
            addPanel("myrestaurant", createPanel("myrestaurant", "MyRestaurantView"),
                     () -> callMethod("myrestaurant", "loadData"));
        } else {
            addPanel("myrestaurant", createPlaceholder("Nhà hàng của tôi"), null);
        }
    }

    /**
     * Add page to content area and registry (initially hidden).
     */
    private void addPanel(String key, Node node, Runnable refreshHook) {
        node.setVisible(false);
        node.setManaged(false);
        contentArea.getChildren().add(node);
        pages.put(key, node);
        if (refreshHook != null) refreshHooks.put(key, refreshHook);
    }

    /** Maps page entries into the content StackPane — called from initPanels(). */
    private void registerPages() {
        // Already done inline in initPanels(); left as hook for subclasses.
    }

    // ── Private: panel factories ───────────────────────────────────────────

    /**
     * Attempts to load a page panel via {@code FXML} or plain instantiation.
     *
     * <p>Naming convention: tries
     * {@code /fxml/<SimpleName>.fxml} first, then falls back to
     * {@code com.restaurant.ui.<SimpleName>} constructor.
     *
     * @param pageKey    navigation key used to store the controller (e.g. {@code "menu"})
     * @param simpleName FXML file base name (e.g. {@code "MenuView"})
     * @return the loaded root {@link Node}, or a placeholder on failure
     */
    private Node createPanel(String pageKey, String simpleName) {
        // 1. Try FXML — check /fxml/ first, then /com/restaurant/ui/ for views stored there
        URL fxml = getClass().getResource("/fxml/" + simpleName + ".fxml");
        if (fxml == null) {
            fxml = getClass().getResource("/com/restaurant/ui/" + simpleName + ".fxml");
        }
        if (fxml != null) {
            try {
                FXMLLoader loader = new FXMLLoader(fxml);
                Node node = loader.load();
                // ── BUG FIX: store controller so callMethod() can invoke loadData() etc. ──
                Object ctrl = loader.getController();
                if (ctrl != null) pageControllers.put(pageKey, ctrl);
                return node;
            } catch (Exception ex) {
                // Catch Exception (not just IOException) so RuntimeException thrown by
                // custom components (e.g. StatCard failing to load its sub-FXML) does
                // not propagate out of initPanels() and prevent subsequent panels
                // (including the Restaurant panel) from being registered.
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                System.err.println("[MainController] FXML load failed for " + simpleName
                        + ": " + cause.getMessage());
            }
        }

        // 2. Try direct instantiation (legacy panels that extend a Node subclass)
        try {
            Class<?> cls = Class.forName("com.restaurant.ui." + simpleName);
            Object inst = cls.getDeclaredConstructor().newInstance();
            if (inst instanceof Node n) {
                pageControllers.put(pageKey, inst);
                return n;
            }
        } catch (Exception ex) {
            // Panel not yet ported to JavaFX
        }

        // 3. Placeholder
        return createPlaceholder(simpleName.replace("Panel", "").replace("View", ""));
    }

    /** Builds a centred label placeholder for panels not yet implemented. */
    private Node createPlaceholder(String name) {
        javafx.scene.control.Label lbl =
                new javafx.scene.control.Label(name + " — Đang phát triển");
        lbl.getStyleClass().add("placeholder-label");
        StackPane sp = new StackPane(lbl);
        sp.getStyleClass().add("placeholder-pane");
        return sp;
    }

    /**
     * Reflectively invokes a no-arg method on the <em>controller</em> registered
     * under {@code pageKey} (e.g. {@code loadData()}, {@code loadAll()}).
     *
     * <p>Previously this method called the method on the root {@link Node}, which
     * always failed with {@link NoSuchMethodException} because standard JavaFX
     * containers (BorderPane, VBox …) have no application-level methods.
     * The fix uses {@link #pageControllers} which stores the actual FXMLLoader
     * controller objects.
     *
     * <p>Silently swallows {@link NoSuchMethodException} so that placeholder
     * panels don't crash navigation.
     */
    private void callMethod(String pageKey, String methodName) {
        Object ctrl = pageControllers.get(pageKey);
        if (ctrl == null) return;
        try {
            ctrl.getClass().getMethod(methodName).invoke(ctrl);
        } catch (NoSuchMethodException ignored) {
            // Controller does not expose this method — safe to skip
        } catch (Exception ex) {
            System.err.println("[MainController] " + methodName + " on " + pageKey
                    + " failed: " + ex.getMessage());
        }
    }

    // ── Private: session timer ─────────────────────────────────────────────

    private void startSessionCheckTimer() {
        sessionCheckTimeline = new Timeline(
            new KeyFrame(Duration.minutes(SESSION_CHECK_MINUTES), e -> checkSession())
        );
        sessionCheckTimeline.setCycleCount(Timeline.INDEFINITE);
        sessionCheckTimeline.play();
    }

    private void stopSessionCheckTimer() {
        if (sessionCheckTimeline != null) sessionCheckTimeline.stop();
    }

    private void checkSession() {
        String  token = AppSession.getInstance().getSessionToken();
        boolean valid = TokenService.getInstance().validateToken(token);

        // Cleanup expired tokens off the FX thread
        new Thread(() -> TokenService.getInstance().cleanExpiredTokens(),
                   "token-cleanup").start();

        if (!valid) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.",
                    ButtonType.OK);
            alert.setTitle("Hết phiên");
            alert.showAndWait();
            AppSession.getInstance().logout();   // triggers onLogout()
        }
    }

    // ── Private: logout flow ───────────────────────────────────────────────

    /**
     * Closes the current main window and re-opens the login screen.
     * On successful login a new MainController + MainView are spawned.
     * On cancel the application exits.
     */
    private void openLoginAndRespawn() {
        // Đóng cửa sổ hiện tại
        Stage currentStage = (Stage) rootPane.getScene().getWindow();

        LoginController loginController = new LoginController();

        loginController.setOnLoginSuccess(() -> {
            try {
                MainController newMain = new MainController();
                javafx.scene.Parent root =
                        com.restaurant.ui.fx.util.FxUtils.loadFxml("MainView.fxml", newMain);

                javafx.scene.Scene scene = new javafx.scene.Scene(root, 1280, 780);
                com.restaurant.ui.fx.util.FxUtils.loadCss(scene);

                Stage newStage = new Stage();
                newStage.setTitle("SmartRestaurant");
                newStage.setMinWidth(900);
                newStage.setMinHeight(600);
                newStage.setScene(scene);
                newStage.setOnCloseRequest(e -> Platform.exit());
                newStage.show();
                currentStage.close();
            } catch (Exception e) {
                e.printStackTrace();
                Platform.exit();
            }
        });

        loginController.setOnLoginCancelled(Platform::exit);

        try {
            javafx.scene.Parent loginRoot =
                    com.restaurant.ui.fx.util.FxUtils.loadFxml("LoginView.fxml", loginController);

            javafx.scene.Scene loginScene = new javafx.scene.Scene(loginRoot, 440, 560);
            com.restaurant.ui.fx.util.FxUtils.loadCss(loginScene);

            Stage loginStage = new Stage();
            loginStage.setTitle("Đăng nhập — SmartRestaurant");
            loginStage.setResizable(false);
            loginStage.setScene(loginScene);
            loginStage.centerOnScreen();
            loginStage.show();
            currentStage.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            Platform.exit();
        }
    }
}