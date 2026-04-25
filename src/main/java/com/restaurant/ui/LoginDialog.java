package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import com.restaurant.data.DataManager;
import com.restaurant.db.DBConnection;

/**
 * Dialog đăng nhập – xác thực trực tiếp qua Oracle JDBC + BCrypt.
 *
 * <p>Tính năng:
 * <ul>
 *   <li>Đăng nhập email + mật khẩu (BCrypt)</li>
 *   <li>Link "Quên mật khẩu?" → flow 2 bước: nhập email → nhận token → nhập token + mật khẩu mới</li>
 * </ul>
 */
public class LoginDialog extends JDialog {

    private boolean loginSuccess = false;

    private JTextField     tfEmail;
    private JPasswordField tfPassword;
    private JLabel         lblError;
    private JButton        btnLogin;

    public LoginDialog(Frame owner) {
        super(owner, "Đăng nhập hệ thống", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(420, 430);
        setResizable(false);
        setLocationRelativeTo(null);
        buildUI();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        setContentPane(root);

        // Header
        JPanel header = new JPanel(new GridBagLayout());
        header.setBackground(UIConstants.PRIMARY);
        header.setPreferredSize(new Dimension(0, 90));

        JPanel logoWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        logoWrap.setOpaque(false);
        JLabel icon  = new JLabel("🍽");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26));
        icon.setForeground(Color.WHITE);
        JLabel title = new JLabel("Quản lý Nhà hàng");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        logoWrap.add(icon);
        logoWrap.add(title);
        header.add(logoWrap);
        root.add(header, BorderLayout.NORTH);

