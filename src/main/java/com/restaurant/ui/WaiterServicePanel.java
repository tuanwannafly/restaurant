package com.restaurant.ui;

import java.awt.BasicStroke;
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
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.AbstractBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.Order;
import com.restaurant.model.TableItem;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

/**
 * Màn hình phục vụ bàn dành cho role WAITER — Phase 4C.
 * <p>
 * Gồm 3 tab:
 * <ol>
 *   <li><b>Phục vụ bàn</b> – danh sách lượt bàn có món READY, cho phép
 *       chuyển sang DELIVERING / DELIVERED.</li>
 *   <li><b>Dọn bàn</b>     – danh sách bàn DIRTY / CLEANING, cho phép
 *       chuyển sang CLEANING / RANH.</li>
 *   <li><b>Đã hủy</b>      – placeholder, Phase tiếp theo.</li>
 * </ol>
 * Auto-refresh mỗi 5 giây. Timer chỉ chạy khi panel đang hiển thị
 * (kiểm soát qua {@link AncestorListener}).
 * <p>
 * RBAC: yêu cầu {@link Permission#VIEW_WAITER_SERVICE}.
 */
public class WaiterServicePanel extends JPanel {

    // ─── DAOs ─────────────────────────────────────────────────────────────────

    private final KitchenDAO kitchenDAO = new KitchenDAO();
    private final TableDAO   tableDAO   = new TableDAO();

    // ─── Fields ───────────────────────────────────────────────────────────────

    private JPanel deliveryCardsPanel;
    private int    lastReadyCount = 0;

