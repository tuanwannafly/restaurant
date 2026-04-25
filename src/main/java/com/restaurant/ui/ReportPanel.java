package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.restaurant.dao.ReportDAO;
import com.restaurant.model.Report;
import com.restaurant.session.RbacGuard;
import com.restaurant.ui.dialog.ReportAddDialog;
import com.restaurant.ui.dialog.ReportDetailDialog;

public class ReportPanel extends JPanel {

    private DefaultTableModel tableModel;
    private StyledTable       table;
    private JComboBox<String> cmbFilter;
    private RoundedTextField  searchField;
    private JLabel            loadingLabel;

    private List<Report> allReports = new ArrayList<>();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final boolean isManager    = RbacGuard.getInstance().isManagerOrAbove();
    private final boolean isSuperAdmin = RbacGuard.getInstance().isSuperAdmin();

    private static final String[] COLUMNS_SUPER_ADMIN = {"ID", "Tiêu đề", "Loại", "Mức độ", "Trạng thái", "Ngày tạo", "Nhà hàng"};
    private static final String[] COLUMNS_MANAGER     = {"ID", "Tiêu đề", "Loại", "Mức độ", "Trạng thái", "Ngày tạo", "Người tạo"};
    private static final String[] COLUMNS_STAFF       = {"ID", "Tiêu đề", "Loại", "Mức độ", "Trạng thái", "Ngày tạo"};

