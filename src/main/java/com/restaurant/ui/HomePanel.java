package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import com.restaurant.dao.StatsDAO;
import com.restaurant.data.DataManager;
import com.restaurant.model.Restaurant;
import com.restaurant.session.AppSession;

public class HomePanel extends JPanel {

    // ── Stats labels (updated by refreshStats) ────────────────────────────────

    private JLabel lblActiveRestaurants;
    private JLabel lblNewRestaurants;
    private JLabel lblRevenue;
    private JLabel lblOrderCount;

    /** G2: hiển thị tên nhà hàng đang đăng nhập. */
    private JLabel lblRestaurantName;

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

        // lblRestaurantName: field được giữ lại nhưng không thêm vào layout (Phase 1)
        lblRestaurantName = new JLabel(" ");
        lblRestaurantName.setFont(UIConstants.FONT_BODY);
        lblRestaurantName.setForeground(UIConstants.TEXT_SECONDARY);
        lblRestaurantName.setAlignmentX(CENTER_ALIGNMENT);

        content.add(Box.createVerticalStrut(20));

        // ── Section: Thống kê nhanh (hôm nay) ──
        JLabel sectionLabel = new JLabel("Thống kê nhanh (hôm nay)");
        sectionLabel.setFont(UIConstants.FONT_BOLD);
        sectionLabel.setForeground(UIConstants.TEXT_PRIMARY);
        content.add(sectionLabel);
        content.add(Box.createVerticalStrut(10));

        JPanel statsPanel = new JPanel(new GridBagLayout());
        statsPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(6, 0, 6, 0);
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        lblActiveRestaurants = makeValueLabel("—");
        lblNewRestaurants    = makeValueLabel("—");
        lblRevenue           = makeValueLabel("—");
        lblOrderCount        = makeValueLabel("—");

        addStatRow(statsPanel, gbc, 0, "Số nhà hàng đang hoạt động:", lblActiveRestaurants, UIConstants.TEXT_PRIMARY);
        addStatRow(statsPanel, gbc, 1, "Số nhà hàng mới tạo :",       lblNewRestaurants,    UIConstants.TEXT_PRIMARY);
        addStatRow(statsPanel, gbc, 2, "Doanh thu:",                   lblRevenue,           UIConstants.TEXT_PRIMARY);
        addStatRow(statsPanel, gbc, 3, "Số đơn hàng:",                 lblOrderCount,        UIConstants.TEXT_PRIMARY);
        content.add(statsPanel);

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
        new SwingWorker<Map<String, Long>, Void>() {
            private String restaurantName;

            @Override
            protected Map<String, Long> doInBackground() {
                // G2: piggybacked onto the existing poll — DataManager cache absorbs cost
                Restaurant r = DataManager.getInstance().getMyRestaurant();
                restaurantName = (r != null && r.getName() != null)
                        ? "🏪 " + r.getName() : "";
                return new StatsDAO().getAdminDashboardStats();
            }

            @Override
            protected void done() {
                try {
                    Map<String, Long> s = get();
                    lblActiveRestaurants.setText(String.valueOf(s.get("active_restaurants")));
                    lblNewRestaurants   .setText(String.valueOf(s.get("new_restaurants")));
                    lblRevenue          .setText(String.format("%,.0f", (double) s.get("revenue_today")));
                    lblOrderCount       .setText(String.valueOf(s.get("orders_today")));
                    // G2: cập nhật restaurantName (field giữ nguyên theo spec)
                    if (lblRestaurantName != null) {
                        lblRestaurantName.setText(restaurantName);
                    }
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
        lbl.setPreferredSize(new Dimension(230, 36));
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
