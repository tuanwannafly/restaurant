package com.restaurant.ui;

import com.restaurant.data.DataManager;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Màn hình Thu ngân (Cashier view) – Phase 5A: Skeleton + Header.
 * <p>
 * Layout: Header (56px) | JSplitPane 50/50 (Chờ thanh toán | Đang thanh toán)
 * <p>
 * Tham chiếu design: Image 2 – màn hình chính thu ngân.
 */
public class CashierPanel extends JPanel {

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final int REFRESH_MS = 5_000;

    // ─── Fields ───────────────────────────────────────────────────────────────

    /** Khu vực cards "Chờ thanh toán" – Phase 5B sẽ populate. */
    private JPanel pendingCardsPanel;

    /** Khu vực cards "Đang thanh toán" – Phase 5B sẽ populate. */
    private JPanel processingCardsPanel;

    /** Filter hiện tại cho cột trái (null = Tất cả). */
    private String selectedPaymentFilter = null;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public CashierPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BG_PAGE);

        if (!AppSession.getInstance().hasPermission(Permission.VIEW_CASHIER)) {
            JLabel denied = new JLabel("Không có quyền truy cập", SwingConstants.CENTER);
            denied.setFont(UIConstants.FONT_TITLE);
            denied.setForeground(UIConstants.TEXT_SECONDARY);
            add(denied, BorderLayout.CENTER);
            return;
        }

        buildUI();
        setupAncestorListener();
    }

    // ─── UI Construction ──────────────────────────────────────────────────────

    private void buildUI() {
        add(buildHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                buildPendingPanel(),
                buildProcessingPanel());
        split.setDividerSize(1);
        split.setBackground(UIConstants.BORDER_COLOR);
        split.setBorder(null);
        split.setResizeWeight(0.5);

        add(split, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.5));
    }

    // ─── Header ───────────────────────────────────────────────────────────────

    /**
     * Header 56px — giống pattern KitchenPanel:
     * LEFT : icon + "Tên hệ thống" + badge "Thu ngân"
     * RIGHT: 🌐 + tên nhà hàng + nút "Kết ca"
     */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BG_WHITE);
        header.setPreferredSize(new Dimension(0, 56));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 24, 0, 24)));

        // ── Left ──
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel iconLabel = new JLabel("⛁");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        iconLabel.setForeground(UIConstants.PRIMARY);

        JLabel sysName = new JLabel("Tên hệ thống");
        sysName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sysName.setForeground(UIConstants.PRIMARY);

        // Badge "Thu ngân" – solid filled rounded
        JLabel badge = new JLabel("Thu ngân") {
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

        // ── Right ──
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        JLabel globeIcon = new JLabel("🌐");
        globeIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));

        String restaurantName = "Tên nhà hàng";
        try {
            String name = DataManager.getInstance().getMyRestaurant().getName();
            if (name != null && !name.isBlank()) restaurantName = name;
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

    // ─── Pending Panel (Chờ thanh toán) ──────────────────────────────────────

    private JPanel buildPendingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);

        // Sub-header
        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(UIConstants.BG_WHITE);
        north.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 16, 0, 16)));

        JLabel title = new JLabel("Chờ thanh toán", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(UIConstants.PRIMARY);
        north.add(title, BorderLayout.NORTH);

        // Filter bar: Tất cả | Tiền mặt | Chuyển khoản
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        ButtonGroup bg = new ButtonGroup();

        String[] filters   = {"Tất cả",  "Tiền mặt", "Chuyển khoản"};
        String[] filterKeys = {null,      "CASH",      "TRANSFER"};

        for (int i = 0; i < filters.length; i++) {
            final String key = filterKeys[i];
            JToggleButton tb = makeCategoryToggle(filters[i]);
            if (i == 0) tb.setSelected(true);          // "Tất cả" mặc định
            tb.addActionListener(e -> {
                selectedPaymentFilter = key;
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

        // Phase 5A: placeholder label
        JLabel placeholder = new JLabel("Đang tải...", SwingConstants.CENTER);
        placeholder.setFont(UIConstants.FONT_BODY);
        placeholder.setForeground(UIConstants.TEXT_SECONDARY);
        pendingCardsPanel.add(placeholder);

        JScrollPane scroll = new JScrollPane(pendingCardsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ─── Processing Panel (Đang thanh toán) ──────────────────────────────────

    private JPanel buildProcessingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);

        // Sub-header (không có filter)
        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(UIConstants.BG_WHITE);
        north.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        JLabel title = new JLabel("Đang thanh toán", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(UIConstants.PRIMARY);
        north.add(title, BorderLayout.CENTER);

        panel.add(north, BorderLayout.NORTH);

        // Cards area
        processingCardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 12));
        processingCardsPanel.setBackground(UIConstants.BG_PAGE);
        processingCardsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Phase 5A: placeholder label
        JLabel placeholder = new JLabel("Đang tải...", SwingConstants.CENTER);
        placeholder.setFont(UIConstants.FONT_BODY);
        placeholder.setForeground(UIConstants.TEXT_SECONDARY);
        processingCardsPanel.add(placeholder);

        JScrollPane scroll = new JScrollPane(processingCardsPanel,
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

    /**
     * Điểm vào duy nhất để load/reload dữ liệu.
     * Phase 5B sẽ gọi DAO và populate cards.
     */
    public void loadData() {
        // Phase 5B: implement DAO + SwingWorker
        // Hiện tại chỉ xóa placeholder và hiện empty state
        SwingUtilities.invokeLater(this::showEmptyState);
    }

    // ─── Filter ───────────────────────────────────────────────────────────────

    /**
     * Áp lại filter lên danh sách cards "Chờ thanh toán".
     * Phase 5B sẽ thực thi filter thật sự.
     */
    private void applyPendingFilter() {
        // Phase 5B: filter allPendingOrders theo selectedPaymentFilter
        pendingCardsPanel.revalidate();
        pendingCardsPanel.repaint();
    }

    // ─── Empty State ──────────────────────────────────────────────────────────

    private void showEmptyState() {
        pendingCardsPanel.removeAll();
        JLabel empty = new JLabel("Không có đơn nào chờ thanh toán ✅", SwingConstants.CENTER);
        empty.setFont(UIConstants.FONT_BODY);
        empty.setForeground(UIConstants.TEXT_SECONDARY);
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(empty);
        pendingCardsPanel.setLayout(new BorderLayout());
        pendingCardsPanel.add(wrapper, BorderLayout.CENTER);
        pendingCardsPanel.revalidate();
        pendingCardsPanel.repaint();

        processingCardsPanel.removeAll();
        JLabel empty2 = new JLabel("Không có đơn nào đang xử lý ✅", SwingConstants.CENTER);
        empty2.setFont(UIConstants.FONT_BODY);
        empty2.setForeground(UIConstants.TEXT_SECONDARY);
        JPanel wrapper2 = new JPanel(new GridBagLayout());
        wrapper2.setOpaque(false);
        wrapper2.add(empty2);
        processingCardsPanel.setLayout(new BorderLayout());
        processingCardsPanel.add(wrapper2, BorderLayout.CENTER);
        processingCardsPanel.revalidate();
        processingCardsPanel.repaint();
    }

    // ─── AncestorListener (auto-refresh) ─────────────────────────────────────

    private void setupAncestorListener() {
        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent e) {
                loadData();
                PollManager.getInstance().register(
                        "cashier", CashierPanel.this::loadData, REFRESH_MS);
            }

            @Override
            public void ancestorRemoved(AncestorEvent e) {
                PollManager.getInstance().unregister("cashier");
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

                int hgap = getHgap(), vgap = getVgap();
                Insets insets = target.getInsets();
                int maxWidth = targetWidth - (insets.left + insets.right + hgap * 2);

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0, rowHeight = 0;

                for (int i = 0; i < target.getComponentCount(); i++) {
                    Component m = target.getComponent(i);
                    if (!m.isVisible()) continue;
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