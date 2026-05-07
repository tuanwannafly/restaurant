package com.restaurant.ui.fx.controller;

import com.restaurant.dao.PasswordResetDAO;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.net.URL;

/**
 * ForgotPasswordController — JavaFX controller cho {@code ForgotPasswordView.fxml}.
 *
 * <h3>Trách nhiệm</h3>
 * <ul>
 *   <li>Validate địa chỉ email nhập vào.</li>
 *   <li>Gọi {@link PasswordResetDAO#generateOtp(String)} trên background Task.</li>
 *   <li>Khi thành công: đóng cửa sổ này và mở {@code ResetPasswordView.fxml},
 *       truyền email và tham chiếu đến loginStage.</li>
 *   <li>Khi huỷ: đóng cửa sổ này và khôi phục màn hình login.</li>
 * </ul>
 *
 * <h3>Thread model</h3>
 * Lệnh gọi {@code generateOtp} chạy trên daemon {@link Task} thread.
 * Mọi cập nhật UI đều được đưa về FX Application Thread qua
 * {@link Platform#runLater}.
 *
 * <p><b>File:</b>
 * {@code src/main/java/com/restaurant/ui/fx/controller/ForgotPasswordController.java}
 */
public class ForgotPasswordController {

    // ── FXML nodes ────────────────────────────────────────────────────────────

    @FXML private TextField tfEmail;
    @FXML private HBox      boxEmail;
    @FXML private Label     lblMessage;
    @FXML private Button    btnSend;

    // ── State injected by caller ───────────────────────────────────────────────

    /**
     * Tham chiếu đến Stage login — dùng để khôi phục cửa sổ khi người dùng
     * huỷ hoặc chuyển sang màn hình ResetPassword.
     */
    private Stage loginStage;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Gọi sau khi tất cả @FXML field được inject. */
    @FXML
    private void initialize() {
        // Enter trên email field → submit
        tfEmail.setOnAction(e -> onSendOtp());
        Platform.runLater(() -> tfEmail.requestFocus());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Đặt tham chiếu đến Stage login để có thể khôi phục sau khi modal đóng.
     * Phải được gọi bởi {@code LoginController} trước khi hiển thị Stage này.
     *
     * @param loginStage Stage chứa LoginView, hiện đang bị ẩn
     */
    public void setLoginStage(Stage loginStage) {
        this.loginStage = loginStage;
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    /**
     * Xử lý nút "Gửi mã xác nhận".
     * Validate email → chạy {@link PasswordResetDAO#generateOtp} trên background Task
     * → khi thành công đóng cửa sổ này và mở {@code ResetPasswordView.fxml}.
     */
    @FXML
    private void onSendOtp() {
        clearMessage();
        clearFieldError();

        String email = tfEmail.getText().trim();

        // ── Client-side validation ─────────────────────────────────────────
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

        // ── Chạy generateOtp trên background thread ─────────────────────────
        setLoading(true);

        Task<String> sendTask = new Task<>() {
            @Override
            protected String call() {
                // Ném IllegalArgumentException nếu email không tồn tại
                return PasswordResetDAO.getInstance().generateOtp(email);
            }
        };

        sendTask.setOnSucceeded(evt -> {
            setLoading(false);
            // OTP đã được gửi thành công — mở màn hình đặt lại mật khẩu
            openResetPasswordView(email);
        });

        sendTask.setOnFailed(evt -> {
            setLoading(false);
            Throwable ex = sendTask.getException();
            System.err.println("[ForgotPasswordController] generateOtp lỗi: " + ex.getMessage());

            if (ex instanceof IllegalArgumentException) {
                setFieldError(boxEmail);
                showError("Email này chưa được đăng ký trong hệ thống.");
            } else {
                showError("Không gửi được mã xác nhận.\nVui lòng kiểm tra kết nối và thử lại.");
            }
        });

        Thread t = new Thread(sendTask, "forgot-password-task");
        t.setDaemon(true);
        t.start();
    }

    /** Xử lý nút "Quay lại đăng nhập" — đóng modal và khôi phục login. */
    @FXML
    private void onCancel() {
        closeThisStage();
        if (loginStage != null) {
            loginStage.show();
        }
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    /**
     * Đóng cửa sổ này và mở {@code ResetPasswordView.fxml} với email đã xác nhận.
     * LoginStage vẫn ẩn — sẽ được {@link ResetPasswordController} khôi phục sau.
     *
     * @param email địa chỉ email đã được xác nhận có tồn tại trong hệ thống
     */
    private void openResetPasswordView(String email) {
        try {
            ResetPasswordController ctrl = new ResetPasswordController();
            ctrl.setEmail(email);
            ctrl.setLoginStage(loginStage);

            Parent root = com.restaurant.ui.fx.util.FxUtils.loadFxml(
                    "ResetPasswordView.fxml", ctrl);

            Scene scene = new Scene(root, 420, 460);
            // Tải login.css cho màn hình này
            URL cssUrl = getClass().getResource("/fxml/login.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            Stage resetStage = new Stage(StageStyle.DECORATED);
            resetStage.setTitle("Đặt lại mật khẩu — SmartRestaurant");
            resetStage.setScene(scene);
            resetStage.setResizable(false);
            // Không initOwner để loginStage vẫn có thể làm owner sau này
            resetStage.show();

            // Đóng cửa sổ Forgot Password hiện tại (không show loginStage)
            closeThisStage();

        } catch (Exception ex) {
            System.err.println("[ForgotPasswordController] Không thể mở ResetPasswordView: "
                    + ex.getMessage());
            ex.printStackTrace();
            showError("Lỗi hệ thống. Vui lòng thử lại.");
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private void showError(String message) {
        lblMessage.getStyleClass().remove("fp-success-label");
        if (!lblMessage.getStyleClass().contains("login-error-label")) {
            lblMessage.getStyleClass().add("login-error-label");
        }
        lblMessage.setText(message);
        lblMessage.setVisible(true);
        lblMessage.setManaged(true);

        FadeTransition ft = new FadeTransition(Duration.millis(200), lblMessage);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void clearMessage() {
        lblMessage.setVisible(false);
        lblMessage.setManaged(false);
        lblMessage.setText("");
    }

    private void setFieldError(HBox field) {
        field.getStyleClass().remove("login-field-box-error");
        field.getStyleClass().add("login-field-box-error");
    }

    private void clearFieldError() {
        boxEmail.getStyleClass().remove("login-field-box-error");
    }

    private void shakeField(HBox field) {
        javafx.animation.TranslateTransition shake =
                new javafx.animation.TranslateTransition(Duration.millis(60), field);
        shake.setByX(8);
        shake.setCycleCount(4);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> field.setTranslateX(0));
        shake.play();
    }

    private void setLoading(boolean loading) {
        btnSend.setDisable(loading);
        tfEmail.setDisable(loading);
        btnSend.setText(loading ? "Đang gửi..." : "Gửi mã xác nhận");
    }

    private void closeThisStage() {
        Stage stage = (Stage) btnSend.getScene().getWindow();
        stage.close();
    }
}