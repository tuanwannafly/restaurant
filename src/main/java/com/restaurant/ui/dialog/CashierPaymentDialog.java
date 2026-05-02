package com.restaurant.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.restaurant.dao.EmployeeDAO;
import com.restaurant.model.Employee;
import com.restaurant.ui.CashierPanel.PaymentRequest;
import com.restaurant.ui.UIConstants;

/**
 * Dialog xác nhận thanh toán — Phase 5D.
 *
 * <p>Hiển thị khi thu ngân click vào một card "Chờ thanh toán".
 * Cho phép:
 * <ul>
 *   <li>Xem phương thức thanh toán và tổng tiền</li>
 *   <li>Chọn nhân viên phụ trách (load từ {@link EmployeeDAO}, lọc role THU_NGAN)</li>
 *   <li>In hóa đơn (stub, Phase 5F implement)</li>
 *   <li>Nhấn "Thực hiện" → validate → gọi {@code onConfirm.accept(selectedEmployee)}</li>
 * </ul>
 *
 * <p>Layout:
 * <pre>
 * ┌──────────────────────────────────────────┐
 * │           Bàn 01  (header, PRIMARY bg)   │
 * ├──────────────────────────────────────────┤
 * │ Phương thức thanh toán: Tiền mặt (…)     │
 * │ Tổng cộng: 250.000đ                      │
 * │ Tên nhân viên: [Chọn nhân viên ▼]        │
 * │                                          │
 * ├──────────────────────────────────────────┤
 * │ [In hóa đơn]  ←            [Thực hiện]  │
 * └──────────────────────────────────────────┘
 * </pre>
 *
 * @see com.restaurant.ui.CashierPanel#openPaymentDialog(PaymentRequest)
 */
public class CashierPaymentDialog extends JDialog {

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final PaymentRequest      req;
    private final Consumer<String>    onConfirm;

    private JComboBox<String>         employeeCombo;
    private List<Employee>            cashierEmployees;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Tạo dialog thanh toán.
     *
     * @param owner     cửa sổ cha (dùng để center dialog)
     * @param req       đơn hàng cần xác nhận
     * @param onConfirm callback nhận tên nhân viên được chọn khi nhấn "Thực hiện"
     */
    public CashierPaymentDialog(Window owner, PaymentRequest req, Consumer<String> onConfirm) {
        super(owner, ModalityType.APPLICATION_MODAL);
        this.req       = req;
        this.onConfirm = onConfirm;

        setTitle("Thanh toán – " + req.tableName);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setSize(500, 420);
        setLocationRelativeTo(owner);

        buildUI();
        loadEmployeesAsync();
    }

