package com.restaurant.ui.fx.controller;

import com.restaurant.dao.UserDAO;
import com.restaurant.session.AuditLogger;
import com.restaurant.session.RefreshTokenService;
import com.restaurant.session.TokenStorage;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

/**
 * LoginController — JavaFX controller for {@code LoginView.fxml}.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Validate email + password inputs.</li>
 *   <li>Delegate authentication to {@link UserDAO#login}.</li>
 *   <li>Persist a refresh-token to disk when "Ghi nhớ đăng nhập" is checked.</li>
 *   <li>Notify the caller via {@link #setOnLoginSuccess} / {@link #setOnLoginCancelled}.</li>
 * </ul>
 *
 * <h3>Thread model</h3>
 * Login runs on a daemon {@link Task} thread so the UI stays responsive.
 * All UI updates are routed back to the FX Application Thread via
 * {@link Platform#runLater}.
 *
 * <p><b>File:</b>
 * {@code src/main/java/com/restaurant/ui/fx/controller/LoginController.java}
 */
public class LoginController {

    // ── FXML nodes ────────────────────────────────────────────────────────────

    @FXML private TextField     tfEmail;
    @FXML private HBox          boxEmail;

    @FXML private PasswordField pfPassword;
    @FXML private TextField     tfPasswordVisible;
    @FXML private HBox          boxPassword;
    @FXML private Button        btnTogglePass;

    @FXML private CheckBox      chkRemember;
    @FXML private Label         lblError;
    @FXML private Button        btnLogin;

    // ── Callbacks set by Main ─────────────────────────────────────────────────

    private Runnable onLoginSuccess;
    private Runnable onLoginCancelled;

    // ── State ─────────────────────────────────────────────────────────────────

    private boolean passwordVisible = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Called by the FX runtime after all @FXML fields are injected.
     * Wire up Enter-key shortcuts and keep both password views in sync.
     */
    @FXML
    private void initialize() {
        // Enter key on email field → focus password
        tfEmail.setOnAction(e -> pfPassword.requestFocus());

        // Enter key on either password view → submit login
        pfPassword.setOnAction(e -> onLogin());
        tfPasswordVisible.setOnAction(e -> onLogin());

        // Keep visible/hidden password fields in sync
        tfPasswordVisible.textProperty().bindBidirectional(pfPassword.textProperty());

        // Focus email on open
        Platform.runLater(() -> tfEmail.requestFocus());
    }

    // ── Public API (called by Main) ───────────────────────────────────────────

