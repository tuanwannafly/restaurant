package com.restaurant.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.restaurant.data.DataManager;
import com.restaurant.model.Employee;
import com.restaurant.session.AppSession;
import com.restaurant.session.RefreshTokenService;
import com.restaurant.session.TokenStorage;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.UIConstants;

/**
 * Dialog cho phép người dùng đang đăng nhập xem và chỉnh sửa thông tin cá nhân.
 *
 * <p>Mọi role đều có thể mở dialog này (có permission {@code EDIT_OWN_PROFILE}).
 *
 * <ul>
 *   <li><b>Tab 1 — Thông tin cá nhân:</b> sửa tên, SĐT, địa chỉ; xem email/nhà hàng/vai trò
 *       (read-only). Gọi {@link DataManager#updateOwnProfile} qua SwingWorker.</li>
 *   <li><b>Tab 2 — Đổi mật khẩu:</b> verify mật khẩu cũ rồi set mật khẩu mới ≥ 6 ký tự.
 *       Gọi {@link DataManager#changeOwnPassword} qua SwingWorker.</li>
 * </ul>
 *
 * <p>Dữ liệu phone/address được load bất đồng bộ từ bảng {@code employees} (via user_id)
 * ngay sau khi dialog hiển thị, tránh block EDT.
 */
public class MyProfileDialog extends JDialog {

    // ── Form fields — Tab 1 ───────────────────────────────────────────────────
    private JTextField tfName;
    private JTextField tfPhone;
    private JTextField tfAddress;
    private JTextField tfEmail;       // read-only
    private JTextField tfRestaurant;  // read-only
    private JTextField tfRole;        // read-only
    private JLabel     lblInfo;       // status message (success / error)
    private RoundedButton btnSave;

    // ── Form fields — Tab 2 ───────────────────────────────────────────────────
    private JPasswordField pfOldPw;
    private JPasswordField pfNewPw;
    private JPasswordField pfConfirmPw;
    private JLabel         lblPwInfo;  // status message (success / error)
    private RoundedButton  btnChangePw;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Tạo dialog "Hồ sơ của tôi".
     *
     * @param owner cửa sổ cha (Frame) để dialog hiển thị đúng vị trí
     */
    public MyProfileDialog(Frame owner) {
        super(owner, "Hồ sơ của tôi", ModalityType.APPLICATION_MODAL);
        buildUI();
        setSize(480, 560);
        setResizable(false);
        setLocationRelativeTo(owner);
        // Load phone/address bất đồng bộ sau khi dialog visible
        loadEmployeeData();
    }

    // ── UI Builder ────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        root.add(buildHeader(),    BorderLayout.NORTH);
        root.add(buildTabs(),      BorderLayout.CENTER);

