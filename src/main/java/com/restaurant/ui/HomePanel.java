package com.restaurant.ui;

import com.restaurant.data.DataManager;
import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

public class HomePanel extends JPanel {

    public HomePanel() {
        setBackground(UIConstants.BG_PAGE);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(32, 60, 32, 60));
        refresh();
    }

    public void refresh() {
        removeAll();
        DataManager dm = DataManager.getInstance();
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // Title
        JLabel title = new JLabel("Tổng quan");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);
        title.setAlignmentX(CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(20));

        // Stats section
        JLabel statsLabel = new JLabel("Thống kê nhanh (hôm nay)");
        statsLabel.setFont(UIConstants.FONT_BODY);
        statsLabel.setForeground(UIConstants.TEXT_SECONDARY);
        content.add(statsLabel);
        content.add(Box.createVerticalStrut(10));

        // Stats cards
        JPanel statsPanel = new JPanel(new GridBagLayout());
        statsPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 6, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        addStatRow(statsPanel, gbc, 0, "Doanh thu:", nf.format((long) dm.getTodayRevenue()) + " đ", UIConstants.SUCCESS);
        addStatRow(statsPanel, gbc, 1, "Số món đã bán:", String.valueOf(dm.getTotalMenuItemsSold()), UIConstants.PRIMARY);
        addStatRow(statsPanel, gbc, 2, "Số đơn hàng:", String.valueOf(dm.getTodayOrderCount()), UIConstants.PRIMARY);
        addStatRow(statsPanel, gbc, 3, "Bàn đang phục vụ:", String.valueOf(dm.getServingTableCount()), UIConstants.WARNING);

        content.add(statsPanel);
        content.add(Box.createVerticalStrut(28));

        // Recent activity
        JLabel recentLabel = new JLabel("Hoạt động gần đây:");
        recentLabel.setFont(UIConstants.FONT_BODY);
        recentLabel.setForeground(UIConstants.TEXT_SECONDARY);
        content.add(recentLabel);
        content.add(Box.createVerticalStrut(10));

        String[] activities = {"Thêm bàn", "Thêm nhân viên", "Cập nhật menu", "Tạo đơn hàng mới"};
        for (String act : activities) {
            content.add(buildActivityRow(act));
            content.add(Box.createVerticalStrut(6));
        }

        add(content, BorderLayout.NORTH);
        revalidate();
        repaint();
    }

    private void addStatRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value, Color valueColor) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        lbl.setPreferredSize(new Dimension(160, 36));
        panel.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        JPanel valueBox = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.dispose();
            }
        };
        valueBox.setOpaque(false);
        valueBox.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        valueBox.setPreferredSize(new Dimension(0, 40));
        JLabel valLbl = new JLabel(value);
        valLbl.setFont(UIConstants.FONT_BOLD);
        valLbl.setForeground(valueColor);
        valueBox.add(valLbl, BorderLayout.WEST);
        panel.add(valueBox, gbc);
    }

    private JPanel buildActivityRow(String text) {
        JPanel row = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.dispose();
            }
        };
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(320, 38));
        row.setMaximumSize(new Dimension(360, 38));
        row.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        JLabel lbl = new JLabel(text);
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        row.add(lbl, BorderLayout.WEST);
        return row;
    }
}
