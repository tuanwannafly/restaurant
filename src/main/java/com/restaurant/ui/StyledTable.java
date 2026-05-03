package com.restaurant.ui;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * StyledTable — Phase 3
 *
 * Features:
 *  • Header  : #F1F5F9 bg, 600-weight text, sort indicator (▲ / ▼)
 *  • Rows    : height 44 px, alternating #FFFFFF / #F8FAFC, hover #EFF6FF
 *  • Loading : animated skeleton rows (call setLoading(true) before data arrives)
 *  • Empty   : centred illustration + bilingual "Không có dữ liệu" message
 *  • wrap()  : returns a matching JScrollPane (border + viewport bg)
 */
public class StyledTable extends JTable {

    // ── Palette ─────────────────────────────────────────────────────────────
    static final Color ROW_EVEN    = Color.WHITE;
    static final Color ROW_ODD     = new Color(0xF8FAFC);
    static final Color ROW_HOVER   = new Color(0xEFF6FF);
    static final Color ROW_SEL     = new Color(0xDBEAFE);
    static final Color HEADER_BG   = new Color(0xF1F5F9);
    static final Color HEADER_FG   = new Color(0x334155);
    static final Color BORDER_COL  = new Color(0xE2E8F0);
    static final Color SKELETON_BG = new Color(0xE2E8F0);
    static final Color EMPTY_FG    = new Color(0x94A3B8);
    static final Color EMPTY_FG2   = new Color(0xCBD5E1);

    // ── State ────────────────────────────────────────────────────────────────
    private int     hoveredRow = -1;
    private boolean loading    = false;

    // Skeleton shimmer animation
    private float   shimmerX   = 0f;
    private Timer   shimmerTimer;

    private static final int SKELETON_ROWS  = 7;
    private static final int SKELETON_PULSE = 30; // ms per frame

    // ── Constructor ──────────────────────────────────────────────────────────
    public StyledTable(TableModel model) {
        super(model);

        // Basic table config
        setFont(UIConstants.FONT_BODY);
        setRowHeight(44);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setFillsViewportHeight(true);
        setSelectionBackground(ROW_SEL);
        setSelectionForeground(UIConstants.TEXT_PRIMARY);
        setBackground(Color.WHITE);

        // Header
        JTableHeader header = getTableHeader();
        header.setFont(UIConstants.FONT_BOLD);
        header.setBackground(HEADER_BG);
        header.setForeground(HEADER_FG);
        header.setPreferredSize(new Dimension(0, 42));
        header.setReorderingAllowed(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));
        header.setDefaultRenderer(new SortableHeaderRenderer(header.getDefaultRenderer()));

        // Auto sort on column click
        setAutoCreateRowSorter(true);

        // Default cell renderer (striped + hover)
        setDefaultRenderer(Object.class, new StripedRenderer());

        // Hover tracking
        setupHover();

        // Shimmer timer (starts only when loading=true)
        shimmerTimer = new Timer(SKELETON_PULSE, e -> {
            shimmerX = (shimmerX + 0.015f) % 1.2f;
            repaint();
        });
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Show/hide the loading skeleton. Call setLoading(false) once data arrives. */
    public void setLoading(boolean loading) {
        this.loading = loading;
        if (loading) shimmerTimer.start();
        else         shimmerTimer.stop();
        repaint();
    }

    public boolean isLoading() { return loading; }

