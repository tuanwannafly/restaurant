package com.restaurant.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Central design-token registry for the Restaurant Management UI.
 *
 * Naming convention:
 *   COLOR_*   – raw Color objects
 *   FONT_*    – Font objects
 *   SPACING_* – int pixel values for padding / gap
 *   SIZE_*    – int pixel values for component dimensions
 *   RADIUS_*  – int pixel values for arc / corner radius
 *   SHADOW_*  – Color objects used as drop-shadow tints
 *
 * Compatible with WindowBuilder (all fields are public static final).
 */
public final class UIConstants {

    private UIConstants() {}

    // -------------------------------------------------------------------------
    // BRAND / PRIMARY
    // -------------------------------------------------------------------------

    /** Main interactive blue – buttons, links, focus rings. */
    public static final Color COLOR_PRIMARY        = new Color(0x2563EB);
    /** Darker shade – hover / pressed state for primary elements. */
    public static final Color COLOR_PRIMARY_DARK   = new Color(0x1D4ED8);
    /** Lighter shade – active nav highlight, selected rows. */
    public static final Color COLOR_PRIMARY_LIGHT  = new Color(0xEFF6FF);
    /** Subtle blue tint used for selected table rows. */
    public static final Color COLOR_PRIMARY_MUTED  = new Color(0xDBEAFE);

    // -------------------------------------------------------------------------
    // SURFACE / BACKGROUND
    // -------------------------------------------------------------------------

    /** Page-level background – very light grey-blue. */
    public static final Color COLOR_SURFACE        = new Color(0xF8FAFC);
    /** Card / panel background. */
    public static final Color COLOR_SURFACE_CARD   = Color.WHITE;
    /** Alternate table row background. */
    public static final Color COLOR_SURFACE_ALT    = new Color(0xF1F5F9);
    /** Section header background. */
    public static final Color COLOR_SURFACE_HEADER = new Color(0xE2E8F0);
    /** Popover / tooltip / dropdown background. */
    public static final Color COLOR_SURFACE_RAISED = Color.WHITE;

    // -------------------------------------------------------------------------
    // BORDER
    // -------------------------------------------------------------------------

    /** Default input and card border. */
    public static final Color COLOR_BORDER         = new Color(0xCBD5E1);
    /** Softer divider line between sections. */
    public static final Color COLOR_BORDER_SUBTLE  = new Color(0xE2E8F0);
    /** Focused input ring. */
    public static final Color COLOR_BORDER_FOCUS   = new Color(0x2563EB);

    // -------------------------------------------------------------------------
    // TEXT
    // -------------------------------------------------------------------------

    /** Headings and primary body text. */
    public static final Color COLOR_TEXT_PRIMARY   = new Color(0x0F172A);
    /** Supporting / secondary text. */
    public static final Color COLOR_TEXT_SECONDARY = new Color(0x64748B);
    /** Placeholder / disabled text. */
    public static final Color COLOR_TEXT_TERTIARY  = new Color(0x94A3B8);
    /** Text on colored (dark) backgrounds. */
    public static final Color COLOR_TEXT_INVERSE   = Color.WHITE;

    // -------------------------------------------------------------------------
    // SEMANTIC – SUCCESS
    // -------------------------------------------------------------------------

    public static final Color COLOR_SUCCESS        = new Color(0x10B981);
    public static final Color COLOR_SUCCESS_DARK   = new Color(0x059669);
    public static final Color COLOR_SUCCESS_BG     = new Color(0xD1FAE5);
    public static final Color COLOR_SUCCESS_TEXT   = new Color(0x065F46);

    // -------------------------------------------------------------------------
    // SEMANTIC – WARNING
    // -------------------------------------------------------------------------

    public static final Color COLOR_WARNING        = new Color(0xF59E0B);
    public static final Color COLOR_WARNING_DARK   = new Color(0xD97706);
    public static final Color COLOR_WARNING_BG     = new Color(0xFEF3C7);
    public static final Color COLOR_WARNING_TEXT   = new Color(0x92400E);

    // -------------------------------------------------------------------------
    // SEMANTIC – DANGER
    // -------------------------------------------------------------------------

    public static final Color COLOR_DANGER         = new Color(0xEF4444);
    public static final Color COLOR_DANGER_DARK    = new Color(0xDC2626);
    public static final Color COLOR_DANGER_BG      = new Color(0xFEE2E2);
    public static final Color COLOR_DANGER_TEXT    = new Color(0x991B1B);

    // -------------------------------------------------------------------------
    // SEMANTIC – NEUTRAL
    // -------------------------------------------------------------------------

