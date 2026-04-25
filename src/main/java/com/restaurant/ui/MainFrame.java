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
    // Phase 5: WaiterServicePanel
    private WaiterServicePanel  waiterServicePanel;
    // F3: MyRestaurantInfoPanel — chỉ cho RESTAURANT_ADMIN
    private MyRestaurantInfoPanel myRestaurantPanel;
    // Phase 5 Audit: AuditLogPanel — chỉ cho SUPER_ADMIN
    private AuditLogPanel         auditLogPanel;
    // Phase 3: RestaurantDetailPanel — chỉ cho SUPER_ADMIN
    private RestaurantDetailPanel restaurantDetailPanel;

    private AdminStatsPanel adminStatsPanel;

    private JButton[] navButtons;
    // Phase 5: thêm "phucvu" / "Phục vụ" vào arrays điều hướng
    private String[]  navPages  = {
        "home", "menu", "ban", "nhanvien", "donhang",
        "chedomlamviec", "baocao", "thongke", "nhahangs", "bep", "phucvu", "myrestaurant",
        "baomat", "adminstats"                               // ← thêm
    };
    private String[]  navLabels = {
        "🏠 Home", "Menu", "Bàn", "Nhân viên", "Đơn hàng",
        "Chế độ làm việc", "Báo cáo", "📈 Thống kê", "🏪 Nhà hàng", "🍳 Bếp", "🛎 Phục vụ",
        "🏪 Nhà hàng của tôi", "🔐 Bảo mật", "📊 Thống kê (Admin)"   // ← thêm
    };

    /**
     * Phase 6: Swing Timer kiểm tra session token mỗi 30 phút.
     * Dừng lại trong {@link #onLogout()} để tránh bắn event sau khi frame đã dispose.
     */
    private Timer sessionCheckTimer;

    /** Khoảng thời gian kiểm tra token (ms): 30 phút. */
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
        // Phase 5C: đăng ký để nhận sự kiện logout từ AppSession
        AppSession.getInstance().addSessionListener(this);
        buildUI();
        startSessionCheckTimer();
    }

    /**
     * Phase 5C: Được gọi khi AppSession.logout() kích hoạt.
     * Đóng MainFrame và mở lại LoginDialog trên EDT.
     */
    @Override
    public void onLogout() {
        SwingUtilities.invokeLater(() -> {
            // Phase 6: dừng session check timer trước khi đóng frame
            stopSessionCheckTimer();
            // Phase 7A: dừng toàn bộ polling timer trước khi đóng frame.
            // Gọi trước dispose() để tránh timer tiếp tục bắn event sau logout.
            PollManager.getInstance().stopAll();
            this.dispose();
            LoginDialog loginDialog = new LoginDialog(null);
            loginDialog.setVisible(true);
            if (loginDialog.isLoginSuccess()) {
                new MainFrame().setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }

    // ── Session Check Timer ────────────────────────────────────────────────────

    /**
     * Khởi động Swing Timer kiểm tra token mỗi {@value #SESSION_CHECK_INTERVAL_MS} ms.
     * <p>
     * Khi token không còn hợp lệ, timer gọi {@link AppSession#logout()} trên EDT,
     * hiện thông báo rồi để {@link #onLogout()} xử lý việc chuyển màn hình.
     * Dọn dẹp các token hết hạn trong DB qua
     * {@link TokenService#cleanExpiredTokens()} cũng được thực hiện tại đây.
     */
    private void startSessionCheckTimer() {
        sessionCheckTimer = new Timer(SESSION_CHECK_INTERVAL_MS, e -> {
            // Chạy trên EDT — an toàn để cập nhật UI
            String token = AppSession.getInstance().getSessionToken();
            boolean valid = TokenService.getInstance().validateToken(token);

            // Dọn dẹp token hết hạn trong DB (thực hiện ở background để không block EDT)
            new Thread(() -> TokenService.getInstance().cleanExpiredTokens(),
                       "token-cleanup").start();

            if (!valid) {
                JOptionPane.showMessageDialog(
                    this,
                    "Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.",
                    "Hết phiên",
                    JOptionPane.WARNING_MESSAGE
                );
                // logout() sẽ kích hoạt onLogout() → chuyển về LoginDialog
                AppSession.getInstance().logout();
            }
        });
        sessionCheckTimer.setInitialDelay(SESSION_CHECK_INTERVAL_MS); // không check ngay khi mở
        sessionCheckTimer.start();
    }

    /** Dừng session check timer — gọi trước khi dispose frame. */
    private void stopSessionCheckTimer() {
        if (sessionCheckTimer != null && sessionCheckTimer.isRunning()) {
            sessionCheckTimer.stop();
        }
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIConstants.BG_WHITE);

        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel nav = buildNavBar();

        cardLayout  = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(UIConstants.BG_PAGE);

        homePanel          = new HomePanel();
        menuPanel          = new MenuPanel();
        tablePanel         = new TablePanel();
        employeePanel      = new EmployeePanel();
        orderPanel         = new OrderPanel();
        reportPanel        = new ReportPanel();
        statsPanel         = new StatsPanel();
        // Chỉ khởi tạo RestaurantPanel khi SUPER_ADMIN — tránh SecurityException khi load dữ liệu
        com.restaurant.session.RbacGuard _guard = com.restaurant.session.RbacGuard.getInstance();
        if (_guard.isSuperAdmin()) {
            restaurantPanel       = new RestaurantPanel();
            restaurantDetailPanel = new RestaurantDetailPanel(() -> navigateTo("nhahangs"));
            adminStatsPanel       = new AdminStatsPanel();          // ← thêm
        }
        kitchenPanel       = new KitchenPanel();
        // Phase 5: khởi tạo WaiterServicePanel
        waiterServicePanel = new WaiterServicePanel();
        // F3: khởi tạo MyRestaurantInfoPanel chỉ khi RESTAURANT_ADMIN
        if (_guard.isRestaurantAdmin()) {
            myRestaurantPanel = new MyRestaurantInfoPanel();
        }
        // Phase 5 Audit: khởi tạo AuditLogPanel chỉ khi SUPER_ADMIN
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
        contentArea.add(restaurantPanel != null ? restaurantPanel : buildPlaceholder("Nha hang"), "nhahangs");
        contentArea.add(restaurantDetailPanel != null ? restaurantDetailPanel : buildPlaceholder("Chi tiet nha hang"), "restaurant_detail");
        contentArea.add(kitchenPanel,       "bep");
        // Phase 5: đăng ký WaiterServicePanel vào CardLayout
        contentArea.add(waiterServicePanel, "phucvu");
        // F3: đăng ký MyRestaurantInfoPanel vào CardLayout
        contentArea.add(myRestaurantPanel != null ? myRestaurantPanel
                : buildPlaceholder("Nhà hàng của tôi"), "myrestaurant");
        // Phase 5: đăng ký AdminStatsPanel vào CardLayout
        contentArea.add(adminStatsPanel != null ? adminStatsPanel
                : buildPlaceholder("Thống kê Admin"), "adminstats");

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, nav, contentArea);
        split.setDividerSize(0);
        split.setEnabled(false);
        split.setBorder(BorderFactory.createEmptyBorder());
        root.add(split, BorderLayout.CENTER);

        setContentPane(root);
        navigateTo("home");
        applyRoleFilter();
    }

    /** Ẩn/hiện tab dựa trên quyền người dùng. */
    private void applyRoleFilter() {
        AppSession session = AppSession.getInstance();
        com.restaurant.session.RbacGuard guard = com.restaurant.session.RbacGuard.getInstance();
        boolean isSuperAdmin = guard.isSuperAdmin();

        for (int i = 0; i < navPages.length; i++) {
            switch (navPages[i]) {
                // ── Các tab chỉ dành cho nhà hàng cụ thể — ẩn với SUPER_ADMIN ──
                case "menu":
                case "ban":
                case "donhang":
                case "chedomlamviec":
                    navButtons[i].setVisible(!isSuperAdmin);
                    break;

                case "nhanvien":
                    // Ẩn với SUPER_ADMIN; với role khác kiểm tra quyền VIEW_EMPLOYEE
                    navButtons[i].setVisible(
                            !isSuperAdmin &&
                            session.hasPermission(com.restaurant.session.Permission.VIEW_EMPLOYEE));
                    break;

                case "bep":
                    // Ẩn với SUPER_ADMIN; bếp là màn hình vận hành nhà hàng cụ thể
                    navButtons[i].setVisible(
                            !isSuperAdmin &&
                            session.hasPermission(com.restaurant.session.Permission.VIEW_KITCHEN));
                    break;

                case "phucvu":
                    // Ẩn với SUPER_ADMIN; phục vụ là màn hình vận hành nhà hàng cụ thể
                    navButtons[i].setVisible(
                            !isSuperAdmin &&
                            session.hasPermission(com.restaurant.session.Permission.VIEW_WAITER_SERVICE));
                    break;

                case "thongke":
                    // Thống kê theo nhà hàng — ẩn với SUPER_ADMIN (không có restaurant_id)
                    navButtons[i].setVisible(
                            !isSuperAdmin &&
                            session.hasPermission(com.restaurant.session.Permission.VIEW_STATS));
                    break;

                case "nhahangs":
                    // Quản lý toàn bộ nhà hàng — chỉ SUPER_ADMIN
                    navButtons[i].setVisible(isSuperAdmin);
                    break;

                case "myrestaurant":
                    // Thông tin nhà hàng của mình — chỉ RESTAURANT_ADMIN
                    navButtons[i].setVisible(guard.isRestaurantAdmin());
                    break;

                case "baomat":
                    // Nhật ký bảo mật — chỉ SUPER_ADMIN
                    navButtons[i].setVisible(isSuperAdmin);
                    break;

                case "adminstats":
                    // Thống kê toàn hệ thống — chỉ SUPER_ADMIN
                    navButtons[i].setVisible(isSuperAdmin);
                    break;

                // "home" và "baocao" luôn hiển thị (không cần case riêng)
                default:
                    break;
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

        // Hiển thị tên user đang đăng nhập ở bên phải
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        AppSession session = AppSession.getInstance();

        // F1: Nút "Hồ sơ của tôi" — hiển thị khi có quyền EDIT_OWN_PROFILE
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

        // G3: tên nhà hàng trong header — load từ cache (nhanh, SELECT by PK)
        if (session.getRestaurantId() != 0) {
            com.restaurant.model.Restaurant r =
                    com.restaurant.data.DataManager.getInstance().getMyRestaurant();
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
            JButton btn = createNavButton(navLabels[i]);
            btn.addActionListener(e -> navigateTo(page));
            navButtons[i] = btn;
            nav.add(btn);
            nav.add(Box.createVerticalStrut(4));
        }

        nav.add(Box.createVerticalGlue());

        // Logout
        JButton btnLogout = createNavButton("⏻  Đăng xuất");
        btnLogout.setForeground(UIConstants.DANGER);
        btnLogout.addActionListener(e -> doLogout());
        nav.add(btnLogout);
        return nav;
    }

    private void doLogout() {
        int r = JOptionPane.showConfirmDialog(this,
                "Bạn có muốn đăng xuất?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) return;
        // Phase 5C: chỉ cần gọi logout() — onLogout() listener xử lý UI tự động
        AppSession.getInstance().logout();
    }

    private JButton createNavButton(String label) {
        JButton btn = new JButton(label) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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
        return btn;
    }

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
                if (adminStatsPanel != null) adminStatsPanel.loadStats();
                break;
            case "nhahangs":  restaurantPanel.loadData();       break;
            case "bep":       kitchenPanel.loadData();          break;
            case "phucvu":    waiterServicePanel.loadData();    break;
            // F3: load dữ liệu khi điều hướng đến tab "Nhà hàng của tôi"
            case "myrestaurant":
                if (myRestaurantPanel != null) myRestaurantPanel.loadData();
                break;
        }
        for (int i = 0; i < navPages.length; i++) {
            boolean active = navPages[i].equals(page);
            navButtons[i].putClientProperty("active", active);
            navButtons[i].setForeground(active ? UIConstants.PRIMARY : UIConstants.TEXT_PRIMARY);
            navButtons[i].setFont(active ? UIConstants.FONT_BOLD : UIConstants.FONT_NAV);
            navButtons[i].repaint();
        }
    }

    /**
     * Phase 3: Điều hướng sang màn hình Chi tiết nhà hàng.
     * Gọi từ RestaurantPanel khi người dùng click "👁 Xem chi tiết".
     *
     * @param r nhà hàng cần hiển thị chi tiết
     */
    public void showRestaurantDetail(Restaurant r) {
        if (restaurantDetailPanel == null) return;
        restaurantDetailPanel.populate(r);
        cardLayout.show(contentArea, "restaurant_detail");
        // Bỏ highlight nav button — đây là sub-view của "nhahangs"
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