package com.restaurant.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.restaurant.dao.EmployeeDAO;
import com.restaurant.data.DataManager;
import com.restaurant.model.Employee;
import com.restaurant.session.AppSession;
import com.restaurant.session.OperationType;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.UIConstants;

/**
 * Dialog thêm / cập nhật thông tin nhân viên.
 *
 * <p><b>Phase 5B — Role Assignment:</b><br>
 * {@code cmbRole} chỉ hiện khi người dùng có quyền {@link Permission#ASSIGN_ROLE}.
 * Danh sách role được lọc theo cấp bậc:
 * <ul>
 *   <li>SUPER_ADMIN → toàn bộ role</li>
 *   <li>RESTAURANT_ADMIN → WAITER / CHEF / CASHIER</li>
 *   <li>Không có quyền → cmbRole ẩn hoàn toàn</li>
 * </ul>
 *
 * <p><b>Giai đoạn 3 — Operation Token:</b><br>
 * Khi thực hiện thay đổi role của nhân viên khác, dialog yêu cầu xác nhận
 * bằng {@link ConfirmOperationDialog} trước khi gọi DAO.
 *
 * <p>Khi dialog ở chế độ <em>thêm mới</em> (chưa có employeeId),
 * cmbRole hiện nhưng bị disable với tooltip hướng dẫn.
 */
public class EmployeeDialog extends JDialog {
    private Consumer<Employee> onSave;
    private Employee item;

    private JTextField tfId, tfName, tfCccd, tfPhone, tfAddress, tfStartDate;

    // ── Role assignment (Phase 5B) ─────────────────────────────────────────
    /** null nếu người dùng không có quyền ASSIGN_ROLE — row role bị ẩn hoàn toàn. */
    private JComboBox<String> cmbRole;
    /** Danh sách role được phép gán theo cấp bậc của người dùng hiện tại. */
    private List<String> allowedRoles;
    /** Có hiện UI gán role không (dựa vào hasPermission). */
    private final boolean canAssignRole;

    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    public EmployeeDialog(Window owner, Employee item, Consumer<Employee> onSave) {
        super(owner, item == null ? "Thêm nhân viên" : "Cập nhật nhân viên", ModalityType.APPLICATION_MODAL);
        this.item = item;
        this.onSave = onSave;

        // ── Quyết định hiện/ẩn cmbRole ────────────────────────────────────
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

        buildUI();
        if (item != null) fillData();

        int height = (canAssignRole && allowedRoles != null && !allowedRoles.isEmpty()) ? 500 : 460;
        setSize(500, height);
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        JLabel title = new JLabel(item == null ? "Thêm nhân viên mới" : "Cập nhật thông tin nhân viên");
        title.setFont(UIConstants.FONT_TITLE);
        title.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(0, 24, 12, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 6, 7, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        tfId        = field();
        tfName      = field();
        tfCccd      = field();
        tfPhone     = field();
        tfAddress   = field();
        tfStartDate = field();

        if (item == null) {
            tfId.setText(DataManager.getInstance().generateEmployeeId());
            tfId.setEditable(false);
            tfId.setBackground(new Color(0xF3F4F6));
        }

        addRow(form, gbc, 0, "ID:",           tfId);
        addRow(form, gbc, 1, "Họ và tên:",    tfName);
        addRow(form, gbc, 2, "CCCD:",          tfCccd);
        addRow(form, gbc, 3, "SDT:",           tfPhone);
        addRow(form, gbc, 4, "Địa chỉ:",      tfAddress);
        addRow(form, gbc, 5, "Ngày vào làm:", tfStartDate);

        // ── Phase 5B: cmbRole ──────────────────────────────────────────────
        if (canAssignRole && allowedRoles != null && !allowedRoles.isEmpty()) {
            cmbRole = new JComboBox<>(allowedRoles.toArray(new String[0]));
            cmbRole.setFont(UIConstants.FONT_BODY);

            if (item == null) {
                cmbRole.setEnabled(false);
                cmbRole.setToolTipText("Gán role sau khi tạo nhân viên");
            }

            addRow(form, gbc, 6, "Role:", cmbRole);
        }

        root.add(form, BorderLayout.CENTER);

        // ── Button bar ─────────────────────────────────────────────────────
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnBar.setBackground(Color.WHITE);
        btnBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR));

        RoundedButton btnCancel = RoundedButton.outline("Hủy");
        btnCancel.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnCancel.addActionListener(e -> dispose());

