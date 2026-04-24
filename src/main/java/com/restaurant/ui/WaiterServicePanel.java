package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.AbstractBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.KitchenDAO.KitchenTicket;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.Order;
import com.restaurant.model.TableItem;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

/**
 * Màn hình phục vụ bàn dành cho role WAITER.
 * <p>
 * Gồm 2 tab:
 * <ol>
 *   <li><b>Cần giao</b> – hiển thị các lượt bàn có TẤT CẢ món ở trạng thái READY,
 *       cho phép chuyển sang DELIVERING / DELIVERED.</li>
 *   <li><b>Bàn cần dọn</b> – hiển thị bàn DIRTY / CLEANING, cho phép dọn xong
 *       và mở lại bàn (→ RANH / AVAILABLE).</li>
 * </ol>
 * Auto-refresh mỗi 5 giây. Timer chỉ chạy khi panel đang hiển thị
 * (kiểm soát qua {@link AncestorListener}).
 * <p>
 * RBAC: yêu cầu {@link Permission#VIEW_WAITER_SERVICE}.
 */
public class WaiterServicePanel extends JPanel {

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final int   REFRESH_MS    = 5_000;
    private static final int   CARD_MIN_W    = 260;
    private static final int   CARD_PAD      = 14;
    private static final int   CARD_GAP      = 14;

    // Delivery-tab badge colours
    private static final Color BADGE_READY_BG      = new Color(0xD1FAE5);
    private static final Color BADGE_READY_FG      = new Color(0x065F46);
    private static final Color BADGE_DELIV_BG      = new Color(0xDBEAFE);
    private static final Color BADGE_DELIV_FG      = new Color(0x1E40AF);

    // Dirty-tab status colours
    private static final Color DIRTY_BG     = new Color(0xFEF3C7);
    private static final Color DIRTY_FG     = new Color(0x92400E);
    private static final Color CLEANING_BG  = new Color(0xFCE7F3);
    private static final Color CLEANING_FG  = new Color(0x9D174D);

    // ─── DAOs ─────────────────────────────────────────────────────────────────

    private final KitchenDAO kitchenDAO = new KitchenDAO();
    private final TableDAO   tableDAO   = new TableDAO();

    // ─── UI refs ──────────────────────────────────────────────────────────────

