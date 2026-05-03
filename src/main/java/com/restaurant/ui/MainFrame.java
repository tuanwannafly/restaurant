package com.restaurant.ui;

import com.restaurant.model.Restaurant;
import com.restaurant.session.AppSession;
import com.restaurant.session.AppSession.SessionListener;
import com.restaurant.session.TokenService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Main application frame.
 *
 * <p>Sidebar design (220 px):
 * <ul>
 *   <li>Brand strip at top (logo + app name)</li>
 *   <li>NavItems: optional icon + label, active / hover highlight</li>
 *   <li>BadgeButton on Kitchen / Waiter / Cashier tabs</li>
 *   <li>User info panel anchored at bottom</li>
 * </ul>
 *
 * Compatible with WindowBuilder.
 */
public class MainFrame extends JFrame implements SessionListener {

    // ── Sidebar width ─────────────────────────────────────────────────────────
    private static final int NAV_WIDTH = 220;

    // ── Content area ──────────────────────────────────────────────────────────
    private CardLayout cardLayout;
    private JPanel     contentArea;

    // ── Page panels ──────────────────────────────────────────────────────────
    private HomePanel             homePanel;
    private MenuPanel             menuPanel;
    private TablePanel            tablePanel;
    private EmployeePanel         employeePanel;
    private OrderPanel            orderPanel;
    private ReportPanel           reportPanel;
    private StatsPanel            statsPanel;
    private RestaurantPanel       restaurantPanel;
    private KitchenPanel          kitchenPanel;
    private WaiterServicePanel    waiterServicePanel;
    private CashierPanel          cashierPanel;
    private MyRestaurantInfoPanel myRestaurantPanel;
    private AuditLogPanel         auditLogPanel;
    private RestaurantDetailPanel restaurantDetailPanel;
    private AdminStatsPanel       adminStatsPanel;

    // ── Navigation ────────────────────────────────────────────────────────────
    private JButton[] navButtons;

    private static final String[] NAV_PAGES  = {
        "home", "menu", "ban", "nhanvien", "donhang",
        "chedomlamviec", "baocao", "thongke", "nhahangs",
        "bep", "phucvu", "thungan",
        "myrestaurant", "baomat", "adminstats"
    };

    private static final String[] NAV_LABELS = {
        "Trang chu",     "Thuc don",   "Quan ban",   "Nhan vien",  "Don hang",
        "Ca lam viec",   "Bao cao",    "Thong ke",   "Nha hang",
        "Bep",           "Phuc vu",    "Thu ngan",
        "Nha hang cua toi", "Bao mat",  "Thong ke Admin"
    };

    /** Icon filenames relative to /icons/ classpath resource folder.
     *  Value null = no icon; graceful fallback to text-only. */
    private static final Map<String, String> NAV_ICONS = new HashMap<String, String>() {{
        put("home",           "home.png");
        put("menu",           "menu.png");
        put("ban",            "table.png");
        put("nhanvien",       "employee.png");
        put("donhang",        "order.png");
        put("chedomlamviec",  "shift.png");
        put("baocao",         "report.png");
        put("thongke",        "stats.png");
        put("nhahangs",       "restaurant.png");
        put("bep",            "kitchen.png");
        put("phucvu",         "waiter.png");
        put("thungan",        "cashier.png");
        put("myrestaurant",   "myrestaurant.png");
        put("baomat",         "security.png");
        put("adminstats",     "adminstats.png");
    }};

    // ── BadgeButtons (Phase 7D) ───────────────────────────────────────────────
    private BadgeButton kitchenBadgeBtn;
    private BadgeButton waiterBadgeBtn;
    private BadgeButton cashierBadgeBtn;

    // ── Session timer ─────────────────────────────────────────────────────────
    private Timer sessionCheckTimer;
    private static final int SESSION_CHECK_MS = 30 * 60 * 1000;

