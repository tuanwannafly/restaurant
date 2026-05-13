package com.restaurant.ui.fx.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.mindrot.jbcrypt.BCrypt;

import com.restaurant.dao.RestaurantRequestDAO;
import com.restaurant.model.RestaurantRequest;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * RestaurantRegistrationController — Phase 2
 * ─────────────────────────────────────────────────────────────────────────────
 * Controller cho màn hình đăng ký nhà hàng công khai ({@code RestaurantRegistrationView.fxml}).
 *
 * <h3>Trách nhiệm</h3>
 * <ul>
 *   <li>Validate toàn bộ input phía client trước khi submit.</li>
 *   <li>Copy file logo và tài liệu vào thư mục storage cục bộ
 *       ({@code ~/SmartRestaurant/uploads/requests/}).</li>
 *   <li>Hash mật khẩu bằng BCrypt trước khi truyền vào DAO.</li>
 *   <li>Gọi {@link RestaurantRequestDAO#submit} trên background {@link Task}.</li>
 *   <li>Hiển thị Toast xác nhận khi thành công và quay về LoginView.</li>
 * </ul>
 *
 * <h3>Điều hướng</h3>
 * Màn hình này được mở từ {@link LoginController} và dùng callback
 * {@link #setOnBack(Runnable)} để quay lại. Pattern giống với
 * {@link LoginController#setOnLoginSuccess}.
 *
 * <h3>Thread model</h3>
 * Submit chạy trên daemon Task thread. Toàn bộ UI update được route về
 * FX Application Thread qua {@link Platform#runLater}.
 *
 * <p><b>File:</b>
 * {@code src/main/java/com/restaurant/ui/fx/controller/RestaurantRegistrationController.java}
 */
public class RestaurantRegistrationController {

    // ── FXML — Phần 1: Thông tin chủ sở hữu ─────────────────────────────────

    @FXML private TextField     tfOwnerName;
    @FXML private HBox          boxOwnerName;

    @FXML private TextField     tfOwnerEmail;
    @FXML private HBox          boxOwnerEmail;

    @FXML private TextField     tfOwnerPhone;
    @FXML private HBox          boxOwnerPhone;

    @FXML private PasswordField pfPassword;
    @FXML private TextField     tfPasswordVisible;
    @FXML private HBox          boxPassword;
    @FXML private Button        btnTogglePass;

    @FXML private PasswordField pfConfirmPassword;
    @FXML private TextField     tfConfirmPasswordVisible;
    @FXML private HBox          boxConfirmPassword;
    @FXML private Button        btnToggleConfirm;

    // ── FXML — Phần 2: Thông tin nhà hàng ────────────────────────────────────

    @FXML private TextField     tfRestName;
    @FXML private HBox          boxRestName;

    @FXML private TextField     tfRestAddress;
    @FXML private HBox          boxRestAddress;

    @FXML private TextField     tfRestPhone;
    @FXML private HBox          boxRestPhone;

    @FXML private TextField     tfRestEmail;
    @FXML private HBox          boxRestEmail;

    // ── FXML — Upload & misc ──────────────────────────────────────────────────

    @FXML private Button        btnUploadLogo;
    @FXML private Label         lblLogoFile;

    @FXML private Button        btnUploadDocument;
    @FXML private Label         lblDocumentFile;

    @FXML private Label         lblError;
    @FXML private Region        spacerError;
    @FXML private Button        btnSubmit;
    @FXML private Button        btnBack;

    // ── State ─────────────────────────────────────────────────────────────────

    private File    selectedLogoFile;
    private File    selectedDocumentFile;
    private boolean passwordVisible        = false;
    private boolean confirmPasswordVisible = false;

    // ── Callback ──────────────────────────────────────────────────────────────

    /** Callback gọi trên FX thread sau khi người dùng nhấn "Quay lại đăng nhập"
     *  hoặc sau khi nộp đơn thành công. Được set bởi {@link LoginController}. */
    private Runnable onBack;

    // ── Storage path (configurable) ───────────────────────────────────────────

    /**
     * Thư mục lưu file upload cục bộ.
     * Mặc định: {@code ~/SmartRestaurant/uploads/requests/}.
     * Có thể ghi đè qua system property {@code smartrestaurant.uploads.dir}.
     */
    private static final String UPLOAD_BASE = System.getProperty(
            "smartrestaurant.uploads.dir",
            System.getProperty("user.home") + "/SmartRestaurant/uploads/requests/");

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        // Đồng bộ hai field password (ẩn / hiện)
        tfPasswordVisible.textProperty().bindBidirectional(pfPassword.textProperty());
        tfConfirmPasswordVisible.textProperty().bindBidirectional(pfConfirmPassword.textProperty());

        // Enter trên confirm password → submit
        pfConfirmPassword.setOnAction(e -> onSubmit());
        tfConfirmPasswordVisible.setOnAction(e -> onSubmit());

        Platform.runLater(() -> tfOwnerName.requestFocus());
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Callback gọi khi màn hình cần quay về LoginView.
     * Phải set trước khi hiển thị màn hình này.
     */
    public void setOnBack(Runnable callback) {
        this.onBack = callback;
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    /** Toggle hiện / ẩn mật khẩu. */
    @FXML
    private void onTogglePassword() {
        passwordVisible = !passwordVisible;
        pfPassword.setVisible(!passwordVisible);
        pfPassword.setManaged(!passwordVisible);
        tfPasswordVisible.setVisible(passwordVisible);
        tfPasswordVisible.setManaged(passwordVisible);
        btnTogglePass.setText(passwordVisible ? "🙈" : "👁");
        if (passwordVisible) {
            tfPasswordVisible.positionCaret(tfPasswordVisible.getText().length());
            tfPasswordVisible.requestFocus();
        } else {
            pfPassword.positionCaret(pfPassword.getText().length());
            pfPassword.requestFocus();
        }
    }

    /** Toggle hiện / ẩn xác nhận mật khẩu. */
    @FXML
    private void onToggleConfirmPassword() {
        confirmPasswordVisible = !confirmPasswordVisible;
        pfConfirmPassword.setVisible(!confirmPasswordVisible);
        pfConfirmPassword.setManaged(!confirmPasswordVisible);
        tfConfirmPasswordVisible.setVisible(confirmPasswordVisible);
        tfConfirmPasswordVisible.setManaged(confirmPasswordVisible);
        btnToggleConfirm.setText(confirmPasswordVisible ? "🙈" : "👁");
        if (confirmPasswordVisible) {
            tfConfirmPasswordVisible.positionCaret(tfConfirmPasswordVisible.getText().length());
            tfConfirmPasswordVisible.requestFocus();
        } else {
            pfConfirmPassword.positionCaret(pfConfirmPassword.getText().length());
            pfConfirmPassword.requestFocus();
        }
    }

    /** Mở FileChooser để chọn logo (PNG / JPG / WEBP). */
    @FXML
    private void onUploadLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn logo nhà hàng");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Ảnh logo (PNG, JPG, WEBP)", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Tất cả file", "*.*"));

        File file = chooser.showOpenDialog(getStage());
        if (file != null) {
            selectedLogoFile = file;
            lblLogoFile.setText(file.getName());
            lblLogoFile.setStyle("-fx-text-fill: #2E7D32;");   // xanh lá — đã chọn
        }
    }

    /** Mở FileChooser để chọn tài liệu chứng minh (tất cả file). */
    @FXML
    private void onUploadDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn tài liệu chứng minh");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Tài liệu (PDF, DOC, DOCX)", "*.pdf", "*.doc", "*.docx"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Tất cả file", "*.*"));

        File file = chooser.showOpenDialog(getStage());
        if (file != null) {
            selectedDocumentFile = file;
            lblDocumentFile.setText(file.getName());
            lblDocumentFile.setStyle("-fx-text-fill: #2E7D32;");  // xanh lá — đã chọn
        }
    }

    /** Quay về màn hình đăng nhập. */
    @FXML
    private void onBackToLogin() {
        if (onBack != null) onBack.run();
    }

    /**
     * Xử lý nộp đơn đăng ký:
     * <ol>
     *   <li>Validate input phía client.</li>
     *   <li>Copy file lên thư mục storage cục bộ.</li>
     *   <li>Hash mật khẩu bằng BCrypt.</li>
     *   <li>Gọi {@link RestaurantRequestDAO#submit} trên background thread.</li>
     *   <li>Hiển thị Toast và quay về Login khi thành công.</li>
     * </ol>
     */
    @FXML
    private void onSubmit() {
        clearError();
        clearAllFieldErrors();

        // ── Validate Phần 1 ──────────────────────────────────────────────────

        String ownerName    = tfOwnerName.getText().trim();
        String ownerEmail   = tfOwnerEmail.getText().trim();
        String ownerPhone   = tfOwnerPhone.getText().trim();
        String password     = pfPassword.getText();
        String confirmPass  = pfConfirmPassword.getText();

        if (ownerName.isEmpty()) {
            markError(boxOwnerName, "Vui lòng nhập họ và tên chủ sở hữu.");
            tfOwnerName.requestFocus();
            return;
        }
        if (ownerName.length() < 2) {
            markError(boxOwnerName, "Họ và tên phải có ít nhất 2 ký tự.");
            tfOwnerName.requestFocus();
            return;
        }
        if (ownerEmail.isEmpty()) {
            markError(boxOwnerEmail, "Vui lòng nhập email chủ sở hữu.");
            tfOwnerEmail.requestFocus();
            return;
        }
        if (!isValidEmail(ownerEmail)) {
            markError(boxOwnerEmail, "Địa chỉ email không hợp lệ.");
            tfOwnerEmail.requestFocus();
            return;
        }
        if (ownerPhone.isEmpty()) {
            markError(boxOwnerPhone, "Vui lòng nhập số điện thoại.");
            tfOwnerPhone.requestFocus();
            return;
        }
        if (!isValidPhone(ownerPhone)) {
            markError(boxOwnerPhone, "Số điện thoại không hợp lệ (10–11 chữ số, bắt đầu bằng 0).");
            tfOwnerPhone.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            markError(boxPassword, "Vui lòng nhập mật khẩu.");
            pfPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            markError(boxPassword, "Mật khẩu phải có ít nhất 6 ký tự.");
            pfPassword.requestFocus();
            return;
        }
        if (confirmPass.isEmpty()) {
            markError(boxConfirmPassword, "Vui lòng xác nhận mật khẩu.");
            pfConfirmPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPass)) {
            markError(boxConfirmPassword, "Mật khẩu xác nhận không khớp.");
            pfConfirmPassword.clear();
            if (confirmPasswordVisible) tfConfirmPasswordVisible.clear();
            pfConfirmPassword.requestFocus();
            return;
        }

        // ── Validate Phần 2 ──────────────────────────────────────────────────

        String restName    = tfRestName.getText().trim();
        String restAddress = tfRestAddress.getText().trim();
        String restPhone   = tfRestPhone.getText().trim();
        String restEmail   = tfRestEmail.getText().trim();

        if (restName.isEmpty()) {
            markError(boxRestName, "Vui lòng nhập tên nhà hàng.");
            tfRestName.requestFocus();
            return;
        }
        if (restAddress.isEmpty()) {
            markError(boxRestAddress, "Vui lòng nhập địa chỉ nhà hàng.");
            tfRestAddress.requestFocus();
            return;
        }
        if (!restPhone.isEmpty() && !isValidPhone(restPhone)) {
            markError(boxRestPhone, "Số điện thoại nhà hàng không hợp lệ.");
            tfRestPhone.requestFocus();
            return;
        }
        if (!restEmail.isEmpty() && !isValidEmail(restEmail)) {
            markError(boxRestEmail, "Email nhà hàng không hợp lệ.");
            tfRestEmail.requestFocus();
            return;
        }

        // ── Copy files & build request object ────────────────────────────────

        setLoading(true);

        // Capture final values for lambda
        final String fOwnerName   = ownerName;
        final String fOwnerEmail  = ownerEmail;
        final String fOwnerPhone  = ownerPhone;
        final String fPassword    = password;
        final String fRestName    = restName;
        final String fRestAddress = restAddress;
        final String fRestPhone   = restPhone.isEmpty()  ? null : restPhone;
        final String fRestEmail   = restEmail.isEmpty()  ? null : restEmail;
        final File   fLogoFile    = selectedLogoFile;
        final File   fDocFile     = selectedDocumentFile;

        Task<Void> submitTask = new Task<>() {
            @Override
            protected Void call() throws Exception {

                // 1. Hash mật khẩu (bcrypt)
                String passwordHash = BCrypt.hashpw(fPassword, BCrypt.gensalt());

                // 2. Copy files vào thư mục storage cục bộ
                String logoPath     = copyUploadedFile(fLogoFile);
                String documentPath = copyUploadedFile(fDocFile);

                // 3. Build model
                RestaurantRequest request = new RestaurantRequest(
                        fOwnerName, fOwnerEmail, fOwnerPhone, passwordHash,
                        fRestName, fRestAddress, fRestPhone, fRestEmail);
                request.setLogoPath(logoPath);
                request.setDocumentPath(documentPath);

                // 4. Gọi DAO
                new RestaurantRequestDAO().submit(request);

                System.out.println("[RestaurantRegistrationController] Đơn #"
                        + request.getRequestId() + " đã nộp thành công.");
                return null;
            }
        };

        submitTask.setOnSucceeded(evt -> {
            setLoading(false);

            // Hiển thị toast thành công trên owner stage
            Stage stage = getStage();
            if (stage != null) {
                com.restaurant.ui.fx.util.ToastNotificationFx.showSuccess(
                        stage,
                        "Đơn đăng ký đã được gửi. Vui lòng chờ admin phê duyệt.");
            }

            // Delay 1.5s để user đọc toast, rồi quay về login
            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(Duration.millis(1500));
            delay.setOnFinished(e -> {
                if (onBack != null) onBack.run();
            });
            delay.play();
        });

        submitTask.setOnFailed(evt -> {
            setLoading(false);
            Throwable ex = submitTask.getException();
            System.err.println("[RestaurantRegistrationController] submit lỗi: "
                    + ex.getMessage());
            showError("Lỗi khi gửi đơn đăng ký:\n" + ex.getMessage()
                    + "\nVui lòng kiểm tra kết nối và thử lại.");
        });

        Thread t = new Thread(submitTask, "reg-submit-task");
        t.setDaemon(true);
        t.start();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Copy một file đính kèm vào thư mục uploads.
     * Đặt tên file theo UUID để tránh conflict.
     *
     * @param source file gốc (nullable — trả về {@code null} nếu null)
     * @return đường dẫn tuyệt đối của bản sao, hoặc {@code null} nếu không có file
     * @throws IOException nếu copy thất bại
     */
    private String copyUploadedFile(File source) throws IOException {
        if (source == null || !source.exists()) return null;

        // Lấy extension
        String originalName = source.getName();
        int dotIdx = originalName.lastIndexOf('.');
        String ext = (dotIdx >= 0) ? originalName.substring(dotIdx) : "";

        // Tạo thư mục đích nếu chưa có
        Path targetDir = Paths.get(UPLOAD_BASE);
        Files.createDirectories(targetDir);

        // Tên file ngẫu nhiên để tránh conflict
        String uniqueName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = targetDir.resolve(uniqueName);

        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("[Upload] " + originalName + " → " + target);
        return target.toAbsolutePath().toString();
    }

    /** Lấy Stage hiện tại từ bất kỳ node nào đã inject. */
    private Stage getStage() {
        try {
            return (Stage) btnSubmit.getScene().getWindow();
        } catch (Exception e) {
            return null;
        }
    }

    /** Hiển thị thông báo lỗi với fade-in. */
    private void showError(String message) {
        lblError.setText(message);
        lblError.setVisible(true);
        lblError.setManaged(true);
        spacerError.setPrefHeight(8);
        spacerError.setVisible(true);
        spacerError.setManaged(true);

        FadeTransition ft = new FadeTransition(Duration.millis(200), lblError);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void clearError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        lblError.setText("");
        spacerError.setVisible(false);
        spacerError.setManaged(false);
    }

    /** Đánh dấu field lỗi + hiện thông báo. */
    private void markError(HBox fieldBox, String message) {
        setFieldError(fieldBox);
        showError(message);
        shakeField(fieldBox);
    }

    private void setFieldError(HBox fieldBox) {
        fieldBox.getStyleClass().remove("login-field-box-error");
        fieldBox.getStyleClass().add("login-field-box-error");
    }

    private void clearAllFieldErrors() {
        for (HBox box : new HBox[]{
                boxOwnerName, boxOwnerEmail, boxOwnerPhone,
                boxPassword, boxConfirmPassword,
                boxRestName, boxRestAddress, boxRestPhone, boxRestEmail}) {
            box.getStyleClass().remove("login-field-box-error");
        }
    }

    private void shakeField(HBox fieldBox) {
        javafx.animation.TranslateTransition shake =
                new javafx.animation.TranslateTransition(Duration.millis(60), fieldBox);
        shake.setByX(8);
        shake.setCycleCount(4);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> fieldBox.setTranslateX(0));
        shake.play();
    }

    /** Khoá / mở toàn bộ input trong lúc submit. */
    private void setLoading(boolean loading) {
        btnSubmit.setDisable(loading);
        btnBack.setDisable(loading);
        btnUploadLogo.setDisable(loading);
        btnUploadDocument.setDisable(loading);
        tfOwnerName.setDisable(loading);
        tfOwnerEmail.setDisable(loading);
        tfOwnerPhone.setDisable(loading);
        pfPassword.setDisable(loading);
        tfPasswordVisible.setDisable(loading);
        pfConfirmPassword.setDisable(loading);
        tfConfirmPasswordVisible.setDisable(loading);
        tfRestName.setDisable(loading);
        tfRestAddress.setDisable(loading);
        tfRestPhone.setDisable(loading);
        tfRestEmail.setDisable(loading);
        btnTogglePass.setDisable(loading);
        btnToggleConfirm.setDisable(loading);

        btnSubmit.setText(loading ? "Đang gửi đơn..." : "GỬI ĐƠN ĐĂNG KÝ");
    }

    // ── Input validation helpers ───────────────────────────────────────────────

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".")
                && email.indexOf("@") < email.lastIndexOf(".")
                && email.length() >= 5;
    }

    /**
     * Validate số điện thoại Việt Nam đơn giản:
     * 10–11 chữ số, bắt đầu bằng 0.
     */
    private boolean isValidPhone(String phone) {
        if (phone == null) return false;
        String digits = phone.replaceAll("[\\s\\-]", "");
        return digits.matches("0[0-9]{9,10}");
    }
}
