package com.restaurant.ui.dialog;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

import javax.swing.*;

import com.restaurant.dao.EmployeeDAO;
import com.restaurant.data.DataManager;
import com.restaurant.model.Employee;
import com.restaurant.session.AppSession;
import com.restaurant.session.OperationType;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;
import com.restaurant.ui.AppComboBox;
import com.restaurant.ui.AppTextField;
import com.restaurant.ui.UIConstants;

/**
 * EmployeeDialog — Phase 4 (redesigned)
 *
 * <p>Extends {@link AppDialog}. Uses {@link AppTextField} / {@link AppComboBox}
 * for consistent look-and-feel, inline validation, and a styled date spinner.
 *
 * <p>Phase 5B role-assignment behaviour is preserved unchanged.
 */
public class EmployeeDialog extends AppDialog {

    // ── Fields ───────────────────────────────────────────────────────────────
    private AppTextField    tfId, tfName, tfCccd, tfPhone, tfAddress;
    private JSpinner        spinDate;      // styled date picker
    private AppComboBox<String> cmbRole;

    // Error labels (wired in buildBody)
    private JLabel errName, errPhone, errDate;

    // ── Role assignment (Phase 5B) ────────────────────────────────────────────
    private       List<String>     allowedRoles;
    private final boolean          canAssignRole;
    private final EmployeeDAO      employeeDAO = new EmployeeDAO();

    // ── Data ─────────────────────────────────────────────────────────────────
    private final Employee         item;
    private final Consumer<Employee> onSave;

    // ── Date format ──────────────────────────────────────────────────────────
    private static final String DATE_PATTERN = "dd/MM/yyyy";

    // ─────────────────────────────────────────────────────────────────────────

