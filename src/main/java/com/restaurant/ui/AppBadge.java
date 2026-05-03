package com.restaurant.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A compact, pill-shaped status label.
 *
 * <h3>Variants</h3>
 * <pre>
 *   SUCCESS  – green  (active, paid, available)
 *   WARNING  – amber  (pending, low-stock)
 *   DANGER   – red    (cancelled, overdue, unavailable)
 *   INFO     – blue   (new, in-progress)
 *   NEUTRAL  – grey   (draft, inactive, unknown)
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 *   AppBadge badge = new AppBadge("Active", AppBadge.Variant.SUCCESS);
 *   panel.add(badge);
 *
 *   // Updating at runtime:
 *   badge.setVariant(AppBadge.Variant.DANGER);
 *   badge.setText("Cancelled");
 * </pre>
 *
 * Rendering uses {@code paintComponent} only for the pill background, because
 * there is no FlatLaf UIDefaults key that produces an arbitrary filled pill on
 * a JLabel without HTML hacks.  All other styling (font, alignment, insets)
 * is handled by standard Swing APIs, keeping it WindowBuilder-friendly.
 */
public class AppBadge extends JLabel {

    // -------------------------------------------------------------------------
    // Variant
    // -------------------------------------------------------------------------

    public enum Variant {
        SUCCESS (UIConstants.COLOR_SUCCESS_BG,  UIConstants.COLOR_SUCCESS_TEXT),
        WARNING (UIConstants.COLOR_WARNING_BG,  UIConstants.COLOR_WARNING_TEXT),
        DANGER  (UIConstants.COLOR_DANGER_BG,   UIConstants.COLOR_DANGER_TEXT),
        INFO    (UIConstants.BADGE_BLUE_BG,     UIConstants.BADGE_BLUE_FG),
        NEUTRAL (UIConstants.COLOR_NEUTRAL_BG,  UIConstants.COLOR_NEUTRAL_TEXT);

        final Color bg;
        final Color fg;

        Variant(Color bg, Color fg) {
            this.bg = bg;
            this.fg = fg;
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private Variant variant;

    /** Horizontal padding inside the pill (left and right). */
    private int hPad = UIConstants.SPACING_SM;
    /** Vertical padding inside the pill (top and bottom). */
    private int vPad = 2;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a NEUTRAL badge with the given label.
     */
    public AppBadge(String text) {
        this(text, Variant.NEUTRAL);
    }

    /**
     * Creates a badge with the specified text and variant.
     */
    public AppBadge(String text, Variant variant) {
        super(text);
        this.variant = variant;
        init();
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    private void init() {
        setFont(UIConstants.FONT_BADGE);
        setHorizontalAlignment(SwingConstants.CENTER);
        setOpaque(false);   // background is painted in paintComponent

        applyVariantColors();
        updateInsets();
    }

    private void applyVariantColors() {
        setForeground(variant.fg);
        // Background is drawn in paintComponent; store it for convenience.
        setBackground(variant.bg);
    }

    private void updateInsets() {
        // Reserve space so the pill never clips the text
        setBorder(BorderFactory.createEmptyBorder(vPad, hPad, vPad, hPad));
    }

    // -------------------------------------------------------------------------
    // Pill painting
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int w = getWidth();
            int h = getHeight();
            // Pill radius = half the height for a true "stadium" shape
            float arc = h;

            g2.setColor(variant.bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
        } finally {
            g2.dispose();
        }
        // Let Swing paint the text (and icon if any) on top
        super.paintComponent(g);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Updates the variant (colour scheme) and repaints.
     */
    public void setVariant(Variant variant) {
        this.variant = variant;
        applyVariantColors();
        repaint();
    }

    /** Returns the current variant. */
    public Variant getVariant() {
        return variant;
    }

    /**
     * Adjusts the horizontal padding inside the pill.
     * Default is {@link UIConstants#SPACING_SM} (8 px).
     */
    public void setHorizontalPadding(int hPad) {
        this.hPad = hPad;
        updateInsets();
        revalidate();
        repaint();
    }

    /**
     * Adjusts the vertical padding inside the pill.
     * Default is 2 px.
     */
    public void setVerticalPadding(int vPad) {
        this.vPad = vPad;
        updateInsets();
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Preferred size
    // -------------------------------------------------------------------------

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        // Minimum width = height so very short labels still look like pills
        d.width  = Math.max(d.width,  d.height);
        return d;
    }

    // -------------------------------------------------------------------------
    // Factory helpers – mirrors common restaurant statuses
    // -------------------------------------------------------------------------

    /** "Active" / "Paid" / "Available" – green. */
    public static AppBadge success(String text) {
        return new AppBadge(text, Variant.SUCCESS);
    }

    /** "Pending" / "Low Stock" – amber. */
    public static AppBadge warning(String text) {
        return new AppBadge(text, Variant.WARNING);
    }

    /** "Cancelled" / "Unavailable" – red. */
    public static AppBadge danger(String text) {
        return new AppBadge(text, Variant.DANGER);
    }

    /** "New" / "In Progress" – blue. */
    public static AppBadge info(String text) {
        return new AppBadge(text, Variant.INFO);
    }

    /** "Draft" / "Inactive" – grey. */
    public static AppBadge neutral(String text) {
        return new AppBadge(text, Variant.NEUTRAL);
    }

    /**
     * Convenience: maps an arbitrary status string to a badge variant using
     * common restaurant domain vocabulary.
     *
     * @param status raw status string (case-insensitive)
     * @return a pre-configured AppBadge
     */
    public static AppBadge forStatus(String status) {
        if (status == null) return neutral("Unknown");
        switch (status.trim().toLowerCase()) {
            case "active":
            case "paid":
            case "available":
            case "completed":
            case "served":
                return success(status);

            case "pending":
            case "low stock":
            case "low_stock":
            case "preparing":
            case "in progress":
            case "in_progress":
                return warning(status);

            case "cancelled":
            case "canceled":
            case "out of stock":
            case "out_of_stock":
            case "unpaid":
            case "overdue":
                return danger(status);

            case "new":
            case "ordered":
            case "reserved":
                return info(status);

            default:
                return neutral(status);
        }
    }
}