    public static final Color COLOR_NEUTRAL        = new Color(0x94A3B8);
    public static final Color COLOR_NEUTRAL_BG     = new Color(0xF1F5F9);
    public static final Color COLOR_NEUTRAL_TEXT   = new Color(0x475569);

    // -------------------------------------------------------------------------
    // BADGE COLORS  (kept for backward-compat with existing badge rendering)
    // -------------------------------------------------------------------------

    public static final Color BADGE_BLUE_BG    = COLOR_PRIMARY_MUTED;
    public static final Color BADGE_BLUE_FG    = new Color(0x1D4ED8);
    public static final Color BADGE_GREEN_BG   = COLOR_SUCCESS_BG;
    public static final Color BADGE_GREEN_FG   = COLOR_SUCCESS_TEXT;
    public static final Color BADGE_RED_BG     = COLOR_DANGER_BG;
    public static final Color BADGE_RED_FG     = COLOR_DANGER_TEXT;
    public static final Color BADGE_YELLOW_BG  = COLOR_WARNING_BG;
    public static final Color BADGE_YELLOW_FG  = COLOR_WARNING_TEXT;
    public static final Color BADGE_GRAY_BG    = COLOR_NEUTRAL_BG;
    public static final Color BADGE_GRAY_FG    = COLOR_NEUTRAL_TEXT;

    // -------------------------------------------------------------------------
    // CARD ACCENT BACKGROUNDS  (HomePanel summary cards)
    // -------------------------------------------------------------------------

    public static final Color CARD_BLUE_BG     = COLOR_PRIMARY_LIGHT;
    public static final Color CARD_GREEN_BG    = new Color(0xF0FDF4);
    public static final Color CARD_AMBER_BG    = new Color(0xFFFBEB);
    public static final Color CARD_RED_BG      = new Color(0xFFF1F2);

    // -------------------------------------------------------------------------
    // BACKWARD-COMPAT ALIASES  (existing code uses these names)
    // -------------------------------------------------------------------------

    public static final Color PRIMARY        = COLOR_PRIMARY;
    public static final Color PRIMARY_DARK   = COLOR_PRIMARY_DARK;
    public static final Color PRIMARY_LIGHT  = COLOR_PRIMARY_LIGHT;
    public static final Color BG_WHITE       = COLOR_SURFACE_CARD;
    public static final Color BG_PAGE        = COLOR_SURFACE;
    public static final Color HEADER_BG      = COLOR_SURFACE_HEADER;
    public static final Color ROW_SELECTED   = COLOR_PRIMARY_MUTED;
    public static final Color BORDER_COLOR   = COLOR_BORDER;
    public static final Color TEXT_PRIMARY   = COLOR_TEXT_PRIMARY;
    public static final Color TEXT_SECONDARY = COLOR_TEXT_SECONDARY;
    public static final Color TEXT_WHITE     = COLOR_TEXT_INVERSE;
    public static final Color SUCCESS        = COLOR_SUCCESS;
    public static final Color WARNING        = COLOR_WARNING;
    public static final Color DANGER         = COLOR_DANGER;
    public static final Color DANGER_LIGHT   = COLOR_DANGER_BG;
    public static final Color CARD_BG        = new Color(0xBFD7F4);

    // -------------------------------------------------------------------------
    // SHADOW COLORS  (for manual drop-shadow painting or FlatLaf border shadow)
    // -------------------------------------------------------------------------

    /** Very subtle ambient shadow (large area, low opacity). */
    public static final Color SHADOW_AMBIENT = new Color(0x0F172A, false); // use with alpha
    /** Card-level shadow tint — use alpha 18. */
    public static final Color SHADOW_CARD    = new Color(15, 23, 42, 18);
    /** Elevated panel shadow tint — use alpha 28. */
    public static final Color SHADOW_RAISED  = new Color(15, 23, 42, 28);
    /** Button press shadow tint — use alpha 12. */
    public static final Color SHADOW_PRESS   = new Color(15, 23, 42, 12);

    // -------------------------------------------------------------------------
    // FONT STACK
    //   Priority order: Inter → Segoe UI → SF Pro Text → system sans-serif
    //   resolveFont() picks the first family available on the current JVM.
    // -------------------------------------------------------------------------

    private static final String[] FONT_FAMILY_STACK = {
        "Inter", "Segoe UI", "SF Pro Text", "Helvetica Neue", "Arial"
    };

    /** The resolved sans-serif family name (first available in FONT_FAMILY_STACK). */
    public static final String FONT_FAMILY = resolveFontFamily();

