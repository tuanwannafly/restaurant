package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import com.restaurant.model.Restaurant;
import com.restaurant.session.AppSession;
import com.restaurant.session.AppSession.SessionListener;
import com.restaurant.session.TokenService;

public class MainFrame extends JFrame implements SessionListener {

    private CardLayout cardLayout;
    private JPanel contentArea;

    private HomePanel           homePanel;
    private MenuPanel           menuPanel;
    private TablePanel          tablePanel;
    private EmployeePanel       employeePanel;
    private OrderPanel          orderPanel;
    private ReportPanel         reportPanel;
    private StatsPanel          statsPanel;
    private RestaurantPanel     restaurantPanel;
    private KitchenPanel        kitchenPanel;
    private WaiterServicePanel  waiterServicePanel;
    private CashierPanel        cashierPanel;
    private MyRestaurantInfoPanel myRestaurantPanel;
    private AuditLogPanel         auditLogPanel;
    private RestaurantDetailPanel restaurantDetailPanel;
    private AdminStatsPanel       adminStatsPanel;

    private JButton[] navButtons;

    // ── Phase 7D: BadgeButton fields cho Bếp, Phục vụ, Thu ngân ─────────────
    /** Badge trên nút "🍳 Bếp" – số món PENDING chờ bếp. */
    private BadgeButton kitchenBadgeBtn;
    /** Badge trên nút "🛎 Phục vụ" – số món READY chờ giao. */
    private BadgeButton waiterBadgeBtn;
    /** Badge trên nút "💳 Thu ngân" – số yêu cầu thanh toán. */
    private BadgeButton cashierBadgeBtn;

    private String[] navPages  = {
        "home", "menu", "ban", "nhanvien", "donhang",
        "chedomlamviec", "baocao", "thongke", "nhahangs",
        "bep", "phucvu", "thungan",
        "myrestaurant", "baomat", "adminstats"
    };
    private String[] navLabels = {
        "🏠 Home", "Menu", "Bàn", "Nhân viên", "Đơn hàng",
        "Chế độ làm việc", "Báo cáo", "📈 Thống kê", "🏪 Nhà hàng",
        "🍳 Bếp", "🛎 Phục vụ", "💳 Thu ngân",
        "🏪 Nhà hàng của tôi", "🔐 Bảo mật", "📊 Thống kê (Admin)"
    };

    /** Swing Timer kiểm tra session token mỗi 30 phút. */
    private Timer sessionCheckTimer;
    private static final int SESSION_CHECK_INTERVAL_MS = 30 * 60 * 1000;

