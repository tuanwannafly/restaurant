package com.restaurant.ui.fx.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;

import com.restaurant.dao.UserDAO;
import com.restaurant.dao.UserDAO.AdminUser;
import com.restaurant.model.Restaurant;
import com.restaurant.model.Restaurant.Status;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Controller cho RestaurantDialog.fxml.
 *
 * <h2>Vòng đời</h2>
 * <ol>
 *   <li>Load FXML, lấy controller.</li>
 *   <li>Gọi {@link #initCreate()} hoặc {@link #initEdit(Restaurant)}.</li>
 *   <li>{@code stage.showAndWait()}.</li>
 *   <li>Kiểm tra {@link #isSaved()}, rồi lấy {@link #getRestaurant()} /
 *       {@link #getAdminChoice()}.</li>
 * </ol>
 */
public class RestaurantDialogController {

    // ── AdminChoice (inner record) ────────────────────────────────────────────

    /** Kết quả chọn admin khi tạo nhà hàng mới. */
    public static class AdminChoice {
        public enum Mode { SKIP, EXISTING, NEW }

        public final Mode   mode;
        public final long   existingUserId;
        public final String newName;
        public final String newEmail;
        public final String newPassword;

        private AdminChoice(Mode mode, long existingUserId,
                            String newName, String newEmail, String newPassword) {
            this.mode           = mode;
            this.existingUserId = existingUserId;
            this.newName        = newName;
            this.newEmail       = newEmail;
            this.newPassword    = newPassword;
        }

        public static AdminChoice skip()                                    { return new AdminChoice(Mode.SKIP,     0,   null, null, null); }
        public static AdminChoice existing(long uid)                        { return new AdminChoice(Mode.EXISTING, uid, null, null, null); }
        public static AdminChoice create(String n, String e, String p)     { return new AdminChoice(Mode.NEW,      0,   n,    e,    p);    }
    }

    // ── FXML fields ───────────────────────────────────────────────────────────

    @FXML private Label             lblTitle;

    // Logo fields
    @FXML private StackPane         logoContainer;
    @FXML private Label             lblLogoPlaceholder;
    @FXML private ImageView         imgLogoPreview;

    // Restaurant fields
    @FXML private TextField         txtName;
    @FXML private TextField         txtOwner;
    @FXML private TextField         txtEmail;
    @FXML private TextField         txtPhone;
    @FXML private TextField         txtAddress;
    @FXML private TextField         txtCreatedDate;
    @FXML private ComboBox<String>  cboStatus;

    // Admin section
    @FXML private VBox              adminSection;
    @FXML private ToggleGroup       adminGroup;
    @FXML private RadioButton       rbSkip;
    @FXML private RadioButton       rbExisting;
    @FXML private RadioButton       rbNew;
    @FXML private HBox              panelExisting;
    @FXML private ComboBox<AdminUser> cbAdmins;
    @FXML private GridPane          panelNew;
    @FXML private TextField         tfAdminName;
    @FXML private TextField         tfAdminEmail;
    @FXML private PasswordField     pfAdminPassword;

    // ── State ─────────────────────────────────────────────────────────────────

    private Restaurant  editTarget; // null = create mode
    private boolean     saved = false;
    private AdminChoice adminChoice = AdminChoice.skip();
    private String      pendingLogoPath = null; // path được chọn qua FileChooser

    // ── Init methods ──────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        cboStatus.setItems(FXCollections.observableArrayList("Hoạt động", "Vô hiệu hóa"));
        cboStatus.getSelectionModel().selectFirst();

        // Clip tròn bo góc 8px cho logo preview
        Rectangle clip = new Rectangle(80, 80);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        imgLogoPreview.setClip(clip);

        // ComboBox render AdminUser.toString()
        cbAdmins.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AdminUser item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.toString());
            }
        });
        cbAdmins.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(AdminUser item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "(Chưa có admin)" : item.toString());
            }
        });
    }

    /** Chế độ TẠO MỚI — hiển thị admin section, load danh sách admin. */
    public void initCreate() {
        editTarget = null;
        lblTitle.setText("Thêm nhà hàng mới");
        txtCreatedDate.setEditable(false);
        txtCreatedDate.setText(LocalDate.now().toString());
        txtCreatedDate.setStyle("-fx-background-color: #F3F4F6;");

        // Owner is assigned via admin section below — disable this field
        txtOwner.setEditable(false);
        txtOwner.setStyle("-fx-background-color: #F3F4F6;");
        txtOwner.setPromptText("(Chọn admin ở mục bên dưới)");

        showAdminSection(true);
        loadAdminListAsync();
    }

    /** Chế độ CHỈNH SỬA — ẩn admin section, điền data vào form. */
    public void initEdit(Restaurant r) {
        editTarget = r;
        lblTitle.setText("Cập nhật nhà hàng");
        showAdminSection(false);
        fillData(r);
        // txtOwner is display-only in edit mode — load admin name async
        txtOwner.setEditable(false);
        txtOwner.setStyle("-fx-background-color: #F3F4F6;");
        txtOwner.setText("Đang tải...");
        loadOwnerAsync(r.getRestaurantId());
    }

    // ── Result getters ────────────────────────────────────────────────────────

    public boolean     isSaved()      { return saved; }
    public AdminChoice getAdminChoice(){ return adminChoice; }

    /**
     * Trả về Restaurant với dữ liệu từ form.
     * Nếu đang edit, cập nhật vào editTarget; nếu tạo mới, tạo đối tượng mới.
     */
    public Restaurant getRestaurant() {
        Restaurant r = (editTarget != null) ? editTarget : new Restaurant();
        r.setName   (txtName.getText().trim());
        r.setEmail  (txtEmail.getText().trim());
        r.setPhone  (txtPhone.getText().trim());
        r.setAddress(txtAddress.getText().trim());
        r.setStatus ("Hoạt động".equals(cboStatus.getSelectionModel().getSelectedItem())
                ? Status.ACTIVE : Status.INACTIVE);
        // Logo: ưu tiên file mới chọn, fallback về logo cũ nếu đang edit
        if (pendingLogoPath != null) {
            r.setLogoUrl(pendingLogoPath);
        } else if (editTarget != null) {
            r.setLogoUrl(editTarget.getLogoUrl());
        }
        return r;
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void onAdminModeChanged() {
        boolean existingSel = rbExisting.isSelected();
        boolean newSel      = rbNew.isSelected();

        panelExisting.setVisible(existingSel);
        panelExisting.setManaged(existingSel);
        panelNew.setVisible(newSel);
        panelNew.setManaged(newSel);

        // Resize dialog to fit content
        Stage stage = currentStage();
        if (stage != null) stage.sizeToScene();
    }

    @FXML
    private void handleSave() {
        // ── Validate restaurant ───────────────────────────────────────────────
        String name = txtName.getText().trim();
        if (name.isEmpty()) {
            showError("Vui lòng nhập tên nhà hàng!");
            txtName.requestFocus();
            return;
        }

        // ── Validate admin section (chỉ khi tạo mới) ─────────────────────────
        if (editTarget == null) { // create mode
            if (rbExisting.isSelected()) {
                AdminUser sel = cbAdmins.getSelectionModel().getSelectedItem();
                if (sel == null) {
                    showError("Vui lòng chọn admin từ danh sách hoặc chuyển sang tạo mới!");
                    return;
                }
                adminChoice = AdminChoice.existing(sel.getUserId());

            } else if (rbNew.isSelected()) {
                String adminName  = tfAdminName.getText().trim();
                String adminEmail = tfAdminEmail.getText().trim();
                String adminPass  = pfAdminPassword.getText().trim();

                if (adminName.isEmpty()) {
                    showError("Vui lòng nhập họ tên admin!"); tfAdminName.requestFocus(); return;
                }
                if (adminEmail.isEmpty() || !adminEmail.contains("@")) {
                    showError("Vui lòng nhập email hợp lệ cho admin!"); tfAdminEmail.requestFocus(); return;
                }
                if (adminPass.length() < 6) {
                    showError("Mật khẩu admin phải có ít nhất 6 ký tự!"); pfAdminPassword.requestFocus(); return;
                }
                adminChoice = AdminChoice.create(adminName, adminEmail, adminPass);

            } else {
                adminChoice = AdminChoice.skip();
            }
        }

        saved = true;
        currentStage().close();
    }

    @FXML
    private void handleCancel() {
        saved = false;
        currentStage().close();
    }

    // ── Admin list async load ─────────────────────────────────────────────────

    /**
     * Tải tên admin (owner) của nhà hàng theo restaurantId — hiển thị vào txtOwner.
     * Gọi trên background thread để không block JavaFX thread.
     */
    private void loadOwnerAsync(long restaurantId) {
        Task<String> task = new Task<>() {
            @Override protected String call() {
                try {
                    return new UserDAO().findRestaurantAdmins().stream()
                        .filter(a -> a.getRestaurantId() == restaurantId)
                        .map(UserDAO.AdminUser::getName)
                        .findFirst()
                        .orElse("Chưa gán admin");
                } catch (Exception e) {
                    return "(Lỗi tải)";
                }
            }
        };
        task.setOnSucceeded(e -> txtOwner.setText(task.getValue()));
        task.setOnFailed(e -> txtOwner.setText("—"));
        new Thread(task, "RestaurantDialog-ownerLoad").start();
    }

    /**
     * Tải danh sách RESTAURANT_ADMIN trên background thread.
     * Nếu danh sách rỗng, tự chuyển sang mode "Tạo mới".
     */
    private void loadAdminListAsync() {
        Task<List<AdminUser>> task = new Task<>() {
            @Override protected List<AdminUser> call() {
                return new UserDAO().findRestaurantAdmins();
            }
        };
        task.setOnSucceeded(e -> {
            List<AdminUser> admins = task.getValue();
            if (admins.isEmpty()) {
                cbAdmins.setDisable(true);
                cbAdmins.setPromptText("(Chưa có admin — hãy tạo mới)");
                // Tự chuyển sang mode tạo mới nếu đang ở mode existing
                if (rbExisting.isSelected()) {
                    rbNew.setSelected(true);
                    onAdminModeChanged();
                }
            } else {
                cbAdmins.setItems(FXCollections.observableArrayList(admins));
                cbAdmins.getSelectionModel().selectFirst();
            }
        });
        task.setOnFailed(e ->
            Platform.runLater(() ->
                cbAdmins.setPromptText("(Lỗi tải danh sách admin)")));
        new Thread(task, "RestaurantDialog-adminLoad").start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void fillData(Restaurant r) {
        txtName.setText(safe(r.getName()));
        txtEmail.setText(safe(r.getEmail()));
        txtPhone.setText(safe(r.getPhone()));
        txtAddress.setText(safe(r.getAddress()));
        txtCreatedDate.setText(r.getCreatedAt() != null
                ? r.getCreatedAt().toLocalDate().toString() : "");
        cboStatus.getSelectionModel().select(
                r.getStatus() == Status.ACTIVE ? "Hoạt động" : "Vô hiệu hóa");

        // Hiển thị logo hiện tại nếu có
        if (r.getLogoUrl() != null && !r.getLogoUrl().isBlank()) {
            showLogoPreview(r.getLogoUrl());
        }
    }

    private void showAdminSection(boolean show) {
        adminSection.setVisible(show);
        adminSection.setManaged(show);
    }

    // ── Logo helpers ──────────────────────────────────────────────────────────

    /**
     * Mở FileChooser để chọn logo (PNG/JPG/WEBP).
     * Copy file vào uploads/logos/ và lưu path vào {@code pendingLogoPath}.
     * Hiển thị preview ngay sau khi chọn xong.
     */
    @FXML
    private void handlePickLogo() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn logo nhà hàng");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Ảnh (PNG, JPG, WEBP)", "*.png", "*.jpg", "*.jpeg", "*.webp"),
            new FileChooser.ExtensionFilter("Tất cả file", "*.*")
        );

        File chosen = fc.showOpenDialog(currentStage());
        if (chosen == null) return; // user cancelled

        // Kiểm tra kích thước (≤ 5 MB)
        if (chosen.length() > 5L * 1024 * 1024) {
            showError("File quá lớn! Vui lòng chọn ảnh ≤ 5 MB.");
            return;
        }

        // Tạo thư mục uploads/logos/ nếu chưa có
        Path logosDir = Paths.get(System.getProperty("user.dir"), "uploads", "logos");
        try {
            Files.createDirectories(logosDir);
        } catch (IOException e) {
            showError("Không thể tạo thư mục uploads/logos: " + e.getMessage());
            return;
        }

        // Tạo tên file duy nhất bằng timestamp
        String ext = chosen.getName().lastIndexOf('.') > 0
            ? chosen.getName().substring(chosen.getName().lastIndexOf('.'))
            : ".png";
        String uniqueName = "logo_" + System.currentTimeMillis() + ext;
        Path dest = logosDir.resolve(uniqueName);

        try {
            Files.copy(chosen.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            showError("Lỗi copy file: " + e.getMessage());
            return;
        }

        // Lưu absolute path và hiển thị preview
        pendingLogoPath = dest.toAbsolutePath().toString();
        showLogoPreview(pendingLogoPath);
    }

    /**
     * Load ảnh từ {@code filePath} (local path hoặc URL) vào imgLogoPreview
     * và ẩn placeholder emoji.
     */
    private void showLogoPreview(String filePath) {
        try {
            String uri = filePath.startsWith("http://") || filePath.startsWith("https://")
                ? filePath
                : new File(filePath).toURI().toString();
            Image img = new Image(uri, 80, 80, false, true, true);
            img.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 1.0 && !img.isError()) {
                    imgLogoPreview.setImage(img);
                    imgLogoPreview.setVisible(true);
                    imgLogoPreview.setManaged(true);
                    lblLogoPlaceholder.setVisible(false);
                    lblLogoPlaceholder.setManaged(false);
                }
            });
            // Nếu đã load ngay (cached / file local)
            if (img.getProgress() >= 1.0 && !img.isError()) {
                imgLogoPreview.setImage(img);
                imgLogoPreview.setVisible(true);
                imgLogoPreview.setManaged(true);
                lblLogoPlaceholder.setVisible(false);
                lblLogoPlaceholder.setManaged(false);
            }
        } catch (Exception e) {
            System.err.println("[RestaurantDialog] Không load được logo preview: " + e.getMessage());
        }
    }

    private Stage currentStage() {
        // txtName luôn có scene sau khi dialog hiển thị
        if (txtName.getScene() == null) return null;
        return (Stage) txtName.getScene().getWindow();
    }

    private String safe(String s) { return s != null ? s : ""; }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle("Lỗi");
        a.initOwner(currentStage());
        a.showAndWait();
    }
}