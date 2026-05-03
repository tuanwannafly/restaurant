package com.restaurant.ui;

import com.restaurant.data.DataManager;
import com.restaurant.db.DBConnection;
import com.restaurant.session.RefreshTokenService;
import com.restaurant.session.TokenStorage;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Dialog đăng nhập – xác thực trực tiếp qua Oracle JDBC + BCrypt.
 *
 * <p>Dependency bắt buộc trong pom.xml:
 * <pre>
 *   &lt;dependency&gt;
 *     &lt;groupId&gt;com.miglayout&lt;/groupId&gt;
 *     &lt;artifactId&gt;miglayout-swing&lt;/artifactId&gt;
 *     &lt;version&gt;11.4.2&lt;/version&gt;
 *   &lt;/dependency&gt;
 * </pre>
 *
 * <p>Tính năng:
 * <ul>
 *   <li>Card trắng bo góc 16px, shadow nhẹ, căn giữa nền xám</li>
 *   <li>AppButton PRIMARY full-width với loading state</li>
 *   <li>Focus ring trên input qua FlatLaf</li>
 *   <li>Link "Quên mật khẩu" styled</li>
 *   <li>Đăng nhập email + mật khẩu (BCrypt)</li>
 *   <li>Flow 2 bước đặt lại mật khẩu</li>
 * </ul>
 *
 * Compatible với WindowBuilder.
 */
public class LoginDialog extends JDialog {

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean loginSuccess = false;

    // ── Components (fields for WindowBuilder) ─────────────────────────────────
    private JTextField     tfEmail;
    private JPasswordField tfPassword;
    private JCheckBox      chkRememberMe;
    private JLabel         lblError;
    private AppButton      btnLogin;

    // ── Constructor ───────────────────────────────────────────────────────────

    public LoginDialog(Frame owner) {
        super(owner, "Dang nhap he thong", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setUndecorated(false);
        setSize(460, 560);
        setResizable(false);
        setLocationRelativeTo(null);
        buildUI();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        // Root fills the dialog with a light surface colour
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(UIConstants.COLOR_SURFACE);
        setContentPane(root);

        // Elevated card panel (white, rounded, shadow)
        CardPanel card = new CardPanel(UIConstants.RADIUS_2XL);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(380, 490));
        card.setMaximumSize (new Dimension(380, 490));

        card.add(buildBrandHeader());
        card.add(buildFormPanel());
        card.add(buildFooterRow());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 0);
        root.add(card, gbc);

