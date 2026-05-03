package com.restaurant.ui.dialog;

import com.restaurant.dao.ReportDAO;
import com.restaurant.model.Report;
import com.restaurant.model.Report.Status;
import com.restaurant.session.RbacGuard;
import com.restaurant.ui.ReportController;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

import com.restaurant.ui.fx.controller.ReportController;

/**
 * Controller cho ReportDetailDialog.fxml.
 *
 * <p>Hiển thị chi tiết báo cáo. Manager trở lên có thể đổi trạng thái
 * qua ComboBox + nút "Cập nhật trạng thái". Staff chỉ được xem.
 */
public class ReportDetailController implements Initializable {

    @FXML private Label             lblTitleHeader;
    @FXML private Label             lblSubtitle;
    @FXML private Label             lblSeverityBadge;
    @FXML private Label             lblTitle;
    @FXML private Label             lblType;
    @FXML private Label             lblStatus;
    @FXML private Label             lblCreatedAt;
    @FXML private Label             lblResolvedAt;
    @FXML private TextArea          txtDescription;

    // Manager-only row
    @FXML private Label             lblUpdateRow;
    @FXML private HBox              updateRow;
    @FXML private ComboBox<String>  cmbNewStatus;
    @FXML private Button            btnUpdate;

    @FXML private Label             lblError;

    // ── Dependencies ──────────────────────────────────────────────────────────
    private Report           report;
    private ReportController parentController;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Ẩn khu vực update nếu không phải Manager
        boolean canUpdate = RbacGuard.getInstance().isManagerOrAbove();
        updateRow.setVisible(canUpdate);
        updateRow.setManaged(canUpdate);
        lblUpdateRow.setVisible(canUpdate);
        lblUpdateRow.setManaged(canUpdate);

        if (canUpdate) {
            cmbNewStatus.setItems(FXCollections.observableArrayList(
                    "Đang mở", "Đang xử lý", "Đã giải quyết", "Đã đóng"));
        }
    }

    /** Gọi trước khi show dialog. */
    public void setReport(Report report) {
        this.report = report;
        populateFields();
    }

    public void setParentController(ReportController controller) {
        this.parentController = controller;
    }

    // ── Populate ───────────────────────────────────────────────────────────────

    private void populateFields() {
        lblSubtitle.setText("ID: #" + report.getReportId());
        lblTitle.setText(report.getTitle() != null ? report.getTitle() : "—");
        lblType.setText(report.getReportTypeDisplay() != null ? report.getReportTypeDisplay() : "—");
        txtDescription.setText(report.getDescription() != null ? report.getDescription() : "");

        lblCreatedAt.setText(report.getCreatedAt() != null
                ? report.getCreatedAt().format(FMT) : "—");
        lblResolvedAt.setText(report.getResolvedAt() != null
                ? report.getResolvedAt().format(FMT) : "Chưa giải quyết");

        // Trạng thái hiện tại
        String statusDisplay = report.getStatusDisplay();
        lblStatus.setText(statusDisplay != null ? statusDisplay : "—");
        styleStatusLabel(statusDisplay);

        // Severity badge
        styleSeverityBadge(report.getSeverityDisplay());

        // Pre-select status trong ComboBox
        if (cmbNewStatus != null && report.getStatus() != null) {
            cmbNewStatus.setValue(toDisplayStatus(report.getStatus()));
        }
    }

    private void styleStatusLabel(String display) {
        if (display == null) return;
        String color = switch (display) {
            case "Mở"            -> "#6B7280";
            case "Đang xử lý"    -> "#1D4ED8";
            case "Đã giải quyết" -> "#065F46";
            case "Đã đóng"       -> "#9CA3AF";
            default              -> "#374151";
        };
        lblStatus.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px; " +
                           "-fx-font-weight: bold; -fx-text-fill: " + color + ";");
    }

    private void styleSeverityBadge(String display) {
        if (display == null) { lblSeverityBadge.setVisible(false); return; }
        String bg, fg, emoji;
        switch (display) {
            case "Nghiêm trọng" -> { bg = "#FEE2E2"; fg = "#B91C1C"; emoji = "🔴"; }
            case "Cao"          -> { bg = "#FFEDD5"; fg = "#C2410C"; emoji = "🟠"; }
            case "Trung bình"   -> { bg = "#FEF3C7"; fg = "#92400E"; emoji = "🟡"; }
            default             -> { bg = "#D1FAE5"; fg = "#065F46"; emoji = "🟢"; }
        }
        lblSeverityBadge.setText(emoji + " " + display);
        lblSeverityBadge.setStyle(
                "-fx-font-family: 'Segoe UI'; -fx-font-size: 12px; -fx-font-weight: bold; " +
                "-fx-text-fill: " + fg + "; -fx-background-color: " + bg + "; " +
                "-fx-background-radius: 10; -fx-padding: 4 10 4 10;");
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    @FXML
    private void updateStatus() {
        String selected = cmbNewStatus.getValue();
        if (selected == null) return;

        Status newStatus = toEnumStatus(selected);
        if (newStatus == null) return;

        btnUpdate.setDisable(true);
        hideError();

        Thread t = new Thread(() -> {
            try {
                new ReportDAO().updateStatus(report.getReportId(), newStatus);
                Platform.runLater(() -> {
                    // Cập nhật label hiển thị trên dialog
                    report.setStatus(newStatus);
                    populateFields();
                    btnUpdate.setDisable(false);
                    if (parentController != null) {
                        parentController.loadData();
                    }
                });
            } catch (SecurityException ex) {
                Platform.runLater(() -> {
                    btnUpdate.setDisable(false);
                    showError("Không có quyền: " + ex.getMessage());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnUpdate.setDisable(false);
                    showError("Lỗi cập nhật: " + ex.getMessage());
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void close() {
        Stage stage = (Stage) lblTitle.getScene().getWindow();
        stage.close();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void hideError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    /** Report.Status → display string cho ComboBox */
    private String toDisplayStatus(Status s) {
        return switch (s) {
            case OPEN        -> "Đang mở";
            case IN_PROGRESS -> "Đang xử lý";
            case RESOLVED    -> "Đã giải quyết";
            case CLOSED      -> "Đã đóng";
        };
    }

    /** Display string → Report.Status enum */
    private Status toEnumStatus(String display) {
        return switch (display) {
            case "Đang mở"       -> Status.OPEN;
            case "Đang xử lý"    -> Status.IN_PROGRESS;
            case "Đã giải quyết" -> Status.RESOLVED;
            case "Đã đóng"       -> Status.CLOSED;
            default              -> null;
        };
    }
}