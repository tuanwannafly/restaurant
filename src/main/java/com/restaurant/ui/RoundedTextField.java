package com.restaurant.ui;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class RoundedTextField extends JTextField {
    public RoundedTextField(String placeholder) {
        setFont(UIConstants.FONT_BODY);
        setForeground(UIConstants.TEXT_PRIMARY);
        setBorder(new RoundedBorder(UIConstants.CORNER_RADIUS, UIConstants.BORDER_COLOR));
        setPreferredSize(new Dimension(220, 34));
        setOpaque(false);
        // placeholder
        putClientProperty("placeholder", placeholder);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fill(new RoundRectangle2D.Double(1,1,getWidth()-2,getHeight()-2, UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS));
        g2.dispose();
        super.paintComponent(g);
        if (getText().isEmpty() && !isFocusOwner()) {
            Graphics2D g3 = (Graphics2D) g.create();
            g3.setColor(UIConstants.TEXT_SECONDARY);
            g3.setFont(getFont());
            FontMetrics fm = g3.getFontMetrics();
            String ph = (String) getClientProperty("placeholder");
            if (ph != null) g3.drawString(ph, 10, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g3.dispose();
        }
    }

    public static class RoundedBorder extends AbstractBorder {
        private int radius;
        private Color color;
        public RoundedBorder(int radius, Color color) {
            this.radius = radius; this.color = color;
        }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.draw(new RoundRectangle2D.Double(x+0.5, y+0.5, w-1, h-1, radius, radius));
            g2.dispose();
        }
        @Override
        public Insets getBorderInsets(Component c) { return new Insets(4,10,4,10); }
    }
}
