package com.restaurant.ui.fx.controller;

// 📁 VỊ TRÍ: src/main/java/com/restaurant/ui/fx/controller/AuditLogController.java

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.restaurant.session.AuditLogger;
import com.restaurant.session.AuditLogger.AuditEntry;
import com.restaurant.session.RbacGuard;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

/**
 * Controller cho AuditLogView.fxml.
 * <p>
 * Tương đương {@code AuditLogPanel} (Swing) — chỉ dành cho SUPER_ADMIN.
 * <ul>
 *   <li>TableView hiển thị audit log với badge cell cho cột Kết quả.</li>
 *   <li>Bộ lọc theo action và khoảng ngày.</li>
 *   <li>Export toàn bộ kết quả ra file CSV (UTF-8 BOM cho Excel).</li>
 * </ul>
 */
public class AuditLogController {

    // ── Hằng số ───────────────────────────────────────────────────────────────

    private static final String[] ACTION_OPTIONS = {
        "Tất cả", "LOGIN", "LOGOUT", "CHANGE_PASSWORD", "CHANGE_ROLE",
        "DELETE_EMPLOYEE", "DELETE_RESTAURANT", "ACCOUNT_LOCKED"
    };

    private static final DateTimeFormatter DT_FMT  =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter CSV_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── FXML injections ───────────────────────────────────────────────────────

    @FXML private ComboBox<String> cmbAction;
    @FXML private DatePicker       dpFrom;
    @FXML private DatePicker       dpTo;

    @FXML private Button btnRefresh;
    @FXML private Button btnExport;
    @FXML private Label  lblCount;

    // TableView + Columns
    @FXML private TableView<AuditEntry>              tableView;
    @FXML private TableColumn<AuditEntry, Long>      colId;
    @FXML private TableColumn<AuditEntry, String>    colAction;
    @FXML private TableColumn<AuditEntry, String>    colActor;
    @FXML private TableColumn<AuditEntry, String>    colTarget;
    @FXML private TableColumn<AuditEntry, String>    colResult;
    @FXML private TableColumn<AuditEntry, String>    colDetail;
    @FXML private TableColumn<AuditEntry, String>    colTime;

    // Empty-state overlay
    @FXML private VBox emptyState;

    // ═════════════════════════════════════════════════════════════════════════
    // Khởi tạo
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        // ── Guard quyền ───────────────────────────────────────────────────────
        if (!RbacGuard.getInstance().isSuperAdmin()) {
            tableView.setPlaceholder(new Label(
                "⛔ Chỉ SUPER_ADMIN mới có quyền xem nhật ký bảo mật."));
            btnRefresh.setDisable(true);
            btnExport.setDisable(true);
            return;
        }

        // ── ComboBox actions ──────────────────────────────────────────────────
        cmbAction.setItems(FXCollections.observableArrayList(ACTION_OPTIONS));
        cmbAction.getSelectionModel().selectFirst();

        // ── Mặc định date filter ──────────────────────────────────────────────
        dpFrom.setValue(LocalDate.now().minusDays(7));
        dpTo.setValue(LocalDate.now().plusDays(1));

        // ── TableView columns (value factories) ───────────────────────────────
        colId.setCellValueFactory(c ->
            new ReadOnlyObjectWrapper<>(c.getValue().logId));