    private static String resolveFontFamily() {
        java.awt.GraphicsEnvironment ge =
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        java.util.Set<String> available = new java.util.HashSet<>(
            java.util.Arrays.asList(ge.getAvailableFontFamilyNames()));
        for (String family : FONT_FAMILY_STACK) {
            if (available.contains(family)) {
                return family;
            }
        }
        return Font.SANS_SERIF;
    }

    // -- Pre-built Font instances ---------------------------------------------

    public static final Font FONT_TITLE   = new Font(FONT_FAMILY, Font.BOLD,  22);
    public static final Font FONT_HEADING = new Font(FONT_FAMILY, Font.BOLD,  16);
    public static final Font FONT_NAV     = new Font(FONT_FAMILY, Font.PLAIN, 13);
    public static final Font FONT_HEADER  = new Font(FONT_FAMILY, Font.BOLD,  13);
    public static final Font FONT_BODY    = new Font(FONT_FAMILY, Font.PLAIN, 13);
    public static final Font FONT_SMALL   = new Font(FONT_FAMILY, Font.PLAIN, 11);
    public static final Font FONT_BOLD    = new Font(FONT_FAMILY, Font.BOLD,  13);
    public static final Font FONT_LOGO    = new Font(FONT_FAMILY, Font.BOLD,  16);
    public static final Font FONT_VALUE   = new Font(FONT_FAMILY, Font.BOLD,  28);
    public static final Font FONT_BADGE   = new Font(FONT_FAMILY, Font.BOLD,  11);
    public static final Font FONT_BUTTON  = new Font(FONT_FAMILY, Font.BOLD,  13);
    public static final Font FONT_CAPTION = new Font(FONT_FAMILY, Font.PLAIN, 12);

    // -------------------------------------------------------------------------
    // SPACING  (multiples of 4 px base unit)
    // -------------------------------------------------------------------------

    /** 4 px – tight gap between icon and label. */
    public static final int SPACING_XS  = 4;
    /** 8 px – inner padding for compact controls. */
    public static final int SPACING_SM  = 8;
    /** 12 px – comfortable inline gap. */
    public static final int SPACING_MD  = 12;
    /** 16 px – standard section gap. */
    public static final int SPACING_LG  = 16;
    /** 24 px – card internal padding. */
    public static final int SPACING_XL  = 24;
    /** 32 px – panel-level vertical rhythm. */
    public static final int SPACING_2XL = 32;
    /** 48 px – page-level outer margins. */
    public static final int SPACING_3XL = 48;

    // Backward-compat aliases
    public static final int PADDING_SM  = SPACING_SM;
    public static final int PADDING_MD  = SPACING_LG;   // was 16
    public static final int PADDING_LG  = SPACING_XL;   // was 24
    public static final int PADDING_XL  = SPACING_3XL;  // was 48

    // -------------------------------------------------------------------------
    // COMPONENT SIZES
    // -------------------------------------------------------------------------

    public static final int SIZE_NAV_HEIGHT      = 44;
    public static final int SIZE_HEADER_HEIGHT   = 60;
    public static final int SIZE_ROW_HEIGHT      = 38;
    public static final int SIZE_BTN_HEIGHT      = 34;
    public static final int SIZE_BTN_HEIGHT_SM   = 28;
    public static final int SIZE_BTN_HEIGHT_LG   = 42;
    public static final int SIZE_INPUT_HEIGHT    = 34;
    public static final int SIZE_ICON_SM         = 16;
    public static final int SIZE_ICON_MD         = 20;
    public static final int SIZE_ICON_LG         = 24;

    // Backward-compat aliases
    public static final int NAV_HEIGHT    = SIZE_NAV_HEIGHT;
    public static final int HEADER_HEIGHT = SIZE_HEADER_HEIGHT;
    public static final int ROW_HEIGHT    = SIZE_ROW_HEIGHT;
    public static final int BTN_HEIGHT    = SIZE_BTN_HEIGHT;

    // -------------------------------------------------------------------------
    // BORDER RADIUS
    // -------------------------------------------------------------------------

    /** 4 px – small badges, tooltips. */
    public static final int RADIUS_SM     = 4;
    /** 6 px – inputs, small buttons. */
    public static final int RADIUS_MD     = 6;
    /** 8 px – standard cards, buttons. */
    public static final int RADIUS_LG     = 8;
    /** 12 px – large panels. */
    public static final int RADIUS_XL     = 12;
    /** 16 px – feature cards. */
    public static final int RADIUS_2XL    = 16;
    /** 999 px – pill / full-round shapes. */
    public static final int RADIUS_PILL   = 999;

    // Backward-compat aliases
    public static final int CORNER_RADIUS = RADIUS_LG;
    public static final int CARD_RADIUS   = RADIUS_2XL;
}