package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingWorker;

import com.restaurant.dao.StatsDAO;

/**
 * Panel thống kê toàn hệ thống — chỉ dành cho SUPER_ADMIN.
 *
 * <p>Hiển thị:
 * <ul>
 *   <li>Date-range filter (Từ ngày / Đến ngày) + nút Lọc</li>
 *   <li>3 summary cards: Tổng Nhà Hàng Hoạt Động · Tổng Doanh Thu · Tổng Đơn Hàng</li>
 *   <li>Chart section với filter Loại / Theo (Tháng|Quý|Năm) / Từ–Đến</li>
 * </ul>
 *
 * <p>Đăng ký trong {@code MainFrame} với key {@code "adminstats"}.
 * Gọi {@link #loadStats()} khi navigate đến panel.
 */
public class AdminStatsPanel extends JPanel {

    // ── Summary value labels ──────────────────────────────────────────────────
    private JLabel lblTotalRestaurants;
    private JLabel lblTotalRevenue;
    private JLabel lblTotalOrders;

    // ── Date filter ───────────────────────────────────────────────────────────
    private JSpinner spnFrom;
    private JSpinner spnTo;

    // ── Chart filter combos ───────────────────────────────────────────────────
    private JComboBox<String> cboType;
    private JComboBox<String> cboPeriod;
    private JComboBox<String> cboChartFrom;
    private JComboBox<String> cboChartTo;

    // ── DAO ───────────────────────────────────────────────────────────────────
    private final StatsDAO dao = new StatsDAO();

    // ═════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═════════════════════════════════════════════════════════════════════════

