package com.restaurant.ui.fx.controller;

import java.io.IOException;
import java.util.List;

import com.restaurant.dao.RestaurantDAO;
import com.restaurant.dao.UserDAO;
import com.restaurant.model.Restaurant;
import com.restaurant.model.Restaurant.Status;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Controller cho RestaurantDetailView.fxml.
 *
 * <h2>Vòng đời</h2>
 * <ol>
 *   <li>Load FXML, lấy controller.</li>
 *   <li>Gọi {@link #setOnBack(Runnable)} để đăng ký callback điều hướng.</li>
 *   <li>Gọi {@link #populate(Restaurant)} để điền dữ liệu.</li>
 * </ol>
 *
 * <p>Tên chủ nhà hàng (owner) được tải bất đồng bộ sau khi {@code populate()}
 * được gọi, tránh block JavaFX Application Thread.
 */
public class RestaurantDetailController {

    // ── FXML fields ───────────────────────────────────────────────────────────

    @FXML private Label  valName;
    @FXML private Label  valOwner;
    @FXML private Label  valEmail;
    @FXML private Label  valPhone;
    @FXML private Label  valAddress;
    @FXML private Label  valCreatedAt;
    @FXML private Label  valStatus;
    @FXML private Button btnEdit;

    // ── Callbacks / state ─────────────────────────────────────────────────────

    private Runnable   onBack;
    private Restaurant currentRestaurant;
    private final RestaurantDAO dao = new RestaurantDAO();

    public void setOnBack(Runnable callback) {
        this.onBack = callback;
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    /**
     * Điền dữ liệu nhà hàng vào các value-label.
     * Tên chủ nhà hàng được load bất đồng bộ để không block EDT.
     *
     * @param r Restaurant cần hiển thị (không được null)
     */
    public void populate(Restaurant r) {
        if (r == null) return;
        this.currentRestaurant = r;

        valName     .setText(safe(r.getName()));
        valOwner    .setText("Đang tải...");
        valOwner    .setStyle("-fx-text-fill: #6B7280;");
        valEmail    .setText(safe(r.getEmail()));
        valPhone    .setText(safe(r.getPhone()));
        valAddress  .setText(safe(r.getAddress()));
        valCreatedAt.setText(r.getCreatedAt() != null
                ? r.getCreatedAt().toLocalDate().toString() : "—");

        // Trạng thái — màu xanh / đỏ
        if (r.getStatus() == Status.ACTIVE) {
            valStatus.setText("Hoạt động");
            valStatus.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
        } else {
            valStatus.setText(r.getStatus() != null ? r.getStatus().label() : "—");
            valStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
        }

        // Load tên chủ nhà hàng bất đồng bộ
        loadOwnerAsync(r.getRestaurantId());
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void handleBack() {
        if (onBack != null) onBack.run();
    }

    /**
     * Mở RestaurantDialog ở chế độ chỉnh sửa.
     * Sau khi lưu, cập nhật lại panel chi tiết với dữ liệu mới.
     */
    @FXML
    private void handleEdit() {
        if (currentRestaurant == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/dialog/RestaurantDialog.fxml"));
            Parent root = loader.load();
            RestaurantDialogController ctrl = loader.getController();
            ctrl.initEdit(currentRestaurant);

            Stage stage = new Stage();
            stage.setTitle("Cập nhật nhà hàng");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(valName.getScene().getWindow());
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();

            if (!ctrl.isSaved()) return;

            // Lưu thay đổi
            Task<Restaurant> saveTask = new Task<>() {
                @Override protected Restaurant call() {
                    dao.update(ctrl.getRestaurant());
                    return dao.findById(currentRestaurant.getRestaurantId());
                }
            };
            saveTask.setOnSucceeded(e -> {
                Restaurant updated = saveTask.getValue();
                if (updated != null) populate(updated);
            });
            saveTask.setOnFailed(e -> {
                Alert a = new Alert(Alert.AlertType.ERROR,
                    "Lỗi cập nhật: " + saveTask.getException().getMessage(), ButtonType.OK);
                a.setTitle("Lỗi"); a.showAndWait();
            });
            new Thread(saveTask, "RestaurantDetail-save").start();

        } catch (IOException ex) {
            Alert a = new Alert(Alert.AlertType.ERROR,
                "Lỗi mở dialog: " + ex.getMessage(), ButtonType.OK);
            a.setTitle("Lỗi"); a.showAndWait();
        }
    }

    // ── Async owner load ──────────────────────────────────────────────────────

    /**
     * Tải tên RESTAURANT_ADMIN của nhà hàng trên background thread.
     * Kết quả được cập nhật vào {@code valOwner} trên JavaFX thread.
     */
    private void loadOwnerAsync(long restaurantId) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                try {
                    List<UserDAO.AdminUser> admins = new UserDAO().findRestaurantAdmins();
                    return admins.stream()
                        .filter(a -> a.getRestaurantId() == restaurantId)
                        .map(UserDAO.AdminUser::getName)
                        .findFirst()
                        .orElse("Chưa gán");
                } catch (Exception e) {
                    return "—";
                }
            }
        };
        task.setOnSucceeded(e -> {
            valOwner.setText(task.getValue());
            valOwner.setStyle("-fx-text-fill: #111827;"); // reset về màu bình thường
        });
        task.setOnFailed(e -> {
            valOwner.setText("—");
            valOwner.setStyle("-fx-text-fill: #6B7280;");
        });
        new Thread(task, "RestaurantDetail-ownerLoad").start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String safe(String s) {
        return (s != null && !s.isBlank()) ? s : "—";
    }
}