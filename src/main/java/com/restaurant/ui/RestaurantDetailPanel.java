package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import com.restaurant.dao.UserDAO;
import com.restaurant.model.Restaurant;
import com.restaurant.model.Restaurant.Status;

/**
 * Màn hình Chi tiết nhà hàng — read-only, hiển thị qua CardLayout của MainFrame.
 *
 * <p>Phase 3: Hiển thị 7 trường thông tin của nhà hàng trong các value-box
 * có bo góc, giống pattern trong HomePanel. Nút "← Quay lại" gọi onBack.
 *
 * <p>Tên chủ nhà hàng được tải bất đồng bộ (SwingWorker) sau khi populate()
 * được gọi, để tránh block EDT.
 */
public class RestaurantDetailPanel extends JPanel {

    // ── Back callback ─────────────────────────────────────────────────────────

    private final Runnable onBack;

    // ── Value labels (filled by populate()) ──────────────────────────────────

    private final JLabel valName      = valueLabel();
    private final JLabel valOwner     = valueLabel();
    private final JLabel valEmail     = valueLabel();
    private final JLabel valPhone     = valueLabel();
    private final JLabel valAddress   = valueLabel();
    private final JLabel valCreatedAt = valueLabel();
    private final JLabel valStatus    = valueLabel();

    // ── Constructor ───────────────────────────────────────────────────────────

    public RestaurantDetailPanel(Runnable onBack) {
        this.onBack = onBack;
        setBackground(UIConstants.BG_PAGE);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(24, 48, 24, 48));
        buildUI();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        // ── NORTH: page header ────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        JLabel titleLbl = new JLabel("Quản lý nhà hàng");
        titleLbl.setFont(UIConstants.FONT_TITLE);
        titleLbl.setForeground(UIConstants.TEXT_PRIMARY);
        topBar.add(titleLbl, BorderLayout.WEST);

        add(topBar, BorderLayout.NORTH);

        // ── CENTER: card ──────────────────────────────────────────────────────
        JPanel card = buildCard();
        JScrollPane scroll = new JScrollPane(card);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UIConstants.BG_PAGE);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);

        // ── SOUTH: back button ────────────────────────────────────────────────
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
        footer.setOpaque(false);
        RoundedButton btnBack = RoundedButton.outline("← Quay lại");
        btnBack.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnBack.addActionListener(e -> onBack.run());
        footer.add(btnBack);
        add(footer, BorderLayout.SOUTH);
    }

    /** Card trắng chứa section title + GridBag fields. */
    private JPanel buildCard() {
        // Outer wrapper để căn giữa card horizontally
        JPanel outer = new JPanel();
        outer.setOpaque(false);
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBorder(BorderFactory.createEmptyBorder(8, 0, 24, 0));

        // Card trắng, có shadow-border nhẹ
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout(0, 0));
        card.setBorder(BorderFactory.createEmptyBorder(28, 32, 32, 32));
        card.setMaximumSize(new Dimension(720, Integer.MAX_VALUE));
        card.setAlignmentX(CENTER_ALIGNMENT);

        // Section title
        JLabel sectionTitle = new JLabel("Chi tiết nhà hàng");
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        sectionTitle.setForeground(UIConstants.TEXT_PRIMARY);
        sectionTitle.setHorizontalAlignment(SwingConstants.LEFT);
        sectionTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        // Fields
        JPanel fieldsPanel = buildFieldsPanel();

        card.add(sectionTitle,  BorderLayout.NORTH);
        card.add(fieldsPanel,   BorderLayout.CENTER);

        outer.add(card);
        outer.add(Box.createVerticalGlue());
        return outer;
    }

    /** GridBagLayout: 7 field rows — label (col 0) + value-box (col 1). */
    private JPanel buildFieldsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.insets  = new Insets(6, 0, 6, 0);
        gbc.anchor  = GridBagConstraints.WEST;

        addFieldRow(panel, gbc, 0, "Tên nhà hàng",  valName);
        addFieldRow(panel, gbc, 1, "Chủ nhà hàng",  valOwner);
        addFieldRow(panel, gbc, 2, "Email",          valEmail);
        addFieldRow(panel, gbc, 3, "SĐT",            valPhone);
        addFieldRow(panel, gbc, 4, "Địa chỉ",        valAddress);
        addFieldRow(panel, gbc, 5, "Ngày tạo",       valCreatedAt);
        addFieldRow(panel, gbc, 6, "Trạng thái",     valStatus);

        return panel;
    }

    private void addFieldRow(JPanel panel, GridBagConstraints gbc,
                              int row, String labelText, JLabel valueLabel) {
        // Col 0: label
        gbc.gridx   = 0;
        gbc.gridy   = row;
        gbc.weightx = 0;
        gbc.ipadx   = 0;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        lbl.setPreferredSize(new Dimension(180, 40));
        panel.add(lbl, gbc);

        // Col 1: value box
        gbc.gridx   = 1;
        gbc.weightx = 1;
        panel.add(buildValueBox(valueLabel), gbc);
    }

    /** Rounded-border white box chứa 1 JLabel. */
    private JPanel buildValueBox(JLabel inner) {
        JPanel box = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        box.setPreferredSize(new Dimension(0, 40));
        box.add(inner, BorderLayout.CENTER);
        return box;
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    /**
     * Điền dữ liệu nhà hàng vào các value-box.
     * Chủ nhà hàng được load bất đồng bộ để không block EDT.
     *
     * @param r đối tượng Restaurant cần hiển thị
     */
    public void populate(Restaurant r) {
        if (r == null) return;

        valName     .setText(safe(r.getName()));
        valOwner    .setText("Đang tải...");
        valEmail    .setText(safe(r.getEmail()));
        valPhone    .setText(safe(r.getPhone()));
        valAddress  .setText(safe(r.getAddress()));
        valCreatedAt.setText(r.getCreatedAt() != null
                ? r.getCreatedAt().toLocalDate().toString() : "—");

        // Trạng thái có màu sắc
        if (r.getStatus() == Status.ACTIVE) {
            valStatus.setText("Hoạt động");
            valStatus.setForeground(UIConstants.SUCCESS);
            valStatus.setFont(UIConstants.FONT_BOLD);
        } else {
            valStatus.setText(r.getStatus() != null ? r.getStatus().label() : "—");
            valStatus.setForeground(UIConstants.DANGER);
            valStatus.setFont(UIConstants.FONT_BOLD);
        }

        // Load tên chủ nhà hàng trên background thread
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    List<UserDAO.AdminUser> admins = new UserDAO().findRestaurantAdmins();
                    return admins.stream()
                        .filter(a -> a.getRestaurantId() == r.getRestaurantId())
                        .map(UserDAO.AdminUser::getName)
                        .findFirst()
                        .orElse("Chưa gán");
                } catch (Exception e) {
                    return "—";
                }
            }
            @Override
            protected void done() {
                try { valOwner.setText(get()); }
                catch (Exception e) { valOwner.setText("—"); }
            }
        }.execute();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JLabel valueLabel() {
        JLabel l = new JLabel("—");
        l.setFont(UIConstants.FONT_BODY);
        l.setForeground(UIConstants.TEXT_PRIMARY);
        return l;
    }

    private static String safe(String s) {
        return (s != null && !s.isBlank()) ? s : "—";
    }
}