package com.restaurant.ui.dialog;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import com.restaurant.model.MenuItem;
import com.restaurant.ui.ImageLoader;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * MenuDialogController — Phase 4 (JavaFX)
 *
 * Controller for {@code MenuDialog.fxml}.
 * Handles add / edit form for a {@link MenuItem}, including:
 *  - Pre-filling fields when editing.
 *  - Async image picking via {@link ImageLoader}.
 *  - Inline validation with error labels.
 *  - Calling back {@code onSave} consumer on successful submit.
 *
 * Usage:
 * <pre>{@code
 *   new MenuDialog(ownerStage, item, saved -> dao.save(saved)).show();
 * }</pre>
 */
public class MenuDialogController implements Initializable {

    // ── FXML injections ───────────────────────────────────────────────────────

    @FXML private Label       lblTitle;
    @FXML private Button      btnSave;

    @FXML private TextField   tfName;
    @FXML private TextField   tfPrice;
    @FXML private TextField   tfDesc;
    @FXML private ComboBox<String> cbCategory;

    @FXML private Label       errName;
    @FXML private Label       errPrice;

    @FXML private StackPane   imgFrame;
    @FXML private ImageView   imgPreview;
    @FXML private Label       imgPlaceholder;

    // ── State ─────────────────────────────────────────────────────────────────

    private MenuItem         item;           // null → add mode
    private Consumer<MenuItem> onSave;
    private String           imageUrlValue = "";

    // ── Static factory (creates Stage + loads FXML) ───────────────────────────

    /**
     * Create and return a fully configured modal Stage.
     *
     * @param owner  parent window
     * @param item   existing item to edit, or {@code null} for add mode
     * @param onSave callback invoked with the filled {@link MenuItem} when user presses save
     * @return ready-to-show Stage
     */
    public static Stage create(Stage owner, MenuItem item, Consumer<MenuItem> onSave) {
        try {
            FXMLLoader loader = new FXMLLoader(
                MenuDialogController.class.getResource("/fxml/MenuDialog.fxml"));
            javafx.scene.Parent root = loader.load();

            MenuDialogController ctrl = loader.getController();
            ctrl.item   = item;
            ctrl.onSave = onSave;
            ctrl.afterLoad();

            Stage stage = new Stage(StageStyle.UNDECORATED);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(owner);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            return stage;
        } catch (Exception e) {
            throw new RuntimeException("Cannot load MenuDialog.fxml", e);
        }
    }

    // ── Initializable ─────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cbCategory.setItems(FXCollections.observableArrayList(
            "Hải sản", "Thịt", "Cơm", "Phở", "Đồ uống", "Khác"));
        cbCategory.setValue("Hải sản");

        // Style the image frame
        imgFrame.setStyle(
            "-fx-background-color: #F8FAFC;" +
            "-fx-border-color: #CBD5E1;" +
            "-fx-border-style: dashed;" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;");
    }

    /**
     * Called after the controller's fields (item, onSave) have been injected
     * by the static factory. Fills the form when in edit mode.
     */
    private void afterLoad() {
        if (item != null) {
            lblTitle.setText("Cập nhật thông tin món");
            btnSave .setText("Lưu");
            fillData();
        }
    }

    // ── Fill / clear form ─────────────────────────────────────────────────────

    private void fillData() {
        tfName .setText(item.getName());
        tfPrice.setText(String.valueOf((long) item.getPrice()));
        tfDesc .setText(item.getDescription());
        cbCategory.setValue(item.getCategory());

        String url = item.getImageUrl();
        if (url != null && !url.isBlank()) {
            imageUrlValue = url;
            ImageLoader.loadAsync(url, imgPreview);
            imgPlaceholder.setVisible(false);
        }
    }

    // ── Image picker ──────────────────────────────────────────────────────────

    @FXML
    private void onPickImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh món ăn");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                "Image files (*.jpg, *.png, *.webp)",
                "*.jpg", "*.jpeg", "*.png", "*.webp"));

        Stage stage = (Stage) tfName.getScene().getWindow();
        File src = chooser.showOpenDialog(stage);
        if (src == null) return;

        if (src.length() > 5L * 1024 * 1024) {
            showAlert(Alert.AlertType.WARNING,
                "Ảnh quá lớn", "File vượt quá 5 MB. Vui lòng chọn ảnh nhỏ hơn.");
            return;
        }

        try {
            File destDir  = new File("assets/menu_images");
            destDir.mkdirs();
            String fileName = System.currentTimeMillis() + "_" + src.getName();
            File   dest     = new File(destDir, fileName);
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            imageUrlValue = "assets/menu_images/" + fileName;
            ImageLoader.loadAsync(imageUrlValue, imgPreview);
            imgPlaceholder.setVisible(false);
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR,
                "Lỗi", "Không thể copy ảnh: " + ex.getMessage());
        }
    }

    // ── Save / close handlers ─────────────────────────────────────────────────

    @FXML
    private void onSave() {
        if (!validate()) return;

        String name     = tfName.getText().trim();
        double price    = Double.parseDouble(
            tfPrice.getText().trim().replaceAll("[,.]", ""));
        String cat      = cbCategory.getValue();
        String desc     = tfDesc.getText().trim();

        MenuItem saved = item == null
            ? new MenuItem("", name, cat, price, desc)
            : new MenuItem(item.getId(), name, cat, price, desc);
        saved.setImageUrl(imageUrlValue);

        onSave.accept(saved);
        close();
    }

    @FXML
    private void onClose() {
        close();
    }

    private void close() {
        ((Stage) tfName.getScene().getWindow()).close();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private boolean validate() {
        boolean valid = true;

        String name = tfName.getText().trim();
        if (name.isEmpty()) {
            showError(errName, "Vui lòng nhập tên món ăn");
            valid = false;
        } else {
            hideError(errName);
        }

        String priceStr = tfPrice.getText().trim();
        if (priceStr.isEmpty()) {
            showError(errPrice, "Vui lòng nhập giá");
            valid = false;
        } else {
            try {
                double p = Double.parseDouble(priceStr.replaceAll("[,.]", ""));
                if (p <= 0) throw new NumberFormatException();
                hideError(errPrice);
            } catch (NumberFormatException ex) {
                showError(errPrice, "Giá phải là số nguyên dương");
                valid = false;
            }
        }

        return valid;
    }

    private void showError(Label label, String message) {
        label.setText("⚠ " + message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void hideError(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}