    public ReportPanel() {
        setBackground(UIConstants.BG_PAGE);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(24, 48, 24, 48));
        buildUI();
    }

    private void buildUI() {
        // ── TopBar ────────────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JLabel title = new JLabel(isSuperAdmin ? "Báo cáo từ các nhà hàng" : "Báo cáo sự cố");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);

        RoundedButton btnAdd = new RoundedButton("+ Gửi báo cáo");
        btnAdd.setPreferredSize(new Dimension(140, UIConstants.BTN_HEIGHT));
        btnAdd.addActionListener(e -> openAddDialog());
        topBar.add(title, BorderLayout.WEST);
        // SUPER_ADMIN chỉ tiếp nhận và xử lý báo cáo, không gửi mới
        if (!isSuperAdmin) {
            topBar.add(btnAdd, BorderLayout.EAST);
        }

        // ── FilterBar ─────────────────────────────────────────────────────────
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        JLabel lblStatus = new JLabel("Lọc theo trạng thái:");
        lblStatus.setFont(UIConstants.FONT_BODY);

        cmbFilter = new JComboBox<>(new String[]{"Tất cả", "OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"});
        cmbFilter.setFont(UIConstants.FONT_BODY);
        cmbFilter.setPreferredSize(new Dimension(140, 32));
        cmbFilter.addActionListener(e -> applyFilter());

        JLabel lblSearch = new JLabel("Tìm kiếm:");
        lblSearch.setFont(UIConstants.FONT_BODY);

        searchField = new RoundedTextField("Tìm tiêu đề...");
        searchField.setPreferredSize(new Dimension(220, 34));
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { applyFilter(); }
        });

        filterBar.add(lblStatus);
        filterBar.add(cmbFilter);
        filterBar.add(lblSearch);
        filterBar.add(searchField);

        if (!isManager) {
            filterBar.setVisible(false);
        }

        // ── Loading label ─────────────────────────────────────────────────────
        loadingLabel = new JLabel("Đang tải dữ liệu...");
        loadingLabel.setFont(UIConstants.FONT_BODY);
        loadingLabel.setForeground(UIConstants.TEXT_SECONDARY);
        loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loadingLabel.setVisible(false);

        // ── Header panel ──────────────────────────────────────────────────────
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(topBar);
        header.add(filterBar);
        header.add(loadingLabel);

        // ── Table ─────────────────────────────────────────────────────────────
        String[] columns = isSuperAdmin ? COLUMNS_SUPER_ADMIN
                         : isManager   ? COLUMNS_MANAGER
                                       : COLUMNS_STAFF;
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        table.getColumnModel().getColumn(5).setPreferredWidth(130);
        if (isSuperAdmin || isManager) {
            table.getColumnModel().getColumn(6).setPreferredWidth(150);
        }

        // Severity cell renderer
        int severityCol = 3;
        table.getColumnModel().getColumn(severityCol).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                if (!isSelected) {
                    String v = value != null ? value.toString() : "";
                    Color fg = switch (v) {
                        case "Nghiêm trọng" -> Color.decode("#E24B4A");
                        case "Cao"          -> Color.decode("#FB923C");
                        case "Trung bình"   -> Color.decode("#D97706");
                        case "Thấp"         -> Color.decode("#16A34A");
                        default             -> UIConstants.TEXT_PRIMARY;
                    };
                    setForeground(fg);
                    setFont(UIConstants.FONT_BOLD);
                } else {
                    setForeground(Color.WHITE);
                    setFont(UIConstants.FONT_BOLD);
                }
                return this;
            }
        });

        // Status cell renderer
        int statusCol = 4;
        table.getColumnModel().getColumn(statusCol).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                if (!isSelected) {
                    String v = value != null ? value.toString() : "";
                    Color fg = switch (v) {
                        case "Mở"             -> UIConstants.TEXT_SECONDARY;
                        case "Đang xử lý"     -> UIConstants.PRIMARY;
                        case "Đã giải quyết"  -> Color.decode("#16A34A");
                        case "Đã đóng"        -> Color.decode("#9CA3AF");
                        default               -> UIConstants.TEXT_PRIMARY;
                    };
                    setForeground(fg);
                }
                return this;
            }
        });

        // Double-click row → detail dialog
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        openDetailDialog(getReportAtRow(row));
                    }
                }
            }
        });

        add(header, BorderLayout.NORTH);
        add(StyledTable.wrap(table), BorderLayout.CENTER);
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    public void loadData() {
        showLoading(true);
        new SwingWorker<List<Report>, Void>() {
            @Override
            protected List<Report> doInBackground() {
                return new ReportDAO().findByCurrentUser();
            }
            @Override
            protected void done() {
                try {
                    allReports = get();
                    applyFilter();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ReportPanel.this,
                        "Lỗi tải dữ liệu: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                } finally {
                    showLoading(false);
                }
            }
        }.execute();
    }

    private void applyFilter() {
        String statusFilter = cmbFilter != null ? (String) cmbFilter.getSelectedItem() : "Tất cả";
        String search = searchField != null ? searchField.getText().trim().toLowerCase() : "";

        List<Report> filtered = allReports.stream().filter(r -> {
            boolean matchStatus = "Tất cả".equals(statusFilter)
                    || (r.getStatus() != null && r.getStatus().name().equals(statusFilter));
            boolean matchSearch = search.isEmpty()
                    || (r.getTitle() != null && r.getTitle().toLowerCase().contains(search));
            return matchStatus && matchSearch;
        }).collect(Collectors.toList());

        tableModel.setRowCount(0);
        for (Report r : filtered) {
            if (isSuperAdmin) {
                tableModel.addRow(new Object[]{
                    r.getReportId(),
                    r.getTitle(),
                    r.getReportTypeDisplay(),
                    r.getSeverityDisplay(),
                    r.getStatusDisplay(),
                    r.getCreatedAt() != null ? r.getCreatedAt().format(FMT) : "",
                    r.getRestaurantName() != null ? r.getRestaurantName() : "Nhà hàng #" + r.getRestaurantId()
                });
            } else if (isManager) {
                tableModel.addRow(new Object[]{
                    r.getReportId(),
                    r.getTitle(),
                    r.getReportTypeDisplay(),
                    r.getSeverityDisplay(),
                    r.getStatusDisplay(),
                    r.getCreatedAt() != null ? r.getCreatedAt().format(FMT) : "",
                    "User #" + r.getCreatedBy()
                });
            } else {
                tableModel.addRow(new Object[]{
                    r.getReportId(),
                    r.getTitle(),
                    r.getReportTypeDisplay(),
                    r.getSeverityDisplay(),
                    r.getStatusDisplay(),
                    r.getCreatedAt() != null ? r.getCreatedAt().format(FMT) : ""
                });
            }
        }
    }

    private Report getReportAtRow(int row) {
        String statusFilter = cmbFilter != null ? (String) cmbFilter.getSelectedItem() : "Tất cả";
        String search = searchField != null ? searchField.getText().trim().toLowerCase() : "";

        return allReports.stream().filter(r -> {
            boolean matchStatus = "Tất cả".equals(statusFilter)
                    || (r.getStatus() != null && r.getStatus().name().equals(statusFilter));
            boolean matchSearch = search.isEmpty()
                    || (r.getTitle() != null && r.getTitle().toLowerCase().contains(search));
            return matchStatus && matchSearch;
        }).collect(Collectors.toList()).get(row);
    }

    private void showLoading(boolean show) {
        if (loadingLabel != null) loadingLabel.setVisible(show);
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private void openAddDialog() {
        new ReportAddDialog(SwingUtilities.getWindowAncestor(this), this).setVisible(true);
    }

    private void openDetailDialog(Report r) {
        new ReportDetailDialog(SwingUtilities.getWindowAncestor(this), r, this).setVisible(true);
    }
}