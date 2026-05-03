package com.restaurant.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * SimpleSpinner — spinner tròn xoay dùng để hiển thị trạng thái đang tải.
 *
 * <p>Cách dùng:
 * <pre>
 *   SimpleSpinner spinner = new SimpleSpinner(24, UIConstants.PRIMARY);
 *   spinner.setVisible(false);
 *   headerPanel.add(spinner);
 *
 *   // Trước khi tải:
 *   spinner.setVisible(true);
 *   spinner.start();
 *
 *   // Sau khi tải xong:
 *   spinner.stop();
 *   spinner.setVisible(false);
 * </pre>
 */
public class SimpleSpinner extends JPanel {

    private int         angle;
    private final Timer animTimer;
    private final int   size;
    private final Color arcColor;

    /**
     * @param size     đường kính tính bằng pixel
     * @param color    màu cung xoay
     */
    public SimpleSpinner(int size, Color color) {
        this.size     = size;
        this.arcColor = color;
        this.angle    = 0;

        setOpaque(false);
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));

        animTimer = new Timer(40, e -> {
            angle = (angle + 15) % 360;
            repaint();
        });
    }

    /** Bắt đầu animation. */
    public void start() {
        animTimer.start();
    }

    /** Dừng animation. */
    public void stop() {
        animTimer.stop();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Track mờ ở phía sau
        g2.setColor(new Color(arcColor.getRed(), arcColor.getGreen(),
                arcColor.getBlue(), 40));
        g2.drawOval(2, 2, size - 4, size - 4);

        // Cung xoay chính
        g2.setColor(arcColor);
        g2.drawArc(2, 2, size - 4, size - 4, angle, 270);

        g2.dispose();
    }
}