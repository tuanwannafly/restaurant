package com.restaurant.ui;

import com.restaurant.dao.MenuItemDAO;
import com.restaurant.dao.OrderDAO;
import com.restaurant.model.MenuItem;
import com.restaurant.model.Order;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * TableOrderFrame — Phase 3B (redesigned with CardLayout)
 *
 * JFrame fullscreen giả lập màn hình tablet tại bàn ăn.
 * Dùng CardLayout với 5 card:
 *   "menu"    — Màn hình chọn món (search, filter, grid cards)
 *   "cart"    — Màn hình giỏ hàng (JTable, số lượng, ghi chú, gửi order)
 *   "status"  — Trạng thái món (Phase 1A skeleton)
 *   "payment" — Thanh toán (Phase 1A skeleton)
 *   "waiting" — Chờ xử lý (Phase 1A skeleton)
 */
public class TableOrderFrame extends JFrame {

    // ─── Card names ───────────────────────────────────────────────────────────
    private static final String CARD_MENU = "menu";
    private static final String CARD_CART = "cart";
    // PHASE 1A — 3 card mới
    private static final String CARD_STATUS  = "status";
    private static final String CARD_PAYMENT = "payment";
    private static final String CARD_WAITING = "waiting";

    // ─── Layout ───────────────────────────────────────────────────────────────
    private CardLayout cardLayout;
    private JPanel     cardPanel;

    // ─── State ────────────────────────────────────────────────────────────────
    private final String tableId;
    private final String orderId;
    private final String tableName;
    private final String restaurantName;

    private int currentRound = 1;

    private final OrderDAO orderDAO = new OrderDAO();

    // ─── Menu screen UI ───────────────────────────────────────────────────────
    private JTextField tfSearch;
    private JPanel     menuGridPanel;
    private JLabel     lblSubtotal;
    private JButton    btnShowCart;

    private List<MenuItem> allMenuItems  = new ArrayList<>();
    private List<MenuItem> filteredItems = new ArrayList<>();
    private String selectedCategory      = "Tất cả";
    private final List<JButton> categoryButtons = new ArrayList<>();

    // ─── Cart screen UI ───────────────────────────────────────────────────────
    private DefaultTableModel cartTableModel;
    private JTable            cartTable;
    private JLabel            lblCartTotal;

    // ─── Cart data ────────────────────────────────────────────────────────────
    private final List<CartItem> cartItems = new ArrayList<>();

    // ─── Formatting ──────────────────────────────────────────────────────────
    private static final NumberFormat PRICE_FMT = NumberFormat.getInstance(Locale.of("vi", "VN"));
    private static final String[]     CART_COLS = {
            "STT", "Tên món", "Đơn giá (đ)", "Thành tiền (đ)", "Ghi chú", "Số lượng"
    };

    // ─── Inner class CartItem ────────────────────────────────────────────────
    private static class CartItem {
        final String menuItemId;
        final String name;
        final double unitPrice;
        int    quantity;
        String note;

        CartItem(String menuItemId, String name, double unitPrice) {
            this.menuItemId = menuItemId;
            this.name       = name;
            this.unitPrice  = unitPrice;
            this.quantity   = 1;
            this.note       = "";
        }

        double subtotal() { return unitPrice * quantity; }
    }

    // ─── Constructor ──────────────────────────────────────────────────────────

