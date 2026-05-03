package com.restaurant.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.AbstractBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.restaurant.data.DataManager;
import com.restaurant.model.Restaurant;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

/**
 * MyRestaurantInfoPanel — dành cho Restaurant Admin.
 * Chỉ hiển thị: thông tin cơ bản + logo + giờ hoạt động.
 * KHÔNG hiển thị dashboard tổng quan kiểu platform-admin.
 */
public class MyRestaurantInfoPanel extends JPanel {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color PAGE_BG      = new Color(0xF8FAFC);
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color BORDER_CLR   = new Color(0xE2E8F0);
    private static final Color LABEL_CLR    = new Color(0x64748B);
    private static final Color TEXT_CLR     = new Color(0x0F172A);
    private static final Color PRIMARY      = new Color(0x2563EB);
    private static final Color SUCCESS_CLR  = new Color(0x16A34A);
    private static final Color DANGER_CLR   = new Color(0xDC2626);
    private static final Color FIELD_BORDER = new Color(0xCBD5E1);
    private static final Color FIELD_FOCUS  = new Color(0x2563EB);
    private static final Color BTN_SAVE_BG  = new Color(0x2563EB);
    private static final Color BTN_SAVE_HOV = new Color(0x1D4ED8);

    private static final Font FONT_TITLE   = new Font("Segoe UI", Font.BOLD, 20);
    private static final Font FONT_SECTION = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font FONT_LABEL   = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BOLD_13 = new Font("Segoe UI", Font.BOLD,  13);
    private static final Font FONT_BODY    = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_SMALL   = new Font("Segoe UI", Font.PLAIN, 11);

    private static final int FIELD_H = 36;
    private static final int ARC     = 8;

    // ── Form fields ───────────────────────────────────────────────────────────
    private CleanField tfName, tfAddress, tfPhone, tfEmail;
    private JTextArea  taDesc;
    private JLabel     lblCreatedAt, lblStatus;

    // ── Logo ──────────────────────────────────────────────────────────────────
    private JLabel logoCircle;

    // ── Header ────────────────────────────────────────────────────────────────
    private JLabel  headerName;
    private JButton btnSave, btnReset;
    private JLabel  lblMsg;

    // ── Data ──────────────────────────────────────────────────────────────────
    private Restaurant current;
    private String     pendingLogoUrl;

