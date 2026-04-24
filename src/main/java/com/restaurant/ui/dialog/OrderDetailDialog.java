package com.restaurant.ui.dialog;

import com.restaurant.model.Order;
import com.restaurant.ui.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

public class OrderDetailDialog extends JDialog {

    public OrderDetailDialog(Window owner, Order order) {
        super(owner, "Chi tiết đơn hàng", ModalityType.APPLICATION_MODAL);
        setSize(560, 500);
        setLocationRelativeTo(owner);
        setResizable(false);
        buildUI(order);
    }

    private void buildUI(Order order) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Color.WHITE);

        // Title
        JLabel title = new JLabel("Xem chi tiết đơn hàng");
        title.setFont(UIConstants.FONT_TITLE);
        title.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        root.add(title, BorderLayout.NORTH);

        // Content
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(0, 24, 12, 24));

        // Info rows
        content.add(infoRow("ID:", order.getId()));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("Bàn:", order.getTableName()));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("Trạng thái:", order.getStatusDisplay()));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("Thời gian:", order.getCreatedTime()));
        content.add(Box.createVerticalStrut(16));

        // Items table label
        JLabel itemsLabel = new JLabel("Danh sách món:");
        itemsLabel.setFont(UIConstants.FONT_BOLD);
        itemsLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(itemsLabel);
        content.add(Box.createVerticalStrut(8));

        // Items table
        String[] cols = {"Tên món", "Số lượng", "Đơn giá", "Thành tiền"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Order.OrderItem oi : order.getItems()) {
            model.addRow(new Object[]{
                oi.getMenuItemName(),
                oi.getQuantity(),
                nf.format((long) oi.getUnitPrice()) + " đ",
                nf.format((long) oi.getSubtotal()) + " đ"
            });
        }
        StyledTable itemTable = new StyledTable(model);
        itemTable.setPreferredScrollableViewportSize(new Dimension(500, 130));
        JScrollPane scroll = StyledTable.wrap(itemTable);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        content.add(scroll);

        content.add(Box.createVerticalStrut(12));

        // Total
        JPanel totalRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        totalRow.setBackground(Color.WHITE);
        totalRow.setAlignmentX(LEFT_ALIGNMENT);
        JLabel totalLbl = new JLabel("Tổng tiền: ");
        totalLbl.setFont(UIConstants.FONT_BOLD);
        JLabel totalVal = new JLabel(nf.format((long) order.getTotalAmount()) + " đ");
        totalVal.setFont(UIConstants.FONT_TITLE);
        totalVal.setForeground(UIConstants.SUCCESS);
        totalRow.add(totalLbl);
        totalRow.add(totalVal);
        content.add(totalRow);

        JScrollPane contentScroll = new JScrollPane(content);
        contentScroll.setBorder(BorderFactory.createEmptyBorder());
        contentScroll.getViewport().setBackground(Color.WHITE);
        root.add(contentScroll, BorderLayout.CENTER);

        // Bottom
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnBar.setBackground(Color.WHITE);
        btnBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR));
        RoundedButton btn = RoundedButton.outline("Đóng");
        btn.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btn.addActionListener(e -> dispose());
        btnBar.add(btn);
        root.add(btnBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel infoRow(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBackground(Color.WHITE);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(100, 32));
        row.add(lbl, BorderLayout.WEST);

        JPanel box = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),20,20);
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,20,20);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
        JLabel val = new JLabel(value);
        val.setFont(UIConstants.FONT_BODY);
        box.add(val, BorderLayout.WEST);
        row.add(box, BorderLayout.CENTER);
        return row;
    }
}
