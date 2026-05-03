package com.restaurant.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import net.miginfocom.swing.MigLayout;

import com.restaurant.dao.StatsDAO;

/**
 * Stats panel: revenue summary, top-5 items, table occupancy.
 *
 * Layout (MigLayout):
 *   NORTH  — top bar (title + refresh button)
 *   CENTER — scrollable content:
 *              date-range picker (JSpinner × 2)
 *              3 revenue metric cards
 *              top-5 items table (StyledTable)
 *              table-status cards + styled progress bar
 *
 * No emoji; all labels in Vietnamese without diacritics.
 */
public class StatsPanel extends JPanel {

    // ── Date range ────────────────────────────────────────────────────────────
    private JSpinner spinFrom;
    private JSpinner spinTo;
    private LocalDate dateFrom = LocalDate.now().minusDays(30);
    private LocalDate dateTo   = LocalDate.now();

    // ── Revenue cards ─────────────────────────────────────────────────────────
    private JLabel lblRevenue;
    private JLabel lblOrderCount;
    private JLabel lblAvgOrder;

    // ── Top items table ───────────────────────────────────────────────────────
    private DefaultTableModel topItemsModel;

    // ── Table status ──────────────────────────────────────────────────────────
    private JLabel       lblAvailable;
    private JLabel       lblOccupied;
    private JLabel       lblReserved;
    private JProgressBar progressTable;

    // ── Status / loading ──────────────────────────────────────────────────────
    private JLabel lblStatus;

    private final StatsDAO dao = new StatsDAO();

    // =========================================================================
    // Constructor
    // =========================================================================

