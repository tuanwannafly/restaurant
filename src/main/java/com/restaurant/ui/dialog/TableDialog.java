package com.restaurant.ui.dialog;

import com.restaurant.model.TableItem;
import com.restaurant.ui.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class TableDialog extends JDialog {
    private Consumer<TableItem> onSave;
    private TableItem item;
    private JTextField tfName, tfCapacity;
    private JComboBox<String> cbStatus;

    public TableDialog(Window owner, TableItem item, Consumer<TableItem> onSave) {
        super(owner, item == null ? "Thêm bàn mới" : "Cập nhật bàn", ModalityType.APPLICATION_MODAL);
        this.item = item;
        this.onSave = onSave;
        buildUI();
        if (item != null) fillData();
        setSize(440, 310);
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        JLabel title = new JLabel(item == null ? "Thêm bàn mới" : "Cập nhật thông tin bàn");
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

        tfName = field(); tfCapacity = field();
        cbStatus = new JComboBox<>(new String[]{"Rảnh", "Bận", "Đặt trước"});
        cbStatus.setFont(UIConstants.FONT_BODY);

        addRow(form, gbc, 0, "Tên bàn:", tfName);
        addRow(form, gbc, 1, "Sức chứa:", tfCapacity);
        addRow(form, gbc, 2, "Trạng thái:", cbStatus);
        root.add(form, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnBar.setBackground(Color.WHITE);
        btnBar.setBorder(BorderFactory.createMatteBorder(1,0,0,0, UIConstants.BORDER_COLOR));

        RoundedButton btnCancel = RoundedButton.outline("Hủy");
        btnCancel.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnCancel.addActionListener(e -> dispose());

        RoundedButton btnSave = new RoundedButton(item == null ? "Thêm bàn" : "Lưu");
        btnSave.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnSave.addActionListener(e -> save());

        btnBar.add(btnCancel); btnBar.add(btnSave);
        root.add(btnBar, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void fillData() {
        tfName.setText(item.getName());
        tfCapacity.setText(String.valueOf(item.getCapacity()));
        cbStatus.setSelectedItem(item.getStatusDisplay());
    }

    private void save() {
        String name = tfName.getText().trim();
        String capStr = tfCapacity.getText().trim();
        String statusStr = (String) cbStatus.getSelectedItem();
        if (name.isEmpty() || capStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int cap;
        try { cap = Integer.parseInt(capStr); } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Sức chứa không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        TableItem.Status status = "Bận".equals(statusStr) ? TableItem.Status.BAN
            : "Đặt trước".equals(statusStr) ? TableItem.Status.DAT_TRUOC : TableItem.Status.RANH;
        String id = item == null ? "" : item.getId();
        onSave.accept(new TableItem(id, name, cap, status));
        dispose();
    }

    private void addRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent comp) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(100, 32));
        form.add(lbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        comp.setPreferredSize(new Dimension(260, 34));
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
