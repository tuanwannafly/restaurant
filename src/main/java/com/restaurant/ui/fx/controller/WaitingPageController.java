package com.restaurant.ui.fx.controller;

import com.restaurant.ui.TableOrderStage;
import com.restaurant.ui.fx.util.PollManagerFx;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * WaitingPageController — Phase 15.
 *
 * <p>Hiển thị màn hình "Đang xử lý thanh toán".
 * Khi đơn đã đóng → ẩn waitingCard, hiện thankYouCard với tên nhà hàng,
 * rồi tự động đóng Stage sau 3 giây.
 */
public class WaitingPageController extends BasePageController {

    @FXML private Label lblTableBadge;
    @FXML private Label lblIcon;
    @FXML private VBox  waitingCard;
    @FXML private VBox  thankYouCard;
    @FXML private Label lblRestaurantName;

    private RotateTransition spinner;

    /** Đã chuyển sang màn hình cảm ơn chưa — tránh gọi 2 lần. */
    private boolean thankYouShown = false;

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
        thankYouShown = false;

        // Reset: hiện waiting, ẩn thank-you
        waitingCard.setVisible(true);
        waitingCard.setManaged(true);
        thankYouCard.setVisible(false);
        thankYouCard.setManaged(false);

        lblTableBadge.setText("Bàn " + stage.getTableName());
        spinner.play();
    }

    /** Gọi từ WS handler (TableOrderStage) mỗi khi nhận event. */
    public void checkOrderCompleted() {
        if (thankYouShown) return;   // đã hiện rồi, không làm gì thêm

        stage.checkOrderActive(completed -> {
            if (!completed) return;
            if (!TableOrderStage.PAGE_WAITING.equals(stage.getCurrentPage())) return;
            if (thankYouShown) return;
            thankYouShown = true;

            // Dừng poll + spinner
            PollManagerFx.getInstance().unregister("order_status_"  + stage.getTableId());
            PollManagerFx.getInstance().unregister("order_waiting_" + stage.getTableId());
            spinner.stop();

            // Đánh dấu thanh toán hoàn tất — closeWithCleanup() sẽ set bàn → DIRTY
            stage.markPaymentCompleted();

            // Chuyển sang màn hình cảm ơn
            waitingCard.setVisible(false);
            waitingCard.setManaged(false);

            String restaurantName = stage.getRestaurantName();
            lblRestaurantName.setText(restaurantName != null && !restaurantName.isBlank()
                    ? restaurantName : "SmartRestaurant");
            thankYouCard.setVisible(true);
            thankYouCard.setManaged(true);

            // Tự động đóng sau 3 giây
            PauseTransition delay = new PauseTransition(Duration.seconds(3));
            delay.setOnFinished(e -> stage.closeWithCleanup());
            delay.play();
        });
    }

    @FXML
    private void onClose() {
        PollManagerFx.getInstance().unregister("order_status_"  + stage.getTableId());
        PollManagerFx.getInstance().unregister("order_waiting_" + stage.getTableId());
        spinner.stop();
        stage.closeWithCleanup();
    }
}
