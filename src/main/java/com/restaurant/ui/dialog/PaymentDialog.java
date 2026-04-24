package com.restaurant.ui.dialog;

import com.restaurant.dao.OrderDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.Order;
import com.restaurant.model.TableItem;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.ToastNotification;
import com.restaurant.ui.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * PaymentDialog — Phase 6
 *
 * <p>Dialog thanh toán dùng chung từ:
 * <ul>
 *   <li>{@code TablePanel} — cashier double-click bàn OCCUPIED</li>
 *   <li>{@code TableOrderFrame} — nút "Thanh toán" ở footer</li>
 * </ul>
 *
 * <p>Khi xác nhận:
 * <ol>
 *   <li>{@link OrderDAO#completeOrder(String)} — đánh dấu order COMPLETED</li>
 *   <li>{@link TableDAO#updateStatus(String, TableItem.Status)} — bàn → DIRTY</li>
 * </ol>
 */
public class PaymentDialog extends JDialog {

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final NumberFormat PRICE_FMT = NumberFormat.getInstance(new Locale("vi", "VN"));

    private static final String[] COLUMNS = {"Món", "SL", "Đơn giá (₫)", "Thành tiền (₫)"};

    // ─── State ────────────────────────────────────────────────────────────────

    private final String tableId;
    private final String orderId;

    private boolean paymentCompleted = false;

    // ─── DAO ─────────────────────────────────────────────────────────────────

    private final OrderDAO orderDAO = new OrderDAO();
    private final TableDAO tableDAO = new TableDAO();

    // ─── UI ──────────────────────────────────────────────────────────────────

    private DefaultTableModel tableModel;
    private JLabel            lblTotal;
    private JRadioButton      rboCash;
    private JRadioButton      rboTransfer;
    private JPanel            qrPanel;
    private RoundedButton     btnConfirm;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * @param parent    Frame chứa (dùng để định vị dialog)
     * @param tableId   ID bàn cần đóng
     * @param tableName Tên bàn hiển thị trên tiêu đề
     * @param orderId   ID đơn hàng cần thanh toán
     */
    public PaymentDialog(Frame parent, String tableId, String tableName, String orderId) {
        super(parent, "Thanh toán — Bàn " + tableName, ModalityType.APPLICATION_MODAL);
        this.tableId = tableId;
        this.orderId = orderId;

        setPreferredSize(new Dimension(520, 600));
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        buildUI(tableName);
        pack();
        setLocationRelativeTo(parent);

        loadItems();
    }

    // ─── UI Construction ──────────────────────────────────────────────────────

    private void buildUI(String tableName) {
        getContentPane().setBackground(UIConstants.BG_WHITE);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(tableName), BorderLayout.NORTH);
        add(buildCenter(),          BorderLayout.CENTER);
        add(buildFooter(),          BorderLayout.SOUTH);
    }

    // NORTH ───────────────────────────────────────────────────────────────────

    private JPanel buildHeader(String tableName) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.PRIMARY);
        panel.setBorder(new EmptyBorder(16, 24, 16, 24));

        JLabel title = new JLabel("Thanh toán — Bàn " + tableName);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);
        panel.add(title, BorderLayout.WEST);

        return panel;
    }

    // CENTER ──────────────────────────────────────────────────────────────────

    private JScrollPane buildCenter() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBackground(UIConstants.BG_WHITE);
        inner.setBorder(new EmptyBorder(20, 24, 12, 24));

        // ── Order items table ─────────────────────────────────────────────────
        JLabel lblItems = new JLabel("Chi tiết đơn hàng");
        lblItems.setFont(UIConstants.FONT_BOLD);
        lblItems.setForeground(UIConstants.TEXT_PRIMARY);
        lblItems.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(lblItems);
        inner.add(Box.createVerticalStrut(10));

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable itemsTable = new JTable(tableModel);
        itemsTable.setFont(UIConstants.FONT_BODY);
        itemsTable.setRowHeight(UIConstants.ROW_HEIGHT);
        itemsTable.setShowGrid(false);
        itemsTable.setIntercellSpacing(new Dimension(0, 0));
        itemsTable.getTableHeader().setFont(UIConstants.FONT_BOLD);
        itemsTable.getTableHeader().setBackground(UIConstants.HEADER_BG);
        itemsTable.getTableHeader().setForeground(UIConstants.TEXT_PRIMARY);
        itemsTable.getTableHeader().setReorderingAllowed(false);

        // Right-align numeric columns
        DefaultTableCellRenderer rightAlign = new DefaultTableCellRenderer();
        rightAlign.setHorizontalAlignment(SwingConstants.RIGHT);
        itemsTable.getColumnModel().getColumn(1).setCellRenderer(rightAlign);
        itemsTable.getColumnModel().getColumn(2).setCellRenderer(rightAlign);
        itemsTable.getColumnModel().getColumn(3).setCellRenderer(rightAlign);

        // Column widths
        itemsTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        itemsTable.getColumnModel().getColumn(1).setPreferredWidth(40);
        itemsTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        itemsTable.getColumnModel().getColumn(3).setPreferredWidth(120);

        JScrollPane tableScroll = new JScrollPane(itemsTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR));
        tableScroll.setPreferredSize(new Dimension(460, 200));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(tableScroll);

        // ── Separator ─────────────────────────────────────────────────────────
        inner.add(Box.createVerticalStrut(16));
        JSeparator sep = new JSeparator();
        sep.setForeground(UIConstants.BORDER_COLOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(sep);
        inner.add(Box.createVerticalStrut(16));

        // ── Total ─────────────────────────────────────────────────────────────
        JPanel totalRow = new JPanel(new BorderLayout());
        totalRow.setOpaque(false);
        totalRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        totalRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblTotalCaption = new JLabel("Tổng cộng:");
        lblTotalCaption.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTotalCaption.setForeground(UIConstants.TEXT_PRIMARY);
        totalRow.add(lblTotalCaption, BorderLayout.WEST);

        lblTotal = new JLabel("0 ₫");
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTotal.setForeground(UIConstants.DANGER);
        lblTotal.setHorizontalAlignment(SwingConstants.RIGHT);
        totalRow.add(lblTotal, BorderLayout.EAST);

        inner.add(totalRow);
        inner.add(Box.createVerticalStrut(20));

        // ── Payment method radio ──────────────────────────────────────────────
        JLabel lblMethod = new JLabel("Hình thức thanh toán");
        lblMethod.setFont(UIConstants.FONT_BOLD);
        lblMethod.setForeground(UIConstants.TEXT_PRIMARY);
        lblMethod.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(lblMethod);
        inner.add(Box.createVerticalStrut(10));

        ButtonGroup bg = new ButtonGroup();
        rboCash     = new JRadioButton("💵  Tiền mặt");
        rboTransfer = new JRadioButton("📱  Chuyển khoản (QR)");
        rboCash.setFont(UIConstants.FONT_BODY);
        rboTransfer.setFont(UIConstants.FONT_BODY);
        rboCash.setBackground(UIConstants.BG_WHITE);
        rboTransfer.setBackground(UIConstants.BG_WHITE);
        rboCash.setSelected(true);
        bg.add(rboCash);
        bg.add(rboTransfer);

        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        radioPanel.setOpaque(false);
        radioPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        radioPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        radioPanel.add(rboCash);
        radioPanel.add(Box.createHorizontalStrut(24));
        radioPanel.add(rboTransfer);
        inner.add(radioPanel);
        inner.add(Box.createVerticalStrut(16));

        // ── QR placeholder ────────────────────────────────────────────────────
        qrPanel = buildQrPanel();
        qrPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        qrPanel.setVisible(false);
        inner.add(qrPanel);

        // Toggle QR panel
        rboTransfer.addActionListener(e -> qrPanel.setVisible(true));
        rboCash.addActionListener(e -> qrPanel.setVisible(false));

        // Wrap in scroll
        JScrollPane scroll = new JScrollPane(inner);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        return scroll;
    }

    private JPanel buildQrPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.BG_PAGE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            new EmptyBorder(16, 0, 16, 0)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));

        JPanel placeholder = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw a simple QR-like placeholder grid
                g2.setColor(new Color(0xE5E7EB));
                int cell = 8;
                int size = 160;
                int ox   = (getWidth()  - size) / 2;
                int oy   = (getHeight() - size) / 2;

                boolean[][] pattern = generateQrPattern(size / cell);
                for (int row = 0; row < size / cell; row++) {
                    for (int col = 0; col < size / cell; col++) {
                        if (pattern[row][col]) {
                            g2.setColor(UIConstants.TEXT_PRIMARY);
                        } else {
                            g2.setColor(Color.WHITE);
                        }
                        g2.fillRect(ox + col * cell, oy + row * cell, cell, cell);
                    }
                }
                // Border
                g2.setColor(UIConstants.TEXT_PRIMARY);
                g2.setStroke(new BasicStroke(2));
                g2.drawRect(ox, oy, size, size);
                g2.dispose();
            }

            // Simple deterministic "QR-like" pattern
            private boolean[][] generateQrPattern(int n) {
                boolean[][] p = new boolean[n][n];
                // Corner finders
                fillFinder(p, 0, 0, 7, n);
                fillFinder(p, 0, n - 7, 7, n);
                fillFinder(p, n - 7, 0, 7, n);
                // Some "data" dots
                java.util.Random rnd = new java.util.Random(42);
                for (int r = 8; r < n - 8; r++) {
                    for (int c = 8; c < n - 8; c++) {
                        p[r][c] = rnd.nextBoolean();
                    }
                }
                return p;
            }

            private void fillFinder(boolean[][] p, int r, int c, int sz, int n) {
                for (int i = r; i < r + sz && i < n; i++) {
                    for (int j = c; j < c + sz && j < n; j++) {
                        boolean outer = (i == r || i == r + sz - 1 || j == c || j == c + sz - 1);
                        boolean inner = (i >= r + 2 && i <= r + sz - 3 && j >= c + 2 && j <= c + sz - 3);
                        p[i][j] = outer || inner;
                    }
                }
            }
        };
        placeholder.setPreferredSize(new Dimension(200, 200));
        placeholder.setBackground(Color.WHITE);

        JPanel centered = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centered.setOpaque(false);
        centered.add(placeholder);

        JLabel hint = new JLabel("QR code tài khoản ngân hàng", SwingConstants.CENTER);
        hint.setFont(UIConstants.FONT_SMALL);
        hint.setForeground(UIConstants.TEXT_SECONDARY);

        panel.add(centered, BorderLayout.CENTER);
        panel.add(hint,     BorderLayout.SOUTH);
        return panel;
    }

    // SOUTH ───────────────────────────────────────────────────────────────────

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        footer.setBackground(UIConstants.BG_WHITE);
        footer.setBorder(new MatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR));

        JButton btnCancel = new JButton("Hủy");
        btnCancel.setFont(UIConstants.FONT_BODY);
        btnCancel.setPreferredSize(new Dimension(100, UIConstants.BTN_HEIGHT));
        btnCancel.setFocusPainted(false);
        btnCancel.addActionListener(e -> dispose());

        btnConfirm = new RoundedButton("✅  Xác nhận thanh toán");
        btnConfirm.setFont(UIConstants.FONT_BOLD);
        btnConfirm.setPreferredSize(new Dimension(200, UIConstants.BTN_HEIGHT));
        btnConfirm.setEnabled(false);   // enabled sau khi load items thành công
        btnConfirm.addActionListener(e -> confirmPayment());

        footer.add(btnCancel);
        footer.add(btnConfirm);
        return footer;
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    private void loadItems() {
        new SwingWorker<List<Order.OrderItem>, Void>() {
            @Override
            protected List<Order.OrderItem> doInBackground() {
                return orderDAO.getItemsWithStatus(orderId);
            }

            @Override
            protected void done() {
                try {
                    List<Order.OrderItem> items = get();
                    populateTable(items);
                    btnConfirm.setEnabled(!items.isEmpty());
                } catch (InterruptedException | ExecutionException ex) {
                    System.err.println("[PaymentDialog] loadItems lỗi: " + ex.getMessage());
                    ToastNotification.show(
                        PaymentDialog.this,
                        "Không thể tải danh sách món. Vui lòng thử lại.",
                        ToastNotification.Type.ERROR
                    );
                }
            }
        }.execute();
    }

    private void populateTable(List<Order.OrderItem> items) {
        tableModel.setRowCount(0);
        double total = 0;

        for (Order.OrderItem item : items) {
            double subtotal = item.getSubtotal();
            total += subtotal;
            tableModel.addRow(new Object[]{
                item.getMenuItemName(),
                item.getQuantity(),
                formatPrice(item.getUnitPrice()),
                formatPrice(subtotal)
            });
        }
        lblTotal.setText(formatPrice(total) + " ₫");
    }

    // ─── Confirm payment ──────────────────────────────────────────────────────

    private void confirmPayment() {
        int choice = JOptionPane.showConfirmDialog(
            this,
            "Xác nhận thanh toán và đóng bàn?",
            "Xác nhận",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) return;

        btnConfirm.setEnabled(false);
        btnConfirm.setText("⏳  Đang xử lý…");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                boolean ok1 = orderDAO.completeOrder(orderId);
                boolean ok2 = tableDAO.updateStatus(tableId, TableItem.Status.DIRTY);
                return ok1 && ok2;
            }

            @Override
            protected void done() {
                boolean ok = false;
                try { ok = get(); } catch (InterruptedException | ExecutionException ex) {
                    System.err.println("[PaymentDialog] confirmPayment lỗi: " + ex.getMessage());
                }

                if (ok) {
                    paymentCompleted = true;
                    ToastNotification.show(
                        PaymentDialog.this,
                        "Thanh toán thành công! Bàn đã được đánh dấu cần dọn.",
                        ToastNotification.Type.SUCCESS
                    );
                    dispose();
                } else {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("✅  Xác nhận thanh toán");
                    ToastNotification.show(
                        PaymentDialog.this,
                        "Thanh toán thất bại. Vui lòng thử lại.",
                        ToastNotification.Type.ERROR
                    );
                }
            }
        }.execute();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** @return {@code true} nếu người dùng đã xác nhận thanh toán thành công. */
    public boolean isPaymentCompleted() {
        return paymentCompleted;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String formatPrice(double amount) {
        return PRICE_FMT.format((long) amount);
    }
}