    public AdminStatsPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BG_PAGE);
        buildUI();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Build UI
    // ═════════════════════════════════════════════════════════════════════════

    private void buildUI() {
        add(buildTopBar(), BorderLayout.NORTH);

        // Scrollable content
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 48, 24, 48));

        content.add(buildSummaryCards());
        content.add(Box.createVerticalStrut(20));
        content.add(buildChartSection());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        add(scroll, BorderLayout.CENTER);
    }

    // ── NORTH: title + date filter bar ───────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setOpaque(false);
        outer.setBorder(BorderFactory.createEmptyBorder(24, 48, 0, 48));

        // Row 1 — title
        JLabel title = new JLabel("Thống kê");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        outer.add(title);

        // Row 2 — date filter
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Default: first day of current month → today
        LocalDate today = LocalDate.now();
        spnFrom = makeDateSpinner(today.withDayOfMonth(1));
        spnTo   = makeDateSpinner(today);

        filterRow.add(bodyLabel("Từ ngày:"));
        filterRow.add(spnFrom);
        filterRow.add(bodyLabel("Đến ngày:"));
        filterRow.add(spnTo);

        RoundedButton btnFilter = RoundedButton.outline("🔍 Lọc");
        btnFilter.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnFilter.addActionListener(e -> loadStats());
        filterRow.add(btnFilter);

        outer.add(filterRow);
        outer.add(Box.createVerticalStrut(16));
        return outer;
    }

    // ── Summary cards ─────────────────────────────────────────────────────────

    private JPanel buildSummaryCards() {
        // Fixed-height wrapper so cards don't stretch vertically
        JPanel wrapper = new JPanel(new java.awt.GridLayout(1, 3, 16, 0));
        wrapper.setOpaque(false);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        wrapper.setPreferredSize(new Dimension(800, 130));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblTotalRestaurants = valueLbl("–");
        lblTotalRevenue     = valueLbl("–");
        lblTotalOrders      = valueLbl("–");

        wrapper.add(buildCard("Tổng Nhà Hàng Hoạt Động", lblTotalRestaurants));
        wrapper.add(buildCard("Tổng Doanh Thu",            lblTotalRevenue));
        wrapper.add(buildCard("Tổng Đơn Hàng",             lblTotalOrders));

        return wrapper;
    }

    /**
     * Một card tổng quan với nền bo tròn màu xanh nhạt {@code 0xBFD7F4}.
     */
    private JPanel buildCard(String titleText, JLabel valueLabel) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        UIConstants.CARD_RADIUS, UIConstants.CARD_RADIUS);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel titleLbl = new JLabel(titleText, JLabel.CENTER);
        titleLbl.setFont(UIConstants.FONT_BOLD);
        titleLbl.setForeground(UIConstants.TEXT_PRIMARY);
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(Box.createVerticalGlue());
        card.add(titleLbl);
        card.add(Box.createVerticalStrut(8));
        card.add(valueLabel);
        card.add(Box.createVerticalGlue());
        return card;
    }

    // ── Chart section ─────────────────────────────────────────────────────────

    private JPanel buildChartSection() {
        // Rounded white panel with BORDER_COLOR outline painted in paintComponent
        JPanel section = new JPanel(new BorderLayout(0, 12)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        section.setOpaque(false);
        section.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMinimumSize(new Dimension(0, 300));
        section.setPreferredSize(new Dimension(800, 360));

        // Filter header
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterBar.setOpaque(false);

        cboType   = styledCombo(new String[]{"Nhà hàng", "Doanh thu", "Đơn hàng"}, 110);
        cboPeriod = styledCombo(new String[]{"Tháng", "Quý", "Năm"}, 80);

        int chartYear = LocalDate.now().getYear();
        String[] months = buildMonthOptions(chartYear);
        cboChartFrom = styledCombo(months, 100);
        cboChartTo   = styledCombo(months, 100);
        cboChartTo.setSelectedIndex(months.length - 1);

        filterBar.add(bodyLabel("Loại:"));
        filterBar.add(cboType);
        filterBar.add(bodyLabel("Theo"));
        filterBar.add(cboPeriod);
        filterBar.add(bodyLabel("Từ"));
        filterBar.add(cboChartFrom);
        filterBar.add(bodyLabel("Đến"));
        filterBar.add(cboChartTo);

        section.add(filterBar, BorderLayout.NORTH);

        // Chart placeholder (swap out for JFreeChart ChartPanel later)
        JPanel chartArea = new JPanel(new BorderLayout());
        chartArea.setOpaque(false);

        JLabel placeholder = new JLabel("Biểu đồ sẽ hiển thị tại đây", JLabel.CENTER);
        placeholder.setFont(UIConstants.FONT_BODY);
        placeholder.setForeground(UIConstants.TEXT_SECONDARY);
        chartArea.add(placeholder, BorderLayout.CENTER);

        section.add(chartArea, BorderLayout.CENTER);
        return section;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Data loading
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Tải dữ liệu thống kê toàn hệ thống trong khoảng ngày đã chọn.
     * Gọi từ {@code MainFrame.navigateTo("adminstats")} và nút Lọc.
     */
    public void loadStats() {
        LocalDate from = spinnerDate(spnFrom);
        LocalDate to   = spinnerDate(spnTo);

        // Show loading state
        lblTotalRestaurants.setText("...");
        lblTotalRevenue.setText("...");
        lblTotalOrders.setText("...");

        new SwingWorker<Map<String, Long>, Void>() {
            @Override
            protected Map<String, Long> doInBackground() {
                return dao.getSuperAdminStats(from, to);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Long> stats = get();
                    NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

                    lblTotalRestaurants.setText(
                        String.valueOf(stats.getOrDefault("total_restaurants", 0L)));
                    lblTotalRevenue.setText(
                        nf.format(stats.getOrDefault("total_revenue", 0L)));
                    lblTotalOrders.setText(
                        String.valueOf(stats.getOrDefault("total_orders", 0L)));

                } catch (Exception ex) {
                    System.err.println("[AdminStatsPanel] loadStats lỗi: " + ex.getMessage());
                    lblTotalRestaurants.setText("–");
                    lblTotalRevenue.setText("–");
                    lblTotalOrders.setText("–");
                }
            }
        }.execute();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private JSpinner makeDateSpinner(LocalDate initial) {
        Date date = java.sql.Date.valueOf(initial);
        SpinnerDateModel model = new SpinnerDateModel(date, null, null, Calendar.DAY_OF_MONTH);
        JSpinner spinner = new JSpinner(model);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "dd/MM/yyyy");
        spinner.setEditor(editor);
        spinner.setFont(UIConstants.FONT_BODY);
        spinner.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        return spinner;
    }

    private LocalDate spinnerDate(JSpinner spinner) {
        Date d = (Date) spinner.getValue();
        return d.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
    }

    private String[] buildMonthOptions(int year) {
        String[] opts = new String[12];
        for (int i = 0; i < 12; i++) opts[i] = "T" + (i + 1) + "/" + year;
        return opts;
    }

    private JLabel bodyLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UIConstants.FONT_BODY);
        l.setForeground(UIConstants.TEXT_PRIMARY);
        return l;
    }

    private JLabel valueLbl(String text) {
        JLabel l = new JLabel(text, JLabel.CENTER);
        l.setFont(new Font("Segoe UI", Font.BOLD, 32));
        l.setForeground(UIConstants.TEXT_PRIMARY);
        return l;
    }

    private JComboBox<String> styledCombo(String[] items, int width) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setFont(UIConstants.FONT_BODY);
        cb.setPreferredSize(new Dimension(width, UIConstants.BTN_HEIGHT));
        return cb;
    }
}