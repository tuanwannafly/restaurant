package com.restaurant.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

/**
 * Màn hình phục vụ bàn dành cho role WAITER — redesign Phase 4B.
 * <p>
 * Gồm 3 tab placeholder:
 * <ol>
 *   <li><b>Phục vụ bàn</b> – danh sách món READY cần giao.</li>
 *   <li><b>Dọn bàn</b>     – bàn DIRTY / CLEANING cần xử lý.</li>
 *   <li><b>Đã hủy</b>      – lịch sử món / đơn bị hủy.</li>
 * </ol>
 * Auto-refresh mỗi 5 giây. Timer chỉ chạy khi panel đang hiển thị
 * (kiểm soát qua {@link AncestorListener}).
 * <p>
 * RBAC: yêu cầu {@link Permission#VIEW_WAITER_SERVICE}.
 */
public class WaiterServicePanel extends JPanel {

    // ─── DAOs ─────────────────────────────────────────────────────────────────

    private final KitchenDAO kitchenDAO = new KitchenDAO();
    private final TableDAO   tableDAO   = new TableDAO();

    // ─── Constructor ──────────────────────────────────────────────────────────

    public WaiterServicePanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BG_PAGE);

        // RBAC guard
        if (!AppSession.getInstance().hasPermission(Permission.VIEW_WAITER_SERVICE)) {
            JLabel denied = new JLabel("Không có quyền truy cập", SwingConstants.CENTER);
            denied.setFont(UIConstants.FONT_TITLE);
            denied.setForeground(UIConstants.TEXT_SECONDARY);
            add(denied, BorderLayout.CENTER);
            return;
        }

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTabs(),   BorderLayout.CENTER);
        setupAncestorListener();
    }

    // ─── Header ───────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BG_WHITE);
        header.setPreferredSize(new Dimension(0, 56));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 24, 0, 24)));

        // ── LEFT: logo + tên app + badge role ──
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel iconLabel = new JLabel("⛁");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        iconLabel.setForeground(UIConstants.PRIMARY);

        JLabel appName = new JLabel("SmartRestaurant");
        appName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        appName.setForeground(UIConstants.PRIMARY);

        JLabel roleBadge = new RoleBadge("Phục vụ");

        left.add(iconLabel);
        left.add(appName);
        left.add(roleBadge);
        header.add(left, BorderLayout.WEST);

        // ── RIGHT: icon toàn cầu + tên nhà hàng + nút kết ca ──
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        JLabel globeIcon = new JLabel("🌐");
        globeIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));

        String restaurantName = "";
        try {
            restaurantName = com.restaurant.data.DataManager.getInstance()
                    .getMyRestaurant().getName();
        } catch (Exception ignored) {}

        JLabel restaurantLabel = new JLabel(restaurantName);
        restaurantLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        restaurantLabel.setForeground(UIConstants.PRIMARY);

        JButton endShiftBtn = new RoundedOutlineButton("Kết ca");
        endShiftBtn.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Xác nhận kết ca?",
                    "Kết ca",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                if (w != null) w.dispose();
            }
        });

        right.add(globeIcon);
        right.add(restaurantLabel);
        right.add(endShiftBtn);
        header.add(right, BorderLayout.EAST);

        return header;
    }

    // ─── Tabs ─────────────────────────────────────────────────────────────────

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(UIConstants.FONT_BOLD);
        tabs.setBackground(UIConstants.BG_PAGE);

        tabs.addTab("🚚  Phục vụ bàn", buildDeliveryPlaceholder());
        tabs.addTab("🧹  Dọn bàn",     buildCleanPlaceholder());
        tabs.addTab("🚫  Đã hủy",      buildCancelledPlaceholder());

        return tabs;
    }

    // ─── Placeholder panels ───────────────────────────────────────────────────

    private JPanel buildDeliveryPlaceholder() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(UIConstants.BG_PAGE);
        JLabel l = new JLabel("Phục vụ bàn – đang xây dựng", SwingConstants.CENTER);
        l.setFont(UIConstants.FONT_BODY);
        l.setForeground(UIConstants.TEXT_SECONDARY);
        p.add(l);
        return p;
    }

    private JPanel buildCleanPlaceholder() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(UIConstants.BG_PAGE);
        JLabel l = new JLabel("Dọn bàn – đang xây dựng", SwingConstants.CENTER);
        l.setFont(UIConstants.FONT_BODY);
        l.setForeground(UIConstants.TEXT_SECONDARY);
        p.add(l);
        return p;
    }

    private JPanel buildCancelledPlaceholder() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(UIConstants.BG_PAGE);
        JLabel l = new JLabel("Đã hủy – đang xây dựng", SwingConstants.CENTER);
        l.setFont(UIConstants.FONT_BODY);
        l.setForeground(UIConstants.TEXT_SECONDARY);
        p.add(l);
        return p;
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    /**
     * Public entry-point – được gọi từ MainFrame.navigateTo() và PollManager.
     * Phase 4B sẽ implement logic thực tế.
     */
    public void loadData() {
        System.out.println("[WaiterServicePanel] loadData called – Phase 4B sẽ implement");
    }

    // ─── AncestorListener — delegate lifecycle to PollManager ─────────────────

    private void setupAncestorListener() {
        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent e) {
                loadData();
                PollManager.getInstance().register("waiter",
                        WaiterServicePanel.this::loadData, 5_000);
            }

            @Override
            public void ancestorRemoved(AncestorEvent e) {
                PollManager.getInstance().unregister("waiter");
            }

            @Override
            public void ancestorMoved(AncestorEvent e) {}
        });
    }

    // ─── Inner classes ────────────────────────────────────────────────────────

    /**
     * Badge hiển thị role người dùng với nền bo tròn màu PRIMARY.
     */
    private static class RoleBadge extends JLabel {

        RoleBadge(String text) {
            super(text);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setForeground(UIConstants.TEXT_WHITE);
            setOpaque(false);
            setPreferredSize(new Dimension(80, 28));
            setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
            setHorizontalAlignment(SwingConstants.CENTER);
        }

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
    }

    /**
     * Nút bo tròn viền PRIMARY, nền trắng — dùng cho "Kết ca".
     */
    private static class RoundedOutlineButton extends JButton {

        RoundedOutlineButton(String text) {
            super(text);
            setFont(UIConstants.FONT_BODY);
            setForeground(UIConstants.PRIMARY);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setPreferredSize(new Dimension(80, 32));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // Fill trắng
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

            // Viền PRIMARY 1.5f
            g2.setColor(UIConstants.PRIMARY);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}