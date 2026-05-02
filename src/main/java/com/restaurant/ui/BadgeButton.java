package com.restaurant.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;

import javax.swing.JButton;

/**
 * JButton với badge số đỏ ở góc trên-phải.
 * setBadgeCount(0) → ẩn badge.
 * setBadgeCount(n>0) → hiện circle đỏ + số n.
 */
public class BadgeButton extends JButton {

    private int badgeCount = 0;
    private static final Color BADGE_BG   = new Color(0xEF4444); // đỏ
    private static final Color BADGE_TEXT = Color.WHITE;
    private static final int   BADGE_SIZE = 18;

    public BadgeButton(String text) {
        super(text);
        setOpaque(false);
        setContentAreaFilled(false);
    }

    public void setBadgeCount(int n) {
        this.badgeCount = Math.max(0, n);
        repaint();
    }

    public int getBadgeCount() {
        return badgeCount;
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Vẽ nền active / hover (giống nav button)
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        if (getClientProperty("active") == Boolean.TRUE) {
            g2.setColor(UIConstants.PRIMARY_LIGHT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                    UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
        } else {
            Point p = getMousePosition();
            if (p != null) {
                g2.setColor(new Color(0xF3F4F6));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
            }
        }
        g2.dispose();

        super.paintComponent(g);

        if (badgeCount <= 0) return;

        // Vẽ badge đỏ
        Graphics2D g2b = (Graphics2D) g.create();
        g2b.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int x = getWidth() - BADGE_SIZE - 2;
        int y = 2;

        g2b.setColor(BADGE_BG);
        g2b.fillOval(x, y, BADGE_SIZE, BADGE_SIZE);

        String label = badgeCount > 99 ? "99+" : String.valueOf(badgeCount);
        g2b.setColor(BADGE_TEXT);
        g2b.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics fm = g2b.getFontMetrics();
        int tx = x + (BADGE_SIZE - fm.stringWidth(label)) / 2;
        int ty = y + (BADGE_SIZE + fm.getAscent() - fm.getDescent()) / 2;
        g2b.drawString(label, tx, ty);
        g2b.dispose();
    }
}