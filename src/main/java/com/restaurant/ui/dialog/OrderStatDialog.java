package com.restaurant.ui.dialog;

import com.restaurant.data.DataManager;
import com.restaurant.model.Order;
import com.restaurant.ui.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.util.*;

public class OrderStatDialog extends JDialog {

    public OrderStatDialog(Window owner) {
        super(owner, "Thống kê đơn hàng", ModalityType.APPLICATION_MODAL);
        setSize(580, 420);
        setLocationRelativeTo(owner);
        setResizable(false);
        buildUI();
    }

    private void buildUI() {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        DataManager dm = DataManager.getInstance();
        java.util.List<Order> orders = dm.getOrders();

        long dps  = orders.stream().filter(o -> o.getStatus() == Order.Status.DANG_PHUC_VU).count();
        long ht   = orders.stream().filter(o -> o.getStatus() == Order.Status.HOAN_THANH).count();
        long huy  = orders.stream().filter(o -> o.getStatus() == Order.Status.DA_HUY).count();
        double rev = orders.stream()
            .filter(o -> o.getStatus() == Order.Status.HOAN_THANH)
            .mapToDouble(Order::getTotalAmount).sum();

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 0, 24));

        JLabel title = new JLabel("Thống kê đơn hàng");
        title.setFont(UIConstants.FONT_TITLE);
        root.add(title, BorderLayout.NORTH);

        // Summary cards
        JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0));
        cards.setBackground(Color.WHITE);
        cards.add(statCard("Đang phục vụ", String.valueOf(dps), UIConstants.WARNING));
        cards.add(statCard("Hoàn thành", String.valueOf(ht), UIConstants.SUCCESS));
        cards.add(statCard("Đã hủy", String.valueOf(huy), UIConstants.DANGER));
        cards.add(statCard("Doanh thu", nf.format((long)rev) + " đ", UIConstants.PRIMARY));

        // Orders table
        String[] cols = {"ID", "Bàn", "Tổng tiền", "Trạng thái", "Thời gian"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Order o : orders) {
            model.addRow(new Object[]{
                o.getId(), o.getTableName(),
                nf.format((long) o.getTotalAmount()) + " đ",
                o.getStatusDisplay(),
                o.getCreatedTime()
            });
        }
        StyledTable table = new StyledTable(model);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setBackground(Color.WHITE);
        center.add(cards, BorderLayout.NORTH);
        center.add(StyledTable.wrap(table), BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnBar.setBackground(Color.WHITE);
        RoundedButton btn = RoundedButton.outline("Đóng");
        btn.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btn.addActionListener(e -> dispose());
        btnBar.add(btn);
        root.add(btnBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel statCard(String label, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout(0, 6)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 20));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 80));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel valLbl = new JLabel(value);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 20));
        valLbl.setForeground(color);
        valLbl.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel nameLbl = new JLabel(label);
        nameLbl.setFont(UIConstants.FONT_SMALL);
        nameLbl.setForeground(UIConstants.TEXT_SECONDARY);
        nameLbl.setHorizontalAlignment(SwingConstants.CENTER);

        card.add(valLbl, BorderLayout.CENTER);
        card.add(nameLbl, BorderLayout.SOUTH);
        return card;
    }
}
