package com.restaurant.ui.fx.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

import com.restaurant.data.DataManager;
import com.restaurant.model.Restaurant;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;
import com.restaurant.ui.control.CleanField;

/**
 * MyRestaurantController — controller cho MyRestaurantView.fxml.
 *
 * <p>Chức năng:
 * <ul>
 *   <li>Load nhà hàng từ {@link DataManager#getMyRestaurant()} bất đồng bộ.</li>
 *   <li>Populate form fields (tên, địa chỉ, điện thoại, email, mô tả, logo, ngày tạo).</li>
 *   <li>{@link #onSave()} — validate rồi gọi {@link DataManager#updateMyRestaurant(Restaurant)}.</li>
 *   <li>{@link #onPickLogo()} — FileChooser ảnh, copy vào assets/restaurant_logos, preview ngay.</li>
 *   <li>{@link #onReset()} — reset về dữ liệu gốc trước khi sửa.</li>
 * </ul>
 *
 * <p><b>Vị trí:</b> {@code src/main/java/com/restaurant/ui/fx/controller/MyRestaurantController.java}
 */
public class MyRestaurantController {

    // ── FXML injections ───────────────────────────────────────────────────────

    @FXML private Label      lblHeaderName;
    @FXML private Label      lblStatus;
    @FXML private Label      lblMsg;
    @FXML private Button     btnSave;
    @FXML private Button     btnReset;
    @FXML private Button     btnUpload;

    // Info card
    @FXML private CleanField tfName;
    @FXML private CleanField tfAddress;
    @FXML private CleanField tfPhone;
    @FXML private CleanField tfEmail;
    @FXML private TextArea   taDesc;
    @FXML private Label      lblCreatedAt;

    // Logo
    @FXML private StackPane  logoPane;
    @FXML private Label      lblLogoInitials;
    @FXML private ImageView  imgLogo;

    // ── State ─────────────────────────────────────────────────────────────────

    private Restaurant current;
    private String     pendingLogoUrl;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        // Clip logo image thành hình tròn
        Circle clip = new Circle(32, 32, 32);
        imgLogo.setClip(clip);

        // Quyền edit
        boolean canEdit = AppSession.getInstance().hasPermission(Permission.EDIT_OWN_RESTAURANT);
        btnSave.setDisable(!canEdit);
        btnUpload.setDisable(!canEdit);
        logoPane.setDisable(!canEdit);