        // Form
        JPanel form = new JPanel();
        form.setBackground(Color.WHITE);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(28, 40, 10, 40));

        form.add(label("Email / Tài khoản"));
        form.add(Box.createVerticalStrut(6));
        tfEmail = styledTextField();
        form.add(tfEmail);

        form.add(Box.createVerticalStrut(16));
        form.add(label("Mật khẩu"));
        form.add(Box.createVerticalStrut(6));
        tfPassword = new JPasswordField();
        styleInput(tfPassword);
        form.add(tfPassword);

        form.add(Box.createVerticalStrut(8));
        lblError = new JLabel(" ");
        lblError.setFont(UIConstants.FONT_SMALL);
        lblError.setForeground(UIConstants.DANGER);
        lblError.setAlignmentX(LEFT_ALIGNMENT);
        form.add(lblError);

        form.add(Box.createVerticalStrut(10));
        btnLogin = new RoundedButton("Đăng nhập");
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIConstants.BTN_HEIGHT + 4));
        btnLogin.setPreferredSize(new Dimension(340, UIConstants.BTN_HEIGHT + 4));
        btnLogin.addActionListener(e -> doLogin());
        form.add(btnLogin);

        // Link "Quên mật khẩu?"
        form.add(Box.createVerticalStrut(12));
        JLabel lnkForgot = new JLabel("<html><a href='#'>Quên mật khẩu?</a></html>");
        lnkForgot.setFont(UIConstants.FONT_SMALL);
        lnkForgot.setAlignmentX(CENTER_ALIGNMENT);
        lnkForgot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lnkForgot.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { openForgotPasswordFlow(); }
        });
        form.add(lnkForgot);

        root.add(form, BorderLayout.CENTER);

        // Footer
        JLabel footer = new JLabel("Smart Restaurant Management System", SwingConstants.CENTER);
        footer.setFont(UIConstants.FONT_SMALL);
        footer.setForeground(UIConstants.TEXT_SECONDARY);
        footer.setBorder(new EmptyBorder(8, 0, 12, 0));
        root.add(footer, BorderLayout.SOUTH);

        // Enter key = đăng nhập
        KeyAdapter enter = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) doLogin();
            }
        };
        tfEmail.addKeyListener(enter);
        tfPassword.addKeyListener(enter);
    }

    // ── Login logic ───────────────────────────────────────────────────────────

    private void doLogin() {
        String email    = tfEmail.getText().trim();
        String password = new String(tfPassword.getPassword());

        if (email.isEmpty() || password.isEmpty()) {
            lblError.setText("Vui lòng nhập email và mật khẩu.");
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Đang đăng nhập...");
        lblError.setText(" ");

        new SwingWorker<Boolean, Void>() {
            String errorMsg = null;

            @Override
            protected Boolean doInBackground() {
                if (!DBConnection.getInstance().testConnection()) {
                    errorMsg = "Không thể kết nối cơ sở dữ liệu. Kiểm tra db.properties.";
                    return false;
                }
                boolean ok = new com.restaurant.dao.UserDAO().login(email, password);
                if (!ok) errorMsg = "Email hoặc mật khẩu không đúng.";
                return ok;
            }

            @Override
            protected void done() {
                btnLogin.setEnabled(true);
                btnLogin.setText("Đăng nhập");
                try {
                    if (get()) {
                        loginSuccess = true;
                        dispose();
                    } else {
                        lblError.setText(errorMsg != null ? errorMsg : "Đăng nhập thất bại.");
                        tfPassword.setText("");
                        tfPassword.requestFocus();
                    }
                } catch (Exception ex) {
                    lblError.setText("Lỗi: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ── Forgot-password flow ──────────────────────────────────────────────────

    /**
     * Bước 1: Dialog nhập email.
     * Gọi {@code DataManager.generatePasswordResetToken(email)}.
     * Hiện token trong dialog xác nhận (thay email server vì là desktop app).
     */
    private void openForgotPasswordFlow() {
        // ── Bước 1: Nhập email ──────────────────────────────────────────────
        JPanel step1Panel = new JPanel();
        step1Panel.setLayout(new BoxLayout(step1Panel, BoxLayout.Y_AXIS));
        step1Panel.setBorder(new EmptyBorder(8, 0, 4, 0));
        step1Panel.setPreferredSize(new Dimension(360, 90));

        JLabel hint = new JLabel("<html>Nhập email tài khoản của bạn.<br>"
                + "Hệ thống sẽ tạo token đặt lại mật khẩu (hết hạn 15 phút).</html>");
        hint.setFont(UIConstants.FONT_SMALL);
        hint.setForeground(UIConstants.TEXT_SECONDARY);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        step1Panel.add(hint);
        step1Panel.add(Box.createVerticalStrut(10));

        step1Panel.add(new JLabel("Email:"));
        step1Panel.add(Box.createVerticalStrut(4));
        JTextField tfResetEmail = new JTextField();
        tfResetEmail.setPreferredSize(new Dimension(340, 34));
        step1Panel.add(tfResetEmail);

        int res1 = JOptionPane.showConfirmDialog(
                this, step1Panel,
                "Quên mật khẩu – Bước 1/2: Nhập email",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (res1 != JOptionPane.OK_OPTION) return;

        String resetEmail = tfResetEmail.getText().trim();
        if (resetEmail.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Email không được để trống.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ── Sinh token (SwingWorker tránh block EDT) ─────────────────────
        final String[] tokenHolder = {null};
        final String[] errHolder   = {null};

        try {
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override protected String doInBackground() {
                    try {
                        return DataManager.getInstance().generatePasswordResetToken(resetEmail);
                    } catch (Exception ex) {
                        errHolder[0] = ex.getMessage();
                        return null;
                    }
                }
            };
            worker.execute();
            tokenHolder[0] = worker.get();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi kết nối: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (errHolder[0] != null) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi: " + errHolder[0], "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (tokenHolder[0] == null) {
            JOptionPane.showMessageDialog(this,
                    "Email không tồn tại trong hệ thống hoặc tài khoản đã bị khoá.",
                    "Không tìm thấy", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ── Hiện token (Desktop app: hiện trực tiếp — thay cho gửi email) ─
        String generatedToken = tokenHolder[0];
        JPanel tokenPanel = new JPanel(new BorderLayout(0, 8));
        tokenPanel.setPreferredSize(new Dimension(420, 90));
        tokenPanel.setBorder(new EmptyBorder(8, 0, 4, 0));

        tokenPanel.add(new JLabel("<html><b>Token đặt lại mật khẩu (hết hạn sau 15 phút):</b></html>"),
                BorderLayout.NORTH);

        JTextField tfShowToken = new JTextField(generatedToken);
        tfShowToken.setEditable(false);
        tfShowToken.setFont(new Font("Monospaced", Font.PLAIN, 11));
        tfShowToken.setBackground(new Color(0xF3F4F6));
        tfShowToken.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xD1D5DB), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        tokenPanel.add(tfShowToken, BorderLayout.CENTER);

        JLabel copyHint = new JLabel("<html><i>Sao chép token này rồi nhấn OK để sang bước 2.</i></html>");
        copyHint.setFont(UIConstants.FONT_SMALL);
        copyHint.setForeground(UIConstants.TEXT_SECONDARY);
        tokenPanel.add(copyHint, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(this, tokenPanel,
                "Token đã được tạo", JOptionPane.INFORMATION_MESSAGE);

        // Tiếp sang bước 2
        openResetStep2(generatedToken);
    }

    /**
     * Bước 2: Dialog nhập token + mật khẩu mới.
     * Token điền sẵn để người dùng không cần gõ lại.
     *
     * @param prefillToken token đã sinh ở bước 1
     */
    private void openResetStep2(String prefillToken) {
        JPanel step2Panel = new JPanel();
        step2Panel.setLayout(new BoxLayout(step2Panel, BoxLayout.Y_AXIS));
        step2Panel.setBorder(new EmptyBorder(8, 0, 4, 0));
        step2Panel.setPreferredSize(new Dimension(380, 170));

        // Token field (điền sẵn)
        step2Panel.add(new JLabel("Token xác nhận:"));
        step2Panel.add(Box.createVerticalStrut(4));
        JTextField tfToken = new JTextField(prefillToken);
        tfToken.setFont(new Font("Monospaced", Font.PLAIN, 11));
        tfToken.setPreferredSize(new Dimension(360, 34));
        step2Panel.add(tfToken);
        step2Panel.add(Box.createVerticalStrut(12));

        // Mật khẩu mới
        step2Panel.add(new JLabel("Mật khẩu mới (tối thiểu 6 ký tự):"));
        step2Panel.add(Box.createVerticalStrut(4));
        JPasswordField pfNew = new JPasswordField();
        pfNew.setPreferredSize(new Dimension(360, 34));
        step2Panel.add(pfNew);
        step2Panel.add(Box.createVerticalStrut(12));

        // Xác nhận mật khẩu mới
        step2Panel.add(new JLabel("Xác nhận mật khẩu mới:"));
        step2Panel.add(Box.createVerticalStrut(4));
        JPasswordField pfConfirm = new JPasswordField();
        pfConfirm.setPreferredSize(new Dimension(360, 34));
        step2Panel.add(pfConfirm);

        int res2 = JOptionPane.showConfirmDialog(
                this, step2Panel,
                "Quên mật khẩu – Bước 2/2: Đặt mật khẩu mới",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (res2 != JOptionPane.OK_OPTION) return;

        String token     = tfToken.getText().trim();
        String newPw     = new String(pfNew.getPassword());
        String confirmPw = new String(pfConfirm.getPassword());

        // Validation client-side
        if (token.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Token không được để trống.",
                    "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (newPw.length() < 6) {
            JOptionPane.showMessageDialog(this, "Mật khẩu mới phải có ít nhất 6 ký tự.",
                    "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!newPw.equals(confirmPw)) {
            JOptionPane.showMessageDialog(this, "Xác nhận mật khẩu không khớp.",
                    "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ── Thực hiện đặt lại mật khẩu (SwingWorker) ────────────────────
        final boolean[] success  = {false};
        final String[]  errReset = {null};

        try {
            SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
                @Override protected Boolean doInBackground() {
                    try {
                        return DataManager.getInstance().resetPasswordWithToken(token, newPw);
                    } catch (Exception ex) {
                        errReset[0] = ex.getMessage();
                        return false;
                    }
                }
            };
            worker.execute();
            success[0] = worker.get();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi kết nối: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (errReset[0] != null) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi: " + errReset[0], "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (success[0]) {
            JOptionPane.showMessageDialog(this,
                    "✅ Đặt lại mật khẩu thành công!\nVui lòng đăng nhập lại với mật khẩu mới.",
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "❌ Token không hợp lệ hoặc đã hết hạn.\nVui lòng thử lại từ bước 1.",
                    "Thất bại", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isLoginSuccess() { return loginSuccess; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UIConstants.FONT_BOLD);
        l.setForeground(UIConstants.TEXT_PRIMARY);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JTextField styledTextField() {
        JTextField tf = new JTextField();
        styleInput(tf);
        return tf;
    }

    private void styleInput(JTextField tf) {
        tf.setFont(UIConstants.FONT_BODY);
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        tf.setPreferredSize(new Dimension(340, 36));
        tf.setAlignmentX(LEFT_ALIGNMENT);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
    }
}
