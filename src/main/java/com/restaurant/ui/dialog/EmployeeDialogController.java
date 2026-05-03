package com.restaurant.ui.dialog;

import com.restaurant.dao.EmployeeDAO;
import com.restaurant.data.DataManager;
import com.restaurant.model.Employee;
import com.restaurant.session.AppSession;
import com.restaurant.session.OperationType;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controller for EmployeeDialog.fxml.
 *
 * <p>Supports both ADD (item == null) and EDIT (item != null) modes.
 * Use the static {@link #show} factory to open the dialog modally:
 *
 * <pre>{@code
 *   EmployeeDialogController.show(owner, null,   saved -> addEmployee(saved));  // add
 *   EmployeeDialogController.show(owner, target, saved -> updateEmployee(saved)); // edit
 * }</pre>
 *
 * <p>Phase-5B role-assignment is preserved: when the current user holds
 * {@link Permission#ASSIGN_ROLE} and is editing an existing employee, a
 * {@link ComboBox} with allowed roles is shown and changing it requires an
 * Operation Token confirmation.
 */
public class EmployeeDialogController {

    // ── FXML ─────────────────────────────────────────────────────────────────

    @FXML private Label     lblTitle;
    @FXML private Button    btnSave;

    @FXML private TextField  tfId;
    @FXML private TextField  tfName;
    @FXML private TextField  tfCccd;
    @FXML private TextField  tfPhone;
    @FXML private TextField  tfAddress;
    @FXML private DatePicker dpStartDate;

    @FXML private Label     lblRole;
    @FXML private ComboBox<String> cmbRole;

    @FXML private Label errName;
    @FXML private Label errPhone;

    // ── State ─────────────────────────────────────────────────────────────────

    private Employee           item;
    private Consumer<Employee> onSave;

    private final boolean      canAssignRole =
            AppSession.getInstance().hasPermission(Permission.ASSIGN_ROLE);
    private final EmployeeDAO  employeeDAO   = new EmployeeDAO();

    private static final DateTimeFormatter ISO   = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISP  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Load the FXML, populate data, and show the dialog modally.
     *
     * @param owner  parent window (may be null)
     * @param item   existing employee to edit, or {@code null} for add mode
     * @param onSave callback invoked on the FX thread with the saved employee
     */
    public static void show(Window owner, Employee item, Consumer<Employee> onSave) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    EmployeeDialogController.class.getResource("EmployeeDialog.fxml"));
            Parent root = loader.load();

            EmployeeDialogController ctrl = loader.getController();
            ctrl.initData(item, onSave);

            Stage stage = new Stage();
            if (owner != null) stage.initOwner(owner);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(item == null ? "Thêm nhân viên mới" : "Cập nhật thông tin nhân viên");
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();

        } catch (IOException e) {
            System.err.println("[EmployeeDialogController] Lỗi tải FXML: " + e.getMessage());
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    /** Called by the static factory after the controller is wired by FXMLLoader. */
    public void initData(Employee item, Consumer<Employee> onSave) {
        this.item   = item;
        this.onSave = onSave;

        boolean isEdit = (item != null);

        // Title + button label
        lblTitle.setText(isEdit ? "Cập nhật thông tin nhân viên" : "Thêm nhân viên mới");
        btnSave.setText(isEdit ? "Lưu" : "Thêm");

        // ID field
        tfId.setText(isEdit ? item.getId() : DataManager.getInstance().generateEmployeeId());
        tfId.setEditable(false);

        // Role ComboBox — show only when canAssignRole
        if (canAssignRole) {
            List<String> allowed = buildAllowedRoles();
            if (!allowed.isEmpty()) {
                cmbRole.getItems().setAll(allowed);
                lblRole.setVisible(true);  lblRole.setManaged(true);
                cmbRole.setVisible(true);  cmbRole.setManaged(true);

                if (!isEdit) {
                    // Can only assign after the employee record exists
                    cmbRole.setDisable(true);
                    cmbRole.setTooltip(new Tooltip("Gán role sau khi tạo nhân viên"));
                }
            }
        }

        // Pre-fill when editing
        if (isEdit) fillData();
    }

    private List<String> buildAllowedRoles() {
        if (RbacGuard.getInstance().isSuperAdmin()) {
            return List.of("WAITER", "CHEF", "CASHIER", "RESTAURANT_ADMIN", "SUPER_ADMIN");
        } else if (RbacGuard.getInstance().isRestaurantAdmin()) {
            return List.of("WAITER", "CHEF", "CASHIER");
        }
        return List.of();
    }

    private void fillData() {
        tfName.setText(item.getName());
        tfCccd.setText(item.getCccd());
        tfPhone.setText(item.getPhone());
        tfAddress.setText(item.getAddress());

        // Parse ISO or display date into LocalDate
        String raw = item.getStartDate();
        if (raw != null && !raw.isBlank()) {
            try {
                dpStartDate.setValue(LocalDate.parse(raw, ISO));
            } catch (Exception e1) {
                try {
                    dpStartDate.setValue(LocalDate.parse(raw, DISP));
                } catch (Exception ignored) {}
            }
        }

        // Select current role in ComboBox
        if (cmbRole != null && item.getRole() != null) {
            String sysRole = toSystemRole(item.getRole());
            cmbRole.getItems().stream()
                    .filter(r -> r.equalsIgnoreCase(sysRole))
                    .findFirst()
                    .ifPresent(cmbRole::setValue);
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @FXML
    private void onSave() {
        if (!validate()) return;

        String id        = tfId.getText().trim();
        String name      = tfName.getText().trim();
        String cccd      = tfCccd.getText().trim();
        String phone     = tfPhone.getText().trim();
        String address   = tfAddress.getText().trim();
        String startDate = dpStartDate.getValue() != null
                           ? dpStartDate.getValue().format(ISO) : "";

        Employee saved = new Employee(
                id, name, cccd, phone, address, startDate,
                item != null ? item.getRole() : Employee.Role.PHUC_VU);

        // Invoke caller's save callback
        onSave.accept(saved);

        // ── Phase 5B: role assignment with Operation Token ─────────────────
        if (canAssignRole && cmbRole != null && cmbRole.isVisible()
                && !cmbRole.isDisable() && item != null) {
            String selectedRole = cmbRole.getValue();
            if (selectedRole != null && !selectedRole.isBlank()) {
                long targetId = resolveTargetUserId(id);
                boolean confirmed = ConfirmOperationDialogController.show(
                        getStage(), OperationType.CHANGE_ROLE, targetId);
                if (!confirmed) { close(); return; }

                try {
                    employeeDAO.updateUserRole(id, selectedRole);
                    showAlert(Alert.AlertType.INFORMATION,
                            "Thông báo",
                            "Thay đổi role sẽ có hiệu lực sau lần đăng nhập tiếp theo.");
                } catch (SecurityException ex) {
                    showAlert(Alert.AlertType.ERROR, "Lỗi phân quyền",
                            "Không có quyền:\n" + ex.getMessage());
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Lỗi",
                            "Lỗi khi cập nhật role:\n" + ex.getMessage());
                }
            }
        }

        close();
    }

    @FXML
    private void onCancel() { close(); }

    // ── Validation ────────────────────────────────────────────────────────────

    private boolean validate() {
        boolean ok = true;

        String name = tfName.getText().trim();
        if (name.isEmpty()) {
            setError(errName, "Vui lòng nhập họ và tên");
            ok = false;
        } else {
            clearError(errName);
        }

        String phone = tfPhone.getText().trim();
        if (phone.isEmpty()) {
            setError(errPhone, "Vui lòng nhập số điện thoại");
            ok = false;
        } else if (!phone.matches("0\\d{9}")) {
            setError(errPhone, "SDT phải có 10 chữ số, bắt đầu bằng 0");
            ok = false;
        } else {
            clearError(errPhone);
        }

        return ok;
    }

    private void setError(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    private void clearError(Label lbl) {
        lbl.setText("");
        lbl.setVisible(false);
        lbl.setManaged(false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long resolveTargetUserId(String employeeId) {
        try {
            return employeeDAO.findUserId(employeeId).map(Long::longValue).orElse(0L);
        } catch (Exception ignored) { return 0L; }
    }

    private String toSystemRole(Employee.Role role) {
        if (role == null) return "WAITER";
        return switch (role) {
            case DAU_BEP  -> "CHEF";
            case THU_NGAN -> "CASHIER";
            case QUAN_LY  -> "RESTAURANT_ADMIN";
            default       -> "WAITER";
        };
    }

    private Stage getStage() {
        return (Stage) btnSave.getScene().getWindow();
    }

    private void close() {
        getStage().close();
    }

    private void showAlert(Alert.AlertType type, String header, String content) {
        Platform.runLater(() -> {
            Alert a = new Alert(type);
            a.initOwner(getStage());
            a.setHeaderText(header);
            a.setContentText(content);
            a.showAndWait();
        });
    }
}