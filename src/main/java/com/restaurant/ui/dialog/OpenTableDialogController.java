package com.restaurant.ui.dialog;

import com.restaurant.dao.OrderDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.TableItem;
import com.restaurant.session.AppSession;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * OpenTableDialogController — Phase 5
 *
 * <p>Controller của {@code OpenTableDialog.fxml}.
 *
 * <p><b>Luồng khi xác nhận:</b>
 * <ol>
 *   <li>{@link OrderDAO#createEmptyOrder} — tạo đơn hàng rỗng trong DB.</li>
 *   <li>{@link TableDAO#updateStatus} — chuyển bàn sang {@code BAN}.</li>
 *   <li>Set {@code confirmed = true}, đóng Stage.</li>
 * </ol>
 *
 * <p>Nếu lỗi DB, dialog giữ nguyên và hiện {@link Alert}.
 *
 * <p><b>Cách dùng từ TableController:</b>
 * <pre>{@code
 *   FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/OpenTableDialog.fxml"));
 *   Parent root = loader.load();
 *   OpenTableDialogController ctrl = loader.getController();
 *   ctrl.init(tableId, tableName);
 *   stage.showAndWait();
 *   if (ctrl.isConfirmed()) { ... }
 * }</pre>
 */
public class OpenTableDialogController {

    // ─── FXML fields ──────────────────────────────────────────────────────────

    @FXML private Label     lblTitle;
    @FXML private TextField tfName;
    @FXML private TextField tfPhone;

    // ─── State ────────────────────────────────────────────────────────────────

    private String  tableId;
    private long    restaurantId;
    private boolean confirmed = false;

    // ─── Init (called by TableController after load) ──────────────────────────

    /**
     * Khởi tạo dialog với thông tin bàn.
     * Phải gọi trước {@code stage.showAndWait()}.
     *
     * @param tableId   khóa chính của bàn
     * @param tableName tên bàn hiển thị trên tiêu đề
     */
    public void init(String tableId, String tableName) {
        this.tableId      = tableId;
        this.restaurantId = AppSession.getInstance().getRestaurantId();
        lblTitle.setText("Mở bàn: " + tableName);
    }

    // ─── FXML handlers ────────────────────────────────────────────────────────

    @FXML
    private void onCancel() {
        close();
    }

    /**
     * Xác nhận mở bàn:
     * <ol>
     *   <li>Tạo đơn rỗng trên background thread.</li>
     *   <li>Cập nhật status bàn → BAN.</li>
     *   <li>Set {@code confirmed = true} và đóng stage.</li>
     * </ol>
     */
    @FXML
    private void onConfirm() {
        String name  = tfName.getText().trim();
        String phone = tfPhone.getText().trim();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                new OrderDAO().createEmptyOrder(
                        tableId,
                        restaurantId,
                        name.isEmpty()  ? null : name,
                        phone.isEmpty() ? null : phone
                );
                new TableDAO().updateStatus(tableId, TableItem.Status.BAN);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            confirmed = true;
            close();
        });

        task.setOnFailed(e -> {
            String msg = task.getException() != null
                    ? task.getException().getMessage()
                    : "Lỗi không xác định";
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "Lỗi mở bàn: " + msg, ButtonType.OK);
                alert.setHeaderText("Lỗi");
                alert.initOwner(getStage());
                alert.showAndWait();
            });
        });

        new Thread(task, "open-table").start();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** @return {@code true} nếu người dùng đã xác nhận và DB thành công. */
    public boolean isConfirmed() {
        return confirmed;
    }

    /** @return tên khách (có thể rỗng). */
    public String getCustomerName() {
        return tfName.getText().trim();
    }

    /** @return số điện thoại (có thể rỗng). */
    public String getCustomerPhone() {
        return tfPhone.getText().trim();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Stage getStage() {
        return (Stage) tfName.getScene().getWindow();
    }

    private void close() {
        getStage().close();
    }
}