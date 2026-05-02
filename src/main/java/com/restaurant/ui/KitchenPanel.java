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
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.AbstractBorder;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.KitchenDAO.KitchenTicket;
import com.restaurant.data.DataManager;
import com.restaurant.model.MenuItem;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

/**
 * Màn hình bếp (Chef view) – Phase 7B: Polling via ComponentListener + Toast delta.
 * <p>
 * Layout: StaffHeader | JSplitPane(Chờ chế biến | Đang chế biến)
 * Auto-refresh via {@link PollManager} key {@code "kitchen_v2"}.
 * Toast được hiện khi số ticket PENDING tăng so với lần poll trước.
 *
 * <h3>Refactor StaffHeader</h3>
 * <ul>
 *   <li>Xóa {@code buildHeader()} nội bộ.</li>
 *   <li>Dùng {@link StaffHeader#create(String, String, Runnable)} thay thế.</li>
 *   <li>Callback "Kết ca" unregister poll trước khi dispose window.</li>
 * </ul>
 *
 * <h3>Refactor EmptyState</h3>
 * <ul>
 *   <li>Dùng {@link EmptyStatePanel} dùng chung thay cho JLabel inline.</li>
 * </ul>
 */
public class KitchenPanel extends JPanel {

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final int    REFRESH_MS = 5_000;
    private static final String POLL_KEY   = "kitchen_v2";

    // Wait-time color thresholds
    private static final Color COLOR_SUCCESS = new Color(0x10B981); // < 10 phút
    private static final Color COLOR_WARNING = new Color(0xF59E0B); // 10-20 phút
    private static final Color COLOR_DANGER  = new Color(0xEF4444); // > 20 phút

    // Hover background for cards
    private static final Color CARD_HOVER_BG = new Color(0xF0F9FF);

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

