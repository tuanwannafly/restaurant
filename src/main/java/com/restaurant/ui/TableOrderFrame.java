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
 * TableOrderFrame — Phase 6C (thêm logo nhà hàng vào buildMenuHeader)
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
    private static final String CARD_MENU    = "menu";
    private static final String CARD_CART    = "cart";
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
    private JLabel            lblStatusTotal;
    private RoundedButton     btnRequestPayment;
    private DefaultTableModel statusTableModel;
    private JTable            statusTable;
    private String currentCard = CARD_MENU;

    // PHASE 1E
    private JLabel         lblPaymentTotal;
    private JToggleButton  tbTransfer;
    private JToggleButton  tbCash;
    private JPanel         cashInputPanel;
    private JTextField     tfCashAmount;
    private String         selectedPaymentMethod = "transfer";

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

        cardPanel.add(buildMenuCard(),    CARD_MENU);
        cardPanel.add(buildCartCard(),    CARD_CART);
        cardPanel.add(buildStatusCard(),  CARD_STATUS);
        cardPanel.add(buildPaymentCard(), CARD_PAYMENT);
        cardPanel.add(buildWaitingCard(), CARD_WAITING);

        setContentPane(cardPanel);
        navigateTo(CARD_MENU);
    }

    // ─── Navigate helper ──────────────────────────────────────────────────────
    private void navigateTo(String card) {
        String prev = currentCard;
        currentCard = card;

        if (CARD_STATUS.equals(prev) && !CARD_STATUS.equals(card)) {
            PollManager.getInstance().unregister("order_status_" + tableId);
        }
        if (CARD_WAITING.equals(prev) && !CARD_WAITING.equals(card)) {
            PollManager.getInstance().unregister("order_waiting_" + tableId);
        }

        if (CARD_STATUS.equals(card)) {
            refreshStatusTable();
            PollManager.getInstance().register(
                    "order_status_" + tableId,
                    this::refreshStatusTable,
                    5_000);
        }

        if (CARD_PAYMENT.equals(card)) {
            syncPaymentTotal();
            updateToggleStyle();
        }

        if (CARD_WAITING.equals(card)) {
            PollManager.getInstance().register(
                    "order_waiting_" + tableId,
                    this::checkOrderCompleted,
                    5_000);
            checkOrderCompleted();
        }

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

    // ─── Card builders ────────────────────────────────────────────────────────

    private JPanel buildStatusCard() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);
        panel.add(buildStatusHeader(), BorderLayout.NORTH);
        panel.add(buildStatusCenter(), BorderLayout.CENTER);
        panel.add(buildStatusFooter(), BorderLayout.SOUTH);
        refreshStatusTable();
        return panel;
    }

    private JPanel buildPaymentCard() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);
        panel.add(buildPaymentHeader(), BorderLayout.NORTH);
        panel.add(buildPaymentCenter(), BorderLayout.CENTER);
        panel.add(buildPaymentFooter(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildStatusHeader() {
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

        JLabel title = new JLabel("Đơn hàng của bạn", SwingConstants.CENTER);
        title.setFont(UIConstants.FONT_TITLE);
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

        bar.add(left,   BorderLayout.WEST);
        bar.add(title,  BorderLayout.CENTER);
        bar.add(right,  BorderLayout.EAST);
        return bar;
    }

    private JPanel buildStatusFooter() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setPreferredSize(new Dimension(0, 56));
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR),
                new EmptyBorder(0, 24, 0, 24)));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 11));
        leftPanel.setOpaque(false);

        lblStatusTotal = new JLabel("Tổng cộng: đang tải...");
        lblStatusTotal.setFont(UIConstants.FONT_BODY);
        lblStatusTotal.setForeground(UIConstants.TEXT_PRIMARY);

        JButton btnBackToMenu = new JButton("← Gọi thêm món");
        btnBackToMenu.setFont(UIConstants.FONT_BODY);
        btnBackToMenu.setForeground(UIConstants.PRIMARY);
        btnBackToMenu.setBorderPainted(false);
        btnBackToMenu.setContentAreaFilled(false);
        btnBackToMenu.setFocusPainted(false);
        btnBackToMenu.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnBackToMenu.addActionListener(e -> navigateTo(CARD_MENU));

        leftPanel.add(lblStatusTotal);
        leftPanel.add(Box.createHorizontalStrut(16));
        leftPanel.add(btnBackToMenu);

        btnRequestPayment = new RoundedButton("Yêu cầu thanh toán");
        btnRequestPayment.setPreferredSize(new Dimension(180, UIConstants.BTN_HEIGHT + 4));
        btnRequestPayment.addActionListener(e -> navigateTo(CARD_PAYMENT));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 10));
        rightPanel.setOpaque(false);
        rightPanel.add(btnRequestPayment);

        bar.add(leftPanel,  BorderLayout.WEST);
        bar.add(rightPanel, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildPaymentHeader() {
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

        JLabel title = new JLabel("Yêu cầu thanh toán", SwingConstants.CENTER);
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

    private JScrollPane buildPaymentCenter() {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBackground(UIConstants.BG_PAGE);
        outer.setBorder(BorderFactory.createEmptyBorder(40, 80, 40, 80));

        lblPaymentTotal = new JLabel("Tổng cộng: đang tải...", SwingConstants.CENTER);
        lblPaymentTotal.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblPaymentTotal.setForeground(UIConstants.PRIMARY);
        lblPaymentTotal.setAlignmentX(CENTER_ALIGNMENT);
        outer.add(lblPaymentTotal);
        outer.add(Box.createVerticalStrut(24));

        JLabel lblMethod = new JLabel("Chọn phương thức thanh toán:");
        lblMethod.setFont(UIConstants.FONT_BOLD);
        lblMethod.setAlignmentX(CENTER_ALIGNMENT);
        outer.add(lblMethod);
        outer.add(Box.createVerticalStrut(12));

        ButtonGroup bg = new ButtonGroup();
        tbTransfer = buildPaymentToggle("🏦  Chuyển khoản");
        tbCash     = buildPaymentToggle("💵  Tiền mặt");
        bg.add(tbTransfer);
        bg.add(tbCash);
        tbTransfer.setSelected(true);

        JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        toggleRow.setOpaque(false);
        toggleRow.add(tbTransfer);
        toggleRow.add(tbCash);
        toggleRow.setAlignmentX(CENTER_ALIGNMENT);
        outer.add(toggleRow);
        outer.add(Box.createVerticalStrut(20));

        cashInputPanel = buildCashInputPanel();
        cashInputPanel.setAlignmentX(CENTER_ALIGNMENT);
        cashInputPanel.setVisible(false);
        outer.add(cashInputPanel);

        tbTransfer.addActionListener(e -> {
            selectedPaymentMethod = "transfer";
            cashInputPanel.setVisible(false);
            updateToggleStyle();
        });
        tbCash.addActionListener(e -> {
            selectedPaymentMethod = "cash";
            cashInputPanel.setVisible(true);
            updateToggleStyle();
        });

        JScrollPane scroll = new JScrollPane(outer);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBackground(UIConstants.BG_PAGE);
        scroll.getViewport().setBackground(UIConstants.BG_PAGE);
        return scroll;
    }

    private JPanel buildPaymentFooter() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setPreferredSize(new Dimension(0, 60));
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR),
                new EmptyBorder(0, 24, 0, 24)));

        JButton btnBack = new JButton("← Quay lại");
        btnBack.setFont(UIConstants.FONT_BODY);
        btnBack.setForeground(UIConstants.PRIMARY);
        btnBack.setBorderPainted(false);
        btnBack.setContentAreaFilled(false);
        btnBack.setFocusPainted(false);
        btnBack.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnBack.addActionListener(e -> navigateTo(CARD_STATUS));

        RoundedButton btnSubmit = new RoundedButton("✅  Gửi yêu cầu");
        btnSubmit.setPreferredSize(new Dimension(160, UIConstants.BTN_HEIGHT + 4));
        btnSubmit.addActionListener(e -> submitPaymentRequest());

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 15));
        leftPanel.setOpaque(false);
        leftPanel.add(btnBack);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 12));
        rightPanel.setOpaque(false);
        rightPanel.add(btnSubmit);

        bar.add(leftPanel,  BorderLayout.WEST);
        bar.add(rightPanel, BorderLayout.EAST);
        return bar;
    }

    // ─── PHASE 1F: buildWaitingCard() ────────────────────────────────────────

    private JPanel buildWaitingCard() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);
        panel.add(buildWaitingHeader(), BorderLayout.NORTH);
        panel.add(buildWaitingCenter(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildWaitingHeader() {
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

        JLabel title = new JLabel("Đang xử lý yêu cầu", SwingConstants.CENTER);
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);

        JButton btnLogout = new JButton("⏻");
        btnLogout.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        btnLogout.setForeground(UIConstants.TEXT_SECONDARY);
        btnLogout.setBorderPainted(false);
        btnLogout.setContentAreaFilled(false);
        btnLogout.setFocusPainted(false);
        btnLogout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogout.setToolTipText("Đóng màn hình bàn");
        btnLogout.addActionListener(e -> {
            PollManager.getInstance().unregister("order_status_"  + tableId);
            PollManager.getInstance().unregister("order_waiting_" + tableId);
            dispose();
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 12));
        right.setOpaque(false);
        right.add(btnLogout);

        bar.add(left,  BorderLayout.WEST);
        bar.add(title, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildWaitingCenter() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(UIConstants.BG_PAGE);

        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(48, 64, 48, 64));
        card.setPreferredSize(new Dimension(480, 280));

        JLabel lblIcon = new JLabel("⏳", SwingConstants.CENTER);
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        lblIcon.setAlignmentX(CENTER_ALIGNMENT);
        card.add(lblIcon);
        card.add(Box.createVerticalStrut(20));

        JLabel lblMain = new JLabel("Đang xử lý", SwingConstants.CENTER);
        lblMain.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblMain.setForeground(UIConstants.TEXT_PRIMARY);
        lblMain.setAlignmentX(CENTER_ALIGNMENT);
        card.add(lblMain);
        card.add(Box.createVerticalStrut(12));

        JLabel lblSub = new JLabel(
                "<html><div style='text-align:center;width:300px'>"
                + "Nhân viên đang đến để thực hiện yêu cầu thanh toán"
                + "</div></html>", SwingConstants.CENTER);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblSub.setForeground(UIConstants.TEXT_PRIMARY);
        lblSub.setAlignmentX(CENTER_ALIGNMENT);
        card.add(lblSub);
        card.add(Box.createVerticalStrut(10));

        JLabel lblHint = new JLabel("Vui lòng chờ trong giây lát.", SwingConstants.CENTER);
        lblHint.setFont(UIConstants.FONT_BODY);
        lblHint.setForeground(UIConstants.TEXT_SECONDARY);
        lblHint.setAlignmentX(CENTER_ALIGNMENT);
        card.add(lblHint);

        wrapper.add(card);
        return wrapper;
    }

    private void checkOrderCompleted() {
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                Order active = new OrderDAO().getActiveOrderByTable(tableId);
                return (active == null);
            }

            @Override
            protected void done() {
                try {
                    boolean completed = get();
                    if (completed && CARD_WAITING.equals(currentCard)) {
                        PollManager.getInstance().unregister("order_status_"  + tableId);
                        PollManager.getInstance().unregister("order_waiting_" + tableId);
                        ToastNotification.show(TableOrderFrame.this,
                                "Thanh toán hoàn tất! Cảm ơn quý khách.",
                                ToastNotification.Type.SUCCESS);
                        javax.swing.Timer delay = new javax.swing.Timer(2000, e -> dispose());
                        delay.setRepeats(false);
                        delay.start();
                    }
                } catch (Exception ex) {
                    System.err.println("[TableOrderFrame] checkOrderCompleted lỗi: "
                            + ex.getMessage());
                }
            }
        }.execute();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CARD 1 — MÀN HÌNH CHỌN MÓN
    // ═════════════════════════════════════════════════════════════════════════

    private JPanel buildMenuCard() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);
        panel.add(buildMenuHeader(), BorderLayout.NORTH);
        panel.add(buildMenuCenter(), BorderLayout.CENTER);
        panel.add(buildMenuFooter(), BorderLayout.SOUTH);
        return panel;
    }

    // ── MENU HEADER — Phase 6C: thêm logoLabel nhà hàng ─────────────────────

    private JPanel buildMenuHeader() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setPreferredSize(new Dimension(0, 56));
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                new EmptyBorder(0, 24, 0, 24)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        // Phase 6C: logoLabel nhà hàng — load async, fallback về icon "⛁"
        JLabel logoLabel = new JLabel();
        logoLabel.setPreferredSize(new Dimension(36, 36));
        logoLabel.setBorder(BorderFactory.createLineBorder(
                new Color(0xBFDBFE), 1, true));
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logoLabel.setVerticalAlignment(SwingConstants.CENTER);
        // Thêm logoLabel vào đầu panel LEFT
        left.add(logoLabel);
        // Load async sau khi frame visible; nếu null/blank → label trống, icon ⛁ vẫn hiển thị
        SwingUtilities.invokeLater(() -> {
            try {
                com.restaurant.model.Restaurant r =
                        com.restaurant.data.DataManager.getInstance().getMyRestaurant();
                if (r != null && r.getLogoUrl() != null && !r.getLogoUrl().isBlank()) {
                    ImageLoader.loadAsync(r.getLogoUrl(), logoLabel);
                }
            } catch (Exception ignored) {
                // logoUrl null/blank hoặc lỗi → giữ label trống, không crash
            }
        });

        JLabel logo = new JLabel("⛁");
        logo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        logo.setForeground(UIConstants.PRIMARY);
        JLabel sysName = new JLabel("SmartRestaurant");
        sysName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sysName.setForeground(UIConstants.PRIMARY);
        left.add(logo);
        left.add(sysName);

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
        Component north = ((BorderLayout) centerContent.getLayout())
                .getLayoutComponent(BorderLayout.NORTH);
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

        JButton btnViewStatus = new JButton("📋 Trạng thái đơn");
        btnViewStatus.setFont(UIConstants.FONT_BODY);
        btnViewStatus.setForeground(UIConstants.PRIMARY);
        btnViewStatus.setBorderPainted(false);
        btnViewStatus.setContentAreaFilled(false);
        btnViewStatus.setFocusPainted(false);
        btnViewStatus.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnViewStatus.addActionListener(e -> navigateTo(CARD_STATUS));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 11));
        leftPanel.setOpaque(false);
        leftPanel.add(lblSubtotal);
        leftPanel.add(Box.createHorizontalStrut(16));
        leftPanel.add(btnViewStatus);

        btnShowCart = new RoundedButton("🛒  Giỏ hàng (0 món)");
        btnShowCart.setPreferredSize(new Dimension(200, UIConstants.BTN_HEIGHT + 4));
        btnShowCart.addActionListener(e -> showCart());

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 10));
        rightPanel.setOpaque(false);
        rightPanel.add(btnShowCart);

        bar.add(leftPanel,  BorderLayout.WEST);
        bar.add(rightPanel, BorderLayout.EAST);
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

    // ─── Phase 6A: buildMenuItemCard — dùng ImageLoader thay JPanel placeholder ───
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

        // ── Phase 6A: JLabel ảnh async thay cho JPanel vẽ emoji ──────────────
        JLabel imgLabel = new JLabel();
        imgLabel.setPreferredSize(new Dimension(ImageLoader.IMG_W, 100));
        imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imgLabel.setVerticalAlignment(SwingConstants.CENTER);
        // ImageLoader tự hiện PLACEHOLDER khi imageUrl null/blank/lỗi
        ImageLoader.loadAsync(item.getImageUrl(), imgLabel);
        // ─────────────────────────────────────────────────────────────────────

        // Info section — giữ nguyên như cũ
        JPanel info = new JPanel(new BorderLayout(0, 2));
        info.setOpaque(false);
        info.setBorder(new EmptyBorder(6, 10, 0, 10));

        JLabel lblName = new JLabel(item.getName());
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblName.setForeground(UIConstants.TEXT_PRIMARY);

        JLabel lblPrice = new JLabel(formatPrice(item.getPrice()) + " đ");
        lblPrice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblPrice.setForeground(UIConstants.TEXT_SECONDARY);

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

        card.add(imgLabel, BorderLayout.CENTER); // ← Phase 6A: dùng imgLabel
        card.add(info,     BorderLayout.SOUTH);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { addToCart(item); }
        });

        return card;
    }

    private String pickFoodEmoji(String category) {
        if (category == null) return "🍽";
        String c = category.toLowerCase();
        if (c.contains("uống") || c.contains("drink"))     return "🥤";
        if (c.contains("tráng") || c.contains("dessert"))  return "🍮";
        if (c.contains("hải sản") || c.contains("seafood")) return "🦐";
        if (c.contains("thịt") || c.contains("meat"))      return "🥩";
        if (c.contains("cơm") || c.contains("rice"))       return "🍚";
        if (c.contains("phở") || c.contains("soup"))       return "🍜";
        if (c.contains("gà") || c.contains("chicken"))     return "🍗";
        return "🍽";
    }

    private JToggleButton buildPaymentToggle(String text) {
        JToggleButton btn = new JToggleButton(text);
        btn.setFont(UIConstants.FONT_BOLD);
        btn.setPreferredSize(new Dimension(180, 44));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createLineBorder(UIConstants.PRIMARY, 2, true));
        btn.setBackground(Color.WHITE);
        btn.setForeground(UIConstants.PRIMARY);
        return btn;
    }

    private void updateToggleStyle() {
        tbTransfer.setBackground(tbTransfer.isSelected() ? UIConstants.PRIMARY : Color.WHITE);
        tbTransfer.setForeground(tbTransfer.isSelected() ? Color.WHITE : UIConstants.PRIMARY);
        tbCash.setBackground(tbCash.isSelected() ? UIConstants.PRIMARY : Color.WHITE);
        tbCash.setForeground(tbCash.isSelected() ? Color.WHITE : UIConstants.PRIMARY);
    }

    private JPanel buildCashInputPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(0xF9FAFB));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(16, 24, 16, 24)));
        panel.setMaximumSize(new Dimension(400, Integer.MAX_VALUE));

        JLabel lblCash = new JLabel("Nhập số tiền khách đưa:");
        lblCash.setFont(UIConstants.FONT_BOLD);
        lblCash.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(lblCash);
        panel.add(Box.createVerticalStrut(8));

        tfCashAmount = new JTextField("300000");
        tfCashAmount.setFont(UIConstants.FONT_BODY);
        tfCashAmount.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        tfCashAmount.setAlignmentX(LEFT_ALIGNMENT);
        tfCashAmount.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        panel.add(tfCashAmount);
        panel.add(Box.createVerticalStrut(6));

        JLabel lblHelper = new JLabel("Tiền thừa sẽ được tính tự động tại quầy.");
        lblHelper.setFont(UIConstants.FONT_SMALL);
        lblHelper.setForeground(UIConstants.TEXT_SECONDARY);
        lblHelper.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(lblHelper);

        return panel;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CARD 2 — MÀN HÌNH GIỎ HÀNG
    // ═════════════════════════════════════════════════════════════════════════

    private JPanel buildCartCard() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);
        panel.add(buildCartHeader(), BorderLayout.NORTH);
        panel.add(buildCartCenter(), BorderLayout.CENTER);
        panel.add(buildCartFooter(), BorderLayout.SOUTH);
        return panel;
    }

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

    private JScrollPane buildCartCenter() {
        cartTableModel = new DefaultTableModel(CART_COLS, 0) {
            @Override public boolean isCellEditable(int row, int col) {
                return col == 4 || col == 5;
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

    private JScrollPane buildStatusCenter() {
        String[] cols = {"STT", "Tên món", "Trạng thái"};
        statusTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        statusTable = new JTable(statusTableModel);
        statusTable.setFont(UIConstants.FONT_BODY);
        statusTable.setRowHeight(UIConstants.ROW_HEIGHT + 4);
        statusTable.setShowGrid(false);
        statusTable.setIntercellSpacing(new Dimension(0, 1));
        statusTable.setSelectionBackground(UIConstants.ROW_SELECTED);
        statusTable.setFillsViewportHeight(true);

        JTableHeader header = statusTable.getTableHeader();
        header.setFont(UIConstants.FONT_HEADER);
        header.setBackground(UIConstants.HEADER_BG);
        header.setForeground(UIConstants.TEXT_PRIMARY);
        header.setPreferredSize(new Dimension(0, 36));
        header.setReorderingAllowed(false);
        ((DefaultTableCellRenderer) header.getDefaultRenderer())
                .setHorizontalAlignment(SwingConstants.CENTER);

        statusTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        statusTable.getColumnModel().getColumn(0).setMaxWidth(60);
        statusTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        statusTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        statusTable.getColumnModel().getColumn(2).setCellRenderer(new StatusCellRenderer());

        JScrollPane scroll = new JScrollPane(statusTable);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR));
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    public void refreshStatusTable() {
        new SwingWorker<List<Order.OrderItem>, Void>() {
            @Override
            protected List<Order.OrderItem> doInBackground() {
                return new OrderDAO().getItemsWithStatus(orderId);
            }

            @Override
            protected void done() {
                try {
                    List<Order.OrderItem> items = get();
                    statusTableModel.setRowCount(0);
                    double total = 0;
                    int stt = 1;
                    for (Order.OrderItem item : items) {
                        String statusVi = mapItemStatus(item.getItemStatus());
                        statusTableModel.addRow(new Object[]{
                            stt++,
                            item.getMenuItemName(),
                            statusVi
                        });
                        total += item.getSubtotal();
                    }
                    if (lblStatusTotal != null) {
                        lblStatusTotal.setText("Tổng cộng: " + formatPrice(total) + " đ");
                    }
                } catch (Exception ex) {
                    System.err.println("[TableOrderFrame] refreshStatusTable lỗi: "
                            + ex.getMessage());
                }
            }
        }.execute();
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

    private String mapItemStatus(Order.OrderItem.ItemStatus status) {
        if (status == null) return "Đang chờ";
        return switch (status) {
            case PENDING    -> "Đang chờ";
            case ACCEPTED   -> "Đang chế biến";
            case COOKING    -> "Đang chế biến";
            case READY      -> "Đã chế biến";
            case DELIVERING -> "Đang mang lên";
            case DELIVERED  -> "Đã nhận";
            default         -> "Đang chờ";
        };
    }

    private void filterMenu() {
        String query = tfSearch.getText().trim().toLowerCase();
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
                        () -> cartItems.add(new CartItem(item.getId(), item.getName(),
                                item.getPrice()))
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
            ToastNotification.show(this, "Giỏ hàng đang trống, hãy thêm món!",
                    ToastNotification.Type.INFO);
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
                            "✅  Đã gửi order! Bếp đang xử lý.",
                            ToastNotification.Type.SUCCESS);
                } else {
                    ToastNotification.show(TableOrderFrame.this,
                            "❌  Gửi order thất bại, vui lòng thử lại.",
                            ToastNotification.Type.ERROR);
                }
            }
        }.execute();
    }

    // ── Window lifecycle ──────────────────────────────────────────────────────

    private void setupWindowLifecycle() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                PollManager.getInstance().unregister("tableorder_"    + tableId);
                PollManager.getInstance().unregister("order_status_"  + tableId);
                PollManager.getInstance().unregister("order_waiting_" + tableId);
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CELL RENDERERS / EDITORS
    // ═════════════════════════════════════════════════════════════════════════

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

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
            String val = value != null ? value.toString() : "";
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            switch (val) {
                case "Đang chờ":
                    lbl.setForeground(UIConstants.TEXT_SECONDARY);
                    lbl.setFont(UIConstants.FONT_BODY);
                    break;
                case "Đang chế biến":
                    lbl.setForeground(UIConstants.WARNING);
                    lbl.setFont(UIConstants.FONT_BOLD);
                    break;
                case "Đã chế biến":
                    lbl.setForeground(UIConstants.SUCCESS);
                    lbl.setFont(UIConstants.FONT_BOLD);
                    break;
                case "Đang mang lên":
                    lbl.setForeground(UIConstants.PRIMARY);
                    lbl.setFont(UIConstants.FONT_BOLD);
                    break;
                case "Đã nhận":
                    lbl.setForeground(new Color(0x9CA3AF));
                    lbl.setFont(UIConstants.FONT_BODY);
                    break;
                default:
                    lbl.setForeground(UIConstants.TEXT_PRIMARY);
            }
            return lbl;
        }
    }

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

    private void submitPaymentRequest() {
        System.out.println("[TableOrderFrame] Payment request:"
                + " orderId=" + orderId
                + " method=" + selectedPaymentMethod
                + (selectedPaymentMethod.equals("cash")
                    ? " amount=" + tfCashAmount.getText()
                    : ""));

        ToastNotification.show(this,
                "Đã gửi yêu cầu! Nhân viên sẽ đến ngay.",
                ToastNotification.Type.SUCCESS);

        navigateTo(CARD_WAITING);
    }

    private void syncPaymentTotal() {
        new SwingWorker<List<Order.OrderItem>, Void>() {
            @Override
            protected List<Order.OrderItem> doInBackground() {
                return new OrderDAO().getItemsWithStatus(orderId);
            }
            @Override
            protected void done() {
                try {
                    double total = get().stream()
                            .mapToDouble(Order.OrderItem::getSubtotal).sum();
                    if (lblPaymentTotal != null)
                        lblPaymentTotal.setText("Tổng cộng: " + formatPrice(total) + " đ");
                } catch (Exception ex) {
                    System.err.println("[TableOrderFrame] syncPaymentTotal lỗi: "
                            + ex.getMessage());
                }
            }
        }.execute();
    }
}