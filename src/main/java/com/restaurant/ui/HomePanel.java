package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
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

        // ── Greeting header ──────────────────────────────────────────────────
        String userName = AppSession.getInstance().getUserName();
        JLabel greeting = new JLabel("Xin chào, " + userName + " 👋");
        greeting.setFont(new Font("Segoe UI", Font.BOLD, 18));
        greeting.setForeground(UIConstants.TEXT_PRIMARY);
        greeting.setAlignmentX(LEFT_ALIGNMENT);

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        JLabel subtitle = new JLabel("Đây là tổng quan hệ thống hôm nay — " + today);
        subtitle.setFont(UIConstants.FONT_BODY);
        subtitle.setForeground(UIConstants.TEXT_SECONDARY);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        content.add(greeting);
        content.add(Box.createVerticalStrut(4));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(24));

        // lblRestaurantName: field giữ lại nhưng không thêm vào layout
        lblRestaurantName = new JLabel(" ");
        lblRestaurantName.setFont(UIConstants.FONT_BODY);
        lblRestaurantName.setForeground(UIConstants.TEXT_SECONDARY);

        // ── 4 stat cards in 2×2 grid ─────────────────────────────────────────
        lblActiveRestaurants = makeValueLabel("—");
        lblNewRestaurants    = makeValueLabel("—");
        lblRevenue           = makeValueLabel("—");
        lblOrderCount        = makeValueLabel("—");

        JPanel cardsPanel = new JPanel(new GridLayout(2, 2, 16, 16));
        cardsPanel.setOpaque(false);
        cardsPanel.setAlignmentX(LEFT_ALIGNMENT);

        cardsPanel.add(buildStatCard("🏪", "Nhà hàng hoạt động", lblActiveRestaurants, new Color(0xEFF6FF)));
        cardsPanel.add(buildStatCard("🆕", "Nhà hàng mới hôm nay", lblNewRestaurants,    new Color(0xF0FDF4)));
        cardsPanel.add(buildStatCard("💰", "Doanh thu hôm nay",     lblRevenue,           new Color(0xFFFBEB)));
        cardsPanel.add(buildStatCard("📦", "Đơn hàng hôm nay",      lblOrderCount,        new Color(0xFFF1F2)));

        content.add(cardsPanel);

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

    /**
     * Build a rounded stat card with accent background, emoji icon,
     * a large value label, and a small description label underneath.
     *
     * @param icon        Emoji shown top-left (28 px)
     * @param label       Description text shown below the value
     * @param valueLbl    The JLabel whose text is updated by refreshStats()
     * @param accentColor Background fill colour for the card
     */
    private JPanel buildStatCard(String icon, String label, JLabel valueLbl, Color accentColor) {
        // Derive a slightly darker border from accentColor (reduce each channel by 20%)
        Color borderColor = new Color(
                Math.max(0, (int) (accentColor.getRed()   * 0.80)),
                Math.max(0, (int) (accentColor.getGreen() * 0.80)),
                Math.max(0, (int) (accentColor.getBlue()  * 0.80))
        );

        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accentColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        UIConstants.CARD_RADIUS, UIConstants.CARD_RADIUS);
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        UIConstants.CARD_RADIUS, UIConstants.CARD_RADIUS);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Icon label — top-left
        JLabel iconLbl = new JLabel(icon);
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        card.add(iconLbl, BorderLayout.NORTH);

        // Centre panel: value + description stacked vertically
        JPanel centre = new JPanel();
        centre.setOpaque(false);
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
        centre.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        valueLbl.setFont(UIConstants.FONT_TITLE);          // 22 px bold
        valueLbl.setForeground(UIConstants.TEXT_PRIMARY);
        valueLbl.setAlignmentX(LEFT_ALIGNMENT);

        JLabel descLbl = new JLabel(label);
        descLbl.setFont(UIConstants.FONT_SMALL);           // 11 px
        descLbl.setForeground(UIConstants.TEXT_SECONDARY);
        descLbl.setAlignmentX(LEFT_ALIGNMENT);

        centre.add(valueLbl);
        centre.add(Box.createVerticalStrut(2));
        centre.add(descLbl);

        card.add(centre, BorderLayout.CENTER);

        return card;
    }

    private JLabel makeValueLabel(String initial) {
        JLabel lbl = new JLabel(initial);
        lbl.setFont(UIConstants.FONT_BOLD);
        return lbl;
    }
}