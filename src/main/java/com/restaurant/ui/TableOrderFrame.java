package com.restaurant.ui;

import com.restaurant.dao.MenuItemDAO;
import com.restaurant.dao.OrderDAO;
import com.restaurant.model.MenuItem;
import com.restaurant.model.Order;
import com.restaurant.ui.dialog.PaymentDialog;
import com.restaurant.ui.dialog.ReportAddDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * TableOrderFrame — Phase 3B (full logic)
 *
 * <p>JFrame fullscreen giả lập màn hình tablet tại bàn ăn.
 * CENTER là JTabbedPane 3 tab:
 * <ol>
 *   <li>"Đặt món"     — JSplitPane menu + cart (Phase 3A)</li>
 *   <li>"Trạng thái"  — list item_status với badge màu, auto-refresh 5s</li>
 *   <li>"Báo cáo"     — form ReportAddDialog nhúng trực tiếp</li>
 * </ol>
 *
 * <p>Nút "Gửi order" gọi {@link OrderDAO#addOrderItems} với round hiện tại,
 * xoá giỏ hàng, tăng {@link #currentRound} và chuyển sang tab Trạng thái.
 */
public class TableOrderFrame extends JFrame {

    // ─── Index hằng số tab ────────────────────────────────────────────────────
    private static final int TAB_ORDER  = 0;
    private static final int TAB_STATUS = 1;
    private static final int TAB_REPORT = 2;

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final String   tableId;
    private final String   orderId;
    private final String   tableName;

    /** Số thứ tự lượt gọi món (tăng sau mỗi lần nhấn "Gửi order"). */
    private int currentRound = 1;

    // DAO
    private final OrderDAO orderDAO = new OrderDAO();

    // Outer tabs (Đặt món / Trạng thái / Báo cáo)
    private JTabbedPane mainTabs;

    // UI – header
    private JLabel lblTime;

    // UI – tab Đặt món
    private JTabbedPane tabbedMenu;   // tabs theo category
    private JPanel      cartListPanel;
    private JLabel      lblTotal;
    private RoundedButton btnSend;
    private RoundedButton btnPayment;
    private JScrollPane cartScroll;

    // UI – tab Trạng thái
    private JPanel   statusListPanel;
    private JLabel   lblStatusHint;

    // Auto-refresh timer (5s) – khởi động khi frame hiện, dừng khi đóng
    private javax.swing.Timer refreshTimer;

    // Cart state
    private final List<CartItem> cartItems = new ArrayList<>();

    // Formatting
    private static final NumberFormat    PRICE_FMT = NumberFormat.getInstance(Locale.of("vi", "VN"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Badge màu theo ItemStatus
    private static final Map<Order.OrderItem.ItemStatus, Color> BADGE_COLOR = new EnumMap<>(Order.OrderItem.ItemStatus.class);
    private static final Map<Order.OrderItem.ItemStatus, String> BADGE_LABEL = new EnumMap<>(Order.OrderItem.ItemStatus.class);
    static {
        BADGE_COLOR.put(Order.OrderItem.ItemStatus.PENDING,    new Color(0x95A5A6));
        BADGE_COLOR.put(Order.OrderItem.ItemStatus.ACCEPTED,   new Color(0x3498DB));
        BADGE_COLOR.put(Order.OrderItem.ItemStatus.COOKING,    new Color(0xE67E22));
        BADGE_COLOR.put(Order.OrderItem.ItemStatus.READY,      new Color(0x2ECC71));
        BADGE_COLOR.put(Order.OrderItem.ItemStatus.DELIVERING, new Color(0x9B59B6));
        BADGE_COLOR.put(Order.OrderItem.ItemStatus.DELIVERED,  new Color(0x1ABC9C));

        BADGE_LABEL.put(Order.OrderItem.ItemStatus.PENDING,    "Đang chờ");
        BADGE_LABEL.put(Order.OrderItem.ItemStatus.ACCEPTED,   "Đã tiếp nhận");
        BADGE_LABEL.put(Order.OrderItem.ItemStatus.COOKING,    "Đang nấu");
        BADGE_LABEL.put(Order.OrderItem.ItemStatus.READY,      "Sẵn sàng");
        BADGE_LABEL.put(Order.OrderItem.ItemStatus.DELIVERING, "Đang mang lên");
        BADGE_LABEL.put(Order.OrderItem.ItemStatus.DELIVERED,  "Đã giao");
    }

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

        add(buildHeader(),   BorderLayout.NORTH);
        add(buildMainTabs(), BorderLayout.CENTER);
        add(buildFooter(),   BorderLayout.SOUTH);

        startClock();
        setupWindowLifecycle();
        setVisible(true);
    }

    // ── Window lifecycle: start/stop refresh timer ────────────────────────────

    private void setupWindowLifecycle() {
        // Timer 5s cho tab Trạng thái
        refreshTimer = new javax.swing.Timer(5000, e -> refreshStatus());
        refreshTimer.setInitialDelay(5000);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                refreshTimer.start();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                refreshTimer.stop();
            }
        });
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
        Runnable tick = () -> {
            if (lblTime != null) lblTime.setText(LocalTime.now().format(TIME_FMT));
        };
        tick.run();
        new javax.swing.Timer(1000, e -> tick.run()).start();
    }

    // ── CENTER: Main tabs (Đặt món / Trạng thái / Báo cáo) ───────────────────

    private JTabbedPane buildMainTabs() {
        mainTabs = new JTabbedPane(JTabbedPane.TOP);
        mainTabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainTabs.setBackground(UIConstants.BG_PAGE);

        mainTabs.addTab("🍽  Đặt món",    buildOrderSplit());
        mainTabs.addTab("📋  Trạng thái", buildStatusPanel());
        mainTabs.addTab("📝  Báo cáo",    buildReportTab());

        // Khi chuyển sang tab Trạng thái, trigger refresh ngay
        mainTabs.addChangeListener(e -> {
            if (mainTabs.getSelectedIndex() == TAB_STATUS) {
                refreshStatus();
            }
        });

        return mainTabs;
    }

    // ── Tab 1: Đặt món — JSplitPane menu + cart ───────────────────────────────

    private JSplitPane buildOrderSplit() {
        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildMenuPanel(),
            buildCartPanel()
        );
        split.setBorder(null);
        split.setDividerSize(1);
        split.setBackground(UIConstants.BORDER_COLOR);
        split.setResizeWeight(0.60);

        SwingUtilities.invokeLater(() -> {
            int total = split.getWidth();
            if (total > 0) split.setDividerLocation((int)(total * 0.60));
        });
        return split;
    }

    // ── LEFT: Menu panel ──────────────────────────────────────────────────────

    private JPanel buildMenuPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);

        tabbedMenu = new JTabbedPane(JTabbedPane.TOP);
        tabbedMenu.setFont(UIConstants.FONT_BOLD);
        tabbedMenu.setBackground(UIConstants.BG_PAGE);

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

    // ── Tab 2: Trạng thái món ─────────────────────────────────────────────────

    private JPanel buildStatusPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UIConstants.BG_PAGE);

        // Header của tab
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BG_WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
            new EmptyBorder(12, 20, 12, 20)
        ));

        JLabel lblTitle = new JLabel("Trạng thái các món đã gọi");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTitle.setForeground(UIConstants.TEXT_PRIMARY);
        header.add(lblTitle, BorderLayout.WEST);

        // Nút refresh thủ công
        JButton btnRefresh = new JButton("↻  Làm mới");
        btnRefresh.setFont(UIConstants.FONT_BODY);
        btnRefresh.setForeground(UIConstants.PRIMARY);
        btnRefresh.setBackground(UIConstants.PRIMARY_LIGHT);
        btnRefresh.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btnRefresh.setFocusPainted(false);
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.addActionListener(e -> refreshStatus());
        header.add(btnRefresh, BorderLayout.EAST);
        wrapper.add(header, BorderLayout.NORTH);

        // Hint "tự động cập nhật mỗi 5 giây"
        lblStatusHint = new JLabel("  Tự động cập nhật mỗi 5 giây", SwingConstants.LEFT);
        lblStatusHint.setFont(UIConstants.FONT_SMALL);
        lblStatusHint.setForeground(UIConstants.TEXT_SECONDARY);
        lblStatusHint.setBorder(new EmptyBorder(4, 20, 4, 0));
        lblStatusHint.setBackground(UIConstants.BG_PAGE);
        lblStatusHint.setOpaque(true);
        wrapper.add(lblStatusHint, BorderLayout.SOUTH);

        // Scrollable list
        statusListPanel = new JPanel();
        statusListPanel.setLayout(new BoxLayout(statusListPanel, BoxLayout.Y_AXIS));
        statusListPanel.setBackground(UIConstants.BG_PAGE);

        JScrollPane scroll = new JScrollPane(statusListPanel);
        scroll.setBorder(null);
        scroll.setBackground(UIConstants.BG_PAGE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        wrapper.add(scroll, BorderLayout.CENTER);

        // Placeholder ban đầu
        showStatusPlaceholder("Nhấn 'Đặt món' và gửi order để xem trạng thái.");

        return wrapper;
    }

    /** Hiển thị placeholder khi chưa có món hoặc đang tải. */
    private void showStatusPlaceholder(String msg) {
        statusListPanel.removeAll();
        JLabel lbl = new JLabel(msg, SwingConstants.CENTER);
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_SECONDARY);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusListPanel.add(Box.createVerticalStrut(40));
        statusListPanel.add(lbl);
        statusListPanel.revalidate();
        statusListPanel.repaint();
    }

    // ── Tab 3: Báo cáo — nhúng ReportAddDialog ───────────────────────────────

    private JPanel buildReportTab() {
        // Tạo dialog với parent = null (embedded mode, không show)
        // ReportAddDialog đã được sửa null-safe với parent
        ReportAddDialog dialog = new ReportAddDialog(this, null);

        // Lấy content pane và wrap vào JPanel có scroll
        JPanel content = (JPanel) dialog.getContentPane();
        content.setBackground(UIConstants.BG_WHITE);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UIConstants.BG_WHITE);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
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

        lblTotal = new JLabel("Tổng: 0 ₫");
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTotal.setForeground(UIConstants.TEXT_PRIMARY);
        footer.add(lblTotal, BorderLayout.WEST);

        // "Gửi order" button
        btnSend = new RoundedButton("🛒  Gửi order");
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSend.setPreferredSize(new Dimension(160, UIConstants.BTN_HEIGHT + 4));
        btnSend.setEnabled(false);
        btnSend.addActionListener(this::onSendOrder);

        // "Thanh toán" button
        btnPayment = new RoundedButton("💳  Thanh toán");
        btnPayment.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnPayment.setPreferredSize(new Dimension(160, UIConstants.BTN_HEIGHT + 4));
        btnPayment.setBackground(new Color(0x10B981));   // SUCCESS green
        btnPayment.addActionListener(e -> openPaymentDialog());

        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnWrap.setBackground(UIConstants.BG_WHITE);
        btnWrap.add(btnSend);
        btnWrap.add(btnPayment);
        footer.add(btnWrap, BorderLayout.EAST);

        return footer;
    }

    /**
     * Mở {@link PaymentDialog} cho bàn hiện tại.
     * Frame sẽ dispose sau khi thanh toán hoàn tất.
     */
    private void openPaymentDialog() {
        PaymentDialog dlg = new PaymentDialog(
            null,       // parent Frame – TableOrderFrame là JFrame, dùng null để tạo dialog độc lập
            tableId,
            tableName,
            orderId
        );
        dlg.setVisible(true);   // blocks – APPLICATION_MODAL

        if (dlg.isPaymentCompleted()) {
            // Dừng timer và đóng frame tablet
            if (refreshTimer != null) refreshTimer.stop();
            dispose();
        }
    }

    // ─── Load menu via SwingWorker ────────────────────────────────────────────

    private void loadMenu() {
        new SwingWorker<List<MenuItem>, Void>() {
            @Override
            protected List<MenuItem> doInBackground() {
                return new MenuItemDAO().getAll();
            }

            @Override
            protected void done() {
                try {
                    populateMenu(get());
                } catch (Exception ex) {
                    System.err.println("[TableOrderFrame] loadMenu lỗi: " + ex.getMessage());
                    tabbedMenu.removeAll();
                    JLabel err = new JLabel("Không tải được thực đơn", SwingConstants.CENTER);
                    err.setFont(UIConstants.FONT_BODY);
                    err.setForeground(UIConstants.DANGER);
                    tabbedMenu.addTab("Lỗi", err);
                }
            }
        }.execute();
    }

    private void populateMenu(List<MenuItem> items) {
        tabbedMenu.removeAll();

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
            tabbedMenu.addTab("  " + entry.getKey() + "  ", buildCategoryTab(entry.getValue()));
        }
    }

    private JScrollPane buildCategoryTab(List<MenuItem> items) {
        JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
        grid.setBackground(UIConstants.BG_PAGE);
        grid.setBorder(new EmptyBorder(16, 16, 16, 16));
        for (MenuItem item : items) grid.add(buildMenuCard(item));

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.setBackground(UIConstants.BG_PAGE);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private JPanel buildMenuCard(MenuItem item) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.BG_WHITE);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth()-1, getHeight()-1, 12, 12));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout(0, 6));
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        card.setPreferredSize(new Dimension(0, 110));

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
        cartItems.stream()
            .filter(c -> c.menuItemId.equals(item.getId()))
            .findFirst()
            .ifPresentOrElse(
                c -> c.quantity++,
                () -> cartItems.add(new CartItem(item.getId(), item.getName(), item.getPrice()))
            );
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
        cartListPanel.add(Box.createVerticalGlue());

        double total = cartItems.stream().mapToDouble(CartItem::subtotal).sum();
        lblTotal.setText("Tổng: " + formatPrice(total) + " ₫");
        btnSend.setEnabled(!cartItems.isEmpty());

        cartListPanel.revalidate();
        cartListPanel.repaint();
    }

    private JPanel buildCartRow(CartItem ci) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(UIConstants.BG_WHITE);
        row.setBorder(new EmptyBorder(10, 20, 10, 16));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

        JLabel lblName = new JLabel(ci.name);
        lblName.setFont(UIConstants.FONT_BODY);
        lblName.setForeground(UIConstants.TEXT_PRIMARY);
        row.add(lblName, BorderLayout.WEST);

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

    // ─── Send order (Phase 3B) ────────────────────────────────────────────────

    /**
     * Gửi lượt order hiện tại xuống DB.
     * <ol>
     *   <li>Chuyển cartItems → List&lt;Order.OrderItem&gt;</li>
     *   <li>Gọi {@link OrderDAO#addOrderItems} với {@link #currentRound}</li>
     *   <li>Xoá giỏ hàng, tăng round, chuyển sang tab Trạng thái</li>
     * </ol>
     */
    private void onSendOrder(ActionEvent e) {
        if (cartItems.isEmpty()) return;

        // Snapshot cart → OrderItem list trước khi clear
        List<Order.OrderItem> orderItems = new ArrayList<>();
        for (CartItem ci : cartItems) {
            orderItems.add(new Order.OrderItem(
                ci.menuItemId,
                ci.name,
                ci.quantity,
                ci.unitPrice
            ));
        }
        final int round = currentRound;

        // Disable nút để tránh double-click
        btnSend.setEnabled(false);
        btnSend.setText("⏳  Đang gửi…");

        // Gọi DB trên worker thread
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return orderDAO.addOrderItems(orderId, orderItems, round);
            }

            @Override
            protected void done() {
                boolean ok = false;
                try { ok = get(); } catch (InterruptedException | ExecutionException ex) {
                    System.err.println("[TableOrderFrame] onSendOrder lỗi: " + ex.getMessage());
                }

                btnSend.setText("🛒  Gửi order");

                if (ok) {
                    currentRound++;          // tăng round cho lượt tiếp theo
                    cartItems.clear();
                    updateCart();            // reset giỏ + disable btnSend

                    JOptionPane.showMessageDialog(
                        TableOrderFrame.this,
                        "✅  Đã gửi order! Nhà bếp sẽ sớm xử lý.",
                        "Gửi thành công",
                        JOptionPane.INFORMATION_MESSAGE
                    );

                    // Chuyển sang tab Trạng thái để khách theo dõi
                    mainTabs.setSelectedIndex(TAB_STATUS);
                    refreshStatus();         // load ngay, không đợi timer
                } else {
                    btnSend.setEnabled(true);
                    JOptionPane.showMessageDialog(
                        TableOrderFrame.this,
                        "❌  Gửi order thất bại, vui lòng thử lại!",
                        "Lỗi",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    // ─── Refresh status (polling) ─────────────────────────────────────────────

    /**
     * Kéo danh sách món kèm trạng thái từ DB (SwingWorker) rồi cập nhật UI
     * trên EDT. Được gọi bởi {@link #refreshTimer} mỗi 5 giây và ngay sau
     * khi gửi order.
     */
    private void refreshStatus() {
        new SwingWorker<List<Order.OrderItem>, Void>() {
            @Override
            protected List<Order.OrderItem> doInBackground() {
                return orderDAO.getItemsWithStatus(orderId);
            }

            @Override
            protected void done() {
                try {
                    updateStatusPanel(get());
                } catch (InterruptedException | ExecutionException ex) {
                    System.err.println("[TableOrderFrame] refreshStatus lỗi: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /** Dựng lại danh sách trạng thái món trên EDT. */
    private void updateStatusPanel(List<Order.OrderItem> items) {
        statusListPanel.removeAll();

        if (items.isEmpty()) {
            showStatusPlaceholder("Chưa có món nào được gọi.");
            return;
        }

        // Hiển thị tuần tự — sắp xếp theo round_number + created_at đã được DB xử lý
        for (Order.OrderItem item : items) {
            statusListPanel.add(buildStatusRow(item));
            statusListPanel.add(buildDivider());
        }

        statusListPanel.add(Box.createVerticalGlue());
        statusListPanel.revalidate();
        statusListPanel.repaint();
    }

    /** Một dòng trạng thái: tên món | số lượng | badge màu. */
    private JPanel buildStatusRow(Order.OrderItem item) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBackground(UIConstants.BG_WHITE);
        row.setBorder(new EmptyBorder(12, 20, 12, 20));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        // Tên món
        JLabel lblName = new JLabel(item.getMenuItemName());
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblName.setForeground(UIConstants.TEXT_PRIMARY);
        row.add(lblName, BorderLayout.WEST);

        // Center: số lượng
        JLabel lblQty = new JLabel("× " + item.getQuantity());
        lblQty.setFont(UIConstants.FONT_BODY);
        lblQty.setForeground(UIConstants.TEXT_SECONDARY);
        row.add(lblQty, BorderLayout.CENTER);

        // Badge trạng thái
        row.add(buildBadge(item.getItemStatus()), BorderLayout.EAST);

        return row;
    }

    /** Pill badge màu theo ItemStatus. */
    private JLabel buildBadge(Order.OrderItem.ItemStatus status) {
        Color  bgColor = BADGE_COLOR.getOrDefault(status, new Color(0x95A5A6));
        String text    = BADGE_LABEL.getOrDefault(status, "Không rõ");

        JLabel badge = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badge.setForeground(Color.WHITE);
        badge.setOpaque(false);
        badge.setBorder(new EmptyBorder(4, 12, 4, 12));

        // Tính preferred size dựa trên text
        FontMetrics fm = badge.getFontMetrics(badge.getFont());
        int w = fm.stringWidth(text) + 28;
        badge.setPreferredSize(new Dimension(w, 26));

        return badge;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String formatPrice(double amount) {
        return PRICE_FMT.format((long) amount);
    }
}