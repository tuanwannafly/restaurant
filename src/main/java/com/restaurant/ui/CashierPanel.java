package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.restaurant.dao.OrderDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.data.DataManager;
import com.restaurant.model.Order;
import com.restaurant.model.TableItem;
import com.restaurant.ui.dialog.CashierPaymentDialog;

/**
 * CashierPanel — Phase 7C (Polling + Toast delta).
 *
 * <p>Thay đổi so với phiên bản cũ:
 * <ul>
 *   <li>Dùng {@link InlineErrorBar#show} thay vì {@link ToastNotification} để
 *       hiện lỗi tải dữ liệu trong {@link #doPoll()} và {@link #loadData()}.</li>
 *   <li>Thêm {@link SimpleSpinner} vào {@link #buildTitleBar()} — hiện khi đang
 *       tải và ẩn khi xong.</li>
 * </ul>
 */
public class CashierPanel extends JPanel {

    // ─── Inner model: PaymentRequest ─────────────────────────────────────────

    public static class PaymentRequest {

        public enum PaymentMethod {
            CASH("Tiền mặt"),
            BANK_TRANSFER("Chuyển khoản"),
            CARD("Thẻ"),
            MOMO("MoMo"),
            VNPAY("VNPay");

            private final String label;
            PaymentMethod(String label) { this.label = label; }
            public String getLabel() { return label; }
        }

        public final String        orderId;
        public final String        tableId;
        public final String        tableName;
        public final double        totalAmount;
        public final PaymentMethod paymentMethod;

        public PaymentRequest(String orderId, String tableId, String tableName,
                              double totalAmount, PaymentMethod paymentMethod) {
            this.orderId       = orderId;
            this.tableId       = tableId;
            this.tableName     = tableName;
            this.totalAmount   = totalAmount;
            this.paymentMethod = paymentMethod;
        }

        public String getPaymentMethodLabel() {
            return paymentMethod != null ? paymentMethod.getLabel() : "Tiền mặt";
        }
    }

    // ─── Constants ───────────────────────────────────────────────────────────

    private static final int    POLL_INTERVAL_MS = 5_000;
    private static final String POLL_KEY         = "cashier";

    // ─── UI components ───────────────────────────────────────────────────────

    private JPanel        pendingColumn;
    private JPanel        processingColumn;
    private SimpleSpinner spinner;

    private final List<PaymentRequest> pendingList    = new ArrayList<>();
    private final List<PaymentRequest> processingList = new ArrayList<>();

    // ─── Polling state ────────────────────────────────────────────────────────

    private int lastPaymentCount = -1;

    // ─── DAO ─────────────────────────────────────────────────────────────────

    private final OrderDAO orderDAO = new OrderDAO();
    private final TableDAO tableDAO = new TableDAO();

    // ─── Constructor ─────────────────────────────────────────────────────────

