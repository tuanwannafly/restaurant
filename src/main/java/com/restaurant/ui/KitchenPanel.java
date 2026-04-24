package com.restaurant.ui;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.KitchenDAO.KitchenTicket;
import com.restaurant.model.Order;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Màn hình bếp (Chef view).
 * <p>
 * Hiển thị các món đang PENDING / ACCEPTED / COOKING nhóm theo bàn dạng card.
 * Auto-refresh mỗi 5 giây bằng {@link Timer}.
 * <p>
 * RBAC: yêu cầu {@link Permission#VIEW_KITCHEN}.
 */
public class KitchenPanel extends JPanel {

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final int  REFRESH_MS        = 5_000;
    private static final int  CARD_MIN_WIDTH     = 240;
    private static final int  CARD_PADDING       = 14;
    private static final int  CARD_GAP           = 14;
    private static final Color CARD_BG           = Color.WHITE;
    private static final Color CARD_BORDER       = new Color(0xD1D5DB);

    // Status badge colours
    private static final Color BADGE_PENDING_BG  = new Color(0xFEF3C7);
    private static final Color BADGE_PENDING_FG  = new Color(0x92400E);
    private static final Color BADGE_ACCEPTED_BG = new Color(0xDBEAFE);
    private static final Color BADGE_ACCEPTED_FG = new Color(0x1E40AF);
    private static final Color BADGE_COOKING_BG  = new Color(0xFCE7F3);
    private static final Color BADGE_COOKING_FG  = new Color(0x9D174D);

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final KitchenDAO dao = new KitchenDAO();

    private JPanel  cardsContainer;
    private JLabel  lastUpdateLabel;

    private final DateTimeFormatter timeFmt =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // ─── Constructor ──────────────────────────────────────────────────────────

    public KitchenPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BG_PAGE);

        // RBAC check
        if (!AppSession.getInstance().hasPermission(Permission.VIEW_KITCHEN)) {
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
        add(buildHeader(), BorderLayout.NORTH);

        // Cards area wrapped in scroll pane
        cardsContainer = new JPanel(new WrapLayout(FlowLayout.LEFT, CARD_GAP, CARD_GAP));
        cardsContainer.setBackground(UIConstants.BG_PAGE);
        cardsContainer.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JScrollPane scroll = new JScrollPane(cardsContainer,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(UIConstants.BG_PAGE);

        add(scroll, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BG_WHITE);
        header.setPreferredSize(new Dimension(0, 56));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 20, 0, 20)));

        // Left: title
        JLabel title = new JLabel("🍳  Màn hình bếp");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(UIConstants.TEXT_PRIMARY);

        // Right: last-update label + refresh button
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        lastUpdateLabel = new JLabel("—");
        lastUpdateLabel.setFont(UIConstants.FONT_SMALL);
        lastUpdateLabel.setForeground(UIConstants.TEXT_SECONDARY);

        JButton btnRefresh = new JButton("↻  Làm mới");
        btnRefresh.setFont(UIConstants.FONT_BODY);
        btnRefresh.setBackground(UIConstants.PRIMARY);
        btnRefresh.setForeground(UIConstants.TEXT_WHITE);
        btnRefresh.setBorderPainted(false);
        btnRefresh.setFocusPainted(false);
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnRefresh.addActionListener(e -> loadData());

        right.add(lastUpdateLabel);
        right.add(btnRefresh);

        header.add(title, BorderLayout.WEST);
        header.add(right,  BorderLayout.EAST);
        return header;
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    /** Called on UI thread to trigger background load. */
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
                            rebuildCards(tickets);
                            lastUpdateLabel.setText("Cập nhật lúc " +
                                    LocalTime.now().format(timeFmt));
                        } catch (Exception ex) {
                            System.err.println("[KitchenPanel] loadData error: " + ex.getMessage());
                            ToastNotification.show(
                                KitchenPanel.this,
                                "Lỗi tải dữ liệu bếp: " + ex.getMessage(),
                                ToastNotification.Type.ERROR
                            );
                        }
                    }
                };
        worker.execute();
    }

    /** Rebuild the card grid on the EDT. */
    private void rebuildCards(List<KitchenTicket> tickets) {
        cardsContainer.removeAll();

        if (tickets.isEmpty()) {
            JLabel empty = new JLabel("Không có món nào đang chờ  ✅", SwingConstants.CENTER);
            empty.setFont(UIConstants.FONT_TITLE);
            empty.setForeground(UIConstants.TEXT_SECONDARY);
            // Center it by using a full-width filler
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setOpaque(false);
            wrapper.add(empty);
            cardsContainer.setLayout(new BorderLayout());
            cardsContainer.add(wrapper, BorderLayout.CENTER);
        } else {
            cardsContainer.setLayout(new WrapLayout(FlowLayout.LEFT, CARD_GAP, CARD_GAP));

            // Group by tableId preserving order
            Map<String, List<KitchenTicket>> grouped = new LinkedHashMap<>();
            for (KitchenTicket t : tickets) {
                grouped.computeIfAbsent(t.tableId, k -> new ArrayList<>()).add(t);
            }

            for (Map.Entry<String, List<KitchenTicket>> entry : grouped.entrySet()) {
                cardsContainer.add(buildTableCard(entry.getValue()));
            }
        }

        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    // ─── Card builder ─────────────────────────────────────────────────────────

    private JPanel buildTableCard(List<KitchenTicket> items) {
        KitchenTicket first = items.get(0);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(UIConstants.CORNER_RADIUS, CARD_BORDER),
                BorderFactory.createEmptyBorder(CARD_PADDING, CARD_PADDING,
                        CARD_PADDING, CARD_PADDING)));
        card.setPreferredSize(new Dimension(CARD_MIN_WIDTH, calcCardHeight(items.size())));
        card.setMinimumSize(new Dimension(CARD_MIN_WIDTH, 0));

        // ── Card header ──
        JLabel cardTitle = new JLabel("Bàn " + first.tableName +
                "  ·  Lượt " + first.roundNumber);
        cardTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        cardTitle.setForeground(UIConstants.TEXT_PRIMARY);
        cardTitle.setAlignmentX(LEFT_ALIGNMENT);
        card.add(cardTitle);
        card.add(Box.createVerticalStrut(10));

        // ── Item rows ──
        for (KitchenTicket t : items) {
            card.add(buildItemRow(t));
            card.add(Box.createVerticalStrut(6));
        }

        card.add(Box.createVerticalStrut(4));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(10));

        // ── "Tiếp nhận tất cả" button ──
        JButton btnAccept = makeButton("✔  Tiếp nhận tất cả", UIConstants.WARNING,
                UIConstants.TEXT_WHITE);
        btnAccept.setAlignmentX(LEFT_ALIGNMENT);
        btnAccept.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIConstants.BTN_HEIGHT));

        // Disable if no PENDING items remain
        boolean hasPending = items.stream()
                .anyMatch(t -> t.itemStatus == Order.OrderItem.ItemStatus.PENDING);
        btnAccept.setEnabled(hasPending);

        btnAccept.addActionListener(e -> {
            btnAccept.setEnabled(false);
            // PENDING → ACCEPTED → COOKING in sequence
            SwingWorker<Void, Void> w = new SwingWorker<>() {
                @Override protected Void doInBackground() throws Exception {
                    for (KitchenTicket t : items) {
                        if (t.itemStatus == Order.OrderItem.ItemStatus.PENDING) {
                            dao.updateItemStatus(t.itemId,
                                    Order.OrderItem.ItemStatus.ACCEPTED);
                            dao.updateItemStatus(t.itemId,
                                    Order.OrderItem.ItemStatus.COOKING);
                        }
                    }
                    return null;
                }
                @Override protected void done() {
                    try { get(); } catch (Exception ex) {
                        ToastNotification.show(KitchenPanel.this,
                            "Lỗi cập nhật trạng thái: " + ex.getMessage(),
                            ToastNotification.Type.ERROR);
                    }
                    loadData();
                }
            };
            w.execute();
        });

        card.add(btnAccept);
        return card;
    }

    /** One row per item inside a card. */
    private JPanel buildItemRow(KitchenTicket t) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        // Left: item name + quantity
        JLabel nameQty = new JLabel(t.itemName + " × " + t.quantity);
        nameQty.setFont(UIConstants.FONT_BODY);
        nameQty.setForeground(UIConstants.TEXT_PRIMARY);

        // Center: status badge
        JLabel badge = makeBadge(t.itemStatus);

        // Right: "Hoàn thành" button (only for COOKING items)
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);

        if (t.itemStatus == Order.OrderItem.ItemStatus.COOKING) {
            JButton btnDone = makeSmallButton("✓ Xong", UIConstants.SUCCESS);
            btnDone.addActionListener(e -> {
                btnDone.setEnabled(false);
                SwingWorker<Void, Void> w = new SwingWorker<>() {
                    @Override protected Void doInBackground() throws Exception {
                        dao.updateItemStatus(t.itemId,
                                Order.OrderItem.ItemStatus.READY);
                        return null;
                    }
                    @Override protected void done() {
                        try { get(); } catch (Exception ex) {
                            ToastNotification.show(KitchenPanel.this,
                                "Lỗi cập nhật trạng thái: " + ex.getMessage(),
                                ToastNotification.Type.ERROR);
                        }
                        loadData();
                    }
                };
                w.execute();
            });
            right.add(btnDone);
        }

        row.add(nameQty, BorderLayout.WEST);
        row.add(badge,   BorderLayout.CENTER);
        row.add(right,   BorderLayout.EAST);
        return row;
    }

    // ─── AncestorListener — delegate lifecycle to PollManager ─────────────────

    private void setupAncestorListener() {
        addAncestorListener(new AncestorListener() {
            /** Panel được thêm vào container → bắt đầu polling. */
            @Override
            public void ancestorAdded(AncestorEvent e) {
                // Tải dữ liệu ngay lập tức, sau đó polling định kỳ qua PollManager.
                loadData();
                PollManager.getInstance().register("kitchen", KitchenPanel.this::loadData, REFRESH_MS);
            }

            /** Panel bị remove khỏi container → dừng polling, giải phóng timer. */
            @Override
            public void ancestorRemoved(AncestorEvent e) {
                PollManager.getInstance().unregister("kitchen");
            }

            @Override
            public void ancestorMoved(AncestorEvent e) {}
        });
    }

    // ─── Widget helpers ───────────────────────────────────────────────────────

    private JLabel makeBadge(Order.OrderItem.ItemStatus status) {
        String text;
        Color bg, fg;
        switch (status) {
            case PENDING:
                text = "Chờ";
                bg = BADGE_PENDING_BG; fg = BADGE_PENDING_FG; break;
            case ACCEPTED:
                text = "Đã nhận";
                bg = BADGE_ACCEPTED_BG; fg = BADGE_ACCEPTED_FG; break;
            case COOKING:
                text = "Đang nấu";
                bg = BADGE_COOKING_BG; fg = BADGE_COOKING_FG; break;
            default:
                text = status.name();
                bg = UIConstants.HEADER_BG; fg = UIConstants.TEXT_SECONDARY;
        }
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(UIConstants.FONT_SMALL);
        lbl.setForeground(fg);
        lbl.setOpaque(true);
        lbl.setBackground(bg);
        lbl.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return lbl;
    }

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

    private JButton makeSmallButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(UIConstants.FONT_SMALL);
        btn.setBackground(bg);
        btn.setForeground(UIConstants.TEXT_WHITE);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(70, 24));
        return btn;
    }

    private JSeparator buildDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(UIConstants.BORDER_COLOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(LEFT_ALIGNMENT);
        return sep;
    }

    /** Estimate card height based on item count. */
    private int calcCardHeight(int itemCount) {
        // header(28) + gap(10) + items(34*n) + gaps(6*(n-1)) + divider(1+10+10) + button(34) + padding(28)
        return 28 + 10 + (34 + 6) * itemCount + 55 + 2 * CARD_PADDING;
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
    // Lightweight wrap layout that respects container width.

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
                            rowWidth = 0;
                            rowHeight = 0;
                        }
                        if (rowWidth != 0) rowWidth += hgap;
                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }
                addRow(dim, rowWidth, rowHeight);
                dim.width += insets.left + insets.right + hgap * 2;
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