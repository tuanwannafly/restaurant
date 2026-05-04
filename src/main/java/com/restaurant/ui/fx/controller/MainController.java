package com.restaurant.ui.fx.controller;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

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
    private final Map<String, Node>     pages         = new LinkedHashMap<>();
    //   pageKey → Runnable called on navigateTo() to refresh the panel's data
    private final Map<String, Runnable> refreshHooks  = new HashMap<>();

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
        navigateTo("home");

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
        addPanel("home",          createPanel("HomeView"),          null);
        addPanel("menu",          createPanel("MenuView"),          () -> callMethod("menu",     "loadData"));
        addPanel("ban",           createPanel("TableView"),         () -> callMethod("ban",      "loadData"));
        addPanel("nhanvien",      createPanel("EmployeeView"),      () -> callMethod("nhanvien", "loadData"));
        addPanel("donhang",       createPanel("OrderView"),         () -> callMethod("donhang",  "loadData"));
        addPanel("chedomlamviec", createPlaceholder("Ca làm việc"),  null);
        addPanel("baocao",        createPanel("ReportView"),        () -> callMethod("baocao",   "loadData"));
        addPanel("thongke",       createPanel("StatsView"),         () -> callMethod("thongke",  "loadAll"));
        addPanel("bep",           createPanel("KitchenView"),       () -> callMethod("bep",      "loadData"));
        addPanel("phucvu",        createPanel("WaiterView"), () -> callMethod("phucvu",   "loadData"));
        addPanel("thungan",       createPanel("CashierView"),       () -> callMethod("thungan",  "loadData"));

        // ── Super-admin-only ─────────────────────────────────────────────
        if (sup) {
            addPanel("nhahangs",   createPanel("RestaurantView"),  () -> callMethod("nhahangs",  "loadData"));
            addPanel("baomat",     createPanel("AuditLogView"),    () -> callMethod("baomat",    "loadData"));
            addPanel("adminstats", createPanel("AdminStatsView"),  () -> callMethod("adminstats","loadStats"));
        } else {
            addPanel("nhahangs",   createPlaceholder("Nhà hàng"),     null);
            addPanel("baomat",     createPlaceholder("Bảo mật"),      null);
            addPanel("adminstats", createPlaceholder("Thống kê Admin"),null);
        }

        // ── Restaurant-admin-only ────────────────────────────────────────
        if (adm) {
            addPanel("myrestaurant", createPanel("MyRestaurantView"),
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
     * @param simpleName simple class / FXML name (e.g. "HomePanel")
     * @return the loaded {@link Node}, or a placeholder on failure
     */
    private Node createPanel(String simpleName) {
        // 1. Try FXML — check /fxml/ first, then /com/restaurant/ui/ for views stored there
        URL fxml = getClass().getResource("/fxml/" + simpleName + ".fxml");
        if (fxml == null) {
            fxml = getClass().getResource("/com/restaurant/ui/" + simpleName + ".fxml");
        }
        if (fxml != null) {
            try {
                FXMLLoader loader = new FXMLLoader(fxml);
                return loader.load();
            } catch (IOException ex) {
                System.err.println("[MainController] FXML load failed for " + simpleName
                        + ": " + ex.getMessage());
            }
        }

        // 2. Try direct instantiation
        try {
            Class<?> cls = Class.forName("com.restaurant.ui." + simpleName);
            Object inst = cls.getDeclaredConstructor().newInstance();
            if (inst instanceof Node n) return n;
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
     * Reflectively invokes a no-arg method on the panel registered under
     * {@code pageKey} (e.g. {@code loadData()}, {@code loadAll()}).
     * Silently swallows {@link NoSuchMethodException} so that stub panels
     * don't crash navigation.
     */
    private void callMethod(String pageKey, String methodName) {
        Node node = pages.get(pageKey);
        if (node == null) return;
        try {
            node.getClass().getMethod(methodName).invoke(node);
        } catch (NoSuchMethodException ignored) {
            // Panel not yet implemented or uses a different API
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
     * Opens {@code LoginDialog}; if login succeeds spawns a new
     * {@link MainStage}, otherwise exits the application.
     */
    private void openLoginAndRespawn() {
        try {
            // Load LoginDialog (FXML or Stage)
            Class<?> loginClass = Class.forName("com.restaurant.ui.LoginDialog");
            Object loginDlg = loginClass.getDeclaredConstructor().newInstance();
            loginClass.getMethod("showAndWait").invoke(loginDlg);

            boolean success = (boolean) loginClass.getMethod("isLoginSuccess").invoke(loginDlg);
            if (success) {
                // Reopen main window
                Class<?> mainStageClass = Class.forName("com.restaurant.ui.MainStage");
                Object   mainStage      = mainStageClass.getDeclaredConstructor().newInstance();
                mainStageClass.getMethod("show").invoke(mainStage);
            } else {
                Platform.exit();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Platform.exit();
        }
    }
}