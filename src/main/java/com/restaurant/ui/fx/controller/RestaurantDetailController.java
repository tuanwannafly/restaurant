package com.restaurant.ui.fx.controller;

import java.io.IOException;
import java.util.List;

import com.restaurant.dao.RestaurantDAO;
import com.restaurant.dao.UserDAO;
import com.restaurant.model.Restaurant;
import com.restaurant.model.Restaurant.Status;
import com.restaurant.ui.ImageLoader;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
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

    // Logo fields
    @FXML private Label     lblLogoEmoji;
    @FXML private ImageView imgLogo;

    // ── Callbacks / state ─────────────────────────────────────────────────────

    private Runnable   onBack;
    private Restaurant currentRestaurant;
    private final RestaurantDAO dao = new RestaurantDAO();

    public void setOnBack(Runnable callback) {
        this.onBack = callback;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Clip bo góc 8px cho logo ImageView trong detail view
        Rectangle clip = new Rectangle(52, 52);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        imgLogo.setClip(clip);
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

        // Load logo bất đồng bộ nếu có
        loadLogoAsync(r.getLogoUrl());
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

    // ── Async logo load ───────────────────────────────────────────────────────

    /**
     * Tải logo nhà hàng bất đồng bộ qua {@link ImageLoader}.
     * Nếu {@code logoUrl} rỗng → giữ nguyên emoji placeholder.
     * Nếu có URL/path → ẩn emoji, hiện ImageView với ảnh đã tải.
     */
    private void loadLogoAsync(String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) {
            // Không có logo — giữ emoji
            imgLogo.setVisible(false);
            imgLogo.setManaged(false);
            lblLogoEmoji.setVisible(true);
            lblLogoEmoji.setManaged(true);
            return;
        }

        ImageLoader.loadAsync(logoUrl, img -> {
            imgLogo.setImage(img);
            imgLogo.setVisible(true);
            imgLogo.setManaged(true);
            lblLogoEmoji.setVisible(false);
            lblLogoEmoji.setManaged(false);
        });
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