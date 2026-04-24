package com.restaurant.ui;

import com.restaurant.dao.StatsDAO;
import com.restaurant.session.AppSession;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class HomePanel extends JPanel {

    // ── Stats labels (updated by refreshStats) ────────────────────────────────

    private JLabel lblAvailable;
    private JLabel lblOccupied;
    private JLabel lblDirty;
    private JLabel lblPending;
    private JLabel lblCompletedToday;

    private static final String POLL_KEY = "home_stats";

    // ── Constructor ───────────────────────────────────────────────────────────

    public HomePanel() {
        setBackground(UIConstants.BG_PAGE);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(32, 60, 32, 60));
        buildUI();
        refreshStats(); // initial load

        // Register 15s polling — must be on EDT, constructor is called on EDT
        PollManager.getInstance().register(POLL_KEY, this::refreshStats, 15_000);

        // Unregister when panel hidden, re-register when shown
        addAncestorListener(new AncestorListener() {
            @Override public void ancestorAdded(AncestorEvent e) {
                PollManager.getInstance().register(POLL_KEY, HomePanel.this::refreshStats, 15_000);
            }
            @Override public void ancestorRemoved(AncestorEvent e) {
                PollManager.getInstance().unregister(POLL_KEY);
            }
            @Override public void ancestorMoved(AncestorEvent e) {}
        });
    }

    // ── Build static UI ───────────────────────────────────────────────────────

    private void buildUI() {
        removeAll();

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // Title
        JLabel title = new JLabel("Tổng quan");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);
        title.setAlignmentX(CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(20));

        // ── Section: Trạng thái bàn ──
        content.add(makeSectionLabel("🪑  Trạng thái bàn"));
        content.add(Box.createVerticalStrut(10));

        JPanel tableStats = new JPanel(new GridBagLayout());
        tableStats.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(6, 0, 6, 0);
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        lblAvailable = makeValueLabel("—");
        lblOccupied  = makeValueLabel("—");
        lblDirty     = makeValueLabel("—");

        addStatRow(tableStats, gbc, 0, "Bàn trống (AVAILABLE):", lblAvailable, UIConstants.SUCCESS);
        addStatRow(tableStats, gbc, 1, "Bàn đang phục vụ:",      lblOccupied,  UIConstants.WARNING);
        addStatRow(tableStats, gbc, 2, "Bàn cần dọn (DIRTY):",   lblDirty,     UIConstants.DANGER);
        content.add(tableStats);
        content.add(Box.createVerticalStrut(22));

        // ── Section: Đơn hàng ──
        content.add(makeSectionLabel("📋  Đơn hàng hôm nay"));
        content.add(Box.createVerticalStrut(10));

        JPanel orderStats = new JPanel(new GridBagLayout());
        orderStats.setOpaque(false);
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets  = new Insets(6, 0, 6, 0);
        gbc2.fill    = GridBagConstraints.HORIZONTAL;
        gbc2.weightx = 1;

        lblPending        = makeValueLabel("—");
        lblCompletedToday = makeValueLabel("—");

        addStatRow(orderStats, gbc2, 0, "Đang chờ / đang nấu:", lblPending,        UIConstants.PRIMARY);
        addStatRow(orderStats, gbc2, 1, "Hoàn tất hôm nay:",    lblCompletedToday, UIConstants.SUCCESS);
        content.add(orderStats);

        add(content, BorderLayout.NORTH);
        revalidate();
        repaint();
    }

    // ── Refresh stats ─────────────────────────────────────────────────────────

    /**
     * Load stats on a background thread; update labels on EDT.
     * Called by PollManager every 15 s and on demand.
     */
    public void refreshStats() {
        long restaurantId = AppSession.getInstance().getRestaurantId();

        new SwingWorker<Map<String, Integer>, Void>() {
            @Override
            protected Map<String, Integer> doInBackground() {
                return new StatsDAO().getDashboardStats(restaurantId);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Integer> s = get();
                    lblAvailable     .setText(String.valueOf(s.get("tables_available")));
                    lblOccupied      .setText(String.valueOf(s.get("tables_occupied")));
                    lblDirty         .setText(String.valueOf(s.get("tables_dirty")));
                    lblPending       .setText(String.valueOf(s.get("orders_pending")));
                    lblCompletedToday.setText(String.valueOf(s.get("orders_completed_today")));
                } catch (InterruptedException | ExecutionException ex) {
                    System.err.println("[HomePanel] refreshStats lỗi: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /** Called by MainFrame.navigateTo("home") — triggers a stats refresh. */
    public void refresh() {
        refreshStats();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private JLabel makeSectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_SECONDARY);
        return lbl;
    }

    private JLabel makeValueLabel(String initial) {
        JLabel lbl = new JLabel(initial);
        lbl.setFont(UIConstants.FONT_BOLD);
        return lbl;
    }

    private void addStatRow(JPanel panel, GridBagConstraints gbc,
                            int row, String labelText, JLabel valueLabel, Color valueColor) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        lbl.setPreferredSize(new Dimension(210, 36));
        panel.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        JPanel valueBox = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.setColor(UIConstants.BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        UIConstants.CORNER_RADIUS, UIConstants.CORNER_RADIUS);
                g2.dispose();
            }
        };
        valueBox.setOpaque(false);
        valueBox.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        valueBox.setPreferredSize(new Dimension(0, 40));
        valueLabel.setForeground(valueColor);
        valueBox.add(valueLabel, BorderLayout.WEST);
        panel.add(valueBox, gbc);
    }
}
