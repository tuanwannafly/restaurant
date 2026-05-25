package com.restaurant.ui.fx.controller;

import com.restaurant.ui.TableOrderStage;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

/**
 * PaymentPageController — Phase 15.
 *
 * <p>Cho phép khách chọn phương thức thanh toán (chuyển khoản / tiền mặt)
 * và gửi yêu cầu thanh toán.
 */
public class PaymentPageController extends BasePageController {

    @FXML private Label         lblTableBadge;
    @FXML private Label         lblTotal;
    @FXML private ToggleButton  tbTransfer;
    @FXML private ToggleButton  tbCash;
    @FXML private VBox          cashPanel;
    @FXML private TextField     tfCashAmount;
    @FXML private Button        btnSubmit;

    private String selectedMethod = "transfer";

    /** Tổng đơn hiện tại — cập nhật bởi syncTotal(). 0 = chưa có món. */
    private double currentTotal = 0;

    @FXML
    private void initialize() {
        ToggleGroup tg = new ToggleGroup();
        tbTransfer.setToggleGroup(tg);
        tbCash.setToggleGroup(tg);
        tbTransfer.setSelected(true);

        tbTransfer.setOnAction(e -> {
            selectedMethod = "transfer";
            cashPanel.setVisible(false);
            cashPanel.setManaged(false);
            updateToggleStyle();
        });
        tbCash.setOnAction(e -> {
            selectedMethod = "cash";
            cashPanel.setVisible(true);
            cashPanel.setManaged(true);
            updateToggleStyle();
        });

        cashPanel.setVisible(false);
        cashPanel.setManaged(false);
        updateToggleStyle();
    }

    @Override
    public void onNavigatedTo() {
        lblTableBadge.setText("Bàn " + stage.getTableName());
        syncTotal();
        tbTransfer.setSelected(true);
        selectedMethod = "transfer";
        cashPanel.setVisible(false);
        cashPanel.setManaged(false);
        updateToggleStyle();
    }

    private void syncTotal() {
        stage.loadOrderItems(items -> {
            // [FIX] Chỉ tính các món chưa bị hủy — CANCELLED không được cộng vào tổng
            currentTotal = items.stream()
                    .filter(i -> i.getItemStatus()
                            != com.restaurant.model.Order.OrderItem.ItemStatus.CANCELLED)
                    .mapToDouble(com.restaurant.model.Order.OrderItem::getSubtotal)
                    .sum();
            lblTotal.setText("Tổng cộng: " + fmt(currentTotal) + " đ");
            if (btnSubmit != null) {
                btnSubmit.setDisable(currentTotal <= 0);
            }
        });
    }

    private void updateToggleStyle() {
        applyToggle(tbTransfer, tbTransfer.isSelected());
        applyToggle(tbCash,     tbCash.isSelected());
    }

    private static void applyToggle(ToggleButton btn, boolean selected) {
        if (selected) {
            btn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white;" +
                         "-fx-font-weight: bold; -fx-background-radius: 8;");
        } else {
            btn.setStyle("-fx-background-color: white; -fx-text-fill: #3B82F6;" +
                         "-fx-border-color: #3B82F6; -fx-border-radius: 8;" +
                         "-fx-background-radius: 8;");
        }
    }

    @FXML
    private void onBack() {
        stage.navigateTo(TableOrderStage.PAGE_STATUS);
    }

    @FXML
    private void onSubmit() {
        if (currentTotal <= 0) {
            // Không cho phép gửi yêu cầu thanh toán nếu đơn trống
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING,
                    "Đơn hàng chưa có món nào.\nVui lòng chọn món trước khi thanh toán.",
                    javafx.scene.control.ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }
        String cash = tfCashAmount.getText().trim();
        stage.submitPaymentRequest(selectedMethod, cash);
        stage.navigateTo(TableOrderStage.PAGE_WAITING);
    }
}