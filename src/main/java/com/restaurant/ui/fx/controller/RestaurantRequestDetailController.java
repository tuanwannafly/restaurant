package com.restaurant.ui.fx.controller;

import java.awt.Desktop;
import java.io.File;
import java.time.format.DateTimeFormatter;

import com.restaurant.dao.RestaurantRequestDAO;
import com.restaurant.dao.RestaurantRequestService;
import com.restaurant.model.RestaurantRequest;
import com.restaurant.model.RestaurantRequest.RequestStatus;
import com.restaurant.session.AppSession;
import com.restaurant.ui.dialog.ApproveRequestDialogController;
import com.restaurant.ui.dialog.RejectRequestDialogController;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * RestaurantRequestDetailController — Phase 4.
 * ─────────────────────────────────────────────────────────────────────────────
 * Controller cho {@code RestaurantRequestDetailView.fxml}.
 *
 * <h2>Vòng đời</h2>
 * <ol>
 *   <li>Được tạo và load FXML bởi {@code MainController} một lần duy nhất.</li>
 *   <li>Gọi {@link #setOnBack(Runnable)} để đăng ký callback điều hướng về danh sách.</li>
 *   <li>Gọi {@link #populate(RestaurantRequest)} mỗi lần cần hiển thị chi tiết một đơn.</li>
 * </ol>
 *
 * <h2>Logic Phê duyệt (APPROVE)</h2>
 * <ol>
 *   <li>Mở {@link ApproveRequestDialogController} — admin xác nhận.</li>
 *   <li>Nếu xác nhận: chạy {@link RestaurantRequestService#approveWithTransaction}
 *       trên background {@link Task}.</li>
 *   <li>Transaction Oracle: INSERT restaurants + INSERT users + UPDATE request → COMMIT.</li>
 *   <li>Ghi AuditLog.</li>
 *   <li>Navigate về danh sách và refresh.</li>
 * </ol>
 *
 * <h2>Logic Từ chối (REJECT)</h2>
 * <ol>
 *   <li>Mở {@link RejectRequestDialogController} — admin nhập lý do (bắt buộc).</li>
 *   <li>Nếu có lý do: chạy {@link RestaurantRequestService#reject} trên background Task.</li>
 *   <li>Navigate về danh sách và refresh.</li>
 * </ol>
 *
 * <h2>Thread model</h2>
 * Mọi DB I/O chạy trên daemon Task thread.
 * UI update được route về FX Application Thread qua {@link Platform#runLater}.
 *
 * <p><b>FXML:</b> {@code src/main/resources/fxml/RestaurantRequestDetailView.fxml}
 */
public class RestaurantRequestDetailController {

    // ── Date formatter ─────────────────────────────────────────────────────────
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ── FXML — Labels ──────────────────────────────────────────────────────────

    @FXML private Label    lblStatusBadge;

    // Owner card
    @FXML private Label    valOwnerName;
    @FXML private Label    valOwnerEmail;
    @FXML private Label    valOwnerPhone;
    @FXML private Label    valSubmittedAt;

    // Restaurant card
    @FXML private Label    valRestName;
    @FXML private Label    valRestAddress;
    @FXML private Label    valRestPhone;
    @FXML private Label    valRestEmail;
    @FXML private Label    valRequestId;

    // Attachments
    @FXML private StackPane logoContainer;
    @FXML private ImageView imgLogo;
    @FXML private Label     lblNoLogo;
    @FXML private Label     lblDocumentName;
    @FXML private Button    btnOpenDocument;

    // Action section (PENDING)
    @FXML private VBox      sectionActions;
    @FXML private Button    btnApprove;
    @FXML private Button    btnReject;

    // Reviewed section (APPROVED / REJECTED)
    @FXML private VBox      sectionReviewed;
    @FXML private HBox      reviewedHeader;
    @FXML private Label     lblReviewedIcon;
    @FXML private Label     lblReviewedTitle;
    @FXML private Label     lblReviewedSubtitle;
    @FXML private Label     valReviewedBy;
    @FXML private Label     valReviewedAt;
    @FXML private Label     lblReasonLabel;
    @FXML private Label     valRejectReason;

    // ── State ──────────────────────────────────────────────────────────────────

    private RestaurantRequest currentRequest;

    /** Tên admin đã xử lý — được tải bất đồng bộ. */
    private final com.restaurant.dao.UserDAO userDAO = new com.restaurant.dao.UserDAO();
    private final RestaurantRequestDAO       reqDAO  = new RestaurantRequestDAO();
    private final RestaurantRequestService   service = new RestaurantRequestService();

    // ── Callbacks ──────────────────────────────────────────────────────────────

    /**
     * Callback gọi trên FX thread sau khi admin bấm "Quay lại"
     * hoặc sau khi phê duyệt / từ chối thành công.
     * Được set bởi {@code MainController}.
     */
    private Runnable onBack;

    public void setOnBack(Runnable callback) {
        this.onBack = callback;
    }

    // ── Public API — populate ──────────────────────────────────────────────────

    /**
     * Điền dữ liệu đơn đăng ký vào màn hình chi tiết.
     * Load lại request mới nhất từ DB để đảm bảo dữ liệu cập nhật.
     *
     * @param request đơn cần xem (không được null)
     */
    public void populate(RestaurantRequest request) {
        if (request == null) return;
        this.currentRequest = request;
        renderRequest(request);

        // Load tên admin reviewer bất đồng bộ (nếu đã xử lý)
        if (!request.isPending() && request.getReviewedBy() > 0) {
            loadReviewerNameAsync(request.getReviewedBy());
        }
    }

    /**
     * Reload request từ DB theo ID rồi populate.
     * Dùng sau khi approve/reject để lấy dữ liệu mới nhất.
     *
     * @param requestId request_id cần reload
     */
    public void populateById(long requestId) {
        Task<RestaurantRequest> task = new Task<>() {
            @Override protected RestaurantRequest call() {
                return reqDAO.findById(requestId);
            }
        };
        task.setOnSucceeded(e -> {
            RestaurantRequest r = task.getValue();
            if (r != null) populate(r);
        });
        task.setOnFailed(e ->
            System.err.println("[RestaurantRequestDetailController] Reload lỗi: "
                    + task.getException().getMessage()));
        new Thread(task, "RequestDetail-reload").start();
    }

    // ── Private — render ───────────────────────────────────────────────────────

    private void renderRequest(RestaurantRequest r) {
        // Status badge
        applyStatusBadge(r.getStatus());

        // Owner card
        valOwnerName  .setText(safe(r.getOwnerName()));
        valOwnerEmail .setText(safe(r.getOwnerEmail()));
        valOwnerPhone .setText(safe(r.getOwnerPhone()));
        valSubmittedAt.setText(r.getSubmittedAt() != null
                ? r.getSubmittedAt().format(DT_FMT) : "—");

        // Restaurant card
        valRestName   .setText(safe(r.getRestaurantName()));
        valRestAddress.setText(safe(r.getRestaurantAddress()));
        valRestPhone  .setText(safe(r.getRestaurantPhone()));
        valRestEmail  .setText(safe(r.getRestaurantEmail()));
        valRequestId  .setText("#" + r.getRequestId());

        // Attachments
        renderLogo(r.getLogoPath());
        renderDocument(r.getDocumentPath());

        // Show/hide action vs reviewed sections
        if (r.isPending()) {
            showSection(sectionActions, true);
            showSection(sectionReviewed, false);
        } else {
            showSection(sectionActions, false);
            showSection(sectionReviewed, true);
            renderReviewedSection(r);
        }
    }

    private void applyStatusBadge(RequestStatus status) {
        switch (status) {
            case PENDING -> {
                lblStatusBadge.setText("Chờ duyệt");
                lblStatusBadge.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;"
                        + " -fx-text-fill: #D97706;"
                        + " -fx-background-color: #FEF3C7;"
                        + " -fx-padding: 3 10 3 10; -fx-background-radius: 20;");
            }
            case APPROVED -> {
                lblStatusBadge.setText("Đã duyệt");
                lblStatusBadge.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;"
                        + " -fx-text-fill: #15803D;"
                        + " -fx-background-color: #DCFCE7;"
                        + " -fx-padding: 3 10 3 10; -fx-background-radius: 20;");
            }
            case REJECTED -> {
                lblStatusBadge.setText("Từ chối");
                lblStatusBadge.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;"
                        + " -fx-text-fill: #DC2626;"
                        + " -fx-background-color: #FEE2E2;"
                        + " -fx-padding: 3 10 3 10; -fx-background-radius: 20;");
            }
        }
    }

    private void renderLogo(String logoPath) {
        if (logoPath != null && !logoPath.isBlank()) {
            File logoFile = new File(logoPath);
            if (logoFile.exists() && logoFile.isFile()) {
                try {
                    Image img = new Image(logoFile.toURI().toString(),
                            120, 120, true, true, true);
                    imgLogo.setImage(img);
                    imgLogo.setVisible(true);
                    imgLogo.setManaged(true);
                    lblNoLogo.setVisible(false);
                    lblNoLogo.setManaged(false);
                    return;
                } catch (Exception ex) {
                    System.err.println("[RestaurantRequestDetailController] Lỗi load logo: "
                            + ex.getMessage());
                }
            }
        }
        // No logo or error
        imgLogo.setVisible(false);
        imgLogo.setManaged(false);
        lblNoLogo.setVisible(true);
        lblNoLogo.setManaged(true);
    }

    private void renderDocument(String docPath) {
        if (docPath != null && !docPath.isBlank()) {
            File docFile = new File(docPath);
            lblDocumentName.setText(docFile.getName());
            btnOpenDocument.setDisable(!docFile.exists());
            if (!docFile.exists()) {
                lblDocumentName.setText(docFile.getName() + " (không tìm thấy file)");
                lblDocumentName.setStyle("-fx-font-size: 13px; -fx-text-fill: #DC2626;");
            }
        } else {
            lblDocumentName.setText("Chưa có file đính kèm");
            lblDocumentName.setStyle("-fx-font-size: 13px; -fx-text-fill: #9CA3AF;");
            btnOpenDocument.setDisable(true);
        }
    }

    private void renderReviewedSection(RestaurantRequest r) {
        // Reviewer name — placeholder, async load later
        valReviewedBy.setText(r.getReviewedBy() > 0
                ? "User #" + r.getReviewedBy() + " (đang tải...)" : "—");
        valReviewedAt.setText(r.getReviewedAt() != null
                ? r.getReviewedAt().format(DT_FMT) : "—");

        if (r.isApproved()) {
            // Green header
            reviewedHeader.setStyle(
                    "-fx-background-color: #F0FDF4; -fx-background-radius: 12 12 0 0;"
                    + " -fx-border-color: transparent transparent #BBF7D0 transparent;"
                    + " -fx-border-width: 0 0 1 0;");
            lblReviewedIcon   .setText("✅");
            lblReviewedTitle  .setText("Đơn đã được phê duyệt");
            lblReviewedTitle  .setStyle("-fx-font-size: 15px; -fx-font-weight: bold;"
                    + " -fx-text-fill: #14532D;");
            lblReviewedSubtitle.setText("Tài khoản nhà hàng và nhà hàng đã được tạo tự động");
            lblReviewedSubtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #15803D;");

            // Hide reject-reason row
            setRejectReasonVisible(false);

        } else if (r.isRejected()) {
            // Red header
            reviewedHeader.setStyle(
                    "-fx-background-color: #FFF1F2; -fx-background-radius: 12 12 0 0;"
                    + " -fx-border-color: transparent transparent #FECDD3 transparent;"
                    + " -fx-border-width: 0 0 1 0;");
            lblReviewedIcon   .setText("❌");
            lblReviewedTitle  .setText("Đơn đã bị từ chối");
            lblReviewedTitle  .setStyle("-fx-font-size: 15px; -fx-font-weight: bold;"
                    + " -fx-text-fill: #9F1239;");
            lblReviewedSubtitle.setText("Nhà hàng và tài khoản không được tạo");
            lblReviewedSubtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #BE123C;");

            // Show reject reason
            setRejectReasonVisible(true);
            valRejectReason.setText(safe(r.getRejectReason()));
        }
    }

    private void setRejectReasonVisible(boolean visible) {
        lblReasonLabel .setVisible(visible);
        lblReasonLabel .setManaged(visible);
        valRejectReason.setVisible(visible);
        valRejectReason.setManaged(visible);
    }

    private static void showSection(VBox section, boolean visible) {
        section.setVisible(visible);
        section.setManaged(visible);
    }

    // ── FXML handlers ──────────────────────────────────────────────────────────

    @FXML
    private void handleBack() {
        if (onBack != null) onBack.run();
    }

    /**
     * Mở file tài liệu bằng ứng dụng mặc định của OS ({@link Desktop#open}).
     * Chạy trên background thread — Desktop.open() có thể chặn tạm thời.
     */
    @FXML
    private void handleOpenDocument() {
        if (currentRequest == null) return;
        String docPath = currentRequest.getDocumentPath();
        if (docPath == null || docPath.isBlank()) return;

        File docFile = new File(docPath);
        if (!docFile.exists()) {
            showError("Không tìm thấy file: " + docFile.getAbsolutePath());
            return;
        }

        // Desktop.open() trên background để không block FX thread
        Thread openThread = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(docFile);
                } else {
                    Platform.runLater(() -> showError(
                            "Hệ thống không hỗ trợ mở file tự động.\n"
                            + "Đường dẫn file: " + docFile.getAbsolutePath()));
                }
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Lỗi mở file: " + ex.getMessage()));
            }
        }, "RequestDetail-openDoc");
        openThread.setDaemon(true);
        openThread.start();
    }

    /**
     * Xử lý nút Phê duyệt.
     *
     * <ol>
     *   <li>Mở {@link ApproveRequestDialogController} xác nhận.</li>
     *   <li>Nếu xác nhận: disable buttons + chạy {@link RestaurantRequestService#approveWithTransaction}
     *       trên background Task.</li>
     *   <li>Thành công → navigate về danh sách (gọi {@link #onBack}).</li>
     *   <li>Thất bại → hiển thị Alert lỗi + re-enable buttons.</li>
     * </ol>
     */
    @FXML
    private void handleApprove() {
        if (currentRequest == null) return;

        // Mở dialog xác nhận
        boolean confirmed = ApproveRequestDialogController.show(
                btnApprove.getScene().getWindow(), currentRequest);

        if (!confirmed) return;

        // Disable buttons để tránh double-click
        setActionButtonsDisabled(true);

        long adminUserId = AppSession.getInstance().getUserId();
        RestaurantRequest reqSnapshot = currentRequest; // capture for lambda

        Task<Long> task = new Task<>() {
            @Override
            protected Long call() throws Exception {
                return service.approveWithTransaction(reqSnapshot, adminUserId);
            }
        };

        task.setOnSucceeded(e -> {
            long newRestaurantId = task.getValue();
            showInfo("Phê duyệt thành công",
                    "Nhà hàng #" + newRestaurantId + " và tài khoản RESTAURANT_ADMIN"
                    + "\nđã được tạo cho " + currentRequest.getOwnerName() + ".");
            // Navigate về danh sách — refresh được MainController trigger
            if (onBack != null) onBack.run();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Lỗi không xác định";
            System.err.println("[RestaurantRequestDetailController] Approve thất bại: " + msg);
            showError("Phê duyệt thất bại:\n" + msg);
            setActionButtonsDisabled(false);
        });

        Thread t = new Thread(task, "RequestDetail-approve");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Xử lý nút Từ chối.
     *
     * <ol>
     *   <li>Mở {@link RejectRequestDialogController} để nhập lý do.</li>
     *   <li>Nếu lý do hợp lệ: disable buttons + chạy {@link RestaurantRequestService#reject}
     *       trên background Task.</li>
     *   <li>Thành công → navigate về danh sách.</li>
     * </ol>
     */
    @FXML
    private void handleReject() {
        if (currentRequest == null) return;

        // Mở dialog nhập lý do
        String reason = RejectRequestDialogController.show(
                btnReject.getScene().getWindow(), currentRequest);

        if (reason == null) return; // Admin nhấn Hủy

        // Disable buttons để tránh double-click
        setActionButtonsDisabled(true);

        long adminUserId = AppSession.getInstance().getUserId();
        long requestId   = currentRequest.getRequestId();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                service.reject(requestId, adminUserId, reason);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            showInfo("Từ chối thành công",
                    "Đơn đăng ký #" + requestId + " đã bị từ chối.");
            if (onBack != null) onBack.run();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Lỗi không xác định";
            System.err.println("[RestaurantRequestDetailController] Reject thất bại: " + msg);
            showError("Từ chối thất bại:\n" + msg);
            setActionButtonsDisabled(false);
        });

        Thread t = new Thread(task, "RequestDetail-reject");
        t.setDaemon(true);
        t.start();
    }

    // ── Async reviewer name load ───────────────────────────────────────────────

    /**
     * Tải tên admin đã xét duyệt bất đồng bộ — không block FX thread.
     *
     * @param reviewedByUserId user_id của reviewer
     */
    private void loadReviewerNameAsync(long reviewedByUserId) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                try {
                    // Tìm trong danh sách AdminUser
                    return userDAO.findRestaurantAdmins().stream()
                            .filter(a -> a.getUserId() == reviewedByUserId)
                            .map(a -> a.getName() + " <" + a.getEmail() + ">")
                            .findFirst()
                            // Không phải restaurant admin → thử lấy tên từ session (nếu chính mình)
                            .orElseGet(() -> {
                                AppSession session = AppSession.getInstance();
                                if (session.getUserId() == reviewedByUserId) {
                                    return session.getUserName();
                                }
                                return "Admin #" + reviewedByUserId;
                            });
                } catch (Exception e) {
                    return "Admin #" + reviewedByUserId;
                }
            }
        };
        task.setOnSucceeded(e -> {
            if (valReviewedBy != null) {
                valReviewedBy.setText(task.getValue());
            }
        });
        task.setOnFailed(e -> {/* giữ nguyên placeholder */});
        Thread t = new Thread(task, "RequestDetail-reviewerName");
        t.setDaemon(true);
        t.start();
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private void setActionButtonsDisabled(boolean disabled) {
        if (btnApprove != null) btnApprove.setDisable(disabled);
        if (btnReject  != null) btnReject .setDisable(disabled);
    }

    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private static String safe(String s) {
        return (s != null && !s.isBlank()) ? s : "—";
    }
}