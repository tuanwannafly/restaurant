package com.restaurant.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.AbstractBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import com.restaurant.data.DataManager;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

/**
 * Màn hình Thu ngân (Cashier view) – Phase 5C: In-Progress Card + moveToInProgress.
 * <p>
 * Layout: Header (56px) | JSplitPane 50/50 (Chờ thanh toán | Đang thanh toán)
 * <p>
 * Phase 5C adds:
 * <ul>
 *   <li>{@link #buildInProgressCard(PaymentRequest, String)} – card UI cho cột phải với
 *       status label màu WARNING và nút "Hoàn thành"</li>
 *   <li>{@link #moveToInProgress(PaymentRequest)} – chuyển card từ cột trái sang cột phải</li>
 *   <li>{@link #completePayment(PaymentRequest)} – confirm dialog, nếu OK chuyển trạng thái
 *       về WAITING và reload UI (mock logic, Phase 5E sẽ gọi DAO)</li>
 *   <li>{@link #allInProgressRequests} – cache riêng cho cột phải</li>
 * </ul>
 */
public class CashierPanel extends JPanel {

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final int   REFRESH_MS    = 5_000;
    private static final Color CARD_HOVER_BG = new Color(0xF0F9FF);

    // ─── Inner class: PaymentRequest ──────────────────────────────────────────

    /**
     * Data class đại diện cho một đơn hàng đang chờ / đang xử lý thanh toán.
     */
    public static class PaymentRequest {

        /** Phương thức thanh toán. */
        public enum PaymentMethod {
            CASH,       // Tiền mặt
            TRANSFER    // Chuyển khoản
        }

        /** Trạng thái trong luồng thu ngân. */
        public enum Status {
            WAITING,       // Chờ thanh toán (cột trái)
            IN_PROGRESS    // Đang thanh toán (cột phải)
        }

        public final String        tableId;
        public final String        tableName;
        public final String        orderId;
        public final PaymentMethod paymentMethod;
        public final double        totalAmount;
        public       Status        status;

        /** Tên nhân viên đang xử lý – nullable (chỉ có khi IN_PROGRESS). */
        public String assignedStaff;

        public PaymentRequest(String tableId, String tableName, String orderId,
                              PaymentMethod paymentMethod, double totalAmount, Status status) {
            this.tableId       = tableId;
            this.tableName     = tableName;
            this.orderId       = orderId;
            this.paymentMethod = paymentMethod;
            this.totalAmount   = totalAmount;
            this.status        = status;
        }

        /** Nhãn hiển thị cho phương thức thanh toán. */
        public String getPaymentMethodLabel() {
            return paymentMethod == PaymentMethod.CASH ? "Tiền mặt" : "Chuyển khoản";
        }

        /** Nhãn hiển thị cho trạng thái. */
        public String getStatusLabel() {
            return status == Status.WAITING ? "Chờ thanh toán" : "Đang thanh toán";
        }
    }

    // ─── Fields ───────────────────────────────────────────────────────────────

    private JPanel pendingCardsPanel;
    private JPanel processingCardsPanel;

    /** Filter hiện tại: null = Tất cả, "CASH" hoặc "TRANSFER". */
    private String selectedPaymentFilter = null;

    /**
     * Cache danh sách WAITING – filter re-apply không cần reload.
     * Phase 5E sẽ thay bằng data từ DAO.
     */
    private List<PaymentRequest> allPendingRequests    = new ArrayList<>();

    /**
     * Cache danh sách IN_PROGRESS – cột phải.
     * Phase 5E sẽ thay bằng data từ DAO.
     */
    private List<PaymentRequest> allInProgressRequests = new ArrayList<>();

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

        ButtonGroup bg = new ButtonGroup();
        String[] labels = {"Tất cả", "Tiền mặt", "Chuyển khoản"};
        String[] keys   = {null,     "CASH",      "TRANSFER"};

