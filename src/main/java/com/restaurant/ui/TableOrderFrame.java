package com.restaurant.ui;

import com.restaurant.dao.MenuItemDAO;
import com.restaurant.model.MenuItem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * TableOrderFrame — Phase 3A (UI only)
 *
 * <p>JFrame fullscreen giả lập màn hình tablet tại bàn ăn.
 * Hiển thị menu theo category (JTabbedPane + grid card),
 * giỏ hàng với add/remove/số lượng, tổng tiền và nút "Gửi order".
 *
 * <p>Chưa gọi DB gửi order — sẽ implement ở Phase 3B.
 */
public class TableOrderFrame extends JFrame {

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final String tableId;
    private final String orderId;
    private final String tableName;

    // UI components
    private JLabel lblTime;
    private JTabbedPane tabbedMenu;
    private JPanel cartListPanel;
    private JLabel lblTotal;
    private RoundedButton btnSend;
    private JScrollPane cartScroll;

    // Cart state
    private final List<CartItem> cartItems = new ArrayList<>();

    // Formatting
    private static final NumberFormat PRICE_FMT = NumberFormat.getInstance(Locale.of("vi", "VN"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ─── Inner class: CartItem ────────────────────────────────────────────────

    private static class CartItem {
        final String menuItemId;
        final String name;
        final double unitPrice;
        int quantity;

        CartItem(String menuItemId, String name, double unitPrice) {
            this.menuItemId = menuItemId;
            this.name       = name;
            this.unitPrice  = unitPrice;
            this.quantity   = 1;
        }

        double subtotal() { return unitPrice * quantity; }
    }

    // ─── Constructor ──────────────────────────────────────────────────────────

    public TableOrderFrame(String tableId, String orderId, String tableName) {
        this.tableId   = tableId;
        this.orderId   = orderId;
        this.tableName = tableName;
        initUI();
        loadMenu();
    }

    // ─── UI Init ──────────────────────────────────────────────────────────────

    private void initUI() {
        setTitle("Bàn " + tableName);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        getContentPane().setBackground(UIConstants.BG_PAGE);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(),  BorderLayout.NORTH);
        add(buildCenter(),  BorderLayout.CENTER);
        add(buildFooter(),  BorderLayout.SOUTH);

        startClock();
        setVisible(true);
    }

    // ── NORTH: Header bar ─────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BG_WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
            new EmptyBorder(0, 24, 0, 24)
        ));
        header.setPreferredSize(new Dimension(0, 56));

        // Left: table name
        JLabel lblTable = new JLabel("Bàn " + tableName);
        lblTable.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTable.setForeground(UIConstants.TEXT_PRIMARY);
        header.add(lblTable, BorderLayout.WEST);

        // Right: live clock
        lblTime = new JLabel();
        lblTime.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        lblTime.setForeground(UIConstants.TEXT_SECONDARY);
        header.add(lblTime, BorderLayout.EAST);

        return header;
    }

    private void startClock() {
        // Update immediately then every second
        Runnable tick = () -> {
            if (lblTime != null)
                lblTime.setText(LocalTime.now().format(TIME_FMT));
        };
        tick.run();
        new javax.swing.Timer(1000, e -> tick.run()).start();
    }

    // ── CENTER: SplitPane ─────────────────────────────────────────────────────

    private JSplitPane buildCenter() {
        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildMenuPanel(),
            buildCartPanel()
        );
        split.setBorder(null);
        split.setDividerSize(1);
        split.setBackground(UIConstants.BORDER_COLOR);

        // Set divider at 60 % after frame is visible
        SwingUtilities.invokeLater(() -> {
            int total = split.getWidth();
            if (total > 0) split.setDividerLocation((int)(total * 0.60));
            else           split.setResizeWeight(0.60);
        });
        split.setResizeWeight(0.60);
        return split;
    }

    // ── LEFT: Menu panel ──────────────────────────────────────────────────────

    private JPanel buildMenuPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);

        tabbedMenu = new JTabbedPane(JTabbedPane.TOP);
        tabbedMenu.setFont(UIConstants.FONT_BOLD);
        tabbedMenu.setBackground(UIConstants.BG_PAGE);

        // Placeholder tab shown while SwingWorker loads
        JPanel loading = new JPanel(new BorderLayout());
        loading.setBackground(UIConstants.BG_PAGE);
        JLabel lbl = new JLabel("Đang tải thực đơn…", SwingConstants.CENTER);
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_SECONDARY);
        loading.add(lbl, BorderLayout.CENTER);
        tabbedMenu.addTab("  …  ", loading);

        panel.add(tabbedMenu, BorderLayout.CENTER);
        return panel;
    }

    // ── RIGHT: Cart panel ─────────────────────────────────────────────────────

    private JPanel buildCartPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_WHITE);

        // Header
        JPanel cartHeader = new JPanel(new BorderLayout());
        cartHeader.setBackground(UIConstants.BG_WHITE);
        cartHeader.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
            new EmptyBorder(14, 20, 14, 20)
        ));
        JLabel lblCart = new JLabel("Giỏ hàng");
        lblCart.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblCart.setForeground(UIConstants.TEXT_PRIMARY);
        cartHeader.add(lblCart, BorderLayout.WEST);
        panel.add(cartHeader, BorderLayout.NORTH);

        // Scrollable list of cart rows
        cartListPanel = new JPanel();
        cartListPanel.setLayout(new BoxLayout(cartListPanel, BoxLayout.Y_AXIS));
        cartListPanel.setBackground(UIConstants.BG_WHITE);

        cartScroll = new JScrollPane(cartListPanel);
        cartScroll.setBorder(null);
        cartScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        cartScroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(cartScroll, BorderLayout.CENTER);

        return panel;
    }

    // ── SOUTH: Footer ─────────────────────────────────────────────────────────

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(UIConstants.BG_WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR),
            new EmptyBorder(12, 24, 12, 24)
        ));
        footer.setPreferredSize(new Dimension(0, 64));

        // Total label (left)
        lblTotal = new JLabel("Tổng: 0 ₫");
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTotal.setForeground(UIConstants.TEXT_PRIMARY);
        footer.add(lblTotal, BorderLayout.WEST);

        // Send button (right) — disabled until cart has items
        btnSend = new RoundedButton("🛒  Gửi order");
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSend.setPreferredSize(new Dimension(160, UIConstants.BTN_HEIGHT + 4));
        btnSend.setEnabled(false);
        btnSend.addActionListener(this::onSendOrder);

        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnWrap.setBackground(UIConstants.BG_WHITE);
        btnWrap.add(btnSend);
        footer.add(btnWrap, BorderLayout.EAST);

        return footer;
    }

    // ─── Load menu via SwingWorker ────────────────────────────────────────────

    private void loadMenu() {
        SwingWorker<List<MenuItem>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<MenuItem> doInBackground() {
                return new MenuItemDAO().getAll();
            }

            @Override
            protected void done() {
                try {
                    List<MenuItem> items = get();
                    populateMenu(items);
                } catch (Exception ex) {
                    System.err.println("[TableOrderFrame] loadMenu lỗi: " + ex.getMessage());
                    tabbedMenu.removeAll();
                    JLabel err = new JLabel("Không tải được thực đơn", SwingConstants.CENTER);
                    err.setFont(UIConstants.FONT_BODY);
                    err.setForeground(UIConstants.DANGER);
                    tabbedMenu.addTab("Lỗi", err);
                }
            }
        };
        worker.execute();
    }

    /** Nhóm items theo category rồi tạo tab cho mỗi nhóm. */
    private void populateMenu(List<MenuItem> items) {
        tabbedMenu.removeAll();

        // Group by category, preserve insertion order
        LinkedHashMap<String, List<MenuItem>> byCategory = new LinkedHashMap<>();
        for (MenuItem item : items) {
            String cat = item.getCategory() != null ? item.getCategory() : "Khác";
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(item);
        }

        if (byCategory.isEmpty()) {
            JLabel empty = new JLabel("Chưa có món ăn nào", SwingConstants.CENTER);
            empty.setFont(UIConstants.FONT_BODY);
            empty.setForeground(UIConstants.TEXT_SECONDARY);
            tabbedMenu.addTab("  —  ", empty);
            return;
        }

        for (Map.Entry<String, List<MenuItem>> entry : byCategory.entrySet()) {
            JScrollPane tabScroll = buildCategoryTab(entry.getValue());
            tabbedMenu.addTab("  " + entry.getKey() + "  ", tabScroll);
        }
    }

    /** Tạo JScrollPane chứa grid 3 cột các card món ăn cho 1 category. */
    private JScrollPane buildCategoryTab(List<MenuItem> items) {
        JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
        grid.setBackground(UIConstants.BG_PAGE);
        grid.setBorder(new EmptyBorder(16, 16, 16, 16));

        for (MenuItem item : items) {
            grid.add(buildMenuCard(item));
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.setBackground(UIConstants.BG_PAGE);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    /** Card đơn: tên món, giá, nút "+". */
    private JPanel buildMenuCard(MenuItem item) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.BG_WHITE);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 12, 12));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout(0, 6));
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        card.setPreferredSize(new Dimension(0, 110));

        // Top: name + price
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel lblName = new JLabel(item.getName());
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblName.setForeground(UIConstants.TEXT_PRIMARY);
        lblName.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblPrice = new JLabel(formatPrice(item.getPrice()) + " ₫");
        lblPrice.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblPrice.setForeground(UIConstants.TEXT_SECONDARY);
        lblPrice.setAlignmentX(Component.LEFT_ALIGNMENT);

        info.add(lblName);
        info.add(Box.createVerticalStrut(4));
        info.add(lblPrice);
        card.add(info, BorderLayout.CENTER);

        // Bottom-right: "+" button
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnRow.setOpaque(false);

        RoundedButton btnAdd = new RoundedButton("+");
        btnAdd.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnAdd.setPreferredSize(new Dimension(40, 34));
        btnAdd.setToolTipText("Thêm vào giỏ");
        btnAdd.addActionListener(e -> addToCart(item));
        btnRow.add(btnAdd);
        card.add(btnRow, BorderLayout.SOUTH);

        return card;
    }

    // ─── Cart operations ──────────────────────────────────────────────────────

    private void addToCart(MenuItem item) {
        Optional<CartItem> existing = cartItems.stream()
            .filter(c -> c.menuItemId.equals(item.getId()))
            .findFirst();

        if (existing.isPresent()) {
            existing.get().quantity++;
        } else {
            cartItems.add(new CartItem(item.getId(), item.getName(), item.getPrice()));
        }
        updateCart();
    }

    private void removeFromCart(CartItem ci) {
        cartItems.remove(ci);
        updateCart();
    }

    private void changeQty(CartItem ci, int delta) {
        ci.quantity += delta;
        if (ci.quantity <= 0) cartItems.remove(ci);
        updateCart();
    }

    /** Xây lại toàn bộ danh sách cart và cập nhật tổng tiền + trạng thái nút. */
    private void updateCart() {
        cartListPanel.removeAll();

        if (cartItems.isEmpty()) {
            JLabel empty = new JLabel("Chưa có món nào", SwingConstants.CENTER);
            empty.setFont(UIConstants.FONT_BODY);
            empty.setForeground(UIConstants.TEXT_SECONDARY);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            cartListPanel.add(Box.createVerticalStrut(32));
            cartListPanel.add(empty);
        } else {
            for (CartItem ci : cartItems) {
                cartListPanel.add(buildCartRow(ci));
                cartListPanel.add(buildDivider());
            }
        }

        // Trailing spacer
        cartListPanel.add(Box.createVerticalGlue());

        // Update total
        double total = cartItems.stream().mapToDouble(CartItem::subtotal).sum();
        lblTotal.setText("Tổng: " + formatPrice(total) + " ₫");

        // Enable/disable send button
        btnSend.setEnabled(!cartItems.isEmpty());

        cartListPanel.revalidate();
        cartListPanel.repaint();
    }

    /** Một dòng trong giỏ hàng: tên | [-] qty [+] | subtotal | [X]. */
    private JPanel buildCartRow(CartItem ci) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(UIConstants.BG_WHITE);
        row.setBorder(new EmptyBorder(10, 20, 10, 16));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

        // Name
        JLabel lblName = new JLabel(ci.name);
        lblName.setFont(UIConstants.FONT_BODY);
        lblName.setForeground(UIConstants.TEXT_PRIMARY);
        row.add(lblName, BorderLayout.WEST);

        // Center: qty controls + subtotal
        JPanel center = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        center.setBackground(UIConstants.BG_WHITE);

        JButton btnMinus = smallQtyBtn("−");
        btnMinus.addActionListener(e -> changeQty(ci, -1));

        JLabel lblQty = new JLabel(String.valueOf(ci.quantity), SwingConstants.CENTER);
        lblQty.setFont(UIConstants.FONT_BOLD);
        lblQty.setPreferredSize(new Dimension(28, 28));

        JButton btnPlus = smallQtyBtn("+");
        btnPlus.addActionListener(e -> changeQty(ci, 1));

        JLabel lblSub = new JLabel(formatPrice(ci.subtotal()) + " ₫");
        lblSub.setFont(UIConstants.FONT_BODY);
        lblSub.setForeground(UIConstants.TEXT_SECONDARY);
        lblSub.setPreferredSize(new Dimension(90, 28));

        center.add(btnMinus);
        center.add(lblQty);
        center.add(btnPlus);
        center.add(Box.createHorizontalStrut(8));
        center.add(lblSub);
        row.add(center, BorderLayout.CENTER);

        // X remove button
        JButton btnRemove = new JButton("✕");
        btnRemove.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnRemove.setForeground(UIConstants.DANGER);
        btnRemove.setBackground(UIConstants.DANGER_LIGHT);
        btnRemove.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        btnRemove.setFocusPainted(false);
        btnRemove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRemove.addActionListener(e -> removeFromCart(ci));
        row.add(btnRemove, BorderLayout.EAST);

        return row;
    }

    /** Nút nhỏ "-" / "+" dùng trong cart row. */
    private JButton smallQtyBtn(String label) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setPreferredSize(new Dimension(30, 30));
        btn.setBackground(UIConstants.BG_PAGE);
        btn.setForeground(UIConstants.TEXT_PRIMARY);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JSeparator buildDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(UIConstants.BORDER_COLOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    // ─── Send order ───────────────────────────────────────────────────────────

    private void onSendOrder(ActionEvent e) {
        // TODO Phase 3B: gọi OrderDAO để lưu cart xuống DB
        System.out.println("[TableOrderFrame] TODO: gửi order");
        System.out.println("  tableId  = " + tableId);
        System.out.println("  orderId  = " + orderId);
        System.out.println("  Cart items:");
        for (CartItem ci : cartItems) {
            System.out.printf("    [%s] %s × %d = %.0f₫%n",
                ci.menuItemId, ci.name, ci.quantity, ci.subtotal());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String formatPrice(double amount) {
        return PRICE_FMT.format((long) amount);
    }
}