    public EmployeeDialog(Window owner, Employee item, Consumer<Employee> onSave) {
        super(owner);
        this.item   = item;
        this.onSave = onSave;

        // Phase 5B: determine allowed roles
        this.canAssignRole = AppSession.getInstance().hasPermission(Permission.ASSIGN_ROLE);
        if (canAssignRole) {
            if (RbacGuard.getInstance().isSuperAdmin()) {
                allowedRoles = List.of("WAITER", "CHEF", "CASHIER", "RESTAURANT_ADMIN", "SUPER_ADMIN");
            } else if (RbacGuard.getInstance().isRestaurantAdmin()) {
                allowedRoles = List.of("WAITER", "CHEF", "CASHIER");
            } else {
                allowedRoles = List.of();
            }
        }

        // Finish layout (super() calls buildBody via template, but we need size after)
        int h = (canAssignRole && allowedRoles != null && !allowedRoles.isEmpty()) ? 520 : 480;
        setSize(540, h);
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    // ── AppDialog contract ───────────────────────────────────────────────────

    @Override
    protected String getDialogTitle() {
        return item == null ? "Thêm nhân viên mới" : "Cập nhật thông tin nhân viên";
    }

    @Override
    protected String getSaveLabel() {
        return item == null ? "Thêm" : "Lưu";
    }

    @Override
    protected JPanel buildBody() {
        FormBuilder fb = new FormBuilder();

        // ── ID ────────────────────────────────────────────────────────────
        tfId = new AppTextField();
        tfId.setFont(UIConstants.FONT_BODY);
        if (item == null) {
            tfId.setText(DataManager.getInstance().generateEmployeeId());
            tfId.setEditable(false);
            tfId.setEnabled(false);
        } else {
            tfId.setText(item.getId());
            tfId.setEditable(false);
            tfId.setEnabled(false);
        }
        fb.addRow("ID:", tfId);

        // ── Name ──────────────────────────────────────────────────────────
        tfName = new AppTextField("Nhập họ và tên...");
        errName = fb.addRow("Họ và tên *:", tfName);
        tfName.attachErrorLabel(errName);

        // ── CCCD ──────────────────────────────────────────────────────────
        tfCccd = new AppTextField("12 chữ số");
        fb.addRow("CCCD:", tfCccd);

        // ── Phone ─────────────────────────────────────────────────────────
        tfPhone = new AppTextField("0xxxxxxxxx");
        errPhone = fb.addRow("SDT *:", tfPhone);
        tfPhone.attachErrorLabel(errPhone);

        // ── Address ───────────────────────────────────────────────────────
        tfAddress = new AppTextField("Địa chỉ...");
        fb.addRow("Địa chỉ:", tfAddress);

        // ── Start Date (JSpinner) ─────────────────────────────────────────
        spinDate = buildStyledDateSpinner();
        errDate = fb.addRow("Ngày vào làm:", spinDate);

        // ── Role (Phase 5B) ───────────────────────────────────────────────
        if (canAssignRole && allowedRoles != null && !allowedRoles.isEmpty()) {
            cmbRole = new AppComboBox<>(allowedRoles.toArray(new String[0]));
            cmbRole.setFont(UIConstants.FONT_BODY);
            if (item == null) {
                cmbRole.setEnabled(false);
                cmbRole.setToolTipText("Gán role sau khi tạo nhân viên");
            }
            fb.addRow("Role:", cmbRole);
        }

        // ── Pre-fill when editing ─────────────────────────────────────────
        if (item != null) fillData();

        return fb.getPanel();
    }

    // ── Fill data ────────────────────────────────────────────────────────────

    private void fillData() {
        tfName.setText(item.getName());
        tfCccd.setText(item.getCccd());
        tfPhone.setText(item.getPhone());
        tfAddress.setText(item.getAddress());

        // Parse date string → spinner
        if (item.getStartDate() != null && !item.getStartDate().isBlank()) {
            try {
                Date d = new SimpleDateFormat(DATE_PATTERN).parse(item.getStartDate());
                spinDate.setValue(d);
            } catch (Exception ignored) {}
        }

        if (cmbRole != null && item.getRole() != null) {
            String sysRole = toSystemRole(item.getRole());
            for (int i = 0; i < cmbRole.getItemCount(); i++) {
                if (cmbRole.getItemAt(i).equalsIgnoreCase(sysRole)) {
                    cmbRole.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    // ── Validation + Save ────────────────────────────────────────────────────

    @Override
    protected void onSave() {
        boolean valid = true;

        String name = tfName.getText().trim();
        if (name.isEmpty()) {
            tfName.setError("Vui lòng nhập họ và tên");
            valid = false;
        } else {
            tfName.setError(null);
        }

        String phone = tfPhone.getText().trim();
        if (phone.isEmpty()) {
            tfPhone.setError("Vui lòng nhập số điện thoại");
            valid = false;
        } else if (!phone.matches("0\\d{9}")) {
            tfPhone.setError("SDT phải có 10 chữ số, bắt đầu bằng 0");
            valid = false;
        } else {
            tfPhone.setError(null);
        }

        if (!valid) return;

        String id        = tfId.getText().trim();
        String cccd      = tfCccd.getText().trim();
        String address   = tfAddress.getText().trim();
        String startDate = new SimpleDateFormat(DATE_PATTERN).format((Date) spinDate.getValue());

        // Callback with basic employee info
        onSave.accept(new Employee(id, name, cccd, phone, address, startDate,
                item != null ? item.getRole() : Employee.Role.PHUC_VU));

        // ── Phase 5B + Phase 3: role assignment with Operation Token ──────
        if (cmbRole != null && cmbRole.isEnabled() && item != null) {
            String selectedRole = (String) cmbRole.getSelectedItem();
            if (selectedRole != null && !selectedRole.isBlank()) {
                long targetId = resolveTargetUserId(id);
                boolean confirmed = ConfirmOperationDialog.show(
                    this, OperationType.CHANGE_ROLE, targetId);
                if (!confirmed) {
                    close();
                    return;
                }
                try {
                    employeeDAO.updateUserRole(id, selectedRole);
                    JOptionPane.showMessageDialog(this,
                        "Thay đổi role sẽ có hiệu lực sau lần đăng nhập tiếp theo.",
                        "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                } catch (SecurityException ex) {
                    JOptionPane.showMessageDialog(this,
                        "Không có quyền:\n" + ex.getMessage(),
                        "Lỗi phân quyền", JOptionPane.ERROR_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                        "Lỗi khi cập nhật role:\n" + ex.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        close();
    }

    // ── Date spinner ─────────────────────────────────────────────────────────

    /**
     * Build a {@link JSpinner} configured for date input, styled to match
     * {@link AppTextField} (border, font, height).
     */
    private JSpinner buildStyledDateSpinner() {
        SpinnerDateModel model = new SpinnerDateModel(
            new Date(), null, null, Calendar.DAY_OF_MONTH);
        JSpinner spinner = new JSpinner(model);

        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, DATE_PATTERN);
        editor.getTextField().setFont(UIConstants.FONT_BODY);
        editor.getTextField().setHorizontalAlignment(SwingConstants.LEFT);
        spinner.setEditor(editor);
        spinner.setFont(UIConstants.FONT_BODY);

        // Match AppTextField border style
        spinner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xCBD5E1), 1, true),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)));

        // Focus highlight
        editor.getTextField().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                spinner.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0x3B82F6), 2, true),
                    BorderFactory.createEmptyBorder(2, 7, 2, 7)));
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                spinner.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xCBD5E1), 1, true),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)));
            }
        });

        return spinner;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long resolveTargetUserId(String employeeId) {
        try {
            java.util.Optional<Long> opt = employeeDAO.findUserId(employeeId);
            return opt.isPresent() ? opt.get() : 0L;
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
}