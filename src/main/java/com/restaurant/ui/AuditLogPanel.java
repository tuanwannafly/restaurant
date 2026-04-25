package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.restaurant.session.AuditLogger;
import com.restaurant.session.AuditLogger.AuditEntry;
import com.restaurant.session.RbacGuard;

/**
 * Panel hiển thị nhật ký kiểm toán bảo mật — chỉ dành cho SUPER_ADMIN.
 * <p>
 * Tính năng:
 * <ul>
 *   <li>JTable hiển thị danh sách audit log (action, actor, target, result, thời gian, chi tiết)</li>
 *   <li>Bộ lọc theo action và khoảng thời gian</li>
 *   <li>Nút "Tải lại" để refresh</li>
 *   <li>Nút "Xuất CSV" export ra file</li>
 * </ul>
 */
public class AuditLogPanel extends JPanel {

    // ── Hằng số ───────────────────────────────────────────────────────────────

    private static final String[] ACTIONS = {
        "Tất cả", "LOGIN", "LOGOUT", "CHANGE_PASSWORD", "CHANGE_ROLE",
        "DELETE_EMPLOYEE", "DELETE_RESTAURANT", "ACCOUNT_LOCKED"
    };

    private static final String[] COLUMNS = {
        "ID", "Hành động", "Actor (User ID)", "Target ID",
        "Kết quả", "Chi tiết", "Thời gian"
    };

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final DateTimeFormatter CSV_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── Components ────────────────────────────────────────────────────────────

    private DefaultTableModel tableModel;
    private StyledTable       table;
    private JLabel            loadingLabel;
    private JLabel            countLabel;
    private JComboBox<String> cmbAction;
    private JSpinner          spinFrom;
    private JSpinner          spinTo;
    private JButton           btnRefresh;
    private JButton           btnExport;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AuditLogPanel() {
        if (!RbacGuard.getInstance().isSuperAdmin()) {
            setLayout(new BorderLayout());
            JLabel denied = new JLabel("⛔ Chỉ SUPER_ADMIN mới có quyền xem nhật ký bảo mật.",
                    JLabel.CENTER);
            denied.setFont(UIConstants.FONT_BODY);
            denied.setForeground(UIConstants.DANGER);
            add(denied, BorderLayout.CENTER);
            return;
        }

        setBackground(UIConstants.BG_PAGE);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(24, 48, 24, 48));
        buildUI();
        loadData();
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private void buildUI() {
        // ── Tiêu đề ───────────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        JLabel title = new JLabel("🔐 Nhật ký bảo mật");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);

        countLabel = new JLabel("Đang tải...");
        countLabel.setFont(UIConstants.FONT_SMALL);
        countLabel.setForeground(UIConstants.TEXT_SECONDARY);

        topBar.add(title,      BorderLayout.WEST);
        topBar.add(countLabel, BorderLayout.EAST);

        // ── Filter bar ────────────────────────────────────────────────────────
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        // Lọc action
        JLabel lblAction = new JLabel("Loại hành động:");
        lblAction.setFont(UIConstants.FONT_BODY);
        cmbAction = new JComboBox<>(ACTIONS);
        cmbAction.setFont(UIConstants.FONT_BODY);
        cmbAction.setPreferredSize(new Dimension(160, 32));

        // Lọc ngày từ - đến
        JLabel lblFrom = new JLabel("Từ ngày:");
        lblFrom.setFont(UIConstants.FONT_BODY);
        spinFrom = makeDateSpinner(LocalDate.now().minusDays(7));

        JLabel lblTo = new JLabel("Đến ngày:");
        lblTo.setFont(UIConstants.FONT_BODY);
        spinTo = makeDateSpinner(LocalDate.now().plusDays(1));

        btnRefresh = new RoundedButton("🔄 Tải lại");
        btnRefresh.setPreferredSize(new Dimension(100, UIConstants.BTN_HEIGHT));
        btnRefresh.setBackground(UIConstants.PRIMARY);
        btnRefresh.setForeground(UIConstants.TEXT_WHITE);
        btnRefresh.addActionListener(e -> loadData());

        btnExport = new RoundedButton("📥 Xuất CSV");
        btnExport.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnExport.setBackground(UIConstants.SUCCESS);
        btnExport.setForeground(UIConstants.TEXT_WHITE);
        btnExport.addActionListener(e -> exportCsv());

        filterBar.add(lblAction);
        filterBar.add(cmbAction);
        filterBar.add(lblFrom);
        filterBar.add(spinFrom);
        filterBar.add(lblTo);
        filterBar.add(spinTo);
        filterBar.add(btnRefresh);
        filterBar.add(btnExport);

        // ── Top wrapper ───────────────────────────────────────────────────────
        JPanel topWrapper = new JPanel(new BorderLayout());
        topWrapper.setOpaque(false);
        topWrapper.add(topBar,    BorderLayout.NORTH);
        topWrapper.add(filterBar, BorderLayout.CENTER);

        // ── Loading label ─────────────────────────────────────────────────────
        loadingLabel = new JLabel("Đang tải dữ liệu...", JLabel.CENTER);
        loadingLabel.setFont(UIConstants.FONT_BODY);
        loadingLabel.setForeground(UIConstants.TEXT_SECONDARY);
        loadingLabel.setVisible(false);

        // ── Table ─────────────────────────────────────────────────────────────
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        table.setRowHeight(UIConstants.ROW_HEIGHT);
        table.getTableHeader().setFont(UIConstants.FONT_HEADER);
        table.getTableHeader().setBackground(UIConstants.HEADER_BG);
        table.getTableHeader().setForeground(UIConstants.TEXT_PRIMARY);
        table.setFont(UIConstants.FONT_BODY);

        // Column widths
        int[] widths = {55, 160, 110, 90, 75, 280, 150};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Màu kết quả
        table.getColumnModel().getColumn(4).setCellRenderer(new ResultCellRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR));
        scroll.getViewport().setBackground(UIConstants.BG_WHITE);