        colAction.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(c.getValue().action));

        colActor.setCellValueFactory(c -> {
            Long actor = c.getValue().actorUserId;
            return new ReadOnlyStringWrapper(actor != null ? String.valueOf(actor) : "—");
        });

        colTarget.setCellValueFactory(c -> {
            Long target = c.getValue().targetId;
            return new ReadOnlyStringWrapper(target != null ? String.valueOf(target) : "—");
        });

        colResult.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(c.getValue().result));

        // ── PHẦN C — pill badge cell ──────────────────────────────────────────
        colResult.setCellFactory(col -> new ResultBadgeTableCell());

        colDetail.setCellValueFactory(c -> {
            String d = c.getValue().detail;
            return new ReadOnlyStringWrapper(d != null ? d : "");
        });

        colTime.setCellValueFactory(c -> {
            LocalDateTime ldt = c.getValue().loggedAt;
            return new ReadOnlyStringWrapper(ldt != null ? ldt.format(DT_FMT) : "—");
        });

        // ── Ẩn empty state ban đầu ────────────────────────────────────────────
        setEmptyState(false);

        // ── Auto-load ─────────────────────────────────────────────────────────
        loadData();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FXML event handlers
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onRefresh() {
        loadData();
    }

    @FXML
    private void onExportCsv() {
        exportCsv();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Data loading
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Tải danh sách audit log theo bộ lọc hiện tại, chạy trên daemon thread.
     * Cập nhật TableView + count label trên FX thread.
     */
    private void loadData() {
        String        actionFilter = buildActionFilter();
        LocalDateTime from         = toStartOfDay(dpFrom.getValue());
        LocalDateTime to           = toEndOfDay(dpTo.getValue());

        // Loading state
        btnRefresh.setDisable(true);
        btnRefresh.setText("⏳ Đang tải...");
        lblCount.setText("Đang tải...");

        Task<List<AuditEntry>> task = new Task<>() {
            @Override
            protected List<AuditEntry> call() {
                return AuditLogger.getInstance()
                        .getFilteredLogs(actionFilter, from, to, 500);
            }
        };

        task.setOnSucceeded(e -> {
            List<AuditEntry> entries = task.getValue();
            populateTable(entries);
            lblCount.setText("Hiển thị " + entries.size() + " bản ghi");
            btnRefresh.setDisable(false);
            btnRefresh.setText("🔄 Tải lại");
        });

        task.setOnFailed(e -> {
            showError("Lỗi tải dữ liệu: " + task.getException().getMessage());
            lblCount.setText("Lỗi tải dữ liệu");
            btnRefresh.setDisable(false);
            btnRefresh.setText("🔄 Tải lại");
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /** Nạp entries vào TableView; ẩn/hiện empty-state phù hợp. */
    private void populateTable(List<AuditEntry> entries) {
        if (entries.isEmpty()) {
            tableView.setItems(FXCollections.emptyObservableList());
            setEmptyState(true);
        } else {
            tableView.setItems(FXCollections.observableArrayList(entries));
            setEmptyState(false);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CSV Export
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Mở FileChooser, sau đó xuất toàn bộ log (tối đa 10,000 bản ghi)
     * ra file CSV UTF-8 BOM (tương thích Excel).
     */
    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu Audit Log CSV");
        chooser.getExtensionFilters().add(
            new ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("audit_log_"
            + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv");

        // getScene().getWindow() để hiển thị dialog đúng parent window
        File file = chooser.showSaveDialog(tableView.getScene().getWindow());
        if (file == null) return;

        final File finalFile = file.getName().toLowerCase().endsWith(".csv")
            ? file : new File(file.getPath() + ".csv");

        btnExport.setDisable(true);

        String        actionFilter = buildActionFilter();
        LocalDateTime from         = toStartOfDay(dpFrom.getValue());
        LocalDateTime to           = toEndOfDay(dpTo.getValue());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<AuditEntry> entries = AuditLogger.getInstance()
                        .getFilteredLogs(actionFilter, from, to, 10_000);

                try (PrintWriter pw = new PrintWriter(
                        new FileWriter(finalFile, java.nio.charset.StandardCharsets.UTF_8))) {

                    // BOM cho Excel UTF-8
                    pw.print('\uFEFF');
                    pw.println(
                        "log_id,action,actor_user_id,target_id,"
                        + "session_token,op_token,result,detail,logged_at");

                    for (AuditEntry e : entries) {
                        pw.printf("%d,%s,%s,%s,%s,%s,%s,\"%s\",%s%n",
                            e.logId,
                            csvEscape(e.action),
                            e.actorUserId  != null ? e.actorUserId  : "",
                            e.targetId     != null ? e.targetId     : "",
                            e.sessionToken != null ? csvEscape(e.sessionToken) : "",
                            e.opToken      != null ? csvEscape(e.opToken)      : "",
                            csvEscape(e.result),
                            e.detail != null ? e.detail.replace("\"", "\"\"") : "",
                            e.loggedAt != null ? e.loggedAt.format(CSV_FMT) : "");
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            btnExport.setDisable(false);
            showInfo("Xuất CSV thành công:\n" + finalFile.getAbsolutePath());
        });

        task.setOnFailed(e -> {
            btnExport.setDisable(false);
            showError("Lỗi xuất CSV: " + task.getException().getMessage());
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    /** Trả về filter action hoặc null nếu chọn "Tất cả". */
    private String buildActionFilter() {
        String sel = cmbAction.getValue();
        return (sel == null || "Tất cả".equals(sel)) ? null : sel;
    }

    /** Hiện/ẩn VBox empty-state và TableView. */
    private void setEmptyState(boolean show) {
        emptyState.setVisible(show);
        emptyState.setManaged(show);
        tableView.setVisible(!show);
        tableView.setManaged(!show);
    }

    private static LocalDateTime toStartOfDay(LocalDate d) {
        return d != null ? d.atStartOfDay() : null;
    }

    private static LocalDateTime toEndOfDay(LocalDate d) {
        return d != null ? d.atTime(23, 59, 59) : null;
    }

    /** Escape CSV field: bao trong dấu ngoặc kép nếu chứa dấu phẩy, ngoặc, hoặc newline. */
    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private void showError(String msg) {
        Alert a = new Alert(AlertType.ERROR, msg);
        a.setHeaderText("Lỗi");
        a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(AlertType.INFORMATION, msg);
        a.setHeaderText("Thành công");
        a.showAndWait();
    }
}
