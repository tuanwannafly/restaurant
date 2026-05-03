package com.restaurant.ui.fx.controller;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TabPane;

import com.restaurant.data.DataManager;
import com.restaurant.model.Employee;
import com.restaurant.session.AppSession;
import com.restaurant.session.RefreshTokenService;
import com.restaurant.session.TokenStorage;
import com.restaurant.ui.control.CleanField;

/**
 * ProfileController — controller cho {@code dialog/MyProfileDialog.fxml}.
 *
 * <p>Mở dialog từ bất kỳ đâu bằng {@link #show(javafx.stage.Window)}:
 * <pre>{@code
 *   ProfileController.show(primaryStage);
 * }</pre>
 *
 * <p>Các thao tác write đều chạy trong background {@link Task} để không block
 * JavaFX Application Thread.
 *
 * <p><b>Vị trí:</b>
 * {@code src/main/java/com/restaurant/ui/fx/controller/ProfileController.java}
 */
public class ProfileController {

    // ── FXML injections ───────────────────────────────────────────────────────

    @FXML private TabPane      tabPane;
    @FXML private Label        lblRoleBadge;

    // Tab 1 – Thông tin cá nhân
    @FXML private CleanField   tfName;
    @FXML private CleanField   tfPhone;
    @FXML private CleanField   tfAddress;
    @FXML private CleanField   tfEmail;
    @FXML private CleanField   tfRestaurant;
    @FXML private CleanField   tfRole;
    @FXML private Label        lblProfileMsg;
    @FXML private Button       btnSaveProfile;

