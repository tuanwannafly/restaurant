package com.restaurant.ui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * AppTextField — Phase 4
 *
 * <p>Styled text field:
 * <ul>
 *   <li>Normal  : 1px #CBD5E1 border, rounded 6px</li>
 *   <li>Focus   : 2px #3B82F6 border + soft blue shadow ring</li>
 *   <li>Error   : 2px #EF4444 border + soft red shadow ring</li>
 *   <li>Disabled: #F3F4F6 background, greyed text</li>
 * </ul>
 *
 * <p>Inline error message is managed externally via {@link #setError(String)}.
 * Pass {@code null} or empty string to clear the error state.
 */
public class AppTextField extends JTextField {

    // ── Colour constants ─────────────────────────────────────────────────────
    private static final Color BORDER_NORMAL   = new Color(0xCBD5E1);
    private static final Color BORDER_FOCUS    = new Color(0x3B82F6);
    private static final Color BORDER_ERROR    = new Color(0xEF4444);
    private static final Color SHADOW_FOCUS    = new Color(0x3B82F6, true); // alpha handled in paint
    private static final Color BG_NORMAL       = Color.WHITE;
    private static final Color BG_DISABLED     = new Color(0xF3F4F6);
    private static final Color FG_PLACEHOLDER  = new Color(0x9CA3AF);

    private static final int ARC = 6;

    // ── State ────────────────────────────────────────────────────────────────
    private boolean errorState   = false;
    private boolean focusState   = false;
    private String  placeholder  = "";

    /** Sibling error label — created once, managed by AppDialog. */
    private JLabel errorLabel;

    // ── Constructor ──────────────────────────────────────────────────────────
    public AppTextField() {
        this("");
    }

    public AppTextField(String placeholder) {
        super();
        this.placeholder = placeholder;
        setFont(UIConstants.FONT_BODY);
        setForeground(UIConstants.TEXT_PRIMARY);
        setBackground(BG_NORMAL);
        setOpaque(false);   // we paint our own background
        applyBorder(BORDER_NORMAL, 1);

        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { focusState = true;  repaint(); }
            @Override public void focusLost (FocusEvent e)  { focusState = false; repaint(); }
        });
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Attach an error label (created by AppDialog row builder). */
    public void attachErrorLabel(JLabel lbl) {
        this.errorLabel = lbl;
    }

    /**
     * Show / clear an inline error.
     * @param message non-null/non-blank → show error; null/blank → clear
     */
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

    public void setPlaceholder(String text) {
        this.placeholder = text;
        repaint();
    }

    // ── Painting ─────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        // Background
        g2.setColor(isEnabled() ? BG_NORMAL : BG_DISABLED);
        g2.fillRoundRect(0, 0, w - 1, h - 1, ARC, ARC);

        // Shadow ring (focus / error)
        if (focusState || errorState) {
            Color ring = errorState ? new Color(0xEF4444) : new Color(0x3B82F6);
            g2.setColor(new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 40));
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, ARC + 2, ARC + 2);
        }

        g2.dispose();
        super.paintComponent(g);

        // Placeholder
        if (getText().isEmpty() && !placeholder.isEmpty() && !isFocusOwner()) {
            Graphics2D pg = (Graphics2D) g.create();
            pg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            pg.setColor(FG_PLACEHOLDER);
            pg.setFont(getFont());
            Insets ins = getInsets();
            FontMetrics fm = pg.getFontMetrics();
            pg.drawString(placeholder, ins.left, (h - fm.getHeight()) / 2 + fm.getAscent());
            pg.dispose();
        }
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color borderColor = errorState ? BORDER_ERROR
                          : focusState ? BORDER_FOCUS
                          :              BORDER_NORMAL;
        float stroke = (errorState || focusState) ? 2f : 1f;

        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(stroke));
        g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, ARC, ARC);
        g2.dispose();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private void applyBorder(Color c, int thickness) {
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, 0, 0, 0), // outer — painted manually
            BorderFactory.createEmptyBorder(5, 10, 5, 10) // inner padding
        ));
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setBackground(enabled ? BG_NORMAL : BG_DISABLED);
        repaint();
    }
}