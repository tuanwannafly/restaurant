package com.restaurant.ui.dialog;

import java.io.IOException;

import com.restaurant.model.RestaurantRequest;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller for RejectRequestDialog.fxml — Phase 4.
 *
 * <p>Dialog nhập lý do từ chối đơn đăng ký nhà hàng.
 * TextArea {@code taReason} là bắt buộc — không được để trống và phải
 * có ít nhất 10 ký tự có nghĩa.
 *
 * <p><b>Usage (static factory):</b>
 * <pre>{@code
 *   String reason = RejectRequestDialogController.show(owner, request);
 *   if (reason != null) {
 *       // proceed with reject using reason
 *   }
 * }</pre>
 *
 * <p>Trả về {@code null} nếu admin nhấn Hủy hoặc đóng dialog.
 * Trả về lý do (đã trim) nếu xác nhận.
 *
 * <p><b>Thread:</b> Must be called on the JavaFX Application Thread.
 */
public class RejectRequestDialogController {

    // ── FXML ─────────────────────────────────────────────────────────────────

    @FXML private Label    lblRestaurantName;
    @FXML private Label    lblOwnerName;
    @FXML private TextArea taReason;
    @FXML private Label    lblError;

    // ── State ─────────────────────────────────────────────────────────────────

    /** Lý do đã nhập sau khi xác nhận; null nếu hủy. */
    private String reason = null;

    /** Đơn đăng ký đang được xét duyệt (cần để gửi email từ chối). */
    private RestaurantRequest currentRequest = null;

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Mở dialog nhập lý do từ chối và trả về lý do.
     *
     * @param owner   cửa sổ cha (có thể null)
     * @param request đơn đăng ký cần hiển thị thông tin tóm tắt
     * @return lý do từ chối (đã trim) nếu xác nhận; {@code null} nếu hủy
     */
    public static String show(Window owner, RestaurantRequest request) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    RejectRequestDialogController.class.getResource(
                            "/fxml/dialog/RejectRequestDialog.fxml"));
            Parent root = loader.load();

            RejectRequestDialogController ctrl = loader.getController();
            ctrl.initData(request);

            Stage stage = new Stage();
            stage.setTitle("Từ chối đơn đăng ký");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) stage.initOwner(owner);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();

            return ctrl.getReason();

        } catch (IOException ex) {
            System.err.println("[RejectRequestDialogController] Lỗi load FXML: " + ex.getMessage());
            return null;
        }
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    /**
     * Điền thông tin request vào dialog.
     *
     * @param request đơn đăng ký cần hiển thị
     */
    public void initData(RestaurantRequest request) {
        if (request == null) return;
        this.currentRequest = request;
        lblRestaurantName.setText(safe(request.getRestaurantName()));
        lblOwnerName.setText("Chủ: " + safe(request.getOwnerName())
                + " (" + safe(request.getOwnerEmail()) + ")");
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void onConfirm() {
        String input = taReason.getText();

        // Validate — bắt buộc, tối thiểu 10 ký tự
        if (input == null || input.isBlank()) {
            showError("Lý do không được để trống.");
            taReason.requestFocus();
            return;
        }
        if (input.trim().length() < 10) {
            showError("Lý do quá ngắn (tối thiểu 10 ký tự).");
            taReason.requestFocus();
            return;
        }

        // Pass — lưu lý do và đóng
        this.reason = input.trim();

        // ── Gửi email từ chối (fire-and-forget daemon thread) ─────────────────
        // Không block JavaFX thread. Thất bại chỉ log, không ảnh hưởng UI flow.
        if (currentRequest != null) {
            final String finalReason = this.reason;
            final RestaurantRequest req = currentRequest;
            Thread emailThread = new Thread(() -> {
                try {
                    com.restaurant.email.EmailService.getInstance()
                            .sendRestaurantRejectionEmail(
                                    req.getOwnerEmail(),
                                    req.getOwnerName(),
                                    req.getRestaurantName(),
                                    finalReason);
                } catch (Exception e) {
                    System.err.println(
                            "[RejectRequestDialogController] Cảnh báo: gửi email từ chối thất bại"
                            + " cho đơn #" + req.getRequestId()
                            + ": " + e.getMessage());
                }
            });
            emailThread.setDaemon(true);
            emailThread.setName("email-rejection-" + req.getRequestId());
            emailThread.start();
        }

        closeStage();
    }

    @FXML
    private void onCancel() {
        this.reason = null;
        closeStage();
    }

    // ── Accessor ──────────────────────────────────────────────────────────────

    /**
     * Lý do từ chối sau khi dialog đóng.
     * {@code null} nếu người dùng hủy hoặc đóng dialog mà không nhấn xác nhận.
     */
    public String getReason() { return reason; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showError(String message) {
        lblError.setText(message);
        lblError.setVisible(true);
        lblError.setManaged(true);
        taReason.setStyle("-fx-border-color: #DC2626; -fx-border-radius: 6; -fx-background-radius: 6;");
    }

    private void closeStage() {
        Stage stage = (Stage) taReason.getScene().getWindow();
        stage.close();
    }

    private static String safe(String s) {
        return (s != null && !s.isBlank()) ? s : "—";
    }
}