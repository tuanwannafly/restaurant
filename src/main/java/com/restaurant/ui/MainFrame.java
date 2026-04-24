package com.restaurant.ui;

import com.restaurant.session.AppSession;
import com.restaurant.session.AppSession.SessionListener;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame implements SessionListener {

    private CardLayout cardLayout;
    private JPanel contentArea;

    private HomePanel       homePanel;
    private MenuPanel       menuPanel;
    private TablePanel      tablePanel;
    private EmployeePanel   employeePanel;
    private OrderPanel      orderPanel;
    private ReportPanel     reportPanel;
    private StatsPanel      statsPanel;
    private RestaurantPanel restaurantPanel;

    private JButton[] navButtons;
    private String[]  navPages  = {"home", "menu", "ban", "nhanvien", "donhang", "chedomlamviec", "baocao", "thongke", "nhahangs"};
    private String[]  navLabels = {"🏠 Home", "Menu", "Bàn", "Nhân viên", "Đơn hàng", "Chế độ làm việc", "Báo cáo", "📈 Thống kê", "🏪 Nhà hàng"};

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
    }

    /**
     * Phase 5C: Được gọi khi AppSession.logout() kích hoạt.
     * Đóng MainFrame và mở lại LoginDialog trên EDT.
     */
    @Override
    public void onLogout() {
        SwingUtilities.invokeLater(() -> {
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

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIConstants.BG_WHITE);

        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel nav = buildNavBar();

        cardLayout  = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(UIConstants.BG_PAGE);

        homePanel       = new HomePanel();
        menuPanel       = new MenuPanel();
        tablePanel      = new TablePanel();
        employeePanel   = new EmployeePanel();
        orderPanel      = new OrderPanel();
        reportPanel     = new ReportPanel();
        statsPanel      = new StatsPanel();
        restaurantPanel = new RestaurantPanel();

        contentArea.add(homePanel,       "home");
        contentArea.add(menuPanel,       "menu");
        contentArea.add(tablePanel,      "ban");
        contentArea.add(employeePanel,   "nhanvien");
        contentArea.add(orderPanel,      "donhang");
        contentArea.add(buildPlaceholder("Che do lam viec"), "chedomlamviec");
        contentArea.add(reportPanel,     "baocao");
        contentArea.add(statsPanel,      "thongke");
        contentArea.add(restaurantPanel, "nhahangs");

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
        com.restaurant.session.RbacGuard guard = com.restaurant.session.RbacGuard.getInstance();

        for (int i = 0; i < navPages.length; i++) {
            switch (navPages[i]) {
                case "thongke":
                    // Chỉ Manager trở lên mới xem Thống kê
                    navButtons[i].setVisible(guard.isManagerOrAbove());
                    break;
                case "nhahangs":
                    // Chỉ SUPER_ADMIN mới thấy tab Nhà hàng
                    navButtons[i].setVisible(guard.isSuperAdmin());
                    break;
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
        JLabel userLbl = new JLabel("👤 " + session.getUserName()
                + "  [" + session.getRoleLabel() + "]");
        userLbl.setFont(UIConstants.FONT_BODY);
        userLbl.setForeground(UIConstants.TEXT_SECONDARY);
        right.add(userLbl);

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
            case "home":      homePanel.refresh();           break;
            case "menu":      menuPanel.loadData();          break;
            case "ban":       tablePanel.loadData();         break;
            case "nhanvien":  employeePanel.loadData();      break;
            case "donhang":   orderPanel.loadData();         break;
            case "baocao":    reportPanel.loadData();        break;
            case "thongke":   statsPanel.loadAll();          break;
            case "nhahangs":  restaurantPanel.loadData();    break;
        }
        for (int i = 0; i < navPages.length; i++) {
            boolean active = navPages[i].equals(page);
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