    // ── Constructor ───────────────────────────────────────────────────────────

    public MainFrame() {
        super("He thong Quan ly Nha hang");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 780);
        setMinimumSize(new Dimension(960, 620));
        setLocationRelativeTo(null);
        AppSession.getInstance().addSessionListener(this);
        buildUI();
        startSessionCheckTimer();
    }

    // ── BadgeButton getters ───────────────────────────────────────────────────

    public BadgeButton getBtnKitchen() { return kitchenBadgeBtn; }
    public BadgeButton getBtnWaiter()  { return waiterBadgeBtn;  }
    public BadgeButton getBtnCashier() { return cashierBadgeBtn; }

    // ── SessionListener ───────────────────────────────────────────────────────

    @Override
    public void onLogout() {
        SwingUtilities.invokeLater(() -> {
            try {
                stopSessionCheckTimer();
                PollManager.getInstance().stopAll();
                this.dispose();
            } catch (Exception ex) {
                System.err.println("[MainFrame] onLogout cleanup error: " + ex.getMessage());
            }
            try {
                LoginDialog dlg = new LoginDialog(null);
                dlg.setVisible(true);
                if (dlg.isLoginSuccess()) {
                    try {
                        new MainFrame().setVisible(true);
                    } catch (Exception frameEx) {
                        frameEx.printStackTrace();
                        JOptionPane.showMessageDialog(null,
                                "Loi khoi tao giao dien: " + frameEx.getMessage(),
                                "Loi", JOptionPane.ERROR_MESSAGE);
                        LoginDialog retry = new LoginDialog(null);
                        retry.setVisible(true);
                        if (retry.isLoginSuccess()) new MainFrame().setVisible(true);
                        else System.exit(0);
                    }
                } else {
                    System.exit(0);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(1);
            }
        });
    }

    // ── Session timer ─────────────────────────────────────────────────────────

    private void startSessionCheckTimer() {
        sessionCheckTimer = new Timer(SESSION_CHECK_MS, e -> {
            String token = AppSession.getInstance().getSessionToken();
            boolean valid = TokenService.getInstance().validateToken(token);
            new Thread(() -> TokenService.getInstance().cleanExpiredTokens(), "token-cleanup").start();
            if (!valid) {
                JOptionPane.showMessageDialog(this,
                    "Phien lam viec da het han. Vui long dang nhap lai.",
                    "Het phien", JOptionPane.WARNING_MESSAGE);
                AppSession.getInstance().logout();
            }
        });
        sessionCheckTimer.setInitialDelay(SESSION_CHECK_MS);
        sessionCheckTimer.start();
    }

    private void stopSessionCheckTimer() {
        if (sessionCheckTimer != null && sessionCheckTimer.isRunning()) sessionCheckTimer.stop();
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    private void handleLogout() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Ban co chac muon dang xuat?",
                "Xac nhan dang xuat", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;
        PollManager.getInstance().stopAll();
        AppSession.getInstance().logout();
    }

    // ── Main UI build ─────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIConstants.COLOR_SURFACE);
        root.add(buildTopBar(), BorderLayout.NORTH);

        JPanel nav = buildSidebar();

        cardLayout  = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(UIConstants.COLOR_SURFACE);

        initPanels();
        wireContentArea();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, nav, contentArea);
        split.setDividerSize(0);
        split.setEnabled(false);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);

        setContentPane(root);
        navigateTo("home");
        applyRoleFilter();
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UIConstants.COLOR_SURFACE_CARD);
        bar.setPreferredSize(new Dimension(0, 52));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.COLOR_BORDER_SUBTLE),
            BorderFactory.createEmptyBorder(0, UIConstants.SPACING_XL, 0, UIConstants.SPACING_XL)));

        // Left: logo + app name
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SM, 0));
        left.setOpaque(false);

        JLabel logoMark = buildLogoMark(28);
        left.add(logoMark);

        JLabel appName = new JLabel("SmartRestaurant");
        appName.setFont(UIConstants.FONT_LOGO);
        appName.setForeground(UIConstants.COLOR_PRIMARY);
        left.add(appName);

        // Right: restaurant name + role + profile button
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_SM, 0));
        right.setOpaque(false);

        AppSession session = AppSession.getInstance();
        com.restaurant.session.RbacGuard guard = com.restaurant.session.RbacGuard.getInstance();

        // Restaurant name label
        if (session.getRestaurantId() != 0) {
            Restaurant r = com.restaurant.data.DataManager.getInstance().getMyRestaurant();
            if (r != null && r.getName() != null && !r.getName().isBlank()) {
                JLabel rName = new JLabel(r.getName());
                rName.setFont(UIConstants.FONT_SMALL);
                rName.setForeground(UIConstants.COLOR_TEXT_SECONDARY);
                right.add(rName);

                JLabel dot = new JLabel("·");
                dot.setForeground(UIConstants.COLOR_BORDER);
                right.add(dot);
            }
        }

        // Role badge
        AppBadge roleBadge = AppBadge.neutral(session.getRoleLabel());
        right.add(roleBadge);

        // Profile button
        if (session.hasPermission(com.restaurant.session.Permission.EDIT_OWN_PROFILE)) {
            AppButton btnProfile = AppButton.ghost(session.getUserName() + "  v");
            btnProfile.setFont(UIConstants.FONT_BODY);
            btnProfile.setForeground(UIConstants.COLOR_TEXT_PRIMARY);
            btnProfile.setToolTipText("Ho so cua toi");
            btnProfile.addActionListener(e -> {
                com.restaurant.ui.dialog.MyProfileDialog dlg =
                        new com.restaurant.ui.dialog.MyProfileDialog(this);
                dlg.setVisible(true);
            });
            right.add(btnProfile);
        }

        bar.add(left,  BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private JPanel buildSidebar() {
        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBackground(UIConstants.COLOR_SURFACE_CARD);
        nav.setPreferredSize(new Dimension(NAV_WIDTH, 0));
        nav.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, UIConstants.COLOR_BORDER_SUBTLE),
            new EmptyBorder(UIConstants.SPACING_LG,
                            UIConstants.SPACING_MD,
                            UIConstants.SPACING_MD,
                            UIConstants.SPACING_MD)));

        // Nav item list
        navButtons = new JButton[NAV_PAGES.length];
        for (int i = 0; i < NAV_PAGES.length; i++) {
            final String page = NAV_PAGES[i];
            JButton btn;

            if ("bep".equals(NAV_PAGES[i])) {
                BadgeButton bb = new BadgeButton(NAV_LABELS[i]);
                styleNavButton(bb, NAV_PAGES[i]);
                kitchenBadgeBtn = bb;
                btn = bb;
            } else if ("phucvu".equals(NAV_PAGES[i])) {
                BadgeButton bb = new BadgeButton(NAV_LABELS[i]);
                styleNavButton(bb, NAV_PAGES[i]);
                waiterBadgeBtn = bb;
                btn = bb;
            } else if ("thungan".equals(NAV_PAGES[i])) {
                BadgeButton bb = new BadgeButton(NAV_LABELS[i]);
                styleNavButton(bb, NAV_PAGES[i]);
                cashierBadgeBtn = bb;
                btn = bb;
            } else {
                btn = buildNavButton(NAV_LABELS[i], NAV_PAGES[i]);
            }

            final int idx = i;
            btn.addActionListener(e -> navigateTo(NAV_PAGES[idx]));
            navButtons[i] = btn;
            nav.add(btn);
            nav.add(Box.createVerticalStrut(2));
        }

        nav.add(Box.createVerticalGlue());

        // Separator
        JSeparator sep = new JSeparator();
        sep.setForeground(UIConstants.COLOR_BORDER_SUBTLE);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        nav.add(sep);
        nav.add(Box.createVerticalStrut(UIConstants.SPACING_MD));

        // User info panel
        nav.add(buildUserInfoPanel());

        return nav;
    }

    // ── Nav button ────────────────────────────────────────────────────────────

    /**
     * Creates a standard nav JButton with icon (if available) + text label.
     * Active / hover states are painted via custom paintComponent.
     */
    private JButton buildNavButton(String label, String pageKey) {
        Icon icon = loadNavIcon(pageKey);

        NavButton btn = new NavButton(label, icon);
        styleNavButton(btn, pageKey);
        return btn;
    }

    private void styleNavButton(JButton btn, String pageKey) {
        btn.setFont(UIConstants.FONT_NAV);
        btn.setForeground(UIConstants.COLOR_TEXT_SECONDARY);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setHorizontalTextPosition(SwingConstants.RIGHT);
        btn.setIconTextGap(UIConstants.SPACING_SM);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIConstants.SIZE_NAV_HEIGHT));
        btn.setPreferredSize(new Dimension(NAV_WIDTH - UIConstants.SPACING_MD * 2,
                                           UIConstants.SIZE_NAV_HEIGHT));
        btn.setBorder(new EmptyBorder(0, UIConstants.SPACING_MD, 0, UIConstants.SPACING_MD));
    }

    /** Tries to load a 16×16 icon from the classpath at /icons/{filename}. */
    private Icon loadNavIcon(String pageKey) {
        String filename = NAV_ICONS.get(pageKey);
        if (filename == null) return null;
        try {
            URL url = getClass().getResource("/icons/" + filename);
            if (url == null) return null;
            ImageIcon raw = new ImageIcon(url);
            Image scaled = raw.getImage().getScaledInstance(
                UIConstants.SIZE_ICON_MD, UIConstants.SIZE_ICON_MD, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── User info panel ───────────────────────────────────────────────────────

    private JPanel buildUserInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        AppSession session = AppSession.getInstance();

        // Avatar row
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        AvatarLabel avatar = new AvatarLabel(initials(session.getUserName()), 34);
        row.add(avatar);

        JPanel nameBlock = new JPanel();
        nameBlock.setOpaque(false);
        nameBlock.setLayout(new BoxLayout(nameBlock, BoxLayout.Y_AXIS));

        JLabel lblName = new JLabel(session.getUserName());
        lblName.setFont(UIConstants.FONT_BOLD);
        lblName.setForeground(UIConstants.COLOR_TEXT_PRIMARY);

        JLabel lblRole = new JLabel(session.getRoleLabel());
        lblRole.setFont(UIConstants.FONT_SMALL);
        lblRole.setForeground(UIConstants.COLOR_TEXT_SECONDARY);

        nameBlock.add(lblName);
        nameBlock.add(lblRole);
        row.add(nameBlock);

        panel.add(row);
        panel.add(Box.createVerticalStrut(UIConstants.SPACING_SM));

        // Logout button
        AppButton btnLogout = AppButton.ghost("Dang xuat");
        btnLogout.setForeground(UIConstants.COLOR_DANGER);
        btnLogout.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIConstants.SIZE_BTN_HEIGHT_SM));
        btnLogout.setPreferredSize(new Dimension(NAV_WIDTH - UIConstants.SPACING_MD * 2,
                                                  UIConstants.SIZE_BTN_HEIGHT_SM));
        btnLogout.addActionListener(e -> handleLogout());
        panel.add(btnLogout);

        return panel;
    }

    // ── Panel initialisation ──────────────────────────────────────────────────

    private void initPanels() {
        homePanel     = new HomePanel();
        menuPanel     = new MenuPanel();
        tablePanel    = new TablePanel();
        employeePanel = new EmployeePanel();
        orderPanel    = new OrderPanel();
        reportPanel   = new ReportPanel();
        statsPanel    = new StatsPanel();

        com.restaurant.session.RbacGuard guard = com.restaurant.session.RbacGuard.getInstance();
        if (guard.isSuperAdmin()) {
            restaurantPanel       = new RestaurantPanel();
            restaurantDetailPanel = new RestaurantDetailPanel(() -> navigateTo("nhahangs"));
            adminStatsPanel       = new AdminStatsPanel();
            auditLogPanel         = new AuditLogPanel();
        }
        kitchenPanel       = new KitchenPanel();
        waiterServicePanel = new WaiterServicePanel();
        cashierPanel       = new CashierPanel();
        if (guard.isRestaurantAdmin()) {
            myRestaurantPanel = new MyRestaurantInfoPanel();
        }
    }

    private void wireContentArea() {
        contentArea.add(homePanel,     "home");
        contentArea.add(menuPanel,     "menu");
        contentArea.add(tablePanel,    "ban");
        contentArea.add(employeePanel, "nhanvien");
        contentArea.add(orderPanel,    "donhang");
        contentArea.add(buildPlaceholder("Che do lam viec"), "chedomlamviec");
        contentArea.add(reportPanel,   "baocao");
        contentArea.add(statsPanel,    "thongke");
        contentArea.add(restaurantPanel    != null ? restaurantPanel
                : buildPlaceholder("Nha hang"), "nhahangs");
        contentArea.add(restaurantDetailPanel != null ? restaurantDetailPanel
                : buildPlaceholder("Chi tiet nha hang"), "restaurant_detail");
        contentArea.add(kitchenPanel,       "bep");
        contentArea.add(waiterServicePanel, "phucvu");
        contentArea.add(cashierPanel,       "thungan");
        contentArea.add(myRestaurantPanel != null ? myRestaurantPanel
                : buildPlaceholder("Nha hang cua toi"), "myrestaurant");
        contentArea.add(adminStatsPanel != null ? adminStatsPanel
                : buildPlaceholder("Thong ke Admin"), "adminstats");
        contentArea.add(auditLogPanel != null ? auditLogPanel
                : buildPlaceholder("Nhat ky bao mat"), "baomat");
    }

    // ── RBAC filter ───────────────────────────────────────────────────────────

    private void applyRoleFilter() {
        AppSession session = AppSession.getInstance();
        com.restaurant.session.RbacGuard guard = com.restaurant.session.RbacGuard.getInstance();
        boolean isSuperAdmin = guard.isSuperAdmin();
        java.util.Set<com.restaurant.session.Permission> perms =
                com.restaurant.session.Permission.forRole(session.getUserRole());

        for (int i = 0; i < NAV_PAGES.length; i++) {
            switch (NAV_PAGES[i]) {
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
                case "nhahangs":    navButtons[i].setVisible(isSuperAdmin); break;
                case "myrestaurant":navButtons[i].setVisible(guard.isRestaurantAdmin()); break;
                case "baomat":      navButtons[i].setVisible(isSuperAdmin); break;
                case "adminstats":  navButtons[i].setVisible(isSuperAdmin); break;
                default: break;
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public void navigateTo(String page) {
        cardLayout.show(contentArea, page);
        switch (page) {
            case "home":       homePanel.refresh();              break;
            case "menu":       menuPanel.loadData();             break;
            case "ban":        tablePanel.loadData();            break;
            case "nhanvien":   employeePanel.loadData();         break;
            case "donhang":    orderPanel.loadData();            break;
            case "baocao":     reportPanel.loadData();           break;
            case "thongke":    statsPanel.loadAll();             break;
            case "adminstats":
                if (adminStatsPanel != null) adminStatsPanel.loadStats();   break;
            case "nhahangs":
                if (restaurantPanel != null) restaurantPanel.loadData();    break;
            case "bep":        kitchenPanel.loadData();          break;
            case "phucvu":     waiterServicePanel.loadData();    break;
            case "thungan":    cashierPanel.loadData();          break;
            case "myrestaurant":
                if (myRestaurantPanel != null) myRestaurantPanel.loadData(); break;
            default: break;
        }
        updateNavSelection(page);
    }

    public void showRestaurantDetail(Restaurant r) {
        if (restaurantDetailPanel == null) return;
        restaurantDetailPanel.populate(r);
        cardLayout.show(contentArea, "restaurant_detail");
        updateNavSelection("nhahangs");
    }

    private void updateNavSelection(String activePage) {
        for (int i = 0; i < NAV_PAGES.length; i++) {
            boolean active = NAV_PAGES[i].equals(activePage);
            navButtons[i].putClientProperty("active", active);
            navButtons[i].setForeground(active
                    ? UIConstants.COLOR_PRIMARY
                    : UIConstants.COLOR_TEXT_SECONDARY);
            navButtons[i].setFont(active ? UIConstants.FONT_BOLD : UIConstants.FONT_NAV);
            navButtons[i].repaint();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JPanel buildPlaceholder(String name) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(UIConstants.COLOR_SURFACE);
        JLabel lbl = new JLabel(name + " — Dang phat trien");
        lbl.setFont(UIConstants.FONT_TITLE);
        lbl.setForeground(UIConstants.COLOR_TEXT_TERTIARY);
        p.add(lbl);
        return p;
    }

    /** Builds the small rounded-square logo mark used in the top bar. */
    private JLabel buildLogoMark(int size) {
        JLabel mark = new JLabel("SR") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.COLOR_PRIMARY);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(),
                        UIConstants.RADIUS_MD * 2f, UIConstants.RADIUS_MD * 2f));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        mark.setFont(new Font(UIConstants.FONT_FAMILY, Font.BOLD, (int)(size * 0.45)));
        mark.setForeground(Color.WHITE);
        mark.setHorizontalAlignment(SwingConstants.CENTER);
        mark.setPreferredSize(new Dimension(size, size));
        mark.setOpaque(false);
        return mark;
    }

    /** Returns up-to-two-letter initials from a display name. */
    private static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    // =========================================================================
    // Inner classes
    // =========================================================================

    // ── NavButton ──────────────────────────────────────────────────────────

    /**
     * A JButton variant that paints its own active/hover background as a
     * rounded rectangle. Compatible with WindowBuilder (named inner class).
     */
    static class NavButton extends JButton {

        private boolean hovered = false;

        NavButton(String text, Icon icon) {
            super(text, icon);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited (MouseEvent e) { hovered = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);

            boolean active = Boolean.TRUE.equals(getClientProperty("active"));

            if (active) {
                // Filled highlight pill
                g2.setColor(UIConstants.COLOR_PRIMARY_LIGHT);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(),
                        UIConstants.RADIUS_LG * 2f, UIConstants.RADIUS_LG * 2f));

                // Left accent stripe
                g2.setColor(UIConstants.COLOR_PRIMARY);
                g2.fill(new RoundRectangle2D.Float(0, 6, 3, getHeight() - 12,
                        3, 3));
            } else if (hovered) {
                g2.setColor(UIConstants.COLOR_NEUTRAL_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(),
                        UIConstants.RADIUS_LG * 2f, UIConstants.RADIUS_LG * 2f));
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ── AvatarLabel ────────────────────────────────────────────────────────

    /**
     * A fixed-size circular label showing text initials on a primary-tinted
     * background. Used in the user info panel at the bottom of the sidebar.
     */
    static class AvatarLabel extends JLabel {

        AvatarLabel(String initials, int size) {
            super(initials);
            setFont(new Font(UIConstants.FONT_FAMILY, Font.BOLD, (int)(size * 0.38)));
            setForeground(Color.WHITE);
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setOpaque(false);
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UIConstants.COLOR_PRIMARY);
            g2.fillOval(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}