        for (int i = 0; i < labels.length; i++) {
            final String key = keys[i];
            JToggleButton tb = makeCategoryToggle(labels[i]);
            if (i == 0) tb.setSelected(true);
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

        processingCardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 12));
        processingCardsPanel.setBackground(UIConstants.BG_PAGE);
        processingCardsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(processingCardsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // ─── buildPaymentCard (Phase 5B) ─────────────────────────────────────────

    /**
     * Tạo card UI cho một {@link PaymentRequest} trong cột "Chờ thanh toán".
     * Click → {@link #openPaymentDialog(PaymentRequest)}.
     *
     * @param req dữ liệu đơn cần hiển thị
     * @return JPanel card đã style
     */
    private JPanel buildPaymentCard(PaymentRequest req) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(8, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        card.setPreferredSize(new Dimension(180, 120));
        card.setMinimumSize(new Dimension(160, 0));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Row 1: Tên bàn
        JLabel nameLabel = new JLabel(req.tableName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);

        // Row 2: Phương thức thanh toán
        JLabel methodLabel = new JLabel(req.getPaymentMethodLabel());
        methodLabel.setFont(UIConstants.FONT_BODY);
        methodLabel.setForeground(UIConstants.TEXT_PRIMARY);
        methodLabel.setAlignmentX(LEFT_ALIGNMENT);

        // Row 3: Trạng thái
        JLabel statusLabel = new JLabel(req.getStatusLabel());
        statusLabel.setFont(UIConstants.FONT_BODY);
        statusLabel.setForeground(UIConstants.TEXT_SECONDARY);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        card.add(nameLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(methodLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(statusLabel);

        // Hover + Click
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { card.setBackground(CARD_HOVER_BG); }
            @Override public void mouseExited(MouseEvent e)  { card.setBackground(Color.WHITE); }
            @Override public void mouseClicked(MouseEvent e) { openPaymentDialog(req); }
        });

        return card;
    }

    // ─── buildInProgressCard (Phase 5C) ──────────────────────────────────────

    /**
     * Tạo card UI cho một {@link PaymentRequest} trong cột "Đang thanh toán".
     * <p>
     * Card hiển thị:
     * <ol>
     *   <li>Tên bàn (bold 14px, TEXT_PRIMARY)</li>
     *   <li>Label "Đang thanh toán" (màu {@link UIConstants#WARNING})</li>
     *   <li>Tên nhân viên đang xử lý (TEXT_PRIMARY)</li>
     *   <li>Nút "Hoàn thành" (PRIMARY, full-width) → {@link #completePayment(PaymentRequest)}</li>
     * </ol>
     * Bo góc 8px, border BORDER_COLOR. Tham chiếu Image 2 card "Bàn 07".
     *
     * @param req          dữ liệu đơn hàng đang xử lý
     * @param employeeName tên nhân viên đang phụ trách (hiển thị ở row 3)
     * @return JPanel card đã style
     */
    private JPanel buildInProgressCard(PaymentRequest req, String employeeName) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(8, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        card.setPreferredSize(new Dimension(180, 158));
        card.setMinimumSize(new Dimension(160, 0));

        // Row 1: Tên bàn (bold)
        JLabel nameLabel = new JLabel(req.tableName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);

        // Row 2: Trạng thái – WARNING color (amber) để phân biệt với cột trái
        JLabel statusLabel = new JLabel("Đang thanh toán");
        statusLabel.setFont(UIConstants.FONT_BODY);
        statusLabel.setForeground(UIConstants.WARNING);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        // Row 3: Tên nhân viên
        String displayName = (employeeName != null && !employeeName.isBlank())
                ? employeeName : "—";
        JLabel staffLabel = new JLabel(displayName);
        staffLabel.setFont(UIConstants.FONT_BODY);
        staffLabel.setForeground(UIConstants.TEXT_PRIMARY);
        staffLabel.setAlignmentX(LEFT_ALIGNMENT);

        // Row 4: Nút "Hoàn thành" – full width, PRIMARY fill
        JButton btnDone = new JButton("Hoàn thành") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                // Hover: sáng hơn một chút khi nhấn
                g2.setColor(getModel().isPressed()
                        ? UIConstants.PRIMARY_DARK : UIConstants.PRIMARY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btnDone.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDone.setForeground(Color.WHITE);
        btnDone.setBorderPainted(false);
        btnDone.setContentAreaFilled(false);
        btnDone.setFocusPainted(false);
        btnDone.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // full width: dùng maximumSize + alignmentX
        btnDone.setAlignmentX(LEFT_ALIGNMENT);
        btnDone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        btnDone.setPreferredSize(new Dimension(148, 32));
        btnDone.addActionListener(e -> completePayment(req));

        card.add(nameLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(statusLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(staffLabel);
        card.add(Box.createVerticalStrut(12));
        card.add(btnDone);

        return card;
    }

    // ─── moveToInProgress (Phase 5C) ─────────────────────────────────────────

    /**
     * Chuyển một đơn từ cột "Chờ thanh toán" sang cột "Đang thanh toán".
     * <p>
     * Luồng xử lý (mock – Phase 5E sẽ gọi DAO):
     * <ol>
     *   <li>Cập nhật {@code req.status = IN_PROGRESS}</li>
     *   <li>Xóa khỏi {@link #allPendingRequests}, thêm vào {@link #allInProgressRequests}</li>
     *   <li>Gán {@code req.assignedStaff} nếu chưa có (dùng tên người dùng hiện tại)</li>
     *   <li>Rebuild cả hai cột</li>
     * </ol>
     * Được gọi từ dialog Phase 5D khi nhấn "Thực hiện".
     *
     * @param req đơn hàng cần chuyển sang trạng thái IN_PROGRESS
     */
    public void moveToInProgress(PaymentRequest req) {
        // Bước 1: Cập nhật trạng thái
        req.status = PaymentRequest.Status.IN_PROGRESS;

        // Bước 2: Di chuyển giữa hai danh sách
        allPendingRequests.removeIf(r -> r.orderId.equals(req.orderId));
        if (allInProgressRequests.stream().noneMatch(r -> r.orderId.equals(req.orderId))) {
            allInProgressRequests.add(req);
        }

        // Bước 3: Gán tên nhân viên nếu chưa có
        if (req.assignedStaff == null || req.assignedStaff.isBlank()) {
            try {
                String currentUser = AppSession.getInstance().getUsername();
                req.assignedStaff = (currentUser != null && !currentUser.isBlank())
                        ? currentUser : "Nhân viên";
            } catch (Exception ignored) {
                req.assignedStaff = "Nhân viên";
            }
        }

        // Bước 4: Refresh UI
        applyPendingFilter();
        rebuildInProgressCards(allInProgressRequests);
    }

    // ─── completePayment (Phase 5C) ───────────────────────────────────────────

    /**
     * Xác nhận hoàn thành thanh toán cho một đơn trong cột phải.
     * <p>
     * Luồng xử lý (mock – Phase 5E sẽ gọi {@code OrderDAO.completeOrder}):
     * <ol>
     *   <li>Hiện JOptionPane xác nhận</li>
     *   <li>Nếu OK: xóa khỏi {@link #allInProgressRequests}, rebuild cột phải</li>
     *   <li>Phase 5E TODO: gọi {@code OrderDAO.completeOrder(req.orderId)} rồi loadData()</li>
     * </ol>
     *
     * @param req đơn hàng vừa hoàn thành thanh toán
     */
    private void completePayment(PaymentRequest req) {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Xác nhận hoàn thành thanh toán cho " + req.tableName + "?",
                "Hoàn thành thanh toán",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        // Mock: chỉ xóa khỏi danh sách in-progress và rebuild
        // Phase 5E: OrderDAO.completeOrder(req.orderId) → loadData()
        allInProgressRequests.removeIf(r -> r.orderId.equals(req.orderId));
        rebuildInProgressCards(allInProgressRequests);

        JOptionPane.showMessageDialog(
                this,
                req.tableName + " đã thanh toán thành công.",
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // ─── openPaymentDialog (Phase 5D) ─────────────────────────────────────────

    /**
     * Mở dialog thanh toán chi tiết cho đơn được chọn từ cột trái.
     * Phase 5D sẽ implement đầy đủ (chọn nhân viên, in hóa đơn, xác nhận "Thực hiện"
     * → gọi {@link #moveToInProgress(PaymentRequest)}).
     *
     * @param req đơn hàng được click
     */
    private void openPaymentDialog(PaymentRequest req) {
        // Phase 5D implement:
        // PaymentDialog dialog = new PaymentDialog(SwingUtilities.getWindowAncestor(this), req);
        // dialog.setOnConfirm(() -> moveToInProgress(req));
        // dialog.setVisible(true);
    }

    // ─── Data Loading ─────────────────────────────────────────────────────────

    /**
     * Điểm vào duy nhất để load/reload toàn bộ dữ liệu cả hai cột.
     * <p>
     * Phase 5B/5C: dùng mock data để test UI.
     * Phase 5E: thay bằng SwingWorker + OrderDAO.
     */
    public void loadData() {
        allPendingRequests    = buildMockPendingData();
        allInProgressRequests = buildMockInProgressData();
        applyPendingFilter();
        rebuildInProgressCards(allInProgressRequests);
    }

    // ─── Mock data (Phase 5B/5C – xóa khi kết nối DB ở Phase 5E) ────────────

    /**
     * Mock data cột trái (WAITING).
     * <strong>TODO Phase 5E:</strong> Thay bằng {@code OrderDAO.getPendingPaymentOrders(restaurantId)}.
     */
    private List<PaymentRequest> buildMockPendingData() {
        List<PaymentRequest> list = new ArrayList<>();
        list.add(new PaymentRequest("1", "Bàn 05", "1001",
                PaymentRequest.PaymentMethod.CASH,     250_000, PaymentRequest.Status.WAITING));
        list.add(new PaymentRequest("2", "Bàn 08", "1002",
                PaymentRequest.PaymentMethod.TRANSFER, 480_000, PaymentRequest.Status.WAITING));
        list.add(new PaymentRequest("3", "Bàn 12", "1003",
                PaymentRequest.PaymentMethod.CASH,     120_000, PaymentRequest.Status.WAITING));
        return list;
    }

    /**
     * Mock data cột phải (IN_PROGRESS).
     * <strong>TODO Phase 5E:</strong> Thay bằng {@code OrderDAO.getInProgressPaymentOrders(restaurantId)}.
     */
    private List<PaymentRequest> buildMockInProgressData() {
        List<PaymentRequest> list = new ArrayList<>();
        PaymentRequest pr = new PaymentRequest("7", "Bàn 07", "1007",
                PaymentRequest.PaymentMethod.CASH, 390_000, PaymentRequest.Status.IN_PROGRESS);
        pr.assignedStaff = "Nguyễn Thị Thanh";
        list.add(pr);
        return list;
    }

    // ─── Filter ───────────────────────────────────────────────────────────────

    /**
     * Lọc {@link #allPendingRequests} theo {@link #selectedPaymentFilter}
     * rồi rebuild cards cho cột trái.
     */
    private void applyPendingFilter() {
        List<PaymentRequest> filtered = allPendingRequests.stream()
                .filter(req -> {
                    if (selectedPaymentFilter == null) return true;
                    return selectedPaymentFilter.equals(
                            req.paymentMethod == PaymentRequest.PaymentMethod.CASH
                                    ? "CASH" : "TRANSFER");
                })
                .collect(Collectors.toList());
        rebuildPendingCards(filtered);
    }

    // ─── Rebuild Cards ────────────────────────────────────────────────────────

    private void rebuildPendingCards(List<PaymentRequest> list) {
        pendingCardsPanel.removeAll();

        if (list.isEmpty()) {
            pendingCardsPanel.setLayout(new BorderLayout());
            JLabel empty = new JLabel("Không có đơn nào chờ thanh toán ✅", SwingConstants.CENTER);
            empty.setFont(UIConstants.FONT_BODY);
            empty.setForeground(UIConstants.TEXT_SECONDARY);
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setOpaque(false);
            wrapper.add(empty);
            pendingCardsPanel.add(wrapper, BorderLayout.CENTER);
        } else {
            pendingCardsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 12, 12));
            for (PaymentRequest req : list) {
                pendingCardsPanel.add(buildPaymentCard(req));
            }
        }

        pendingCardsPanel.revalidate();
        pendingCardsPanel.repaint();
    }

    /**
     * Rebuild cột phải "Đang thanh toán" từ danh sách {@code list}.
     * Mỗi card được tạo bởi {@link #buildInProgressCard(PaymentRequest, String)}.
     *
     * @param list danh sách đơn đang xử lý
     */
    private void rebuildInProgressCards(List<PaymentRequest> list) {
        processingCardsPanel.removeAll();

        if (list.isEmpty()) {
            processingCardsPanel.setLayout(new BorderLayout());
            JLabel empty = new JLabel("Không có đơn nào đang xử lý ✅", SwingConstants.CENTER);
            empty.setFont(UIConstants.FONT_BODY);
            empty.setForeground(UIConstants.TEXT_SECONDARY);
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setOpaque(false);
            wrapper.add(empty);
            processingCardsPanel.add(wrapper, BorderLayout.CENTER);
        } else {
            processingCardsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 12, 12));
            for (PaymentRequest req : list) {
                String staff = (req.assignedStaff != null && !req.assignedStaff.isBlank())
                        ? req.assignedStaff : "—";
                processingCardsPanel.add(buildInProgressCard(req, staff));
            }
        }

        processingCardsPanel.revalidate();
        processingCardsPanel.repaint();
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

    // ─── RoundedBorder ────────────────────────────────────────────────────────

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

    // ─── WrapLayout ───────────────────────────────────────────────────────────

    private static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container t) { return layoutSize(t, true); }
        @Override public Dimension minimumLayoutSize(Container t) {
            Dimension d = layoutSize(t, false);
            d.width -= getHgap() + 1;
            return d;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
                int hgap = getHgap(), vgap = getVgap();
                Insets ins = target.getInsets();
                int maxWidth = targetWidth - (ins.left + ins.right + hgap * 2);
                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0, rowHeight = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    Component m = target.getComponent(i);
                    if (!m.isVisible()) continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                        dim.width = Math.max(dim.width, rowWidth);
                        if (dim.height > 0) dim.height += vgap;
                        dim.height += rowHeight;
                        rowWidth = 0; rowHeight = 0;
                    }
                    if (rowWidth != 0) rowWidth += hgap;
                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
                dim.width = Math.max(dim.width, rowWidth);
                if (dim.height > 0) dim.height += vgap;
                dim.height += rowHeight;
                dim.width  += ins.left + ins.right + hgap * 2;
                dim.height += ins.top  + ins.bottom + vgap * 2;
                return dim;
            }
        }
    }
}