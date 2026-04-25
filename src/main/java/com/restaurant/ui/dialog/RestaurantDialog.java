package com.restaurant.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;

import com.restaurant.dao.UserDAO;
import com.restaurant.dao.UserDAO.AdminUser;
import com.restaurant.model.Restaurant;
import com.restaurant.model.Restaurant.Status;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.RoundedTextField;
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

    private static final String STATUS_ACTIVE   = "Hoạt động";
    private static final String STATUS_INACTIVE = "Vô hiệu hóa";

    private final BiConsumer<Restaurant, AdminChoice> onSave;
    private final Restaurant                          item;
    private final boolean                             isNew;

    // Restaurant fields
    private RoundedTextField txtName;
    private RoundedTextField txtOwner;
    private RoundedTextField txtEmail;
    private RoundedTextField txtPhone;
    private RoundedTextField txtAddress;
    private RoundedTextField txtCreatedDate;
    private JComboBox<String> cboStatus;

    // Admin section (chỉ hiển thị khi tạo mới)
    private JRadioButton   rbSkip;
    private JRadioButton   rbExisting;
    private JRadioButton   rbNew;
    private JComboBox<AdminUser> cbAdmins;
    private RoundedTextField tfAdminName;
    private RoundedTextField tfAdminEmail;
    private JPasswordField pfAdminPassword;
    private JPanel         panelExisting;
    private JPanel         panelNew;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Mở dialog với callback đầy đủ (có AdminChoice) — dùng khi thêm mới.
     */
    public RestaurantDialog(Window owner, Restaurant item,
                            BiConsumer<Restaurant, AdminChoice> onSave) {
        super(owner, "Tạo / Cập nhật nhà hàng", ModalityType.APPLICATION_MODAL);
        this.item   = item;
        this.onSave = onSave;
        this.isNew  = (item == null);
        buildUI();
        if (item != null) fillData();
        pack();
        setMinimumSize(new Dimension(600, 0));
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

        // NORTH: tiêu đề căn giữa
        JLabel title = new JLabel("Tạo / Cập nhật nhà hàng", JLabel.CENTER);
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);
        title.setBorder(BorderFactory.createEmptyBorder(20, 48, 12, 48));
        root.add(title, BorderLayout.NORTH);

        // CENTER: scroll chứa form + AdminChoice section
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.add(buildRestaurantForm());
        if (isNew) {
            content.add(Box.createVerticalStrut(16));
            content.add(buildAdminSection());
            content.add(Box.createVerticalStrut(8));
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Color.WHITE);
        root.add(scroll, BorderLayout.CENTER);

        // SOUTH: button bar — Lưu (WEST) + Hủy (EAST)
        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.setBackground(Color.WHITE);
        outerPanel.setBorder(BorderFactory.createEmptyBorder(12, 48, 20, 48));

        RoundedButton btnSave = new RoundedButton("Lưu");
        btnSave.setPreferredSize(new Dimension(80, UIConstants.BTN_HEIGHT));
        btnSave.addActionListener(e -> handleSave());

        RoundedButton btnCancel = new RoundedButton("Hủy");
        btnCancel.setPreferredSize(new Dimension(80, UIConstants.BTN_HEIGHT));
        btnCancel.addActionListener(e -> dispose());

        outerPanel.add(btnSave,   BorderLayout.WEST);
        outerPanel.add(btnCancel, BorderLayout.EAST);
        root.add(outerPanel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ── Restaurant form ───────────────────────────────────────────────────────

    private JPanel buildRestaurantForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(20, 48, 20, 48));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(6, 4, 6, 4);
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.anchor  = GridBagConstraints.WEST;

        txtName        = new RoundedTextField("");
        txtOwner       = new RoundedTextField("");
        txtEmail       = new RoundedTextField("");
        txtPhone       = new RoundedTextField("");
        txtAddress     = new RoundedTextField("");
        txtCreatedDate = new RoundedTextField("");
        txtCreatedDate.setEditable(!isNew);   // read-only khi edit mode

        cboStatus = new JComboBox<>(new String[]{ STATUS_ACTIVE, STATUS_INACTIVE });
        cboStatus.setFont(UIConstants.FONT_BODY);
        cboStatus.setBorder(new RoundedTextField.RoundedBorder(
                UIConstants.CORNER_RADIUS, UIConstants.BORDER_COLOR));

        addRow(form, gbc, 0, "Tên nhà hàng:",  txtName);
        addRow(form, gbc, 1, "Chủ nhà hàng:",  txtOwner);
        addRow(form, gbc, 2, "Email:",          txtEmail);
        addRow(form, gbc, 3, "SĐT:",            txtPhone);
        addRow(form, gbc, 4, "Địa chỉ:",        txtAddress);
        addRow(form, gbc, 5, "Ngày tạo:",       txtCreatedDate);
        addRow(form, gbc, 6, "Trạng thái:",     cboStatus);

        return form;
    }

    // ── Admin section (chỉ hiện khi tạo mới) ─────────────────────────────────

    private JPanel buildAdminSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(Color.WHITE);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

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

        tfAdminName     = new RoundedTextField("");
        tfAdminEmail    = new RoundedTextField("");
        pfAdminPassword = new JPasswordField();
        pfAdminPassword.setFont(UIConstants.FONT_BODY);
        pfAdminPassword.setBorder(new RoundedTextField.RoundedBorder(
                UIConstants.CORNER_RADIUS, UIConstants.BORDER_COLOR));

        addRow(panelNew, gbc, 0, "Họ tên *:",   tfAdminName);
        addRow(panelNew, gbc, 1, "Email *:",     tfAdminEmail);
        addRow(panelNew, gbc, 2, "Mật khẩu *:", pfAdminPassword);

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
        txtName.setText(nvl(item.getName()));
        // txtOwner: requires item.getOwnerName() — add field to Restaurant model if needed
        txtEmail.setText(nvl(item.getEmail()));
        txtPhone.setText(nvl(item.getPhone()));
        txtAddress.setText(nvl(item.getAddress()));
        txtCreatedDate.setText(item.getCreatedAt() != null ? item.getCreatedAt().toString() : "");
        cboStatus.setSelectedItem(item.getStatus() == Status.ACTIVE ? STATUS_ACTIVE : STATUS_INACTIVE);
    }

    private void handleSave() {
        // --- Validate restaurant ---
        String name = txtName.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Vui lòng nhập tên nhà hàng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            txtName.requestFocusInWindow();
            return;
        }

        Restaurant r = (item != null) ? item : new Restaurant();
        r.setName(name);
        r.setEmail(txtEmail.getText().trim());
        r.setPhone(txtPhone.getText().trim());
        r.setAddress(txtAddress.getText().trim());
        r.setStatus(STATUS_ACTIVE.equals(cboStatus.getSelectedItem()) ? Status.ACTIVE : Status.INACTIVE);

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

    /**
     * Thêm một hàng label + field vào form GridBagLayout.
     * Label cố định 180px, RIGHT align, FONT_BODY, TEXT_PRIMARY.
     * Field fill HORIZONTAL với weightx=1.0.
     */
    private void addRow(JPanel form, GridBagConstraints gbc,
                        int row, String labelText, java.awt.Component comp) {
        // Label
        gbc.gridx   = 0;
        gbc.gridy   = row;
        gbc.weightx = 0;
        JLabel lbl = new JLabel(labelText, JLabel.RIGHT);
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        lbl.setPreferredSize(new Dimension(180, 34));
        form.add(lbl, gbc);

        // Field
        gbc.gridx   = 1;
        gbc.weightx = 1.0;
        if (comp instanceof RoundedTextField) {
            ((RoundedTextField) comp).setPreferredSize(new Dimension(300, 34));
        } else {
            comp.setPreferredSize(new Dimension(300, 34));
        }
        form.add(comp, gbc);
    }

    private JRadioButton radio(String text) {
        JRadioButton rb = new JRadioButton(text);
        rb.setFont(UIConstants.FONT_BODY);
        rb.setBackground(Color.WHITE);
        rb.setFocusPainted(false);
        return rb;
    }

    private String nvl(String s) { return s != null ? s : ""; }
}