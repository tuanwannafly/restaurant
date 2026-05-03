package com.restaurant.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * AppComboBox — Phase 4
 *
 * <p>Styled JComboBox to match AppTextField visually:
 * <ul>
 *   <li>Normal  : 1px #CBD5E1 rounded border</li>
 *   <li>Focus   : 2px #3B82F6 border + ring</li>
 *   <li>Error   : 2px #EF4444 border + ring</li>
 *   <li>Custom arrow button (no default L&amp;F chrome)</li>
 * </ul>
 */
public class AppComboBox<T> extends JComboBox<T> {

    private static final Color BORDER_NORMAL = new Color(0xCBD5E1);
    private static final Color BORDER_FOCUS  = new Color(0x3B82F6);
    private static final Color BORDER_ERROR  = new Color(0xEF4444);
    private static final int   ARC           = 6;

    private boolean errorState = false;
    private boolean focusState = false;
    private JLabel  errorLabel;

    // ── Constructors ─────────────────────────────────────────────────────────
    public AppComboBox() {
        super();
        init();
    }

    public AppComboBox(T[] items) {
        super(items);
        init();
    }

    // ── Init ─────────────────────────────────────────────────────────────────
    private void init() {
        setFont(UIConstants.FONT_BODY);
        setForeground(UIConstants.TEXT_PRIMARY);
        setBackground(Color.WHITE);
        setOpaque(false);
        setFocusable(true);

        // Custom UI to suppress default chrome and draw our own border/arrow
        setUI(new CleanComboUI());
        setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 6));

        // Focus tracking for border colour
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { focusState = true;  repaint(); }
            @Override public void focusLost (FocusEvent e)  { focusState = false; repaint(); }
        });
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void attachErrorLabel(JLabel lbl) { this.errorLabel = lbl; }

    public void setError(String message) {
        boolean hasError = (message != null && !message.isBlank());
        errorState = hasError;
        if (errorLabel != null) {
            errorLabel.setText(hasError ? message : "");
            errorLabel.setVisible(hasError);
        }
        repaint();
    }

    public boolean hasError() { return errorState; }

    // ── Painting ─────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        // Background
        g2.setColor(isEnabled() ? Color.WHITE : new Color(0xF3F4F6));
        g2.fillRoundRect(0, 0, w - 1, h - 1, ARC, ARC);

        // Ring
        if (focusState || errorState) {
            Color ring = errorState ? new Color(0xEF4444) : new Color(0x3B82F6);
            g2.setColor(new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 40));
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, ARC + 2, ARC + 2);
        }

        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color c = errorState ? BORDER_ERROR : focusState ? BORDER_FOCUS : BORDER_NORMAL;
        float stroke = (errorState || focusState) ? 2f : 1f;

        g2.setColor(c);
        g2.setStroke(new BasicStroke(stroke));
        g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, ARC, ARC);
        g2.dispose();
    }

    // ── Custom UI (clean — no extra chrome) ──────────────────────────────────

    private static class CleanComboUI extends BasicComboBoxUI {

        @Override
        protected JButton createArrowButton() {
            JButton btn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0x64748B));
                    // Draw a simple chevron ▾
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    int[] xs = { cx - 4, cx, cx + 4 };
                    int[] ys = { cy - 2, cy + 2, cy - 2 };
                    g2.fillPolygon(xs, ys, 3);
                    g2.dispose();
                }
            };
            btn.setOpaque(false);
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setPreferredSize(new Dimension(28, 0));
            btn.setFocusable(false);
            return btn;
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox) {
                @Override
                protected void configurePopup() {
                    super.configurePopup();
                    setBorderPainted(true);
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0xCBD5E1), 1),
                        BorderFactory.createEmptyBorder(4, 0, 4, 0)));
                }
            };
            return popup;
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            // Remove default background painting
            comboBox.setOpaque(false);
        }
    }
}