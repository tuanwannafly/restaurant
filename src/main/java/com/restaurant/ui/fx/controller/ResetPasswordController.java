package com.restaurant.ui.fx.controller;

import com.restaurant.dao.UserDAO;
import com.restaurant.ui.fx.util.FxUtils;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * ResetPasswordController — JavaFX controller cho {@code ResetPasswordView.fxml}.
 *
 * <h3>Trách nhiệm</h3>
 * <ul>
 *   <li>Nhận email từ {@link ForgotPasswordController}.</li>
 *   <li>Validate OTP (6 chữ số), mật khẩu mới và xác nhận mật khẩu.</li>
 *   <li>Gọi {@link UserDAO#resetPassword(String, String, String)} trên background Task.</li>
 *   <li>Khi thành công: đóng cửa sổ này, khôi phục loginStage và hiển thị toast thành công.</li>
 *   <li>Khi huỷ: đóng cửa sổ này và khôi phục loginStage.</li>
 * </ul>
 *
 * <h3>Thread model</h3>
 * Lệnh gọi {@code resetPassword} chạy trên daemon {@link Task} thread.
 * Mọi cập nhật UI đều được đưa về FX Application Thread qua
 * {@link Platform#runLater}.
 *
 * <p><b>File:</b>
 * {@code src/main/java/com/restaurant/ui/fx/controller/ResetPasswordController.java}
 */
public class ResetPasswordController {

    // ── FXML nodes ────────────────────────────────────────────────────────────

    @FXML private Label         lblEmailHint;

    @FXML private TextField     tfOtp;
    @FXML private HBox          boxOtp;

    @FXML private PasswordField pfNewPassword;
    @FXML private HBox          boxNewPass;

    @FXML private PasswordField pfConfirmPassword;
    @FXML private HBox          boxConfirmPass;

    @FXML private Label         lblError;
    @FXML private Button        btnReset;

    // ── State injected by caller ───────────────────────────────────────────────

    /** Email tài khoản cần đặt lại mật khẩu — set bởi ForgotPasswordController. */
    private String email;

    /**
     * Tham chiếu đến Stage login — để khôi phục màn hình đăng nhập sau khi
     * hoàn tất hoặc huỷ.
     */
    private Stage loginStage;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Gọi sau khi tất cả @FXML field được inject. */
    @FXML
    private void initialize() {
        // Enter trên mỗi field → chuyển sang field tiếp theo / submit
        tfOtp.setOnAction(e -> pfNewPassword.requestFocus());
        pfNewPassword.setOnAction(e -> pfConfirmPassword.requestFocus());
        pfConfirmPassword.setOnAction(e -> onResetPassword());

        // Chỉ cho phép nhập số vào OTP field, tối đa 6 ký tự
        tfOtp.textProperty().addListener((obs, oldVal, newVal) -> {
            String digits = newVal.replaceAll("[^0-9]", "");
            if (digits.length() > 6) digits = digits.substring(0, 6);
            if (!digits.equals(newVal)) {
                tfOtp.setText(digits);
                tfOtp.positionCaret(digits.length());
            }
        });

        Platform.runLater(() -> tfOtp.requestFocus());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Đặt địa chỉ email của tài khoản cần đặt lại mật khẩu.
     * Phải được gọi trước khi Stage hiển thị.
     *
     * @param email địa chỉ email đã xác nhận tồn tại trong hệ thống
     */
    public void setEmail(String email) {
        this.email = email;
        // Cập nhật hint sau khi initialize() đã chạy
        Platform.runLater(() -> {
            if (lblEmailHint != null && email != null) {
                lblEmailHint.setText("Mã OTP đã được gửi đến: " + maskEmail(email));
            }
        });
    }

    /**
     * Đặt tham chiếu đến Stage login để có thể khôi phục sau khi modal đóng.
     *
     * @param loginStage Stage chứa LoginView, hiện đang bị ẩn
     */
    public void setLoginStage(Stage loginStage) {
        this.loginStage = loginStage;
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    /**
     * Xử lý nút "Đặt lại mật khẩu".
     * Validate inputs → gọi {@link UserDAO#resetPassword} trên background Task
     * → khi thành công khôi phục login và hiển thị toast.
     */
    @FXML
    private void onResetPassword() {
        clearError();
        clearAllFieldErrors();

        String otp         = tfOtp.getText().trim();
        String newPassword = pfNewPassword.getText();
        String confirmPass = pfConfirmPassword.getText();

        // ── Client-side validation ─────────────────────────────────────────
        if (otp.isEmpty()) {
            shakeField(boxOtp);
            showError("Vui lòng nhập mã OTP.");
            tfOtp.requestFocus();
            return;
        }
        if (otp.length() != 6) {
            setFieldError(boxOtp);
            showError("Mã OTP phải có đúng 6 chữ số.");
            tfOtp.requestFocus();
            return;
        }
        if (newPassword.isEmpty()) {
            shakeField(boxNewPass);
            showError("Vui lòng nhập mật khẩu mới.");
            pfNewPassword.requestFocus();
            return;
        }
        if (newPassword.length() < 6) {
            setFieldError(boxNewPass);
            showError("Mật khẩu mới phải có ít nhất 6 ký tự.");
            pfNewPassword.requestFocus();
            return;
        }
        if (!newPassword.equals(confirmPass)) {
            setFieldError(boxNewPass);
            setFieldError(boxConfirmPass);
            shakeField(boxConfirmPass);
            showError("Mật khẩu xác nhận không khớp. Vui lòng kiểm tra lại.");
            pfConfirmPassword.requestFocus();
            return;
        }

        // ── Gọi UserDAO.resetPassword trên background thread ───────────────
        setLoading(true);

        final String finalEmail = email;
        Task<Boolean> resetTask = new Task<>() {
            @Override
            protected Boolean call() {
                return new UserDAO().resetPassword(finalEmail, otp, newPassword);
            }
        };

        resetTask.setOnSucceeded(evt -> {
            setLoading(false);
            boolean ok = resetTask.getValue();

            if (ok) {
                // Thành công — đóng modal, khôi phục login, hiển thị toast
                Stage thisStage = (Stage) btnReset.getScene().getWindow();
                thisStage.close();

                if (loginStage != null) {
                    loginStage.show();
                    // Hiển thị toast thành công trên cửa sổ login
                    FxUtils.showToast(
                            loginStage,
                            "✅  Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại.",
                            FxUtils.ToastType.SUCCESS,
                            4000);
                }
            } else {
                // OTP sai hoặc hết hạn
                setFieldError(boxOtp);
                shakeField(boxOtp);
                showError("Mã OTP không hợp lệ hoặc đã hết hạn (15 phút).\n"
                        + "Vui lòng yêu cầu gửi lại mã mới.");
                tfOtp.clear();
                tfOtp.requestFocus();
            }
        });

        resetTask.setOnFailed(evt -> {
            setLoading(false);
            Throwable ex = resetTask.getException();
            System.err.println("[ResetPasswordController] resetPassword lỗi: " + ex.getMessage());
            showError("Lỗi kết nối cơ sở dữ liệu.\nVui lòng kiểm tra kết nối và thử lại.");
        });

        Thread t = new Thread(resetTask, "reset-password-task");
        t.setDaemon(true);
        t.start();
    }

    /** Xử lý nút "Quay lại" — đóng modal và khôi phục login. */
    @FXML
    private void onCancel() {
        Stage thisStage = (Stage) btnReset.getScene().getWindow();
        thisStage.close();

        if (loginStage != null) {
            loginStage.show();
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

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

    private void setFieldError(HBox field) {
        field.getStyleClass().remove("login-field-box-error");
        field.getStyleClass().add("login-field-box-error");
    }

    private void clearAllFieldErrors() {
        boxOtp.getStyleClass().remove("login-field-box-error");
        boxNewPass.getStyleClass().remove("login-field-box-error");
        boxConfirmPass.getStyleClass().remove("login-field-box-error");
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
        btnReset.setDisable(loading);
        tfOtp.setDisable(loading);
        pfNewPassword.setDisable(loading);
        pfConfirmPassword.setDisable(loading);
        btnReset.setText(loading ? "Đang xử lý..." : "Đặt lại mật khẩu");
    }

    /**
     * Ẩn một phần email để bảo vệ thông tin người dùng.
     * Ví dụ: {@code "john@example.com"} → {@code "jo**@example.com"}.
     */
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return email;
        String local  = email.substring(0, at);
        String domain = email.substring(at);
        String masked = local.substring(0, 2)
                + "*".repeat(Math.min(local.length() - 2, 4))
                + domain;
        return masked;
    }
}