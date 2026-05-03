package com.restaurant.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * StaffHeader — header bar dùng chung cho KitchenPanel, WaiterServicePanel, CashierPanel.
 *
 * <p>Layout (height 56px, nền trắng, border bottom 1px BORDER_COLOR):
 * <pre>
 *  ┌────────────────────────────────────────────────────────────────┐
 *  │ ⛁  SmartRestaurant  [Đầu bếp]   ··   🌐  Nhà hàng ABC  [Kết ca]│
 *  └────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <ul>
 *   <li><b>LEFT</b>  – icon ⛁ + tên hệ thống + badge role (nền PRIMARY bo tròn)</li>
 *   <li><b>RIGHT</b> – icon 🌐 + tên nhà hàng + nút "Kết ca" (viền PRIMARY, nền trắng)</li>
 * </ul>
 *
 * Nút "Kết ca" hiển thị dialog xác nhận trước khi gọi {@code onEndShift}.
 * Truyền {@code null} cho {@code onEndShift} để ẩn nút (dùng cho CashierPanel).
 */
public final class StaffHeader {

    private StaffHeader() {}

    // ─── Public factory ───────────────────────────────────────────────────────

    /**
     * Tạo header bar đã sẵn sàng dùng.
     *
     * @param roleName       Tên role hiển thị trong badge: "Đầu bếp", "Phục vụ", "Thu ngân", …
     * @param restaurantName Tên nhà hàng (có thể rỗng, sẽ để trống thay vì crash)
     * @param onEndShift     Callback khi người dùng xác nhận kết ca; {@code null} = ẩn nút
     * @return JPanel height 56px đã cấu hình đầy đủ
     */
    public static JPanel create(String roleName,
                                String restaurantName,
                                Runnable onEndShift) {

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BG_WHITE);
        header.setPreferredSize(new Dimension(0, 56));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 24, 0, 24)));

        header.add(buildLeft(roleName),                         BorderLayout.WEST);
        header.add(buildRight(restaurantName, onEndShift, header), BorderLayout.EAST);

        return header;
    }

    // ─── LEFT side ───────────────────────────────────────────────────────────

    private static JPanel buildLeft(String roleName) {
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel iconLabel = new JLabel("⛁");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        iconLabel.setForeground(UIConstants.PRIMARY);

        JLabel sysName = new JLabel("SmartRestaurant");
        sysName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sysName.setForeground(UIConstants.PRIMARY);

        JLabel badge = new RoleBadge(roleName);

        left.add(iconLabel);
        left.add(sysName);
        left.add(badge);
        return left;
    }

    // ─── RIGHT side ───────────────────────────────────────────────────────────

    private static JPanel buildRight(String restaurantName,
                                     Runnable onEndShift,
                                     JPanel parent) {
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        JLabel globeIcon = new JLabel("🌐");
        globeIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));

        String displayName = (restaurantName != null && !restaurantName.isBlank())
                ? restaurantName : "";
        JLabel restaurantLabel = new JLabel(displayName);
        restaurantLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        restaurantLabel.setForeground(UIConstants.PRIMARY);

        right.add(globeIcon);
        right.add(restaurantLabel);

        if (onEndShift != null) {
            JButton btnEnd = new RoundedOutlineButton("Kết ca");
            btnEnd.addActionListener(e -> {
                // parent dùng để định vị dialog; nếu null JOptionPane tự center màn hình
                int result = JOptionPane.showConfirmDialog(
                        parent,
                        "Xác nhận kết ca?",
                        "Kết ca",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    onEndShift.run();
                }
            });
            right.add(btnEnd);
        }

        return right;
    }

    // ─── Inner: RoleBadge ────────────────────────────────────────────────────

    /**
     * Badge hiển thị role với nền PRIMARY bo tròn và chữ trắng.
     */
    private static class RoleBadge extends JLabel {

        RoleBadge(String text) {
            super(text);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setForeground(UIConstants.TEXT_WHITE);
            setOpaque(false);
            // Padding ngang để badge không quá sát chữ
            setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
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

    // ─── Inner: RoundedOutlineButton ─────────────────────────────────────────

    /**
     * Nút "Kết ca": viền PRIMARY 1.5px, nền trắng, text PRIMARY.
     * Reuse pattern từ WaiterServicePanel / KitchenPanel.
     */
    private static class RoundedOutlineButton extends JButton {

        RoundedOutlineButton(String text) {
            super(text);
            setFont(UIConstants.FONT_BODY);
            setForeground(UIConstants.PRIMARY);
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(80, 32));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(UIConstants.PRIMARY);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}