        setContentPane(root);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.PRIMARY);
        header.setBorder(BorderFactory.createEmptyBorder(18, 28, 18, 28));

        JLabel title = new JLabel("Hồ sơ của tôi");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_WHITE);

        // Badge hiển thị role label ở góc phải header
        JLabel roleLabel = new JLabel(AppSession.getInstance().getRoleLabel());
        roleLabel.setFont(UIConstants.FONT_SMALL);
        roleLabel.setForeground(new Color(0xBFDBFE));  // xanh nhạt
        roleLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x93C5FD), 1, true),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)));

        header.add(title,     BorderLayout.WEST);
        header.add(roleLabel, BorderLayout.EAST);
        return header;
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(UIConstants.FONT_BOLD);
        tabs.setBackground(Color.WHITE);
        tabs.setFocusable(false);

        tabs.addTab("👤  Thông tin cá nhân", buildProfileTab());
        tabs.addTab("🔒  Đổi mật khẩu",     buildPasswordTab());

        return tabs;
    }

    // ── Tab 1: Thông tin cá nhân ──────────────────────────────────────────────

    private JPanel buildProfileTab() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(BorderFactory.createEmptyBorder(20, 36, 8, 36));

        // ── Họ và tên (editable) ──
        wrapper.add(makeLabel("Họ và tên"));
        wrapper.add(Box.createVerticalStrut(5));
        tfName = makeTextField(false);
        tfName.setText(nvl(AppSession.getInstance().getUserName()));
        wrapper.add(tfName);
        wrapper.add(Box.createVerticalStrut(12));

        // ── Số điện thoại (editable, filled async) ──
        wrapper.add(makeLabel("Số điện thoại"));
        wrapper.add(Box.createVerticalStrut(5));
        tfPhone = makeTextField(false);
        tfPhone.setText("Đang tải...");
        wrapper.add(tfPhone);
        wrapper.add(Box.createVerticalStrut(12));

        // ── Địa chỉ (editable, filled async) ──
        wrapper.add(makeLabel("Địa chỉ"));
        wrapper.add(Box.createVerticalStrut(5));
        tfAddress = makeTextField(false);
        tfAddress.setText("Đang tải...");
        wrapper.add(tfAddress);
        wrapper.add(Box.createVerticalStrut(16));

        // ── Divider label ──
        JLabel divider = new JLabel("— Thông tin chỉ đọc —");
        divider.setFont(UIConstants.FONT_SMALL);
        divider.setForeground(UIConstants.TEXT_SECONDARY);
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(divider);
        wrapper.add(Box.createVerticalStrut(10));

        // ── Email (read-only) ──
        wrapper.add(makeLabel("Email"));
        wrapper.add(Box.createVerticalStrut(5));
        tfEmail = makeTextField(true);
        tfEmail.setText(nvl(AppSession.getInstance().getUserEmail()));
        wrapper.add(tfEmail);
        wrapper.add(Box.createVerticalStrut(12));

        // ── Nhà hàng + Vai trò — side by side ──
        JPanel rowPanel = new JPanel(new GridBagLayout());
        rowPanel.setOpaque(false);
        rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.insets  = new Insets(0, 0, 0, 0);
        gbc.weighty = 0;

        // Nhà hàng — chiếm 60% width
        JPanel colRestaurant = new JPanel();
        colRestaurant.setLayout(new BoxLayout(colRestaurant, BoxLayout.Y_AXIS));
        colRestaurant.setOpaque(false);
        colRestaurant.add(makeLabel("Nhà hàng"));
        colRestaurant.add(Box.createVerticalStrut(5));
        tfRestaurant = makeTextField(true);
        tfRestaurant.setText(loadRestaurantName());
        colRestaurant.add(tfRestaurant);

        // Vai trò — chiếm 40% width, với khoảng trái 10px
        JPanel colRole = new JPanel();
        colRole.setLayout(new BoxLayout(colRole, BoxLayout.Y_AXIS));
        colRole.setOpaque(false);
        colRole.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        colRole.add(makeLabel("Vai trò"));
        colRole.add(Box.createVerticalStrut(5));
        tfRole = makeTextField(true);
        tfRole.setText(AppSession.getInstance().getRoleLabel());
        colRole.add(tfRole);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.6;
        rowPanel.add(colRestaurant, gbc);
        gbc.gridx = 1; gbc.weightx = 0.4;
        rowPanel.add(colRole, gbc);

        wrapper.add(rowPanel);
        wrapper.add(Box.createVerticalStrut(16));

        // ── Status label + Save button ──
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        lblInfo = new JLabel(" ");
        lblInfo.setFont(UIConstants.FONT_SMALL);
        lblInfo.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Wrapper ngang: label ở trái, button ở phải
        JPanel actionRow = new JPanel(new BorderLayout());
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        actionRow.add(lblInfo, BorderLayout.WEST);

        btnSave = new RoundedButton("💾 Lưu thay đổi");
        btnSave.setPreferredSize(new Dimension(140, UIConstants.BTN_HEIGHT));
        btnSave.addActionListener(e -> saveProfile());
        actionRow.add(btnSave, BorderLayout.EAST);

        wrapper.add(actionRow);
        wrapper.add(Box.createVerticalStrut(8));

        return wrapper;
    }

    // ── Tab 2: Đổi mật khẩu ──────────────────────────────────────────────────

    private JPanel buildPasswordTab() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(BorderFactory.createEmptyBorder(24, 36, 8, 36));

        // ── Mật khẩu hiện tại ──
        wrapper.add(makeLabel("Mật khẩu hiện tại"));
        wrapper.add(Box.createVerticalStrut(5));
        pfOldPw = makePasswordField();
        wrapper.add(pfOldPw);
        wrapper.add(Box.createVerticalStrut(16));

        // ── Mật khẩu mới ──
        wrapper.add(makeLabel("Mật khẩu mới  (tối thiểu 6 ký tự)"));
        wrapper.add(Box.createVerticalStrut(5));
        pfNewPw = makePasswordField();
        wrapper.add(pfNewPw);
        wrapper.add(Box.createVerticalStrut(16));

        // ── Xác nhận mật khẩu mới ──
        wrapper.add(makeLabel("Xác nhận mật khẩu mới"));
        wrapper.add(Box.createVerticalStrut(5));
        pfConfirmPw = makePasswordField();
        wrapper.add(pfConfirmPw);
        wrapper.add(Box.createVerticalStrut(20));

        // ── Status label + Change-password button ──
        lblPwInfo = new JLabel(" ");
        lblPwInfo.setFont(UIConstants.FONT_SMALL);
        lblPwInfo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actionRow = new JPanel(new BorderLayout());
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        actionRow.add(lblPwInfo, BorderLayout.WEST);

        btnChangePw = new RoundedButton("🔑 Đổi mật khẩu");
        btnChangePw.setPreferredSize(new Dimension(145, UIConstants.BTN_HEIGHT));
        btnChangePw.addActionListener(e -> changePassword());
        actionRow.add(btnChangePw, BorderLayout.EAST);

        wrapper.add(actionRow);
        wrapper.add(Box.createVerticalStrut(8));

        // ── Separator + "Đăng xuất tất cả thiết bị" ──
        wrapper.add(Box.createVerticalStrut(12));
        javax.swing.JSeparator sep = new javax.swing.JSeparator();
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(UIConstants.BORDER_COLOR);
        wrapper.add(sep);
        wrapper.add(Box.createVerticalStrut(12));

        JLabel lblDeviceHint = new JLabel(
            "<html><i>Đăng xuất khỏi tất cả thiết bị sẽ xoá mọi phiên ghi nhớ đăng nhập.</i></html>");
        lblDeviceHint.setFont(UIConstants.FONT_SMALL);
        lblDeviceHint.setForeground(UIConstants.TEXT_SECONDARY);
        lblDeviceHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(lblDeviceHint);
        wrapper.add(Box.createVerticalStrut(8));

        RoundedButton btnRevokeAll = new RoundedButton("🔒 Đăng xuất tất cả thiết bị");
        btnRevokeAll.setPreferredSize(new Dimension(220, UIConstants.BTN_HEIGHT));
        btnRevokeAll.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIConstants.BTN_HEIGHT));
        btnRevokeAll.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRevokeAll.addActionListener(e -> revokeAllDevices());
        wrapper.add(btnRevokeAll);
        wrapper.add(Box.createVerticalStrut(8));

        return wrapper;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    /**
     * Load phone/address bất đồng bộ từ bảng employees (via user_id).
     * Điền vào fields ngay khi kết quả trả về, không block EDT.
     * SUPER_ADMIN hoặc user chưa có employee record → để trống.
     */
    private void loadEmployeeData() {
        new SwingWorker<Employee, Void>() {
            @Override
            protected Employee doInBackground() {
                return DataManager.getInstance().getOwnEmployeeInfo();
            }

            @Override
            protected void done() {
                try {
                    Employee emp = get();
                    if (emp != null) {
                        tfPhone.setText(nvl(emp.getPhone()));
                        tfAddress.setText(nvl(emp.getAddress()));
                    } else {
                        // SUPER_ADMIN hoặc user không có employee record
                        tfPhone.setText("");
                        tfAddress.setText("");
                    }
                } catch (Exception e) {
                    tfPhone.setText("");
                    tfAddress.setText("");
                }
            }
        }.execute();
    }

    /**
     * Lấy tên nhà hàng của phiên hiện tại (gọi đồng bộ vì gọi trước khi dialog pack).
     * Fallback về "—" nếu không có (SUPER_ADMIN không gắn nhà hàng nào).
     */
    private String loadRestaurantName() {
        try {
            var r = DataManager.getInstance().getMyRestaurant();
            return (r != null && r.getName() != null && !r.getName().isBlank())
                   ? r.getName() : "—";
        } catch (Exception e) {
            return "—";
        }
    }

    // ── Tab 1: Save logic ─────────────────────────────────────────────────────

    private void saveProfile() {
        lblInfo.setText(" ");

        final String name    = tfName.getText().trim();
        final String phone   = tfPhone.getText().trim();
        final String address = tfAddress.getText().trim();

        if (name.isEmpty()) {
            showInfo(lblInfo, "❌ Họ và tên không được để trống.", UIConstants.DANGER);
            return;
        }

        btnSave.setEnabled(false);
        btnSave.setText("Đang lưu...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                long uid = AppSession.getInstance().getUserId();
                DataManager.getInstance().updateOwnProfile(uid, name, phone, address);
                // Cập nhật lại AppSession.userName nếu name thay đổi
                AppSession.getInstance().login(uid, name,
                    AppSession.getInstance().getUserEmail(),
                    AppSession.getInstance().getUserRole(),
                    AppSession.getInstance().getRestaurantId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    showInfo(lblInfo, "✅ Đã lưu thành công", UIConstants.SUCCESS);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    showInfo(lblInfo,
                        "❌ " + (cause != null ? cause.getMessage() : ex.getMessage()),
                        UIConstants.DANGER);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showInfo(lblInfo, "❌ Thao tác bị gián đoạn.", UIConstants.DANGER);
                } finally {
                    btnSave.setEnabled(true);
                    btnSave.setText("💾 Lưu thay đổi");
                }
            }
        }.execute();
    }

    // ── Tab 2: Change-password logic ──────────────────────────────────────────

    private void changePassword() {
        lblPwInfo.setText(" ");

        final String oldPw     = new String(pfOldPw.getPassword());
        final String newPw     = new String(pfNewPw.getPassword());
        final String confirmPw = new String(pfConfirmPw.getPassword());

        // Validation
        if (oldPw.isEmpty() || newPw.isEmpty() || confirmPw.isEmpty()) {
            showInfo(lblPwInfo, "❌ Vui lòng điền đầy đủ tất cả các trường.", UIConstants.DANGER);
            return;
        }
        if (newPw.length() < 6) {
            showInfo(lblPwInfo, "❌ Mật khẩu mới phải có ít nhất 6 ký tự.", UIConstants.DANGER);
            return;
        }
        if (!newPw.equals(confirmPw)) {
            showInfo(lblPwInfo, "❌ Xác nhận mật khẩu không khớp.", UIConstants.DANGER);
            return;
        }

        btnChangePw.setEnabled(false);
        btnChangePw.setText("Đang đổi...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                long uid = AppSession.getInstance().getUserId();
                DataManager.getInstance().changeOwnPassword(uid, oldPw, newPw);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    // Thu hồi toàn bộ refresh token khi đổi mật khẩu (bảo mật)
                    long uid = AppSession.getInstance().getUserId();
                    RefreshTokenService.getInstance().revokeAllForUser(uid);
                    TokenStorage.getInstance().clearSavedToken();
                    showInfo(lblPwInfo, "✅ Đổi mật khẩu thành công! Đã đăng xuất tất cả thiết bị.", UIConstants.SUCCESS);
                    clearPasswordFields();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IllegalArgumentException) {
                        // "Mật khẩu hiện tại không đúng"
                        showInfo(lblPwInfo, "❌ " + cause.getMessage(), UIConstants.DANGER);
                    } else {
                        showInfo(lblPwInfo,
                            "❌ " + (cause != null ? cause.getMessage() : ex.getMessage()),
                            UIConstants.DANGER);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showInfo(lblPwInfo, "❌ Thao tác bị gián đoạn.", UIConstants.DANGER);
                } finally {
                    btnChangePw.setEnabled(true);
                    btnChangePw.setText("🔑 Đổi mật khẩu");
                }
            }
        }.execute();
    }

    // ── Revoke all devices ───────────────────────────────────────────────────

    /**
     * Thu hồi tất cả refresh token của user hiện tại sau khi xác nhận.
     * Gọi {@link RefreshTokenService#revokeAllForUser(long)} trên SwingWorker.
     */
    private void revokeAllDevices() {
        int confirm = javax.swing.JOptionPane.showConfirmDialog(
                this,
                "<html>Bạn sẽ bị đăng xuất khỏi <b>tất cả thiết bị</b> đang ghi nhớ đăng nhập.<br>"
                + "Hành động này không thể hoàn tác. Tiếp tục?</html>",
                "Xác nhận đăng xuất tất cả thiết bị",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);

        if (confirm != javax.swing.JOptionPane.YES_OPTION) return;

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                long uid = AppSession.getInstance().getUserId();
                RefreshTokenService.getInstance().revokeAllForUser(uid);
                TokenStorage.getInstance().clearSavedToken();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    javax.swing.JOptionPane.showMessageDialog(
                            MyProfileDialog.this,
                            "✅ Đã đăng xuất khỏi tất cả thiết bị thành công.",
                            "Thành công",
                            javax.swing.JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    javax.swing.JOptionPane.showMessageDialog(
                            MyProfileDialog.this,
                            "❌ Lỗi: " + ex.getMessage(),
                            "Thất bại",
                            javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Cập nhật status label với text và màu chỉ định. */
    private void showInfo(JLabel lbl, String text, Color color) {
        lbl.setText(text);
        lbl.setForeground(color);
    }

    /** Xóa nội dung 3 password fields sau khi đổi mật khẩu thành công. */
    private void clearPasswordFields() {
        pfOldPw.setText("");
        pfNewPw.setText("");
        pfConfirmPw.setText("");
    }

    /** Null-safe: trả về chuỗi rỗng thay vì null. */
    private String nvl(String s) {
        return s != null ? s : "";
    }

    // ── Field factories ───────────────────────────────────────────────────────

    private JLabel makeLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    /**
     * Tạo JTextField với styling nhất quán.
     *
     * @param readOnly nếu {@code true}: disabled, nền xám nhạt, không thể focus
     */
    private JTextField makeTextField(boolean readOnly) {
        JTextField tf = new JTextField();
        tf.setFont(UIConstants.FONT_BODY);
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        tf.setAlignmentX(Component.LEFT_ALIGNMENT);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));

        if (readOnly) {
            tf.setEnabled(false);
            tf.setBackground(new Color(0xF3F4F6));       // xám nhạt
            tf.setForeground(UIConstants.TEXT_SECONDARY); // chữ xám
            tf.setDisabledTextColor(UIConstants.TEXT_SECONDARY);
        }
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