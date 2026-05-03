package com.restaurant.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Custom JButton with four visual variants, an optional leading icon,
 * and a loading (spinner) state.
 *
 * <h3>Variants</h3>
 * <pre>
 *   PRIMARY   – filled blue    (CTA actions)
 *   SECONDARY – outlined blue  (secondary actions)
 *   DANGER    – filled red     (destructive actions)
 *   GHOST     – transparent    (tertiary / toolbar actions)
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 *   AppButton btn = new AppButton("Save", AppButton.Variant.PRIMARY);
 *   btn.setLeadingIcon(myIcon);
 *   btn.setLoading(true);   // shows spinner text, disables interaction
 * </pre>
 *
 * All painting is achieved through FlatLaf UIDefaults and client-properties;
 * {@code paintComponent} is NOT overridden, keeping the component
 * WindowBuilder-compatible.
 */
public class AppButton extends JButton {

    // -------------------------------------------------------------------------
    // Variant enum
    // -------------------------------------------------------------------------

    public enum Variant {
        /** Filled primary-blue button for the most important action. */
        PRIMARY,
        /** Outlined button for secondary / cancel actions. */
        SECONDARY,
        /** Filled red button for destructive actions (delete, remove). */
        DANGER,
        /** No background or border – use in toolbars or dense UIs. */
        GHOST
    }

    // -------------------------------------------------------------------------
    // Sizing enum
    // -------------------------------------------------------------------------

    public enum Size {
        SMALL  (UIConstants.SIZE_BTN_HEIGHT_SM, UIConstants.SPACING_SM,  UIConstants.FONT_SMALL),
        MEDIUM (UIConstants.SIZE_BTN_HEIGHT,    UIConstants.SPACING_LG,  UIConstants.FONT_BUTTON),
        LARGE  (UIConstants.SIZE_BTN_HEIGHT_LG, UIConstants.SPACING_XL,  UIConstants.FONT_BUTTON);

        final int height;
        final int hPad;
        final Font font;