        // Enter key triggers login on both fields
        KeyAdapter enterKey = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) doLogin();
            }
        };
        tfEmail.addKeyListener(enterKey);
        tfPassword.addKeyListener(enterKey);
    }

    // ── Brand header ──────────────────────────────────────────────────────────

    private JPanel buildBrandHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(
            UIConstants.SPACING_XL, UIConstants.SPACING_XL,
            UIConstants.SPACING_LG, UIConstants.SPACING_XL));
        header.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Logo mark — coloured rounded square with initials "SR"
        JPanel logoMark = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.COLOR_PRIMARY);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(),
                        UIConstants.RADIUS_LG * 2f, UIConstants.RADIUS_LG * 2f));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        logoMark.setOpaque(false);
        logoMark.setPreferredSize(new Dimension(48, 48));
        logoMark.setMaximumSize (new Dimension(48, 48));
        logoMark.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel initials = new JLabel("SR");
        initials.setFont(new Font(UIConstants.FONT_FAMILY, Font.BOLD, 20));
        initials.setForeground(Color.WHITE);
        logoMark.add(initials);

        // App name
        JLabel lblAppName = new JLabel("Smart Restaurant");
        lblAppName.setFont(UIConstants.FONT_TITLE);
        lblAppName.setForeground(UIConstants.COLOR_TEXT_PRIMARY);
        lblAppName.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Subtitle
        JLabel lblSubtitle = new JLabel("He thong quan ly nha hang");
        lblSubtitle.setFont(UIConstants.FONT_CAPTION);
        lblSubtitle.setForeground(UIConstants.COLOR_TEXT_SECONDARY);
        lblSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(logoMark);
        header.add(Box.createVerticalStrut(UIConstants.SPACING_MD));
        header.add(lblAppName);
        header.add(Box.createVerticalStrut(UIConstants.SPACING_XS));
        header.add(lblSubtitle);

        return header;
    }

    // ── Form panel (MigLayout) ────────────────────────────────────────────────

    private JPanel buildFormPanel() {
        // MigLayout: single column, consistent gaps
        JPanel form = new JPanel(new MigLayout(
            "insets " + UIConstants.SPACING_SM + " " + UIConstants.SPACING_XL
                      + " " + UIConstants.SPACING_SM + " " + UIConstants.SPACING_XL
                      + ", fillx, gap 0 0",   // layout constraints
            "[grow, fill]",                    // column constraint
            "[]" + UIConstants.SPACING_XS + "[]" +  // label + field pairs
            UIConstants.SPACING_MD + "[]" +
            UIConstants.SPACING_XS + "[]" +
            UIConstants.SPACING_XS + "[]" +
            UIConstants.SPACING_SM + "[]" +
            UIConstants.SPACING_MD + "[]"
        ));
        form.setOpaque(false);

        // ── Email ────────────────────────────────────────────────────────────
        form.add(fieldLabel("Email / Ten tai khoan"), "wrap");
        tfEmail = new JTextField();
        configureInputField(tfEmail);
        form.add(tfEmail, "h " + UIConstants.SIZE_INPUT_HEIGHT + "!, wrap " + UIConstants.SPACING_MD);

        // ── Password ─────────────────────────────────────────────────────────
        form.add(fieldLabel("Mat khau"), "wrap");
        tfPassword = new JPasswordField();
        configureInputField(tfPassword);
        form.add(tfPassword, "h " + UIConstants.SIZE_INPUT_HEIGHT + "!, wrap " + UIConstants.SPACING_XS);

        // ── Error label ───────────────────────────────────────────────────────
        lblError = new JLabel(" ");
        lblError.setFont(UIConstants.FONT_SMALL);
        lblError.setForeground(UIConstants.COLOR_DANGER);
        form.add(lblError, "wrap " + UIConstants.SPACING_XS);

        // ── Remember me ───────────────────────────────────────────────────────
        chkRememberMe = new JCheckBox("Ghi nho dang nhap 30 ngay");
        chkRememberMe.setFont(UIConstants.FONT_SMALL);
        chkRememberMe.setForeground(UIConstants.COLOR_TEXT_SECONDARY);
        chkRememberMe.setOpaque(false);
        chkRememberMe.setFocusPainted(false);
        form.add(chkRememberMe, "wrap " + UIConstants.SPACING_MD);

        // ── Login button ──────────────────────────────────────────────────────
        btnLogin = new AppButton("Dang nhap", AppButton.Variant.PRIMARY, AppButton.Size.LARGE);
        form.add(btnLogin, "h " + UIConstants.SIZE_BTN_HEIGHT_LG + "!, wrap " + UIConstants.SPACING_MD);

        // ── Forgot password link ──────────────────────────────────────────────
        JLabel lnkForgot = buildLinkLabel("Quen mat khau?");
        lnkForgot.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { openForgotPasswordFlow(); }
        });
        form.add(lnkForgot, "align center");

        btnLogin.addActionListener(e -> doLogin());

        return form;
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private JPanel buildFooterRow() {
        JPanel footer = new JPanel(new GridBagLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(
            UIConstants.SPACING_LG, UIConstants.SPACING_XL,
            UIConstants.SPACING_XL, UIConstants.SPACING_XL));

        JLabel lbl = new JLabel("Smart Restaurant Management System");
        lbl.setFont(UIConstants.FONT_SMALL);
        lbl.setForeground(UIConstants.COLOR_TEXT_TERTIARY);
        footer.add(lbl);
        return footer;
    }

    // ── Login logic ───────────────────────────────────────────────────────────

    private void doLogin() {
        String email    = tfEmail.getText().trim();
        String password = new String(tfPassword.getPassword());

        if (email.isEmpty() || password.isEmpty()) {
            lblError.setText("Vui long nhap email va mat khau.");
            return;
        }

        btnLogin.setLoading(true);
        lblError.setText(" ");

        new SwingWorker<Boolean, Void>() {
            String errorMsg = null;

            @Override
            protected Boolean doInBackground() {
                if (!DBConnection.getInstance().testConnection()) {
                    errorMsg = "Khong the ket noi co so du lieu. Kiem tra db.properties.";
                    return false;
                }
                boolean ok = new com.restaurant.dao.UserDAO().login(email, password);
                if (!ok) errorMsg = "Email hoac mat khau khong dung.";
                return ok;
            }

            @Override
            protected void done() {
                btnLogin.setLoading(false);
                try {
                    if (get()) {
                        if (chkRememberMe.isSelected()) {
                            try {
                                long uid = com.restaurant.session.AppSession.getInstance().getUserId();
                                String rt = RefreshTokenService.getInstance().generateRefreshToken(uid);
                                TokenStorage.getInstance().saveRefreshToken(rt);
                            } catch (Exception rtEx) {
                                System.err.println("[LoginDialog] Canh bao: khong luu duoc refresh token: "
                                        + rtEx.getMessage());
                            }
                        }
                        loginSuccess = true;
                        dispose();
                    } else {
                        lblError.setText(errorMsg != null ? errorMsg : "Dang nhap that bai.");
                        tfPassword.setText("");
                        tfPassword.requestFocus();
                        // Brief shake animation on error label
                        animateError();
                    }
                } catch (Exception ex) {
                    lblError.setText("Loi: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /** Briefly highlights the error label to draw attention. */
    private void animateError() {
        final Color orig = lblError.getForeground();
        lblError.setForeground(UIConstants.COLOR_DANGER);
        Timer t = new Timer(120, null);
        final int[] count = {0};
        t.addActionListener(e -> {
            count[0]++;
            lblError.setForeground(count[0] % 2 == 0 ? orig : UIConstants.COLOR_DANGER_BG);
            if (count[0] >= 4) { t.stop(); lblError.setForeground(UIConstants.COLOR_DANGER); }
        });
        t.start();
    }

    // ── Forgot-password flow ──────────────────────────────────────────────────

    private void openForgotPasswordFlow() {
        JPanel step1Panel = new JPanel();
        step1Panel.setLayout(new BoxLayout(step1Panel, BoxLayout.Y_AXIS));
        step1Panel.setBorder(new EmptyBorder(8, 0, 4, 0));
        step1Panel.setPreferredSize(new Dimension(360, 90));

        JLabel hint = new JLabel("<html>Nhap email tai khoan cua ban.<br>"
                + "He thong se tao token dat lai mat khau (het han 15 phut).</html>");
        hint.setFont(UIConstants.FONT_SMALL);
        hint.setForeground(UIConstants.COLOR_TEXT_SECONDARY);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        step1Panel.add(hint);
        step1Panel.add(Box.createVerticalStrut(10));
        step1Panel.add(new JLabel("Email:"));
        step1Panel.add(Box.createVerticalStrut(4));
        JTextField tfResetEmail = new JTextField();
        tfResetEmail.setPreferredSize(new Dimension(340, UIConstants.SIZE_INPUT_HEIGHT));
        step1Panel.add(tfResetEmail);

        int res1 = JOptionPane.showConfirmDialog(this, step1Panel,
                "Quen mat khau - Buoc 1/2: Nhap email",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res1 != JOptionPane.OK_OPTION) return;

        String resetEmail = tfResetEmail.getText().trim();
        if (resetEmail.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Email khong duoc de trong.", "Loi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final String[] tokenHolder = {null};
        final String[] errHolder   = {null};
        try {
            SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
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
                    "Loi ket noi: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (errHolder[0] != null) {
            JOptionPane.showMessageDialog(this,
                    "Loi: " + errHolder[0], "Loi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (tokenHolder[0] == null) {
            JOptionPane.showMessageDialog(this,
                    "Email khong ton tai hoac tai khoan da bi khoa.",
                    "Khong tim thay", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String generatedToken = tokenHolder[0];
        JPanel tokenPanel = new JPanel(new BorderLayout(0, 8));
        tokenPanel.setPreferredSize(new Dimension(420, 90));
        tokenPanel.setBorder(new EmptyBorder(8, 0, 4, 0));
        tokenPanel.add(new JLabel("<html><b>Token dat lai mat khau (het han sau 15 phut):</b></html>"),
                BorderLayout.NORTH);
        JTextField tfShowToken = new JTextField(generatedToken);
        tfShowToken.setEditable(false);
        tfShowToken.setFont(new Font("Monospaced", Font.PLAIN, 11));
        tfShowToken.setBackground(UIConstants.COLOR_NEUTRAL_BG);
        tfShowToken.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.COLOR_BORDER, 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        tokenPanel.add(tfShowToken, BorderLayout.CENTER);
        JLabel copyHint = new JLabel("<html><i>Sao chep token nay roi nhan OK de sang buoc 2.</i></html>");
        copyHint.setFont(UIConstants.FONT_SMALL);
        copyHint.setForeground(UIConstants.COLOR_TEXT_SECONDARY);
        tokenPanel.add(copyHint, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this, tokenPanel,
                "Token da duoc tao", JOptionPane.INFORMATION_MESSAGE);

        openResetStep2(generatedToken);
    }

    private void openResetStep2(String prefillToken) {
        JPanel step2Panel = new JPanel();
        step2Panel.setLayout(new BoxLayout(step2Panel, BoxLayout.Y_AXIS));
        step2Panel.setBorder(new EmptyBorder(8, 0, 4, 0));
        step2Panel.setPreferredSize(new Dimension(380, 170));

        step2Panel.add(new JLabel("Token xac nhan:"));
        step2Panel.add(Box.createVerticalStrut(4));
        JTextField tfToken = new JTextField(prefillToken);
        tfToken.setFont(new Font("Monospaced", Font.PLAIN, 11));
        tfToken.setPreferredSize(new Dimension(360, UIConstants.SIZE_INPUT_HEIGHT));
        step2Panel.add(tfToken);
        step2Panel.add(Box.createVerticalStrut(12));

        step2Panel.add(new JLabel("Mat khau moi (toi thieu 6 ky tu):"));
        step2Panel.add(Box.createVerticalStrut(4));
        JPasswordField pfNew = new JPasswordField();
        pfNew.setPreferredSize(new Dimension(360, UIConstants.SIZE_INPUT_HEIGHT));
        step2Panel.add(pfNew);
        step2Panel.add(Box.createVerticalStrut(12));

        step2Panel.add(new JLabel("Xac nhan mat khau moi:"));
        step2Panel.add(Box.createVerticalStrut(4));
        JPasswordField pfConfirm = new JPasswordField();
        pfConfirm.setPreferredSize(new Dimension(360, UIConstants.SIZE_INPUT_HEIGHT));
        step2Panel.add(pfConfirm);

        int res2 = JOptionPane.showConfirmDialog(this, step2Panel,
                "Quen mat khau - Buoc 2/2: Dat mat khau moi",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res2 != JOptionPane.OK_OPTION) return;

        String token     = tfToken.getText().trim();
        String newPw     = new String(pfNew.getPassword());
        String confirmPw = new String(pfConfirm.getPassword());

        if (token.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Token khong duoc de trong.",
                    "Loi", JOptionPane.WARNING_MESSAGE); return;
        }
        if (newPw.length() < 6) {
            JOptionPane.showMessageDialog(this, "Mat khau moi phai co it nhat 6 ky tu.",
                    "Loi", JOptionPane.WARNING_MESSAGE); return;
        }
        if (!newPw.equals(confirmPw)) {
            JOptionPane.showMessageDialog(this, "Xac nhan mat khau khong khop.",
                    "Loi", JOptionPane.WARNING_MESSAGE); return;
        }

        final boolean[] success  = {false};
        final String[]  errReset = {null};
        try {
            SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
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
                    "Loi ket noi: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (errReset[0] != null) {
            JOptionPane.showMessageDialog(this,
                    "Loi: " + errReset[0], "Loi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (success[0]) {
            JOptionPane.showMessageDialog(this,
                    "Dat lai mat khau thanh cong!\nVui long dang nhap lai voi mat khau moi.",
                    "Thanh cong", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Token khong hop le hoac da het han.\nVui long thu lai tu buoc 1.",
                    "That bai", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Accessor ──────────────────────────────────────────────────────────────

    public boolean isLoginSuccess() { return loginSuccess; }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UIConstants.FONT_BOLD);
        l.setForeground(UIConstants.COLOR_TEXT_PRIMARY);
        return l;
    }

    private void configureInputField(JTextField tf) {
        tf.setFont(UIConstants.FONT_BODY);
        // FlatLaf handles focus ring colour via UIConstants.COLOR_BORDER_FOCUS
        // set in FlatLafConfig. Only need to size + padding here.
        tf.putClientProperty("JTextField.placeholderText", "");
    }

    /**
     * Creates an underlined hyperlink-style JLabel.
     * Uses HTML for the underline — consistent across all Swing LAFs.
     */
    private JLabel buildLinkLabel(String text) {
        JLabel l = new JLabel("<html><u>" + text + "</u></html>");
        l.setFont(UIConstants.FONT_SMALL);
        l.setForeground(UIConstants.COLOR_PRIMARY);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                l.setForeground(UIConstants.COLOR_PRIMARY_DARK);
            }
            @Override public void mouseExited(MouseEvent e) {
                l.setForeground(UIConstants.COLOR_PRIMARY);
            }
        });
        return l;
    }

    // ── Inner class: shadow card panel ────────────────────────────────────────

    /**
     * A JPanel that draws a white rounded-rect background with a soft drop-shadow.
     * Compatible with WindowBuilder (named inner class, standard constructor).
     */
    static class CardPanel extends JPanel {

        private final int arcSize;

        /** Shadow offset and spread constants. */
        private static final int SHADOW_OFFSET_Y = 4;
        private static final int SHADOW_SPREAD    = 10;
        private static final int SHADOW_INSET     = 12;  // border reserved for shadow

        CardPanel(int arcSize) {
            this.arcSize = arcSize;
            setOpaque(false);
            // Reserve space around the card for shadow painting
            setBorder(BorderFactory.createEmptyBorder(
                SHADOW_INSET / 2,
                SHADOW_INSET / 2,
                SHADOW_INSET,
                SHADOW_INSET / 2));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth()  - SHADOW_INSET;
            int h = getHeight() - SHADOW_INSET;
            int x = SHADOW_INSET / 2;
            int y = SHADOW_INSET / 2;

            // Draw shadow layers (largest → smallest, low alpha)
            for (int i = SHADOW_SPREAD; i >= 1; i--) {
                float alpha = 0.028f * (SHADOW_SPREAD - i + 1);
                g2.setColor(new Color(
                    UIConstants.SHADOW_CARD.getRed(),
                    UIConstants.SHADOW_CARD.getGreen(),
                    UIConstants.SHADOW_CARD.getBlue(),
                    Math.min(255, (int)(alpha * 255))));
                g2.fill(new RoundRectangle2D.Float(
                    x - i,
                    y - i + SHADOW_OFFSET_Y,
                    w + i * 2f,
                    h + i * 2f,
                    arcSize + i,
                    arcSize + i));
            }

            // White card background
            g2.setColor(UIConstants.COLOR_SURFACE_CARD);
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, arcSize, arcSize));

            g2.dispose();
            super.paintComponent(g);
        }
    }
}