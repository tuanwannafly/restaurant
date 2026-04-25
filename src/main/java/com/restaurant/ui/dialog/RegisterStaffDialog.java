package com.restaurant.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.CompoundBorder;

import com.restaurant.data.DataManager;
import com.restaurant.session.OperationType;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.UIConstants;

/**
 * Dialog để RESTAURANT_ADMIN tạo tài khoản đăng nhập cho nhân viên mới.
 *
 * <p>Được gọi từ {@code EmployeePanel} và mở modal. Sau khi dialog đóng,
 * caller kiểm tra {@link #isSuccess()} để biết tài khoản có được tạo thành công.
 *
 * <p><b>Giai đoạn 3 — Operation Token:</b> trước khi gọi {@code registerStaff},
 * người dùng phải xác nhận bằng {@link ConfirmOperationDialog} để ngăn chặn
 * việc gán role nhầm do thao tác vô ý.
 */
public class RegisterStaffDialog extends JDialog {

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean success = false;

    // ── Form fields ───────────────────────────────────────────────────────────
    private JTextField      tfName;
    private JTextField      tfEmail;
    private JPasswordField  pfPassword;
    private JPasswordField  pfConfirm;
    private JComboBox<String> cmbRole;

    // ── Action controls ───────────────────────────────────────────────────────
    private JLabel        lblError;
    private RoundedButton btnSubmit;

    // ── Constructor ───────────────────────────────────────────────────────────

    public RegisterStaffDialog(Frame owner) {
        super(owner, "Tạo tài khoản nhân viên", ModalityType.APPLICATION_MODAL);
        buildUI();
        setSize(460, 520);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** @return {@code true} nếu tài khoản được tạo thành công trước khi dialog đóng. */
    public boolean isSuccess() {
        return success;
    }

    // ── UI Builder ────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildForm(),    BorderLayout.CENTER);
        root.add(buildBtnBar(),  BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.PRIMARY);
        header.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

        JLabel title = new JLabel("Tạo tài khoản nhân viên");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_WHITE);
        header.add(title, BorderLayout.WEST);