    private JPanel      deliveryCardsPanel;
    private JPanel      dirtyTablePanel;
    private JTabbedPane tabbedPane;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public WaiterServicePanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BG_PAGE);

        // RBAC guard
        if (!AppSession.getInstance().hasPermission(Permission.VIEW_WAITER_SERVICE)) {
            JLabel denied = new JLabel("Không có quyền truy cập", SwingConstants.CENTER);
            denied.setFont(UIConstants.FONT_TITLE);
            denied.setForeground(UIConstants.TEXT_SECONDARY);
            add(denied, BorderLayout.CENTER);
            return;
        }

        buildUI();
        setupAncestorListener();
    }

    // ─── UI construction ──────────────────────────────────────────────────────

    private void buildUI() {
        // ── Page header ──
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BG_WHITE);
        header.setPreferredSize(new Dimension(0, 52));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 24, 0, 24)));

        JLabel title = new JLabel("🛎  Phục vụ bàn");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // ── Tabbed pane ──
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(UIConstants.FONT_BOLD);
        tabbedPane.setBackground(UIConstants.BG_PAGE);

        tabbedPane.addTab("🚚  Cần giao", buildDeliveryTab());
        tabbedPane.addTab("🧹  Bàn cần dọn", buildDirtyTab());

        add(tabbedPane, BorderLayout.CENTER);
    }

    // ── Tab 1: Cần giao ───────────────────────────────────────────────────────

    private JScrollPane buildDeliveryTab() {
        deliveryCardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, CARD_GAP, CARD_GAP));
        deliveryCardsPanel.setBackground(UIConstants.BG_PAGE);
        deliveryCardsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(deliveryCardsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(UIConstants.BG_PAGE);
        return scroll;
    }

    // ── Tab 2: Bàn cần dọn ───────────────────────────────────────────────────

    private JPanel buildDirtyTab() {
        dirtyTablePanel = new JPanel(new BorderLayout());
        dirtyTablePanel.setBackground(UIConstants.BG_PAGE);
        return dirtyTablePanel;
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    /**
     * Public entry-point – được gọi từ MainFrame.navigateTo().
     * Kick-off một SwingWorker để không block EDT.
     */
    public void loadData() {
        refreshAll();
    }

    private void refreshAll() {
        new SwingWorker<Void, Void>() {
            Map<String, List<KitchenTicket>> readyMap;
            List<TableItem>                  dirtyList;

            @Override
            protected Void doInBackground() {
                long rid = AppSession.getInstance().getRestaurantId();
                readyMap  = kitchenDAO.getReadyByTable(rid);
                dirtyList = kitchenDAO.getDirtyTables(rid);
                return null;
            }

            @Override
            protected void done() {
                rebuildDeliveryTab(readyMap);
                rebuildDirtyTab(dirtyList);
            }
        }.execute();
    }

    // ── Rebuild delivery cards ────────────────────────────────────────────────

    private void rebuildDeliveryTab(Map<String, List<KitchenTicket>> readyMap) {
        deliveryCardsPanel.removeAll();

        if (readyMap == null || readyMap.isEmpty()) {
            JLabel empty = new JLabel("Không có món nào cần giao", SwingConstants.CENTER);
            empty.setFont(UIConstants.FONT_BODY);
            empty.setForeground(UIConstants.TEXT_SECONDARY);
            empty.setBorder(BorderFactory.createEmptyBorder(40, 0, 0, 0));
            deliveryCardsPanel.add(empty);
        } else {
            for (Map.Entry<String, List<KitchenTicket>> entry : readyMap.entrySet()) {
                deliveryCardsPanel.add(buildDeliveryCard(entry.getValue()));
            }
        }

        deliveryCardsPanel.revalidate();
        deliveryCardsPanel.repaint();
    }

    private JPanel buildDeliveryCard(List<KitchenTicket> items) {
        KitchenTicket first = items.get(0);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(UIConstants.CORNER_RADIUS, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(CARD_PAD, CARD_PAD, CARD_PAD, CARD_PAD)));
        card.setPreferredSize(new Dimension(CARD_MIN_W, calcDeliveryCardHeight(items.size())));
        card.setMinimumSize(new Dimension(CARD_MIN_W, 0));

        // ── Card header ──
        JLabel cardTitle = new JLabel("Bàn " + first.tableName + "  ·  Lượt " + first.roundNumber);
        cardTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        cardTitle.setForeground(UIConstants.TEXT_PRIMARY);
        cardTitle.setAlignmentX(LEFT_ALIGNMENT);
        card.add(cardTitle);
        card.add(Box.createVerticalStrut(10));

        // ── Item rows ──
        boolean allDelivering = items.stream()
                .allMatch(t -> t.itemStatus == Order.OrderItem.ItemStatus.DELIVERING);
        boolean allDelivered  = items.stream()
                .allMatch(t -> t.itemStatus == Order.OrderItem.ItemStatus.DELIVERED);

        for (KitchenTicket t : items) {
            card.add(buildDeliveryItemRow(t));
            card.add(Box.createVerticalStrut(6));
        }

        card.add(Box.createVerticalStrut(4));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(10));

        // ── Buttons ──
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        btnPanel.setOpaque(false);
        btnPanel.setAlignmentX(LEFT_ALIGNMENT);
        btnPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIConstants.BTN_HEIGHT));

        // "Đang mang lên" → DELIVERING
        JButton btnDeliv = makeButton("🚶 Đang mang lên", UIConstants.WARNING, UIConstants.TEXT_WHITE);
        btnDeliv.setEnabled(!allDelivering && !allDelivered);
        btnDeliv.addActionListener(e -> {
            btnDeliv.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    for (KitchenTicket t : items) {
                        if (t.itemStatus == Order.OrderItem.ItemStatus.READY) {
                            kitchenDAO.updateItemStatus(t.itemId,
                                    Order.OrderItem.ItemStatus.DELIVERING);
                        }
                    }
                    return null;
                }
                @Override protected void done() { refreshAll(); }
            }.execute();
        });

        // "Đã giao xong" → DELIVERED
        JButton btnDone = makeButton("✔ Đã giao xong", UIConstants.SUCCESS, UIConstants.TEXT_WHITE);
        btnDone.setEnabled(!allDelivered);
        btnDone.addActionListener(e -> {
            btnDone.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    for (KitchenTicket t : items) {
                        if (t.itemStatus != Order.OrderItem.ItemStatus.DELIVERED) {
                            kitchenDAO.updateItemStatus(t.itemId,
                                    Order.OrderItem.ItemStatus.DELIVERED);
                        }
                    }
                    return null;
                }
                @Override protected void done() { refreshAll(); }
            }.execute();
        });

        btnPanel.add(btnDeliv);
        btnPanel.add(btnDone);
        card.add(btnPanel);
        return card;
    }

    private JPanel buildDeliveryItemRow(KitchenTicket t) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel nameQty = new JLabel(t.itemName + " × " + t.quantity);
        nameQty.setFont(UIConstants.FONT_BODY);
        nameQty.setForeground(UIConstants.TEXT_PRIMARY);

        JLabel badge = makeDeliveryBadge(t.itemStatus);

        row.add(nameQty, BorderLayout.WEST);
        row.add(badge,   BorderLayout.EAST);
        return row;
    }

    private JLabel makeDeliveryBadge(Order.OrderItem.ItemStatus status) {
        String text;
        Color bg, fg;
        if (status == Order.OrderItem.ItemStatus.DELIVERING) {
            text = "Đang mang";
            bg = BADGE_DELIV_BG; fg = BADGE_DELIV_FG;
        } else if (status == Order.OrderItem.ItemStatus.DELIVERED) {
            text = "Đã giao";
            bg = new Color(0xE5E7EB); fg = UIConstants.TEXT_SECONDARY;
        } else {
            text = "Sẵn sàng";
            bg = BADGE_READY_BG; fg = BADGE_READY_FG;
        }
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(UIConstants.FONT_SMALL);
        lbl.setForeground(fg);
        lbl.setOpaque(true);
        lbl.setBackground(bg);
        lbl.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return lbl;
    }

    // ── Rebuild dirty-table list ──────────────────────────────────────────────

    private void rebuildDirtyTab(List<TableItem> dirtyList) {
        dirtyTablePanel.removeAll();

        if (dirtyList == null || dirtyList.isEmpty()) {
            JLabel empty = new JLabel("Không có bàn nào cần dọn", SwingConstants.CENTER);
            empty.setFont(UIConstants.FONT_BODY);
            empty.setForeground(UIConstants.TEXT_SECONDARY);
            dirtyTablePanel.add(empty, BorderLayout.CENTER);
        } else {
            dirtyTablePanel.add(buildDirtyTable(dirtyList), BorderLayout.CENTER);
        }

        dirtyTablePanel.revalidate();
        dirtyTablePanel.repaint();
    }

    /**
     * Xây dựng bảng bàn cần dọn sử dụng JTable thuần (không extends StyledTable
     * vì cần nhúng nút hành động vào cột cuối).
     */
    private JScrollPane buildDirtyTable(List<TableItem> tables) {
        String[] cols = {"Tên bàn", "Sức chứa", "Trạng thái", "Hành động"};
        Object[][] data = new Object[tables.size()][4];
        for (int i = 0; i < tables.size(); i++) {
            TableItem t = tables.get(i);
            data[i][0] = t.getName();
            data[i][1] = t.getCapacity();
            data[i][2] = t.getStatusDisplay();
            data[i][3] = t;          // raw object; renderer/editor extracts it
        }

        JTable table = new JTable(data, cols) {
            @Override public boolean isCellEditable(int row, int col) { return col == 3; }
        };
        table.setFont(UIConstants.FONT_BODY);
        table.setRowHeight(UIConstants.ROW_HEIGHT + 4);
        table.setGridColor(UIConstants.BORDER_COLOR);
        table.setShowGrid(true);
        table.setSelectionBackground(UIConstants.ROW_SELECTED);
        table.getTableHeader().setFont(UIConstants.FONT_HEADER);
        table.getTableHeader().setBackground(UIConstants.HEADER_BG);
        table.getTableHeader().setReorderingAllowed(false);

        // Status column: colour badge
        table.getColumnModel().getColumn(2).setCellRenderer((tbl, val, sel, foc, row, col) -> {
            TableItem item = tables.get(row);
            JLabel lbl = new JLabel(item.getStatusDisplay(), SwingConstants.CENTER);
            lbl.setFont(UIConstants.FONT_SMALL);
            lbl.setOpaque(true);
            if (item.getStatus() == TableItem.Status.DIRTY) {
                lbl.setBackground(DIRTY_BG);
                lbl.setForeground(DIRTY_FG);
            } else {
                lbl.setBackground(CLEANING_BG);
                lbl.setForeground(CLEANING_FG);
            }
            return lbl;
        });

        // Action column: button renderer + editor
        table.getColumnModel().getColumn(3).setCellRenderer(
                new DirtyActionRenderer(tables));
        table.getColumnModel().getColumn(3).setCellEditor(
                new DirtyActionEditor(tables, this::refreshAll));

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        return scroll;
    }

    // ─── AncestorListener — delegate lifecycle to PollManager ─────────────────

    private void setupAncestorListener() {
        addAncestorListener(new AncestorListener() {
            /** Panel được thêm vào container → bắt đầu polling. */
            @Override
            public void ancestorAdded(AncestorEvent e) {
                // Load ngay lập tức khi panel hiển thị, sau đó PollManager tiếp quản.
                loadData();
                PollManager.getInstance().register("waiter", WaiterServicePanel.this::loadData, REFRESH_MS);
            }

            /** Panel bị remove → dừng polling, giải phóng timer. */
            @Override
            public void ancestorRemoved(AncestorEvent e) {
                PollManager.getInstance().unregister("waiter");
            }

            @Override
            public void ancestorMoved(AncestorEvent e) {}
        });
    }

    // ─── Widget helpers ───────────────────────────────────────────────────────

    private JButton makeButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(UIConstants.FONT_BOLD);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, UIConstants.BTN_HEIGHT));
        return btn;
    }

    private JSeparator buildDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(UIConstants.BORDER_COLOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(LEFT_ALIGNMENT);
        return sep;
    }

    private int calcDeliveryCardHeight(int itemCount) {
        // header(28) + gap(10) + items(28+6)*n + divider(21) + buttons(34) + padding*2
        return 28 + 10 + 34 * itemCount + 21 + UIConstants.BTN_HEIGHT + 2 * CARD_PAD;
    }

    // ─── Inner classes ────────────────────────────────────────────────────────

    /**
     * Renderer dùng JButton cho cột Hành động trong bảng Bàn cần dọn.
     */
    private static class DirtyActionRenderer implements javax.swing.table.TableCellRenderer {
        private final List<TableItem> tables;
        DirtyActionRenderer(List<TableItem> tables) { this.tables = tables; }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            TableItem item = tables.get(row);
            JButton btn = new JButton(
                    item.getStatus() == TableItem.Status.DIRTY ? "Bắt đầu dọn" : "Dọn xong");
            btn.setFont(UIConstants.FONT_SMALL);
            btn.setBackground(item.getStatus() == TableItem.Status.DIRTY
                    ? UIConstants.WARNING : UIConstants.SUCCESS);
            btn.setForeground(UIConstants.TEXT_WHITE);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            return btn;
        }
    }

    /**
     * Editor cho cột Hành động – kích hoạt khi người dùng click vào ô.
     */
    private class DirtyActionEditor extends DefaultCellEditor {
        private final List<TableItem> tables;
        private final Runnable        onDone;
        private JButton               btn;
        private TableItem             currentItem;

        DirtyActionEditor(List<TableItem> tables, Runnable onDone) {
            super(new JCheckBox());
            this.tables = tables;
            this.onDone = onDone;
            setClickCountToStart(1);
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable table, Object value, boolean isSelected, int row, int column) {
            currentItem = tables.get(row);

            boolean isDirty = currentItem.getStatus() == TableItem.Status.DIRTY;
            String  label   = isDirty ? "Bắt đầu dọn" : "Dọn xong";
            Color   bg      = isDirty ? UIConstants.WARNING : UIConstants.SUCCESS;

            btn = new JButton(label);
            btn.setFont(UIConstants.FONT_SMALL);
            btn.setBackground(bg);
            btn.setForeground(UIConstants.TEXT_WHITE);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);

            btn.addActionListener(e -> {
                fireEditingStopped();
                TableItem.Status nextStatus = isDirty
                        ? TableItem.Status.CLEANING
                        : TableItem.Status.RANH;

                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        tableDAO.updateStatus(currentItem.getId(), nextStatus);
                        return null;
                    }
                    @Override protected void done() { onDone.run(); }
                }.execute();
            });
            return btn;
        }

        @Override public Object getCellEditorValue() { return ""; }
    }

    // ─── RoundedBorder ────────────────────────────────────────────────────────

    private static class RoundedBorder extends AbstractBorder {
        private final int   radius;
        private final Color color;

        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color  = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }
    }

    // ─── WrapLayout ───────────────────────────────────────────────────────────

    private static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override
        public Dimension preferredLayoutSize(Container target) { return layoutSize(target, true); }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension min = layoutSize(target, false);
            min.width -= getHgap() + 1;
            return min;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

                int hgap = getHgap(), vgap = getVgap();
                Insets insets  = target.getInsets();
                int maxWidth   = targetWidth - (insets.left + insets.right + hgap * 2);
                Dimension dim  = new Dimension(0, 0);
                int rowWidth = 0, rowHeight = 0;

                for (int i = 0; i < target.getComponentCount(); i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                        if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0; rowHeight = 0;
                        }
                        if (rowWidth != 0) rowWidth += hgap;
                        rowWidth  += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }
                addRow(dim, rowWidth, rowHeight);
                dim.width  += insets.left + insets.right + hgap * 2;
                dim.height += insets.top  + insets.bottom + vgap * 2;
                return dim;
            }
        }

        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);
            if (dim.height > 0) dim.height += getVgap();
            dim.height += rowHeight;
        }
    }
}