    // ── Painting ─────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        if (loading) {
            paintSkeleton((Graphics2D) g.create());
        } else if (getRowCount() == 0) {
            paintEmptyState((Graphics2D) g.create());
        } else {
            super.paintComponent(g);
        }
    }

    /** Shimmer-animated skeleton rows */
    private void paintSkeleton(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        int rowH = getRowHeight();

        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);

        // Column skeleton widths (rough proportions)
        int[][] blocks = { {50, 12}, {140, 12}, {80, 12}, {80, 12}, {0, 0} };

        for (int i = 0; i < SKELETON_ROWS; i++) {
            int y = i * rowH;

            // Stripe
            g2.setColor(i % 2 == 0 ? ROW_EVEN : ROW_ODD);
            g2.fillRect(0, y, w, rowH);

            // Blocks
            int x = 14;
            for (int[] block : blocks) {
                if (block[0] == 0) break;
                int bw = block[0], bh = block[1];
                int by = y + (rowH - bh) / 2;

                // Base colour
                g2.setColor(SKELETON_BG);
                g2.fillRoundRect(x, by, bw, bh, 6, 6);

                // Shimmer overlay
                float shimmerStart = shimmerX - 0.4f;
                float shimmerEnd   = shimmerX;
                float relX  = (float) x / w;
                float relX2 = (float) (x + bw) / w;
                if (shimmerEnd > relX && shimmerStart < relX2) {
                    float lo = Math.max(relX, shimmerStart);
                    float hi = Math.min(relX2, shimmerEnd);
                    int sx = (int) (lo * w);
                    int sw = (int) ((hi - lo) * w);
                    GradientPaint gp = new GradientPaint(
                        sx, 0, new Color(0xFF, 0xFF, 0xFF, 0),
                        sx + sw, 0, new Color(0xFF, 0xFF, 0xFF, 110));
                    g2.setPaint(gp);
                    g2.fillRoundRect(x, by, bw, bh, 6, 6);
                    g2.setPaint(null);
                }

                x += bw + (i == 0 ? 20 : 20);
            }

            // Action column placeholder (wider, pill shape)
            int pillW = 220, pillH = 22;
            int pillX = w - pillW - 14;
            int pillY = y + (rowH - pillH) / 2;
            g2.setColor(SKELETON_BG);
            g2.fillRoundRect(pillX, pillY, pillW, pillH, 11, 11);
        }

        g2.dispose();
    }

    /** Centred empty-state illustration */
    private void paintEmptyState(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cx = getWidth()  / 2;
        int cy = getHeight() / 2;

        // Background fill
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // Outer circle
        g2.setColor(new Color(0xF1F5F9));
        g2.fillOval(cx - 44, cy - 76, 88, 88);

        // Inner circle
        g2.setColor(new Color(0xE2E8F0));
        g2.fillOval(cx - 30, cy - 62, 60, 60);

        // Icon  (use Segoe UI Emoji / fallback)
        g2.setColor(new Color(0x94A3B8));
        g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26));
        FontMetrics fm = g2.getFontMetrics();
        String icon = "📋";
        g2.drawString(icon, cx - fm.stringWidth(icon) / 2, cy - 20);

        // Title
        g2.setFont(UIConstants.FONT_BOLD.deriveFont(Font.BOLD, 14f));
        g2.setColor(new Color(0x475569));
        fm = g2.getFontMetrics();
        String title = "Không có dữ liệu";
        g2.drawString(title, cx - fm.stringWidth(title) / 2, cy + 16);

        // Subtitle
        g2.setFont(UIConstants.FONT_BODY);
        g2.setColor(EMPTY_FG);
        fm = g2.getFontMetrics();
        String sub = "Thêm mới hoặc thay đổi bộ lọc để xem kết quả";
        g2.drawString(sub, cx - fm.stringWidth(sub) / 2, cy + 38);

        g2.dispose();
    }

    // ── Hover ────────────────────────────────────────────────────────────────

    private void setupHover() {
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                if (row != hoveredRow) {
                    hoveredRow = row;
                    repaint();
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredRow = -1;
                repaint();
            }
        });
    }

    // ── Renderers ────────────────────────────────────────────────────────────

    /** Striped rows + hover highlight */
    private class StripedRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (!isSelected) {
                // Convert view row → model row for correct stripe when sorted
                int modelRow = table.convertRowIndexToModel(row);
                setBackground(row == hoveredRow ? ROW_HOVER
                            : (modelRow % 2 == 0 ? ROW_EVEN : ROW_ODD));
            }
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            return this;
        }
    }

    /** Header with sort indicator (▲ / ▼) appended to label text */
    private static class SortableHeaderRenderer implements TableCellRenderer {
        private final TableCellRenderer delegate;

        SortableHeaderRenderer(TableCellRenderer delegate) {
            this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {

            Component c = delegate.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);

            if (c instanceof JLabel lbl) {
                lbl.setOpaque(true);
                lbl.setBackground(HEADER_BG);
                lbl.setForeground(HEADER_FG);
                lbl.setFont(UIConstants.FONT_BOLD);
                lbl.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                lbl.setHorizontalAlignment(SwingConstants.LEFT);

                // Sort icon suffix
                String sortSuffix = "";
                RowSorter<?> sorter = table.getRowSorter();
                if (sorter != null && !sorter.getSortKeys().isEmpty()) {
                    RowSorter.SortKey key = sorter.getSortKeys().get(0);
                    if (key.getColumn() == col) {
                        sortSuffix = key.getSortOrder() == SortOrder.ASCENDING
                                ? "  ▲" : "  ▼";
                    }
                }
                lbl.setText((value != null ? value.toString() : "") + sortSuffix);
            }
            return c;
        }
    }

    // ── Static helper ────────────────────────────────────────────────────────

    /**
     * Wrap this table in a matching JScrollPane with correct borders and bg.
     */
    public static JScrollPane wrap(StyledTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COL, 1));
        sp.getViewport().setBackground(Color.WHITE);
        // Match header background in top-right corner
        JPanel corner = new JPanel();
        corner.setBackground(HEADER_BG);
        sp.setCorner(JScrollPane.UPPER_RIGHT_CORNER, corner);
        return sp;
    }
}