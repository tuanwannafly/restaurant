package com.restaurant.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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

    // ── Chart panel ───────────────────────────────────────────────────────────
    private SimpleBarChart chartPanel;

    // ── DAO ───────────────────────────────────────────────────────────────────
    private final StatsDAO dao = new StatsDAO();

    // ═════════════════════════════════════════════════════════════════════════
    public AdminStatsPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BG_PAGE);
        buildUI();
    }

    // ═════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        add(buildTopBar(), BorderLayout.NORTH);

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

        JLabel title = new JLabel("Thống kê");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        outer.add(title);

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);

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
        section.setMinimumSize(new Dimension(0, 340));
        section.setPreferredSize(new Dimension(800, 400));

        // Filter header
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterBar.setOpaque(false);

        cboType   = styledCombo(new String[]{"Doanh thu", "Đơn hàng"}, 110);
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

        // Nút vẽ biểu đồ
        RoundedButton btnChart = new RoundedButton(
            "📊 Vẽ biểu đồ", UIConstants.PRIMARY, UIConstants.PRIMARY_DARK,
            Color.WHITE, UIConstants.CORNER_RADIUS
        );
        btnChart.setPreferredSize(new Dimension(130, UIConstants.BTN_HEIGHT));
        btnChart.addActionListener(e -> loadChartData());
        filterBar.add(btnChart);

        section.add(filterBar, BorderLayout.NORTH);

        // Khởi tạo SimpleBarChart với data giả (12 tháng, values = 0)
        String[] initLabels = buildMonthOptions(chartYear);
        long[]   initValues = new long[12];
        chartPanel = new SimpleBarChart(initLabels, initValues, UIConstants.PRIMARY, "đ");

        section.add(chartPanel, BorderLayout.CENTER);
        return section;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Data loading
    // ═════════════════════════════════════════════════════════════════════════

    public void loadStats() {
        LocalDate from = spinnerDate(spnFrom);
        LocalDate to   = spinnerDate(spnTo);

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

    /** Tải dữ liệu biểu đồ theo bộ lọc đã chọn, chạy trên SwingWorker. */
    private void loadChartData() {
        String type    = (String) cboType.getSelectedItem();
        String fromStr = (String) cboChartFrom.getSelectedItem();
        String toStr   = (String) cboChartTo.getSelectedItem();

        int[] fromParsed = parseMonthLabel(fromStr);
        int[] toParsed   = parseMonthLabel(toStr);
        if (fromParsed == null || toParsed == null) return;

        // Đảm bảo thứ tự from <= to (cùng năm)
        int fromMonth = fromParsed[0], toMonth = toParsed[0];
        int yr        = fromParsed[1];
        if (fromMonth > toMonth) { int t = fromMonth; fromMonth = toMonth; toMonth = t; }

        final int fM = fromMonth, tM = toMonth, year = yr;
        final String chartType = type;

        new SwingWorker<Map<String, Long>, Void>() {
            @Override
            protected Map<String, Long> doInBackground() {
                return "Doanh thu".equals(chartType)
                    ? dao.getMonthlyRevenue(year, fM, tM)
                    : dao.getMonthlyOrders(year, fM, tM);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Long> data = get();

                    int count = tM - fM + 1;
                    String[] labels = new String[count];
                    long[]   values = new long[count];

                    for (int i = 0; i < count; i++) {
                        int m = fM + i;
                        labels[i] = "T" + m + "/" + year;
                        values[i] = data.getOrDefault(labels[i], 0L);
                    }

                    Color  barColor = "Doanh thu".equals(chartType) ? UIConstants.PRIMARY : UIConstants.SUCCESS;
                    String unit     = "Doanh thu".equals(chartType) ? "đ" : "đơn";

                    chartPanel.setBarColor(barColor);
                    chartPanel.setUnit(unit);
                    chartPanel.updateData(labels, values);

                } catch (Exception ex) {
                    System.err.println("[AdminStatsPanel] loadChartData lỗi: " + ex.getMessage());
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
        return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    private String[] buildMonthOptions(int year) {
        String[] opts = new String[12];
        for (int i = 0; i < 12; i++) opts[i] = "T" + (i + 1) + "/" + year;
        return opts;
    }

    /** Parse "T3/2026" → {3, 2026}. Null nếu format sai. */
    private int[] parseMonthLabel(String label) {
        if (label == null) return null;
        try {
            String s = label.startsWith("T") ? label.substring(1) : label;
            String[] parts = s.split("/");
            return new int[]{ Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
        } catch (Exception e) {
            return null;
        }
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

    // ═════════════════════════════════════════════════════════════════════════
    // PHẦN A — SimpleBarChart (Java2D thuần, không dependency ngoài)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Bar chart đơn giản vẽ bằng Java2D.
     * Hỗ trợ hover highlight, Y-axis đường kẻ ngang nét đứt, label trên đỉnh cột.
     */
    private static class SimpleBarChart extends JPanel {

        private String[] labels;
        private long[]   values;
        private Color    barColor;
        private String   unit;

        /** Vị trí chuột X hiện tại (dùng để xác định hover). */
        private int mouseX = -1;

        // Insets vùng vẽ
        private static final int INS_TOP    = 24;
        private static final int INS_BOTTOM = 52;
        private static final int INS_LEFT   = 64;
        private static final int INS_RIGHT  = 20;

        // Colors
        private static final Color GRID_COLOR  = new Color(0xE5E7EB);
        private static final Color AXIS_COLOR  = new Color(0xD1D5DB);
        private static final Color LABEL_COLOR = new Color(0x6B7280);
        private static final Color VALUE_COLOR = new Color(0x374151);

        // Fonts
        private static final Font FONT_VAL  = new Font("Segoe UI", Font.BOLD, 10);
        private static final Font FONT_X    = new Font("Segoe UI", Font.PLAIN, 11);
        private static final Font FONT_Y    = new Font("Segoe UI", Font.PLAIN, 10);
        private static final Font FONT_HINT = new Font("Segoe UI", Font.PLAIN, 13);

        SimpleBarChart(String[] labels, long[] values, Color barColor, String unit) {
            this.labels   = labels;
            this.values   = values;
            this.barColor = barColor;
            this.unit     = unit;

            setOpaque(false);

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    mouseX = e.getX();
                    repaint();
                }
            });
        }

        // ── Public update ─────────────────────────────────────────────────────

        public void updateData(String[] labels, long[] values) {
            this.labels = labels;
            this.values = values;
            repaint();
        }

        public void setBarColor(Color c) { this.barColor = c; repaint(); }
        public void setUnit(String u)    { this.unit = u; repaint(); }

        // ── Paint ─────────────────────────────────────────────────────────────

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // 1. Nền trắng
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);

            // 2. Kiểm tra maxVal
            long maxVal = 0;
            if (values != null) for (long v : values) if (v > maxVal) maxVal = v;

            if (maxVal == 0 || labels == null || labels.length == 0) {
                g2.setFont(FONT_HINT);
                g2.setColor(LABEL_COLOR);
                String msg = "Chọn bộ lọc rồi nhấn  📊 Vẽ biểu đồ  để hiển thị dữ liệu";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, (h + fm.getAscent()) / 2);
                g2.dispose();
                return;
            }

            // 3. Vùng chart
            int cX = INS_LEFT;
            int cY = INS_TOP;
            int cW = w - INS_LEFT - INS_RIGHT;
            int cH = h - INS_TOP  - INS_BOTTOM;

            // 4. Y-axis: 5 mức ngang
            final int Y_LINES = 5;
            g2.setFont(FONT_Y);
            FontMetrics yFm = g2.getFontMetrics();

            for (int i = 0; i <= Y_LINES; i++) {
                long lineVal = maxVal * i / Y_LINES;
                int  lineY   = cY + cH - (int)((long) cH * i / Y_LINES);

                if (i > 0) {
                    // Đường kẻ nét đứt
                    g2.setColor(GRID_COLOR);
                    g2.setStroke(new BasicStroke(
                        1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                        0f, new float[]{4f, 4f}, 0f
                    ));
                    g2.drawLine(cX, lineY, cX + cW, lineY);
                }

                // Label giá trị bên trái
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(LABEL_COLOR);
                String yLbl = compactVal(lineVal);
                int lx = cX - yFm.stringWidth(yLbl) - 6;
                g2.drawString(yLbl, Math.max(2, lx), lineY + yFm.getAscent() / 2 - 1);
            }

            // 5. Vẽ các cột
            int n = labels.length;
            double slotW = (double) cW / n;
            double barW  = slotW * 0.60;

            for (int i = 0; i < n; i++) {
                long val   = values[i];
                int  barH  = (int)((double) cH * val / maxVal);
                int  barTop= cY + cH - barH;
                int  bx    = (int)(cX + slotW * i + (slotW - barW) / 2);
                int  bw    = Math.max(1, (int) barW);

                // Hover check
                boolean hovered = mouseX >= 0 && Math.abs(mouseX - (bx + bw / 2)) <= slotW / 2;
                int alpha = hovered ? 255 : 200;

                if (barH > 0) {
                    // Fill bo tròn
                    g2.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), alpha));
                    g2.fillRoundRect(bx, barTop, bw, barH, 6, 6);

                    // Viền tối hơn ~20%
                    Color brd = darken(barColor, 0.78f);
                    g2.setColor(new Color(brd.getRed(), brd.getGreen(), brd.getBlue(), alpha));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(bx, barTop, bw, barH, 6, 6);
                }

                // Label giá trị trên đỉnh cột
                if (val > 0) {
                    g2.setFont(FONT_VAL);
                    g2.setColor(VALUE_COLOR);
                    FontMetrics vFm = g2.getFontMetrics();
                    String vStr = compactVal(val);
                    int vx = bx + (bw - vFm.stringWidth(vStr)) / 2;
                    int vy = barTop - 4;
                    if (vy >= cY + vFm.getHeight()) {
                        g2.drawString(vStr, vx, vy);
                    }
                }

                // Label X phía dưới
                g2.setFont(FONT_X);
                g2.setColor(hovered ? UIConstants.TEXT_PRIMARY : LABEL_COLOR);
                FontMetrics xFm = g2.getFontMetrics();
                String xLbl = labels[i];
                int xy = cY + cH + xFm.getAscent() + 8;

                if (xFm.stringWidth(xLbl) <= bw + 10) {
                    // Hiển thị thẳng
                    int xx = bx + (bw - xFm.stringWidth(xLbl)) / 2;
                    g2.drawString(xLbl, xx, xy);
                } else {
                    // Xoay -45° để tránh chồng chéo
                    java.awt.geom.AffineTransform orig = g2.getTransform();
                    g2.translate(bx + bw / 2, cY + cH + 8);
                    g2.rotate(-Math.PI / 4);
                    g2.drawString(xLbl, -xFm.stringWidth(xLbl) / 2, xFm.getAscent());
                    g2.setTransform(orig);
                }
            }

            // 6. Trục X và Y
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(AXIS_COLOR);
            g2.drawLine(cX, cY, cX, cY + cH);           // Y-axis
            g2.drawLine(cX, cY + cH, cX + cW, cY + cH); // X-axis

            g2.dispose();
        }

        // ── Utilities ─────────────────────────────────────────────────────────

        private Color darken(Color c, float f) {
            return new Color(
                Math.max(0, (int)(c.getRed()   * f)),
                Math.max(0, (int)(c.getGreen() * f)),
                Math.max(0, (int)(c.getBlue()  * f))
            );
        }

        /** Rút gọn giá trị: 1_500_000 → "1.5M", 250_000 → "250K", ... */
        private String compactVal(long val) {
            if (val == 0)                  return "0";
            if (val >= 1_000_000_000L)     return String.format("%.1fB", val / 1_000_000_000.0);
            if (val >= 1_000_000L)         return String.format("%.1fM", val / 1_000_000.0);
            if (val >= 1_000L)             return String.format("%.0fK", val / 1_000.0);
            return String.valueOf(val);
        }
    }
}
