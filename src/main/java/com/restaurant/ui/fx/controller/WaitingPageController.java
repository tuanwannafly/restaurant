package com.restaurant.ui.fx.controller;

import com.restaurant.ui.TableOrderStage;
import com.restaurant.ui.fx.util.PollManagerFx;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * WaitingPageController — Phase 15.
 *
 * <p>Hiển thị màn hình "Đang xử lý thanh toán".
 * PollManagerFx gọi {@link #checkOrderCompleted()} mỗi 5s.
 * Khi đơn đã đóng → toast + delay 2s → Stage.close().
 */
public class WaitingPageController extends BasePageController {

    @FXML private Label lblTableBadge;
    @FXML private Label lblIcon;

    private RotateTransition spinner;

    @FXML
    private void initialize() {
        // Xoay icon ⏳
        spinner = new RotateTransition(Duration.seconds(2), lblIcon);
        spinner.setByAngle(360);
        spinner.setCycleCount(Animation.INDEFINITE);
        spinner.setInterpolator(Interpolator.LINEAR);
    }

    @Override
    public void onNavigatedTo() {
        lblTableBadge.setText("Bàn " + stage.getTableName());
        spinner.play();
    }

    /** Gọi từ PollManagerFx mỗi 5s. */
    public void checkOrderCompleted() {
        stage.checkOrderActive(completed -> {
            if (completed && TableOrderStage.PAGE_WAITING.equals(stage.getCurrentPage())) {
                // Dừng poll
                PollManagerFx.getInstance().unregister("order_status_"  + stage.getTableId());
                PollManagerFx.getInstance().unregister("order_waiting_" + stage.getTableId());

                spinner.stop();

                // Hiện toast (dùng Alert tạm — thay bằng ToastFx nếu có)
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION,
                        "Thanh toán hoàn tất! Cảm ơn quý khách.",
                        javafx.scene.control.ButtonType.OK);
                alert.setHeaderText(null);
                alert.showAndWait();

                // Delay 2s rồi đóng Stage
                PauseTransition delay = new PauseTransition(Duration.seconds(2));
                delay.setOnFinished(e -> stage.close());
                delay.play();
            }
        });
    }

    @FXML
    private void onClose() {
        PollManagerFx.getInstance().unregister("order_status_"  + stage.getTableId());
        PollManagerFx.getInstance().unregister("order_waiting_" + stage.getTableId());
        spinner.stop();
        stage.close();
    }
}
