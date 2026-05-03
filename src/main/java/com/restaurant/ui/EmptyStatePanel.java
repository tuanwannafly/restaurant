package com.restaurant.ui;

import java.awt.Font;
import java.awt.GridBagLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Shared empty-state panel — căn giữa theo GridBagLayout.
 *
 * <p>Thay thế các inner-class {@code EmptyStatePanel} riêng lẻ trong
 * {@link CashierPanel}, {@link KitchenPanel}, và {@link WaiterServicePanel}.
 *
 * <pre>
 *   ┌──────────────────────────┐
 *   │                          │
 *   │          🍳              │  ← icon  (48 px emoji)
 *   │  Không có món nào chờ   │  ← title (FONT_TITLE / TEXT_SECONDARY)
 *   │  Tất cả đã được chế biến│  ← hint  (FONT_BODY  / TEXT_SECONDARY, nullable)
 *   │                          │
 *   └──────────────────────────┘
 * </pre>
 */
public class EmptyStatePanel extends JPanel {

    /**
     * @param icon  Emoji icon lớn (vd: {@code "🍳"}, {@code "🛎"}, {@code "💳"}, {@code "🧹"})
     * @param title Dòng chính   (vd: {@code "Không có món nào đang chờ"})
     * @param hint  Dòng gợi ý nhỏ hơn — truyền {@code null} nếu không cần
     */
    public EmptyStatePanel(String icon, String title, String hint) {
        setLayout(new GridBagLayout());
        setOpaque(false);

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        JLabel iconLbl = new JLabel(icon, SwingConstants.CENTER);
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        iconLbl.setAlignmentX(CENTER_ALIGNMENT);

        JLabel titleLbl = new JLabel(title, SwingConstants.CENTER);
        titleLbl.setFont(UIConstants.FONT_TITLE);
        titleLbl.setForeground(UIConstants.TEXT_SECONDARY);
        titleLbl.setAlignmentX(CENTER_ALIGNMENT);

        inner.add(iconLbl);
        inner.add(Box.createVerticalStrut(12));
        inner.add(titleLbl);

        if (hint != null && !hint.isBlank()) {
            JLabel hintLbl = new JLabel(hint, SwingConstants.CENTER);
            hintLbl.setFont(UIConstants.FONT_BODY);
            hintLbl.setForeground(UIConstants.TEXT_SECONDARY);
            hintLbl.setAlignmentX(CENTER_ALIGNMENT);
            inner.add(Box.createVerticalStrut(6));
            inner.add(hintLbl);
        }

        add(inner);
    }
}