    /** Callback invoked on the FX thread after a successful login. */
    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }

    /**
     * Callback invoked on the FX thread when the user closes the window
     * without logging in (not currently wired to a button, but Main wires it
     * to the stage close-request handler).
     */
    public void setOnLoginCancelled(Runnable callback) {
        this.onLoginCancelled = callback;
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    /** Toggle show / hide password. */
    @FXML
    private void onTogglePassword() {
        passwordVisible = !passwordVisible;

        pfPassword.setVisible(!passwordVisible);
        pfPassword.setManaged(!passwordVisible);
        tfPasswordVisible.setVisible(passwordVisible);
        tfPasswordVisible.setManaged(passwordVisible);

        btnTogglePass.setText(passwordVisible ? "🙈" : "👁");

        // Move caret to end of revealed field
        if (passwordVisible) {
            tfPasswordVisible.positionCaret(tfPasswordVisible.getText().length());
            tfPasswordVisible.requestFocus();
        } else {
            pfPassword.positionCaret(pfPassword.getText().length());
            pfPassword.requestFocus();
        }
    }

    /** Primary action: validate inputs, then run auth on a background thread. */
    @FXML
    private void onLogin() {
        clearError();
        clearFieldErrors();

        String email    = tfEmail.getText().trim();
        String password = pfPassword.getText();

        // ── Client-side validation ────────────────────────────────────────
        if (email.isEmpty()) {
            shakeField(boxEmail);
            showError("Vui lòng nhập địa chỉ email.");
            tfEmail.requestFocus();
            return;
        }
        if (!email.contains("@") || !email.contains(".")) {
            setFieldError(boxEmail);
            showError("Địa chỉ email không hợp lệ.");
            tfEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            shakeField(boxPassword);
            showError("Vui lòng nhập mật khẩu.");
            pfPassword.requestFocus();
            return;
        }

        // ── Check account lock (fast, synchronous) ────────────────────────
        if (AuditLogger.getInstance().isAccountLocked(email)) {
            setFieldError(boxEmail);
            showError("Tài khoản đang bị khoá tạm thời do đăng nhập sai nhiều lần.\n"
                    + "Vui lòng thử lại sau vài phút.");
            return;
        }

        // ── Run auth on background thread ─────────────────────────────────
        setLoading(true);

        Task<Boolean> loginTask = new Task<>() {
            @Override
            protected Boolean call() {
                return new UserDAO().login(email, password);
            }
        };

        loginTask.setOnSucceeded(evt -> {
            setLoading(false);
            boolean ok = loginTask.getValue();

            if (ok) {
                // ── Persist refresh-token if "Ghi nhớ" is checked ──────────
                if (chkRemember.isSelected()) {
                    try {
                        String token = RefreshTokenService.getInstance()
                                .generateRefreshToken(getUserIdFromSession());
                        TokenStorage.getInstance().saveRefreshToken(token);
                    } catch (Exception ex) {
                        System.err.println("[LoginController] Không lưu được refresh-token: "
                                + ex.getMessage());
                        // Non-fatal — login still succeeded
                    }
                } else {
                    TokenStorage.getInstance().clearSavedToken();
                }

                if (onLoginSuccess != null) {
                    onLoginSuccess.run();
                }
            } else {
                setFieldError(boxEmail);
                setFieldError(boxPassword);
                shakeField(boxPassword);
                showError("Email hoặc mật khẩu không đúng.\n"
                        + "Vui lòng kiểm tra lại thông tin đăng nhập.");
                pfPassword.clear();
                if (passwordVisible) tfPasswordVisible.clear();
                pfPassword.requestFocus();
            }
        });

        loginTask.setOnFailed(evt -> {
            setLoading(false);
            Throwable ex = loginTask.getException();
            System.err.println("[LoginController] Login task lỗi: " + ex.getMessage());
            showError("Lỗi kết nối cơ sở dữ liệu.\nVui lòng kiểm tra kết nối và thử lại.");
        });

        Thread t = new Thread(loginTask, "login-task");
        t.setDaemon(true);
        t.start();
    }

    /** Open forgot-password flow (show informational alert for now). */
    @FXML
    private void onForgotPassword() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Quên mật khẩu");
        alert.setHeaderText("Đặt lại mật khẩu");
        alert.setContentText(
                "Vui lòng liên hệ quản trị viên hệ thống để đặt lại mật khẩu.\n\n"
                + "Email hỗ trợ: admin@smartrestaurant.vn");
        alert.showAndWait();
    }

    /**
     * Mở màn hình đăng ký nhà hàng công khai ({@code RestaurantRegistrationView.fxml}).
     * Thay thế scene hiện tại; khi người dùng nhấn "Quay lại" sẽ restore scene đăng nhập.
     */
    @FXML
    private void onRegister() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) btnLogin.getScene().getWindow();
            javafx.scene.Scene loginScene = btnLogin.getScene();

            RestaurantRegistrationController regCtrl = new RestaurantRegistrationController();

            // Callback: quay về màn hình đăng nhập
            regCtrl.setOnBack(() -> {
                stage.setScene(loginScene);
                stage.setTitle("SmartRestaurant — Đăng nhập");
                stage.setResizable(false);
                stage.setWidth(440);
                stage.setHeight(560);
                stage.centerOnScreen();
                Platform.runLater(() -> tfEmail.requestFocus());
            });

            javafx.scene.Parent regRoot =
                    com.restaurant.ui.fx.util.FxUtils.loadFxml(
                            "RestaurantRegistrationView.fxml", regCtrl);

            javafx.scene.Scene regScene = new javafx.scene.Scene(regRoot, 500, 680);
            com.restaurant.ui.fx.util.FxUtils.loadCss(regScene);

            stage.setScene(regScene);
            stage.setTitle("SmartRestaurant — Đăng ký nhà hàng");
            stage.setResizable(true);
            stage.setMinWidth(480);
            stage.setMinHeight(580);
            stage.centerOnScreen();

        } catch (Exception ex) {
            System.err.println("[LoginController] Không thể mở màn hình đăng ký: "
                    + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    /**
     * Show / hide the error label with a fade-in animation.
     *
     * @param message non-null, non-empty message to display
     */
    private void showError(String message) {
        lblError.setText(message);
        lblError.setVisible(true);
        lblError.setManaged(true);

        FadeTransition ft = new FadeTransition(Duration.millis(200), lblError);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void clearError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        lblError.setText("");
    }

    /** Highlight a field box in red (error state). */
    private void setFieldError(HBox fieldBox) {
        fieldBox.getStyleClass().remove("login-field-box-error");
        fieldBox.getStyleClass().add("login-field-box-error");
    }

    private void clearFieldErrors() {
        boxEmail.getStyleClass().remove("login-field-box-error");
        boxPassword.getStyleClass().remove("login-field-box-error");
    }

    /**
     * Brief horizontal shake animation on a field to draw attention.
     * Uses a lightweight translate-X sequence.
     */
    private void shakeField(HBox fieldBox) {
        javafx.animation.TranslateTransition shake =
                new javafx.animation.TranslateTransition(Duration.millis(60), fieldBox);
        shake.setByX(8);
        shake.setCycleCount(4);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> fieldBox.setTranslateX(0));
        shake.play();
    }

    /**
     * Disable inputs and change the button text during the network call.
     *
     * @param loading {@code true} = disable; {@code false} = re-enable
     */
    private void setLoading(boolean loading) {
        btnLogin.setDisable(loading);
        tfEmail.setDisable(loading);
        pfPassword.setDisable(loading);
        tfPasswordVisible.setDisable(loading);
        chkRemember.setDisable(loading);
        btnTogglePass.setDisable(loading);

        btnLogin.setText(loading ? "Đang đăng nhập..." : "ĐĂNG NHẬP");
    }

    /**
     * Retrieve the userId of the currently logged-in session.
     * Delegates to {@link com.restaurant.session.AppSession}.
     */
    private long getUserIdFromSession() {
        return com.restaurant.session.AppSession.getInstance().getUserId();
    }
}