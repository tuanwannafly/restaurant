package com.restaurant.ui;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Reusable stat card for the dashboard.
 *
 * <p>Usage (HomeController):
 * <pre>
 *   cardActive.configure(StatCard.CardIcon.STORE, "Nhà hàng hoạt động", Color.web("#3B82F6"));
 *   cardActive.setValue("12");
 * </pre>
 *
 * <p>Loads {@code StatCardComponent.fxml} via the fx:root pattern.
 * Icons are rendered with JavaFX {@link GraphicsContext} — no emoji, all shapes.
 * Hover raises the drop-shadow (lift effect).
 */
public class StatCard extends HBox {

    // ── Icon types ─────────────────────────────────────────────────────────

    public enum CardIcon { STORE, PLUS, COIN, BOX }

    // ── FXML bindings ──────────────────────────────────────────────────────

    @FXML private Canvas iconCanvas;
    @FXML private Label  lblValue;
    @FXML private Label  lblTitle;

    // ── Internal state ─────────────────────────────────────────────────────

    private CardIcon    cardIcon;
    private Color       accentColor;

    private final DropShadow shadowIdle  = new DropShadow(8,  0, 3, Color.rgb(0, 0, 0, 0.12));
    private final DropShadow shadowHover = new DropShadow(18, 0, 7, Color.rgb(0, 0, 0, 0.20));

    // =========================================================================
    // Constructor — fx:root loader
    // =========================================================================

    public StatCard() {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/restaurant/ui/StatCardComponent.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Cannot load StatCardComponent.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        // Drop-shadow on idle
        setEffect(shadowIdle);

        // Hover lift
        setOnMouseEntered(e -> setEffect(shadowHover));
        setOnMouseExited(e  -> setEffect(shadowIdle));
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Must be called once after the card is injected by the FXML loader.
     *
     * @param icon   icon shape to draw inside the circle
     * @param title  descriptor label below the value
     * @param accent brand colour — used for the left border bar and icon tint
     */
    public void configure(CardIcon icon, String title, Color accent) {
        this.cardIcon    = icon;
        this.accentColor = accent;

        lblTitle.setText(title);
        applyCardStyle(accent);
        drawIcon();
    }

    /**
     * Updates the large value label (call on the JavaFX Application Thread).
     */
    public void setValue(String value) {
        if (lblValue != null) lblValue.setText(value);
    }

    // =========================================================================
    // Style
    // =========================================================================

    private void applyCardStyle(Color accent) {
        String hex = toCssHex(accent);
        setStyle(
            "-fx-background-color: white;"      +
            "-fx-background-radius: 14;"         +
            "-fx-border-radius: 14;"             +
            // 4 px left accent bar (top right bottom LEFT)
            "-fx-border-color: transparent transparent transparent " + hex + ";" +
            "-fx-border-width: 0 0 0 4;"
        );
    }

    // =========================================================================
    // Icon rendering
    // =========================================================================

    private void drawIcon() {
        if (iconCanvas == null || cardIcon == null || accentColor == null) return;

        GraphicsContext gc = iconCanvas.getGraphicsContext2D();
        double w  = iconCanvas.getWidth();
        double h  = iconCanvas.getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;

        gc.clearRect(0, 0, w, h);

        // ── Background circle — very light accent tint ───────────────────────
        Color bg = accentColor.deriveColor(0, 1.0, 1.0, 0.12);
        gc.setFill(bg);
        gc.fillOval(0, 0, w, h);

        // ── Icon strokes ─────────────────────────────────────────────────────
        gc.setStroke(accentColor);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.setLineWidth(1.8);

        switch (cardIcon) {
            case STORE -> drawStore(gc, cx, cy);
            case PLUS  -> drawPlus(gc, cx, cy);
            case COIN  -> drawCoin(gc, cx, cy);
            case BOX   -> drawBox(gc, cx, cy);
        }
    }

    // ── Shape painters (mirrored from HomePanel Java2D helpers) ──────────────

    /**
     * Building / store silhouette: roof triangle + rectangle body + door.
     */
    private void drawStore(GraphicsContext gc, double cx, double cy) {
        // Roof triangle (open polyline)
        gc.strokePolyline(
            new double[]{ cx - 9, cx,      cx + 9 },
            new double[]{ cy - 1, cy - 9,  cy - 1 },
            3);
        // Body
        gc.strokeRect(cx - 7, cy - 1, 14, 10);
        // Door
        gc.strokeRect(cx - 3, cy + 3,  6,  6);
    }

    /**
     * Plus / cross (new item indicator).
     */
    private void drawPlus(GraphicsContext gc, double cx, double cy) {
        gc.setLineWidth(2.0);
        gc.strokeLine(cx,      cy - 8, cx,      cy + 8);
        gc.strokeLine(cx - 8,  cy,     cx + 8,  cy);
    }

    /**
     * Coin (currency): outer circle + vertical bar + top/bottom arcs.
     */
    private void drawCoin(GraphicsContext gc, double cx, double cy) {
        gc.strokeOval(cx - 9, cy - 9, 18, 18);
        gc.strokeLine(cx, cy - 5, cx, cy + 5);
        // Top arc (₫ style)
        gc.strokeArc(cx - 4, cy - 5, 8, 5,   0, 180, ArcType.OPEN);
        // Bottom arc
        gc.strokeArc(cx - 4, cy,     8, 5, 180, 180, ArcType.OPEN);
    }

    /**
     * Package / box: body + lid + ribbon cross.
     */
    private void drawBox(GraphicsContext gc, double cx, double cy) {
        // Box body
        gc.strokeRect(cx - 8, cy - 3, 16, 12);
        // Lid top bar
        gc.strokeLine(cx - 10, cy - 5, cx + 10, cy - 5);
        // Lid corner slopes
        gc.strokeLine(cx - 10, cy - 5, cx - 8,  cy - 3);
        gc.strokeLine(cx + 10, cy - 5, cx + 8,  cy - 3);
        // Ribbon horizontal seam
        gc.strokeLine(cx - 8,  cy - 3, cx + 8,  cy - 3);
        // Ribbon vertical
        gc.strokeLine(cx,      cy - 5, cx,       cy + 9);
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private static String toCssHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed()   * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue()  * 255));
    }
}