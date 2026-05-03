package com.restaurant.ui.dialog;

import com.restaurant.dao.ReportDAO;
import com.restaurant.model.Report;
import com.restaurant.model.Report.ReportType;
import com.restaurant.model.Report.Severity;
import com.restaurant.ui.ReportController;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

import com.restaurant.ui.fx.controller.ReportController;

/**
 * Controller cho ReportAddDialog.fxml.
 *
 * <p>Validate → gọi {@link ReportDAO#add(Report)} trên background thread →
 * đóng dialog → báo parent controller refresh.
 */
public class ReportAddController implements Initializable {

    @FXML private TextField         txtTitle;
    @FXML private TextArea          txtDescription;
    @FXML private ComboBox<String>  cmbType;
    @FXML private ComboBox<String>  cmbSeverity;
    @FXML private Label             lblError;
    @FXML private Button            btnSubmit;

    /** Set bởi ReportController trước khi show dialog. */
    private ReportController parentController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Loại báo cáo (display → enum)
        cmbType.setItems(FXCollections.observableArrayList(
                "Sự cố", "Bảo trì", "Phản hồi"));
        cmbType.setValue("Sự cố");

        // Mức độ
        cmbSeverity.setItems(FXCollections.observableArrayList(
                "Thấp", "Trung bình", "Cao", "Nghiêm trọng"));
        cmbSeverity.setValue("Thấp");
    }

    public void setParentController(ReportController controller) {
        this.parentController = controller;
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    @FXML
    private void submit() {
        if (!validate()) return;

        Report r = new Report();
        r.setTitle(txtTitle.getText().trim());
        r.setDescription(txtDescription.getText().trim());
        r.setReportType(toReportType(cmbType.getValue()));
        r.setSeverity(toSeverity(cmbSeverity.getValue()));

        btnSubmit.setDisable(true);

        Thread t = new Thread(() -> {
            try {
                new ReportDAO().add(r);
                Platform.runLater(() -> {
                    closeDialog();
                    if (parentController != null) {
                        parentController.loadData();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSubmit.setDisable(false);
                    showError("Lỗi khi gửi báo cáo: " + ex.getMessage());
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void cancel() {
        closeDialog();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean validate() {
        String title = txtTitle.getText() == null ? "" : txtTitle.getText().trim();
        if (title.isEmpty()) {
            showError("Tiêu đề không được để trống.");
            txtTitle.requestFocus();
            return false;
        }
        if (title.length() > 200) {
            showError("Tiêu đề không được vượt quá 200 ký tự.");
            return false;
        }
        hideError();
        return true;
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void hideError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    private void closeDialog() {
        Stage stage = (Stage) btnSubmit.getScene().getWindow();
        stage.close();
    }

    // ── Enum mapping ───────────────────────────────────────────────────────────

    private ReportType toReportType(String display) {
        return switch (display) {
            case "Bảo trì"   -> ReportType.MAINTENANCE;
            case "Phản hồi"  -> ReportType.FEEDBACK;
            default           -> ReportType.INCIDENT;
        };
    }

    private Severity toSeverity(String display) {
        return switch (display) {
            case "Trung bình"   -> Severity.MEDIUM;
            case "Cao"          -> Severity.HIGH;
            case "Nghiêm trọng" -> Severity.CRITICAL;
            default              -> Severity.LOW;
        };
    }
}