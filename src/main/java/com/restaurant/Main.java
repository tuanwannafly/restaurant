package com.restaurant;

import java.util.Optional;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import com.restaurant.dao.UserDAO;
import com.restaurant.data.DataManager;
import com.restaurant.session.AppSession;
import com.restaurant.session.RefreshTokenService;
import com.restaurant.session.TokenStorage;
import com.restaurant.ui.fx.controller.LoginController;
import com.restaurant.ui.fx.controller.MainController;
import com.restaurant.ui.fx.util.FxUtils;
import com.restaurant.ui.fx.util.PollManagerFx;

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
     * {@inheritDoc}
     *
     * <p>Called when the application is about to exit (last window closed or
     * {@link Platform#exit()} called).  Stops all polling timers so no
     * background work runs after the session ends.
     */
    @Override
    public void stop() {
        // Ensure all Timelines are stopped (parallel to Swing MainFrame.handleLogout)
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

        // Wire badge updater into PollManagerFx
        PollManagerFx.getInstance().setBadgeUpdater((k, w, p) -> {
            controller.getKitchenBadge().setCount(k);
            controller.getWaiterBadge() .setCount(w);
            controller.getCashierBadge().setCount(p);
        });
        PollManagerFx.getInstance().registerBadgeRefresh(10_000);
    }

    /**
     * Loads {@code LoginView.fxml} as a fixed-size modal-style window.
     * On successful login, replaces the scene with the main view.
     * On cancel, exits the application.
     *
     * <p>Equivalent to the Swing {@code LoginDialog} + follow-on
     * {@code MainFrame} construction.
     */
    private void openLoginView(Stage stage) {
        LoginController loginController = new LoginController();

        // Callback: invoked by LoginController after successful authentication
        loginController.setOnLoginSuccess(() -> {
            try {
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

                // Allow user to try again rather than hard-exiting
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