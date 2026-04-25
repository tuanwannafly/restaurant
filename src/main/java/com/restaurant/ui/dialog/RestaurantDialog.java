package com.restaurant.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;

import com.restaurant.dao.UserDAO;
import com.restaurant.dao.UserDAO.AdminUser;
import com.restaurant.model.Restaurant;
import com.restaurant.model.Restaurant.Status;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.UIConstants;

/**
 * Dialog modal để thêm mới hoặc chỉnh sửa thông tin nhà hàng.
 *
 * <p>Khi thêm mới (item == null), dialog hiển thị thêm phần
 * "Admin quản lý nhà hàng" cho phép SUPER_ADMIN:
 * <ul>
 *   <li>Chọn admin có sẵn từ danh sách (RESTAURANT_ADMIN đang có trong hệ thống)</li>
 *   <li>Tạo admin mới ngay tại chỗ (nhập tên, email, mật khẩu)</li>
 * </ul>
 *
 * <p>Callback {@code onSave} được gọi với {@link Restaurant} và {@link AdminChoice};
 * caller (RestaurantPanel) chịu trách nhiệm thực thi DAO.
 */
public class RestaurantDialog extends JDialog {

    // ── AdminChoice ───────────────────────────────────────────────────────────

    /** Kết quả chọn admin khi thêm nhà hàng mới. */
    public static class AdminChoice {
        public enum Mode { SKIP, EXISTING, NEW }

        public final Mode   mode;
        public final long   existingUserId;   // dùng khi mode == EXISTING
        public final String newName;          // dùng khi mode == NEW
        public final String newEmail;
        public final String newPassword;

        private AdminChoice(Mode mode, long existingUserId,
                            String newName, String newEmail, String newPassword) {
            this.mode           = mode;
            this.existingUserId = existingUserId;
            this.newName        = newName;
            this.newEmail       = newEmail;
            this.newPassword    = newPassword;
        }

        public static AdminChoice skip()                                { return new AdminChoice(Mode.SKIP, 0, null, null, null); }
        public static AdminChoice existing(long uid)                    { return new AdminChoice(Mode.EXISTING, uid, null, null, null); }
        public static AdminChoice create(String n, String e, String p)  { return new AdminChoice(Mode.NEW, 0, n, e, p); }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final BiConsumer<Restaurant, AdminChoice> onSave;
    private final Restaurant                          item;
    private final boolean                             isNew;

    // Restaurant fields
    private JTextField    tfName;
    private JTextField    tfAddress;
    private JTextField    tfPhone;
    private JTextField    tfEmail;
    private JComboBox<String> cbStatus;

    // Admin section (chỉ hiển thị khi tạo mới)
    private JRadioButton  rbSkip;
    private JRadioButton  rbExisting;
    private JRadioButton  rbNew;
    private JComboBox<AdminUser> cbAdmins;
    private JTextField    tfAdminName;
    private JTextField    tfAdminEmail;
    private JPasswordField pfAdminPassword;
    private JPanel        panelExisting;
    private JPanel        panelNew;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Mở dialog với callback đầy đủ (có AdminChoice) — dùng khi thêm mới.
     */
    public RestaurantDialog(Window owner, Restaurant item,
                            BiConsumer<Restaurant, AdminChoice> onSave) {
        super(owner,
              item == null ? "Thêm nhà hàng mới" : "Cập nhật thông tin nhà hàng",
              ModalityType.APPLICATION_MODAL);
        this.item   = item;
        this.onSave = onSave;
        this.isNew  = (item == null);
        buildUI();
        if (item != null) fillData();
        pack();
        setMinimumSize(new Dimension(560, 0));
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    /**
     * Backward-compatible constructor dùng Consumer thường (không có AdminChoice).
     * Dùng cho chế độ chỉnh sửa.
     */
    public RestaurantDialog(Window owner, Restaurant item, Consumer<Restaurant> onSaveLegacy) {
        this(owner, item, (r, a) -> onSaveLegacy.accept(r));
    }

    // ── UI Builder ────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        // Header title
        JLabel title = new JLabel(isNew ? "Thêm nhà hàng mới" : "Cập nhật thông tin nhà hàng");
        title.setFont(UIConstants.FONT_TITLE);
        title.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        root.add(title, BorderLayout.NORTH);

        // Scroll panel chứa tất cả nội dung
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(0, 24, 12, 24));
        content.add(buildRestaurantForm());
        if (isNew) {
            content.add(Box.createVerticalStrut(16));
            content.add(buildAdminSection());
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Color.WHITE);
        root.add(scroll, BorderLayout.CENTER);