        add(topWrapper, BorderLayout.NORTH);
        add(loadingLabel, BorderLayout.CENTER);
        add(scroll, BorderLayout.CENTER);
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadData() {
        btnRefresh.setEnabled(false);
        loadingLabel.setVisible(true);
        countLabel.setText("Đang tải...");

        String actionFilter = buildActionFilter();
        LocalDateTime from  = toLocalDateTime(spinFrom, false);
        LocalDateTime to    = toLocalDateTime(spinTo, true);

        SwingWorker<List<AuditEntry>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<AuditEntry> doInBackground() {
                return AuditLogger.getInstance().getFilteredLogs(actionFilter, from, to, 500);
            }

            @Override
            protected void done() {
                try {
                    List<AuditEntry> entries = get();
                    populateTable(entries);
                    countLabel.setText("Tổng: " + entries.size() + " bản ghi");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(AuditLogPanel.this,
                            "Lỗi tải dữ liệu: " + e.getMessage(),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    countLabel.setText("Lỗi tải dữ liệu");
                } finally {
                    loadingLabel.setVisible(false);
                    btnRefresh.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void populateTable(List<AuditEntry> entries) {
        tableModel.setRowCount(0);
        for (AuditEntry e : entries) {
            tableModel.addRow(new Object[]{
                e.logId,
                e.action,
                e.actorUserId != null ? e.actorUserId : "—",
                e.targetId    != null ? e.targetId    : "—",
                e.result,
                e.detail != null ? e.detail : "",
                e.loggedAt != null ? e.loggedAt.format(DT_FMT) : "—"
            });
        }
    }

    // ── CSV Export ────────────────────────────────────────────────────────────

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("audit_log_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv"));
        int res = chooser.showSaveDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getPath() + ".csv");
        }
        final File finalFile = file;

        btnExport.setEnabled(false);
        String actionFilter = buildActionFilter();
        LocalDateTime from  = toLocalDateTime(spinFrom, false);
        LocalDateTime to    = toLocalDateTime(spinTo, true);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                List<AuditEntry> entries =
                        AuditLogger.getInstance().getFilteredLogs(actionFilter, from, to, 10000);
                try (PrintWriter pw = new PrintWriter(new FileWriter(finalFile, java.nio.charset.StandardCharsets.UTF_8))) {
                    // BOM cho Excel UTF-8
                    pw.print('\uFEFF');
                    pw.println("log_id,action,actor_user_id,target_id,session_token,op_token,result,detail,logged_at");
                    for (AuditEntry e : entries) {
                        pw.printf("%d,%s,%s,%s,%s,%s,%s,\"%s\",%s%n",
                                e.logId,
                                csvEscape(e.action),
                                e.actorUserId != null ? e.actorUserId : "",
                                e.targetId    != null ? e.targetId    : "",
                                e.sessionToken != null ? csvEscape(e.sessionToken) : "",
                                e.opToken      != null ? csvEscape(e.opToken)      : "",
                                csvEscape(e.result),
                                e.detail != null ? e.detail.replace("\"", "\"\"") : "",
                                e.loggedAt != null ? e.loggedAt.format(CSV_FMT) : "");
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                btnExport.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(AuditLogPanel.this,
                            "Xuất CSV thành công:\n" + finalFile.getAbsolutePath(),
                            "Thành công", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(AuditLogPanel.this,
                            "Lỗi xuất CSV: " + e.getMessage(),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildActionFilter() {
        String sel = (String) cmbAction.getSelectedItem();
        return (sel == null || "Tất cả".equals(sel)) ? null : sel;
    }

    private LocalDateTime toLocalDateTime(JSpinner spinner, boolean endOfDay) {
        Object val = spinner.getValue();
        if (val instanceof java.util.Date d) {
            LocalDateTime ldt = d.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
            return endOfDay ? ldt.withHour(23).withMinute(59).withSecond(59) : ldt.withHour(0).withMinute(0).withSecond(0);
        }
        return null;
    }

    private JSpinner makeDateSpinner(LocalDate defaultDate) {
        java.util.Date initDate = java.util.Date.from(
                defaultDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
        SpinnerModel model = new SpinnerDateModel(initDate, null, null, java.util.Calendar.DAY_OF_MONTH);
        JSpinner spinner = new JSpinner(model);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "dd/MM/yyyy");
        spinner.setEditor(editor);
        spinner.setPreferredSize(new Dimension(110, 32));
        spinner.setFont(UIConstants.FONT_BODY);
        return spinner;
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    // ── Custom cell renderer ──────────────────────────────────────────────────

    private static class ResultCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                javax.swing.JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String v = value != null ? value.toString() : "";
            if (!isSelected) {
                switch (v) {
                    case "SUCCESS" -> { setBackground(new Color(0xD1FAE5)); setForeground(new Color(0x065F46)); }
                    case "FAIL"    -> { setBackground(UIConstants.DANGER_LIGHT); setForeground(UIConstants.DANGER); }
                    case "LOCKED" -> { setBackground(new Color(0xFEF3C7)); setForeground(new Color(0x92400E)); }
                    default        -> { setBackground(UIConstants.BG_WHITE);   setForeground(UIConstants.TEXT_PRIMARY); }
                }
            }
            setHorizontalAlignment(CENTER);
            return this;
        }
    }
}