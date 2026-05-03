package com.restaurant.ui.dialog;

import java.io.IOException;

import com.restaurant.session.OperationTokenService;
import com.restaurant.session.OperationType;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller for ConfirmOperationDialog.fxml.
 *
 * <p>Mirrors the Swing {@code ConfirmOperationDialog} with the same token-verify
 * flow, but using JavaFX {@link Stage#showAndWait()} for blocking modal behaviour.
 *
 * <p>Usage — identical single-call API to the Swing version:
 * <pre>{@code
 *   boolean confirmed = ConfirmOperationDialogController.show(
 *       owner, OperationType.DELETE_EMPLOYEE, targetId);
 *   if (confirmed) dao.delete(id);
 * }</pre>
 *
 * <p><b>Flow inside {@link #show}:</b>
 * <ol>
 *   <li>Issue a token via {@link OperationTokenService#issueToken}.</li>
 *   <li>Load FXML, inject the token + description into the controller.</li>
 *   <li>Show the stage modally ({@code showAndWait}).</li>
 *   <li>Return {@code controller.isConfirmed()}.</li>
 * </ol>
 */
public class ConfirmOperationDialogController {

    // ── FXML ─────────────────────────────────────────────────────────────────

    @FXML private Label     lblDescription;
    @FXML private Label     lblToken;
    @FXML private TextField tfInput;
    @FXML private Label     lblError;
    @FXML private Button    btnConfirm;

    // ── State ─────────────────────────────────────────────────────────────────

    private OperationType type;
    private long          targetId;
    private String        issuedToken;
    private boolean       confirmed = false;

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Issue a token, open the confirm dialog modally, and return the result.
     *
     * <p>Must be called on the JavaFX Application Thread.
     *
     * @param owner    parent window (may be null)
     * @param type     operation type to confirm
     * @param targetId ID of the entity being acted on
     * @return {@code true} if the user entered the correct token
     */
    public static boolean show(Window owner, OperationType type, long targetId) {
        try {
            // Issue token before opening dialog
            String token = OperationTokenService.getInstance().issueToken(type, targetId);

            FXMLLoader loader = new FXMLLoader(
                    ConfirmOperationDialogController.class.getResource("ConfirmOperationDialog.fxml"));
            Parent root = loader.load();

            ConfirmOperationDialogController ctrl = loader.getController();
            ctrl.initData(type, targetId, token);

            Stage stage = new Stage();
            if (owner != null) stage.initOwner(owner);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Xác nhận thao tác");
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();          // blocks until stage.close()

            return ctrl.isConfirmed();

        } catch (IOException e) {
            System.err.println("[ConfirmOperationDialogController] Lỗi tải FXML: " + e.getMessage());
            return false;
        } catch (Exception e) {
            // Token issuance failed — show system error and deny operation
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (owner != null) alert.initOwner(owner);
            alert.setTitle("Lỗi hệ thống");
            alert.setHeaderText("Không thể phát hành mã xác nhận");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
            return false;
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    /**
     * Populate the dialog before it becomes visible.
     * Called by the static factory immediately after FXML load.
     */
    public void initData(OperationType type, long targetId, String token) {
        this.type        = type;
        this.targetId    = targetId;
        this.issuedToken = token;

        lblToken.setText(token);
        lblDescription.setText(
                "Thao tác " + type.getDisplayName() + " không thể hoàn tác.\n\n"
                + "Mã xác nhận của bạn có hiệu lực trong 5 phút:");

        // Auto-focus the input field once the stage is shown
        tfInput.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((obs2, oldWin, newWin) -> {
                    if (newWin != null) {
                        newWin.focusedProperty().addListener((obs3, wasFocused, isFocused) -> {
                            if (isFocused) tfInput.requestFocus();
                        });
                    }
                });
            }
        });
    }

    // ── Public result ─────────────────────────────────────────────────────────

    /** @return {@code true} after the user successfully verified the token. */
    public boolean isConfirmed() { return confirmed; }

    // ── Handlers ─────────────────────────────────────────────────────────────

    @FXML
    private void onConfirm() {
        String input = tfInput.getText().trim();

        if (input.isEmpty()) {
            lblError.setText("Vui lòng nhập mã xác nhận.");
            return;
        }

        boolean valid = OperationTokenService.getInstance()
                .confirmToken(input, type, targetId);

        if (valid) {
            confirmed = true;
            getStage().close();
        } else {
            lblError.setText("Mã không hợp lệ, đã dùng hoặc đã hết hạn. Vui lòng thử lại.");
            tfInput.selectAll();
            tfInput.requestFocus();

            // Pulse the input border red as visual feedback
            tfInput.setStyle(
                    "-fx-font-family:'Monospace';-fx-font-size:18px;-fx-alignment:center;" +
                    "-fx-border-color:#DC3545;-fx-border-radius:6;-fx-background-radius:6;" +
                    "-fx-padding:8 12 8 12;");
            // Reset after a short delay
            new Thread(() -> {
                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                javafx.application.Platform.runLater(() ->
                        tfInput.setStyle(
                                "-fx-font-family:'Monospace';-fx-font-size:18px;-fx-alignment:center;" +
                                "-fx-border-color:#CBD5E1;-fx-border-radius:6;-fx-background-radius:6;" +
                                "-fx-padding:8 12 8 12;"));
            }).start();
        }
    }

    @FXML
    private void onCancel() { getStage().close(); }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Stage getStage() {
        return (Stage) btnConfirm.getScene().getWindow();
    }
}