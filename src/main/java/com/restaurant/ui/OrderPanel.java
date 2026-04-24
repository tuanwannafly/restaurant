package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.restaurant.data.DataManager;
import com.restaurant.model.Order;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;
import com.restaurant.ui.dialog.OrderDetailDialog;
import com.restaurant.ui.dialog.OrderStatDialog;

public class OrderPanel extends JPanel {

    private DefaultTableModel tableModel;
    private StyledTable table;
    private RoundedTextField searchField;
    private List<Order> displayedItems = new ArrayList<>();
    private List<Order> allItems = new ArrayList<>();

    // Phase 3 — permission fields
    private RoundedButton btnAddOrder;
    private boolean canAdd    = false;
    private boolean canDelete = false;

    private static final String[] COLUMNS = {"ID", "Bàn", "Tổng tiền", "Trạng thái", "Thời gian", "Hành động"};

    public OrderPanel() {
        setBackground(UIConstants.BG_PAGE);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(24, 48, 24, 48));
        buildUI();
        loadData();
    }

    private void buildUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        JLabel title = new JLabel("Quản lý đơn hàng");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setOpaque(false);
        btnAddOrder = new RoundedButton("+ Tạo đơn mới");
        btnAddOrder.setPreferredSize(new Dimension(130, UIConstants.BTN_HEIGHT));
        btnAddOrder.addActionListener(e -> openAddOrderDialog());
        // Phase 3 — ẩn theo quyền; sẽ re-evaluate trong applyOrderRoleFilter()
        btnAddOrder.setVisible(AppSession.getInstance().hasPermission(Permission.ADD_ORDER));
        btnPanel.add(btnAddOrder);
        RoundedButton btnStat = new RoundedButton("Xem thống kê", new Color(0x6366F1), new Color(0x4F46E5), Color.WHITE, UIConstants.CORNER_RADIUS);
        btnStat.setPreferredSize(new Dimension(140, UIConstants.BTN_HEIGHT));
        btnStat.addActionListener(e -> new OrderStatDialog(SwingUtilities.getWindowAncestor(this)).setVisible(true));
        btnPanel.add(btnStat);

        topBar.add(title, BorderLayout.WEST);
        topBar.add(btnPanel, BorderLayout.EAST);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        searchField = new RoundedTextField("Tìm kiếm");
        searchField.setPreferredSize(new Dimension(240, 34));
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { applyFilter(); }
        });

        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font("Segoe UI", Font.PLAIN, 16));

        filterBar.add(searchField);
        filterBar.add(searchIcon);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(140);
        table.getColumnModel().getColumn(5).setPreferredWidth(260);
        table.getColumnModel().getColumn(3).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new ActionRenderer());

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (col == 5 && row >= 0) handleAction(e, row);
            }
        });

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(topBar);
        header.add(filterBar);

        add(header, BorderLayout.NORTH);
        add(StyledTable.wrap(table), BorderLayout.CENTER);
    }

    public void loadData() {
        new SwingWorker<List<Order>, Void>() {
            @Override
            protected List<Order> doInBackground() {
                return DataManager.getInstance().getOrders();
            }
            @Override
            protected void done() {
                try {
                    allItems = get();
                    applyOrderRoleFilter();   // Phase 3 — refresh permission flags
                    applyFilter();
                } catch (Exception e) {
                    System.err.println("[OrderPanel] loadData lỗi: " + e.getMessage());
                }
            }
        }.execute();
    }

    // Phase 3 — cập nhật permission flags và ẩn/hiện controls
    private void applyOrderRoleFilter() {
        AppSession session = AppSession.getInstance();
        canAdd    = session.hasPermission(Permission.ADD_ORDER);
        canDelete = session.hasPermission(Permission.DELETE_ORDER);
        btnAddOrder.setVisible(canAdd);
        tableModel.fireTableDataChanged(); // re-render cột action
    }

    private void openAddOrderDialog() {
        if (!AppSession.getInstance().hasPermission(Permission.ADD_ORDER)) {
            JOptionPane.showMessageDialog(this, "Bạn không có quyền thực hiện thao tác này.");
            return;
        }
        // TODO: mở dialog tạo đơn mới khi có OrderCreateDialog
        JOptionPane.showMessageDialog(this, "Chức năng tạo đơn mới đang phát triển.");
    }

    private void applyFilter() {
        String search = searchField.getText().trim().toLowerCase();
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        displayedItems = allItems.stream().filter(o ->
            search.isEmpty()
            || o.getId().toLowerCase().contains(search)
            || o.getTableName().toLowerCase().contains(search)
        ).collect(Collectors.toList());

        tableModel.setRowCount(0);
        for (Order o : displayedItems) {
            tableModel.addRow(new Object[]{
                o.getId(), o.getTableName(),
                nf.format((long) o.getTotalAmount()) + " đ",
                o.getStatusDisplay(),
                o.getCreatedTime(), ""
            });
        }
    }

    private void handleAction(MouseEvent e, int row) {
        Rectangle cellRect = table.getCellRect(row, 5, false);
        int x = e.getX() - cellRect.x;
        Order item = displayedItems.get(row);

        if (x < 60) {
            // Phase 3 — guard xóa
            if (!canDelete) return;
            int confirm = JOptionPane.showConfirmDialog(this,
                "Xóa đơn hàng \"" + item.getId() + "\"?", "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        DataManager.getInstance().deleteOrder(item.getId());
                        return null;
                    }
                    @Override protected void done() { loadData(); }
                }.execute();
            }
        } else if (x < 160) {
            String[] statuses = {"Đang phục vụ", "Hoàn thành", "Đã hủy"};
            String chosen = (String) JOptionPane.showInputDialog(this,
                "Cập nhật trạng thái:", "Cập nhật", JOptionPane.PLAIN_MESSAGE,
                null, statuses, item.getStatusDisplay());
            if (chosen != null) {
                Order.Status newStatus = chosen.equals("Đang phục vụ") ? Order.Status.DANG_PHUC_VU
                    : chosen.equals("Hoàn thành") ? Order.Status.HOAN_THANH : Order.Status.DA_HUY;
                item.setStatus(newStatus);
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        DataManager.getInstance().updateOrder(item);
                        return null;
                    }
                    @Override protected void done() { loadData(); }
                }.execute();
            }
        } else {
            new OrderDetailDialog(SwingUtilities.getWindowAncestor(this), item).setVisible(true);
        }
    }

    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            String s = value == null ? "" : value.toString();
            switch (s) {
                case "Đang phục vụ": lbl.setForeground(UIConstants.WARNING); break;
                case "Hoàn thành":   lbl.setForeground(UIConstants.SUCCESS); break;
                case "Đã hủy":       lbl.setForeground(UIConstants.DANGER); break;
                default: lbl.setForeground(UIConstants.TEXT_PRIMARY);
            }
            lbl.setFont(UIConstants.FONT_BOLD);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            return lbl;
        }
    }

    /** Non-static inner class để đọc canDelete từ outer panel. */
    private class ActionRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            panel.setBackground(isSelected ? UIConstants.ROW_SELECTED : (row % 2 == 0 ? Color.WHITE : new Color(0xF9FAFB)));
            if (canDelete) {
                panel.add(styledLabel("🗑 Xóa", UIConstants.DANGER));
                panel.add(sep());
            }
            panel.add(styledLabel("✏ Cập nhật", UIConstants.PRIMARY));
            panel.add(sep());
            panel.add(styledLabel("👁 Xem chi tiết", new Color(0x6366F1)));
            return panel;
        }
        private JLabel styledLabel(String text, Color color) {
            JLabel l = new JLabel(text);
            l.setFont(UIConstants.FONT_SMALL);
            l.setForeground(color);
            l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
            return l;
        }
        private JSeparator sep() {
            JSeparator s = new JSeparator(SwingConstants.VERTICAL);
            s.setPreferredSize(new Dimension(1, 16));
            s.setForeground(UIConstants.BORDER_COLOR);
            return s;
        }
    }
}
