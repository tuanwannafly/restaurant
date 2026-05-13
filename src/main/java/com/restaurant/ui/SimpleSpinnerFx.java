package com.restaurant.ui;

import javafx.animation.RotateTransition;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * SimpleSpinnerFx  ─  Phase 8
 *
 * <p>Custom control hiển thị vòng tròn xoay (Arc) dùng {@link RotateTransition}.
 * Tương đương {@code SimpleSpinner} trong Swing (Phase 7B).
 *
 * <h3>Cách dùng</h3>
 * <pre>{@code
 * SimpleSpinnerFx spinner = new SimpleSpinnerFx(24, Color.web("#3B82F6"));
 * spinner.start();   // bắt đầu xoay + hiện
 * spinner.stop();    // dừng + ẩn
 * }</pre>
 */
public class SimpleSpinnerFx extends Region {

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final Arc            arc;
    private final RotateTransition rotation;

    private final double size;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * @param size  đường kính (px) của spinner
     * @param color màu của Arc xoay
     */
    public SimpleSpinnerFx(double size, Color color) {
        this.size = size;
        setPrefSize(size, size);
        setMinSize(size, size);
        setMaxSize(size, size);

        double r      = size / 2.0;
        double stroke = Math.max(2.0, size / 10.0);

        // Track (vòng nền mờ)
        Circle track = new Circle(r, r, r - stroke / 2.0);
        track.setFill(null);
        track.setStroke(color.deriveColor(0, 1, 1, 0.18));
        track.setStrokeWidth(stroke);

        // Arc xoay (270° / 360° cung)
        arc = new Arc(r, r,
                      r - stroke / 2.0, r - stroke / 2.0,
                      90, 270);   // start=90°, length=270°
        arc.setType(ArcType.OPEN);
        arc.setFill(null);
        arc.setStroke(color);
        arc.setStrokeWidth(stroke);
        // Bo đầu arc
        arc.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);

        getChildren().addAll(track, arc);

        // ── RotateTransition trên arc ─────────────────────────────────────
        rotation = new RotateTransition(Duration.millis(900), arc);
        rotation.setFromAngle(0);
        rotation.setToAngle(360);
        rotation.setCycleCount(RotateTransition.INDEFINITE);
        rotation.setInterpolator(javafx.animation.Interpolator.LINEAR);

        // Ẩn ban đầu
        setVisible(false);
        setManaged(false);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Bắt đầu xoay và hiện spinner.
     * Gọi từ JavaFX Application Thread.
     */
    public void start() {
        setVisible(true);
        setManaged(true);
        if (rotation.getStatus() != javafx.animation.Animation.Status.RUNNING) {
            rotation.play();
        }
    }

    /**
     * Dừng xoay và ẩn spinner.
     * Gọi từ JavaFX Application Thread.
     */
    public void stop() {
        rotation.stop();
        setVisible(false);
        setManaged(false);
    }

    /** Kiểm tra spinner có đang chạy không. */
    public boolean isRunning() {
        return rotation.getStatus() == javafx.animation.Animation.Status.RUNNING;
    }

    // ─── Override ─────────────────────────────────────────────────────────────

    @Override
    protected void layoutChildren() {
        // Children đã được đặt tọa độ tuyệt đối trong constructor
    }
}