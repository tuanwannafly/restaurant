package com.restaurant.ui.fx.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * ToastNotificationFx — Phase 10 · Utility
 * ─────────────────────────────────────────────────────────────────────────────
 * Hiển thị thông báo nhỏ (toast) ở <b>góc dưới phải</b> của cửa sổ chính.
 * Dùng {@link Popup} (không phải Stage con) để toast nổi trên màn hình mà
 * không block tương tác.
 *
 * <p>Tương đương {@code com.restaurant.ui.ToastNotification} (Swing Phase 7C).
 *
 * <h3>Các loại toast:</h3>
 * <ul>
 *   <li>{@link #showInfo(Stage, String)}    — nền xanh dương, icon 💡</li>
 *   <li>{@link #showSuccess(Stage, String)} — nền xanh lá,   icon ✅</li>
 *   <li>{@link #showError(Stage, String)}   — nền đỏ,         icon ⛔</li>
 * </ul>
 *
 * <h3>Animation:</h3>
 * <pre>
 *   FadeIn (0.3s) → Hiển thị (2.4s) → FadeOut (0.3s) → Popup.hide()
 *   Tổng: 3 giây
 * </pre>
 *
 * <h3>Cách dùng:</h3>
 * <pre>{@code
 *   // Trong CashierController:
 *   ToastNotificationFx.showSuccess(getStage(), "Thanh toán hoàn tất!");
 *   ToastNotificationFx.showInfo(getStage(),    "Có 2 yêu cầu thanh toán mới!");
 *   ToastNotificationFx.showError(getStage(),   "Không thể hoàn tất đơn #123");
 * }</pre>
 *
 * <h3>Lưu ý:</h3>
 * <ul>
 *   <li>Phải gọi trên JavaFX Application Thread (hoặc wrap trong
 *       {@link Platform#runLater(Runnable)}).</li>
 *   <li>Nếu {@code ownerStage} là {@code null}, toast sẽ không hiển thị.</li>
 * </ul>
 *
 * <h3>File vị trí:</h3>
 * {@code src/main/java/com/restaurant/ui/fx/util/ToastNotificationFx.java}
 */
public final class ToastNotificationFx {

    // ─── Hằng số giao diện ───────────────────────────────────────────────────

    /** Tổng thời gian hiển thị toast (fade-in + pause + fade-out) = 3 giây. */
    private static final double TOTAL_DURATION_S = 3.0;

    private static final double FADE_IN_S        = 0.3;
    private static final double PAUSE_S          = 2.4;
    private static final double FADE_OUT_S       = 0.3;

    /** Khoảng cách từ cạnh phải và cạnh dưới của cửa sổ (px). */
    private static final double MARGIN_RIGHT  = 24;
    private static final double MARGIN_BOTTOM = 24;

    /** Chiều cao ước tính của toast (dùng để tính vị trí Y). */
    private static final double TOAST_HEIGHT  = 48;

    // ─── Màu nền theo loại ───────────────────────────────────────────────────

    private static final String COLOR_INFO    = "#1565C0"; // xanh dương đậm
    private static final String COLOR_SUCCESS = "#2E7D32"; // xanh lá đậm
    private static final String COLOR_ERROR   = "#C62828"; // đỏ đậm

    // ─── Constructor ẩn (utility class) ─────────────────────────────────────

    private ToastNotificationFx() {}

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Toast thông tin — nền xanh dương, icon 💡.
     *
     * @param owner   Stage chủ (toast neo theo vị trí cửa sổ này)
     * @param message nội dung cần hiển thị
     */
    public static void showInfo(Stage owner, String message) {
        show(owner, "💡  " + message, COLOR_INFO);
    }

    /**
     * Toast thành công — nền xanh lá, icon ✅.
     *
     * @param owner   Stage chủ
     * @param message nội dung cần hiển thị
     */
    public static void showSuccess(Stage owner, String message) {
        show(owner, "✅  " + message, COLOR_SUCCESS);
    }

    /**
     * Toast lỗi — nền đỏ, icon ⛔.
     *
     * @param owner   Stage chủ
     * @param message nội dung cần hiển thị
     */
    public static void showError(Stage owner, String message) {
        show(owner, "⛔  " + message, COLOR_ERROR);
    }

    // ─── Core show ───────────────────────────────────────────────────────────

    /**
     * Tạo {@link Popup} chứa HBox styled, tính tọa độ góc dưới phải,
     * rồi chạy animation {@code FadeIn → Pause → FadeOut → hide()}.
     *
     * @param owner      Stage neo toast; nếu null thì bỏ qua
     * @param message    nội dung đã có icon
     * @param bgColorHex hex màu nền (#RRGGBB)
     */
    private static void show(Stage owner, String message, String bgColorHex) {
        if (owner == null) return;

        // Đảm bảo chạy trên JavaFX Application Thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(owner, message, bgColorHex));
            return;
        }

        // ── Build nội dung toast ──
        Label lbl = new Label(message);
        lbl.setTextFill(Color.WHITE);
        lbl.setStyle(
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 13px;"
        );
        lbl.setWrapText(false);

        HBox box = new HBox(lbl);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(12, 20, 12, 20));
        box.setStyle(
            "-fx-background-color: " + bgColorHex + ";" +
            "-fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 10, 0, 0, 3);"
        );
        // Opacity khởi đầu = 0 cho FadeIn
        box.setOpacity(0);

        // ── Tạo Popup ──
        Popup popup = new Popup();
        popup.setAutoHide(false);    // không hide khi click ra ngoài
        popup.getContent().add(box);

        // Phải show trước khi biết kích thước thực để tính tọa độ
        popup.show(owner);

        // Tính tọa độ góc dưới phải dựa trên kích thước cửa sổ chủ
        double x = owner.getX() + owner.getWidth()
                   - (box.getWidth() > 0 ? box.getWidth() : 300)
                   - MARGIN_RIGHT;
        double y = owner.getY() + owner.getHeight()
                   - TOAST_HEIGHT
                   - MARGIN_BOTTOM;
        popup.setX(x);
        popup.setY(y);

        // ── Animation: FadeIn → Pause → FadeOut ──
        FadeTransition fadeIn = new FadeTransition(Duration.seconds(FADE_IN_S), box);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        PauseTransition pause = new PauseTransition(Duration.seconds(PAUSE_S));

        FadeTransition fadeOut = new FadeTransition(Duration.seconds(FADE_OUT_S), box);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        SequentialTransition seq = new SequentialTransition(fadeIn, pause, fadeOut);
        seq.setOnFinished(e -> popup.hide());
        seq.play();
    }
}