package com.restaurant.ui.dialog;

import java.io.IOException;

import com.restaurant.model.RestaurantRequest;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller for ApproveRequestDialog.fxml — Phase 4.
 *
 * <p>Dialog xác nhận phê duyệt đơn đăng ký nhà hàng.
 * Thông báo rõ: hệ thống sẽ tự động tạo tài khoản RESTAURANT_ADMIN
 * và bản ghi nhà hàng khi xác nhận.
 *
 * <p><b>Usage (static factory — identical to ConfirmOperationDialogController pattern):</b>
 * <pre>{@code
 *   boolean confirmed = ApproveRequestDialogController.show(owner, request);
 *   if (confirmed) { // proceed with approve }
 * }</pre>
 *
 * <p><b>Thread:</b> Must be called on the JavaFX Application Thread.
 */
public class ApproveRequestDialogController {

    // ── FXML ─────────────────────────────────────────────────────────────────

    @FXML private Label lblRestaurantName;
    @FXML private Label lblOwnerName;

    // ── State ─────────────────────────────────────────────────────────────────

    private boolean confirmed = false;

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Mở dialog xác nhận phê duyệt và trả về kết quả.
     *
     * @param owner   cửa sổ cha (có thể null)
     * @param request đơn đăng ký cần hiển thị thông tin tóm tắt
     * @return {@code true} nếu admin xác nhận phê duyệt
     */
    public static boolean show(Window owner, RestaurantRequest request) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    ApproveRequestDialogController.class.getResource(
                            "/fxml/dialog/ApproveRequestDialog.fxml"));
            Parent root = loader.load();

            ApproveRequestDialogController ctrl = loader.getController();
            ctrl.initData(request);

            Stage stage = new Stage();
            stage.setTitle("Xác nhận phê duyệt");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) stage.initOwner(owner);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();

            return ctrl.isConfirmed();

        } catch (IOException ex) {
            System.err.println("[ApproveRequestDialogController] Lỗi load FXML: " + ex.getMessage());
            return false;
        }
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    /**
     * Điền thông tin request vào dialog để admin xem trước khi xác nhận.
     *
     * @param request đơn đăng ký cần hiển thị
     */
    public void initData(RestaurantRequest request) {
        if (request == null) return;
        lblRestaurantName.setText(safe(request.getRestaurantName()));
        lblOwnerName.setText("Chủ: " + safe(request.getOwnerName())
                + " (" + safe(request.getOwnerEmail()) + ")");
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void onConfirm() {
        confirmed = true;
        closeStage();
    }

    @FXML
    private void onCancel() {
        confirmed = false;
        closeStage();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isConfirmed() { return confirmed; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void closeStage() {
        Stage stage = (Stage) lblRestaurantName.getScene().getWindow();
        stage.close();
    }

    private static String safe(String s) {
        return (s != null && !s.isBlank()) ? s : "—";
    }
}