        Size(int height, int hPad, Font font) {
            this.height = height;
            this.hPad   = hPad;
            this.font   = font;
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private Variant variant;
    private Size    size;
    private boolean loading;
    private String  originalText;
    private Icon    leadingIcon;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a PRIMARY / MEDIUM button with the given text label.
     */
    public AppButton(String text) {
        this(text, Variant.PRIMARY, Size.MEDIUM);
    }

    /**
     * Creates a MEDIUM button with the specified variant.
     */
    public AppButton(String text, Variant variant) {
        this(text, variant, Size.MEDIUM);
    }

    /**
     * Full constructor.
     */
    public AppButton(String text, Variant variant, Size size) {
        super(text);
        this.variant  = variant;
        this.size     = size;
        this.originalText = text;
        init();
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    private void init() {
        setFont(size.font);
        setFocusPainted(false);
        setBorderPainted(true);
        setOpaque(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // FlatLaf client-property for rounded corners
        putClientProperty("JButton.buttonType",  "roundRect");
        putClientProperty("JComponent.outline",   null);

        applyVariantColors();
        applySizing();
    }

    // -------------------------------------------------------------------------
    // Variant colors applied via UIManager / client properties
    // -------------------------------------------------------------------------

    private void applyVariantColors() {
        switch (variant) {

            case PRIMARY:
                setBackground(UIConstants.COLOR_PRIMARY);
                setForeground(UIConstants.COLOR_TEXT_INVERSE);
                setBorder(BorderFactory.createEmptyBorder(
                    0, size.hPad, 0, size.hPad));
                // FlatLaf hover/press colors
                putClientProperty("JButton.background",       UIConstants.COLOR_PRIMARY);
                putClientProperty("JButton.hoverBackground",  UIConstants.COLOR_PRIMARY_DARK);
                putClientProperty("JButton.pressedBackground",UIConstants.COLOR_PRIMARY_DARK);
                break;

            case SECONDARY:
                setBackground(UIConstants.COLOR_SURFACE_CARD);
                setForeground(UIConstants.COLOR_PRIMARY);
                setBorder(BorderFactory.createLineBorder(UIConstants.COLOR_PRIMARY, 1, true));
                putClientProperty("JButton.background",       UIConstants.COLOR_SURFACE_CARD);
                putClientProperty("JButton.hoverBackground",  UIConstants.COLOR_PRIMARY_LIGHT);
                putClientProperty("JButton.pressedBackground",UIConstants.COLOR_PRIMARY_MUTED);
                break;

            case DANGER:
                setBackground(UIConstants.COLOR_DANGER);
                setForeground(UIConstants.COLOR_TEXT_INVERSE);
                setBorder(BorderFactory.createEmptyBorder(
                    0, size.hPad, 0, size.hPad));
                putClientProperty("JButton.background",       UIConstants.COLOR_DANGER);
                putClientProperty("JButton.hoverBackground",  UIConstants.COLOR_DANGER_DARK);
                putClientProperty("JButton.pressedBackground",UIConstants.COLOR_DANGER_DARK);
                break;

            case GHOST:
                setBackground(new Color(0, 0, 0, 0));
                setForeground(UIConstants.COLOR_TEXT_SECONDARY);
                setBorder(BorderFactory.createEmptyBorder(
                    0, size.hPad, 0, size.hPad));
                setOpaque(false);
                putClientProperty("JButton.background",       UIConstants.COLOR_SURFACE);
                putClientProperty("JButton.hoverBackground",  UIConstants.COLOR_NEUTRAL_BG);
                putClientProperty("JButton.pressedBackground",UIConstants.COLOR_BORDER_SUBTLE);
                // No visible border for ghost
                setBorder(BorderFactory.createEmptyBorder(
                    0, UIConstants.SPACING_SM, 0, UIConstants.SPACING_SM));
                break;
        }
    }

    private void applySizing() {
        Dimension pref = getPreferredSize();
        int h = size.height;
        setPreferredSize(new Dimension(Math.max(pref.width, 80), h));
        setMinimumSize(new Dimension(40, h));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Switches the button into a "loading" state:
     * <ul>
     *   <li>Text changes to "Loading..." (or the supplied loadingText)</li>
     *   <li>Button is disabled so it cannot be clicked again</li>
     *   <li>Cursor switches to default</li>
     * </ul>
     * Call {@code setLoading(false)} to restore normal state.
     */
    public void setLoading(boolean loading) {
        this.loading = loading;
        if (loading) {
            if (originalText == null) {
                originalText = getText();
            }
            setText("Loading...");
            setEnabled(false);
            setIcon(null);
            setCursor(Cursor.getDefaultCursor());
        } else {
            setText(originalText);
            setEnabled(true);
            setIcon(leadingIcon);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        repaint();
    }

    /** Returns whether this button is in the loading state. */
    public boolean isLoading() {
        return loading;
    }

    /**
     * Sets the icon displayed before the text label.
     * Pass {@code null} to remove the icon.
     */
    public void setLeadingIcon(Icon icon) {
        this.leadingIcon = icon;
        if (!loading) {
            setIcon(icon);
            if (icon != null) {
                // Gap between icon and text label
                setIconTextGap(UIConstants.SPACING_XS + 2);
                // Horizontal alignment so icon+text block is visually centred
                setHorizontalAlignment(SwingConstants.CENTER);
            }
        }
        revalidate();
        repaint();
    }

    /** Returns the leading icon (may be {@code null}). */
    public Icon getLeadingIcon() {
        return leadingIcon;
    }

    /**
     * Changes the variant at runtime and repaints the button.
     * Useful for toggling a button between SECONDARY and DANGER states.
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
     * Changes the sizing bucket and repaints the button.
     */
    public void setSize(Size size) {
        this.size = size;
        setFont(size.font);
        applySizing();
        revalidate();
        repaint();
    }

    /**
     * Overrides {@code setText} to also update the remembered originalText
     * when the button is not in loading state.
     */
    @Override
    public void setText(String text) {
        if (!loading) {
            originalText = text;
        }
        super.setText(text);
    }

    // -------------------------------------------------------------------------
    // Disabled-state visuals
    // -------------------------------------------------------------------------

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // Re-apply full-opacity foreground
            switch (variant) {
                case PRIMARY: case DANGER:
                    setForeground(UIConstants.COLOR_TEXT_INVERSE);  break;
                case SECONDARY:
                    setForeground(UIConstants.COLOR_PRIMARY);       break;
                case GHOST:
                    setForeground(UIConstants.COLOR_TEXT_SECONDARY);break;
            }
        } else {
            setCursor(Cursor.getDefaultCursor());
            setForeground(UIConstants.COLOR_TEXT_TERTIARY);
        }
    }

    // -------------------------------------------------------------------------
    // Factory helpers
    // -------------------------------------------------------------------------

    /** Creates a small PRIMARY button. */
    public static AppButton primary(String text) {
        return new AppButton(text, Variant.PRIMARY);
    }

    /** Creates a SECONDARY (outlined) button. */
    public static AppButton secondary(String text) {
        return new AppButton(text, Variant.SECONDARY);
    }

    /** Creates a DANGER button. */
    public static AppButton danger(String text) {
        return new AppButton(text, Variant.DANGER);
    }

    /** Creates a GHOST button (no background). */
    public static AppButton ghost(String text) {
        return new AppButton(text, Variant.GHOST);
    }

    /** Creates a small-sized GHOST button (ideal for toolbar icon+label buttons). */
    public static AppButton ghostSmall(String text) {
        return new AppButton(text, Variant.GHOST, Size.SMALL);
    }
}