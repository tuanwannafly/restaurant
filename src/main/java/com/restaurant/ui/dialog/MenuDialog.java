package com.restaurant.ui.dialog;

import com.restaurant.model.MenuItem;
import com.restaurant.ui.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class MenuDialog extends JDialog {
    private Consumer<MenuItem> onSave;
    private MenuItem item;

    private JTextField tfName, tfPrice, tfDesc;
    private JComboBox<String> cbCategory;

    public MenuDialog(Window owner, MenuItem item, Consumer<MenuItem> onSave) {
        super(owner, item == null ? "Thêm món mới" : "Cập nhật món", ModalityType.APPLICATION_MODAL);
        this.item = item;
        this.onSave = onSave;
        buildUI();
        if (item != null) fillData();
        setSize(480, 380);
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Color.WHITE);

        JLabel title = new JLabel(item == null ? "Thêm món mới" : "Cập nhật thông tin món");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);
        title.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(0, 24, 12, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 6, 7, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        tfName = field();
        cbCategory = new JComboBox<>(new String[]{"Hải sản", "Thịt", "Cơm", "Phở", "Đồ uống", "Khác"});
        cbCategory.setFont(UIConstants.FONT_BODY);
        tfPrice = field();
        tfDesc = field();

        addRow(form, gbc, 0, "Tên món:", tfName);
        addRow(form, gbc, 1, "Loại:", cbCategory);
        addRow(form, gbc, 2, "Giá (đ):", tfPrice);
        addRow(form, gbc, 3, "Mô tả:", tfDesc);

        root.add(form, BorderLayout.CENTER);

        // Buttons
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnBar.setBackground(Color.WHITE);
        btnBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR));

        RoundedButton btnCancel = RoundedButton.outline("Hủy");
        btnCancel.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnCancel.addActionListener(e -> dispose());

        RoundedButton btnSave = new RoundedButton(item == null ? "Thêm món" : "Lưu");
        btnSave.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnSave.addActionListener(e -> save());

        btnBar.add(btnCancel);
        btnBar.add(btnSave);
        root.add(btnBar, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void fillData() {
        tfName.setText(item.getName());
        cbCategory.setSelectedItem(item.getCategory());
        tfPrice.setText(String.valueOf((long) item.getPrice()));
        tfDesc.setText(item.getDescription());
    }

    private void save() {
        String name = tfName.getText().trim();
        String cat = (String) cbCategory.getSelectedItem();
        String priceStr = tfPrice.getText().trim();
        String desc = tfDesc.getText().trim();

        if (name.isEmpty() || priceStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double price;
        try { price = Double.parseDouble(priceStr); }
        catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Giá không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        MenuItem saved = item == null
            ? new MenuItem("", name, cat, price, desc)
            : new MenuItem(item.getId(), name, cat, price, desc);

        onSave.accept(saved);
        dispose();
    }

    private void addRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent comp) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(100, 32));
        form.add(lbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        comp.setPreferredSize(new Dimension(290, 34));
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
