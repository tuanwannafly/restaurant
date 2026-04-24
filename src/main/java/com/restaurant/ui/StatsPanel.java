package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import com.restaurant.dao.StatsDAO;

/**
 * Panel thống kê doanh thu & trạng thái bàn — chỉ RESTAURANT_ADMIN+ thấy.
 */
public class StatsPanel extends JPanel {

    // ── Date filter fields ────────────────────────────────────────────────────
    private JTextField  txtFrom;
    private JTextField  txtTo;
    private LocalDate   dateFrom = LocalDate.now().minusDays(30);
    private LocalDate   dateTo   = LocalDate.now();

    // ── Section 1 — Doanh thu ─────────────────────────────────────────────────
    private JLabel lblRevenue;
    private JLabel lblOrderCount;
    private JLabel lblAvgOrder;

    // ── Section 2 — Top 5 món ─────────────────────────────────────────────────
    private DefaultTableModel topItemsModel;

    // ── Section 3 — Trạng thái bàn ───────────────────────────────────────────
    private JLabel        lblAvailable;
    private JLabel        lblOccupied;
    private JLabel        lblReserved;
    private JProgressBar  progressTable;

    // ── Status ────────────────────────────────────────────────────────────────
    private JLabel lblStatus;

    // ── DAO ───────────────────────────────────────────────────────────────────
    private final StatsDAO dao = new StatsDAO();

    // ═════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═════════════════════════════════════════════════════════════════════════

