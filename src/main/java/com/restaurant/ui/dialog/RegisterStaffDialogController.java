package com.restaurant.ui.dialog;

import com.restaurant.data.DataManager;
import com.restaurant.session.OperationType;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;

/**
 * Controller for RegisterStaffDialog.fxml.
 *
 * <p>Allows a {@code RESTAURANT_ADMIN} to create a login account for a staff member.
 * The flow mirrors the Swing {@code RegisterStaffDialog}:
 * <ol>
 *   <li>Validate all form fields.</li>
 *   <li>Show {@link ConfirmOperationDialogController} (Operation Token) before persisting.</li>
 *   <li>Run {@link DataManager#registerStaff} on a background thread.</li>
 *   <li>Set {@link #success} = {@code true} and close on success.</li>
 * </ol>
 *
 * <p>Callers use the static {@link #show} factory and then check the return value:
 * <pre>{@code
 *   boolean ok = RegisterStaffDialogController.show(owner);
 *   if (ok) { loadData(); showToast("Tạo tài khoản thành công!"); }
 * }</pre>
 */
public class RegisterStaffDialogController {

    // ── FXML ─────────────────────────────────────────────────────────────────

    @FXML private TextField    tfName;
    @FXML private TextField    tfEmail;
    @FXML private PasswordField pfPassword;
    @FXML private PasswordField pfConfirm;
    @FXML private ComboBox<String> cmbRole;

    @FXML private Label        lblInfoRestaurant;
    @FXML private Label        lblError;
    @FXML private Button       btnSubmit;

    // ── State ─────────────────────────────────────────────────────────────────

    private boolean success = false;

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Open the dialog modally and return whether an account was successfully created.
     *
     * @param owner parent window (may be null)
     * @return {@code true} if the account was created successfully
     */
    public static boolean show(Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    RegisterStaffDialogController.class.getResource("RegisterStaffDialog.fxml"));
            Parent root = loader.load();
            RegisterStaffDialogController ctrl = loader.getController();

            Stage stage = new Stage();
            if (owner != null) stage.initOwner(owner);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Tạo tài khoản nhân viên");
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();

            return ctrl.isSuccess();

        } catch (IOException e) {
            System.err.println("[RegisterStaffDialogController] Lỗi tải FXML: " + e.getMessage());
            return false;
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Populate role options (RESTAURANT_ADMIN cannot assign higher roles)
        cmbRole.getItems().setAll("Phục vụ", "Đầu bếp", "Thu ngân");
        cmbRole.setValue("Phục vụ");

        // Populate restaurant info label
        try {
            var restaurant = DataManager.getInstance().getMyRestaurant();
            String name = (restaurant != null
                           && restaurant.getName() != null
                           && !restaurant.getName().isBlank())
                          ? restaurant.getName() : "nhà hàng của bạn";
            lblInfoRestaurant.setText("Tài khoản sẽ được tạo cho nhà hàng: " + name);
        } catch (Exception e) {
            lblInfoRestaurant.setText("Tài khoản sẽ được tạo cho nhà hàng của bạn");
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** @return {@code true} if the account was created before the dialog closed. */
    public boolean isSuccess() { return success; }

    // ── Handlers ─────────────────────────────────────────────────────────────

    @FXML
    private void onSubmit() {
        lblError.setText("");

        // ── Validate ──
        String errorMsg = validateForm();
        if (errorMsg != null) {
            lblError.setText(errorMsg);
            return;
        }

        // Snapshot inputs before any async work
        final String name     = tfName.getText().trim();
        final String email    = tfEmail.getText().trim();
        final String password = pfPassword.getText();
        final String roleName = toRoleName(cmbRole.getValue());

        // ── Operation Token: confirm before persisting ─────────────────────
        // targetId = 0 because the user does not exist yet
        boolean confirmed = ConfirmOperationDialogController.show(
                getStage(), OperationType.CHANGE_ROLE, 0L);
        if (!confirmed) return;

        // ── Background task ───────────────────────────────────────────────
        setSubmitBusy(true);

        Task<Long> task = new Task<>() {
            @Override
            protected Long call() throws Exception {
                return DataManager.getInstance().registerStaff(name, email, password, roleName);
            }
        };

        task.setOnSucceeded(e -> {
            success = true;
            getStage().close();
        });

        task.setOnFailed(e -> Platform.runLater(() -> {
            Throwable cause = task.getException();
            lblError.setText(cause instanceof IllegalArgumentException
                    ? cause.getMessage()
                    : "Lỗi tạo tài khoản: " + (cause != null ? cause.getMessage() : "unknown"));
            setSubmitBusy(false);
        }));

        new Thread(task, "register-staff").start();
    }

    @FXML
    private void onCancel() { getStage().close(); }

    // ── Validation ────────────────────────────────────────────────────────────

    private String validateForm() {
        String name     = tfName.getText().trim();
        String email    = tfEmail.getText().trim();
        String password = pfPassword.getText();
        String confirm  = pfConfirm.getText();

        if (name.isEmpty())                               return "Vui lòng nhập họ và tên.";
        if (email.isEmpty())                              return "Vui lòng nhập email đăng nhập.";
        if (!email.contains("@") || email.contains(" ")) return "Email không hợp lệ (phải chứa @ và không có khoảng trắng).";
        if (password.length() < 6)                        return "Mật khẩu phải có ít nhất 6 ký tự.";
        if (!password.equals(confirm))                    return "Xác nhận mật khẩu không khớp.";
        if (cmbRole.getValue() == null)                   return "Vui lòng chọn vai trò.";
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String toRoleName(String displayRole) {
        if (displayRole == null) return "WAITER";
        return switch (displayRole) {
            case "Đầu bếp"  -> "CHEF";
            case "Thu ngân" -> "CASHIER";
            default         -> "WAITER";
        };
    }

    private void setSubmitBusy(boolean busy) {
        btnSubmit.setDisable(busy);
        btnSubmit.setText(busy ? "Đang tạo..." : "Tạo tài khoản →");
    }

    private Stage getStage() {
        return (Stage) btnSubmit.getScene().getWindow();
    }
}