    /**
     * Phase 7B: Tổng số ticket PENDING từ lần poll trước.
     * Dùng để phát hiện món mới và show toast.
     */
    private int lastPendingCount = 0;

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
        setupComponentListener(); // Phase 7B: replaces AncestorListener
    }

    // ─── UI Construction ──────────────────────────────────────────────────────

    private void buildUI() {
        // ── StaffHeader (thay thế buildHeader() cũ) ──────────────────────────
        String rName = "";
        try {
            String name = DataManager.getInstance().getMyRestaurant().getName();
            if (name != null && !name.isEmpty()) rName = name;
        } catch (Exception ignored) {}

        add(StaffHeader.create("Đầu bếp", rName, () -> {
            PollManager.getInstance().unregister(POLL_KEY);
            Window w = SwingUtilities.getWindowAncestor(KitchenPanel.this);
            if (w != null) w.dispose();
        }), BorderLayout.NORTH);

        // ── Body ──────────────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildPendingPanel(), buildCookingPanel());
        split.setDividerSize(1);
        split.setBackground(UIConstants.BORDER_COLOR);
        split.setBorder(null);
        split.setResizeWeight(0.5);

        // Responsive: keep divider centred whenever the pane is resized
        split.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> split.setDividerLocation(0.5));
            }
        });

        add(split, BorderLayout.CENTER);
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.5));
    }

    // ─── Pending Panel ────────────────────────────────────────────────────────

    private JPanel buildPendingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(UIConstants.BG_WHITE);
        north.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 12, 0, 12)));

        JLabel title = new JLabel("Chờ chế biến", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(UIConstants.PRIMARY);
        north.add(title, BorderLayout.NORTH);

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

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(UIConstants.BG_WHITE);
        north.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 12, 0, 12)));

        JLabel title = new JLabel("Đang chế biến", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(UIConstants.PRIMARY);
        north.add(title, BorderLayout.NORTH);

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

    // ─── Phase 7B: ComponentListener ─────────────────────────────────────────

    /**
     * Đăng ký / huỷ polling dựa trên visibility của panel trong MainFrame.
     */
    private void setupComponentListener() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                PollManager.getInstance().register(POLL_KEY, KitchenPanel.this::doPoll, REFRESH_MS);
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                PollManager.getInstance().unregister(POLL_KEY);
            }
        });
    }

    // ─── Phase 7B: doPoll() ───────────────────────────────────────────────────

    private void doPoll() {
        long restaurantId = AppSession.getInstance().getRestaurantId();

        new SwingWorker<KitchenData, Void>() {
            @Override
            protected KitchenData doInBackground() {
                List<KitchenTicket> all = dao.getActiveTickets(restaurantId);

                List<KitchenTicket> pending = new ArrayList<>();
                List<KitchenTicket> cooking = new ArrayList<>();

                for (KitchenTicket ticket : all) {
                    switch (ticket.itemStatus) {
                        case PENDING:
                        case ACCEPTED:
                            pending.add(ticket);
                            break;
                        case COOKING:
                            cooking.add(ticket);
                            break;
                        default:
                            break;
                    }
                }
                return new KitchenData(pending, cooking);
            }

            @Override
            protected void done() {
                try {
                    KitchenData data = get();

                    allPendingGroups = groupByItem(data.pending);
                    allCookingGroups = groupByItem(data.cooking);
                    applyPendingFilter();
                    applyCookingFilter();

                    int newCount = data.pending.size();
                    if (newCount > lastPendingCount) {
                        int diff = newCount - lastPendingCount;
                        ToastNotification.show(
                                KitchenPanel.this,
                                "Có " + diff + " món mới cần chế biến!",
                                ToastNotification.Type.INFO);
                    }
                    lastPendingCount = newCount;

                } catch (Exception ex) {
                    System.err.println("[KitchenPanel] doPoll error: " + ex.getMessage());
                    ToastNotification.show(
                            KitchenPanel.this,
                            "Lỗi tải dữ liệu bếp: " + ex.getMessage(),
                            ToastNotification.Type.ERROR);
                }
            }
        }.execute();
    }

    private static Map<String, List<KitchenTicket>> groupByItem(List<KitchenTicket> tickets) {
        Map<String, List<KitchenTicket>> map = new LinkedHashMap<>();
        for (KitchenTicket t : tickets) {
            map.computeIfAbsent(t.itemName, k -> new ArrayList<>()).add(t);
        }
        return map;
    }

    // ─── Data Loading (public API – delegates to doPoll) ─────────────────────

    public void loadData() {
        doPoll();
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
            pendingCardsPanel.setLayout(new BorderLayout());
            pendingCardsPanel.add(
                    new EmptyStatePanel("🍽", "Không có món nào đang chờ",
                            "Tất cả đã được chế biến ✓"),
                    BorderLayout.CENTER);
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
            cookingCardsPanel.setLayout(new BorderLayout());
            cookingCardsPanel.add(
                    new EmptyStatePanel("👨‍🍳", "Không có món nào đang chế biến", null),
                    BorderLayout.CENTER);
        } else {
            cookingCardsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 12, 12));
            for (Map.Entry<String, List<KitchenTicket>> entry : grouped.entrySet()) {
                cookingCardsPanel.add(buildCookingCard(entry.getKey(), entry.getValue()));
            }
        }

        cookingCardsPanel.revalidate();
        cookingCardsPanel.repaint();
    }

    // ─── Card builders ────────────────────────────────────────────────────────

    private JPanel buildPendingCard(String itemName, List<KitchenTicket> tickets) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(8, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        card.setPreferredSize(new Dimension(220, 130));
        card.setMinimumSize(new Dimension(220, 0));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel nameLabel = new JLabel(itemName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);

        int totalQty = sumQuantity(tickets);
        JLabel qtyLabel = new JLabel("Số lượng chờ: " + totalQty);
        qtyLabel.setFont(UIConstants.FONT_BODY);
        qtyLabel.setForeground(UIConstants.TEXT_PRIMARY);
        qtyLabel.setAlignmentX(LEFT_ALIGNMENT);

        long waitMinutes = calcWaitMinutes(tickets);
        JLabel waitLabel = new JLabel("Chờ lâu nhất: " + waitMinutes + " phút");
        waitLabel.setAlignmentX(LEFT_ALIGNMENT);

        if (waitMinutes < 10) {
            waitLabel.setFont(UIConstants.FONT_BODY);
            waitLabel.setForeground(COLOR_SUCCESS);
        } else if (waitMinutes <= 20) {
            waitLabel.setFont(UIConstants.FONT_BODY);
            waitLabel.setForeground(COLOR_WARNING);
        } else {
            waitLabel.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD));
            waitLabel.setForeground(COLOR_DANGER);
        }

        card.add(nameLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(qtyLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(waitLabel);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { card.setBackground(CARD_HOVER_BG); }
            @Override public void mouseExited(MouseEvent e)  { card.setBackground(Color.WHITE); }
            @Override public void mouseClicked(MouseEvent e) { openPendingDetailDialog(itemName, tickets); }
        });

        return card;
    }

    private JPanel buildCookingCard(String itemName, List<KitchenTicket> tickets) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(8, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        card.setPreferredSize(new Dimension(220, 130));
        card.setMinimumSize(new Dimension(220, 0));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel nameLabel = new JLabel(itemName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);

        String assignedTo = "Đang chế biến";
        if (!tickets.isEmpty() && tickets.get(0).assignedTo != null
                && !tickets.get(0).assignedTo.isBlank()) {
            assignedTo = tickets.get(0).assignedTo;
        }
        JLabel staffLabel = new JLabel(assignedTo);
        staffLabel.setFont(UIConstants.FONT_BODY);
        staffLabel.setForeground(UIConstants.TEXT_SECONDARY);
        staffLabel.setAlignmentX(LEFT_ALIGNMENT);

        int totalQty = sumQuantity(tickets);
        JLabel qtyLabel = new JLabel("Số lượng: " + totalQty);
        qtyLabel.setFont(UIConstants.FONT_BODY);
        qtyLabel.setForeground(UIConstants.TEXT_PRIMARY);
        qtyLabel.setAlignmentX(LEFT_ALIGNMENT);

        card.add(nameLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(staffLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(qtyLabel);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { card.setBackground(CARD_HOVER_BG); }
            @Override public void mouseExited(MouseEvent e)  { card.setBackground(Color.WHITE); }
            @Override public void mouseClicked(MouseEvent e) { openCookingDetailDialog(itemName, tickets); }
        });

        return card;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private long calcWaitMinutes(List<KitchenTicket> tickets) {
        return tickets.stream()
                .map(t -> t.createdAt)
                .filter(Objects::nonNull)
                .mapToLong(dt -> Duration.between(dt, LocalDateTime.now()).toMinutes())
                .max()
                .orElse(0L);
    }

    private int sumQuantity(List<KitchenTicket> tickets) {
        return tickets.stream().mapToInt(t -> t.quantity).sum();
    }

    // ─── Detail Dialogs (Phase 3C – placeholder) ──────────────────────────────

    private void openPendingDetailDialog(String itemName, List<KitchenTicket> tickets) {
        // Phase 3C implement
    }

    private void openCookingDetailDialog(String itemName, List<KitchenTicket> tickets) {
        // Phase 3C implement
    }

    // ─── Phase 7B: KitchenData ────────────────────────────────────────────────

    private static final class KitchenData {
        final List<KitchenTicket> pending;
        final List<KitchenTicket> cooking;

        KitchenData(List<KitchenTicket> pending, List<KitchenTicket> cooking) {
            this.pending = pending;
            this.cooking = cooking;
        }
    }

    // ─── Inner classes ────────────────────────────────────────────────────────

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