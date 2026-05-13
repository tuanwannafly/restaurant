package com.restaurant.ui;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * InlineErrorBarFx  ─  Phase 8
 *
 * <p>HBox hiển thị lỗi inline (thay {@code System.err} và Swing {@code InlineErrorBar}).
 * Tự động mờ dần và ẩn sau {@value #AUTO_HIDE_MS} ms.
 *
 * <h3>Cách dùng</h3>
 * <pre>{@code
 * // 1. Tạo một lần và thêm vào layout (ẩn mặc định)
 * InlineErrorBarFx errorBar = new InlineErrorBarFx();
 * headerHBox.getChildren().add(errorBar);
 *
 * // 2. Hiện khi có lỗi (gọi từ bất kỳ thread nào)
 * errorBar.show("Lỗi tải dữ liệu: " + ex.getMessage());
 *
 * // 3. Ẩn thủ công nếu cần
 * errorBar.hide();
 * }</pre>
 */
public class InlineErrorBarFx extends HBox {

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final long   AUTO_HIDE_MS    = 5_000;
    private static final String BG_COLOR        = "#FEF2F2";
    private static final String BORDER_COLOR    = "#FCA5A5";
    private static final String TEXT_COLOR      = "#B91C1C";

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final Label          messageLabel;
    private       Timeline       autoHideTimer;
    private       FadeTransition fadeOut;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public InlineErrorBarFx() {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        setPadding(new Insets(6, 12, 6, 12));
        setStyle(
            "-fx-background-color: " + BG_COLOR + ";" +
            "-fx-border-color: "     + BORDER_COLOR + ";" +
            "-fx-border-radius: 6;"  +
            "-fx-background-radius: 6;"
        );

        // ── Icon (⚠ encoded as text; swap SVGPath if icon font unavailable)
        Label icon = new Label("⚠");
        icon.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_COLOR + ";");

        messageLabel = new Label();
        messageLabel.setWrapText(false);
        messageLabel.setStyle(
            "-fx-font-family: 'Segoe UI';" +
            "-fx-font-size: 12px;" +
            "-fx-text-fill: " + TEXT_COLOR + ";"
        );

        // Close button
        Label closeBtn = new Label("✕");
        closeBtn.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-text-fill: " + TEXT_COLOR + ";" +
            "-fx-cursor: hand;"
        );
        closeBtn.setOnMouseClicked(e -> hide());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(icon, messageLabel, spacer, closeBtn);

        // Hide by default
        setVisible(false);
        setManaged(false);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Hiện error bar với nội dung {@code message}.
     * An toàn khi gọi từ background thread — sẽ chuyển về FX thread tự động.
     *
     * @param message nội dung lỗi cần hiển thị
     */
    public void show(String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(message));
            return;
        }

        // Huỷ animation cũ nếu đang chạy
        cancelAnimations();

        messageLabel.setText(message);
        setOpacity(1.0);
        setVisible(true);
        setManaged(true);

        // Auto-hide sau AUTO_HIDE_MS
        autoHideTimer = new Timeline(
            new KeyFrame(Duration.millis(AUTO_HIDE_MS), e -> startFadeOut())
        );
        autoHideTimer.play();
    }

    /**
     * Ẩn error bar ngay lập tức (không animation).
     * An toàn khi gọi từ background thread.
     */
    public void hide() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::hide);
            return;
        }
        cancelAnimations();
        setVisible(false);
        setManaged(false);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void startFadeOut() {
        fadeOut = new FadeTransition(Duration.millis(600), this);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            setVisible(false);
            setManaged(false);
            setOpacity(1.0); // reset cho lần sau
        });
        fadeOut.play();
    }

    private void cancelAnimations() {
        if (autoHideTimer != null) {
            autoHideTimer.stop();
            autoHideTimer = null;
        }
        if (fadeOut != null) {
            fadeOut.stop();
            fadeOut = null;
        }
    }
}