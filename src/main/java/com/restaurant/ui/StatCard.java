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
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Reusable stat card for the dashboard (REDESIGNED).
 * Modern card: white background, top accent bar, large icon, clean typography.
 */
public class StatCard extends HBox {

    public enum CardIcon { STORE, PLUS, COIN, BOX }

    @FXML private Canvas iconCanvas;
    @FXML private Label  lblValue;
    @FXML private Label  lblTitle;

    private CardIcon cardIcon;
    private Color    accentColor;

    private final DropShadow shadowIdle  = new DropShadow(12, 0, 4, Color.rgb(0, 0, 0, 0.09));
    private final DropShadow shadowHover = new DropShadow(24, 0, 8, Color.rgb(0, 0, 0, 0.16));

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
            setScaleX(1.012);
            setScaleY(1.012);
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

    private void applyCardStyle(Color accent) {
        String hex = toCssHex(accent);
        setStyle(
            "-fx-background-color: white;"      +
            "-fx-background-radius: 14;"         +
            "-fx-border-radius: 14;"             +
            "-fx-border-color: " + hex + " transparent transparent transparent;" +
            "-fx-border-width: 4 0 0 0;"
        );
    }

    private void drawIcon() {
        if (iconCanvas == null || cardIcon == null || accentColor == null) return;

        GraphicsContext gc = iconCanvas.getGraphicsContext2D();
        double w  = iconCanvas.getWidth();
        double h  = iconCanvas.getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;

        gc.clearRect(0, 0, w, h);

        RadialGradient grad = new RadialGradient(
            0, 0, cx, cy, w / 2.0, false, CycleMethod.NO_CYCLE,
            new Stop(0.0, accentColor.deriveColor(0, 1.0, 1.0, 0.18)),
            new Stop(1.0, accentColor.deriveColor(0, 1.0, 1.0, 0.07))
        );
        gc.setFill(grad);
        gc.fillOval(0, 0, w, h);

        gc.setStroke(accentColor);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.setLineWidth(2.0);

        switch (cardIcon) {
            case STORE -> drawStore(gc, cx, cy);
            case PLUS  -> drawPlus(gc, cx, cy);
            case COIN  -> drawCoin(gc, cx, cy);
            case BOX   -> drawBox(gc, cx, cy);
        }
    }

    private void drawStore(GraphicsContext gc, double cx, double cy) {
        gc.strokePolyline(
            new double[]{ cx - 11, cx,       cx + 11 },
            new double[]{ cy - 1,  cy - 11,  cy - 1  },
            3);
        gc.strokeRect(cx - 9, cy - 1, 18, 12);
        gc.strokeRect(cx - 3.5, cy + 4, 7, 7);
    }

    private void drawPlus(GraphicsContext gc, double cx, double cy) {
        gc.setLineWidth(2.4);
        gc.strokeLine(cx,      cy - 10, cx,      cy + 10);
        gc.strokeLine(cx - 10, cy,      cx + 10, cy);
    }

    private void drawCoin(GraphicsContext gc, double cx, double cy) {
        gc.strokeOval(cx - 11, cy - 11, 22, 22);
        gc.strokeLine(cx, cy - 6, cx, cy + 6);
        gc.strokeArc(cx - 5, cy - 6, 10, 6,   0, 180, ArcType.OPEN);
        gc.strokeArc(cx - 5, cy,     10, 6, 180, 180, ArcType.OPEN);
    }

    private void drawBox(GraphicsContext gc, double cx, double cy) {
        gc.strokeRect(cx - 9, cy - 3, 18, 13);
        gc.strokeLine(cx - 12, cy - 6, cx + 12, cy - 6);
        gc.strokeLine(cx - 12, cy - 6, cx - 9,  cy - 3);
        gc.strokeLine(cx + 12, cy - 6, cx + 9,  cy - 3);
        gc.strokeLine(cx - 9,  cy - 3, cx + 9,  cy - 3);
        gc.strokeLine(cx,      cy - 6, cx,       cy + 10);
    }

    private static String toCssHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed()   * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue()  * 255));
    }
}
