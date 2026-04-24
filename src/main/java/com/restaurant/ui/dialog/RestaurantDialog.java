package com.restaurant.ui.dialog;

import com.restaurant.model.Restaurant;
import com.restaurant.model.Restaurant.Status;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Dialog modal để thêm mới hoặc chỉnh sửa thông tin nhà hàng.
 *
 * <p>Mở với {@code item == null} → chế độ thêm mới.
 * Mở với {@code item != null} → chế độ cập nhật, form điền sẵn dữ liệu.
 *
 * <p>Khi bấm Lưu, callback {@code onSave} được gọi với đối tượng
 * {@link Restaurant} đã được điền; caller chịu trách nhiệm gọi DAO.
 */
public class RestaurantDialog extends JDialog {

    private final Consumer<Restaurant> onSave;
    private final Restaurant           item;

    private JTextField tfName;
    private JTextField tfAddress;
    private JTextField tfPhone;
    private JTextField tfEmail;
    private JComboBox<String> cbStatus;

    // ── Constructor ───────────────────────────────────────────────────────────

    public RestaurantDialog(Window owner, Restaurant item, Consumer<Restaurant> onSave) {
        super(owner,
              item == null ? "Thêm nhà hàng mới" : "Cập nhật thông tin nhà hàng",
              ModalityType.APPLICATION_MODAL);
        this.item   = item;
        this.onSave = onSave;
        buildUI();
        if (item != null) fillData();
        setSize(520, 380);
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    // ── UI Builder ────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        // ── Tiêu đề ──
        JLabel title = new JLabel(item == null ? "Thêm nhà hàng mới" : "Cập nhật thông tin nhà hàng");
        title.setFont(UIConstants.FONT_TITLE);
        title.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        root.add(title, BorderLayout.NORTH);

        // ── Form ──
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(0, 24, 12, 24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(7, 6, 7, 6);
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

        root.add(form, BorderLayout.CENTER);

        // ── Button bar ──
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
        String name    = tfName.getText().trim();
        String address = tfAddress.getText().trim();
        String phone   = tfPhone.getText().trim();
        String email   = tfEmail.getText().trim();
        String statusStr = (String) cbStatus.getSelectedItem();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Vui lòng nhập tên nhà hàng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            tfName.requestFocusInWindow();
            return;
        }

        Restaurant r = (item != null) ? item : new Restaurant();
        r.setName(name);
        r.setAddress(address);
        r.setPhone(phone);
        r.setEmail(email);
        r.setStatus(Status.from(statusStr));

        onSave.accept(r);
        dispose();
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private void addRow(JPanel form, GridBagConstraints gbc,
                        int row, String labelText, JComponent comp) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(140, 32));
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

    private String nvl(String s) {
        return s != null ? s : "";
    }
}