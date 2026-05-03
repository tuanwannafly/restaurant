package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * InlineErrorBar — thanh lỗi inline hiển thị ở {@code BorderLayout.SOUTH} của panel cha.
 *
 * <p>Tự ẩn sau {@value #AUTO_HIDE_MS} ms. An toàn để gọi nhiều lần liên tiếp —
 * banner cũ sẽ bị thay bằng banner mới.
 *
 * <p>Cách dùng:
 * <pre>
 *   InlineErrorBar.show(this, "Lỗi tải dữ liệu: " + ex.getMessage());
 * </pre>
 *
 * <p><b>Lưu ý:</b> {@code parent} phải dùng {@link BorderLayout} và
 * {@code BorderLayout.SOUTH} chưa bị chiếm bởi component khác, hoặc
 * component khác phải chấp nhận bị thay thế tạm thời.
 */
public final class InlineErrorBar {

    private static final int AUTO_HIDE_MS = 5_000;

    private InlineErrorBar() {}

    /**
     * Hiện banner lỗi inline.
     *
     * @param parent  panel cha (phải dùng {@link BorderLayout})
     * @param message nội dung lỗi cần hiển thị
     */
    public static void show(JPanel parent, String message) {
        if (parent == null || message == null) return;

        SwingUtilities.invokeLater(() -> {
            // Xóa banner lỗi cũ nếu còn tồn tại
            for (Component c : parent.getComponents()) {
                if (c instanceof JComponent
                        && Boolean.TRUE.equals(((JComponent) c)
                                .getClientProperty("inlineError"))) {
                    parent.remove(c);
                }
            }

            JLabel bar = new JLabel("⚠  " + message);
            bar.putClientProperty("inlineError", Boolean.TRUE);
            bar.setFont(UIConstants.FONT_SMALL);
            bar.setForeground(UIConstants.DANGER);
            bar.setOpaque(true);
            bar.setBackground(new Color(0xFEE2E2));
            bar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xFCA5A5)),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)));

            parent.add(bar, BorderLayout.SOUTH);
            parent.revalidate();
            parent.repaint();

            Timer hideTimer = new Timer(AUTO_HIDE_MS, e -> {
                parent.remove(bar);
                parent.revalidate();
                parent.repaint();
            });
            hideTimer.setRepeats(false);
            hideTimer.start();
        });
    }
}