    public TableOrderFrame(String tableId, String orderId, String tableName) {
        this.tableId        = tableId;
        this.orderId        = orderId;
        this.tableName      = tableName;
        this.restaurantName = loadRestaurantName();

        setTitle("Bàn " + tableName);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1024, 768));

        initCardLayout();
        setupWindowLifecycle();
        setVisible(true);
        loadMenu();
    }

    // ─── Root layout ─────────────────────────────────────────────────────────

    private void initCardLayout() {
        cardLayout = new CardLayout();
        cardPanel  = new JPanel(cardLayout);
        cardPanel.setBackground(UIConstants.BG_PAGE);

        cardPanel.add(buildMenuCard(), CARD_MENU);
        cardPanel.add(buildCartCard(), CARD_CART);
        // PHASE 1A — đăng ký 3 card skeleton mới
        cardPanel.add(buildStatusCard(),  CARD_STATUS);
        cardPanel.add(buildPaymentCard(), CARD_PAYMENT);
        cardPanel.add(buildWaitingCard(), CARD_WAITING);

        setContentPane(cardPanel);
        navigateTo(CARD_MENU);
    }

    // PHASE 1A — Navigate helper (dùng cho các phase sau)
    private void navigateTo(String card) {
        cardLayout.show(cardPanel, card);
    }

    // ─── PHASE 1A: Placeholder builder ───────────────────────────────────────

    private JPanel buildPlaceholder(String name) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UIConstants.BG_PAGE);
        JLabel lbl = new JLabel("Card: " + name + " — đang phát triển", SwingConstants.CENTER);
        lbl.setFont(UIConstants.FONT_TITLE);
        lbl.setForeground(UIConstants.TEXT_SECONDARY);
        p.add(lbl, BorderLayout.CENTER);
        return p;
    }

    // PHASE 1A — 3 card skeleton builders
    private JPanel buildStatusCard()  { return buildPlaceholder("status"); }
    private JPanel buildPaymentCard() { return buildPlaceholder("payment"); }
    private JPanel buildWaitingCard() { return buildPlaceholder("waiting"); }

    // ═════════════════════════════════════════════════════════════════════════
    // CARD 1 — MÀN HÌNH CHỌN MÓN
    // ═════════════════════════════════════════════════════════════════════════

    private JPanel buildMenuCard() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);

        panel.add(buildMenuHeader(),  BorderLayout.NORTH);
        panel.add(buildMenuCenter(),  BorderLayout.CENTER);
        panel.add(buildMenuFooter(),  BorderLayout.SOUTH);

        return panel;
    }

    // ── MENU HEADER ──────────────────────────────────────────────────────────

    private JPanel buildMenuHeader() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setPreferredSize(new Dimension(0, 56));
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                new EmptyBorder(0, 24, 0, 24)));

        // LEFT — logo + system name
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JLabel logo = new JLabel("⛁");
        logo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        logo.setForeground(UIConstants.PRIMARY);
        JLabel sysName = new JLabel("SmartRestaurant");
        sysName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sysName.setForeground(UIConstants.PRIMARY);
        left.add(logo);
        left.add(sysName);

        // CENTER — table badge (rounded pill, PRIMARY background)
        JLabel tableBadge = new JLabel("Bàn " + tableName, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.PRIMARY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tableBadge.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tableBadge.setForeground(Color.WHITE);
        tableBadge.setOpaque(false);
        tableBadge.setPreferredSize(new Dimension(110, 34));

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 11));
        center.setOpaque(false);
        center.add(tableBadge);

        // RIGHT — globe icon + restaurant name + logout button
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JLabel globeIcon = new JLabel("🌐");
        globeIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        JLabel lblRestaurant = new JLabel(restaurantName);
        lblRestaurant.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblRestaurant.setForeground(UIConstants.PRIMARY);

        JButton btnLogout = new JButton("⏻");
        btnLogout.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        btnLogout.setForeground(UIConstants.TEXT_SECONDARY);
        btnLogout.setBorderPainted(false);
        btnLogout.setContentAreaFilled(false);
        btnLogout.setFocusPainted(false);
        btnLogout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogout.setToolTipText("Đóng màn hình bàn");
        btnLogout.addActionListener(e -> {
            PollManager.getInstance().unregister("tableorder_" + tableId);
            dispose();
        });

        right.add(globeIcon);
        right.add(lblRestaurant);
        right.add(Box.createHorizontalStrut(4));
        right.add(btnLogout);

        bar.add(left,   BorderLayout.WEST);
        bar.add(center, BorderLayout.CENTER);
        bar.add(right,  BorderLayout.EAST);
        return bar;
    }

    // ── MENU CENTER ───────────────────────────────────────────────────────────

    private JScrollPane buildMenuCenter() {
        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBackground(UIConstants.BG_PAGE);

        // Sub-NORTH: search bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 12));
        searchBar.setBackground(UIConstants.BG_PAGE);
        searchBar.setBorder(new EmptyBorder(8, 16, 0, 16));

        tfSearch = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tfSearch.setFont(UIConstants.FONT_BODY);
        tfSearch.setOpaque(false);
        tfSearch.setPreferredSize(new Dimension(400, 38));
        tfSearch.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
                new EmptyBorder(4, 14, 4, 14)));
        setPlaceholder(tfSearch, "🔍  Tìm kiếm món ăn...");

        tfSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { filterMenu(); }
            @Override public void removeUpdate(DocumentEvent e)  { filterMenu(); }
            @Override public void changedUpdate(DocumentEvent e) { filterMenu(); }
        });

        searchBar.add(tfSearch);

        // Sub-CENTER: category filter + grid
        JPanel centerContent = new JPanel(new BorderLayout(0, 0));
        centerContent.setBackground(UIConstants.BG_PAGE);

        centerContent.add(buildCategoryFilterBar(), BorderLayout.NORTH);

        menuGridPanel = new JPanel(new GridLayout(0, 6, 12, 12));
        menuGridPanel.setBackground(UIConstants.BG_PAGE);
        menuGridPanel.setBorder(new EmptyBorder(12, 20, 20, 20));

        JLabel loading = new JLabel("Đang tải thực đơn…", SwingConstants.CENTER);
        loading.setFont(UIConstants.FONT_BODY);
        loading.setForeground(UIConstants.TEXT_SECONDARY);
        menuGridPanel.add(loading);

        centerContent.add(menuGridPanel, BorderLayout.CENTER);

        content.add(searchBar,     BorderLayout.NORTH);
        content.add(centerContent, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setBackground(UIConstants.BG_PAGE);
        scroll.getViewport().setBackground(UIConstants.BG_PAGE);
        return scroll;
    }

    private JPanel buildCategoryFilterBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        bar.setBackground(UIConstants.BG_PAGE);
        bar.setBorder(new EmptyBorder(4, 20, 0, 20));
        JButton btnAll = buildCategoryButton("Tất cả", true);
        categoryButtons.add(btnAll);
        bar.add(btnAll);
        return bar;
    }

    private void updateCategoryBar(List<String> categories) {
        Container centerContent = menuGridPanel.getParent();
        Component north = ((BorderLayout) centerContent.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        if (!(north instanceof JPanel filterBar)) return;

        filterBar.removeAll();
        categoryButtons.clear();

        List<String> all = new ArrayList<>();
        all.add("Tất cả");
        all.addAll(categories);

        for (String cat : all) {
            JButton btn = buildCategoryButton(cat, cat.equals(selectedCategory));
            categoryButtons.add(btn);
            filterBar.add(btn);

            btn.addActionListener(e -> {
                selectedCategory = cat;
                for (JButton b : categoryButtons) {
                    boolean active = b.getText().equals(selectedCategory);
                    b.setBackground(active ? UIConstants.PRIMARY : Color.WHITE);
                    b.setForeground(active ? Color.WHITE : UIConstants.PRIMARY);
                }
                filterMenu();
            });
        }

        filterBar.revalidate();
        filterBar.repaint();
    }

    private JButton buildCategoryButton(String text, boolean active) {
        JButton btn = new JButton(text);
        btn.setFont(UIConstants.FONT_BODY);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(Math.max(80, text.length() * 9 + 28), 32));
        btn.setBorder(BorderFactory.createLineBorder(UIConstants.PRIMARY, 1, true));
        btn.setBackground(active ? UIConstants.PRIMARY : Color.WHITE);
        btn.setForeground(active ? Color.WHITE : UIConstants.PRIMARY);
        return btn;
    }

    // ── MENU FOOTER ───────────────────────────────────────────────────────────

    private JPanel buildMenuFooter() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setPreferredSize(new Dimension(0, 56));
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR),
                new EmptyBorder(0, 24, 0, 24)));

        lblSubtotal = new JLabel("Tạm tính: 0 đ");
        lblSubtotal.setFont(UIConstants.FONT_BODY);
        lblSubtotal.setForeground(UIConstants.TEXT_PRIMARY);

        btnShowCart = new RoundedButton("🛒  Giỏ hàng (0 món)");
        btnShowCart.setPreferredSize(new Dimension(200, UIConstants.BTN_HEIGHT + 4));
        btnShowCart.addActionListener(e -> showCart());

        bar.add(lblSubtotal, BorderLayout.WEST);
        bar.add(btnShowCart, BorderLayout.EAST);
        return bar;
    }

    // ── MENU GRID ─────────────────────────────────────────────────────────────

    private void rebuildMenuGrid() {
        menuGridPanel.removeAll();
        int cols = Math.max(3, Math.min(6, getWidth() / 180));
        menuGridPanel.setLayout(new GridLayout(0, cols, 12, 12));

        if (filteredItems.isEmpty()) {
            JLabel empty = new JLabel("Không tìm thấy món phù hợp", SwingConstants.CENTER);
            empty.setFont(UIConstants.FONT_BODY);
            empty.setForeground(UIConstants.TEXT_SECONDARY);
            menuGridPanel.add(empty);
        } else {
            for (MenuItem item : filteredItems) {
                menuGridPanel.add(buildMenuItemCard(item));
            }
        }
        menuGridPanel.revalidate();
        menuGridPanel.repaint();
    }

    private JPanel buildMenuItemCard(MenuItem item) {
        JPanel card = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(0, 0, 10, 0));
        card.setPreferredSize(new Dimension(0, 180));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Image placeholder (120x120)
        JPanel imgPlaceholder = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xF3F4F6));
                g2.fillRoundRect(0, 0, getWidth(), getHeight() + 12, 12, 12);
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 36));
                g2.setColor(new Color(0xD1D5DB));
                String emoji = pickFoodEmoji(item.getCategory());
                FontMetrics fm = g2.getFontMetrics();
                int ex = (getWidth()  - fm.stringWidth(emoji)) / 2;
                int ey = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.drawString(emoji, ex, ey);
                g2.dispose();
            }
        };
        imgPlaceholder.setOpaque(false);
        imgPlaceholder.setPreferredSize(new Dimension(0, 100));

        // Info section
        JPanel info = new JPanel(new BorderLayout(0, 2));
        info.setOpaque(false);
        info.setBorder(new EmptyBorder(6, 10, 0, 10));

        JLabel lblName = new JLabel(item.getName());
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblName.setForeground(UIConstants.TEXT_PRIMARY);

        JLabel lblPrice = new JLabel(formatPrice(item.getPrice()) + " đ");
        lblPrice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblPrice.setForeground(UIConstants.TEXT_SECONDARY);

        // "+" add-to-cart button
        JButton btnAdd = new JButton("+") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hover = getModel().isRollover();
                g2.setColor(hover ? UIConstants.PRIMARY_DARK : UIConstants.PRIMARY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btnAdd.setFont(new Font("Segoe UI", Font.BOLD, 18));
        btnAdd.setForeground(Color.WHITE);
        btnAdd.setOpaque(false);
        btnAdd.setContentAreaFilled(false);
        btnAdd.setBorderPainted(false);
        btnAdd.setFocusPainted(false);
        btnAdd.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAdd.setPreferredSize(new Dimension(32, 32));
        btnAdd.addActionListener(e -> addToCart(item));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
        btnRow.setOpaque(false);
        btnRow.add(btnAdd);

        info.add(lblName,  BorderLayout.NORTH);
        info.add(lblPrice, BorderLayout.CENTER);
        info.add(btnRow,   BorderLayout.SOUTH);

        card.add(imgPlaceholder, BorderLayout.CENTER);
        card.add(info,           BorderLayout.SOUTH);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { addToCart(item); }
        });

        return card;
    }

    private String pickFoodEmoji(String category) {
        if (category == null) return "🍽";
        String c = category.toLowerCase();
        if (c.contains("uống") || c.contains("drink"))   return "🥤";
        if (c.contains("tráng") || c.contains("dessert")) return "🍮";
        if (c.contains("hải sản") || c.contains("seafood")) return "🦐";
        if (c.contains("thịt") || c.contains("meat"))    return "🥩";
        if (c.contains("cơm") || c.contains("rice"))     return "🍚";
        if (c.contains("phở") || c.contains("soup"))     return "🍜";
        if (c.contains("gà") || c.contains("chicken"))   return "🍗";
        return "🍽";
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CARD 2 — MÀN HÌNH GIỎ HÀNG
    // ═════════════════════════════════════════════════════════════════════════

    private JPanel buildCartCard() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);

        panel.add(buildCartHeader(),  BorderLayout.NORTH);
        panel.add(buildCartCenter(),  BorderLayout.CENTER);
        panel.add(buildCartFooter(),  BorderLayout.SOUTH);

        return panel;
    }

    // ── CART HEADER ───────────────────────────────────────────────────────────

    private JPanel buildCartHeader() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setPreferredSize(new Dimension(0, 56));
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                new EmptyBorder(0, 24, 0, 24)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JLabel logo = new JLabel("⛁");
        logo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        logo.setForeground(UIConstants.PRIMARY);
        JLabel sysName = new JLabel("SmartRestaurant");
        sysName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sysName.setForeground(UIConstants.PRIMARY);
        left.add(logo);
        left.add(sysName);

        JLabel title = new JLabel("🛒  Giỏ hàng của bạn", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(UIConstants.TEXT_PRIMARY);

        JLabel tableBadge = new JLabel("Bàn " + tableName, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.PRIMARY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tableBadge.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tableBadge.setForeground(Color.WHITE);
        tableBadge.setOpaque(false);
        tableBadge.setPreferredSize(new Dimension(90, 32));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 12));
        right.setOpaque(false);
        right.add(tableBadge);

        bar.add(left,  BorderLayout.WEST);
        bar.add(title, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ── CART CENTER ───────────────────────────────────────────────────────────

    private JScrollPane buildCartCenter() {
        cartTableModel = new DefaultTableModel(CART_COLS, 0) {
            @Override public boolean isCellEditable(int row, int col) {
                return col == 4 || col == 5; // Ghi chú + Số lượng editable
            }
        };

        cartTable = new JTable(cartTableModel);
        cartTable.setFont(UIConstants.FONT_BODY);
        cartTable.setRowHeight(50);
        cartTable.setShowGrid(false);
        cartTable.setIntercellSpacing(new Dimension(0, 1));
        cartTable.setSelectionBackground(UIConstants.ROW_SELECTED);
        cartTable.setFillsViewportHeight(true);

        JTableHeader header = cartTable.getTableHeader();
        header.setFont(UIConstants.FONT_BOLD);
        header.setBackground(UIConstants.HEADER_BG);
        header.setForeground(UIConstants.TEXT_PRIMARY);
        header.setPreferredSize(new Dimension(0, 38));
        header.setReorderingAllowed(false);
        ((DefaultTableCellRenderer) header.getDefaultRenderer())
                .setHorizontalAlignment(SwingConstants.CENTER);

        setColWidth(cartTable, 0, 50,  50);
        setColWidth(cartTable, 1, 200, 300);
        setColWidth(cartTable, 2, 120, 150);
        setColWidth(cartTable, 3, 130, 160);
        setColWidth(cartTable, 4, 160, 250);
        setColWidth(cartTable, 5, 140, 180);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int c : new int[]{0, 2, 3}) {
            cartTable.getColumnModel().getColumn(c).setCellRenderer(centerRenderer);
        }

        cartTable.getColumnModel().getColumn(4).setCellRenderer(new NoteRenderer());
        cartTable.getColumnModel().getColumn(4).setCellEditor(new NoteEditor(cartItems));

        cartTable.getColumnModel().getColumn(5).setCellRenderer(new QtyRenderer());
        cartTable.getColumnModel().getColumn(5).setCellEditor(
                new QtyEditor(cartItems, this::refreshCartTable));

        JScrollPane scroll = new JScrollPane(cartTable);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR));
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    // ── CART FOOTER ───────────────────────────────────────────────────────────

    private JPanel buildCartFooter() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setPreferredSize(new Dimension(0, 60));
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR),
                new EmptyBorder(0, 24, 0, 24)));

        lblCartTotal = new JLabel("Tổng cộng: 0 đ");
        lblCartTotal.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblCartTotal.setForeground(UIConstants.TEXT_PRIMARY);

        JButton btnBack = new JButton("← Tiếp tục chọn món");
        btnBack.setFont(UIConstants.FONT_BODY);
        btnBack.setForeground(UIConstants.PRIMARY);
        btnBack.setBorderPainted(false);
        btnBack.setContentAreaFilled(false);
        btnBack.setFocusPainted(false);
        btnBack.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnBack.addActionListener(e -> showMenu());

        RoundedButton btnSend = new RoundedButton("✅  Gửi món");
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSend.setPreferredSize(new Dimension(160, UIConstants.BTN_HEIGHT + 6));
        btnSend.addActionListener(e -> sendOrder());

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        rightPanel.setOpaque(false);
        rightPanel.add(btnBack);
        rightPanel.add(btnSend);

        bar.add(lblCartTotal, BorderLayout.WEST);
        bar.add(rightPanel,   BorderLayout.EAST);
        return bar;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DATA & LOGIC
    // ═════════════════════════════════════════════════════════════════════════

    private void loadMenu() {
        new SwingWorker<List<MenuItem>, Void>() {
            @Override
            protected List<MenuItem> doInBackground() {
                return new MenuItemDAO().getAll();
            }

            @Override
            protected void done() {
                try {
                    allMenuItems  = get();
                    filteredItems = new ArrayList<>(allMenuItems);

                    List<String> categories = allMenuItems.stream()
                            .map(MenuItem::getCategory)
                            .filter(Objects::nonNull)
                            .distinct()
                            .sorted()
                            .collect(Collectors.toList());

                    updateCategoryBar(categories);
                    rebuildMenuGrid();

                } catch (InterruptedException | ExecutionException ex) {
                    menuGridPanel.removeAll();
                    JLabel err = new JLabel("Không tải được thực đơn", SwingConstants.CENTER);
                    err.setFont(UIConstants.FONT_BODY);
                    err.setForeground(UIConstants.DANGER);
                    menuGridPanel.add(err);
                    menuGridPanel.revalidate();
                }
            }
        }.execute();
    }

    private void filterMenu() {
        String query = tfSearch.getText().trim().toLowerCase();
        // Skip if placeholder text is still showing
        if (query.equals("🔍  tìm kiếm món ăn...")) query = "";

        final String q = query;
        filteredItems = allMenuItems.stream()
                .filter(m -> {
                    boolean catMatch = "Tất cả".equals(selectedCategory)
                            || selectedCategory.equals(m.getCategory());
                    boolean nameMatch = q.isEmpty()
                            || m.getName().toLowerCase().contains(q);
                    return catMatch && nameMatch;
                })
                .collect(Collectors.toList());

        rebuildMenuGrid();
    }

    // ── Cart operations ───────────────────────────────────────────────────────

    private void addToCart(MenuItem item) {
        cartItems.stream()
                .filter(c -> c.menuItemId.equals(item.getId()))
                .findFirst()
                .ifPresentOrElse(
                        c -> c.quantity++,
                        () -> cartItems.add(new CartItem(item.getId(), item.getName(), item.getPrice()))
                );
        refreshCartSummary();
        int total = cartItems.stream().mapToInt(c -> c.quantity).sum();
        btnShowCart.setText("🛒  Giỏ hàng (" + total + " món)");
    }

    private void refreshCartSummary() {
        double subtotal = cartItems.stream().mapToDouble(CartItem::subtotal).sum();
        int    count    = cartItems.stream().mapToInt(c -> c.quantity).sum();
        lblSubtotal.setText("Tạm tính: " + formatPrice(subtotal) + " đ");
        btnShowCart.setText("🛒  Giỏ hàng (" + count + " món)");
    }

    private void refreshCartTable() {
        if (cartTableModel == null) return;
        cartTableModel.setRowCount(0);

        int stt = 1;
        for (CartItem ci : cartItems) {
            cartTableModel.addRow(new Object[]{
                    stt++,
                    ci.name,
                    formatPrice(ci.unitPrice),
                    formatPrice(ci.subtotal()),
                    ci.note.isEmpty() ? "" : ci.note,
                    ci.quantity
            });
        }

        double total = cartItems.stream().mapToDouble(CartItem::subtotal).sum();
        if (lblCartTotal != null) {
            lblCartTotal.setText("Tổng cộng: " + formatPrice(total) + " đ");
        }
        refreshCartSummary();
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void showCart() {
        if (cartItems.isEmpty()) {
            ToastNotification.show(this, "Giỏ hàng đang trống, hãy thêm món!", ToastNotification.Type.INFO);
            return;
        }
        refreshCartTable();
        navigateTo(CARD_CART);
    }

    private void showMenu() {
        navigateTo(CARD_MENU);
    }

    // ── Send order ────────────────────────────────────────────────────────────

    private void sendOrder() {
        if (cartItems.isEmpty()) {
            ToastNotification.show(this, "Giỏ hàng trống!", ToastNotification.Type.INFO);
            return;
        }

        if (cartTable.isEditing()) {
            cartTable.getCellEditor().stopCellEditing();
        }

        List<Order.OrderItem> orderItems = new ArrayList<>();
        for (CartItem ci : cartItems) {
            orderItems.add(new Order.OrderItem(ci.menuItemId, ci.name, ci.quantity, ci.unitPrice));
        }
        final int round = currentRound;

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return orderDAO.addOrderItems(orderId, orderItems, round);
            }

            @Override
            protected void done() {
                boolean ok = false;
                try { ok = get(); } catch (InterruptedException | ExecutionException ex) {
                    System.err.println("[TableOrderFrame] sendOrder lỗi: " + ex.getMessage());
                }

                if (ok) {
                    currentRound++;
                    cartItems.clear();
                    refreshCartTable();
                    showMenu();
                    ToastNotification.show(TableOrderFrame.this,
                            "✅  Đã gửi order! Bếp đang xử lý.", ToastNotification.Type.SUCCESS);
                } else {
                    ToastNotification.show(TableOrderFrame.this,
                            "❌  Gửi order thất bại, vui lòng thử lại.", ToastNotification.Type.ERROR);
                }
            }
        }.execute();
    }

    // ── Window lifecycle ──────────────────────────────────────────────────────

    private void setupWindowLifecycle() {
        final String key = "tableorder_" + tableId;
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                PollManager.getInstance().unregister(key);
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CELL RENDERERS / EDITORS
    // ═════════════════════════════════════════════════════════════════════════

    /** Renderer cột Ghi chú — italic xám khi trống */
    private static class NoteRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, value, sel, foc, row, col);
            String text = value == null ? "" : value.toString();
            if (text.isEmpty()) {
                setText("không rau...");
                setForeground(new Color(0xBDBDBD));
                setFont(new Font("Segoe UI", Font.ITALIC, 12));
            } else {
                setText(text);
                setForeground(UIConstants.TEXT_PRIMARY);
                setFont(UIConstants.FONT_BODY);
            }
            setBorder(new EmptyBorder(0, 8, 0, 8));
            return this;
        }
    }

    /** Editor cột Ghi chú */
    private static class NoteEditor extends DefaultCellEditor {
        private final List<CartItem> items;
        private final JTextField     tf;
        private int editingRow;

        NoteEditor(List<CartItem> items) {
            super(new JTextField());
            this.items = items;
            this.tf    = (JTextField) getComponent();
            tf.setFont(UIConstants.FONT_BODY);
            tf.setBorder(new EmptyBorder(0, 8, 0, 8));
        }

        @Override
        public Component getTableCellEditorComponent(JTable t, Object value,
                boolean sel, int row, int col) {
            editingRow = row;
            tf.setText(value == null ? "" : value.toString());
            return tf;
        }

        @Override
        public Object getCellEditorValue() {
            if (editingRow >= 0 && editingRow < items.size()) {
                items.get(editingRow).note = tf.getText();
            }
            return tf.getText();
        }
    }

    /** Renderer cột Số lượng — [−] [N] [+] */
    private static class QtyRenderer extends JPanel implements TableCellRenderer {
        private final JLabel lblMinus = pill("−");
        private final JLabel lblQty   = new JLabel("1", SwingConstants.CENTER);
        private final JLabel lblPlus  = pill("+");

        QtyRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 6, 0));
            setOpaque(false);
            lblQty.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lblQty.setForeground(UIConstants.TEXT_PRIMARY);
            lblQty.setPreferredSize(new Dimension(32, 30));
            add(lblMinus);
            add(lblQty);
            add(lblPlus);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                boolean sel, boolean foc, int row, int col) {
            int qty = (value instanceof Integer) ? (Integer) value : 1;
            lblQty.setText(String.valueOf(qty));
            setBackground(sel ? UIConstants.ROW_SELECTED
                    : (row % 2 == 0 ? Color.WHITE : new Color(0xF9FAFB)));
            setOpaque(true);
            return this;
        }

        static JLabel pill(String text) {
            JLabel l = new JLabel(text, SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0xF3F4F6));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            l.setFont(new Font("Segoe UI", Font.BOLD, 16));
            l.setForeground(UIConstants.TEXT_PRIMARY);
            l.setOpaque(false);
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.setPreferredSize(new Dimension(30, 30));
            return l;
        }
    }

    /** Editor cột Số lượng — click [−]/[+] thực sự thay đổi quantity */
    private class QtyEditor extends AbstractCellEditor implements TableCellEditor {
        private final List<CartItem> items;
        private final Runnable       onRefresh;
        private final JPanel         panel;
        private final JLabel         lblMinus;
        private final JLabel         lblQty;
        private final JLabel         lblPlus;
        private int editingRow;

        QtyEditor(List<CartItem> items, Runnable onRefresh) {
            this.items     = items;
            this.onRefresh = onRefresh;

            lblMinus = QtyRenderer.pill("−");
            lblQty   = new JLabel("1", SwingConstants.CENTER);
            lblPlus  = QtyRenderer.pill("+");
            lblQty.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lblQty.setForeground(UIConstants.TEXT_PRIMARY);
            lblQty.setPreferredSize(new Dimension(32, 30));

            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
            panel.setBackground(Color.WHITE);
            panel.add(lblMinus);
            panel.add(lblQty);
            panel.add(lblPlus);

            lblMinus.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (editingRow < 0 || editingRow >= items.size()) return;
                    CartItem ci = items.get(editingRow);
                    ci.quantity--;
                    if (ci.quantity <= 0) {
                        items.remove(ci);
                        fireEditingStopped();
                        onRefresh.run();
                    } else {
                        lblQty.setText(String.valueOf(ci.quantity));
                        onRefresh.run();
                    }
                }
            });

            lblPlus.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (editingRow < 0 || editingRow >= items.size()) return;
                    CartItem ci = items.get(editingRow);
                    ci.quantity++;
                    lblQty.setText(String.valueOf(ci.quantity));
                    onRefresh.run();
                }
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable t, Object value,
                boolean sel, int row, int col) {
            editingRow = row;
            int qty = (row < items.size()) ? items.get(row).quantity : 1;
            lblQty.setText(String.valueOf(qty));
            return panel;
        }

        @Override public Object getCellEditorValue() {
            return editingRow < items.size() ? items.get(editingRow).quantity : 0;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═════════════════════════════════════════════════════════════════════════

    private static String formatPrice(double v) {
        return PRICE_FMT.format((long) v);
    }

    private static void setColWidth(JTable t, int col, int min, int pref) {
        TableColumn tc = t.getColumnModel().getColumn(col);
        tc.setMinWidth(min);
        tc.setPreferredWidth(pref);
    }

    private static void setPlaceholder(JTextField tf, String placeholder) {
        tf.setForeground(UIConstants.TEXT_SECONDARY);
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (tf.getText().equals(placeholder)) {
                    tf.setText("");
                    tf.setForeground(UIConstants.TEXT_PRIMARY);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (tf.getText().isEmpty()) {
                    tf.setText(placeholder);
                    tf.setForeground(UIConstants.TEXT_SECONDARY);
                }
            }
        });
        tf.setText(placeholder);
    }

    private String loadRestaurantName() {
        try {
            var r = com.restaurant.data.DataManager.getInstance().getMyRestaurant();
            return (r != null && r.getName() != null && !r.getName().isBlank())
                    ? r.getName() : "Nhà hàng";
        } catch (Exception e) {
            return "Nhà hàng";
        }
    }
}