    public StatsPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BG_PAGE);
        buildUI();
    }

    // =========================================================================
    // Build UI
    // =========================================================================

    private void buildUI() {

        // ── NORTH: top bar ────────────────────────────────────────────────────
        add(buildTopBar(), BorderLayout.NORTH);

        // ── CENTER: scrollable body ───────────────────────────────────────────
        JPanel body = new JPanel(new MigLayout(
                "fillx, insets 0 48 24 48, gapy 0",
                "[grow]",
                "[]16[]12[]24[]12[]24[]12[]12[]"));
        body.setBackground(UIConstants.BG_PAGE);

        body.add(buildDateFilter(),                       "growx, wrap");
        body.add(buildSectionHeader("Doanh thu"),         "growx, wrap");
        body.add(buildRevenueCards(),                     "growx, wrap");
        body.add(buildSectionHeader("Top 5 mon ban chay"), "growx, wrap");
        body.add(buildTopItemsTable(),                    "growx, h 220!, wrap");
        body.add(buildSectionHeader("Trang thai ban"),    "growx, wrap");
        body.add(buildTableStatusCards(),                 "growx, wrap");
        body.add(buildOccupancyBar(),                     "growx, wrap");

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(UIConstants.BG_PAGE);
        scroll.getViewport().setBackground(UIConstants.BG_PAGE);
        add(scroll, BorderLayout.CENTER);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new MigLayout(
                "fillx, insets 20 48 12 48",
                "[grow]push[]8[]",
                "[]"));
        bar.setOpaque(false);

        JLabel title = new JLabel("Thong ke & Doanh thu");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);

        lblStatus = new JLabel("");
        lblStatus.setFont(UIConstants.FONT_BODY);
        lblStatus.setForeground(UIConstants.TEXT_SECONDARY);

        RoundedButton btnRefresh = new RoundedButton("Lam moi");
        btnRefresh.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnRefresh.addActionListener(e -> loadAll());

        bar.add(title,      "growx");
        bar.add(lblStatus,  "");
        bar.add(btnRefresh, "");
        return bar;
    }

    // ── Date filter (JSpinner) ────────────────────────────────────────────────

    private JPanel buildDateFilter() {
        JPanel p = new JPanel(new MigLayout("insets 0, gapy 0", "[]8[]16[]8[]12[]", "[]"));
        p.setOpaque(false);

        spinFrom = makeDateSpinner(dateFrom);
        spinTo   = makeDateSpinner(dateTo);

        JLabel lFrom = sideLabel("Tu ngay:");
        JLabel lTo   = sideLabel("Den ngay:");

        RoundedButton btnApply = new RoundedButton("Ap dung");
        btnApply.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnApply.addActionListener(e -> applyDateFilter());

        p.add(lFrom,     "");
        p.add(spinFrom,  "w 120!");
        p.add(lTo,       "");
        p.add(spinTo,    "w 120!");
        p.add(btnApply,  "");
        return p;
    }

    private JSpinner makeDateSpinner(LocalDate initial) {
        Date d = Date.from(initial.atStartOfDay(ZoneId.systemDefault()).toInstant());
        SpinnerDateModel model = new SpinnerDateModel(d, null, null, java.util.Calendar.DAY_OF_MONTH);
        JSpinner sp = new JSpinner(model);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(sp, "dd/MM/yyyy");
        sp.setEditor(editor);
        sp.setFont(UIConstants.FONT_BODY);
        sp.setPreferredSize(new Dimension(120, UIConstants.BTN_HEIGHT));
        return sp;
    }

    private void applyDateFilter() {
        try {
            LocalDate from = toLocalDate((Date) spinFrom.getValue());
            LocalDate to   = toLocalDate((Date) spinTo.getValue());
            if (from.isAfter(to)) {
                JOptionPane.showMessageDialog(this,
                        "Ngay bat dau phai truoc ngay ket thuc.",
                        "Loi ngay", JOptionPane.WARNING_MESSAGE);
                return;
            }
            dateFrom = from;
            dateTo   = to;
            loadAll();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Khong doc duoc ngay tu spinner: " + ex.getMessage(),
                    "Loi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static LocalDate toLocalDate(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ── Section header ────────────────────────────────────────────────────────

    private JLabel buildSectionHeader(String text) {
        JLabel lbl = new JLabel(text.toUpperCase());
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(UIConstants.TEXT_SECONDARY);
        return lbl;
    }

    // ── Revenue cards (3 across) ──────────────────────────────────────────────

    private JPanel buildRevenueCards() {
        JPanel row = new JPanel(new MigLayout(
                "fillx, insets 0, gap 16 0",
                "[grow,fill][grow,fill][grow,fill]",
                "[90!]"));
        row.setOpaque(false);

        lblRevenue    = metricValueLabel("0 d");
        lblOrderCount = metricValueLabel("0 don");
        lblAvgOrder   = metricValueLabel("0 d");

        row.add(buildMetricCard("Tong doanh thu",    lblRevenue,    new Color(0x3B82F6)));
        row.add(buildMetricCard("So don hoan thanh", lblOrderCount, new Color(0x10B981)));
        row.add(buildMetricCard("Trung binh / don",  lblAvgOrder,   new Color(0xF59E0B)), "wrap");

        return row;
    }

    // ── Top items table ───────────────────────────────────────────────────────

    private JPanel buildTopItemsTable() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);

        topItemsModel = new DefaultTableModel(
                new String[]{ "Hang", "Ten mon", "So luong", "Doanh thu (d)" }, 0) {
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

    // ── Table status cards (3 across) ────────────────────────────────────────

    private JPanel buildTableStatusCards() {
        JPanel row = new JPanel(new MigLayout(
                "fillx, insets 0, gap 16 0",
                "[grow,fill][grow,fill][grow,fill]",
                "[90!]"));
        row.setOpaque(false);

        lblAvailable = metricValueLabel("0");
        lblOccupied  = metricValueLabel("0");
        lblReserved  = metricValueLabel("0");

        row.add(buildMetricCard("Ban trong",    lblAvailable, new Color(0x10B981)));
        row.add(buildMetricCard("Dang phuc vu", lblOccupied,  new Color(0xEF4444)));
        row.add(buildMetricCard("Dat truoc",    lblReserved,  new Color(0xF59E0B)), "wrap");

        return row;
    }

    // ── Occupancy progress bar ────────────────────────────────────────────────

    private JPanel buildOccupancyBar() {
        JPanel p = new JPanel(new MigLayout("insets 0", "[]12[grow]", "[]"));
        p.setOpaque(false);

        JLabel lbl = sideLabel("Ti le su dung:");

        // Custom-painted gradient progress bar
        progressTable = new JProgressBar(0, 100) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                int r = h / 2;

                // Track
                g2.setColor(new Color(0xE5E7EB));
                g2.fillRoundRect(0, 0, w, h, r, r);

                // Fill gradient
                int fillW = (int) (w * (double) getValue() / getMaximum());
                if (fillW > 0) {
                    Color c1 = new Color(0x3B82F6);
                    Color c2 = new Color(0x10B981);
                    g2.setPaint(new GradientPaint(0, 0, c1, fillW, 0, c2));
                    g2.fillRoundRect(0, 0, fillW, h, r, r);

                    // Shine highlight
                    g2.setColor(new Color(255, 255, 255, 40));
                    g2.fillRoundRect(0, 0, fillW, h / 2, r, r);
                }

                // Border
                g2.setColor(new Color(0xD1D5DB));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);

                // Text
                String txt = getString();
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.setColor(getValue() > 55 ? Color.WHITE : new Color(0x374151));
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(txt)) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(txt, tx, ty);

                g2.dispose();
            }
        };
        progressTable.setStringPainted(false);   // we paint it ourselves
        progressTable.setString("0/0 ban dang dung");
        progressTable.setOpaque(false);
        progressTable.setPreferredSize(new Dimension(0, 26));

        p.add(lbl,           "");
        p.add(progressTable, "growx");
        return p;
    }

    // ── Metric card ───────────────────────────────────────────────────────────

    /**
     * White card with thin left accent line, title + bold value.
     */
    private JPanel buildMetricCard(String title, JLabel valueLbl, Color accent) {

        JPanel card = new JPanel(new MigLayout("insets 16 20 16 20, fillx", "[grow]", "[]6[]")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight(), r = 10;
                // Shadow
                g2.setColor(new Color(0, 0, 0, 14));
                g2.fillRoundRect(1, 3, w - 2, h, r, r);
                // Body
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w - 1, h - 1, r, r);
                // Left accent bar
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(2, r / 2, 2, h - 1 - r / 2);
                g2.dispose();
            }
        };
        card.setOpaque(false);

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(UIConstants.FONT_SMALL);
        lblTitle.setForeground(UIConstants.TEXT_SECONDARY);

        valueLbl.setForeground(UIConstants.TEXT_PRIMARY);

        card.add(lblTitle,  "growx, wrap");
        card.add(valueLbl,  "growx");
        return card;
    }

    private JLabel metricValueLabel(String initial) {
        JLabel lbl = new JLabel(initial);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        return lbl;
    }

    private JLabel sideLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_SECONDARY);
        return lbl;
    }

    // =========================================================================
    // Data loading
    // =========================================================================

    private static class StatBundle {
        StatsDAO.RevenueStats   revenue;
        List<StatsDAO.TopItem>  topItems;
        StatsDAO.TableStats     tables;
    }

    /** Full async refresh — called on button click and on panel open. */
    public void loadAll() {
        showLoading(true);
        new SwingWorker<StatBundle, Void>() {
            @Override
            protected StatBundle doInBackground() {
                StatBundle b = new StatBundle();
                b.revenue  = dao.getRevenue(dateFrom, dateTo);
                b.topItems = dao.getTopItems(dateFrom, dateTo, 5);
                b.tables   = dao.getTableStats();
                return b;
            }

            @Override
            protected void done() {
                try {
                    StatBundle b = get();
                    updateRevenueUI(b.revenue);
                    updateTopItemsUI(b.topItems);
                    updateTableStatsUI(b.tables);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    JOptionPane.showMessageDialog(StatsPanel.this,
                            "Tai thong ke bi gian doan.",
                            "Loi", JOptionPane.ERROR_MESSAGE);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(StatsPanel.this,
                            "Loi tai thong ke: " + cause.getMessage(),
                            "Loi", JOptionPane.ERROR_MESSAGE);
                } finally {
                    showLoading(false);
                }
            }
        }.execute();
    }

    // ── EDT updaters ──────────────────────────────────────────────────────────

    private void updateRevenueUI(StatsDAO.RevenueStats stats) {
        lblRevenue   .setText(formatVnd(stats.totalRevenue));
        lblOrderCount.setText(stats.orderCount + " don");
        lblAvgOrder  .setText(formatVnd(stats.avgPerOrder));
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
        lblOccupied .setText(String.valueOf(ts.occupied));
        lblReserved .setText(String.valueOf(ts.reserved));

        int pct = ts.total > 0 ? (ts.occupied * 100 / ts.total) : 0;
        progressTable.setValue(pct);
        progressTable.setString(ts.occupied + "/" + ts.total + " ban dang dung");
        progressTable.repaint();
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        lblStatus.setText(show ? "Dang tai..." : "");
    }

    private static String formatVnd(long value) {
        return String.format("%,.0f d", (double) value);
    }
}