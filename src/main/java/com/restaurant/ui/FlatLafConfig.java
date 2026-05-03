package com.restaurant.ui;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies FlatLaf as the Swing Look-and-Feel and injects design-token overrides
 * so every component picks up the restaurant app's visual language automatically.
 *
 * Usage – call once, before any Swing component is created:
 * <pre>
 *     FlatLafConfig.install();
 * </pre>
 *
 * All colour / dimension values are sourced from {@link UIConstants} so there is
 * a single source of truth for the design system.
 *
 * Compatible with WindowBuilder (no paintComponent overrides; pure UIDefaults).
 */
public final class FlatLafConfig {

    private static final Logger LOG = Logger.getLogger(FlatLafConfig.class.getName());

    private FlatLafConfig() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Installs FlatLightLaf and injects all custom UIDefaults.
     * Must be called on the Event Dispatch Thread (or before the EDT starts).
     *
     * @return {@code true} if FlatLaf was installed successfully,
     *         {@code false} if it fell back to the system LAF.
     */
    public static boolean install() {
        try {
            // 1. Register custom defaults BEFORE setting the LAF so FlatLaf
            //    merges them during its own setup.
            FlatLaf.registerCustomDefaultsSource("com.restaurant.ui");

            // 2. Install FlatLightLaf.
            FlatLightLaf.setup();

            // 3. Inject UIDefaults overrides programmatically.
            applyDefaults();

            // 4. Repaint any already-visible components (should be none at startup).
            FlatLaf.updateUI();

            LOG.info("FlatLaf installed successfully – family: " + UIConstants.FONT_FAMILY);
            return true;

        } catch (Exception ex) {
            LOG.log(Level.WARNING, "FlatLaf installation failed, using system LAF.", ex);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { /* best-effort */ }
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Writes restaurant-specific design tokens into the Swing UIDefaults table.
     *
     * Key groups:
     *   - Global / frame
     *   - Button
     *   - TextField / ComboBox / Spinner
     *   - Table
     *   - ScrollPane / ScrollBar
     *   - TabbedPane
     *   - Panel / Label
     *   - ToolTip
     *   - Menu / MenuItem / PopupMenu
     *   - ProgressBar
     */
    private static void applyDefaults() {
        UIDefaults ui = UIManager.getLookAndFeelDefaults();

        // ------------------------------------------------------------------ //
        // GLOBAL
        // ------------------------------------------------------------------ //
        ui.put("defaultFont",                  UIConstants.FONT_BODY);

        // FlatLaf accent / highlight
        ui.put("Component.accentColor",        UIConstants.COLOR_PRIMARY);
        ui.put("Component.focusColor",         UIConstants.COLOR_BORDER_FOCUS);
        ui.put("Component.focusWidth",         2);
        ui.put("Component.innerFocusWidth",    0);

        // Rounded corners for all components that support it
        ui.put("Component.arc",                UIConstants.RADIUS_LG);
        ui.put("Button.arc",                   UIConstants.RADIUS_LG);
        ui.put("TextComponent.arc",            UIConstants.RADIUS_MD);
        ui.put("Component.arrowType",          "chevron");

        // Border
        ui.put("Component.borderColor",        UIConstants.COLOR_BORDER);
        ui.put("Component.disabledBorderColor",UIConstants.COLOR_BORDER_SUBTLE);

        // ------------------------------------------------------------------ //
        // FRAME / PANEL / CONTENT-PANE
        // ------------------------------------------------------------------ //
        ui.put("Panel.background",             UIConstants.COLOR_SURFACE);
        ui.put("RootPane.background",          UIConstants.COLOR_SURFACE);
        ui.put("ContentPane.background",       UIConstants.COLOR_SURFACE);

        // ------------------------------------------------------------------ //
        // LABEL
        // ------------------------------------------------------------------ //
        ui.put("Label.foreground",             UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("Label.font",                   UIConstants.FONT_BODY);

        // ------------------------------------------------------------------ //
        // BUTTON  (base defaults; AppButton overrides per-variant)
        // ------------------------------------------------------------------ //
        ui.put("Button.font",                  UIConstants.FONT_BUTTON);
        ui.put("Button.background",            UIConstants.COLOR_PRIMARY);
        ui.put("Button.foreground",            UIConstants.COLOR_TEXT_INVERSE);
        ui.put("Button.hoverBackground",       UIConstants.COLOR_PRIMARY_DARK);
        ui.put("Button.pressedBackground",     UIConstants.COLOR_PRIMARY_DARK);
        ui.put("Button.focusedBackground",     UIConstants.COLOR_PRIMARY);
        ui.put("Button.borderColor",           UIConstants.COLOR_PRIMARY);
        ui.put("Button.hoverBorderColor",      UIConstants.COLOR_PRIMARY_DARK);
        ui.put("Button.focusedBorderColor",    UIConstants.COLOR_BORDER_FOCUS);
        ui.put("Button.disabledBackground",    UIConstants.COLOR_NEUTRAL_BG);
        ui.put("Button.disabledForeground",    UIConstants.COLOR_TEXT_TERTIARY);
        ui.put("Button.default.background",    UIConstants.COLOR_PRIMARY);
        ui.put("Button.default.foreground",    UIConstants.COLOR_TEXT_INVERSE);
        ui.put("Button.default.hoverBackground", UIConstants.COLOR_PRIMARY_DARK);
        ui.put("Button.default.boldText",      true);
        ui.put("Button.innerFocusWidth",       0);
        ui.put("Button.margin",
            new java.awt.Insets(0, UIConstants.SPACING_LG, 0, UIConstants.SPACING_LG));

        // ------------------------------------------------------------------ //
        // TOGGLE BUTTON
        // ------------------------------------------------------------------ //
        ui.put("ToggleButton.arc",             UIConstants.RADIUS_LG);
        ui.put("ToggleButton.selectedBackground", UIConstants.COLOR_PRIMARY_MUTED);
        ui.put("ToggleButton.selectedForeground", UIConstants.COLOR_PRIMARY);

        // ------------------------------------------------------------------ //
        // TEXT FIELDS / TEXT AREA / PASSWORD
        // ------------------------------------------------------------------ //
        ui.put("TextField.background",         UIConstants.COLOR_SURFACE_CARD);
        ui.put("TextField.foreground",         UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("TextField.placeholderForeground", UIConstants.COLOR_TEXT_TERTIARY);
        ui.put("TextField.selectionBackground",UIConstants.COLOR_PRIMARY_MUTED);
        ui.put("TextField.selectionForeground",UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("TextField.caretForeground",    UIConstants.COLOR_PRIMARY);
        ui.put("TextField.font",               UIConstants.FONT_BODY);

        ui.put("TextArea.background",          UIConstants.COLOR_SURFACE_CARD);
        ui.put("TextArea.foreground",          UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("TextArea.font",                UIConstants.FONT_BODY);
        ui.put("TextArea.caretForeground",     UIConstants.COLOR_PRIMARY);

        ui.put("PasswordField.background",     UIConstants.COLOR_SURFACE_CARD);
        ui.put("PasswordField.foreground",     UIConstants.COLOR_TEXT_PRIMARY);

        // ------------------------------------------------------------------ //
        // COMBO BOX / SPINNER
        // ------------------------------------------------------------------ //
        ui.put("ComboBox.background",          UIConstants.COLOR_SURFACE_CARD);
        ui.put("ComboBox.foreground",          UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("ComboBox.selectionBackground", UIConstants.COLOR_PRIMARY_MUTED);
        ui.put("ComboBox.selectionForeground", UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("ComboBox.buttonBackground",    UIConstants.COLOR_SURFACE_CARD);
        ui.put("ComboBox.font",                UIConstants.FONT_BODY);
        ui.put("ComboBox.popupBackground",     UIConstants.COLOR_SURFACE_CARD);
        ui.put("ComboBox.padding",
            new java.awt.Insets(4, UIConstants.SPACING_SM, 4, UIConstants.SPACING_SM));

        ui.put("Spinner.background",           UIConstants.COLOR_SURFACE_CARD);
        ui.put("Spinner.foreground",           UIConstants.COLOR_TEXT_PRIMARY);

        // ------------------------------------------------------------------ //
        // TABLE
        // ------------------------------------------------------------------ //
        ui.put("Table.background",             UIConstants.COLOR_SURFACE_CARD);
        ui.put("Table.foreground",             UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("Table.alternateRowBackground", UIConstants.COLOR_SURFACE_ALT);
        ui.put("Table.selectionBackground",    UIConstants.COLOR_PRIMARY_MUTED);
        ui.put("Table.selectionForeground",    UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("Table.gridColor",              UIConstants.COLOR_BORDER_SUBTLE);
        ui.put("Table.font",                   UIConstants.FONT_BODY);
        ui.put("Table.rowHeight",              UIConstants.SIZE_ROW_HEIGHT);
        ui.put("Table.showHorizontalLines",    true);
        ui.put("Table.showVerticalLines",      false);
        ui.put("Table.intercellSpacing",       new java.awt.Dimension(0, 0));

        // Table header
        ui.put("TableHeader.background",       UIConstants.COLOR_SURFACE_HEADER);
        ui.put("TableHeader.foreground",       UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("TableHeader.font",             UIConstants.FONT_HEADER);
        ui.put("TableHeader.separatorColor",   UIConstants.COLOR_BORDER);
        ui.put("TableHeader.hoverBackground",  UIConstants.COLOR_BORDER_SUBTLE);
        ui.put("TableHeader.height",           UIConstants.SIZE_ROW_HEIGHT);

        // ------------------------------------------------------------------ //
        // SCROLL PANE / SCROLL BAR
        // ------------------------------------------------------------------ //
        ui.put("ScrollPane.background",        UIConstants.COLOR_SURFACE);
        ui.put("ScrollPane.border",            BorderFactory.createEmptyBorder());
        ui.put("ScrollBar.width",              8);
        ui.put("ScrollBar.thumbArc",           UIConstants.RADIUS_PILL);
        ui.put("ScrollBar.thumbColor",         UIConstants.COLOR_BORDER);
        ui.put("ScrollBar.hoverThumbColor",    UIConstants.COLOR_TEXT_TERTIARY);
        ui.put("ScrollBar.pressedThumbColor",  UIConstants.COLOR_TEXT_SECONDARY);
        ui.put("ScrollBar.trackColor",         UIConstants.COLOR_SURFACE);
        ui.put("ScrollBar.showButtons",        false);

        // ------------------------------------------------------------------ //
        // TABBED PANE
        // ------------------------------------------------------------------ //
        ui.put("TabbedPane.font",              UIConstants.FONT_BODY);
        ui.put("TabbedPane.foreground",        UIConstants.COLOR_TEXT_SECONDARY);
        ui.put("TabbedPane.selectedForeground",UIConstants.COLOR_PRIMARY);
        ui.put("TabbedPane.underlineColor",    UIConstants.COLOR_PRIMARY);
        ui.put("TabbedPane.hoverColor",        UIConstants.COLOR_PRIMARY_LIGHT);
        ui.put("TabbedPane.tabHeight",         36);
        ui.put("TabbedPane.tabArc",            UIConstants.RADIUS_MD);
        ui.put("TabbedPane.showTabSeparators", false);

        // ------------------------------------------------------------------ //
        // MENU / MENUBAR / POPUP
        // ------------------------------------------------------------------ //
        ui.put("MenuBar.background",           UIConstants.COLOR_SURFACE_CARD);
        ui.put("MenuBar.borderColor",          UIConstants.COLOR_BORDER_SUBTLE);
        ui.put("Menu.font",                    UIConstants.FONT_BODY);
        ui.put("Menu.foreground",              UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("MenuItem.font",                UIConstants.FONT_BODY);
        ui.put("MenuItem.foreground",          UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("MenuItem.selectionBackground", UIConstants.COLOR_PRIMARY_MUTED);
        ui.put("MenuItem.selectionForeground", UIConstants.COLOR_PRIMARY);
        ui.put("PopupMenu.background",         UIConstants.COLOR_SURFACE_CARD);
        ui.put("PopupMenu.border",
            BorderFactory.createLineBorder(UIConstants.COLOR_BORDER_SUBTLE, 1));

        // ------------------------------------------------------------------ //
        // TOOLTIP
        // ------------------------------------------------------------------ //
        ui.put("ToolTip.background",           UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("ToolTip.foreground",           UIConstants.COLOR_TEXT_INVERSE);
        ui.put("ToolTip.font",                 UIConstants.FONT_SMALL);
        ui.put("ToolTip.arc",                  UIConstants.RADIUS_MD);

        // ------------------------------------------------------------------ //
        // PROGRESS BAR
        // ------------------------------------------------------------------ //
        ui.put("ProgressBar.background",       UIConstants.COLOR_NEUTRAL_BG);
        ui.put("ProgressBar.foreground",       UIConstants.COLOR_PRIMARY);
        ui.put("ProgressBar.arc",              UIConstants.RADIUS_PILL);

        // ------------------------------------------------------------------ //
        // CHECK BOX / RADIO BUTTON
        // ------------------------------------------------------------------ //
        ui.put("CheckBox.font",                UIConstants.FONT_BODY);
        ui.put("CheckBox.foreground",          UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("CheckBox.icon.selectedColor",  UIConstants.COLOR_PRIMARY);
        ui.put("CheckBox.icon.checkmarkColor", UIConstants.COLOR_TEXT_INVERSE);
        ui.put("RadioButton.font",             UIConstants.FONT_BODY);
        ui.put("RadioButton.foreground",       UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("RadioButton.icon.selectedColor", UIConstants.COLOR_PRIMARY);

        // ------------------------------------------------------------------ //
        // SEPARATOR
        // ------------------------------------------------------------------ //
        ui.put("Separator.foreground",         UIConstants.COLOR_BORDER_SUBTLE);

        // ------------------------------------------------------------------ //
        // SPLIT PANE
        // ------------------------------------------------------------------ //
        ui.put("SplitPane.dividerSize",        5);
        ui.put("SplitPaneDivider.draggingColor", UIConstants.COLOR_BORDER);

        // ------------------------------------------------------------------ //
        // LIST
        // ------------------------------------------------------------------ //
        ui.put("List.background",              UIConstants.COLOR_SURFACE_CARD);
        ui.put("List.foreground",              UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("List.selectionBackground",     UIConstants.COLOR_PRIMARY_MUTED);
        ui.put("List.selectionForeground",     UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("List.font",                    UIConstants.FONT_BODY);

        // ------------------------------------------------------------------ //
        // TREE
        // ------------------------------------------------------------------ //
        ui.put("Tree.background",              UIConstants.COLOR_SURFACE_CARD);
        ui.put("Tree.foreground",              UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("Tree.selectionBackground",     UIConstants.COLOR_PRIMARY_MUTED);
        ui.put("Tree.selectionForeground",     UIConstants.COLOR_TEXT_PRIMARY);
        ui.put("Tree.font",                    UIConstants.FONT_BODY);
    }
}