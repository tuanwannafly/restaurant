package com.restaurant.ui.dialog;

import com.restaurant.model.MenuItem;
import com.restaurant.ui.*;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

public class MenuDetailDialog extends JDialog {
    public MenuDetailDialog(Window owner, MenuItem item) {
        super(owner, "Xem chi tiết món", ModalityType.APPLICATION_MODAL);
        setSize(480, 340);
        setLocationRelativeTo(owner);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        JLabel title = new JLabel("Xem chi tiết món");
        title.setFont(UIConstants.FONT_TITLE);
        title.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(0, 24, 24, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 6, 7, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi","VN"));
        addRow(form, gbc, 0, "ID:", item.getId());
        addRow(form, gbc, 1, "Tên món:", item.getName());
        addRow(form, gbc, 2, "Loại:", item.getCategory());
        addRow(form, gbc, 3, "Giá:", nf.format((long) item.getPrice()) + " đ");
        addRow(form, gbc, 4, "Mô tả:", item.getDescription());

        root.add(form, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnBar.setBackground(Color.WHITE);
        btnBar.setBorder(BorderFactory.createMatteBorder(1,0,0,0, UIConstants.BORDER_COLOR));
        RoundedButton btnClose = RoundedButton.outline("Đóng");
        btnClose.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnClose.addActionListener(e -> dispose());
        btnBar.add(btnClose);
        root.add(btnBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void addRow(JPanel p, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(100, 32));
        p.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        JPanel box = roundedBox(value);
        p.add(box, gbc);
    }

    private JPanel roundedBox(String text) {
        JPanel box = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        box.setPreferredSize(new Dimension(290, 34));
        JLabel lbl = new JLabel(text);
        lbl.setFont(UIConstants.FONT_BODY);
        box.add(lbl, BorderLayout.WEST);
        return box;
    }
}
