package com.restaurant.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

public class RoundedButton extends JButton {
    private Color bgColor;
    private Color hoverColor;
    private Color textColor;
    private int radius;

    public RoundedButton(String text) {
        this(text, UIConstants.PRIMARY, UIConstants.PRIMARY_DARK, UIConstants.TEXT_WHITE, UIConstants.CORNER_RADIUS);
    }

    public RoundedButton(String text, Color bg, Color hover, Color fg, int radius) {
        super(text);
        this.bgColor = bg;
        this.hoverColor = hover;
        this.textColor = fg;
        this.radius = radius;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setForeground(fg);
        setFont(UIConstants.FONT_NAV);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { repaint(); }
            @Override public void mouseExited(MouseEvent e) { repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Point p = getMousePosition();
        boolean hover = p != null;
        g2.setColor(hover ? hoverColor : bgColor);
        g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), radius, radius));
        g2.dispose();
        super.paintComponent(g);
    }

    public static RoundedButton danger(String text) {
        return new RoundedButton(text, UIConstants.DANGER, new Color(0xDC2626), Color.WHITE, UIConstants.CORNER_RADIUS);
    }

    public static RoundedButton outline(String text) {
        RoundedButton btn = new RoundedButton(text, Color.WHITE, new Color(0xF3F4F6), UIConstants.TEXT_PRIMARY, UIConstants.CORNER_RADIUS) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Point p = getMousePosition();
                g2.setColor(p != null ? new Color(0xF3F4F6) : Color.WHITE);
                g2.fill(new java.awt.geom.RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS));
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.draw(new java.awt.geom.RoundRectangle2D.Double(0.5, 0.5, getWidth()-1, getHeight()-1, UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(UIConstants.TEXT_PRIMARY);
        return btn;
    }
}