    public MainFrame() {
        super("Hệ thống Quản lý Nhà hàng");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 760);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        AppSession.getInstance().addSessionListener(this);
        buildUI();
        startSessionCheckTimer();
    }

    // ── Phase 7D: Getters cho BadgeButton ────────────────────────────────────

    /** Trả về BadgeButton nút Bếp để cập nhật badge từ poll. */
    public BadgeButton getBtnKitchen()  { return kitchenBadgeBtn; }

    /** Trả về BadgeButton nút Phục vụ để cập nhật badge từ poll. */
    public BadgeButton getBtnWaiter()   { return waiterBadgeBtn; }

    /** Trả về BadgeButton nút Thu ngân để cập nhật badge từ poll. */
    public BadgeButton getBtnCashier()  { return cashierBadgeBtn; }

    // ── SessionListener ───────────────────────────────────────────────────────

    @Override
    public void onLogout() {
        SwingUtilities.invokeLater(() -> {
            try {
                stopSessionCheckTimer();
                PollManager.getInstance().stopAll();
                this.dispose();
            } catch (Exception cleanupEx) {
                System.err.println("[MainFrame] onLogout cleanup lỗi: " + cleanupEx.getMessage());
            }

            try {
                LoginDialog loginDialog = new LoginDialog(null);
                loginDialog.setVisible(true);

                if (loginDialog.isLoginSuccess()) {
                    try {
                        MainFrame frame = new MainFrame();
                        frame.setVisible(true);
                    } catch (Exception frameEx) {
                        System.err.println("[MainFrame] Không thể tạo MainFrame sau login: " + frameEx.getMessage());
                        frameEx.printStackTrace();
                        JOptionPane.showMessageDialog(null,
                                "Lỗi khởi tạo giao diện: " + frameEx.getMessage() +
                                "\nVui lòng đăng nhập lại.",
                                "Lỗi", JOptionPane.ERROR_MESSAGE);
                        LoginDialog retry = new LoginDialog(null);
                        retry.setVisible(true);
                        if (retry.isLoginSuccess()) {
                            new MainFrame().setVisible(true);
                        } else {
                            System.exit(0);
                        }
                    }
                } else {
                    System.exit(0);
                }
            } catch (Exception ex) {
                System.err.println("[MainFrame] onLogout flow lỗi: " + ex.getMessage());
                ex.printStackTrace();
                System.exit(1);
            }
        });
    }

    // ── Session Check Timer ───────────────────────────────────────────────────

    private void startSessionCheckTimer() {
        sessionCheckTimer = new Timer(SESSION_CHECK_INTERVAL_MS, e -> {
            String token = AppSession.getInstance().getSessionToken();
            boolean valid = TokenService.getInstance().validateToken(token);
            new Thread(() -> TokenService.getInstance().cleanExpiredTokens(), "token-cleanup").start();
            if (!valid) {
                JOptionPane.showMessageDialog(this,
                    "Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.",
                    "Hết phiên", JOptionPane.WARNING_MESSAGE);
                AppSession.getInstance().logout();
            }
        });
        sessionCheckTimer.setInitialDelay(SESSION_CHECK_INTERVAL_MS);
        sessionCheckTimer.start();
    }

    private void stopSessionCheckTimer() {
        if (sessionCheckTimer != null && sessionCheckTimer.isRunning()) {
            sessionCheckTimer.stop();
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * Phase 7A: Xử lý đăng xuất theo đúng thứ tự.
     * Entry point duy nhất cho mọi nút logout trong app.
     */
    private void handleLogout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn đăng xuất?",
                "Xác nhận đăng xuất", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        PollManager.getInstance().stopAll();
        AppSession.getInstance().logout();
    }

    // ── UI Build ──────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIConstants.BG_WHITE);
        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel nav = buildNavBar();

        cardLayout  = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(UIConstants.BG_PAGE);

        homePanel     = new HomePanel();
        menuPanel     = new MenuPanel();
        tablePanel    = new TablePanel();
        employeePanel = new EmployeePanel();
        orderPanel    = new OrderPanel();
        reportPanel   = new ReportPanel();
        statsPanel    = new StatsPanel();

        com.restaurant.session.RbacGuard _guard = com.restaurant.session.RbacGuard.getInstance();
        if (_guard.isSuperAdmin()) {
            restaurantPanel       = new RestaurantPanel();
            restaurantDetailPanel = new RestaurantDetailPanel(() -> navigateTo("nhahangs"));
            adminStatsPanel       = new AdminStatsPanel();
        }
        kitchenPanel       = new KitchenPanel();
        waiterServicePanel = new WaiterServicePanel();
        cashierPanel       = new CashierPanel();
        if (_guard.isRestaurantAdmin()) {
            myRestaurantPanel = new MyRestaurantInfoPanel();
        }
        if (_guard.isSuperAdmin()) {
            auditLogPanel = new AuditLogPanel();
        }

        contentArea.add(homePanel,          "home");
        contentArea.add(menuPanel,          "menu");
        contentArea.add(tablePanel,         "ban");
        contentArea.add(employeePanel,      "nhanvien");
        contentArea.add(orderPanel,         "donhang");
        contentArea.add(buildPlaceholder("Che do lam viec"), "chedomlamviec");
        contentArea.add(reportPanel,        "baocao");
        contentArea.add(statsPanel,         "thongke");
        contentArea.add(restaurantPanel != null ? restaurantPanel
                : buildPlaceholder("Nha hang"), "nhahangs");
        contentArea.add(restaurantDetailPanel != null ? restaurantDetailPanel
                : buildPlaceholder("Chi tiet nha hang"), "restaurant_detail");
        contentArea.add(kitchenPanel,       "bep");
        contentArea.add(waiterServicePanel, "phucvu");
        contentArea.add(cashierPanel,       "thungan");
        contentArea.add(myRestaurantPanel != null ? myRestaurantPanel
                : buildPlaceholder("Nhà hàng của tôi"), "myrestaurant");
        contentArea.add(adminStatsPanel != null ? adminStatsPanel
                : buildPlaceholder("Thống kê Admin"), "adminstats");
        contentArea.add(auditLogPanel != null ? auditLogPanel
                : buildPlaceholder("Nhật ký bảo mật"), "baomat");

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, nav, contentArea);
        split.setDividerSize(0);
        split.setEnabled(false);
        split.setBorder(BorderFactory.createEmptyBorder());
        root.add(split, BorderLayout.CENTER);

        setContentPane(root);
        navigateTo("home");
        applyRoleFilter();
    }

    private void applyRoleFilter() {
        AppSession session = AppSession.getInstance();
        com.restaurant.session.RbacGuard guard = com.restaurant.session.RbacGuard.getInstance();
        boolean isSuperAdmin = guard.isSuperAdmin();
        String userRole = session.getUserRole();
        java.util.Set<com.restaurant.session.Permission> perms =
                com.restaurant.session.Permission.forRole(userRole);

        for (int i = 0; i < navPages.length; i++) {
            switch (navPages[i]) {
                case "menu": case "ban": case "donhang": case "chedomlamviec":
                    navButtons[i].setVisible(!isSuperAdmin); break;
                case "nhanvien":
                    navButtons[i].setVisible(!isSuperAdmin &&
                            perms.contains(com.restaurant.session.Permission.VIEW_EMPLOYEE)); break;
                case "bep":
                    navButtons[i].setVisible(!isSuperAdmin &&
                            perms.contains(com.restaurant.session.Permission.VIEW_KITCHEN)); break;
                case "phucvu":
                    navButtons[i].setVisible(!isSuperAdmin &&
                            perms.contains(com.restaurant.session.Permission.VIEW_WAITER_SERVICE)); break;
                case "thungan":
                    navButtons[i].setVisible(!isSuperAdmin &&
                            perms.contains(com.restaurant.session.Permission.VIEW_CASHIER)); break;
                case "thongke":
                    navButtons[i].setVisible(!isSuperAdmin &&
                            perms.contains(com.restaurant.session.Permission.VIEW_STATS)); break;
                case "nhahangs":  navButtons[i].setVisible(isSuperAdmin); break;
                case "myrestaurant": navButtons[i].setVisible(guard.isRestaurantAdmin()); break;
                case "baomat":    navButtons[i].setVisible(isSuperAdmin); break;
                case "adminstats": navButtons[i].setVisible(isSuperAdmin); break;
                default: break;
            }
        }
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BG_WHITE);
        header.setPreferredSize(new Dimension(0, 56));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
            BorderFactory.createEmptyBorder(0, 28, 0, 28)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        JLabel icon = new JLabel("⛁");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        icon.setForeground(UIConstants.PRIMARY);
        JLabel sysName = new JLabel("SmartRestaurant");
        sysName.setFont(UIConstants.FONT_LOGO);
        sysName.setForeground(UIConstants.PRIMARY);
        left.add(icon);
        left.add(sysName);

        com.restaurant.session.RbacGuard guard = com.restaurant.session.RbacGuard.getInstance();
        if (!guard.isSuperAdmin()) {
            JLabel logoLabel = new JLabel();
            logoLabel.setPreferredSize(new Dimension(32, 32));
            logoLabel.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true));
            logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
            logoLabel.setVerticalAlignment(SwingConstants.CENTER);
            left.add(logoLabel, 0);
            SwingUtilities.invokeLater(() -> {
                try {
                    Restaurant r = com.restaurant.data.DataManager.getInstance().getMyRestaurant();
                    if (r != null && r.getLogoUrl() != null && !r.getLogoUrl().isBlank()) {
                        ImageLoader.loadAsync(r.getLogoUrl(), logoLabel);
                    }
                } catch (Exception ignored) {}
            });
        }

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        AppSession session = AppSession.getInstance();
        if (session.hasPermission(com.restaurant.session.Permission.EDIT_OWN_PROFILE)) {
            JButton btnProfile = new JButton(session.getUserName() + " ▾");
            btnProfile.setFont(UIConstants.FONT_BODY);
            btnProfile.setForeground(UIConstants.TEXT_PRIMARY);
            btnProfile.setBorderPainted(false);
            btnProfile.setContentAreaFilled(false);
            btnProfile.setFocusPainted(false);
            btnProfile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnProfile.setToolTipText("Hồ sơ của tôi");
            btnProfile.addActionListener(e -> {
                com.restaurant.ui.dialog.MyProfileDialog dlg =
                        new com.restaurant.ui.dialog.MyProfileDialog(this);
                dlg.setVisible(true);
            });
            right.add(btnProfile);
        }
        JLabel roleLbl = new JLabel("[" + session.getRoleLabel() + "]");
        roleLbl.setFont(UIConstants.FONT_BODY);
        roleLbl.setForeground(UIConstants.TEXT_SECONDARY);
        right.add(roleLbl);

        if (session.getRestaurantId() != 0) {
            Restaurant r = com.restaurant.data.DataManager.getInstance().getMyRestaurant();
            if (r != null && r.getName() != null) {
                JLabel lblRestaurant = new JLabel("·  " + r.getName());
                lblRestaurant.setFont(UIConstants.FONT_SMALL);
                lblRestaurant.setForeground(UIConstants.TEXT_SECONDARY);
                right.add(lblRestaurant);
            }
        }

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // ── Nav bar ───────────────────────────────────────────────────────────────

    private JPanel buildNavBar() {
        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBackground(UIConstants.BG_WHITE);
        nav.setPreferredSize(new Dimension(190, 0));
        nav.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, UIConstants.BORDER_COLOR),
            BorderFactory.createEmptyBorder(16, 12, 16, 12)));

        navButtons = new JButton[navPages.length];

        for (int i = 0; i < navPages.length; i++) {
            final String page = navPages[i];
            JButton btn;

            // Phase 7D: BadgeButton cho 3 tab quan trọng
            if ("bep".equals(navPages[i])) {
                BadgeButton bb = new BadgeButton(navLabels[i]);
                applyNavStyle(bb);
                kitchenBadgeBtn = bb;
                btn = bb;
            } else if ("phucvu".equals(navPages[i])) {
                BadgeButton bb = new BadgeButton(navLabels[i]);
                applyNavStyle(bb);
                waiterBadgeBtn = bb;
                btn = bb;
            } else if ("thungan".equals(navPages[i])) {
                BadgeButton bb = new BadgeButton(navLabels[i]);
                applyNavStyle(bb);
                cashierBadgeBtn = bb;
                btn = bb;
            } else {
                btn = createNavButton(navLabels[i]);
            }

            btn.addActionListener(e -> navigateTo(page));
            navButtons[i] = btn;
            nav.add(btn);
            nav.add(Box.createVerticalStrut(4));
        }

        nav.add(Box.createVerticalGlue());

        JButton btnLogout = createNavButton("⏻  Đăng xuất");
        btnLogout.setForeground(UIConstants.DANGER);
        btnLogout.addActionListener(e -> handleLogout());
        nav.add(btnLogout);
        return nav;
    }

    private void applyNavStyle(JButton btn) {
        btn.setFont(UIConstants.FONT_NAV);
        btn.setForeground(UIConstants.TEXT_PRIMARY);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        btn.setPreferredSize(new Dimension(166, 38));
        btn.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
    }

    private JButton createNavButton(String label) {
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                if (getClientProperty("active") == Boolean.TRUE) {
                    g2.setColor(UIConstants.PRIMARY_LIGHT);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                } else {
                    Point p = getMousePosition();
                    if (p != null) {
                        g2.setColor(new Color(0xF3F4F6));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                                UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                    }
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        applyNavStyle(btn);
        return btn;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public void navigateTo(String page) {
        cardLayout.show(contentArea, page);
        switch (page) {
            case "home":      homePanel.refresh();              break;
            case "menu":      menuPanel.loadData();             break;
            case "ban":       tablePanel.loadData();            break;
            case "nhanvien":  employeePanel.loadData();         break;
            case "donhang":   orderPanel.loadData();            break;
            case "baocao":    reportPanel.loadData();           break;
            case "thongke":   statsPanel.loadAll();             break;
            case "adminstats":
                if (adminStatsPanel != null) adminStatsPanel.loadStats(); break;
            case "nhahangs":
                if (restaurantPanel != null) restaurantPanel.loadData(); break;
            case "bep":       kitchenPanel.loadData();          break;
            case "phucvu":    waiterServicePanel.loadData();    break;
            case "thungan":   cashierPanel.loadData();          break;
            case "myrestaurant":
                if (myRestaurantPanel != null) myRestaurantPanel.loadData(); break;
        }
        for (int i = 0; i < navPages.length; i++) {
            boolean active = navPages[i].equals(page);
            navButtons[i].putClientProperty("active", active);
            navButtons[i].setForeground(active ? UIConstants.PRIMARY : UIConstants.TEXT_PRIMARY);
            navButtons[i].setFont(active ? UIConstants.FONT_BOLD : UIConstants.FONT_NAV);
            navButtons[i].repaint();
        }
    }

    public void showRestaurantDetail(Restaurant r) {
        if (restaurantDetailPanel == null) return;
        restaurantDetailPanel.populate(r);
        cardLayout.show(contentArea, "restaurant_detail");
        for (int i = 0; i < navPages.length; i++) {
            boolean active = "nhahangs".equals(navPages[i]);
            navButtons[i].putClientProperty("active", active);
            navButtons[i].setForeground(active ? UIConstants.PRIMARY : UIConstants.TEXT_PRIMARY);
            navButtons[i].setFont(active ? UIConstants.FONT_BOLD : UIConstants.FONT_NAV);
            navButtons[i].repaint();
        }
    }

    private JPanel buildPlaceholder(String name) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(UIConstants.BG_PAGE);
        JLabel lbl = new JLabel(name + " — Đang phát triển");
        lbl.setFont(UIConstants.FONT_TITLE);
        lbl.setForeground(UIConstants.TEXT_SECONDARY);
        p.add(lbl);
        return p;
    }
}