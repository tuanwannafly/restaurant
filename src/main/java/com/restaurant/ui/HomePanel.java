package com.restaurant.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import net.miginfocom.swing.MigLayout;

import com.restaurant.dao.StatsDAO;
import com.restaurant.data.DataManager;
import com.restaurant.model.Restaurant;
import com.restaurant.session.AppSession;

/**
 * Home dashboard panel.
 *
 * Layout (MigLayout):
 *   - Header row: greeting + date
 *   - Section label row: "TONG QUAN" + countdown indicator
 *   - 2x2 stat card grid (Java2D icon, large value, label, shadow + hover-lift)
 *   - "HOAT DONG GAN DAY" section with styled JList
 *
 * Auto-refresh: PollManager every 30 s + countdown indicator.
 * No emoji — all icons drawn with Java2D.
 */
public class HomePanel extends JPanel {

    // ── Stat value labels ─────────────────────────────────────────────────────
    private JLabel lblActiveRestaurants;
    private JLabel lblNewRestaurants;
    private JLabel lblRevenue;
    private JLabel lblOrderCount;
    private JLabel lblRestaurantName;           // kept per spec, not laid out

    // ── Refresh indicator ─────────────────────────────────────────────────────
    private JLabel  lblRefreshDot;             // pulsing colour dot
    private JLabel  lblRefreshText;            // "Cap nhat sau: 30s"
    private Timer   countdownTimer;
    private int     secondsLeft = 30;

    // ── Recent-activity list ──────────────────────────────────────────────────
    private DefaultListModel<String> activityModel;

    private static final String POLL_KEY         = "home_stats";
    private static final int    REFRESH_INTERVAL = 30_000;      // ms

    // =========================================================================
    // Constructor
    // =========================================================================

    public HomePanel() {
        setBackground(UIConstants.BG_PAGE);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(28, 56, 28, 56));

        buildUI();
        refreshStats();
        startCountdown();

        PollManager.getInstance().register(POLL_KEY, this::refreshStats, REFRESH_INTERVAL);