    // ─── UI Construction ──────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIConstants.BG_WHITE);
        setContentPane(root);

        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildBody(),    BorderLayout.CENTER);
        root.add(buildFooter(),  BorderLayout.SOUTH);
    }

    // ─── Header ───────────────────────────────────────────────────────────────

    /**
     * Header xanh PRIMARY với tên bàn in đậm, căn giữa (giống Image 1 "Bàn 01").
     */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.PRIMARY);
                // Chỉ bo góc trên; góc dưới để vuông phẳng với body
                g2.fillRoundRect(0, 0, getWidth(), getHeight() + 16, 16, 16);
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setPreferredSize(new Dimension(0, 72));
        header.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 24));

        JLabel tableLabel = new JLabel(req.tableName, SwingConstants.CENTER);
        tableLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        tableLabel.setForeground(Color.WHITE);
        header.add(tableLabel, BorderLayout.CENTER);

        return header;
    }

    // ─── Body ─────────────────────────────────────────────────────────────────

    /**
     * Body chứa thông tin thanh toán và combo chọn nhân viên.
     */
    private JPanel buildBody() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(UIConstants.BG_WHITE);
        body.setBorder(BorderFactory.createEmptyBorder(28, 32, 8, 32));

        // ── Phương thức thanh toán ──
        String methodText = buildPaymentMethodText();
        JLabel methodLabel = new JLabel(methodText);
        methodLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        methodLabel.setForeground(UIConstants.TEXT_PRIMARY);
        methodLabel.setAlignmentX(LEFT_ALIGNMENT);

        // ── Tổng cộng ──
        JLabel totalLabel = new JLabel("Tổng cộng: " + formatAmount(req.totalAmount) + "đ");
        totalLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        totalLabel.setForeground(UIConstants.TEXT_PRIMARY);
        totalLabel.setAlignmentX(LEFT_ALIGNMENT);

        // ── Tên nhân viên row ──
        JPanel staffRow = buildStaffRow();
        staffRow.setAlignmentX(LEFT_ALIGNMENT);

        body.add(methodLabel);
        body.add(Box.createVerticalStrut(12));
        body.add(totalLabel);
        body.add(Box.createVerticalStrut(20));
        body.add(staffRow);
        body.add(Box.createVerticalGlue());

        return body;
    }

    /**
     * Dòng chọn nhân viên: label "Tên nhân viên:" + JComboBox.
     */
    private JPanel buildStaffRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JLabel label = new JLabel("Tên nhân viên:");
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        label.setForeground(UIConstants.TEXT_PRIMARY);

        employeeCombo = new JComboBox<>();
        employeeCombo.setFont(UIConstants.FONT_BODY);
        employeeCombo.setPreferredSize(new Dimension(200, 32));
        employeeCombo.setModel(new DefaultComboBoxModel<>(new String[]{"Đang tải..."}));
        employeeCombo.setEnabled(false);

        row.add(label);
        row.add(Box.createHorizontalStrut(12));
        row.add(employeeCombo);

        return row;
    }

    // ─── Footer ───────────────────────────────────────────────────────────────

    /**
     * Footer gồm: [In hóa đơn] [←]  ............  [Thực hiện]
     * Giống Image 1.
     */
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(UIConstants.BG_WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 24, 16, 24)));

        // ── Left: In hóa đơn + ← ──
        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftGroup.setOpaque(false);

        JButton btnPrint = buildOutlineButton("In hóa đơn", 110, 34);
        btnPrint.addActionListener(e ->
                JOptionPane.showMessageDialog(this,
                        "Chức năng in đang phát triển",
                        "Thông báo",
                        JOptionPane.INFORMATION_MESSAGE));

        JButton btnBack = buildTextIconButton("←");
        btnBack.addActionListener(e -> dispose());

        leftGroup.add(btnPrint);
        leftGroup.add(btnBack);

        // ── Right: Thực hiện ──
        JButton btnConfirm = buildPrimaryButton("Thực hiện", 110, 34);
        btnConfirm.addActionListener(e -> handleConfirm());

        footer.add(leftGroup,  BorderLayout.WEST);
        footer.add(btnConfirm, BorderLayout.EAST);

        return footer;
    }

    // ─── Action Handlers ──────────────────────────────────────────────────────

    /**
     * Validate nhân viên đã chọn rồi gọi callback {@link #onConfirm}.
     */
    private void handleConfirm() {
        String selected = (String) employeeCombo.getSelectedItem();

        if (selected == null
                || selected.isBlank()
                || selected.equals("Chọn nhân viên")
                || selected.equals("Đang tải...")
                || selected.equals("(Không có thu ngân)")) {

            JOptionPane.showMessageDialog(
                    this,
                    "Vui lòng chọn nhân viên phụ trách trước khi thực hiện.",
                    "Thiếu thông tin",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        dispose();
        if (onConfirm != null) {
            onConfirm.accept(selected);
        }
    }

    // ─── Async Employee Loading ───────────────────────────────────────────────

    /**
     * Load danh sách thu ngân từ {@link EmployeeDAO} trên background thread.
     * Lọc {@code role == THU_NGAN} rồi cập nhật combo trên EDT.
     */
    private void loadEmployeesAsync() {
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                try {
                    cashierEmployees = new EmployeeDAO().findAll().stream()
                            .filter(e -> e.getRole() == Employee.Role.THU_NGAN)
                            .collect(Collectors.toList());
                    return cashierEmployees.stream()
                            .map(Employee::getName)
                            .collect(Collectors.toList());
                } catch (Exception ex) {
                    System.err.println("[CashierPaymentDialog] loadEmployees lỗi: " + ex.getMessage());
                    return List.of();
                }
            }

            @Override
            protected void done() {
                try {
                    List<String> names = get();
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                    model.addElement("Chọn nhân viên");   // placeholder index 0
                    if (names.isEmpty()) {
                        model.addElement("(Không có thu ngân)");
                    } else {
                        names.forEach(model::addElement);
                    }
                    employeeCombo.setModel(model);
                    employeeCombo.setSelectedIndex(0);
                    employeeCombo.setEnabled(!names.isEmpty());
                } catch (Exception ex) {
                    System.err.println("[CashierPaymentDialog] done() lỗi: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Tạo text động cho label phương thức thanh toán.
     * Ví dụ: "Phương thức thanh toán: Tiền mặt (Tiền khách đưa: 300.000đ)"
     * hoặc    "Phương thức thanh toán: Chuyển khoản"
     */
    private String buildPaymentMethodText() {
        String method = req.getPaymentMethodLabel();
        if (req.paymentMethod == PaymentRequest.PaymentMethod.CASH) {
            // Ước tính "tiền khách đưa" = làm tròn lên bội số 50.000 gần nhất
            long rounded = (long) (Math.ceil(req.totalAmount / 50_000.0) * 50_000);
            return "Phương thức thanh toán: " + method
                    + " (Tiền khách đưa: " + formatAmount(rounded) + "đ)";
        }
        return "Phương thức thanh toán: " + method;
    }

    /** Định dạng số tiền với dấu chấm phân cách hàng nghìn. */
    private String formatAmount(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format((long) amount);
    }

    // ─── Button Factory Methods ───────────────────────────────────────────────

    /** Nút outline (viền PRIMARY, nền trắng). */
    private JButton buildOutlineButton(String text, int w, int h) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed()
                        ? UIConstants.PRIMARY_LIGHT : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.setColor(UIConstants.PRIMARY);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(UIConstants.FONT_BODY);
        btn.setForeground(UIConstants.PRIMARY);
        btn.setPreferredSize(new Dimension(w, h));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** Nút PRIMARY fill (xanh, chữ trắng). */
    private JButton buildPrimaryButton(String text, int w, int h) {
        JButton btn = new JButton(text) {
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
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(w, h));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** Nút icon text (mũi tên ←, không border, không background). */
    private JButton buildTextIconButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        btn.setForeground(UIConstants.TEXT_SECONDARY);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(36, 34));
        return btn;
    }

    // ─── Static Factory (convenience) ────────────────────────────────────────

    /**
     * Tạo và hiển thị dialog ngay lập tức (convenience method).
     *
     * @param owner     cửa sổ cha
     * @param req       đơn hàng
     * @param onConfirm callback khi xác nhận thành công
     */
    public static void show(Window owner, PaymentRequest req, Consumer<String> onConfirm) {
        SwingUtilities.invokeLater(() -> {
            CashierPaymentDialog dlg = new CashierPaymentDialog(owner, req, onConfirm);
            dlg.setVisible(true);
        });
    }
}