    public CashierPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BG_WHITE);
        buildUI();
        setupComponentListener();
    }

    // ─── UI Construction ─────────────────────────────────────────────────────

    private void buildUI() {
        String rName = "";
        try {
            String name = DataManager.getInstance().getMyRestaurant().getName();
            if (name != null && !name.isEmpty()) rName = name;
        } catch (Exception ignored) {}

        add(StaffHeader.create("Thu ngân", rName, null), BorderLayout.NORTH);

        JPanel centerArea = new JPanel(new BorderLayout());
        centerArea.setBackground(UIConstants.BG_WHITE);
        centerArea.add(buildTitleBar(),   BorderLayout.NORTH);
        centerArea.add(buildTwoColumns(), BorderLayout.CENTER);

        add(centerArea, BorderLayout.CENTER);
    }

    /**
     * Thanh phụ: tiêu đề + spinner (loading) + nút "↻ Làm mới".
     */
    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UIConstants.BG_WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 24, 12, 24)));

        JLabel title = new JLabel("Thu ngân – Thanh toán");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(UIConstants.TEXT_PRIMARY);

        // Spinner + nút Làm mới phía EAST
        spinner = new SimpleSpinner(20, UIConstants.PRIMARY);
        spinner.setVisible(false);

        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        eastPanel.setOpaque(false);
        eastPanel.add(spinner);
        eastPanel.add(buildRefreshButton());

        bar.add(title,     BorderLayout.WEST);
        bar.add(eastPanel, BorderLayout.EAST);
        return bar;
    }

    private JButton buildRefreshButton() {
        JButton btn = new JButton("↻  Làm mới") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed()
                        ? UIConstants.PRIMARY_DARK : UIConstants.PRIMARY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(UIConstants.FONT_BODY);
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(110, 34));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> loadData());
        return btn;
    }

    private JPanel buildTwoColumns() {
        JPanel wrapper = new JPanel(new GridLayout(1, 2, 16, 0));
        wrapper.setBackground(UIConstants.BG_WHITE);
        wrapper.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        wrapper.add(buildColumnPanel("Chờ thanh toán",
                new Color(0xFFF8E1), new Color(0xF59E0B), true));
        wrapper.add(buildColumnPanel("Đang xử lý",
                new Color(0xE8F5E9), new Color(0x2E7D32), false));

        return wrapper;
    }

    private JPanel buildColumnPanel(String title, Color headerBg, Color headerFg,
                                    boolean isPending) {
        JPanel outer = new JPanel(new BorderLayout(0, 12));
        outer.setBackground(UIConstants.BG_WHITE);

        JLabel badge = new JLabel(title, SwingConstants.CENTER);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 14));
        badge.setForeground(headerFg);
        badge.setOpaque(true);
        badge.setBackground(headerBg);
        badge.setPreferredSize(new Dimension(0, 36));
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(headerFg, 1, true),
                BorderFactory.createEmptyBorder(4, 16, 4, 16)));

        JPanel cards = new JPanel();
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        cards.setBackground(UIConstants.BG_WHITE);

        if (isPending) pendingColumn    = cards;
        else           processingColumn = cards;

        JScrollPane scroll = new JScrollPane(cards);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UIConstants.BG_WHITE);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        outer.add(badge,  BorderLayout.NORTH);
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    // ─── ComponentListener ────────────────────────────────────────────────────

    private void setupComponentListener() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadData();
                PollManager.getInstance().register(
                        POLL_KEY,
                        CashierPanel.this::doPoll,
                        POLL_INTERVAL_MS);
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                PollManager.getInstance().unregister(POLL_KEY);
                lastPaymentCount = -1;
            }
        });
    }

    // ─── doPoll ───────────────────────────────────────────────────────────────

    private void doPoll() {
        if (spinner != null) {
            spinner.setVisible(true);
            spinner.start();
        }

        new SwingWorker<List<PaymentRequest>, Void>() {

            @Override
            protected List<PaymentRequest> doInBackground() {
                return orderDAO.getAll().stream()
                        .filter(CashierPanel::isActiveForCashier)
                        .map(CashierPanel::toPaymentRequest)
                        .collect(Collectors.toList());
            }

            @Override
            protected void done() {
                if (spinner != null) {
                    spinner.stop();
                    spinner.setVisible(false);
                }

                List<PaymentRequest> loaded;
                try {
                    loaded = get();
                } catch (ExecutionException | InterruptedException ex) {
                    InlineErrorBar.show(CashierPanel.this,
                            "Lỗi tải dữ liệu: " + ex.getMessage());
                    return;
                }

                List<String> processingIds = processingList.stream()
                        .map(r -> r.orderId)
                        .collect(Collectors.toList());

                List<PaymentRequest> filtered = loaded.stream()
                        .filter(r -> !processingIds.contains(r.orderId))
                        .collect(Collectors.toList());

                pendingList.clear();
                pendingList.addAll(filtered);
                rebuildPendingColumn();

                int newCount = pendingList.size();
                if (lastPaymentCount >= 0 && newCount > lastPaymentCount) {
                    int diff = newCount - lastPaymentCount;
                    ToastNotification.show(
                            CashierPanel.this,
                            "Có " + diff + " yêu cầu thanh toán mới!",
                            ToastNotification.Type.INFO);
                }
                lastPaymentCount = newCount;
            }
        }.execute();
    }

    // ─── loadData ─────────────────────────────────────────────────────────────

    public void loadData() {
        if (spinner != null) {
            spinner.setVisible(true);
            spinner.start();
        }

        new SwingWorker<List<PaymentRequest>, Void>() {

            @Override
            protected List<PaymentRequest> doInBackground() {
                return orderDAO.getAll().stream()
                        .filter(CashierPanel::isActiveForCashier)
                        .map(CashierPanel::toPaymentRequest)
                        .collect(Collectors.toList());
            }

            @Override
            protected void done() {
                if (spinner != null) {
                    spinner.stop();
                    spinner.setVisible(false);
                }

                try {
                    List<PaymentRequest> loaded = get();

                    List<String> processingIds = processingList.stream()
                            .map(r -> r.orderId)
                            .collect(Collectors.toList());

                    List<PaymentRequest> filtered = loaded.stream()
                            .filter(r -> !processingIds.contains(r.orderId))
                            .collect(Collectors.toList());

                    pendingList.clear();
                    pendingList.addAll(filtered);
                    rebuildPendingColumn();

                    lastPaymentCount = pendingList.size();

                } catch (ExecutionException | InterruptedException e) {
                    InlineErrorBar.show(CashierPanel.this,
                            "Lỗi tải dữ liệu: " + e.getMessage());
                }
            }
        }.execute();
    }

    // ─── Dialog callback → moveToInProgress ──────────────────────────────────

    private void moveToInProgress(PaymentRequest req, String employeeName) {
        pendingList.removeIf(r -> r.orderId.equals(req.orderId));
        rebuildPendingColumn();

        processingList.add(req);
        rebuildProcessingColumn(employeeName, req);
    }

    // ─── Complete payment ─────────────────────────────────────────────────────

    private void completePayment(PaymentRequest req) {
        new SwingWorker<Boolean, Void>() {

            @Override
            protected Boolean doInBackground() {
                boolean ok = orderDAO.completeOrder(req.orderId);
                if (ok) {
                    tableDAO.updateStatus(req.tableId, TableItem.Status.DIRTY);
                }
                return ok;
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    if (ok) {
                        processingList.removeIf(r -> r.orderId.equals(req.orderId));
                        rebuildProcessingColumnFull();
                        ToastNotification.show(CashierPanel.this,
                                req.tableName + " – Thanh toán hoàn tất!",
                                ToastNotification.Type.SUCCESS);
                    } else {
                        ToastNotification.show(CashierPanel.this,
                                "Không thể hoàn tất đơn #" + req.orderId,
                                ToastNotification.Type.ERROR);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    InlineErrorBar.show(CashierPanel.this,
                            "Lỗi thanh toán: " + e.getMessage());
                }
            }
        }.execute();
    }

    // ─── Rebuild columns ─────────────────────────────────────────────────────

    private void rebuildPendingColumn() {
        pendingColumn.removeAll();

        if (pendingList.isEmpty()) {
            pendingColumn.add(
                    new EmptyStatePanel("💳", "Không có bàn nào chờ thanh toán", null));
        } else {
            for (PaymentRequest req : pendingList) {
                pendingColumn.add(buildPendingCard(req));
                pendingColumn.add(Box.createVerticalStrut(10));
            }
        }

        pendingColumn.revalidate();
        pendingColumn.repaint();
    }

    private void rebuildProcessingColumnFull() {
        processingColumn.removeAll();

        if (processingList.isEmpty()) {
            processingColumn.add(
                    new EmptyStatePanel("✅", "Chưa có đơn nào đang xử lý", null));
        } else {
            for (PaymentRequest req : processingList) {
                processingColumn.add(buildProcessingCard(req, null));
                processingColumn.add(Box.createVerticalStrut(10));
            }
        }

        processingColumn.revalidate();
        processingColumn.repaint();
    }

    private void rebuildProcessingColumn(String employeeName, PaymentRequest newReq) {
        if (processingColumn.getComponentCount() == 1
                && processingColumn.getComponent(0) instanceof EmptyStatePanel) {
            processingColumn.removeAll();
        }

        processingColumn.add(buildProcessingCard(newReq, employeeName));
        processingColumn.add(Box.createVerticalStrut(10));

        processingColumn.revalidate();
        processingColumn.repaint();
    }

    // ─── Card builders ────────────────────────────────────────────────────────

    private JPanel buildPendingCard(PaymentRequest req) {
        JPanel card = new CardPanel(UIConstants.BG_WHITE, UIConstants.BORDER_COLOR);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel tableLabel = new JLabel(req.tableName);
        tableLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        tableLabel.setForeground(UIConstants.TEXT_PRIMARY);

        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        info.setOpaque(false);

        JLabel amountLabel = new JLabel(formatAmount(req.totalAmount) + "đ");
        amountLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        amountLabel.setForeground(UIConstants.PRIMARY);

        JLabel sepLabel = new JLabel("  ·  ");
        sepLabel.setForeground(UIConstants.TEXT_SECONDARY);

        JLabel methodLabel = new JLabel(req.getPaymentMethodLabel());
        methodLabel.setFont(UIConstants.FONT_BODY);
        methodLabel.setForeground(UIConstants.TEXT_SECONDARY);

        info.add(amountLabel);
        info.add(sepLabel);
        info.add(methodLabel);

        JLabel pill = buildStatusPill("Chờ thanh toán",
                new Color(0xFFF8E1), new Color(0xF59E0B));

        card.add(tableLabel, BorderLayout.NORTH);
        card.add(info,        BorderLayout.CENTER);
        card.add(pill,        BorderLayout.SOUTH);

        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openPaymentDialog(req);
            }
        });

        return card;
    }

    private JPanel buildProcessingCard(PaymentRequest req, String employeeName) {
        JPanel card = new CardPanel(UIConstants.BG_WHITE, new Color(0x2E7D32));
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);

        JLabel tableLabel = new JLabel(req.tableName);
        tableLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        tableLabel.setForeground(UIConstants.TEXT_PRIMARY);

        JLabel amountLabel = new JLabel(formatAmount(req.totalAmount) + "đ");
        amountLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        amountLabel.setForeground(UIConstants.PRIMARY);

        headerRow.add(tableLabel,  BorderLayout.WEST);
        headerRow.add(amountLabel, BorderLayout.EAST);

        String staffText = (employeeName != null && !employeeName.isBlank())
                ? "Nhân viên: " + employeeName
                : req.getPaymentMethodLabel();
        JLabel staffLabel = new JLabel(staffText);
        staffLabel.setFont(UIConstants.FONT_BODY);
        staffLabel.setForeground(UIConstants.TEXT_SECONDARY);

        JButton btnDone = new JButton("✓  Hoàn tất") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed()
                        ? new Color(0x1B5E20) : new Color(0x2E7D32));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btnDone.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDone.setForeground(Color.WHITE);
        btnDone.setPreferredSize(new Dimension(Integer.MAX_VALUE, 32));
        btnDone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        btnDone.setBorderPainted(false);
        btnDone.setContentAreaFilled(false);
        btnDone.setFocusPainted(false);
        btnDone.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnDone.addActionListener(e -> completePayment(req));

        card.add(headerRow,  BorderLayout.NORTH);
        card.add(staffLabel, BorderLayout.CENTER);
        card.add(btnDone,    BorderLayout.SOUTH);

        return card;
    }

    // ─── Dialog ───────────────────────────────────────────────────────────────

    public void openPaymentDialog(PaymentRequest req) {
        CashierPaymentDialog.show(
                SwingUtilities.getWindowAncestor(this),
                req,
                employeeName -> moveToInProgress(req, employeeName)
        );
    }

    // ─── Utility components ───────────────────────────────────────────────────

    private static class CardPanel extends JPanel {
        private final Color border;

        CardPanel(Color bg, Color border) {
            this.border = border;
            setBackground(bg);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fillRoundRect(2, 3, getWidth() - 2, getHeight() - 2, 12, 12);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 2, getHeight() - 2, 12, 12);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, getWidth() - 3, getHeight() - 3, 12, 12);
            g2.dispose();
        }
    }

    private JLabel buildStatusPill(String text, Color bg, Color fg) {
        JLabel pill = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setOpaque(false);
        pill.setFont(new Font("Segoe UI", Font.BOLD, 11));
        pill.setForeground(fg);
        pill.setPreferredSize(new Dimension(120, 22));
        pill.setMaximumSize(new Dimension(140, 22));
        return pill;
    }

    // ─── Static helpers ───────────────────────────────────────────────────────

    private static boolean isActiveForCashier(Order o) {
        Order.Status s = o.getStatus();
        return s != Order.Status.COMPLETED
            && s != Order.Status.CANCELLED
            && s != Order.Status.DA_HUY
            && s != Order.Status.HOAN_THANH;
    }

    private static PaymentRequest toPaymentRequest(Order o) {
        String tableName = o.getTableName() != null && !o.getTableName().isBlank()
                ? "Bàn " + o.getTableName()
                : "Bàn #" + o.getTableId();
        return new PaymentRequest(
                o.getId(),
                o.getTableId(),
                tableName,
                o.getTotalAmount(),
                PaymentRequest.PaymentMethod.CASH);
    }

    private static String formatAmount(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format((long) amount);
    }
}