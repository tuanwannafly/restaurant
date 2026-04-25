package com.restaurant.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.restaurant.session.OperationTokenService;
import com.restaurant.session.OperationType;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.UIConstants;

/**
 * Dialog xác nhận thao tác nhạy cảm bằng Operation Token.
 *
 * <p>Hiển thị mã token ngắn hạn (8 ký tự) và yêu cầu người dùng
 * nhập lại đúng mã trước khi thao tác được thực thi.
 *
 * <p><b>Cách sử dụng (một dòng):</b>
 * <pre>{@code
 *   boolean confirmed = ConfirmOperationDialog.show(
 *       SwingUtilities.getWindowAncestor(this),
 *       OperationType.DELETE_RESTAURANT,
 *       item.getRestaurantId()
 *   );
 *   if (confirmed) dao.delete(item.getRestaurantId());
 * }</pre>
 *
 * <p>Phương thức tĩnh {@link #show} lo toàn bộ vòng đời: phát hành token,
 * mở dialog, xác nhận và trả về kết quả boolean.
 */
public class ConfirmOperationDialog extends JDialog {

    // ── Visual constants ──────────────────────────────────────────────────────

    private static final Color COLOR_BG          = Color.WHITE;
    private static final Color COLOR_TOKEN_BG    = new Color(0xFFF3CD);
    private static final Color COLOR_TOKEN_BORDER = new Color(0xFFC107);
    private static final Color COLOR_DANGER      = new Color(0xDC3545);
    private static final Color COLOR_ERROR_TEXT  = new Color(0xB00020);
    private static final Font  FONT_TOKEN        = new Font("Monospaced", Font.BOLD, 28);

    // ── State ─────────────────────────────────────────────────────────────────

    private final OperationType type;
    private final long          targetId;
    private final String        issuedToken;

    private JTextField tfInput;
    private JLabel     lblError;
    private boolean    confirmed = false;

    // ── Constructor (private — use static factory) ────────────────────────────

    private ConfirmOperationDialog(Window owner, OperationType type, long targetId, String token) {
        super(owner, "Xác nhận thao tác", ModalityType.APPLICATION_MODAL);
        this.type        = type;
        this.targetId    = targetId;
        this.issuedToken = token;
        buildUI();
        pack();
        setMinimumSize(new Dimension(440, 0));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Phát hành token, mở dialog xác nhận và trả về kết quả.
     *
     * <p>Phương thức này <em>blocking</em> (do modal dialog) và phải được
     * gọi trên Swing Event Dispatch Thread.
     *
     * @param owner    cửa sổ cha (có thể null)
     * @param type     loại thao tác cần xác nhận
     * @param targetId ID của đối tượng bị tác động
     * @return {@code true} nếu người dùng nhập đúng mã và xác nhận
     */
    public static boolean show(Window owner, OperationType type, long targetId) {
        try {
            String token = OperationTokenService.getInstance().issueToken(type, targetId);
            ConfirmOperationDialog dlg = new ConfirmOperationDialog(owner, type, targetId, token);
            dlg.setVisible(true); // blocks until dispose()
            return dlg.confirmed;
        } catch (Exception e) {
            javax.swing.JOptionPane.showMessageDialog(
                owner,
                "Không thể phát hành mã xác nhận:\n" + e.getMessage(),
                "Lỗi hệ thống",
                javax.swing.JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(COLOR_BG);

        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_DANGER);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        JLabel icon = new JLabel("⚠  Xác nhận thao tác nhạy cảm");
        icon.setFont(UIConstants.FONT_TITLE);
        icon.setForeground(Color.WHITE);
        panel.add(icon, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildCenter() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(COLOR_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 8, 24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx   = 0;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets  = new Insets(0, 0, 14, 0);

        // Mô tả thao tác
        JLabel lblDesc = new JLabel(
            "<html><body style='width:370px'>"
            + "Thao tác <b>" + escapeHtml(type.getDisplayName()) + "</b> "
            + "không thể hoàn tác.<br><br>"
            + "Mã xác nhận của bạn (có hiệu lực trong <b>5 phút</b>):"
            + "</body></html>");
        lblDesc.setFont(UIConstants.FONT_BODY);
        gbc.gridy = 0;
        panel.add(lblDesc, gbc);

        // Token box
        JPanel tokenBox = buildTokenBox();
        gbc.gridy = 1;
        panel.add(tokenBox, gbc);

        // Input label
        JLabel lblPrompt = new JLabel("Nhập lại mã xác nhận để tiếp tục:");
        lblPrompt.setFont(UIConstants.FONT_BOLD);
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 6, 0);
        panel.add(lblPrompt, gbc);

        // Input field
        tfInput = new JTextField();
        tfInput.setFont(new Font("Monospaced", Font.PLAIN, 18));
        tfInput.setHorizontalAlignment(JTextField.CENTER);
        tfInput.setPreferredSize(new Dimension(380, 40));
        tfInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        // Enter key triggers confirm
        tfInput.addActionListener(e -> doConfirm());
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 6, 0);
        panel.add(tfInput, gbc);

        // Error label (hidden by default)
        lblError = new JLabel(" ");
        lblError.setFont(UIConstants.FONT_BODY);
        lblError.setForeground(COLOR_ERROR_TEXT);
        gbc.gridy = 4;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(lblError, gbc);

        return panel;
    }

    private JPanel buildTokenBox() {
        JPanel box = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        box.setBackground(COLOR_TOKEN_BG);
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(COLOR_TOKEN_BORDER, 1, true),
            BorderFactory.createEmptyBorder(4, 16, 4, 16)));

        JLabel lblToken = new JLabel(issuedToken);
        lblToken.setFont(FONT_TOKEN);
        lblToken.setForeground(new Color(0x856404));
        box.add(lblToken);

        return box;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        panel.setBackground(COLOR_BG);
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR));

        RoundedButton btnCancel = RoundedButton.outline("Hủy");
        btnCancel.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnCancel.addActionListener(e -> dispose());

        RoundedButton btnConfirm = new RoundedButton("Xác nhận");
        btnConfirm.setPreferredSize(new Dimension(120, UIConstants.BTN_HEIGHT));
        btnConfirm.setBackground(COLOR_DANGER);
        btnConfirm.addActionListener(e -> doConfirm());

        panel.add(btnCancel);
        panel.add(btnConfirm);
        return panel;
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void doConfirm() {
        String input = tfInput.getText().trim();

        if (input.isEmpty()) {
            lblError.setText("Vui lòng nhập mã xác nhận.");
            return;
        }

        boolean valid = OperationTokenService.getInstance()
                            .confirmToken(input, type, targetId);

        if (valid) {
            confirmed = true;
            dispose();
        } else {
            lblError.setText("Mã không hợp lệ, đã dùng hoặc đã hết hạn. Vui lòng thử lại.");
            tfInput.selectAll();
            tfInput.requestFocusInWindow();
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