    private static final String POLL_KEY = "my_restaurant_clean";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ═════════════════════════════════════════════════════════════════════════
    public MyRestaurantInfoPanel() {
        setLayout(new BorderLayout());
        setBackground(PAGE_BG);
        buildUI();

        addAncestorListener(new AncestorListener() {
            @Override public void ancestorAdded(AncestorEvent e) {
                PollManager.getInstance().register(
                        POLL_KEY, MyRestaurantInfoPanel.this::loadData, 60_000);
            }
            @Override public void ancestorRemoved(AncestorEvent e) {
                PollManager.getInstance().unregister(POLL_KEY);
            }
            @Override public void ancestorMoved(AncestorEvent e) {}
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BUILD UI
    // ═════════════════════════════════════════════════════════════════════════

    private void buildUI() {
        add(buildPageHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setBackground(PAGE_BG);
        body.setBorder(BorderFactory.createEmptyBorder(20, 24, 24, 24));

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill    = GridBagConstraints.BOTH;
        gc.gridy   = 0;
        gc.weighty = 1.0;

        // Left 60%
        gc.gridx   = 0;
        gc.weightx = 0.60;
        gc.insets  = new Insets(0, 0, 0, 12);
        body.add(buildInfoCard(), gc);

        // Right 40% — chỉ logo + giờ hoạt động (không có stats)
        gc.gridx   = 1;
        gc.weightx = 0.40;
        gc.insets  = new Insets(0, 0, 0, 0);
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.weighty = 0;

        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setOpaque(false);
        rightCol.add(buildLogoCard());
        rightCol.add(Box.createVerticalStrut(12));
        rightCol.add(buildHoursCard());
        body.add(rightCol, gc);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getViewport().setBackground(PAGE_BG);
        scroll.setBackground(PAGE_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    // ── PAGE HEADER ───────────────────────────────────────────────────────────

    private JPanel buildPageHeader() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_CLR),
                BorderFactory.createEmptyBorder(16, 24, 16, 24)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);

        JLabel title = new JLabel("Thông tin nhà hàng");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_CLR);

        headerName = new JLabel("Đang tải...");
        headerName.setFont(FONT_BODY);
        headerName.setForeground(LABEL_CLR);

        lblStatus = new JLabel("● Hoạt động");
        lblStatus.setFont(FONT_BOLD_13);
        lblStatus.setForeground(SUCCESS_CLR);

        left.add(title);
        left.add(sep());
        left.add(headerName);
        left.add(sep());
        left.add(lblStatus);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        lblMsg = new JLabel("");
        lblMsg.setFont(FONT_SMALL);
        lblMsg.setForeground(LABEL_CLR);

        btnReset = buildOutlineBtn("Đặt lại", 90);
        btnReset.addActionListener(e -> { if (current != null) populate(current); });

        btnSave = buildPrimaryBtn("Lưu thay đổi", 130);
        btnSave.setEnabled(AppSession.getInstance().hasPermission(Permission.EDIT_OWN_RESTAURANT));
        btnSave.addActionListener(e -> doSave());

        right.add(lblMsg);
        right.add(btnReset);
        right.add(btnSave);

        bar.add(left,  BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ── LEFT CARD: Thông tin cơ bản ──────────────────────────────────────────

    private JPanel buildInfoCard() {
        JPanel card = makeCard();
        card.setLayout(new BorderLayout(0, 16));

        card.add(sectionLabel("THÔNG TIN CƠ BẢN"), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        GridBagConstraints lc = labelGbc();
        GridBagConstraints fc = fieldGbc();
        int r = 0;

        tfName    = new CleanField(); addFormRow(form, lc, fc, r++, "Tên nhà hàng",  tfName);
        tfAddress = new CleanField(); addFormRow(form, lc, fc, r++, "Địa chỉ",        tfAddress);
        tfPhone   = new CleanField(); addFormRow(form, lc, fc, r++, "Số điện thoại",  tfPhone);
        tfEmail   = new CleanField(); addFormRow(form, lc, fc, r++, "Email liên hệ",  tfEmail);

        // Mô tả
        lc.gridy = r; lc.anchor = GridBagConstraints.NORTHWEST; lc.insets = new Insets(6, 0, 12, 12);
        form.add(fieldLabel("Mô tả ngắn"), lc);
        lc.anchor = GridBagConstraints.WEST; lc.insets = new Insets(0, 0, 12, 12);

        fc.gridy = r++;
        taDesc = new JTextArea(3, 0);
        taDesc.setFont(FONT_BODY);
        taDesc.setForeground(TEXT_CLR);
        taDesc.setLineWrap(true);
        taDesc.setWrapStyleWord(true);
        taDesc.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(ARC, FIELD_BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        taDesc.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                taDesc.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(ARC, FIELD_FOCUS),
                        BorderFactory.createEmptyBorder(7, 9, 7, 9)));
            }
            @Override public void focusLost(FocusEvent e) {
                taDesc.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(ARC, FIELD_BORDER),
                        BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            }
        });
        JScrollPane taScroll = new JScrollPane(taDesc);
        taScroll.setBorder(null);
        taScroll.setPreferredSize(new Dimension(0, 80));
        form.add(taScroll, fc);

        // Ngày tạo (read-only)
        lc.gridy = r; fc.gridy = r++;
        lc.insets = new Insets(0, 0, 0, 12);
        form.add(fieldLabel("Ngày tạo"), lc);
        lblCreatedAt = new JLabel("—");
        lblCreatedAt.setFont(FONT_BODY);
        lblCreatedAt.setForeground(LABEL_CLR);
        form.add(lblCreatedAt, fc);

        card.add(form, BorderLayout.CENTER);
        return card;
    }

    // ── RIGHT CARD 1: Logo ────────────────────────────────────────────────────

    private JPanel buildLogoCard() {
        JPanel card = makeCard();
        card.setLayout(new BorderLayout(0, 12));
        card.add(sectionLabel("LOGO NHÀ HÀNG"), BorderLayout.NORTH);

        JPanel content = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        content.setOpaque(false);

        logoCircle = new JLabel("NH", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xEFF6FF));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(0xBFDBFE));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(1, 1, getWidth() - 2, getHeight() - 2);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        logoCircle.setPreferredSize(new Dimension(64, 64));
        logoCircle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        logoCircle.setForeground(PRIMARY);
        logoCircle.setOpaque(false);
        logoCircle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoCircle.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { pickLogo(); }
        });

        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.setOpaque(false);

        JButton btnUpload = buildOutlineBtn("Tải lên", 100);
        btnUpload.setAlignmentX(LEFT_ALIGNMENT);
        btnUpload.setEnabled(AppSession.getInstance().hasPermission(Permission.EDIT_OWN_RESTAURANT));
        btnUpload.addActionListener(e -> pickLogo());

        JLabel h1 = smallHint("JPG, PNG — tối đa 2 MB");
        h1.setAlignmentX(LEFT_ALIGNMENT);

        actions.add(btnUpload);
        actions.add(Box.createVerticalStrut(6));
        actions.add(h1);

        content.add(logoCircle);
        content.add(actions);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    // ── RIGHT CARD 2: Giờ hoạt động ───────────────────────────────────────────

    private JPanel buildHoursCard() {
        JPanel card = makeCard();
        card.setLayout(new BorderLayout(0, 12));
        card.add(sectionLabel("GIỜ HOẠT ĐỘNG"), BorderLayout.NORTH);

        JPanel rows = new JPanel(new GridLayout(3, 2, 0, 8));
        rows.setOpaque(false);

        String[][] schedule = {
            {"Thứ 2 – 6", "07:00 – 22:00"},
            {"Thứ 7",     "08:00 – 23:00"},
            {"Chủ nhật",  "Đóng cửa"}
        };

        for (int i = 0; i < 3; i++) {
            JLabel dayLbl = new JLabel(schedule[i][0]);
            dayLbl.setFont(FONT_BODY);
            dayLbl.setForeground(LABEL_CLR);

            JLabel hrLbl = new JLabel(schedule[i][1], SwingConstants.RIGHT);
            hrLbl.setFont(FONT_BOLD_13);
            hrLbl.setForeground(i < 2 ? SUCCESS_CLR : DANGER_CLR);

            rows.add(dayLbl);
            rows.add(hrLbl);
        }

        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // LOGO PICKER
    // ═════════════════════════════════════════════════════════════════════════

    private void pickLogo() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Image files", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File src = chooser.getSelectedFile();
        if (src.length() > 2L * 1024 * 1024) {
            showMsg("File vượt quá 2 MB!", DANGER_CLR);
            return;
        }
        try {
            File destDir = new File("assets/restaurant_logos");
            destDir.mkdirs();
            String fn   = System.currentTimeMillis() + "_" + src.getName();
            File   dest = new File(destDir, fn);
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            pendingLogoUrl = "assets/restaurant_logos/" + fn;

            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(dest);
            if (img != null) {
                logoCircle.setIcon(new ImageIcon(makeCircleImage(img, 64)));
                logoCircle.setText("");
            }
            showMsg("Logo sẽ được lưu khi nhấn 'Lưu thay đổi'.", LABEL_CLR);
        } catch (Exception ex) {
            showMsg("Lỗi tải logo: " + ex.getMessage(), DANGER_CLR);
        }
    }

    private java.awt.image.BufferedImage makeCircleImage(java.awt.image.BufferedImage src, int size) {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, size, size));
        g2.drawImage(src.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null);
        g2.dispose();
        return out;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DATA
    // ═════════════════════════════════════════════════════════════════════════

    public void loadData() {
        showMsg("Đang tải...", LABEL_CLR);
        btnSave.setEnabled(false);

        new SwingWorker<Restaurant, Void>() {
            @Override protected Restaurant doInBackground() {
                return DataManager.getInstance().getMyRestaurant();
            }
            @Override protected void done() {
                try {
                    current = get();
                    if (current == null) { showMsg("Không tìm thấy nhà hàng.", DANGER_CLR); return; }
                    populate(current);
                    btnSave.setEnabled(AppSession.getInstance().hasPermission(Permission.EDIT_OWN_RESTAURANT));
                    showMsg("", Color.WHITE);
                } catch (Exception ex) {
                    showMsg("Lỗi tải dữ liệu.", DANGER_CLR);
                }
            }
        }.execute();
    }

    public void populate(Restaurant r) {
        pendingLogoUrl = null;

        tfName.setText(nv(r.getName()));
        tfAddress.setText(nv(r.getAddress()));
        tfPhone.setText(nv(r.getPhone()));
        tfEmail.setText(nv(r.getEmail()));
        taDesc.setText("");

        headerName.setText(nv(r.getName()));
        lblCreatedAt.setText(r.getCreatedAt() != null
                ? r.getCreatedAt().format(DATE_FMT) : "—");

        boolean active = r.getStatus() == Restaurant.Status.ACTIVE;
        lblStatus.setText(active ? "● Hoạt động" : "● Tạm dừng");
        lblStatus.setForeground(active ? SUCCESS_CLR : DANGER_CLR);

        // Logo
        if (r.getLogoUrl() != null && !r.getLogoUrl().isBlank()) {
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new File(r.getLogoUrl()));
                if (img != null) {
                    logoCircle.setIcon(new ImageIcon(makeCircleImage(img, 64)));
                    logoCircle.setText("");
                }
            } catch (Exception ignored) {}
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SAVE
    // ═════════════════════════════════════════════════════════════════════════

    private void doSave() {
        if (current == null) return;
        String name = tfName.getText().trim();
        if (name.isEmpty()) {
            showMsg("Tên nhà hàng không được để trống.", DANGER_CLR);
            tfName.requestFocus();
            return;
        }

        Restaurant updated = new Restaurant(
                current.getRestaurantId(), name,
                tfAddress.getText().trim(),
                tfPhone.getText().trim(),
                tfEmail.getText().trim(),
                current.getStatus(),
                current.getCreatedAt());
        updated.setLogoUrl(pendingLogoUrl != null ? pendingLogoUrl : current.getLogoUrl());

        btnSave.setEnabled(false);
        showMsg("Đang lưu...", LABEL_CLR);

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                DataManager.getInstance().updateMyRestaurant(updated);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    current = updated;
                    DataManager.getInstance().invalidateRestaurantCache();
                    pendingLogoUrl = null;
                    headerName.setText(updated.getName());
                    showMsg("✓  Đã lưu thành công", SUCCESS_CLR);
                    ToastNotification.show(MyRestaurantInfoPanel.this,
                            "Thông tin nhà hàng đã được cập nhật.",
                            ToastNotification.Type.SUCCESS);
                } catch (Exception ex) {
                    showMsg("Lưu thất bại: " + ex.getMessage(), DANGER_CLR);
                } finally {
                    btnSave.setEnabled(AppSession.getInstance().hasPermission(Permission.EDIT_OWN_RESTAURANT));
                }
            }
        }.execute();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FACTORIES / HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private JPanel makeCard() {
        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(10, BORDER_CLR),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_SECTION);
        l.setForeground(LABEL_CLR);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        return l;
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_LABEL);
        l.setForeground(LABEL_CLR);
        l.setPreferredSize(new Dimension(130, FIELD_H));
        return l;
    }

    private GridBagConstraints labelGbc() {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx   = 0;
        gc.anchor  = GridBagConstraints.WEST;
        gc.weightx = 0;
        gc.insets  = new Insets(0, 0, 12, 12);
        return gc;
    }

    private GridBagConstraints fieldGbc() {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx   = 1;
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.insets  = new Insets(0, 0, 12, 0);
        return gc;
    }

    private void addFormRow(JPanel form, GridBagConstraints lc, GridBagConstraints fc,
                             int row, String labelText, CleanField field) {
        lc.gridy = row;
        form.add(fieldLabel(labelText), lc);
        fc.gridy = row;
        field.setPreferredSize(new Dimension(0, FIELD_H));
        form.add(field, fc);
    }

    private JButton buildPrimaryBtn(String text, int w) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled()
                        ? (getModel().isRollover() ? BTN_SAVE_HOV : BTN_SAVE_BG)
                        : new Color(0xCBD5E1));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_BOLD_13);
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(w, 34));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton buildOutlineBtn(String text, int w) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(0xF8FAFC) : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
                g2.setColor(FIELD_BORDER);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_LABEL);
        btn.setForeground(TEXT_CLR);
        btn.setPreferredSize(new Dimension(w, 34));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel smallHint(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_SMALL);
        l.setForeground(new Color(0x94A3B8));
        return l;
    }

    private JLabel sep() {
        JLabel l = new JLabel("·");
        l.setFont(FONT_BODY);
        l.setForeground(BORDER_CLR);
        return l;
    }

    private void showMsg(String text, Color color) {
        if (lblMsg != null) { lblMsg.setText(text); lblMsg.setForeground(color); }
    }

    private static String nv(String s) { return s != null ? s : ""; }

    // ═════════════════════════════════════════════════════════════════════════
    // INNER: CleanField
    // ═════════════════════════════════════════════════════════════════════════

    private static class CleanField extends JTextField {
        CleanField() {
            setFont(new Font("Segoe UI", Font.PLAIN, 13));
            setForeground(new Color(0x0F172A));
            setBackground(Color.WHITE);
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(
                    new RoundBorder(8, new Color(0xCBD5E1)),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)));

            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) {
                    setBorder(BorderFactory.createCompoundBorder(
                            new RoundBorder(8, new Color(0x2563EB)),
                            BorderFactory.createEmptyBorder(5, 9, 5, 9)));
                }
                @Override public void focusLost(FocusEvent e) {
                    setBorder(BorderFactory.createCompoundBorder(
                            new RoundBorder(8, new Color(0xCBD5E1)),
                            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
                }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isEnabled() ? Color.WHITE : new Color(0xF8FAFC));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override protected void paintBorder(Graphics g) { /* handled in border object */ }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // INNER: RoundBorder
    // ═════════════════════════════════════════════════════════════════════════

    private static class RoundBorder extends AbstractBorder {
        private final int   arc;
        private final Color color;
        RoundBorder(int arc, Color color) { this.arc = arc; this.color = color; }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
            g2.dispose();
        }
    }
}