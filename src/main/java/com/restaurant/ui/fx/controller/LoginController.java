package com.restaurant.ui;

import com.restaurant.dao.UserDAO;
import com.restaurant.session.AppSession;
import com.restaurant.session.RefreshTokenService;
import com.restaurant.session.SessionExpiredException;
import com.restaurant.session.TokenStorage;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Controller cho {@code LoginView.fxml}.
 *
 * <h3>Trách nhiệm</h3>
 * <ul>
 *   <li>Silent re-auth: kiểm tra refresh token trên disk khi initialize → tự login lại.</li>
 *   <li>Login thường: gọi {@link UserDAO#login(String, String)} trong {@link Task}
 *       để không block JavaFX thread.</li>
 *   <li>Remember me: sau login thành công, sinh refresh token và lưu qua
 *       {@link TokenStorage}.</li>
 *   <li>Forgot-password: flow 2 bước (generate token → reset password) dùng
 *       JavaFX {@link Dialog}.</li>
 *   <li>Loading overlay + button disabled state trong suốt thời gian Task chạy.</li>
 * </ul>
 *
 * <h3>Lưu ý thread</h3>
 * Mọi cập nhật UI phải chạy trên JavaFX Application Thread (JAT).
 * Task.onSucceeded / onFailed tự động chạy trên JAT nên an toàn.
 * Tuy nhiên các lời gọi {@link Platform#runLater} được giữ lại để an toàn
 * nếu một số callback được gọi từ pool thread.
 */
public class LoginController implements Initializable {

    // ── FXML bindings ─────────────────────────────────────────────────────────

    @FXML private VBox          loginCard;
    @FXML private TextField     tfEmail;
    @FXML private PasswordField tfPassword;
    @FXML private CheckBox      chkRememberMe;
    @FXML private Label         lblError;
    @FXML private Button        btnLogin;
    @FXML private Hyperlink     lnkForgot;
    @FXML private StackPane     loadingOverlay;

    // ── State ─────────────────────────────────────────────────────────────────

    /** Callback được set bởi caller sau khi FXML load (thường là Main). */
    private Runnable onLoginSuccess;

    /**
     * Shared daemon executor — giới hạn 1 thread để tránh race giữa các lần
     * click liên tiếp. Shutdown khi Stage đóng.
     */
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "LoginTask-Thread");
                t.setDaemon(true);
                return t;
            });

    private final UserDAO userDAO = new UserDAO();

    // ── Initializable ─────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Card entrance animation (fade-in + slide-up)
        loginCard.setOpacity(0);
        loginCard.setTranslateY(24);
        FadeTransition fade = new FadeTransition(Duration.millis(380), loginCard);
        fade.setFromValue(0); fade.setToValue(1);
        Timeline slide = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(loginCard.translateYProperty(), 24)),
            new KeyFrame(Duration.millis(380),
                new KeyValue(loginCard.translateYProperty(), 0))
        );
        fade.play();
        slide.play();

        // Silent re-auth: kiểm tra refresh token đã lưu trên disk
        attemptSilentAuth();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Đặt callback được gọi khi đăng nhập (kể cả silent auth) thành công.
     * Callback luôn được gọi trên JavaFX Application Thread.
     *
     * @param callback Runnable mở màn hình chính
     */
    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }

    // ── Silent re-auth ────────────────────────────────────────────────────────

    /**
     * Thử tự động đăng nhập lại bằng refresh token đã lưu trên disk.
     *
     * <p>Luồng:
     * <ol>
     *   <li>{@link TokenStorage#loadRefreshToken()} → nếu không có → dừng.</li>
     *   <li>{@link RefreshTokenService#validateAndRotate(String)} → nếu không hợp lệ → dừng.</li>
     *   <li>{@link UserDAO#loginByUserId(long)} → load session.</li>
     *   <li>Gọi {@link #onLoginSuccess}.</li>
     * </ol>
     *
     * Hiện loading overlay trong lúc chạy. Nếu thất bại thì ẩn overlay,
     * để người dùng đăng nhập tay bình thường.
     */
    private void attemptSilentAuth() {
        Optional<String> savedToken = TokenStorage.getInstance().loadRefreshToken();
        if (savedToken.isEmpty()) return;  // không có token → đăng nhập tay

        // Hiện loading overlay với nội dung "Đang khôi phục phiên…"
        setLoadingOverlayText("Đang khôi phục phiên…");
        showLoading(true);

        Task<Boolean> silentTask = new Task<>() {
            @Override
            protected Boolean call() {
                // Xác thực + rotate token
                Optional<Long> userIdOpt =
                        RefreshTokenService.getInstance().validateAndRotate(savedToken.get());

                if (userIdOpt.isEmpty()) return false;

                // Load AppSession từ DB
                return userDAO.loginByUserId(userIdOpt.get());
            }
        };

        silentTask.setOnSucceeded(e -> {
            if (silentTask.getValue()) {
                fireLoginSuccess();   // vào thẳng main screen
            } else {
                // Token hết hạn hoặc revoked — xoá file, để login tay
                TokenStorage.getInstance().clearSavedToken();
                showLoading(false);
            }
        });

        silentTask.setOnFailed(e -> {
            Throwable ex = silentTask.getException();
            System.err.println("[LoginController] Silent auth thất bại: " + ex.getMessage());
            TokenStorage.getInstance().clearSavedToken();
            showLoading(false);
        });

        executor.submit(silentTask);
    }

    // ── Login action ──────────────────────────────────────────────────────────

    /**
     * Xử lý sự kiện bấm nút "Đăng nhập" (hoặc nhấn Enter).
     * Validate client-side trước, sau đó chạy Task DB.
     */
    @FXML
    private void doLogin() {
        String email    = tfEmail.getText().trim();
        String password = tfPassword.getText();

        // Client-side validation
        if (email.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập email và mật khẩu.");
            shakeError();
            return;
        }

        clearError();
        showLoading(true);
        setLoadingOverlayText("Đang xác thực…");

        Task<LoginResult> loginTask = buildLoginTask(email, password);

        loginTask.setOnSucceeded(e -> {
            showLoading(false);
            LoginResult result = loginTask.getValue();
            if (result.success()) {
                handleLoginSuccess(result);
            } else {
                showError(result.errorMessage());
                shakeError();
                tfPassword.clear();
                tfPassword.requestFocus();
            }
        });

        loginTask.setOnFailed(e -> {
            showLoading(false);
            Throwable ex = loginTask.getException();
            if (ex instanceof SessionExpiredException) {
                showError("Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.");
            } else {
                showError("Lỗi kết nối: " + ex.getMessage());
            }
            shakeError();
        });

        executor.submit(loginTask);
    }

    /**
     * Tạo Task thực hiện login DB. Trả về {@link LoginResult} — không throw exception
     * đến onFailed (chỉ wrap exception thật sự bất ngờ).
     */
    private Task<LoginResult> buildLoginTask(String email, String password) {
        return new Task<>() {
            @Override
            protected LoginResult call() throws Exception {
                try {
                    boolean ok = userDAO.login(email, password);
                    if (ok) {
                        return LoginResult.success();
                    } else {
                        // Kiểm tra xem có phải bị khoá không (log từ AuditLogger)
                        return LoginResult.failure("Email hoặc mật khẩu không đúng.");
                    }
                } catch (SecurityException se) {
                    // AuditLogger đã ghi nhận brute-force lock
                    return LoginResult.failure(
                        "Tài khoản bị khoá tạm thời 15 phút do nhập sai quá nhiều lần.");
                }
                // Các exception khác (DB, network) sẽ bubble lên onFailed
            }
        };
    }

    /**
     * Xử lý sau khi Task login trả về success = true.
     * Nếu "ghi nhớ" được chọn → sinh + lưu refresh token (fire-and-forget).
     */
    private void handleLoginSuccess(LoginResult result) {
        if (chkRememberMe.isSelected()) {
            saveRefreshTokenAsync();
        }
        fireLoginSuccess();
    }

    /** Sinh refresh token trong background (không block UI; lỗi chỉ log, không ném). */
    private void saveRefreshTokenAsync() {
        long userId = AppSession.getInstance().getUserId();
        executor.submit(() -> {
            try {
                String rt = RefreshTokenService.getInstance().generateRefreshToken(userId);
                TokenStorage.getInstance().saveRefreshToken(rt);
            } catch (Exception ex) {
                System.err.println("[LoginController] Không lưu được refresh token: "
                        + ex.getMessage());
            }
        });
    }

    /** Gọi callback onLoginSuccess trên JAT và đóng executor. */
    private void fireLoginSuccess() {
        Platform.runLater(() -> {
            if (onLoginSuccess != null) onLoginSuccess.run();
            shutdownExecutor();
        });
    }

    // ── Forgot-password flow ──────────────────────────────────────────────────

    /**
     * Flow 2 bước đặt lại mật khẩu — mỗi bước là một JavaFX {@link Dialog}.
     *
     * <ul>
     *   <li>Bước 1: Nhập email → gọi
     *       {@link UserDAO#generatePasswordResetToken(String)} → hiện token.</li>
     *   <li>Bước 2: Nhập token + mật khẩu mới →
     *       {@link UserDAO#resetPasswordWithToken(String, String)}.</li>
     * </ul>
     */
    @FXML
    private void openForgotPasswordFlow() {
        // ── Bước 1: Thu thập email ────────────────────────────────────────────
        String email = showStep1EmailDialog();
        if (email == null) return; // người dùng Cancel

        // Gọi DB trong Task để không block JAT
        String token = runGenerateTokenTask(email);
        if (token == null) return; // lỗi hoặc email không tồn tại (đã show dialog)

        // Hiện token cho người dùng (trong app UI vì không có email server)
        showTokenInfoDialog(token);

        // ── Bước 2: Nhập token + mật khẩu mới ───────────────────────────────
        openResetStep2(token);
    }

    /** Hiện Dialog nhập email (Bước 1). Trả về email trim hoặc null nếu Cancel. */
    private String showStep1EmailDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Quên mật khẩu — Bước 1/2: Nhập email");
        dialog.setHeaderText(null);

        // Nút OK / Cancel
        ButtonType okType = new ButtonType("Tiếp theo", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        // Nội dung
        VBox content = new VBox(8);
        content.setPadding(new Insets(12, 4, 8, 4));
        content.setPrefWidth(360);

        Label hint = new Label(
            "Nhập email tài khoản của bạn. Hệ thống sẽ tạo token đặt lại mật khẩu\n"
          + "(hết hạn sau 15 phút).");
        hint.getStyleClass().add("step-hint");
        hint.setWrapText(true);

        Label lbl = new Label("Email:");
        lbl.getStyleClass().add("step-field-label");

        TextField tfResetEmail = new TextField();
        tfResetEmail.setPromptText("email@example.com");
        tfResetEmail.getStyleClass().add("input-field");
        tfResetEmail.setPrefHeight(40);

        content.getChildren().addAll(hint, lbl, tfResetEmail);
        dialog.getDialogPane().setContent(content);

        // Disable OK nếu email trống
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(okType);
        okBtn.setDisable(true);
        tfResetEmail.textProperty().addListener((obs, o, n) ->
            okBtn.setDisable(n.trim().isEmpty()));

        // Enter gửi form
        tfResetEmail.setOnAction(e -> okBtn.fire());

        dialog.setResultConverter(bt ->
            bt == okType ? tfResetEmail.getText().trim() : null);

        applyDialogStylesheet(dialog.getDialogPane());
        Platform.runLater(tfResetEmail::requestFocus);

        return dialog.showAndWait().orElse(null);
    }

    /**
     * Chạy {@link UserDAO#generatePasswordResetToken(String)} trong Task
     * (blocking, gọi từ JAT — dùng {@code Task.get()} để đợi kết quả).
     *
     * @param email email đã nhập ở bước 1
     * @return token string hoặc null nếu email không tồn tại / lỗi
     */
    private String runGenerateTokenTask(String email) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return userDAO.generatePasswordResetToken(email);
            }
        };

        executor.submit(task);

        try {
            String token = task.get(); // block — ổn vì đây không phải JAT call trực tiếp
                                        // (ta đang trong Platform.runLater scope từ button click)
            if (token == null) {
                Platform.runLater(() ->
                    showAlert(Alert.AlertType.WARNING,
                        "Không tìm thấy",
                        "Email không tồn tại hoặc tài khoản đã bị khoá."));
            }
            return token;
        } catch (Exception ex) {
            Platform.runLater(() ->
                showAlert(Alert.AlertType.ERROR,
                    "Lỗi kết nối",
                    "Không thể kết nối cơ sở dữ liệu: " + ex.getMessage()));
            return null;
        }
    }

    /** Hiện dialog thông tin token (copy để dùng ở bước 2). */
    private void showTokenInfoDialog(String token) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Token đã được tạo");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);

        VBox content = new VBox(10);
        content.setPadding(new Insets(12, 4, 8, 4));
        content.setPrefWidth(440);

        Label title = new Label("Token đặt lại mật khẩu (hết hạn sau 15 phút):");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13;");

        TextField tfToken = new TextField(token);
        tfToken.setEditable(false);
        tfToken.getStyleClass().add("token-display-field");
        tfToken.setPrefHeight(38);
        // Select all để dễ copy
        tfToken.setOnMouseClicked(e -> tfToken.selectAll());

        Label copyHint = new Label("Nhấp vào ô token để chọn toàn bộ, rồi Ctrl+C để sao chép.");
        copyHint.getStyleClass().add("step-hint");

        content.getChildren().addAll(title, tfToken, copyHint);
        dialog.getDialogPane().setContent(content);

        applyDialogStylesheet(dialog.getDialogPane());
        dialog.showAndWait();
    }

    /**
     * Bước 2: Dialog nhập token + mật khẩu mới + xác nhận.
     * Gọi {@link UserDAO#resetPasswordWithToken(String, String)}.
     *
     * @param prefillToken token đã sinh ở bước 1 (được điền sẵn vào field)
     */
    private void openResetStep2(String prefillToken) {
        Dialog<ResetResult> dialog = new Dialog<>();
        dialog.setTitle("Quên mật khẩu — Bước 2/2: Đặt mật khẩu mới");
        dialog.setHeaderText(null);

        ButtonType confirmType = new ButtonType("Xác nhận", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);

        // ── Form fields ───────────────────────────────────────────────────────
        VBox content = new VBox(4);
        content.setPadding(new Insets(12, 4, 8, 4));
        content.setPrefWidth(400);

        Label lblToken = new Label("Token xác nhận:");
        lblToken.getStyleClass().add("step-field-label");
        TextField tfToken = new TextField(prefillToken);
        tfToken.setPromptText("Dán token vào đây");
        tfToken.getStyleClass().addAll("input-field", "token-display-field");
        tfToken.setPrefHeight(40);

        Label lblNew = new Label("Mật khẩu mới (tối thiểu 6 ký tự):");
        lblNew.getStyleClass().add("step-field-label");
        PasswordField pfNew = new PasswordField();
        pfNew.setPromptText("••••••••");
        pfNew.getStyleClass().add("input-field");
        pfNew.setPrefHeight(40);

        Label lblConfirm = new Label("Xác nhận mật khẩu mới:");
        lblConfirm.getStyleClass().add("step-field-label");
        PasswordField pfConfirm = new PasswordField();
        pfConfirm.setPromptText("••••••••");
        pfConfirm.getStyleClass().add("input-field");
        pfConfirm.setPrefHeight(40);

        // Inline validation label
        Label lblStepError = new Label(" ");
        lblStepError.setStyle("-fx-text-fill: #C62828; -fx-font-size: 12;");

        content.getChildren().addAll(
            lblToken, tfToken,
            lblNew,   pfNew,
            lblConfirm, pfConfirm,
            lblStepError
        );
        dialog.getDialogPane().setContent(content);

        // ── Validation guards on OK button ────────────────────────────────────
        Button confirmBtn = (Button) dialog.getDialogPane().lookupButton(confirmType);
        confirmBtn.setDisable(true);

        Runnable validateStep2 = () -> {
            String t    = tfToken.getText().trim();
            String np   = pfNew.getText();
            String conf = pfConfirm.getText();
            boolean valid = !t.isEmpty() && np.length() >= 6 && np.equals(conf);
            confirmBtn.setDisable(!valid);
            if (!t.isEmpty() && np.length() > 0 && np.length() < 6) {
                lblStepError.setText("Mật khẩu mới phải có ít nhất 6 ký tự.");
            } else if (!np.isEmpty() && !conf.isEmpty() && !np.equals(conf)) {
                lblStepError.setText("Xác nhận mật khẩu không khớp.");
            } else {
                lblStepError.setText(" ");
            }
        };

        tfToken.textProperty().addListener((o, ov, nv) -> validateStep2.run());
        pfNew.textProperty().addListener((o, ov, nv)     -> validateStep2.run());
        pfConfirm.textProperty().addListener((o, ov, nv) -> validateStep2.run());

        dialog.setResultConverter(bt -> bt == confirmType
            ? new ResetResult(tfToken.getText().trim(), pfNew.getText())
            : null);

        applyDialogStylesheet(dialog.getDialogPane());
        Platform.runLater(pfNew::requestFocus);

        Optional<ResetResult> result = dialog.showAndWait();
        if (result.isEmpty()) return; // Cancel

        runResetTask(result.get().token(), result.get().newPassword());
    }

    /**
     * Thực thi {@link UserDAO#resetPasswordWithToken} trong Task và hiện kết quả.
     */
    private void runResetTask(String token, String newPassword) {
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return userDAO.resetPasswordWithToken(token, newPassword);
            }
        };

        executor.submit(task);

        try {
            boolean ok = task.get();
            if (ok) {
                showAlert(Alert.AlertType.INFORMATION,
                    "Thành công",
                    "Đặt lại mật khẩu thành công!\nVui lòng đăng nhập lại với mật khẩu mới.");
            } else {
                showAlert(Alert.AlertType.ERROR,
                    "Thất bại",
                    "Token không hợp lệ hoặc đã hết hạn.\nVui lòng thử lại từ bước 1.");
            }
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR,
                "Lỗi",
                "Không thể đặt lại mật khẩu: " + ex.getMessage());
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    /** Hiện / ẩn loading overlay và disable login button + fields. */
    private void showLoading(boolean show) {
        loadingOverlay.setVisible(show);
        loadingOverlay.setManaged(show);
        btnLogin.setDisable(show);
        tfEmail.setDisable(show);
        tfPassword.setDisable(show);
        chkRememberMe.setDisable(show);
        lnkForgot.setDisable(show);
        if (show) {
            btnLogin.getStyleClass().add("loading");
        } else {
            btnLogin.getStyleClass().remove("loading");
        }
    }

    /** Đổi text label trong loading overlay. */
    private void setLoadingOverlayText(String text) {
        // Label là con thứ 2 trong VBox bên trong overlay
        loadingOverlay.getChildren().stream()
            .filter(n -> n instanceof VBox)
            .map(n -> (VBox) n)
            .findFirst()
            .ifPresent(vbox -> vbox.getChildren().stream()
                .filter(n -> n instanceof Label)
                .map(n -> (Label) n)
                .findFirst()
                .ifPresent(lbl -> lbl.setText(text)));
    }

    /** Hiện thông báo lỗi nhỏ dưới password field. */
    private void showError(String message) {
        lblError.setText(message);
    }

    /** Xoá thông báo lỗi. */
    private void clearError() {
        lblError.setText(" ");
    }

    /**
     * Hiệu ứng lắc nhẹ error label (tương đương animateError() trong Swing).
     * Dùng Timeline dịch chuyển X để bắt mắt người dùng.
     */
    private void shakeError() {
        Timeline shake = new Timeline(
            new KeyFrame(Duration.ZERO,       new KeyValue(lblError.translateXProperty(), 0)),
            new KeyFrame(Duration.millis(60),  new KeyValue(lblError.translateXProperty(), -8)),
            new KeyFrame(Duration.millis(120), new KeyValue(lblError.translateXProperty(),  8)),
            new KeyFrame(Duration.millis(180), new KeyValue(lblError.translateXProperty(), -6)),
            new KeyFrame(Duration.millis(240), new KeyValue(lblError.translateXProperty(),  6)),
            new KeyFrame(Duration.millis(300), new KeyValue(lblError.translateXProperty(), 0))
        );
        shake.play();
    }

    /**
     * Áp stylesheet {@code login.css} lên DialogPane để các dialog
     * forgot-password có cùng style với màn hình chính.
     */
    private void applyDialogStylesheet(DialogPane pane) {
        URL css = getClass().getResource("login.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
    }

    /** Hiện Alert tiện ích, blocking trên JAT. */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        applyDialogStylesheet(alert.getDialogPane());
        alert.showAndWait();
    }

    /** Shutdown executor khi login thành công hoặc Stage đóng. */
    private void shutdownExecutor() {
        executor.shutdownNow();
    }

    // ── Inner records ─────────────────────────────────────────────────────────

    /** Kết quả từ Task login — tránh throw exception cho lỗi nghiệp vụ. */
    private record LoginResult(boolean success, String errorMessage) {
        static LoginResult success()              { return new LoginResult(true,  null); }
        static LoginResult failure(String msg)    { return new LoginResult(false, msg);  }
    }

    /** DTO cho bước 2 reset password dialog. */
    private record ResetResult(String token, String newPassword) {}
}