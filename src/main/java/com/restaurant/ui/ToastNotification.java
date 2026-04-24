package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * ToastNotification — Phase 7B
 *
 * <p>Hiển thị thông báo nhỏ ở góc dưới phải màn hình, tự ẩn sau 3 giây.
 * Dùng {@link JWindow} (undecorated) để tránh taskbar entry.
 *
 * <h3>Cách dùng</h3>
 * <pre>
 *   ToastNotification.show(this, "Thanh toán thành công!", ToastNotification.Type.SUCCESS);
 *   ToastNotification.show(this, "Lỗi kết nối DB.",        ToastNotification.Type.ERROR);
 *   ToastNotification.show(this, "Đang tải dữ liệu…",      ToastNotification.Type.INFO);
 * </pre>
 */
public final class ToastNotification {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final int WIDTH        = 320;
    private static final int HEIGHT       = 52;
    private static final int CORNER_R     = 12;
    private static final int AUTO_HIDE_MS = 3000;
    private static final int MARGIN       = 24;   // distance from screen edge

    // ── Type ──────────────────────────────────────────────────────────────────

    public enum Type {
        SUCCESS(new Color(0xE8F5E9), new Color(0x2E7D32)),   // green
        ERROR  (new Color(0xFFEBEE), new Color(0xC62828)),   // red
        INFO   (new Color(0xE3F2FD), new Color(0x1565C0));   // blue

        final Color bg;
        final Color fg;
        Type(Color bg, Color fg) { this.bg = bg; this.fg = fg; }
    }

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Tạo và hiển thị toast.
     *
     * @param parent  Component làm mốc để tìm Window chứa (dùng để locate màn hình)
     * @param message Nội dung hiển thị
     * @param type    Loại toast ({@link Type#SUCCESS}, {@link Type#ERROR}, {@link Type#INFO})
     */
    public static void show(java.awt.Component parent, String message, Type type) {
        SwingUtilities.invokeLater(() -> {
            new ToastNotification(parent, message, type).display();
        });
    }

    // ── Instance ──────────────────────────────────────────────────────────────

    private final JWindow window;

    private ToastNotification(java.awt.Component parent, String message, Type type) {
        // Locate the owning window so the toast stays on the correct screen
        java.awt.Window owner = (parent != null)
                ? SwingUtilities.getWindowAncestor(parent)
                : null;

        window = (owner != null) ? new JWindow(owner) : new JWindow();
        window.setSize(WIDTH, HEIGHT);

        // ── Content panel ──
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(type.bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_R * 2, CORNER_R * 2);
                // subtle border
                g2.setColor(type.fg.darker());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        CORNER_R * 2, CORNER_R * 2);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));

        // Icon prefix by type
        String icon = switch (type) {
            case SUCCESS -> "✅  ";
            case ERROR   -> "❌  ";
            case INFO    -> "ℹ️  ";
        };

        JLabel lbl = new JLabel(icon + message, SwingConstants.LEFT);
        lbl.setFont(new Font(UIConstants.FONT_BODY.getName(),
                Font.PLAIN, UIConstants.FONT_BODY.getSize()));
        lbl.setForeground(type.fg);
        panel.add(lbl, BorderLayout.CENTER);

        window.setContentPane(panel);
        window.setBackground(new Color(0, 0, 0, 0)); // transparent frame
    }

    private void display() {
        // Position: bottom-right of the screen
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int x = screen.width  - WIDTH  - MARGIN;
        int y = screen.height - HEIGHT - MARGIN - 48; // 48 = taskbar approx
        window.setLocation(x, y);
        window.setVisible(true);
        window.toFront();

        // Auto-hide after AUTO_HIDE_MS
        Timer timer = new Timer(AUTO_HIDE_MS, e -> {
            window.setVisible(false);
            window.dispose();
        });
        timer.setRepeats(false);
        timer.start();

        // Also dismiss on click
        window.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                timer.stop();
                window.setVisible(false);
                window.dispose();
            }
        });
    }
}
