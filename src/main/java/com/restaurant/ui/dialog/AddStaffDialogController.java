package com.restaurant.ui.dialog;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.restaurant.data.DataManager;
import com.restaurant.session.AppSession;
import com.restaurant.session.OperationType;
import com.restaurant.session.RbacGuard;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller cho AddStaffDialog.fxml.
 *
 * <p>Hợp nhất "Thêm nhân viên" và "Tạo tài khoản" thành một dialog duy nhất.
 * Một lần submit tạo cả employee record (với đầy đủ thông tin) lẫn user account,
 * trong một transaction atomic qua {@link DataManager#addStaffWithAccount}.
 *
 * <p>Cách dùng:
 * <pre>{@code
 *   boolean ok = AddStaffDialogController.show(owner);
 *   if (ok) loadData();
 * }</pre>
 */
public class AddStaffDialogController {

    // ── FXML ─────────────────────────────────────────────────────────────────

    // Nhân viên
    @FXML private TextField    tfName;
    @FXML private TextField    tfCccd;
    @FXML private TextField    tfPhone;
    @FXML private TextField    tfAddress;
    @FXML private DatePicker   dpStartDate;
    @FXML private ComboBox<String> cmbRole;

    // Tài khoản
    @FXML private TextField    tfEmail;
    @FXML private PasswordField pfPassword;
    @FXML private PasswordField pfConfirm;

    // Labels info / lỗi
    @FXML private Label lblInfo;
    @FXML private Label errName;
    @FXML private Label errPhone;
    @FXML private Label errRole;
    @FXML private Label errEmail;
    @FXML private Label errPassword;
    @FXML private Label errConfirm;

    @FXML private Button btnSave;

    // ── State ─────────────────────────────────────────────────────────────────

    private boolean success = false;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Mở dialog và chờ. Trả về {@code true} nếu nhân viên + tài khoản được tạo thành công.
     */
    public static boolean show(Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    AddStaffDialogController.class.getResource(
                            "/fxml/dialog/AddStaffDialog.fxml"));
            Parent root = loader.load();
            AddStaffDialogController ctrl = loader.getController();

            Stage stage = new Stage();
            if (owner != null) stage.initOwner(owner);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Thêm nhân viên mới");
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();

            return ctrl.success;
        } catch (IOException e) {
            System.err.println("[AddStaffDialogController] Lỗi tải FXML: " + e.getMessage());
            return false;
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Vai trò được phép tạo tuỳ theo quyền của người đang đăng nhập
        List<String> roles = buildAllowedRoleLabels();
        cmbRole.getItems().setAll(roles);
        if (!roles.isEmpty()) cmbRole.setValue(roles.get(0));

        // Info label
        try {
            var restaurant = DataManager.getInstance().getMyRestaurant();
            String name = (restaurant != null
                           && restaurant.getName() != null
                           && !restaurant.getName().isBlank())
                          ? restaurant.getName() : "nhà hàng của bạn";
            lblInfo.setText("Tài khoản sẽ được tạo cho nhà hàng: " + name
                    + ". Nhân viên có thể đăng nhập ngay sau khi tạo.");
        } catch (Exception ignored) {}

        // Mặc định ngày vào làm là hôm nay
        dpStartDate.setValue(LocalDate.now());
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    @FXML
    private void onSave() {
        if (!validate()) return;

        // Snapshot inputs
        final String name      = tfName.getText().trim();
        final String cccd      = tfCccd.getText().trim();
        final String phone     = tfPhone.getText().trim();
        final String address   = tfAddress.getText().trim();
        final String startDate = dpStartDate.getValue() != null
                                 ? dpStartDate.getValue().format(ISO) : "";
        final String email     = tfEmail.getText().trim();
        final String password  = pfPassword.getText();
        final String roleName  = toRoleName(cmbRole.getValue());

        // Xác nhận Operation Token trước khi tạo
        boolean confirmed = ConfirmOperationDialogController.show(
                getStage(), OperationType.CHANGE_ROLE, 0L);
        if (!confirmed) return;

        // Tạo trên background thread
        setBusy(true);

        Task<Long> task = new Task<>() {
            @Override
            protected Long call() {
                return DataManager.getInstance().addStaffWithAccount(
                        name, cccd, phone, address, startDate,
                        email, password, roleName);
            }
        };

        task.setOnSucceeded(e -> {
            success = true;
            getStage().close();
        });

        task.setOnFailed(e -> Platform.runLater(() -> {
            Throwable cause = task.getException();
            String msg = (cause instanceof IllegalArgumentException
                          || cause instanceof SecurityException)
                         ? cause.getMessage()
                         : "Lỗi tạo nhân viên: " + (cause != null ? cause.getMessage() : "unknown");
            // Hiện lỗi ở trường email (lỗi thường là email đã tồn tại)
            showFieldError(errEmail, msg);
            setBusy(false);
        }));

        new Thread(task, "add-staff").start();
    }

    @FXML
    private void onCancel() { getStage().close(); }

    // ── Validation ────────────────────────────────────────────────────────────

    private boolean validate() {
        clearAllErrors();
        boolean ok = true;

        // Tên
        if (tfName.getText().trim().isEmpty()) {
            showFieldError(errName, "Vui lòng nhập họ và tên");
            ok = false;
        }

        // SDT
        String phone = tfPhone.getText().trim();
        if (phone.isEmpty()) {
            showFieldError(errPhone, "Vui lòng nhập số điện thoại");
            ok = false;
        } else if (!phone.matches("0\\d{9}")) {
            showFieldError(errPhone, "SDT phải có 10 chữ số, bắt đầu bằng 0");
            ok = false;
        }

        // Vai trò
        if (cmbRole.getValue() == null) {
            showFieldError(errRole, "Vui lòng chọn vai trò");
            ok = false;
        }

        // Email
        String email = tfEmail.getText().trim();
        if (email.isEmpty()) {
            showFieldError(errEmail, "Vui lòng nhập email");
            ok = false;
        } else if (!email.contains("@") || email.contains(" ")) {
            showFieldError(errEmail, "Email không hợp lệ");
            ok = false;
        }

        // Mật khẩu
        String pw = pfPassword.getText();
        if (pw.length() < 6) {
            showFieldError(errPassword, "Mật khẩu phải có ít nhất 6 ký tự");
            ok = false;
        }

        // Xác nhận
        if (!pw.equals(pfConfirm.getText())) {
            showFieldError(errConfirm, "Xác nhận mật khẩu không khớp");
            ok = false;
        }

        return ok;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> buildAllowedRoleLabels() {
        if (RbacGuard.getInstance().isSuperAdmin()) {
            return List.of("Phục vụ", "Đầu bếp", "Thu ngân", "Quản lý");
        }
        // RESTAURANT_ADMIN chỉ tạo được 3 role thấp hơn
        return List.of("Phục vụ", "Đầu bếp", "Thu ngân");
    }

    /** Chuyển label hiển thị → system role name cho UserDAO. */
    private String toRoleName(String display) {
        if (display == null) return "WAITER";
        return switch (display) {
            case "Đầu bếp"  -> "CHEF";
            case "Thu ngân" -> "CASHIER";
            case "Quản lý"  -> "RESTAURANT_ADMIN";
            default         -> "WAITER";
        };
    }

    private void showFieldError(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    private void clearAllErrors() {
        for (Label lbl : new Label[]{errName, errPhone, errRole, errEmail, errPassword, errConfirm}) {
            lbl.setText("");
            lbl.setVisible(false);
            lbl.setManaged(false);
        }
    }

    private void setBusy(boolean busy) {
        btnSave.setDisable(busy);
        btnSave.setText(busy ? "Đang tạo..." : "Tạo nhân viên →");
    }

    private Stage getStage() {
        return (Stage) btnSave.getScene().getWindow();
    }
}