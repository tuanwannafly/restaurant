package com.restaurant.ui.dialog;

import com.restaurant.model.TableItem;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.security.SecureRandom;

/**
 * TableDialogController — Thêm/sửa bàn kèm thông tin tài khoản TABLET.
 *
 * <ul>
 *   <li>Add mode: sinh tự động loginId + password, cho phép admin chỉnh.</li>
 *   <li>Edit mode: loginId hiển thị (readonly), password bỏ trống = giữ nguyên.</li>
 * </ul>
 */
public class TableDialogController {

    @FXML private Label            lblTitle;
    @FXML private TextField        tfName;
    @FXML private TextField        tfCapacity;
    @FXML private ComboBox<String> cbStatus;
    @FXML private Label            errName;
    @FXML private Label            errCapacity;

    // Credentials
    @FXML private TextField tfLoginId;
    @FXML private TextField tfPassword;
    @FXML private Label     lblPasswordHint;
    @FXML private Label     errLoginId;
    @FXML private Label     errPassword;

    private TableItem item;          // null → add mode
    private TableItem result;
    private boolean   saved = false;

    // Existing loginId (edit mode) — to detect changes
    private String existingLoginId;

    @FXML
    private void initialize() {
        cbStatus.getItems().addAll("RANH", "DAT_TRUOC", "DIRTY", "CLEANING");
        cbStatus.setValue("RANH");
    }

    /** Called by TableController after FXMLLoader.load(). */
    public void setItem(TableItem item) {
        this.item = item;
        if (item != null) {
            lblTitle.setText("Cập nhật bàn");
            tfName.setText(item.getName());
            tfCapacity.setText(String.valueOf(item.getCapacity()));
            if (item.getStatus() != null) cbStatus.setValue(item.getStatus().name());

            // Edit mode: show hint that blank password = keep existing
            lblPasswordHint.setText("Mật khẩu mới (để trống = giữ nguyên)");
        } else {
            lblTitle.setText("Thêm bàn mới");
            // Auto-generate default credentials for new table
            autoGenLoginId();
            autoGenPassword();
        }
    }

    /** Prefill existing loginId in edit mode (called from TableController). */
    public void setExistingLoginId(String loginId) {
        this.existingLoginId = loginId;
        if (loginId != null) {
            tfLoginId.setText(loginId);
        }
    }

    // ── Result getters ────────────────────────────────────────────────────────

    public boolean   isSaved()      { return saved; }
    public TableItem getResult()    { return result; }
    public String    getLoginId()   { return tfLoginId.getText().trim().toLowerCase(); }
    public String    getPassword()  { return tfPassword.getText().trim(); }

    // ── Auto-generate ─────────────────────────────────────────────────────────

    @FXML
    private void onAutoGenLoginId() {
        autoGenLoginId();
    }

    @FXML
    private void onAutoGenPassword() {
        autoGenPassword();
    }

    private void autoGenLoginId() {
        // Use name if available, else random
        String base = tfName.getText().trim();
        if (base.isEmpty()) {
            tfLoginId.setText("ban" + (int)(Math.random() * 900 + 100));
        } else {
            // Normalize: remove diacritics roughly, lowercase, keep alphanumeric
            String sanitized = removeDiacritics(base)
                    .replaceAll("[^a-zA-Z0-9]", "")
                    .toLowerCase();
            if (sanitized.isEmpty()) sanitized = "ban";
            tfLoginId.setText(sanitized);
        }
    }

    private void autoGenPassword() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        tfPassword.setText(sb.toString());
    }

    /** Very simple diacritic stripper for Vietnamese table names. */
    private String removeDiacritics(String s) {
        return s.replace("à","a").replace("á","a").replace("ả","a").replace("ã","a").replace("ạ","a")
                .replace("ă","a").replace("â","a").replace("è","e").replace("é","e").replace("ê","e")
                .replace("ì","i").replace("í","i").replace("ò","o").replace("ó","o").replace("ô","o")
                .replace("ơ","o").replace("ù","u").replace("ú","u").replace("ư","u").replace("ỳ","y")
                .replace("ý","y").replace("đ","d")
                // uppercase
                .replace("À","A").replace("Á","A").replace("Ả","A").replace("Ã","A").replace("Ạ","A")
                .replace("Ă","A").replace("Â","A").replace("È","E").replace("É","E").replace("Ê","E")
                .replace("Ì","I").replace("Í","I").replace("Ò","O").replace("Ó","O").replace("Ô","O")
                .replace("Ơ","O").replace("Ù","U").replace("Ú","U").replace("Ư","U")
                .replace("Đ","D");
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @FXML
    private void onSave() {
        clearErrors();

        String name   = tfName.getText().trim();
        String capStr = tfCapacity.getText().trim();
        String loginId  = tfLoginId.getText().trim();
        String password = tfPassword.getText().trim();

        boolean valid = true;

        if (name.isEmpty()) {
            errName.setText("Tên bàn không được trống.");
            valid = false;
        }
        int capacity = 0;
        try {
            capacity = Integer.parseInt(capStr);
            if (capacity <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            errCapacity.setText("Sức chứa phải là số nguyên dương.");
            valid = false;
        }
        if (loginId.isEmpty()) {
            errLoginId.setText("Mã đăng nhập không được trống.");
            valid = false;
        } else if (loginId.contains("@") || loginId.contains(" ")) {
            errLoginId.setText("Mã đăng nhập không được chứa '@' hay khoảng trắng.");
            valid = false;
        }
        // Password required for new tables; optional for edit
        if (item == null && password.length() < 6) {
            errPassword.setText("Mật khẩu phải có ít nhất 6 ký tự.");
            valid = false;
        } else if (item != null && !password.isEmpty() && password.length() < 6) {
            errPassword.setText("Mật khẩu mới phải có ít nhất 6 ký tự.");
            valid = false;
        }

        if (!valid) return;

        TableItem.Status status = TableItem.Status.valueOf(cbStatus.getValue());
        String id = (item != null) ? item.getId() : null;
        result = new TableItem(id, name, capacity, status);
        saved  = true;
        close();
    }

    @FXML
    private void onCancel() { close(); }

    private void close() {
        ((Stage) tfName.getScene().getWindow()).close();
    }

    private void clearErrors() {
        errName.setText("");
        errCapacity.setText("");
        errLoginId.setText("");
        errPassword.setText("");
    }
}