        loadData();
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    /**
     * Load nhà hàng trong background thread, populate trên JavaFX thread.
     * Gọi khi khởi tạo và có thể gọi lại khi cần refresh.
     */
    public void loadData() {
        showMsg("Đang tải...", "msg-label");
        btnSave.setDisable(true);

        Task<Restaurant> task = new Task<>() {
            @Override
            protected Restaurant call() {
                return DataManager.getInstance().getMyRestaurant();
            }
        };

        task.setOnSucceeded(e -> {
            current = task.getValue();
            if (current == null) {
                showMsg("Không tìm thấy nhà hàng.", "msg-danger");
                return;
            }
            populate(current);
            boolean canEdit = AppSession.getInstance()
                    .hasPermission(Permission.EDIT_OWN_RESTAURANT);
            btnSave.setDisable(!canEdit);
            showMsg("", "msg-label");
        });

        task.setOnFailed(e -> showMsg("Lỗi tải dữ liệu.", "msg-danger"));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ── Populate ──────────────────────────────────────────────────────────────

    /**
     * Điền toàn bộ form từ object {@link Restaurant}.
     * Gọi trên JavaFX Application Thread.
     */
    private void populate(Restaurant r) {
        pendingLogoUrl = null;

        tfName.setText(nv(r.getName()));
        tfAddress.setText(nv(r.getAddress()));
        tfPhone.setText(nv(r.getPhone()));
        tfEmail.setText(nv(r.getEmail()));
        taDesc.setText("");

        lblHeaderName.setText(nv(r.getName()));
        lblCreatedAt.setText(r.getCreatedAt() != null
                ? r.getCreatedAt().format(DATE_FMT) : "—");

        boolean active = r.getStatus() == Restaurant.Status.ACTIVE;
        lblStatus.setText(active ? "● Hoạt động" : "● Tạm dừng");
        lblStatus.getStyleClass().removeAll("badge-success", "badge-warning");
        lblStatus.getStyleClass().add(active ? "badge-success" : "badge-warning");

        // Logo
        if (r.getLogoUrl() != null && !r.getLogoUrl().isBlank()) {
            loadLogoImage(r.getLogoUrl());
        } else {
            // Hiển thị initials
            resetLogoToInitials(r.getName());
        }
    }

    // ── Logo helpers ──────────────────────────────────────────────────────────

    private void loadLogoImage(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) { resetLogoToInitials(current != null ? current.getName() : "NH"); return; }
            Image img = new Image(f.toURI().toString(), 64, 64, false, true);
            imgLogo.setImage(img);
            imgLogo.setVisible(true);
            imgLogo.setManaged(true);
            lblLogoInitials.setVisible(false);
            lblLogoInitials.setManaged(false);
        } catch (Exception ex) {
            resetLogoToInitials(current != null ? current.getName() : "NH");
        }
    }

    private void resetLogoToInitials(String name) {
        imgLogo.setVisible(false);
        imgLogo.setManaged(false);
        lblLogoInitials.setVisible(true);
        lblLogoInitials.setManaged(true);
        lblLogoInitials.setText(initials(name));
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "NH";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Mở FileChooser chọn ảnh logo.
     * Sau khi chọn: copy vào assets/restaurant_logos, preview ngay.
     * URL được giữ trong {@link #pendingLogoUrl} cho đến khi Save.
     */
    @FXML
    private void onPickLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn logo nhà hàng");
        chooser.getExtensionFilters().add(
                new ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png"));

        File src = chooser.showOpenDialog(logoPane.getScene().getWindow());
        if (src == null) return;

        // Kiểm tra kích thước <= 2 MB
        if (src.length() > 2L * 1024 * 1024) {
            showMsg("File vượt quá 2 MB!", "msg-danger");
            return;
        }

        try {
            File destDir = new File("assets/restaurant_logos");
            destDir.mkdirs();
            String fn   = System.currentTimeMillis() + "_" + src.getName();
            File   dest = new File(destDir, fn);
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            pendingLogoUrl = "assets/restaurant_logos/" + fn;

            // Preview ngay
            loadLogoImage(dest.getPath());
            showMsg("Logo sẽ được lưu khi nhấn 'Lưu thay đổi'.", "msg-label");
        } catch (IOException ex) {
            showMsg("Lỗi tải logo: " + ex.getMessage(), "msg-danger");
        }
    }

    /** Đặt lại form về trạng thái ban đầu (trước khi chỉnh sửa). */
    @FXML
    private void onReset() {
        if (current != null) populate(current);
    }

    /**
     * Validate rồi gọi {@link DataManager#updateMyRestaurant(Restaurant)} trong Task.
     */
    @FXML
    private void onSave() {
        if (current == null) return;

        String name = tfName.getText().trim();
        if (name.isEmpty()) {
            showMsg("Tên nhà hàng không được để trống.", "msg-danger");
            tfName.requestFocus();
            return;
        }

        Restaurant updated = new Restaurant(
                current.getRestaurantId(), name,
                tfAddress.getText().trim(),
                tfPhone.getText().trim(),
                tfEmail.getText().trim(),
                current.getStatus(),
                current.getCreatedAt());
        updated.setLogoUrl(pendingLogoUrl != null ? pendingLogoUrl : current.getLogoUrl());

        btnSave.setDisable(true);
        showMsg("Đang lưu...", "msg-label");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                DataManager.getInstance().updateMyRestaurant(updated);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            current = updated;
            DataManager.getInstance().invalidateRestaurantCache();
            pendingLogoUrl = null;
            lblHeaderName.setText(updated.getName());
            showMsg("✓  Đã lưu thành công", "msg-success");
            boolean canEdit = AppSession.getInstance()
                    .hasPermission(Permission.EDIT_OWN_RESTAURANT);
            btnSave.setDisable(!canEdit);
        });

        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            showMsg("Lưu thất bại: " + (cause != null ? cause.getMessage() : "Lỗi không xác định"),
                    "msg-danger");
            boolean canEdit = AppSession.getInstance()
                    .hasPermission(Permission.EDIT_OWN_RESTAURANT);
            btnSave.setDisable(!canEdit);
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Hiển thị message với style class tương ứng.
     *
     * @param text      nội dung (rỗng để ẩn)
     * @param styleClass {@code msg-label | msg-success | msg-danger}
     */
    private void showMsg(String text, String styleClass) {
        Platform.runLater(() -> {
            lblMsg.setText(text);
            lblMsg.getStyleClass().removeAll("msg-label", "msg-success", "msg-danger");
            lblMsg.getStyleClass().add(styleClass);
        });
    }

    private static String nv(String s) {
        return s != null ? s : "";
    }
}