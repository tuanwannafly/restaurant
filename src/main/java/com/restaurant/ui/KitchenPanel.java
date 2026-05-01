package com.restaurant.ui;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.KitchenDAO.KitchenTicket;
import com.restaurant.data.DataManager;
import com.restaurant.model.MenuItem;
import com.restaurant.model.Order;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Màn hình bếp (Chef view) – Phase 3A redesign.
 * <p>
 * Layout: Header | JSplitPane(Chờ chế biến | Đang chế biến)
 * Auto-refresh via {@link PollManager}.
 */
public class KitchenPanel extends JPanel {

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final int REFRESH_MS = 5_000;

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final KitchenDAO dao = new KitchenDAO();

    private JPanel pendingCardsPanel;
    private JPanel cookingCardsPanel;

    private String selectedPendingCategory = null;
    private String selectedCookingCategory = null;

    private List<MenuItem> allMenuItems = new ArrayList<>();

    // Cached grouped data for filter re-apply
    private Map<String, List<KitchenTicket>> allPendingGroups = new LinkedHashMap<>();
    private Map<String, List<KitchenTicket>> allCookingGroups = new LinkedHashMap<>();

    // ─── Constructor ──────────────────────────────────────────────────────────

    public KitchenPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BG_PAGE);

        if (!AppSession.getInstance().hasPermission(Permission.VIEW_KITCHEN)) {
            JLabel denied = new JLabel("Không có quyền truy cập", SwingConstants.CENTER);
            denied.setFont(UIConstants.FONT_TITLE);
            denied.setForeground(UIConstants.TEXT_SECONDARY);
            add(denied, BorderLayout.CENTER);
            return;
        }

        // Load menu items once for category lookup
        try {
            allMenuItems = DataManager.getInstance().getMenuItems();
        } catch (Exception ignored) {}

        buildUI();
        setupAncestorListener();
    }

    // ─── UI Construction ──────────────────────────────────────────────────────

    private void buildUI() {
        add(buildHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildPendingPanel(), buildCookingPanel());
        split.setDividerSize(1);
        split.setBackground(UIConstants.BORDER_COLOR);
        split.setBorder(null);
        split.setResizeWeight(0.5);

        add(split, BorderLayout.CENTER);

        // Set divider to 50% after layout
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.5));
    }

    // ─── Header ───────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BG_WHITE);
        header.setPreferredSize(new Dimension(0, 56));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 24, 0, 24)));

        // ── Left: icon + system name + role badge ──
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel iconLabel = new JLabel("🍴");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));

        JLabel sysName = new JLabel("Tên hệ thống");
        sysName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sysName.setForeground(UIConstants.PRIMARY);

        JLabel badge = new JLabel("Đầu bếp") {
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
        };
        badge.setFont(new Font("Segoe UI", Font.BOLD, 13));
        badge.setForeground(Color.WHITE);
        badge.setOpaque(false);
        badge.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));

        left.add(iconLabel);
        left.add(sysName);
        left.add(badge);

        // ── Right: globe + restaurant name + "Kết ca" button ──
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        JLabel globeIcon = new JLabel("🌐");
        globeIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));

        String restaurantName = "Nhà hàng";
        try {
            String name = DataManager.getInstance().getMyRestaurant().getName();
            if (name != null && !name.isEmpty()) restaurantName = name;
        } catch (Exception ignored) {}

        JLabel restaurantLabel = new JLabel(restaurantName);
        restaurantLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        restaurantLabel.setForeground(UIConstants.PRIMARY);

        JButton btnEndShift = new RoundedOutlineButton("Kết ca", 80, 32);
        btnEndShift.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                    this, "Xác nhận kết ca?", "Kết ca",
                    JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                Window w = SwingUtilities.getWindowAncestor(this);
                if (w != null) w.dispose();
            }
        });

        right.add(globeIcon);
        right.add(restaurantLabel);
        right.add(btnEndShift);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // ─── Pending Panel ────────────────────────────────────────────────────────

    private JPanel buildPendingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);

        // Sub-NORTH
        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(UIConstants.BG_WHITE);
        north.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 12, 0, 12)));

        JLabel title = new JLabel("Chờ chế biến", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(UIConstants.PRIMARY);
        north.add(title, BorderLayout.NORTH);

        // Filter bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        ButtonGroup bg = new ButtonGroup();
        String[] categories = {"Tất cả", "Món chính", "Đồ uống", "Tráng miệng"};
        for (String cat : categories) {
            JToggleButton tb = makeCategoryToggle(cat);
            if (cat.equals("Tất cả")) tb.setSelected(true);
            tb.addActionListener(e -> {
                selectedPendingCategory = cat.equals("Tất cả") ? null : cat;
                applyPendingFilter();
            });
            bg.add(tb);
            filterBar.add(tb);
        }
        north.add(filterBar, BorderLayout.CENTER);

        panel.add(north, BorderLayout.NORTH);

        // Cards area
        pendingCardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 12));
        pendingCardsPanel.setBackground(UIConstants.BG_PAGE);
        pendingCardsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(pendingCardsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ─── Cooking Panel ────────────────────────────────────────────────────────

    private JPanel buildCookingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);

        // Sub-NORTH
        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(UIConstants.BG_WHITE);
        north.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 12, 0, 12)));

        JLabel title = new JLabel("Đang chế biến", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(UIConstants.PRIMARY);
        north.add(title, BorderLayout.NORTH);

        // Filter bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        ButtonGroup bg = new ButtonGroup();
        String[] categories = {"Tất cả", "Món chính", "Đồ uống", "Tráng miệng"};
        for (String cat : categories) {
            JToggleButton tb = makeCategoryToggle(cat);
            if (cat.equals("Tất cả")) tb.setSelected(true);
            tb.addActionListener(e -> {
                selectedCookingCategory = cat.equals("Tất cả") ? null : cat;
                applyCookingFilter();
            });
            bg.add(tb);
            filterBar.add(tb);
        }
        north.add(filterBar, BorderLayout.CENTER);

        panel.add(north, BorderLayout.NORTH);

        // Cards area
        cookingCardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 12));
        cookingCardsPanel.setBackground(UIConstants.BG_PAGE);
        cookingCardsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(cookingCardsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ─── Filter Toggle Button ─────────────────────────────────────────────────

    private JToggleButton makeCategoryToggle(String text) {
        JToggleButton tb = new JToggleButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                if (isSelected()) {
                    g2.setColor(UIConstants.PRIMARY);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                    setForeground(Color.WHITE);
                } else {
                    g2.setColor(Color.WHITE);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(UIConstants.PRIMARY);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.dispose();
                    setForeground(UIConstants.PRIMARY);
                }
                super.paintComponent(g);
            }
        };
        tb.setFont(UIConstants.FONT_BODY);
        tb.setPreferredSize(new Dimension(tb.getPreferredSize().width + 28, 30));
        tb.setBorderPainted(false);
        tb.setContentAreaFilled(false);
        tb.setFocusPainted(false);
        tb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tb.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        return tb;
    }

    // ─── Data Loading ─────────────────────────────────────────────────────────

    public void loadData() {
        long restaurantId = AppSession.getInstance().getRestaurantId();

        SwingWorker<List<KitchenTicket>, Void> worker =
                new SwingWorker<List<KitchenTicket>, Void>() {
                    @Override
                    protected List<KitchenTicket> doInBackground() {
                        return dao.getActiveTickets(restaurantId);
                    }

                    @Override
                    protected void done() {
                        try {
                            List<KitchenTicket> tickets = get();
                            allPendingGroups = groupByItemName(tickets);
                            allCookingGroups = groupCooking(tickets);
                            applyPendingFilter();
                            applyCookingFilter();
                        } catch (Exception ex) {
                            System.err.println("[KitchenPanel] loadData error: " + ex.getMessage());
                            ToastNotification.show(
                                    KitchenPanel.this,
                                    "Lỗi tải dữ liệu bếp: " + ex.getMessage(),
                                    ToastNotification.Type.ERROR);
                        }
                    }
                };
        worker.execute();
    }

    // ─── Grouping ─────────────────────────────────────────────────────────────

    private Map<String, List<KitchenTicket>> groupByItemName(List<KitchenTicket> tickets) {
        Map<String, List<KitchenTicket>> result = new LinkedHashMap<>();
        for (KitchenTicket t : tickets) {
            if (t.itemStatus == Order.OrderItem.ItemStatus.PENDING
                    || t.itemStatus == Order.OrderItem.ItemStatus.ACCEPTED) {
                result.computeIfAbsent(t.itemName, k -> new ArrayList<>()).add(t);
            }
        }
        return result;
    }

    private Map<String, List<KitchenTicket>> groupCooking(List<KitchenTicket> tickets) {
        Map<String, List<KitchenTicket>> result = new LinkedHashMap<>();
        for (KitchenTicket t : tickets) {
            if (t.itemStatus == Order.OrderItem.ItemStatus.COOKING) {
                result.computeIfAbsent(t.itemName, k -> new ArrayList<>()).add(t);
            }
        }
        return result;
    }

    // ─── Filter ───────────────────────────────────────────────────────────────

    private void applyPendingFilter() {
        Map<String, List<KitchenTicket>> filtered = allPendingGroups.entrySet().stream()
                .filter(e -> selectedPendingCategory == null
                        || getCategoryOf(e.getKey()).equals(selectedPendingCategory))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
        rebuildPendingCards(filtered);
    }

    private void applyCookingFilter() {
        Map<String, List<KitchenTicket>> filtered = allCookingGroups.entrySet().stream()
                .filter(e -> selectedCookingCategory == null
                        || getCategoryOf(e.getKey()).equals(selectedCookingCategory))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
        rebuildCookingCards(filtered);
    }

    private String getCategoryOf(String itemName) {
        for (MenuItem item : allMenuItems) {
            if (itemName.equals(item.getName())) {
                String cat = item.getCategory();
                return cat != null ? cat : "Khác";
            }
        }
        return "Khác";
    }

    // ─── Rebuild Cards ────────────────────────────────────────────────────────

    private void rebuildPendingCards(Map<String, List<KitchenTicket>> grouped) {
        pendingCardsPanel.removeAll();

        if (grouped.isEmpty()) {
            JLabel empty = new JLabel("Không có món nào đang chờ ✅", SwingConstants.CENTER);
            empty.setFont(UIConstants.FONT_BODY);
            empty.setForeground(UIConstants.TEXT_SECONDARY);
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setOpaque(false);
            wrapper.add(empty);
            pendingCardsPanel.setLayout(new BorderLayout());
            pendingCardsPanel.add(wrapper, BorderLayout.CENTER);
        } else {
            pendingCardsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 12, 12));
            for (Map.Entry<String, List<KitchenTicket>> entry : grouped.entrySet()) {
                pendingCardsPanel.add(buildPendingCard(entry.getKey(), entry.getValue()));
            }
        }

        pendingCardsPanel.revalidate();
        pendingCardsPanel.repaint();
    }

    private void rebuildCookingCards(Map<String, List<KitchenTicket>> grouped) {
        cookingCardsPanel.removeAll();

        if (grouped.isEmpty()) {
            JLabel empty = new JLabel("Không có món nào đang chế biến ✅", SwingConstants.CENTER);
            empty.setFont(UIConstants.FONT_BODY);
            empty.setForeground(UIConstants.TEXT_SECONDARY);
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setOpaque(false);
            wrapper.add(empty);
            cookingCardsPanel.setLayout(new BorderLayout());
            cookingCardsPanel.add(wrapper, BorderLayout.CENTER);
        } else {
            cookingCardsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 12, 12));
            for (Map.Entry<String, List<KitchenTicket>> entry : grouped.entrySet()) {
                cookingCardsPanel.add(buildCookingCard(entry.getKey(), entry.getValue()));
            }
        }

        cookingCardsPanel.revalidate();
        cookingCardsPanel.repaint();
    }

    // ─── Card Placeholders (Phase 3B will replace these) ─────────────────────

    /**
     * Placeholder pending card – Phase 3B will implement full card.
     */
    private JPanel buildPendingCard(String itemName, List<KitchenTicket> tickets) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(UIConstants.CORNER_RADIUS, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        card.setPreferredSize(new Dimension(220, 120));

        JLabel nameLabel = new JLabel(itemName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel qtyLabel = new JLabel("Số lượng chờ: " + tickets.size());
        qtyLabel.setFont(UIConstants.FONT_BODY);
        qtyLabel.setForeground(UIConstants.TEXT_SECONDARY);
        qtyLabel.setAlignmentX(LEFT_ALIGNMENT);

        card.add(nameLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(qtyLabel);

        return card;
    }

    /**
     * Placeholder cooking card – Phase 3B will implement full card.
     */
    private JPanel buildCookingCard(String itemName, List<KitchenTicket> tickets) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(UIConstants.CORNER_RADIUS, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        card.setPreferredSize(new Dimension(220, 120));

        JLabel nameLabel = new JLabel(itemName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel qtyLabel = new JLabel("Số lượng: " + tickets.size());
        qtyLabel.setFont(UIConstants.FONT_BODY);
        qtyLabel.setForeground(UIConstants.TEXT_SECONDARY);
        qtyLabel.setAlignmentX(LEFT_ALIGNMENT);

        card.add(nameLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(qtyLabel);

        return card;
    }

    // ─── AncestorListener ─────────────────────────────────────────────────────

    private void setupAncestorListener() {
        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent e) {
                loadData();
                PollManager.getInstance().register("kitchen", KitchenPanel.this::loadData, REFRESH_MS);
            }

            @Override
            public void ancestorRemoved(AncestorEvent e) {
                PollManager.getInstance().unregister("kitchen");
            }

            @Override
            public void ancestorMoved(AncestorEvent e) {}
        });
    }

    // ─── RoundedOutlineButton ─────────────────────────────────────────────────

    private static class RoundedOutlineButton extends JButton {
        RoundedOutlineButton(String text, int w, int h) {
            super(text);
            setFont(UIConstants.FONT_BODY);
            setForeground(UIConstants.PRIMARY);
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(w, h));
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
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
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ─── RoundedBorder inner class ────────────────────────────────────────────

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

    // ─── WrapLayout inner class ───────────────────────────────────────────────

    private static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

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

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int maxWidth = targetWidth - (insets.left + insets.right + hgap * 2);

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0, rowHeight = 0;

                int count = target.getComponentCount();
                for (int i = 0; i < count; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                        if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth  = 0;
                            rowHeight = 0;
                        }
                        if (rowWidth != 0) rowWidth += hgap;
                        rowWidth  += d.width;
                        rowHeight  = Math.max(rowHeight, d.height);
                    }
                }
                addRow(dim, rowWidth, rowHeight);
                dim.width  += insets.left + insets.right + hgap * 2;
                dim.height += insets.top + insets.bottom + vgap * 2;
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