        // Button bar
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnBar.setBackground(Color.WHITE);
        btnBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR));

        RoundedButton btnCancel = RoundedButton.outline("Hủy");
        btnCancel.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnCancel.addActionListener(e -> dispose());

        RoundedButton btnSave = new RoundedButton(isNew ? "Thêm" : "Lưu");
        btnSave.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnSave.addActionListener(e -> save());

        btnBar.add(btnCancel);
        btnBar.add(btnSave);
        root.add(btnBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ── Restaurant form ───────────────────────────────────────────────────────

    private JPanel buildRestaurantForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(6, 4, 6, 4);
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.anchor  = GridBagConstraints.WEST;

        tfName    = field();
        tfAddress = field();
        tfPhone   = field();
        tfEmail   = field();
        cbStatus  = new JComboBox<>(new String[]{"ACTIVE", "INACTIVE"});
        cbStatus.setFont(UIConstants.FONT_BODY);

        addRow(form, gbc, 0, "Tên nhà hàng *:", tfName);
        addRow(form, gbc, 1, "Địa chỉ:",         tfAddress);
        addRow(form, gbc, 2, "Số điện thoại:",   tfPhone);
        addRow(form, gbc, 3, "Email:",             tfEmail);
        addRow(form, gbc, 4, "Trạng thái:",        cbStatus);

        return form;
    }

    // ── Admin section (chỉ hiện khi tạo mới) ─────────────────────────────────

    private JPanel buildAdminSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(Color.WHITE);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Tiêu đề phân cách
        TitledBorder border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR),
            "  Admin quản lý nhà hàng  ");
        border.setTitleFont(UIConstants.FONT_BOLD);
        border.setTitleColor(UIConstants.TEXT_PRIMARY);
        section.setBorder(border);

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBackground(Color.WHITE);
        inner.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));

        // Radio buttons
        ButtonGroup grp = new ButtonGroup();
        rbSkip     = radio("Bỏ qua — sẽ gán admin sau");
        rbExisting = radio("Chọn từ danh sách admin có sẵn");
        rbNew      = radio("Tạo admin mới ngay tại đây");
        grp.add(rbSkip);
        grp.add(rbExisting);
        grp.add(rbNew);
        rbSkip.setSelected(true);

        inner.add(rbSkip);
        inner.add(Box.createVerticalStrut(4));
        inner.add(rbExisting);
        inner.add(buildExistingPanel());
        inner.add(Box.createVerticalStrut(4));
        inner.add(rbNew);
        inner.add(buildNewAdminPanel());

        // Radio listener để show/hide sub-panels
        rbSkip.addActionListener(e -> updateAdminPanelVisibility());
        rbExisting.addActionListener(e -> updateAdminPanelVisibility());
        rbNew.addActionListener(e -> updateAdminPanelVisibility());
        updateAdminPanelVisibility();

        section.add(inner, BorderLayout.CENTER);
        return section;
    }

    private JPanel buildExistingPanel() {
        panelExisting = new JPanel(new BorderLayout(8, 0));
        panelExisting.setBackground(Color.WHITE);
        panelExisting.setBorder(BorderFactory.createEmptyBorder(6, 24, 6, 0));

        // Load danh sách admin từ DB
        cbAdmins = new JComboBox<>();
        cbAdmins.setFont(UIConstants.FONT_BODY);
        cbAdmins.setPreferredSize(new Dimension(0, 34));

        JLabel lbl = new JLabel("Chọn admin:");
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(100, 34));

        loadAdminList();

        panelExisting.add(lbl,      BorderLayout.WEST);
        panelExisting.add(cbAdmins, BorderLayout.CENTER);
        panelExisting.setVisible(false);
        return panelExisting;
    }

    private JPanel buildNewAdminPanel() {
        panelNew = new JPanel(new GridBagLayout());
        panelNew.setBackground(Color.WHITE);
        panelNew.setBorder(BorderFactory.createEmptyBorder(6, 24, 0, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(5, 4, 5, 4);
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.anchor  = GridBagConstraints.WEST;

        tfAdminName     = field();
        tfAdminEmail    = field();
        pfAdminPassword = new JPasswordField();
        pfAdminPassword.setFont(UIConstants.FONT_BODY);
        pfAdminPassword.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));

        addRow(panelNew, gbc, 0, "Họ tên *:",     tfAdminName);
        addRow(panelNew, gbc, 1, "Email *:",        tfAdminEmail);
        addRow(panelNew, gbc, 2, "Mật khẩu *:",    pfAdminPassword);

        // Gợi ý: link mở RegisterStaffDialog-style (tạo riêng sau)
        JLabel hint = new JLabel(
            "<html><font color='#6B7280' size='2'>* Tài khoản sẽ có quyền RESTAURANT_ADMIN</font></html>");
        hint.setFont(UIConstants.FONT_SMALL);
        GridBagConstraints hgbc = new GridBagConstraints();
        hgbc.gridx = 1; hgbc.gridy = 3;
        hgbc.fill  = GridBagConstraints.HORIZONTAL;
        hgbc.insets = new Insets(0, 4, 4, 4);
        panelNew.add(hint, hgbc);

        panelNew.setVisible(false);
        return panelNew;
    }

    /** Tải danh sách RESTAURANT_ADMIN vào cbAdmins theo background thread. */
    private void loadAdminList() {
        new SwingWorker<List<AdminUser>, Void>() {
            @Override
            protected List<AdminUser> doInBackground() {
                return new UserDAO().findRestaurantAdmins();
            }

            @Override
            protected void done() {
                try {
                    List<AdminUser> admins = get();
                    cbAdmins.removeAllItems();
                    if (admins.isEmpty()) {
                        cbAdmins.addItem(null);
                        cbAdmins.setRenderer(new DefaultListCellRenderer() {
                            @Override
                            public Component getListCellRendererComponent(JList<?> list, Object value,
                                    int index, boolean isSelected, boolean cellHasFocus) {
                                JLabel l = (JLabel) super.getListCellRendererComponent(
                                        list, value, index, isSelected, cellHasFocus);
                                l.setText("(Chưa có admin nào — hãy tạo mới)");
                                l.setForeground(UIConstants.TEXT_SECONDARY);
                                return l;
                            }
                        });
                        // Tự động chuyển sang "tạo mới"
                        if (rbExisting.isSelected()) {
                            rbNew.setSelected(true);
                            updateAdminPanelVisibility();
                        }
                    } else {
                        for (AdminUser a : admins) cbAdmins.addItem(a);
                    }
                } catch (Exception ex) {
                    System.err.println("[RestaurantDialog] Lỗi load admin: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void updateAdminPanelVisibility() {
        panelExisting.setVisible(rbExisting.isSelected());
        panelNew.setVisible(rbNew.isSelected());
        pack();
    }

    // ── Data helpers ──────────────────────────────────────────────────────────

    private void fillData() {
        if (item == null) return;
        tfName.setText(nvl(item.getName()));
        tfAddress.setText(nvl(item.getAddress()));
        tfPhone.setText(nvl(item.getPhone()));
        tfEmail.setText(nvl(item.getEmail()));
        cbStatus.setSelectedItem(item.getStatus() != null ? item.getStatus().name() : "ACTIVE");
    }

    private void save() {
        // --- Validate restaurant ---
        String name = tfName.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Vui lòng nhập tên nhà hàng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            tfName.requestFocusInWindow();
            return;
        }

        Restaurant r = (item != null) ? item : new Restaurant();
        r.setName(name);
        r.setAddress(tfAddress.getText().trim());
        r.setPhone(tfPhone.getText().trim());
        r.setEmail(tfEmail.getText().trim());
        r.setStatus(Status.from((String) cbStatus.getSelectedItem()));

        // --- Validate admin section (chỉ khi tạo mới) ---
        AdminChoice adminChoice = AdminChoice.skip();

        if (isNew) {
            if (rbExisting.isSelected()) {
                AdminUser selected = (AdminUser) cbAdmins.getSelectedItem();
                if (selected == null) {
                    JOptionPane.showMessageDialog(this,
                        "Vui lòng chọn admin từ danh sách hoặc chuyển sang tạo mới!",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                adminChoice = AdminChoice.existing(selected.getUserId());

            } else if (rbNew.isSelected()) {
                String adminName  = tfAdminName.getText().trim();
                String adminEmail = tfAdminEmail.getText().trim();
                String adminPass  = new String(pfAdminPassword.getPassword()).trim();

                if (adminName.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                        "Vui lòng nhập họ tên admin!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    tfAdminName.requestFocusInWindow();
                    return;
                }
                if (adminEmail.isEmpty() || !adminEmail.contains("@")) {
                    JOptionPane.showMessageDialog(this,
                        "Vui lòng nhập email hợp lệ cho admin!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    tfAdminEmail.requestFocusInWindow();
                    return;
                }
                if (adminPass.length() < 6) {
                    JOptionPane.showMessageDialog(this,
                        "Mật khẩu admin phải có ít nhất 6 ký tự!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    pfAdminPassword.requestFocusInWindow();
                    return;
                }
                adminChoice = AdminChoice.create(adminName, adminEmail, adminPass);
            }
            // rbSkip → adminChoice đã là SKIP
        }

        onSave.accept(r, adminChoice);
        dispose();
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private void addRow(JPanel form, GridBagConstraints gbc,
                        int row, String labelText, JComponent comp) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(130, 32));
        form.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        comp.setPreferredSize(new Dimension(300, 34));
        form.add(comp, gbc);
    }

    private JRadioButton radio(String text) {
        JRadioButton rb = new JRadioButton(text);
        rb.setFont(UIConstants.FONT_BODY);
        rb.setBackground(Color.WHITE);
        rb.setFocusPainted(false);
        return rb;
    }

    private JTextField field() {
        JTextField tf = new JTextField();
        tf.setFont(UIConstants.FONT_BODY);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        return tf;
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
