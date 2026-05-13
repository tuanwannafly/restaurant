package com.restaurant.ui.fx.controller;

import com.restaurant.dao.UserDAO;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * TableLoginDialogController — đăng nhập nhanh dành riêng cho bàn ăn.
 *
 * <p>Admin cấp cho mỗi bàn một {@code loginId} (VD: "ban01") và mật khẩu.
 * Controller này nhận thông tin đó, gọi {@link UserDAO#loginAsTable} và
 * thông báo kết quả qua callback {@link #setOnLoginSuccess}.
 */
public class TableLoginDialogController {

    @FXML private TextField     tfLoginId;
    @FXML private PasswordField pfPassword;
    @FXML private Label         errLoginId;
    @FXML private Label         errPassword;
    @FXML private Label         lblError;
    @FXML private Button        btnLogin;

    private Runnable onLoginSuccess;

    // ── Public API ────────────────────────────────────────────────────────────

    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        // Enter on loginId → focus password
        tfLoginId.setOnAction(e -> pfPassword.requestFocus());
        // Enter on password → submit
        pfPassword.setOnAction(e -> onLogin());
    }

    @FXML
    private void onLogin() {
        clearErrors();

        String loginId  = tfLoginId.getText().trim();
        String password = pfPassword.getText();

        if (loginId.isEmpty()) {
            showInline(errLoginId, "Vui lòng nhập mã đăng nhập.");
            tfLoginId.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            showInline(errPassword, "Vui lòng nhập mật khẩu.");
            pfPassword.requestFocus();
            return;
        }

        setLoading(true);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return new UserDAO().loginAsTable(loginId, password);
            }
        };

        task.setOnSucceeded(evt -> {
            setLoading(false);
            if (task.getValue()) {
                closeStage();
                if (onLoginSuccess != null) onLoginSuccess.run();
            } else {
                showBanner("Mã đăng nhập hoặc mật khẩu không đúng.");
            }
        });

        task.setOnFailed(evt -> {
            setLoading(false);
            showBanner("Đã xảy ra lỗi, vui lòng thử lại.");
        });

        new Thread(task, "table-login").start();
    }

    @FXML
    private void onCancel() {
        closeStage();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void clearErrors() {
        hideInline(errLoginId);
        hideInline(errPassword);
        hideBanner();
    }

    private void showInline(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    private void hideInline(Label lbl) {
        lbl.setText("");
        lbl.setVisible(false);
        lbl.setManaged(false);
    }

    private void showBanner(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void hideBanner() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    private void setLoading(boolean loading) {
        btnLogin.setDisable(loading);
        btnLogin.setText(loading ? "Đang xác thực..." : "ĐĂNG NHẬP");
    }

    private void closeStage() {
        Stage stage = (Stage) btnLogin.getScene().getWindow();
        if (stage != null) stage.close();
    }
}