    // Tab 2 – Đổi mật khẩu
    @FXML private PasswordField pfOldPw;
    @FXML private PasswordField pfNewPw;
    @FXML private PasswordField pfConfirmPw;
    @FXML private Label         lblPwMsg;
    @FXML private Button        btnChangePw;
    @FXML private Button        btnRevokeAll;

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Load FXML, tạo Stage modal APPLICATION_MODAL, show dialog.
     * Gọi trên JavaFX Application Thread.
     *
     * @param owner cửa sổ cha để dialog hiển thị đúng vị trí
     */
    public static void show(javafx.stage.Window owner) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    ProfileController.class.getResource(
                            "/fxml/dialog/MyProfileDialog.fxml"));
            javafx.scene.Parent root = loader.load();

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.initOwner(owner);
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setTitle("Hồ sơ của tôi");
            stage.setResizable(false);
            stage.setScene(new javafx.scene.Scene(root));
            stage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        AppSession session = AppSession.getInstance();

        // Header badge
        lblRoleBadge.setText(session.getRoleLabel());

        // Pre-fill static fields (từ session)
        tfName.setText(nv(session.getUserName()));
        tfEmail.setText(nv(session.getUserEmail()));
        tfRole.setText(nv(session.getRoleLabel()));

        // Nhà hàng
        loadRestaurantName();

        // Phone/address từ bảng employees (async)
        loadEmployeeData();
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    /**
     * Lấy tên nhà hàng qua {@link DataManager#getMyRestaurant()} trong background.
     * Điền vào {@link #tfRestaurant}, fallback "—" nếu SUPER_ADMIN.
     */
    private void loadRestaurantName() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                try {
                    var r = DataManager.getInstance().getMyRestaurant();
                    return (r != null && r.getName() != null && !r.getName().isBlank())
                            ? r.getName() : "—";
                } catch (Exception e) {
                    return "—";
                }
            }
        };
        task.setOnSucceeded(e -> tfRestaurant.setText(task.getValue()));
        task.setOnFailed(e -> tfRestaurant.setText("—"));
        daemon(task);
    }

    /**
     * Load phone/address bất đồng bộ từ bảng {@code employees} (via user_id).
     * SUPER_ADMIN hoặc user chưa có employee record → để trống.
     */
    private void loadEmployeeData() {
        tfPhone.setPromptText("Đang tải...");
        tfAddress.setPromptText("Đang tải...");

        Task<Employee> task = new Task<>() {
            @Override
            protected Employee call() {
                return DataManager.getInstance().getOwnEmployeeInfo();
            }
        };

        task.setOnSucceeded(e -> {
            Employee emp = task.getValue();
            if (emp != null) {
                tfPhone.setText(nv(emp.getPhone()));
                tfAddress.setText(nv(emp.getAddress()));
            } else {
                tfPhone.setText("");
                tfAddress.setText("");
            }
            tfPhone.setPromptText("");
            tfAddress.setPromptText("");
        });

        task.setOnFailed(e -> {
            tfPhone.setText("");
            tfAddress.setText("");
        });

        daemon(task);
    }

    // ── Tab 1: Save profile ───────────────────────────────────────────────────

    @FXML
    private void onSaveProfile() {
        clearMsg(lblProfileMsg);

        String name    = tfName.getText().trim();
        String phone   = tfPhone.getText().trim();
        String address = tfAddress.getText().trim();

        if (name.isEmpty()) {
            showMsg(lblProfileMsg, "❌ Họ và tên không được để trống.", "msg-danger");
            tfName.requestFocus();
            return;
        }

        btnSaveProfile.setDisable(true);
        btnSaveProfile.setText("Đang lưu...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long uid = AppSession.getInstance().getUserId();
                DataManager.getInstance().updateOwnProfile(uid, name, phone, address);
                // Cập nhật lại tên trong AppSession
                AppSession s = AppSession.getInstance();
                s.login(uid, name, s.getUserEmail(), s.getUserRole(), s.getRestaurantId());
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            showMsg(lblProfileMsg, "✅ Đã lưu thành công", "msg-success");
            btnSaveProfile.setDisable(false);
            btnSaveProfile.setText("💾 Lưu thay đổi");
        });

        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            showMsg(lblProfileMsg,
                    "❌ " + (cause != null ? cause.getMessage() : "Lỗi không xác định"),
                    "msg-danger");
            btnSaveProfile.setDisable(false);
            btnSaveProfile.setText("💾 Lưu thay đổi");
        });

        daemon(task);
    }

    // ── Tab 2: Change password ────────────────────────────────────────────────

    @FXML
    private void onChangePassword() {
        clearMsg(lblPwMsg);

        String oldPw     = pfOldPw.getText();
        String newPw     = pfNewPw.getText();
        String confirmPw = pfConfirmPw.getText();

        // Validation
        if (oldPw.isEmpty() || newPw.isEmpty() || confirmPw.isEmpty()) {
            showMsg(lblPwMsg, "❌ Vui lòng điền đầy đủ tất cả các trường.", "msg-danger");
            return;
        }
        if (newPw.length() < 6) {
            showMsg(lblPwMsg, "❌ Mật khẩu mới phải có ít nhất 6 ký tự.", "msg-danger");
            return;
        }
        if (!newPw.equals(confirmPw)) {
            showMsg(lblPwMsg, "❌ Xác nhận mật khẩu không khớp.", "msg-danger");
            return;
        }

        btnChangePw.setDisable(true);
        btnChangePw.setText("Đang đổi...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long uid = AppSession.getInstance().getUserId();
                DataManager.getInstance().changeOwnPassword(uid, oldPw, newPw);
                // Thu hồi refresh tokens → đăng xuất mọi thiết bị
                RefreshTokenService.getInstance().revokeAllForUser(uid);
                TokenStorage.getInstance().clearSavedToken();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            showMsg(lblPwMsg,
                    "✅ Đổi mật khẩu thành công! Đã đăng xuất tất cả thiết bị.",
                    "msg-success");
            clearPasswordFields();
            btnChangePw.setDisable(false);
            btnChangePw.setText("🔑 Đổi mật khẩu");
        });

        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            String msg = (cause instanceof IllegalArgumentException)
                    ? cause.getMessage()
                    : (cause != null ? cause.getMessage() : "Lỗi không xác định");
            showMsg(lblPwMsg, "❌ " + msg, "msg-danger");
            btnChangePw.setDisable(false);
            btnChangePw.setText("🔑 Đổi mật khẩu");
        });

        daemon(task);
    }

    // ── Tab 2: Revoke all devices ─────────────────────────────────────────────

    /**
     * Hiện confirm dialog rồi gọi {@link RefreshTokenService#revokeAllForUser(long)}.
     */
    @FXML
    private void onRevokeAllDevices() {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận đăng xuất tất cả thiết bị");
        confirm.setHeaderText(null);
        confirm.setContentText(
                "Bạn sẽ bị đăng xuất khỏi tất cả thiết bị đang ghi nhớ đăng nhập.\n"
                + "Hành động này không thể hoàn tác. Tiếp tục?");
        confirm.initOwner(btnRevokeAll.getScene().getWindow());

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                long uid = AppSession.getInstance().getUserId();
                RefreshTokenService.getInstance().revokeAllForUser(uid);
                TokenStorage.getInstance().clearSavedToken();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            Alert ok = new Alert(AlertType.INFORMATION);
            ok.setTitle("Thành công");
            ok.setHeaderText(null);
            ok.setContentText("✅ Đã đăng xuất khỏi tất cả thiết bị thành công.");
            ok.initOwner(btnRevokeAll.getScene().getWindow());
            ok.showAndWait();
        });

        task.setOnFailed(e -> {
            Alert err = new Alert(AlertType.ERROR);
            err.setTitle("Thất bại");
            err.setHeaderText(null);
            Throwable cause = task.getException();
            err.setContentText("❌ Lỗi: " + (cause != null ? cause.getMessage() : "Lỗi không xác định"));
            err.initOwner(btnRevokeAll.getScene().getWindow());
            err.showAndWait();
        });

        daemon(task);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Cập nhật status label với text và CSS style class. */
    private void showMsg(Label lbl, String text, String styleClass) {
        Platform.runLater(() -> {
            lbl.setText(text);
            lbl.getStyleClass().removeAll("msg-label", "msg-success", "msg-danger");
            lbl.getStyleClass().add(styleClass);
        });
    }

    private void clearMsg(Label lbl) {
        lbl.setText("");
        lbl.getStyleClass().removeAll("msg-success", "msg-danger");
        lbl.getStyleClass().add("msg-label");
    }

    private void clearPasswordFields() {
        pfOldPw.clear();
        pfNewPw.clear();
        pfConfirmPw.clear();
    }

    /** Chạy task trong daemon thread (không block EDT / FX thread). */
    private static void daemon(Task<?> task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private static String nv(String s) {
        return s != null ? s : "";
    }
}