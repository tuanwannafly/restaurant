package com.restaurant.ui.dialog;

import com.restaurant.model.Employee;
import com.restaurant.ui.*;

import javax.swing.*;
import java.awt.*;

public class EmployeeDetailDialog extends JDialog {

    public EmployeeDetailDialog(Window owner, Employee emp) {
        super(owner, "Xem chi tiết nhân viên", ModalityType.APPLICATION_MODAL);
        setSize(560, 440);
        setLocationRelativeTo(owner);
        setResizable(false);
        buildUI(emp);
    }

    private void buildUI(Employee emp) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        // Sub-header
        JPanel subHeader = new JPanel(new FlowLayout(FlowLayout.LEFT));
        subHeader.setBackground(new Color(0xF9FAFB));
        subHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR));
        JLabel subTitle = new JLabel("Quản lý nhân viên  ›  Xem chi tiết nhân viên");
        subTitle.setFont(UIConstants.FONT_BODY);
        subTitle.setForeground(UIConstants.TEXT_SECONDARY);
        subHeader.add(subTitle);

        JLabel mainTitle = new JLabel("Xem chi tiết nhân viên");
        mainTitle.setFont(UIConstants.FONT_TITLE);
        mainTitle.setBorder(BorderFactory.createEmptyBorder(16, 24, 10, 24));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(Color.WHITE);
        titlePanel.add(subHeader, BorderLayout.NORTH);
        titlePanel.add(mainTitle, BorderLayout.CENTER);
        root.add(titlePanel, BorderLayout.NORTH);

        // Form fields (read-only, rounded boxes like in design)
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(4, 24, 8, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        addRow(form, gbc, 0, "ID:",           emp.getId());
        addRow(form, gbc, 1, "Tên:",           emp.getName());
        addRow(form, gbc, 2, "CCCD:",          emp.getCccd());
        addRow(form, gbc, 3, "SDT:",           emp.getPhone());
        addRow(form, gbc, 4, "Địa chỉ:",      emp.getAddress());
        addRow(form, gbc, 5, "Ngày vào làm:", emp.getStartDate());

        // Vai trò - shows as toggle buttons like in design
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0;
        JLabel roleLbl = new JLabel("Vai trò");
        roleLbl.setFont(UIConstants.FONT_BOLD);
        roleLbl.setPreferredSize(new Dimension(120, 32));
        form.add(roleLbl, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        JPanel rolePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rolePanel.setOpaque(false);
        String[] roles = {"Phục vụ", "Đầu bếp", "Thu ngân"};
        for (String r : roles) {
            JLabel badge = new JLabel(r);
            badge.setFont(UIConstants.FONT_BODY);
            boolean isCurrentRole = r.equalsIgnoreCase(emp.getRoleDisplay());
            badge.setForeground(isCurrentRole ? Color.WHITE : UIConstants.TEXT_PRIMARY);
            badge.setBackground(isCurrentRole ? UIConstants.PRIMARY : Color.WHITE);
            badge.setOpaque(true);
            badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isCurrentRole ? UIConstants.PRIMARY : UIConstants.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
            rolePanel.add(badge);
        }
        form.add(rolePanel, gbc);

        root.add(form, BorderLayout.CENTER);

        // Bottom bar with back button
        JPanel btnBar = new JPanel(new BorderLayout());
        btnBar.setBackground(Color.WHITE);
        btnBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR),
            BorderFactory.createEmptyBorder(10, 24, 10, 24)));

        JButton btnBack = new JButton("← Quay lại");
        btnBack.setFont(UIConstants.FONT_BODY);
        btnBack.setForeground(UIConstants.PRIMARY);
        btnBack.setBorderPainted(false);
        btnBack.setContentAreaFilled(false);
        btnBack.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnBack.addActionListener(e -> dispose());
        btnBar.add(btnBack, BorderLayout.WEST);

        root.add(btnBar, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void addRow(JPanel p, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(120, 32));
        p.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        JPanel box = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
        box.setPreferredSize(new Dimension(340, 34));
        JLabel val = new JLabel(value);
        val.setFont(UIConstants.FONT_BODY);
        box.add(val, BorderLayout.WEST);
        p.add(box, gbc);
    }
}