        addAncestorListener(new AncestorListener() {
            @Override public void ancestorAdded(AncestorEvent e) {
                PollManager.getInstance().register(POLL_KEY, HomePanel.this::refreshStats, REFRESH_INTERVAL);
                startCountdown();
            }
            @Override public void ancestorRemoved(AncestorEvent e) {
                PollManager.getInstance().unregister(POLL_KEY);
                stopCountdown();
            }
            @Override public void ancestorMoved(AncestorEvent e) {}
        });
    }

    // =========================================================================
    // Build UI
    // =========================================================================

    private void buildUI() {
        removeAll();

        JPanel content = new JPanel(new MigLayout(
                "fillx, insets 0, gapy 0",
                "[grow]",
                "[]12[]18[]24[]"));
        content.setOpaque(false);

        content.add(buildHeaderRow(),        "growx, wrap");
        content.add(buildSectionBar(),       "growx, wrap");
        content.add(buildCardsGrid(),        "growx, wrap");
        content.add(buildActivitySection(),  "growx, wrap");

        add(content, BorderLayout.NORTH);
        revalidate();
        repaint();
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private JPanel buildHeaderRow() {
        JPanel p = new JPanel(new MigLayout("insets 0", "[grow]", "[]3[]"));
        p.setOpaque(false);

        String user = AppSession.getInstance().getUserName();
        JLabel greeting = new JLabel("Xin chao, " + user);
        greeting.setFont(new Font("Segoe UI", Font.BOLD, 22));
        greeting.setForeground(UIConstants.TEXT_PRIMARY);

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy, EEEE"));
        JLabel sub = new JLabel("Tong quan he thong  —  " + today);
        sub.setFont(UIConstants.FONT_BODY);
        sub.setForeground(UIConstants.TEXT_SECONDARY);

        p.add(greeting, "growx, wrap");
        p.add(sub,      "growx");
        return p;
    }

    // ── Section label + countdown ─────────────────────────────────────────────

    private JPanel buildSectionBar() {
        JPanel p = new JPanel(new MigLayout("insets 0", "[grow]push[]8[]", "[]"));
        p.setOpaque(false);

        JLabel section = new JLabel("TONG QUAN HE THONG");
        section.setFont(new Font("Segoe UI", Font.BOLD, 10));
        section.setForeground(UIConstants.TEXT_SECONDARY);

        // Coloured dot that turns green/amber based on state
        lblRefreshDot = new JLabel("\u25CF");   // filled circle character
        lblRefreshDot.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblRefreshDot.setForeground(new Color(0x10B981));   // green = idle

        lblRefreshText = new JLabel("Cap nhat sau: 30s");
        lblRefreshText.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblRefreshText.setForeground(UIConstants.TEXT_SECONDARY);

        p.add(section,        "growx");
        p.add(lblRefreshDot,  "");
        p.add(lblRefreshText, "");
        return p;
    }

    // ── 2×2 stat cards ───────────────────────────────────────────────────────

    private JPanel buildCardsGrid() {
        JPanel grid = new JPanel(new MigLayout(
                "fillx, insets 0, gap 16 16",
                "[grow,fill][grow,fill]",
                "[130!][130!]"));
        grid.setOpaque(false);

        lblActiveRestaurants = makeValueLabel("—");
        lblNewRestaurants    = makeValueLabel("—");
        lblRevenue           = makeValueLabel("—");
        lblOrderCount        = makeValueLabel("—");

        // kept per original spec (not added to layout)
        lblRestaurantName = new JLabel(" ");

        grid.add(buildStatCard(CardIcon.STORE,   "Nha hang hoat dong",  lblActiveRestaurants, new Color(0x3B82F6)));
        grid.add(buildStatCard(CardIcon.PLUS,    "Nha hang moi hom nay", lblNewRestaurants,   new Color(0x10B981)), "wrap");
        grid.add(buildStatCard(CardIcon.COIN,    "Doanh thu hom nay",    lblRevenue,           new Color(0xF59E0B)));
        grid.add(buildStatCard(CardIcon.BOX,     "Don hang hom nay",     lblOrderCount,        new Color(0xEF4444)), "wrap");

        return grid;
    }

    // ── Activity section ──────────────────────────────────────────────────────

    private JPanel buildActivitySection() {
        JPanel section = new JPanel(new MigLayout("fillx, insets 0", "[grow]", "[]8[]"));
        section.setOpaque(false);

        JLabel header = new JLabel("HOAT DONG GAN DAY");
        header.setFont(new Font("Segoe UI", Font.BOLD, 10));
        header.setForeground(UIConstants.TEXT_SECONDARY);
        section.add(header, "growx, wrap");

        activityModel = new DefaultListModel<>();
        activityModel.addElement("Dang tai du lieu...");

        JList<String> list = new JList<>(activityModel);
        list.setFont(UIConstants.FONT_BODY);
        list.setFixedCellHeight(38);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ActivityCellRenderer());
        list.setBackground(UIConstants.BG_WHITE);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        scroll.setPreferredSize(new Dimension(0, 192));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(UIConstants.BG_WHITE);
        scroll.getViewport().setBackground(UIConstants.BG_WHITE);

        section.add(scroll, "growx, h 192!");
        return section;
    }

    // =========================================================================
    // Stat card builder
    // =========================================================================

    private enum CardIcon { STORE, PLUS, COIN, BOX }

    /**
     * Builds a rounded, shadowed stat card.
     * Hover raises the shadow (lift effect) via MouseAdapter → repaint().
     */
    private JPanel buildStatCard(CardIcon icon, String label, JLabel valueLbl, Color accent) {

        // Mutable shadow state — int[] so lambdas can write
        final int[] shadowRadius = { 6 };
        final int[] shadowY      = { 3 };
        final int[] shadowAlpha  = { 28 };

        Color lightAccent = blend(Color.WHITE, accent, 0.08f);   // very light tint for icon circle bg

        JPanel card = new JPanel(new MigLayout("insets 18 20 18 20, fillx", "[40!][grow]", "[]")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int r = 14;  // corner radius

                // ── Drop shadow ──────────────────────────────────────────────
                int sr = shadowRadius[0];
                int sy = shadowY[0];
                for (int i = sr; i >= 0; i--) {
                    int alpha = (int) ((shadowAlpha[0]) * (1.0 - (double) i / sr));
                    g2.setColor(new Color(0, 0, 0, alpha));
                    g2.fillRoundRect(i / 2, sy + i / 2, w - i, h - sy - i / 2, r + 2, r + 2);
                }

                // ── Card body ────────────────────────────────────────────────
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w - 1, h - sy - 1, r, r);

                // ── Left accent bar (4 px) ────────────────────────────────────
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(2, r / 2, 2, h - sy - r / 2);

                g2.dispose();
            }
        };
        card.setOpaque(false);

        // ── Icon panel ────────────────────────────────────────────────────────
        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Background circle
                g2.setColor(lightAccent);
                g2.fillOval(0, 0, 38, 38);

                // Icon strokes
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                switch (icon) {
                    case STORE -> drawStoreIcon(g2, 19, 19);
                    case PLUS  -> drawPlusIcon(g2, 19, 19);
                    case COIN  -> drawCoinIcon(g2, 19, 19);
                    case BOX   -> drawBoxIcon(g2, 19, 19);
                }
                g2.dispose();
            }

            @Override public Dimension getPreferredSize() { return new Dimension(38, 38); }
        };
        iconPanel.setOpaque(false);

        // ── Text block ────────────────────────────────────────────────────────
        JPanel text = new JPanel(new MigLayout("insets 0", "[grow]", "[]2[]"));
        text.setOpaque(false);

        valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 26));
        valueLbl.setForeground(UIConstants.TEXT_PRIMARY);

        JLabel desc = new JLabel(label);
        desc.setFont(UIConstants.FONT_SMALL);
        desc.setForeground(UIConstants.TEXT_SECONDARY);

        text.add(valueLbl, "growx, wrap");
        text.add(desc,     "growx");

        card.add(iconPanel, "cell 0 0, top, gaptop 2");
        card.add(text,      "cell 1 0, grow, gapleft 14");

        // ── Hover lift ────────────────────────────────────────────────────────
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                shadowRadius[0] = 12; shadowY[0] = 6; shadowAlpha[0] = 42;
                card.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                shadowRadius[0] = 6;  shadowY[0] = 3; shadowAlpha[0] = 28;
                card.repaint();
            }
        });

        return card;
    }

    // =========================================================================
    // Java2D icon painters (cx/cy = centre of 38×38 circle)
    // =========================================================================

    /** Building / store silhouette */
    private void drawStoreIcon(Graphics2D g, int cx, int cy) {
        // Roof triangle
        int[] px = { cx - 9, cx, cx + 9 };
        int[] py = { cy - 1, cy - 9, cy - 1 };
        g.drawPolyline(px, py, 3);
        // Body rectangle
        g.drawRect(cx - 7, cy - 1, 14, 10);
        // Door
        g.drawRect(cx - 3, cy + 3, 6, 6);
    }

    /** Plus sign (new item) */
    private void drawPlusIcon(Graphics2D g, int cx, int cy) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx, cy - 8, cx, cy + 8);
        g.drawLine(cx - 8, cy, cx + 8, cy);
    }

    /** Coin (currency) */
    private void drawCoinIcon(Graphics2D g, int cx, int cy) {
        g.drawOval(cx - 9, cy - 9, 18, 18);
        // Vertical bar
        g.drawLine(cx, cy - 5, cx, cy + 5);
        // Top arc
        g.drawArc(cx - 4, cy - 5, 8, 5, 0, 180);
        // Bottom arc
        g.drawArc(cx - 4, cy,     8, 5, 180, 180);
    }

    /** Package / box */
    private void drawBoxIcon(Graphics2D g, int cx, int cy) {
        // Box body
        g.drawRect(cx - 8, cy - 3, 16, 12);
        // Lid top line
        g.drawLine(cx - 10, cy - 5, cx + 10, cy - 5);
        // Lid sides
        g.drawLine(cx - 10, cy - 5, cx - 8, cy - 3);
        g.drawLine(cx + 10, cy - 5, cx + 8, cy - 3);
        // Tie ribbon
        g.drawLine(cx - 8, cy - 3, cx + 8, cy - 3);
        g.drawLine(cx, cy - 5, cx, cy + 9);
    }

    // =========================================================================
    // Refresh / countdown logic
    // =========================================================================

    private void startCountdown() {
        stopCountdown();
        secondsLeft = 30;
        countdownTimer = new Timer(1000, e -> {
            if (secondsLeft > 0) secondsLeft--;
            if (lblRefreshText != null) {
                if (secondsLeft > 0) {
                    lblRefreshText.setText("Cap nhat sau: " + secondsLeft + "s");
                } else {
                    lblRefreshText.setText("Dang tai...");
                }
            }
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null) { countdownTimer.stop(); countdownTimer = null; }
    }

    /** Background load of stats; updates UI on EDT. */
    public void refreshStats() {
        // Mark loading
        if (lblRefreshDot  != null) lblRefreshDot.setForeground(new Color(0xF59E0B));  // amber = loading
        if (lblRefreshText != null) lblRefreshText.setText("Dang tai...");

        new SwingWorker<Map<String, Long>, Void>() {
            private String restaurantName;

            @Override
            protected Map<String, Long> doInBackground() {
                Restaurant r = DataManager.getInstance().getMyRestaurant();
                restaurantName = (r != null && r.getName() != null) ? r.getName() : "";
                return new StatsDAO().getAdminDashboardStats();
            }

            @Override
            protected void done() {
                try {
                    Map<String, Long> s = get();

                    lblActiveRestaurants.setText(String.valueOf(s.get("active_restaurants")));
                    lblNewRestaurants   .setText(String.valueOf(s.get("new_restaurants")));
                    lblRevenue          .setText(String.format("%,.0f d", (double) s.get("revenue_today")));
                    lblOrderCount       .setText(String.valueOf(s.get("orders_today")));
                    if (lblRestaurantName != null) lblRestaurantName.setText(restaurantName);

                    updateActivityList(s);

                    // Reset countdown
                    secondsLeft = 30;
                    if (lblRefreshDot  != null) lblRefreshDot.setForeground(new Color(0x10B981));   // green = ok
                    if (lblRefreshText != null) lblRefreshText.setText("Cap nhat sau: 30s");

                } catch (InterruptedException | ExecutionException ex) {
                    System.err.println("[HomePanel] refreshStats loi: " + ex.getMessage());
                    if (lblRefreshDot  != null) lblRefreshDot.setForeground(new Color(0xEF4444));   // red = error
                    if (lblRefreshText != null) lblRefreshText.setText("Loi tai du lieu");
                }
            }
        }.execute();
    }

    /** Populate the recent-activity JList from the stats map. */
    private void updateActivityList(Map<String, Long> s) {
        if (activityModel == null) return;
        activityModel.clear();

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        long active  = s.getOrDefault("active_restaurants", 0L);
        long newR    = s.getOrDefault("new_restaurants",    0L);
        long orders  = s.getOrDefault("orders_today",       0L);
        long revenue = s.getOrDefault("revenue_today",      0L);

        activityModel.addElement(ts + "  He thong co " + active + " nha hang dang hoat dong");
        if (newR > 0) {
            activityModel.addElement(ts + "  " + newR + " nha hang moi duoc dang ky hom nay");
        }
        activityModel.addElement(ts + "  " + orders + " don hang da duoc tao hom nay");
        activityModel.addElement(ts + "  Doanh thu hom nay: " + String.format("%,.0f d", (double) revenue));

        if (activityModel.isEmpty()) {
            activityModel.addElement("Chua co hoat dong nao trong ngay hom nay.");
        }
    }

    /** Called by MainFrame.navigateTo("home") */
    public void refresh() { refreshStats(); }

    // =========================================================================
    // Helpers
    // =========================================================================

    private JLabel makeValueLabel(String initial) {
        JLabel lbl = new JLabel(initial);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        return lbl;
    }

    /**
     * Blends two colours.  t=0 → pure base, t=1 → pure overlay.
     */
    private static Color blend(Color base, Color overlay, float t) {
        float s = 1 - t;
        return new Color(
            Math.min(255, (int)(base.getRed()   * s + overlay.getRed()   * t)),
            Math.min(255, (int)(base.getGreen() * s + overlay.getGreen() * t)),
            Math.min(255, (int)(base.getBlue()  * s + overlay.getBlue()  * t))
        );
    }

    // =========================================================================
    // Activity list cell renderer
    // =========================================================================

    private static class ActivityCellRenderer extends DefaultListCellRenderer {

        private static final Color ROW_EVEN = Color.WHITE;
        private static final Color ROW_ODD  = new Color(0xF9FAFB);
        private static final Color ROW_SEL  = new Color(0xEFF6FF);
        private static final Color SEP      = new Color(0xEEEEEE);

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            JLabel lbl = (JLabel) super.getListCellRendererComponent(
                    list, " " + value, index, isSelected, cellHasFocus);

            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lbl.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, SEP),
                    BorderFactory.createEmptyBorder(4, 10, 4, 10)));

            if (!isSelected) {
                lbl.setBackground(index % 2 == 0 ? ROW_EVEN : ROW_ODD);
                lbl.setForeground(new Color(0x374151));
            } else {
                lbl.setBackground(ROW_SEL);
                lbl.setForeground(new Color(0x1D4ED8));
            }
            return lbl;
        }
    }
}