        RoundedButton btnSave = new RoundedButton(item == null ? "Thêm" : "Lưu");
        btnSave.setPreferredSize(new Dimension(100, UIConstants.BTN_HEIGHT));
        btnSave.addActionListener(e -> save());

        btnBar.add(btnCancel);
        btnBar.add(btnSave);
        root.add(btnBar, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void fillData() {
        tfId.setText(item.getId());
        tfId.setEditable(false);
        tfId.setBackground(new Color(0xF3F4F6));
        tfName.setText(item.getName());
        tfCccd.setText(item.getCccd());
        tfPhone.setText(item.getPhone());
        tfAddress.setText(item.getAddress());
        tfStartDate.setText(item.getStartDate());

        if (cmbRole != null && item.getRole() != null) {
            String systemRole = toSystemRole(item.getRole());
            for (int i = 0; i < cmbRole.getItemCount(); i++) {
                if (cmbRole.getItemAt(i).equalsIgnoreCase(systemRole)) {
                    cmbRole.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void save() {
        String id        = tfId.getText().trim();
        String name      = tfName.getText().trim();
        String cccd      = tfCccd.getText().trim();
        String phone     = tfPhone.getText().trim();
        String address   = tfAddress.getText().trim();
        String startDate = tfStartDate.getText().trim();

        if (name.isEmpty() || phone.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập Họ tên và SDT!",
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Gọi callback lưu thông tin cơ bản của nhân viên
        onSave.accept(new Employee(id, name, cccd, phone, address, startDate,
                item != null ? item.getRole() : Employee.Role.PHUC_VU));

        // ── Phase 5B + Giai đoạn 3: gán role với Operation Token ──────────
        if (cmbRole != null && cmbRole.isEnabled() && item != null) {
            String selectedRole = (String) cmbRole.getSelectedItem();
            if (selectedRole != null && !selectedRole.isBlank()) {

                // Xác định targetId = user_id liên kết với nhân viên
                long targetId = resolveTargetUserId(id);

                // ── Operation Token: yêu cầu xác nhận trước khi đổi role ──
                boolean confirmed = ConfirmOperationDialog.show(
                    this, OperationType.CHANGE_ROLE, targetId);
                if (!confirmed) {
                    // Người dùng huỷ → đóng dialog mà không thay đổi role
                    dispose();
                    return;
                }

                try {
                    employeeDAO.updateUserRole(id, selectedRole);
                    JOptionPane.showMessageDialog(this,
                            "Thay đổi role sẽ có hiệu lực sau lần đăng nhập tiếp theo của nhân viên này.",
                            "Thông báo",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (SecurityException ex) {
                    JOptionPane.showMessageDialog(this,
                            "Không có quyền thực hiện thao tác này:\n" + ex.getMessage(),
                            "Lỗi phân quyền",
                            JOptionPane.ERROR_MESSAGE);
                } catch (IllegalStateException ex) {
                    JOptionPane.showMessageDialog(this,
                            ex.getMessage(),
                            "Lỗi",
                            JOptionPane.WARNING_MESSAGE);
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(this,
                            ex.getMessage(),
                            "Lỗi role",
                            JOptionPane.ERROR_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "Lỗi không xác định khi cập nhật role:\n" + ex.getMessage(),
                            "Lỗi",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        dispose();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Lấy user_id liên kết với employeeId để dùng làm targetId cho Operation Token.
     * Nếu nhân viên chưa có tài khoản, trả về 0 (token vẫn hợp lệ — type đủ để nhận diện).
     */
    private long resolveTargetUserId(String employeeId) {
        try {
            var opt = employeeDAO.findUserIdByEmployeeId(employeeId);
            return opt.isPresent() ? opt.get() : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /**
     * Chuyển {@link Employee.Role} sang tên role hệ thống (khớp với bảng roles trong DB).
     */
    private String toSystemRole(Employee.Role role) {
        if (role == null) return "WAITER";
        return switch (role) {
            case DAU_BEP  -> "CHEF";
            case THU_NGAN -> "CASHIER";
            case QUAN_LY  -> "RESTAURANT_ADMIN";
            default       -> "WAITER";
        };
    }

    private void addRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent comp) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(110, 32));
        form.add(lbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        comp.setPreferredSize(new Dimension(300, 34));
        form.add(comp, gbc);
    }

    private JTextField field() {
        JTextField tf = new JTextField();
        tf.setFont(UIConstants.FONT_BODY);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        return tf;
    }
}