    private JPanel cleanTablePanel;  // Phase 4C

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

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTabs(),   BorderLayout.CENTER);
        setupAncestorListener();
    }

    // ─── Header ───────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BG_WHITE);
        header.setPreferredSize(new Dimension(0, 56));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 24, 0, 24)));

        // ── LEFT ──
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel iconLabel = new JLabel("⛁");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        iconLabel.setForeground(UIConstants.PRIMARY);

        JLabel appName = new JLabel("SmartRestaurant");
        appName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        appName.setForeground(UIConstants.PRIMARY);

        JLabel roleBadge = new RoleBadge("Phục vụ");

        left.add(iconLabel);
        left.add(appName);
        left.add(roleBadge);
        header.add(left, BorderLayout.WEST);

        // ── RIGHT ──
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        JLabel globeIcon = new JLabel("🌐");
        globeIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));

        String restaurantName = "";
        try {
            restaurantName = com.restaurant.data.DataManager.getInstance()
                    .getMyRestaurant().getName();
        } catch (Exception ignored) {}

        JLabel restaurantLabel = new JLabel(restaurantName);
        restaurantLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        restaurantLabel.setForeground(UIConstants.PRIMARY);

        JButton endShiftBtn = new RoundedOutlineButton("Kết ca");
        endShiftBtn.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Xác nhận kết ca?",
                    "Kết ca",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                if (w != null) w.dispose();
            }
        });

        right.add(globeIcon);
        right.add(restaurantLabel);
        right.add(endShiftBtn);
        header.add(right, BorderLayout.EAST);

        return header;
    }

    // ─── Tabs ─────────────────────────────────────────────────────────────────

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(UIConstants.FONT_BOLD);
        tabs.setBackground(UIConstants.BG_PAGE);

        tabs.addTab("🚚  Phục vụ bàn", buildDeliveryTab());
        tabs.addTab("🧹  Dọn bàn",     buildCleanTab());          // Phase 4C
        tabs.addTab("🚫  Đã hủy",      buildCancelledPlaceholder());

        return tabs;
    }

    // ─── Tab 1: Phục vụ bàn ──────────────────────────────────────────────────

    private JPanel buildDeliveryTab() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(UIConstants.BG_PAGE);

        deliveryCardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 14, 14));
        deliveryCardsPanel.setBackground(UIConstants.BG_PAGE);
        deliveryCardsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(deliveryCardsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    // ─── Tab 2: Dọn bàn (Phase 4C) ───────────────────────────────────────────

    private JPanel buildCleanTab() {
        cleanTablePanel = new JPanel(new BorderLayout());
        cleanTablePanel.setBackground(UIConstants.BG_PAGE);
        return cleanTablePanel;
    }

    // ─── Tab 3: Placeholder ───────────────────────────────────────────────────

    private JPanel buildCancelledPlaceholder() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(UIConstants.BG_PAGE);
        JLabel l = new JLabel("Đã hủy – đang xây dựng", SwingConstants.CENTER);
        l.setFont(UIConstants.FONT_BODY);
        l.setForeground(UIConstants.TEXT_SECONDARY);
        p.add(l);
        return p;
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    /**
     * Public entry-point – được gọi từ MainFrame.navigateTo() và PollManager.
     * Kick-off SwingWorker để không block EDT.
     */
    public void loadData() {
        new SwingWorker<Void, Void>() {
            Map<String, List<KitchenDAO.KitchenTicket>> readyMap;
            List<TableItem>                              dirtyList;

            @Override
            protected Void doInBackground() {
                long rid = AppSession.getInstance().getRestaurantId();
                readyMap  = kitchenDAO.getReadyByTable(rid);
                dirtyList = kitchenDAO.getDirtyTables(rid);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    rebuildDeliveryCards(readyMap);
                    rebuildCleanCards(dirtyList);
                } catch (Exception ex) {
                    System.err.println("[WaiterServicePanel] loadData lỗi: "
                            + ex.getMessage());
                }
            }
        }.execute();
    }

    // ─── Rebuild delivery cards (Tab 1) ──────────────────────────────────────

    private void rebuildDeliveryCards(
            Map<String, List<KitchenDAO.KitchenTicket>> map) {

        deliveryCardsPanel.removeAll();

        if (map == null || map.isEmpty()) {
            deliveryCardsPanel.setLayout(new BorderLayout());
            deliveryCardsPanel.add(
                    buildEmptyState("🛎", "Không có bàn nào cần phục vụ"),
                    BorderLayout.CENTER);
            deliveryCardsPanel.revalidate();
            deliveryCardsPanel.repaint();
            return;
        }

        deliveryCardsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 14, 14));

        int currentCount = map.size();
        if (currentCount > lastReadyCount && lastReadyCount >= 0) {
            int diff = currentCount - lastReadyCount;
            if (diff > 0) {
                ToastNotification.show(
                        this,
                        "Có " + diff + " lượt bàn mới cần phục vụ!",
                        ToastNotification.Type.INFO);
            }
        }
        lastReadyCount = currentCount;

        for (Map.Entry<String, List<KitchenDAO.KitchenTicket>> entry : map.entrySet()) {
            deliveryCardsPanel.add(buildDeliveryCard(entry.getValue()));
        }

        deliveryCardsPanel.revalidate();
        deliveryCardsPanel.repaint();
    }

    // ─── Rebuild clean table (Tab 2) ─────────────────────────────────────────

    private void rebuildCleanCards(List<TableItem> tables) {
        cleanTablePanel.removeAll();

        if (tables == null || tables.isEmpty()) {
            cleanTablePanel.add(
                    buildEmptyState("🧹", "Không có bàn nào cần dọn"),
                    BorderLayout.CENTER);
        } else {
            cleanTablePanel.add(buildCleanTable(tables), BorderLayout.CENTER);
        }

        cleanTablePanel.revalidate();
        cleanTablePanel.repaint();
    }

    private JScrollPane buildCleanTable(List<TableItem> tables) {
        String[] cols = {"Tên bàn", "Sức chứa", "Trạng thái", "Hành động"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return c == 3; }
        };
        for (TableItem t : tables) {
            model.addRow(new Object[]{
                    t.getName(), t.getCapacity(), t.getStatusDisplay(), t
            });
        }

        JTable table = new JTable(model);
        table.setFont(UIConstants.FONT_BODY);
        table.setRowHeight(UIConstants.ROW_HEIGHT + 4);
        table.setShowGrid(true);
        table.setGridColor(UIConstants.BORDER_COLOR);
        table.setSelectionBackground(UIConstants.ROW_SELECTED);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(UIConstants.FONT_HEADER);
        table.getTableHeader().setBackground(UIConstants.HEADER_BG);
        table.getTableHeader().setForeground(UIConstants.TEXT_PRIMARY);
        table.getTableHeader().setReorderingAllowed(false);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);

        // Status renderer (cột 2)
        table.getColumnModel().getColumn(2).setCellRenderer(
                (tbl, val, sel, foc, row, col) -> {
                    TableItem item = tables.get(row);
                    JLabel lbl = new JLabel(item.getStatusDisplay(), SwingConstants.CENTER);
                    lbl.setFont(UIConstants.FONT_SMALL);
                    lbl.setOpaque(true);
                    if (item.getStatus() == TableItem.Status.DIRTY) {
                        lbl.setBackground(new Color(0xFEF3C7));
                        lbl.setForeground(new Color(0x92400E));
                    } else {
                        lbl.setBackground(new Color(0xFCE7F3));
                        lbl.setForeground(new Color(0x9D174D));
                    }
                    return lbl;
                });

        // Action renderer & editor (cột 3)
        table.getColumnModel().getColumn(3).setCellRenderer(new CleanActionRenderer(tables));
        table.getColumnModel().getColumn(3).setCellEditor(
                new CleanActionEditor(tables, tableDAO, this::loadData));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        return scroll;
    }

    // ─── Card builder (Tab 1) ────────────────────────────────────────────────

    private JPanel buildDeliveryCard(List<KitchenDAO.KitchenTicket> tickets) {
        KitchenDAO.KitchenTicket first = tickets.get(0);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(8, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        card.setPreferredSize(new Dimension(260, 46 + tickets.size() * 34 + 58));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel lblTitle = new JLabel(
                "Bàn " + first.tableName + "  ·  Lượt " + first.roundNumber);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(UIConstants.TEXT_PRIMARY);
        lblTitle.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblTitle);
        card.add(Box.createVerticalStrut(10));

        boolean allDelivered = tickets.stream()
                .allMatch(t -> t.itemStatus == Order.OrderItem.ItemStatus.DELIVERED);
        boolean allDelivering = tickets.stream()
                .allMatch(t -> t.itemStatus == Order.OrderItem.ItemStatus.DELIVERING
                            || t.itemStatus == Order.OrderItem.ItemStatus.DELIVERED);

        for (KitchenDAO.KitchenTicket t : tickets) {
            card.add(buildItemRow(t));
            card.add(Box.createVerticalStrut(6));
        }

        JSeparator sep = new JSeparator();
        sep.setForeground(UIConstants.BORDER_COLOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(LEFT_ALIGNMENT);
        card.add(sep);
        card.add(Box.createVerticalStrut(10));

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 8, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIConstants.BTN_HEIGHT));

        JButton btnDeliv = makeActionButton("🚶 Đang mang", UIConstants.WARNING, Color.WHITE);
        btnDeliv.setEnabled(!allDelivering);
        btnDeliv.addActionListener(e -> {
            btnDeliv.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    for (KitchenDAO.KitchenTicket t : tickets) {
                        if (t.itemStatus == Order.OrderItem.ItemStatus.READY) {
                            kitchenDAO.updateItemStatus(t.itemId,
                                    Order.OrderItem.ItemStatus.DELIVERING);
                        }
                    }
                    return null;
                }
                @Override protected void done() {
                    try { get(); } catch (Exception ex) {
                        System.err.println("[WaiterServicePanel] btnDeliv: " + ex.getMessage());
                    }
                    loadData();
                }
            }.execute();
        });

        JButton btnDone = makeActionButton("✔ Đã giao xong", UIConstants.SUCCESS, Color.WHITE);
        btnDone.setEnabled(!allDelivered);
        btnDone.addActionListener(e -> {
            btnDone.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    for (KitchenDAO.KitchenTicket t : tickets) {
                        if (t.itemStatus != Order.OrderItem.ItemStatus.DELIVERED) {
                            kitchenDAO.updateItemStatus(t.itemId,
                                    Order.OrderItem.ItemStatus.DELIVERED);
                        }
                    }
                    return null;
                }
                @Override protected void done() {
                    try { get(); } catch (Exception ex) {
                        System.err.println("[WaiterServicePanel] btnDone: " + ex.getMessage());
                    }
                    ToastNotification.show(
                            WaiterServicePanel.this,
                            "Đã giao xong bàn " + first.tableName + "!",
                            ToastNotification.Type.SUCCESS);
                    loadData();
                }
            }.execute();
        });

        btnRow.add(btnDeliv);
        btnRow.add(btnDone);
        card.add(btnRow);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                card.setBackground(new Color(0xF0F9FF));
            }
            @Override public void mouseExited(MouseEvent e) {
                card.setBackground(Color.WHITE);
            }
        });

        return card;
    }

    // ─── Item row ─────────────────────────────────────────────────────────────

    private JPanel buildItemRow(KitchenDAO.KitchenTicket t) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel nameQty = new JLabel(t.itemName + " × " + t.quantity);
        nameQty.setFont(UIConstants.FONT_BODY);

        row.add(nameQty,              BorderLayout.WEST);
        row.add(makeBadge(t.itemStatus), BorderLayout.EAST);
        return row;
    }

    // ─── Badge factory ────────────────────────────────────────────────────────

    private JLabel makeBadge(Order.OrderItem.ItemStatus s) {
        String text;
        Color  bg, fg;
        switch (s) {
            case READY:
                text = "Sẵn sàng";  bg = new Color(0xD1FAE5); fg = new Color(0x065F46); break;
            case DELIVERING:
                text = "Đang mang"; bg = new Color(0xDBEAFE); fg = new Color(0x1E40AF); break;
            case DELIVERED:
                text = "Đã giao";   bg = new Color(0xE5E7EB); fg = UIConstants.TEXT_SECONDARY; break;
            default:
                text = "Sẵn sàng";  bg = new Color(0xD1FAE5); fg = new Color(0x065F46); break;
        }
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(UIConstants.FONT_SMALL);
        l.setForeground(fg);
        l.setOpaque(true);
        l.setBackground(bg);
        l.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return l;
    }

    // ─── Button factory ───────────────────────────────────────────────────────

    private JButton makeActionButton(String text, Color bg, Color fg) {
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

    // ─── Empty state ──────────────────────────────────────────────────────────

    private JPanel buildEmptyState(String icon, String msg) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        JLabel icLbl = new JLabel(icon, SwingConstants.CENTER);
        icLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        icLbl.setAlignmentX(CENTER_ALIGNMENT);

        JLabel msgLbl = new JLabel(msg, SwingConstants.CENTER);
        msgLbl.setFont(UIConstants.FONT_BODY);
        msgLbl.setForeground(UIConstants.TEXT_SECONDARY);
        msgLbl.setAlignmentX(CENTER_ALIGNMENT);

        inner.add(icLbl);
        inner.add(Box.createVerticalStrut(16));
        inner.add(msgLbl);

        p.add(inner);
        return p;
    }

    // ─── AncestorListener ─────────────────────────────────────────────────────

    private void setupAncestorListener() {
        addAncestorListener(new AncestorListener() {
            @Override public void ancestorAdded(AncestorEvent e) {
                loadData();
                PollManager.getInstance().register("waiter",
                        WaiterServicePanel.this::loadData, 5_000);
            }
            @Override public void ancestorRemoved(AncestorEvent e) {
                PollManager.getInstance().unregister("waiter");
            }
            @Override public void ancestorMoved(AncestorEvent e) {}
        });
    }

    // ─── Inner classes ────────────────────────────────────────────────────────

    /** Badge role với nền bo tròn màu PRIMARY. */
    private static class RoleBadge extends JLabel {
        RoleBadge(String text) {
            super(text);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setForeground(UIConstants.TEXT_WHITE);
            setOpaque(false);
            setPreferredSize(new Dimension(80, 28));
            setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
            setHorizontalAlignment(SwingConstants.CENTER);
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UIConstants.PRIMARY);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Nút bo tròn viền PRIMARY, nền trắng — "Kết ca". */
    private static class RoundedOutlineButton extends JButton {
        RoundedOutlineButton(String text) {
            super(text);
            setFont(UIConstants.FONT_BODY);
            setForeground(UIConstants.PRIMARY);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setPreferredSize(new Dimension(80, 32));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(UIConstants.PRIMARY);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Viền bo tròn cho card. */
    private static class RoundedBorder extends AbstractBorder {
        private final int   radius;
        private final Color color;
        RoundedBorder(int radius, Color color) {
            this.radius = radius; this.color = color;
        }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, radius * 2, radius * 2);
            g2.dispose();
        }
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }
    }

    /** FlowLayout tự động xuống dòng. */
    private static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }
        @Override public Dimension minimumLayoutSize(Container target) {
            Dimension min = layoutSize(target, false);
            min.width -= getHgap() + 1;
            return min;
        }
        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
                int hgap = getHgap(), vgap = getVgap();
                Insets insets   = target.getInsets();
                int maxWidth    = targetWidth - (insets.left + insets.right + hgap * 2);
                Dimension dim   = new Dimension(0, 0);
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
                        rowHeight  = Math.max(rowHeight, d.height);
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

    // ─── CleanActionRenderer ─────────────────────────────────────────────────

    /**
     * Renderer hiển thị JButton trong cột "Hành động" của bảng Dọn bàn.
     */
    private static class CleanActionRenderer implements TableCellRenderer {
        private final List<TableItem> tables;

        CleanActionRenderer(List<TableItem> tables) {
            this.tables = tables;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            TableItem item = tables.get(row);
            boolean isDirty = item.getStatus() == TableItem.Status.DIRTY;
            JButton btn = new JButton(isDirty ? "Bắt đầu dọn" : "Dọn xong");
            btn.setFont(UIConstants.FONT_SMALL);
            btn.setBackground(isDirty ? UIConstants.WARNING : UIConstants.SUCCESS);
            btn.setForeground(Color.WHITE);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            return btn;
        }
    }

    // ─── CleanActionEditor ────────────────────────────────────────────────────

    /**
     * Editor cho cột "Hành động" – kích hoạt khi người dùng click.
     * Chuyển DIRTY → CLEANING → RANH (sẵn sàng).
     */
    private class CleanActionEditor extends DefaultCellEditor {
        private final List<TableItem> tables;
        private final TableDAO        tableDAO;
        private final Runnable        onRefresh;
        private JButton               btn;
        private TableItem             currentItem;

        CleanActionEditor(List<TableItem> tables, TableDAO tableDAO, Runnable onRefresh) {
            super(new JCheckBox());
            this.tables    = tables;
            this.tableDAO  = tableDAO;
            this.onRefresh = onRefresh;
            setClickCountToStart(1);
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable table, Object value, boolean isSelected, int row, int column) {
            currentItem = tables.get(row);
            boolean isDirty = currentItem.getStatus() == TableItem.Status.DIRTY;

            btn = new JButton(isDirty ? "Bắt đầu dọn" : "Dọn xong");
            btn.setFont(UIConstants.FONT_SMALL);
            btn.setBackground(isDirty ? UIConstants.WARNING : UIConstants.SUCCESS);
            btn.setForeground(Color.WHITE);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);

            btn.addActionListener(e -> {
                fireEditingStopped();
                TableItem.Status next = isDirty
                        ? TableItem.Status.CLEANING
                        : TableItem.Status.RANH;

                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() {
                        tableDAO.updateStatus(currentItem.getId(), next);
                        return null;
                    }
                    @Override
                    protected void done() {
                        try { get(); } catch (Exception ex) {
                            System.err.println("[CleanActionEditor] " + ex.getMessage());
                        }
                        String msg = (next == TableItem.Status.RANH)
                                ? "Bàn " + currentItem.getName() + " đã sẵn sàng phục vụ!"
                                : "Đang dọn bàn " + currentItem.getName();
                        ToastNotification.Type type = (next == TableItem.Status.RANH)
                                ? ToastNotification.Type.SUCCESS
                                : ToastNotification.Type.INFO;
                        ToastNotification.show(null, msg, type);
                        onRefresh.run();
                    }
                }.execute();
            });

            return btn;
        }

        @Override
        public Object getCellEditorValue() { return ""; }
    }
}