    public StatsPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BG_PAGE);
        buildUI();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Build UI
    // ═════════════════════════════════════════════════════════════════════════

    private void buildUI() {
        // ── NORTH: TopBar ─────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(24, 48, 12, 48));

        JLabel title = new JLabel("Thống kê & Doanh thu");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topRight.setOpaque(false);

        lblStatus = new JLabel("");
        lblStatus.setFont(UIConstants.FONT_BODY);
        lblStatus.setForeground(UIConstants.TEXT_SECONDARY);

        RoundedButton btnRefresh = new RoundedButton("↻  Làm mới");
        btnRefresh.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnRefresh.addActionListener(e -> loadAll());

        topRight.add(lblStatus);
        topRight.add(btnRefresh);

        topBar.add(title,    BorderLayout.WEST);
        topBar.add(topRight, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // ── CENTER: Scrollable content ────────────────────────────────────────
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(UIConstants.BG_PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(0, 48, 24, 48));

        // Date filter
        content.add(buildDateFilter());
        content.add(Box.createVerticalStrut(20));

        // Section 1 — Doanh thu
        content.add(buildSectionHeader("📊  Doanh thu"));
        content.add(Box.createVerticalStrut(10));
        content.add(buildRevenueCards());
        content.add(Box.createVerticalStrut(24));

        // Section 2 — Top 5 món
        content.add(buildSectionHeader("🍽️  Top 5 món bán chạy"));
        content.add(Box.createVerticalStrut(10));
        content.add(buildTopItemsTable());
        content.add(Box.createVerticalStrut(24));

        // Section 3 — Trạng thái bàn
        content.add(buildSectionHeader("🪑  Trạng thái bàn hiện tại"));
        content.add(Box.createVerticalStrut(10));
        content.add(buildTableStats());
        content.add(Box.createVerticalStrut(8));
        content.add(buildProgressBar());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBackground(UIConstants.BG_PAGE);
        scrollPane.getViewport().setBackground(UIConstants.BG_PAGE);
        add(scrollPane, BorderLayout.CENTER);
    }

    // ── Date filter ───────────────────────────────────────────────────────────

    private JPanel buildDateFilter() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        JLabel lblFrom = new JLabel("Từ ngày:");
        lblFrom.setFont(UIConstants.FONT_BODY);
        lblFrom.setForeground(UIConstants.TEXT_PRIMARY);

        txtFrom = new JTextField(dateFrom.toString(), 10);
        txtFrom.setFont(UIConstants.FONT_BODY);
        txtFrom.setToolTipText("yyyy-MM-dd");

        JLabel lblTo = new JLabel("Đến ngày:");
        lblTo.setFont(UIConstants.FONT_BODY);
        lblTo.setForeground(UIConstants.TEXT_PRIMARY);

        txtTo = new JTextField(dateTo.toString(), 10);
        txtTo.setFont(UIConstants.FONT_BODY);
        txtTo.setToolTipText("yyyy-MM-dd");

        RoundedButton btnApply = new RoundedButton("Áp dụng");
        btnApply.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnApply.addActionListener(e -> applyDateFilter());

        panel.add(lblFrom);
        panel.add(txtFrom);
        panel.add(lblTo);
        panel.add(txtTo);
        panel.add(btnApply);

        return panel;
    }

    private void applyDateFilter() {
        try {
            LocalDate from = LocalDate.parse(txtFrom.getText().trim());
            LocalDate to   = LocalDate.parse(txtTo.getText().trim());
            if (from.isAfter(to)) {
                JOptionPane.showMessageDialog(this,
                    "Ngày bắt đầu phải trước ngày kết thúc",
                    "Lỗi ngày", JOptionPane.WARNING_MESSAGE);
                return;
            }
            dateFrom = from;
            dateTo   = to;
            loadAll();
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this,
                "Định dạng ngày không hợp lệ. Vui lòng nhập theo dạng yyyy-MM-dd",
                "Lỗi định dạng", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ── Section header ────────────────────────────────────────────────────────

    private JLabel buildSectionHeader(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        return lbl;
    }

    // ── Section 1: Revenue cards ──────────────────────────────────────────────

    private JPanel buildRevenueCards() {
        JPanel row = new JPanel(new GridLayout(1, 3, 16, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        row.setAlignmentX(LEFT_ALIGNMENT);

        lblRevenue    = new JLabel("0 đ");
        lblOrderCount = new JLabel("0 đơn");
        lblAvgOrder   = new JLabel("0 đ");

        row.add(buildMetricCard("Tổng doanh thu",    lblRevenue));
        row.add(buildMetricCard("Số đơn hoàn thành", lblOrderCount));
        row.add(buildMetricCard("Trung bình / đơn",  lblAvgOrder));

        return row;
    }

    // ── Section 2: Top items table ────────────────────────────────────────────

    private JPanel buildTopItemsTable() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        topItemsModel = new DefaultTableModel(
                new String[]{"Hạng", "Tên món", "Số lượng", "Doanh thu (đ)"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        StyledTable table = new StyledTable(topItemsModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(280);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);

        wrapper.add(StyledTable.wrap(table), BorderLayout.CENTER);
        return wrapper;
    }

    // ── Section 3: Table status cards ────────────────────────────────────────

    private JPanel buildTableStats() {
        JPanel row = new JPanel(new GridLayout(1, 3, 16, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        row.setAlignmentX(LEFT_ALIGNMENT);

        lblAvailable = new JLabel("0");
        lblOccupied  = new JLabel("0");
        lblReserved  = new JLabel("0");

        row.add(buildMetricCard("Bàn trống",      lblAvailable));
        row.add(buildMetricCard("Đang phục vụ",   lblOccupied));
        row.add(buildMetricCard("Đặt trước",       lblReserved));

        return row;
    }

    private JPanel buildProgressBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        panel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lbl = new JLabel("Tỷ lệ sử dụng bàn:");
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_SECONDARY);

        progressTable = new JProgressBar(0, 100);
        progressTable.setStringPainted(true);
        progressTable.setString("0/0 bàn đang dùng");
        progressTable.setFont(UIConstants.FONT_BODY);
        progressTable.setForeground(UIConstants.PRIMARY);
        progressTable.setBackground(UIConstants.BG_PAGE);
        progressTable.setPreferredSize(new Dimension(0, 24));

        panel.add(lbl,           BorderLayout.WEST);
        panel.add(progressTable, BorderLayout.CENTER);
        return panel;
    }

    // ── Metric card helper ────────────────────────────────────────────────────

    private JPanel buildMetricCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(UIConstants.BG_WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(UIConstants.FONT_BODY);
        lblTitle.setForeground(UIConstants.TEXT_SECONDARY);
        lblTitle.setAlignmentX(LEFT_ALIGNMENT);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        valueLabel.setForeground(UIConstants.PRIMARY);
        valueLabel.setAlignmentX(LEFT_ALIGNMENT);

        card.add(lblTitle);
        card.add(Box.createVerticalStrut(8));
        card.add(valueLabel);

        return card;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Data loading
    // ═════════════════════════════════════════════════════════════════════════

    /** Inner bundle để truyền kết quả từ worker về EDT. */
    private static class StatBundle {
        StatsDAO.RevenueStats revenue;
        List<StatsDAO.TopItem> topItems;
        StatsDAO.TableStats   tables;
    }

    public void loadAll() {
        showLoading(true);

        new SwingWorker<StatBundle, Void>() {
            @Override
            protected StatBundle doInBackground() {
                StatBundle bundle = new StatBundle();
                bundle.revenue  = dao.getRevenue(dateFrom, dateTo);
                bundle.topItems = dao.getTopItems(dateFrom, dateTo, 5);
                bundle.tables   = dao.getTableStats();
                return bundle;
            }

            @Override
            protected void done() {
                try {
                    StatBundle bundle = get();
                    updateRevenueUI(bundle.revenue);
                    updateTopItemsUI(bundle.topItems);
                    updateTableStatsUI(bundle.tables);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    JOptionPane.showMessageDialog(StatsPanel.this,
                        "Tải thống kê bị gián đoạn",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(StatsPanel.this,
                        "Lỗi tải thống kê: " + cause.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                } finally {
                    showLoading(false);
                }
            }
        }.execute();
    }

    // ── UI updaters (run on EDT via done()) ───────────────────────────────────

    private void updateRevenueUI(StatsDAO.RevenueStats stats) {
        lblRevenue.setText(formatVnd(stats.totalRevenue));
        lblOrderCount.setText(stats.orderCount + " đơn");
        lblAvgOrder.setText(formatVnd(stats.avgPerOrder));
    }

    private void updateTopItemsUI(List<StatsDAO.TopItem> items) {
        topItemsModel.setRowCount(0);
        int rank = 1;
        for (StatsDAO.TopItem item : items) {
            topItemsModel.addRow(new Object[]{
                rank++,
                item.itemName,
                item.totalQty,
                formatVnd(item.totalRevenue)
            });
        }
    }

    private void updateTableStatsUI(StatsDAO.TableStats ts) {
        lblAvailable.setText(String.valueOf(ts.available));
        lblOccupied.setText(String.valueOf(ts.occupied));
        lblReserved.setText(String.valueOf(ts.reserved));

        int pct = ts.total > 0 ? (ts.occupied * 100 / ts.total) : 0;
        progressTable.setValue(pct);
        progressTable.setString(ts.occupied + "/" + ts.total + " bàn đang dùng");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        lblStatus.setText(show ? "Đang tải..." : "");
    }

    private String formatVnd(long value) {
        return String.format("%,.0f đ", (double) value);
    }
}