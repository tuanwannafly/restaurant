package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import javax.swing.SwingConstants;
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

    // Card names for CardLayout swap
    private static final String CARD_TABLE = "table";
    private static final String CARD_EMPTY = "empty";

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

    /** CardLayout host that swaps between the table scroll pane and empty state. */
    private JPanel    contentPanel;
    private CardLayout contentLayout;

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
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JLabel title = new JLabel("🔐 Nhật ký bảo mật");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);

        topBar.add(title, BorderLayout.WEST);

        // ── PHẦN A — Filter Row 1: action + date range ────────────────────────
        JPanel filterRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterRow1.setOpaque(false);

        JLabel lblAction = new JLabel("Loại hành động:");
        lblAction.setFont(UIConstants.FONT_BODY);
        cmbAction = new JComboBox<>(ACTIONS);
        cmbAction.setFont(UIConstants.FONT_BODY);
        cmbAction.setPreferredSize(new Dimension(160, 32));

        JLabel lblFrom = new JLabel("Từ ngày:");
        lblFrom.setFont(UIConstants.FONT_BODY);
        spinFrom = makeDateSpinner(LocalDate.now().minusDays(7));

        JLabel lblTo = new JLabel("Đến ngày:");
        lblTo.setFont(UIConstants.FONT_BODY);
        spinTo = makeDateSpinner(LocalDate.now().plusDays(1));

        filterRow1.add(lblAction);
        filterRow1.add(cmbAction);
        filterRow1.add(lblFrom);
        filterRow1.add(spinFrom);
        filterRow1.add(lblTo);
        filterRow1.add(spinTo);

        // ── PHẦN A+B+E — Filter Row 2: buttons (left) + countLabel (right) ────
        JPanel filterRow2 = new JPanel(new BorderLayout());
        filterRow2.setOpaque(false);
        filterRow2.setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));

        // PHẦN B — Button panel
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnPanel.setOpaque(false);

        btnRefresh = new RoundedButton("Tải lại");
        btnRefresh.setPreferredSize(new Dimension(100, UIConstants.BTN_HEIGHT));
        btnRefresh.setBackground(UIConstants.PRIMARY);
        btnRefresh.setForeground(UIConstants.TEXT_WHITE);
        btnRefresh.addActionListener(e -> loadData());

        btnExport = new RoundedButton("Xuất CSV");
        btnExport.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnExport.setBackground(UIConstants.SUCCESS);
        btnExport.setForeground(UIConstants.TEXT_WHITE);
        btnExport.addActionListener(e -> exportCsv());

        btnPanel.add(btnRefresh);
        btnPanel.add(btnExport);

        // PHẦN E — Count label pushed to the right
        countLabel = new JLabel("Đang tải...");
        countLabel.setFont(UIConstants.FONT_SMALL);
        countLabel.setForeground(UIConstants.TEXT_SECONDARY);
        countLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        filterRow2.add(btnPanel,    BorderLayout.WEST);
        filterRow2.add(countLabel,  BorderLayout.EAST);

        // ── PHẦN A — filterWrapper combines both rows ─────────────────────────
        JPanel filterWrapper = new JPanel(new BorderLayout());
        filterWrapper.setOpaque(false);
        filterWrapper.add(filterRow1, BorderLayout.NORTH);
        filterWrapper.add(filterRow2, BorderLayout.CENTER);

        // ── Top wrapper ───────────────────────────────────────────────────────
        JPanel topWrapper = new JPanel(new BorderLayout());
        topWrapper.setOpaque(false);
        topWrapper.add(topBar,       BorderLayout.NORTH);
        topWrapper.add(filterWrapper, BorderLayout.CENTER);

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

        int[] widths = {55, 160, 110, 90, 75, 280, 150};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // PHẦN C — Badge renderer for result column
        table.getColumnModel().getColumn(4).setCellRenderer(new ResultCellRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR));
        scroll.getViewport().setBackground(UIConstants.BG_WHITE);

        // ── PHẦN D — Empty state panel ────────────────────────────────────────
        JPanel emptyState = buildEmptyState();

        // ── PHẦN D — CardLayout host swaps table ↔ emptyState ────────────────
        contentLayout = new CardLayout();
        contentPanel  = new JPanel(contentLayout);
        contentPanel.setOpaque(false);
        contentPanel.add(scroll,      CARD_TABLE);
        contentPanel.add(emptyState,  CARD_EMPTY);

        add(topWrapper,   BorderLayout.NORTH);
        add(loadingLabel, BorderLayout.BEFORE_FIRST_LINE); // invisible by default
        add(contentPanel, BorderLayout.CENTER);
    }

    // ── PHẦN D — Empty state builder ─────────────────────────────────────────

    private JPanel buildEmptyState() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel iconLbl = new JLabel("🔍");
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        iconLbl.setAlignmentX(CENTER_ALIGNMENT);

        JLabel msgLbl = new JLabel("Không có bản ghi nào");
        msgLbl.setFont(UIConstants.FONT_TITLE);
        msgLbl.setForeground(UIConstants.TEXT_SECONDARY);
        msgLbl.setAlignmentX(CENTER_ALIGNMENT);

        JLabel hintLbl = new JLabel("Thử thay đổi bộ lọc hoặc khoảng thời gian");
        hintLbl.setFont(UIConstants.FONT_BODY);
        hintLbl.setForeground(UIConstants.TEXT_SECONDARY);
        hintLbl.setAlignmentX(CENTER_ALIGNMENT);

        panel.add(Box.createVerticalGlue());
        panel.add(iconLbl);
        panel.add(Box.createVerticalStrut(12));
        panel.add(msgLbl);
        panel.add(Box.createVerticalStrut(6));
        panel.add(hintLbl);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadData() {
        // PHẦN B — loading state text
        btnRefresh.setEnabled(false);
        btnRefresh.setText("Đang tải...");
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
                    // PHẦN E — updated format
                    countLabel.setText("Hiển thị " + entries.size() + " bản ghi");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(AuditLogPanel.this,
                            "Lỗi tải dữ liệu: " + e.getMessage(),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    countLabel.setText("Lỗi tải dữ liệu");
                } finally {
                    loadingLabel.setVisible(false);
                    // PHẦN B — restore button text
                    btnRefresh.setEnabled(true);
                    btnRefresh.setText("Tải lại");
                }
            }
        };
        worker.execute();
    }

    private void populateTable(List<AuditEntry> entries) {
        tableModel.setRowCount(0);

        // PHẦN D — show empty state when no data
        if (entries.isEmpty()) {
            contentLayout.show(contentPanel, CARD_EMPTY);
            return;
        }

        contentLayout.show(contentPanel, CARD_TABLE);
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

    // ── PHẦN C — Pill badge renderer ─────────────────────────────────────────

    /**
     * Renders a pill-shaped badge for the Kết quả column.
     * Background is painted via fillRoundRect; the label text is replaced
     * with a prefixed version ("✓ SUCCESS", "✗ FAIL", "⚠ LOCKED").
     */
    private static class ResultCellRenderer extends DefaultTableCellRenderer {

        private Color bgColor = Color.WHITE;

        @Override
        public Component getTableCellRendererComponent(
                javax.swing.JTable t, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {

            String v = value != null ? value.toString() : "";

            // Prefix text with visual indicator
            String display = switch (v) {
                case "SUCCESS" -> "✓ SUCCESS";
                case "FAIL"    -> "✗ FAIL";
                case "LOCKED"  -> "⚠ LOCKED";
                default        -> v;
            };

            super.getTableCellRendererComponent(t, display, isSelected, hasFocus, row, column);
            setHorizontalAlignment(CENTER);
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            setOpaque(false); // we paint the background ourselves

            if (isSelected) {
                bgColor = UIConstants.ROW_SELECTED;
                setForeground(UIConstants.TEXT_PRIMARY);
            } else {
                switch (v) {
                    case "SUCCESS" -> { bgColor = Color.decode("#D1FAE5"); setForeground(Color.decode("#065F46")); }
                    case "FAIL"    -> { bgColor = Color.decode("#FEE2E2"); setForeground(Color.decode("#991B1B")); }
                    case "LOCKED"  -> { bgColor = Color.decode("#FEF3C7"); setForeground(Color.decode("#92400E")); }
                    default        -> { bgColor = UIConstants.BG_WHITE;   setForeground(UIConstants.TEXT_PRIMARY); }
                }
            }
            setFont(UIConstants.FONT_BOLD);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Pill shape inset by 2px vertically for breathing room
            g2.setColor(bgColor);
            g2.fillRoundRect(2, 3, getWidth() - 4, getHeight() - 6, 12, 12);
            g2.dispose();
            super.paintComponent(g); // draw text on top
        }
    }
}