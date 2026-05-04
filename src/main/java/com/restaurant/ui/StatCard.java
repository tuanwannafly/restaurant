package com.restaurant.ui;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Reusable stat card for the dashboard (REDESIGNED v3).
 * Features: coloured top accent bar, tinted icon badge, bold value, clean subtitle.
 */
public class StatCard extends VBox {

    public enum CardIcon { STORE, PLUS, COIN, BOX }

    @FXML private Canvas iconCanvas;
    @FXML private Label  lblValue;
    @FXML private Label  lblTitle;
    @FXML private Region accentBar;

    private CardIcon cardIcon;
    private Color    accentColor;

    private final DropShadow shadowIdle  = new DropShadow(14, 0, 4, Color.rgb(0, 0, 0, 0.08));
    private final DropShadow shadowHover = new DropShadow(28, 0, 10, Color.rgb(0, 0, 0, 0.15));

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
        setEffect(shadowIdle);
        setOnMouseEntered(e -> {
            setEffect(shadowHover);
            setScaleX(1.015);
            setScaleY(1.015);
        });
        setOnMouseExited(e -> {
            setEffect(shadowIdle);
            setScaleX(1.0);
            setScaleY(1.0);
        });
    }

    public void configure(CardIcon icon, String title, Color accent) {
        this.cardIcon    = icon;
        this.accentColor = accent;
        lblTitle.setText(title);
        applyCardStyle(accent);
        drawIcon();
    }

    public void setValue(String value) {
        if (lblValue != null) lblValue.setText(value);
    }

    // ── Styling ─────────────────────────────────────────────────────────────

    private void applyCardStyle(Color accent) {
        String hex    = toCssHex(accent);
        String hexBg  = toCssHex(accent.deriveColor(0, 1.0, 1.0, 0.05));

        // Card container: white with subtle tinted background and rounded corners
        setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 14;" +
            "-fx-border-radius: 14;" +
            "-fx-border-color: #E8EDF3;" +
            "-fx-border-width: 1;"
        );

        // Accent top bar
        if (accentBar != null) {
            accentBar.setStyle(
                "-fx-background-color: " + hex + ";" +
                "-fx-background-radius: 13 13 0 0;"
            );
        }
    }

    // ── Icon drawing ─────────────────────────────────────────────────────────

    private void drawIcon() {
        if (iconCanvas == null || cardIcon == null || accentColor == null) return;

        GraphicsContext gc = iconCanvas.getGraphicsContext2D();
        double w  = iconCanvas.getWidth();   // 64
        double h  = iconCanvas.getHeight();  // 64
        double cx = w / 2.0;
        double cy = h / 2.0;
        double r  = w / 2.0;

        gc.clearRect(0, 0, w, h);

        // Soft radial gradient background circle
        RadialGradient bg = new RadialGradient(
            0, 0, cx, cy, r, false, CycleMethod.NO_CYCLE,
            new Stop(0.0, accentColor.deriveColor(0, 1.0, 1.1, 0.22)),
            new Stop(1.0, accentColor.deriveColor(0, 1.0, 0.95, 0.10))
        );
        gc.setFill(bg);
        gc.fillOval(0, 0, w, h);

        // Icon strokes — slightly thicker for the larger canvas
        gc.setStroke(accentColor);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.setLineWidth(2.4);

        switch (cardIcon) {
            case STORE   -> drawStore(gc, cx, cy);
            case PLUS    -> drawPlus(gc, cx, cy);
            case COIN    -> drawCoin(gc, cx, cy);
            case BOX     -> drawBox(gc, cx, cy);
        }
    }

    private void drawStore(GraphicsContext gc, double cx, double cy) {
        // Roof
        gc.strokePolyline(
            new double[]{ cx - 13, cx,      cx + 13 },
            new double[]{ cy - 1,  cy - 13, cy - 1  },
            3);
        // Walls
        gc.strokeRect(cx - 11, cy - 1, 22, 14);
        // Door
        gc.strokeRect(cx - 4, cy + 5, 8, 8);
    }

    private void drawPlus(GraphicsContext gc, double cx, double cy) {
        gc.setLineWidth(2.8);
        gc.strokeLine(cx,      cy - 12, cx,      cy + 12);
        gc.strokeLine(cx - 12, cy,      cx + 12, cy);
    }

    private void drawCoin(GraphicsContext gc, double cx, double cy) {
        // Outer circle
        gc.strokeOval(cx - 13, cy - 13, 26, 26);
        // Dollar sign top stroke
        gc.strokeLine(cx, cy - 9, cx, cy + 9);
        gc.strokeArc(cx - 6, cy - 9, 12, 7,   0, 180, ArcType.OPEN);
        gc.strokeArc(cx - 6, cy + 2, 12, 7, 180, 180, ArcType.OPEN);
    }

    private void drawBox(GraphicsContext gc, double cx, double cy) {
        // Box body
        gc.strokeRect(cx - 11, cy - 2, 22, 15);
        // Lid
        gc.strokeLine(cx - 14, cy - 5, cx + 14, cy - 5);
        gc.strokeLine(cx - 14, cy - 5, cx - 11, cy - 2);
        gc.strokeLine(cx + 14, cy - 5, cx + 11, cy - 2);
        gc.strokeLine(cx - 11, cy - 2, cx + 11, cy - 2);
        // Center ribbon
        gc.strokeLine(cx, cy - 5, cx, cy + 13);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static String toCssHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed()   * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue()  * 255));
    }
}