        return header;
    }

    // ── Form ──────────────────────────────────────────────────────────────────

    private JPanel buildForm() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(BorderFactory.createEmptyBorder(28, 40, 12, 40));

        // ── Họ và tên ──
        wrapper.add(makeLabel("Họ và tên *"));
        wrapper.add(Box.createVerticalStrut(6));
        tfName = makeTextField();
        wrapper.add(tfName);

        wrapper.add(Box.createVerticalStrut(16));

        // ── Email ──
        wrapper.add(makeLabel("Email đăng nhập *"));
        wrapper.add(Box.createVerticalStrut(6));
        tfEmail = makeTextField();
        wrapper.add(tfEmail);

        wrapper.add(Box.createVerticalStrut(16));

        // ── Mật khẩu ──
        wrapper.add(makeLabel("Mật khẩu *"));
        wrapper.add(Box.createVerticalStrut(6));
        pfPassword = makePasswordField();
        wrapper.add(pfPassword);

        wrapper.add(Box.createVerticalStrut(16));

        // ── Xác nhận mật khẩu ──
        wrapper.add(makeLabel("Xác nhận mật khẩu *"));
        wrapper.add(Box.createVerticalStrut(6));
        pfConfirm = makePasswordField();
        wrapper.add(pfConfirm);

        wrapper.add(Box.createVerticalStrut(16));

        // ── Vai trò ──
        wrapper.add(makeLabel("Vai trò *"));
        wrapper.add(Box.createVerticalStrut(6));
        cmbRole = new JComboBox<>(new String[]{"Phục vụ", "Đầu bếp", "Thu ngân"});
        cmbRole.setFont(UIConstants.FONT_BODY);
        cmbRole.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        cmbRole.setAlignmentX(Component.LEFT_ALIGNMENT);
        cmbRole.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        wrapper.add(cmbRole);

        wrapper.add(Box.createVerticalStrut(16));

        // ── Info box ──
        wrapper.add(buildInfoBox());

        wrapper.add(Box.createVerticalStrut(10));

        // ── Error label ──
        lblError = new JLabel(" ");
        lblError.setFont(UIConstants.FONT_SMALL);
        lblError.setForeground(UIConstants.DANGER);
        lblError.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(lblError);

        return wrapper;
    }

    // ── Info box ──────────────────────────────────────────────────────────────

    private JPanel buildInfoBox() {
        String restaurantName;
        try {
            var restaurant = DataManager.getInstance().getMyRestaurant();
            restaurantName = (restaurant != null && restaurant.getName() != null
                              && !restaurant.getName().isBlank())
                             ? restaurant.getName()
                             : "nhà hàng của bạn";
        } catch (Exception e) {
            restaurantName = "nhà hàng của bạn";
        }

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(new Color(0xEFF6FF));
        box.setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(new Color(0xBFDBFE), 1, true),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JLabel line1 = new JLabel("ℹ️  Tài khoản sẽ được tạo cho nhà hàng: " + restaurantName);
        line1.setFont(UIConstants.FONT_SMALL);
        line1.setForeground(UIConstants.TEXT_PRIMARY);
        line1.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel line2 = new JLabel("   Nhân viên có thể đăng nhập ngay sau khi tạo.");
        line2.setFont(UIConstants.FONT_SMALL);
        line2.setForeground(UIConstants.TEXT_SECONDARY);
        line2.setAlignmentX(Component.LEFT_ALIGNMENT);

        box.add(line1);
        box.add(Box.createVerticalStrut(4));
        box.add(line2);

        return box;
    }

    // ── Button bar ────────────────────────────────────────────────────────────

    private JPanel buildBtnBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR));

        RoundedButton btnCancel = RoundedButton.outline("Hủy");
        btnCancel.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnCancel.addActionListener(e -> dispose());

        btnSubmit = new RoundedButton("Tạo tài khoản →");
        btnSubmit.setPreferredSize(new Dimension(150, UIConstants.BTN_HEIGHT));
        btnSubmit.addActionListener(e -> submit());

        bar.add(btnCancel);
        bar.add(btnSubmit);
        return bar;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private String validateForm() {
        String name     = tfName.getText().trim();
        String email    = tfEmail.getText().trim();
        String password = new String(pfPassword.getPassword());
        String confirm  = new String(pfConfirm.getPassword());

        if (name.isEmpty())                              return "Vui lòng nhập họ và tên.";
        if (email.isEmpty())                             return "Vui lòng nhập email đăng nhập.";
        if (!email.contains("@") || email.contains(" "))return "Email không hợp lệ (phải chứa @ và không có khoảng trắng).";
        if (password.length() < 6)                       return "Mật khẩu phải có ít nhất 6 ký tự.";
        if (!password.equals(confirm))                   return "Xác nhận mật khẩu không khớp.";
        if (cmbRole.getSelectedItem() == null)           return "Vui lòng chọn vai trò.";
        return null;
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private void submit() {
        // Xoá lỗi cũ
        lblError.setText(" ");

        // Validate form trước
        String error = validateForm();
        if (error != null) {
            lblError.setText(error);
            return;
        }

        // Snapshot inputs trước khi mở dialog xác nhận
        final String name     = tfName.getText().trim();
        final String email    = tfEmail.getText().trim();
        final String password = new String(pfPassword.getPassword());
        final String roleName = switch ((String) cmbRole.getSelectedItem()) {
            case "Phục vụ"  -> "WAITER";
            case "Đầu bếp"  -> "CHEF";
            case "Thu ngân" -> "CASHIER";
            default -> throw new IllegalArgumentException("Vai trò không hợp lệ");
        };

        // ── Operation Token: xác nhận trước khi gán role cho user mới ─────
        // targetId = 0 vì user chưa tồn tại; type đủ để nhận diện thao tác.
        boolean confirmed = ConfirmOperationDialog.show(
            SwingUtilities.getWindowAncestor(this),
            OperationType.CHANGE_ROLE,
            0L);
        if (!confirmed) return;   // người dùng huỷ hoặc nhập sai mã → dừng lại

        // Disable button, hiện "Đang tạo..."
        btnSubmit.setEnabled(false);
        btnSubmit.setText("Đang tạo...");

        new SwingWorker<Long, Void>() {
            String errorMsg = null;

            @Override
            protected Long doInBackground() throws Exception {
                return DataManager.getInstance().registerStaff(name, email, password, roleName);
            }

            @Override
            protected void done() {
                try {
                    get(); // throws ExecutionException on error
                    success = true;
                    dispose();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IllegalArgumentException) {
                        errorMsg = cause.getMessage();
                    } else {
                        errorMsg = "Lỗi tạo tài khoản: " + (cause != null ? cause.getMessage() : ex.getMessage());
                    }
                    lblError.setText(errorMsg);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    lblError.setText("Thao tác bị gián đoạn.");
                } finally {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Tạo tài khoản →");
                }
            }
        }.execute();
    }

    // ── Field factories ───────────────────────────────────────────────────────

    private JLabel makeLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JTextField makeTextField() {
        JTextField tf = new JTextField();
        tf.setFont(UIConstants.FONT_BODY);
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        tf.setAlignmentX(Component.LEFT_ALIGNMENT);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        return tf;
    }

    private JPasswordField makePasswordField() {
        JPasswordField pf = new JPasswordField();
        pf.setFont(UIConstants.FONT_BODY);
        pf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        pf.setAlignmentX(Component.LEFT_ALIGNMENT);
        pf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        return pf;
    }
}
