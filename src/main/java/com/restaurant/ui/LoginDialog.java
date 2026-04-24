package com.restaurant.ui;

import com.restaurant.dao.UserDAO;
import com.restaurant.db.DBConnection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Dialog đăng nhập – xác thực trực tiếp qua Oracle JDBC + BCrypt.
 * Thay thế hoàn toàn phiên bản REST API (Spring Boot).
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
        setSize(420, 400);
        setResizable(false);
        setLocationRelativeTo(null);
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        setContentPane(root);

        // ── Header ──────────────────────────────────────────────────────────
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

        // ── Form ────────────────────────────────────────────────────────────
        JPanel form = new JPanel();
        form.setBackground(Color.WHITE);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(28, 40, 20, 40));

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

        form.add(Box.createVerticalStrut(14));
        btnLogin = new RoundedButton("Đăng nhập");
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIConstants.BTN_HEIGHT + 4));
        btnLogin.setPreferredSize(new Dimension(340, UIConstants.BTN_HEIGHT + 4));
        btnLogin.addActionListener(e -> doLogin());
        form.add(btnLogin);

        root.add(form, BorderLayout.CENTER);

        // ── Footer ──────────────────────────────────────────────────────────
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
                // 1. Kiểm tra kết nối DB trước
                if (!DBConnection.getInstance().testConnection()) {
                    errorMsg = "Không thể kết nối cơ sở dữ liệu. Kiểm tra db.properties.";
                    return false;
                }
                // 2. Xác thực
                boolean ok = new UserDAO().login(email, password);
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
