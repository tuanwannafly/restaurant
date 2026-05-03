package com.restaurant.ui.control;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * BadgeLabel — custom JavaFX control that wraps a text label and
 * overlays a small red circular badge in the top-right corner
 * showing a numeric count (e.g. pending kitchen orders).
 *
 * <pre>
 *  ┌─────────────────────┐
 *  │  Bếp             ③  │  ← badge (red circle, white number)
 *  └─────────────────────┘
 * </pre>
 *
 * <b>Usage:</b>
 * <pre>{@code
 *   BadgeLabel bl = new BadgeLabel("Bếp");
 *   bl.setCount(5);     // shows badge "5"
 *   bl.setCount(0);     // hides badge
 *   bl.setCount(100);   // shows "99+"
 * }</pre>
 *
 * <b>Styling:</b>
 * Apply CSS class {@code .badge-label} on the outer StackPane,
 * {@code .badge-dot} on the inner circle overlay.
 *
 * <b>Active state:</b>
 * CSS pseudo-class {@code :active-nav} is toggled via {@link #setActive(boolean)}.
 */
public class BadgeLabel extends StackPane {

    // ── CSS pseudo-class for active nav state ──────────────────────────────
    private static final PseudoClass ACTIVE_NAV =
            PseudoClass.getPseudoClass("active-nav");

    // ── Count property ─────────────────────────────────────────────────────
    private final IntegerProperty count = new SimpleIntegerProperty(this, "count", 0) {
        @Override
        protected void invalidated() {
            updateBadgeVisibility();
        }
    };

    // ── Child nodes ────────────────────────────────────────────────────────
    private final Label    navLabel;   // main text (e.g. "Bếp")
    private final StackPane badge;     // red circle overlay
    private final Label    badgeText;  // number inside circle

    // ── Constructor ────────────────────────────────────────────────────────

    /**
     * Creates a BadgeLabel with the given nav text.
     *
     * @param text the navigation item label (e.g. "Bếp", "Phục vụ")
     */
    public BadgeLabel(String text) {
        getStyleClass().add("badge-label-container");
        setAlignment(Pos.CENTER_LEFT);

        // Main nav text label
        navLabel = new Label(text);
        navLabel.getStyleClass().add("nav-label-text");
        navLabel.setMaxWidth(Double.MAX_VALUE);

        // Badge dot (red circle)
        badgeText = new Label("0");
        badgeText.getStyleClass().add("badge-dot-text");
        badgeText.setFont(Font.font("System", FontWeight.BOLD, 9));
        badgeText.setAlignment(Pos.CENTER);

        badge = new StackPane(badgeText);
        badge.getStyleClass().add("badge-dot");
        badge.setMinSize(16, 16);
        badge.setMaxSize(18, 18);
        badge.setAlignment(Pos.CENTER);
        badge.setVisible(false);
        badge.setManaged(false);

        // Layout: navLabel takes full width; badge floats to top-right corner
        StackPane.setAlignment(navLabel, Pos.CENTER_LEFT);
        StackPane.setAlignment(badge,    Pos.TOP_RIGHT);
        StackPane.setMargin(badge,       new Insets(-4, -4, 0, 0));

        getChildren().addAll(navLabel, badge);
    }

    // ── Count property accessors ───────────────────────────────────────────

    public IntegerProperty countProperty() { return count; }

    public int  getCount()           { return count.get(); }
    public void setCount(int value)  { count.set(Math.max(0, value)); }

    // ── Active nav state ───────────────────────────────────────────────────

    /**
     * Toggles the {@code :active-nav} pseudo-class for CSS styling.
     * When active, the nav item should receive a highlighted background
     * and primary-colored text.
     *
     * @param active {@code true} = current page, {@code false} = idle
     */
    public void setActive(boolean active) {
        pseudoClassStateChanged(ACTIVE_NAV, active);
        navLabel.pseudoClassStateChanged(ACTIVE_NAV, active);
    }

    // ── Nav label text ─────────────────────────────────────────────────────

    public String getText()           { return navLabel.getText(); }
    public void   setText(String txt) { navLabel.setText(txt); }

    // ── Private helpers ────────────────────────────────────────────────────

    private void updateBadgeVisibility() {
        int c = count.get();
        if (c <= 0) {
            badge.setVisible(false);
            badge.setManaged(false);
        } else {
            badgeText.setText(c > 99 ? "99+" : String.valueOf(c));
            badge.setVisible(true);
            badge.setManaged(true);
        }
    }
}