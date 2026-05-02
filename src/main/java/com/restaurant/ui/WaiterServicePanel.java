package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.AbstractBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.Order;
import com.restaurant.model.TableItem;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

/**
 * Màn hình phục vụ bàn dành cho role WAITER — Phase 7C (Polling + Toast delta).
 *
 * <p>Gồm 3 tab:
 * <ol>
 *   <li><b>Phục vụ bàn</b> – danh sách lượt bàn có món READY.</li>
 *   <li><b>Dọn bàn</b>     – danh sách bàn DIRTY / CLEANING.</li>
 *   <li><b>Đã hủy</b>      – danh sách đơn/món bị hủy trong ngày.</li>
 * </ol>
 *
 * <h3>Refactor StaffHeader</h3>
 * <ul>
 *   <li>Dùng {@link StaffHeader#create(String, String, Runnable)} thay thế.</li>
 * </ul>
 *
 * <h3>Refactor EmptyState</h3>
 * <ul>
 *   <li>Dùng {@link EmptyStatePanel} dùng chung — xóa {@code buildEmptyState()}.</li>
 * </ul>
 *
 * RBAC: yêu cầu {@link Permission#VIEW_WAITER_SERVICE}.
 */
public class WaiterServicePanel extends JPanel {

    // ─── DAOs ─────────────────────────────────────────────────────────────────

    private final KitchenDAO kitchenDAO = new KitchenDAO();
    private final TableDAO   tableDAO   = new TableDAO();

    // ─── Polling state ────────────────────────────────────────────────────────

    private int lastServeCount = -1;
    private int lastCleanCount = -1;

    // ─── UI panels ────────────────────────────────────────────────────────────

    private JPanel deliveryCardsPanel;
    private JPanel cleanTablePanel;
    private JPanel cancelledPanel;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public WaiterServicePanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BG_PAGE);

        // RBAC guard
        if (!AppSession.getInstance().hasPermission(Permission.VIEW_WAITER_SERVICE)) {
            JLabel denied = new JLabel("Không có quyền truy cập", SwingConstants.CENTER);
            denied.setFont(UIConstants.FONT_TITLE);
            denied.setForeground(UIConstants.TEXT_SECONDARY);
            add(denied, BorderLayout.CENTER);
            return;
        }

        // ── StaffHeader ───────────────────────────────────────────────────────
        String rName = "";
        try {
            rName = com.restaurant.data.DataManager.getInstance()
                    .getMyRestaurant().getName();
        } catch (Exception ignored) {}

        add(StaffHeader.create("Phục vụ", rName, () -> {
            PollManager.getInstance().unregister("waiter_v2");
            java.awt.Window w = SwingUtilities.getWindowAncestor(WaiterServicePanel.this);
            if (w != null) w.dispose();
        }), BorderLayout.NORTH);

        add(buildTabs(), BorderLayout.CENTER);
        setupComponentListener();
    }

    // ─── Tabs ─────────────────────────────────────────────────────────────────

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(UIConstants.FONT_BOLD);
        tabs.setBackground(UIConstants.BG_PAGE);

        tabs.addTab("🚚  Phục vụ bàn", buildDeliveryTab());
        tabs.addTab("🧹  Dọn bàn",     buildCleanTab());
        tabs.addTab("🚫  Đã hủy",      buildCancelledTab());

        // Refresh tab "Đã hủy" khi người dùng chuyển sang
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 2) {
                new SwingWorker<List<KitchenDAO.KitchenTicket>, Void>() {
                    @Override
                    protected List<KitchenDAO.KitchenTicket> doInBackground() {
                        return kitchenDAO.getCancelledItems(
                                AppSession.getInstance().getRestaurantId());
                    }
                    @Override
                    protected void done() {
                        try {
                            rebuildCancelledTab(get());
                        } catch (Exception ex) {
                            System.err.println("[WaiterServicePanel] tabChange cancelled: "
                                    + ex.getMessage());
                            showInlineError("Không thể tải danh sách đã hủy: " + ex.getMessage());
                        }
                    }
                }.execute();
            }
        });

        return tabs;
    }

    // ─── Tab 1: Phục vụ bàn ──────────────────────────────────────────────────

    private JPanel buildDeliveryTab() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(UIConstants.BG_PAGE);

        deliveryCardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 14, 14));
        deliveryCardsPanel.setBackground(UIConstants.BG_PAGE);
        deliveryCardsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(deliveryCardsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    // ─── Tab 2: Dọn bàn ──────────────────────────────────────────────────────

    private JPanel buildCleanTab() {
        cleanTablePanel = new JPanel(new BorderLayout());
        cleanTablePanel.setBackground(UIConstants.BG_PAGE);
        return cleanTablePanel;
    }

    // ─── Tab 3: Đã hủy ───────────────────────────────────────────────────────

    private JPanel buildCancelledTab() {
        cancelledPanel = new JPanel(new BorderLayout());
        cancelledPanel.setBackground(UIConstants.BG_PAGE);
        return cancelledPanel;
    }

    // ─── showInlineError ──────────────────────────────────────────────────────

    private void showInlineError(String msg) {
        for (Component c : getComponents()) {
            if (c instanceof JLabel
                    && Boolean.TRUE.equals(((JLabel) c).getClientProperty("inlineError"))) {
                remove(c);
            }
        }

        JLabel errLabel = new JLabel("⚠  " + msg);
        errLabel.putClientProperty("inlineError", Boolean.TRUE);
        errLabel.setFont(UIConstants.FONT_SMALL);
        errLabel.setForeground(UIConstants.DANGER);
        errLabel.setOpaque(true);
        errLabel.setBackground(new Color(0xFEE2E2));
        errLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xFCA5A5)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));

        add(errLabel, BorderLayout.SOUTH);
        revalidate();
        repaint();

        Timer hideTimer = new Timer(5000, e -> {
            remove(errLabel);
            revalidate();
            repaint();
        });
        hideTimer.setRepeats(false);
        hideTimer.start();
    }

    // ─── ComponentListener — Phase 7C ─────────────────────────────────────────

    private void setupComponentListener() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadData();
                PollManager.getInstance().register(
                        "waiter_v2",
                        WaiterServicePanel.this::doPoll,
                        5_000);
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                PollManager.getInstance().unregister("waiter_v2");
                lastServeCount = -1;
                lastCleanCount = -1;
            }
        });
    }

    // ─── doPoll — Phase 7C ───────────────────────────────────────────────────

    private void doPoll() {
        new SwingWorker<WaiterPollData, Void>() {

            @Override
            protected WaiterPollData doInBackground() {
                long rid = AppSession.getInstance().getRestaurantId();
                Map<String, List<KitchenDAO.KitchenTicket>> readyMap =
                        kitchenDAO.getReadyByTable(rid);
                List<TableItem> dirtyList = kitchenDAO.getDirtyTables(rid);
                return new WaiterPollData(readyMap, dirtyList);
            }

            @Override
            protected void done() {
                WaiterPollData data;
                try {
                    data = get();
                } catch (ExecutionException | InterruptedException ex) {
                    showInlineError("Lỗi tải dữ liệu: " + ex.getMessage());
                    return;
                }

                rebuildDeliveryCards(data.readyMap);
                rebuildCleanCards(data.dirtyList);

                // ── Toast delta: "Phục vụ bàn" ──
                int newServe = (data.readyMap != null) ? data.readyMap.size() : 0;
                if (lastServeCount >= 0 && newServe > lastServeCount) {
                    int diff = newServe - lastServeCount;
                    ToastNotification.show(
                            WaiterServicePanel.this,
                            "Có " + diff + " bàn cần phục vụ!",
                            ToastNotification.Type.INFO);
                }
                lastServeCount = newServe;

                // ── Toast delta: "Dọn bàn" ──
                int newClean = (data.dirtyList != null) ? data.dirtyList.size() : 0;
                if (lastCleanCount >= 0 && newClean > lastCleanCount) {
                    int diff = newClean - lastCleanCount;
                    ToastNotification.show(
                            WaiterServicePanel.this,
                            "Có " + diff + " bàn cần dọn!",
                            ToastNotification.Type.INFO);
                }
                lastCleanCount = newClean;
            }
        }.execute();
    }

    // ─── loadData (full load, gọi lần đầu) ───────────────────────────────────

    public void loadData() {
        new SwingWorker<Void, Void>() {
            Map<String, List<KitchenDAO.KitchenTicket>> readyMap;
            List<TableItem>                              dirtyList;
            List<KitchenDAO.KitchenTicket>               cancelledList;

            @Override
            protected Void doInBackground() {
                long rid = AppSession.getInstance().getRestaurantId();
                readyMap      = kitchenDAO.getReadyByTable(rid);
                dirtyList     = kitchenDAO.getDirtyTables(rid);
                cancelledList = kitchenDAO.getCancelledItems(rid);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    rebuildDeliveryCards(readyMap);
                    rebuildCleanCards(dirtyList);
                    rebuildCancelledTab(cancelledList);

                    lastServeCount = (readyMap  != null) ? readyMap.size()  : 0;
                    lastCleanCount = (dirtyList != null) ? dirtyList.size() : 0;

                } catch (Exception ex) {
                    showInlineError("Lỗi tải dữ liệu: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ─── Rebuild delivery cards (Tab 1) ──────────────────────────────────────

    private void rebuildDeliveryCards(
            Map<String, List<KitchenDAO.KitchenTicket>> map) {

        deliveryCardsPanel.removeAll();

        if (map == null || map.isEmpty()) {
            deliveryCardsPanel.setLayout(new BorderLayout());
            deliveryCardsPanel.add(
                    new EmptyStatePanel("🛎", "Không có bàn nào cần phục vụ",
                            "Tất cả đang ổn ✓"),
                    BorderLayout.CENTER);
            deliveryCardsPanel.revalidate();
            deliveryCardsPanel.repaint();
            return;
        }

        deliveryCardsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 14, 14));

        for (Map.Entry<String, List<KitchenDAO.KitchenTicket>> entry : map.entrySet()) {
            deliveryCardsPanel.add(buildDeliveryCard(entry.getValue()));
        }

        deliveryCardsPanel.revalidate();
        deliveryCardsPanel.repaint();
    }

    // ─── Rebuild clean table (Tab 2) ─────────────────────────────────────────

    private void rebuildCleanCards(List<TableItem> tables) {
        cleanTablePanel.removeAll();

        if (tables == null || tables.isEmpty()) {
            cleanTablePanel.add(
                    new EmptyStatePanel("🧹", "Không có bàn nào cần dọn", null),
                    BorderLayout.CENTER);
        } else {
            cleanTablePanel.add(buildCleanTable(tables), BorderLayout.CENTER);
        }

        cleanTablePanel.revalidate();
        cleanTablePanel.repaint();
    }

    private JScrollPane buildCleanTable(List<TableItem> tables) {
        String[] cols = {"Tên bàn", "Sức chứa", "Trạng thái", "Hành động"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return c == 3; }
        };
        for (TableItem t : tables) {
            model.addRow(new Object[]{
                    t.getName(), t.getCapacity(), t.getStatusDisplay(), t
            });
        }

        JTable table = new JTable(model);
        table.setFont(UIConstants.FONT_BODY);
        table.setRowHeight(UIConstants.ROW_HEIGHT + 4);
        table.setShowGrid(true);
        table.setGridColor(UIConstants.BORDER_COLOR);
        table.setSelectionBackground(UIConstants.ROW_SELECTED);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(UIConstants.FONT_HEADER);
        table.getTableHeader().setBackground(UIConstants.HEADER_BG);
        table.getTableHeader().setForeground(UIConstants.TEXT_PRIMARY);
        table.getTableHeader().setReorderingAllowed(false);

        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);

        // Status renderer (cột 2)
        table.getColumnModel().getColumn(2).setCellRenderer(
                (tbl, val, sel, foc, row, col) -> {
                    TableItem item = tables.get(row);
                    JLabel lbl = new JLabel(item.getStatusDisplay(), SwingConstants.CENTER);
                    lbl.setFont(UIConstants.FONT_SMALL);
                    lbl.setOpaque(true);
                    if (item.getStatus() == TableItem.Status.DIRTY) {
                        lbl.setBackground(new Color(0xFEF3C7));
                        lbl.setForeground(new Color(0x92400E));
                    } else {
                        lbl.setBackground(new Color(0xFCE7F3));
                        lbl.setForeground(new Color(0x9D174D));
                    }
                    return lbl;
                });

        // Action renderer & editor (cột 3)
        table.getColumnModel().getColumn(3).setCellRenderer(new CleanActionRenderer(tables));
        table.getColumnModel().getColumn(3).setCellEditor(
                new CleanActionEditor(tables, tableDAO, this::loadData));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        return scroll;
    }

    // ─── Rebuild cancelled tab (Tab 3) ───────────────────────────────────────

    private void rebuildCancelledTab(List<KitchenDAO.KitchenTicket> items) {
        cancelledPanel.removeAll();

        if (items == null || items.isEmpty()) {
            cancelledPanel.add(
                    new EmptyStatePanel("✅", "Không có món nào bị hủy hôm nay", null),
                    BorderLayout.CENTER);
            cancelledPanel.revalidate();
            cancelledPanel.repaint();
            return;
        }

        int totalQty = items.stream().mapToInt(t -> t.quantity).sum();

        JPanel statsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        statsBar.setBackground(UIConstants.BG_WHITE);
        statsBar.setBorder(new MatteBorder(0, 0, 1, 0, UIConstants.BORDER_COLOR));

        JLabel lblCount = new JLabel("Hôm nay: " + items.size() + " đơn hủy");
        lblCount.setFont(UIConstants.FONT_BODY);
        lblCount.setForeground(UIConstants.TEXT_SECONDARY);

        JLabel lblSep = new JLabel("  |  ");
        lblSep.setForeground(UIConstants.TEXT_SECONDARY);

        JLabel lblQty = new JLabel("Tổng số lượng: " + totalQty + " món");
        lblQty.setFont(UIConstants.FONT_BOLD);
        lblQty.setForeground(UIConstants.DANGER);

        statsBar.add(lblCount);
        statsBar.add(lblSep);
        statsBar.add(lblQty);

        cancelledPanel.add(statsBar, BorderLayout.NORTH);
        cancelledPanel.add(buildCancelledTable(items), BorderLayout.CENTER);
        cancelledPanel.revalidate();
        cancelledPanel.repaint();
    }

    private JScrollPane buildCancelledTable(List<KitchenDAO.KitchenTicket> items) {
        String[] cols = {"Bàn", "Tên món", "SL", "Lượt", "Thời gian hủy"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm  dd/MM");
        for (KitchenDAO.KitchenTicket t : items) {
            String timeStr = (t.createdAt != null) ? t.createdAt.format(fmt) : "—";
            model.addRow(new Object[]{
                    t.tableName, t.itemName, t.quantity, t.roundNumber, timeStr
            });
        }

        StyledTable table = new StyledTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        table.getColumnModel().getColumn(4).setPreferredWidth(150);

        DefaultTableCellRenderer centerR = new DefaultTableCellRenderer();
        centerR.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(2).setCellRenderer(centerR);
        table.getColumnModel().getColumn(3).setCellRenderer(centerR);

        JScrollPane scroll = StyledTable.wrap(table);
        scroll.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        return scroll;
    }

    // ─── Card builder (Tab 1) ────────────────────────────────────────────────

    private JPanel buildDeliveryCard(List<KitchenDAO.KitchenTicket> tickets) {
        KitchenDAO.KitchenTicket first = tickets.get(0);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(8, UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        card.setPreferredSize(new Dimension(260, 46 + tickets.size() * 34 + 58));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel lblTitle = new JLabel(
                "Bàn " + first.tableName + "  ·  Lượt " + first.roundNumber);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(UIConstants.TEXT_PRIMARY);
        lblTitle.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblTitle);
        card.add(Box.createVerticalStrut(10));

        boolean allDelivered = tickets.stream()
                .allMatch(t -> t.itemStatus == Order.OrderItem.ItemStatus.DELIVERED);
        boolean allDelivering = tickets.stream()
                .allMatch(t -> t.itemStatus == Order.OrderItem.ItemStatus.DELIVERING
                            || t.itemStatus == Order.OrderItem.ItemStatus.DELIVERED);

        for (KitchenDAO.KitchenTicket t : tickets) {
            card.add(buildItemRow(t));
            card.add(Box.createVerticalStrut(6));
        }

        JSeparator sep = new JSeparator();
        sep.setForeground(UIConstants.BORDER_COLOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(LEFT_ALIGNMENT);
        card.add(sep);
        card.add(Box.createVerticalStrut(10));

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 8, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIConstants.BTN_HEIGHT));

        JButton btnDeliv = makeActionButton("🚶 Đang mang", UIConstants.WARNING, Color.WHITE);
        btnDeliv.setEnabled(!allDelivering);
        btnDeliv.addActionListener(e -> {
            btnDeliv.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    for (KitchenDAO.KitchenTicket t : tickets) {
                        if (t.itemStatus == Order.OrderItem.ItemStatus.READY) {
                            kitchenDAO.updateItemStatus(t.itemId,
                                    Order.OrderItem.ItemStatus.DELIVERING);
                        }
                    }
                    return null;
                }
                @Override protected void done() {
                    try { get(); } catch (Exception ex) {
                        showInlineError("Lỗi cập nhật trạng thái: " + ex.getMessage());
                    }
                    loadData();
                }
            }.execute();
        });

        JButton btnDone = makeActionButton("✔ Đã giao xong", UIConstants.SUCCESS, Color.WHITE);
        btnDone.setEnabled(!allDelivered);
        btnDone.addActionListener(e -> {
            btnDone.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    for (KitchenDAO.KitchenTicket t : tickets) {
                        if (t.itemStatus != Order.OrderItem.ItemStatus.DELIVERED) {
                            kitchenDAO.updateItemStatus(t.itemId,
                                    Order.OrderItem.ItemStatus.DELIVERED);
                        }
                    }
                    return null;
                }
                @Override protected void done() {
                    try { get(); } catch (Exception ex) {
                        showInlineError("Lỗi cập nhật trạng thái: " + ex.getMessage());
                    }
                    ToastNotification.show(
                            WaiterServicePanel.this,
                            "Đã giao xong bàn " + first.tableName + "!",
                            ToastNotification.Type.SUCCESS);
                    loadData();
                }
            }.execute();
        });

        btnRow.add(btnDeliv);
        btnRow.add(btnDone);
        card.add(btnRow);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                card.setBackground(new Color(0xF0F9FF));
            }
            @Override public void mouseExited(MouseEvent e) {
                card.setBackground(Color.WHITE);
            }
        });

        return card;
    }

    // ─── Item row ─────────────────────────────────────────────────────────────

    private JPanel buildItemRow(KitchenDAO.KitchenTicket t) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel nameQty = new JLabel(t.itemName + " × " + t.quantity);
        nameQty.setFont(UIConstants.FONT_BODY);

        row.add(nameQty,                BorderLayout.WEST);
        row.add(makeBadge(t.itemStatus), BorderLayout.EAST);
        return row;
    }

    // ─── Badge factory ────────────────────────────────────────────────────────

    private JLabel makeBadge(Order.OrderItem.ItemStatus s) {
        String text;
        Color  bg, fg;
        switch (s) {
            case READY:
                text = "Sẵn sàng";  bg = new Color(0xD1FAE5); fg = new Color(0x065F46); break;
            case DELIVERING:
                text = "Đang mang"; bg = new Color(0xDBEAFE); fg = new Color(0x1E40AF); break;
            case DELIVERED:
                text = "Đã giao";   bg = new Color(0xE5E7EB); fg = UIConstants.TEXT_SECONDARY; break;
            default:
                text = "Sẵn sàng";  bg = new Color(0xD1FAE5); fg = new Color(0x065F46); break;
        }
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(UIConstants.FONT_SMALL);
        l.setForeground(fg);
        l.setOpaque(true);
        l.setBackground(bg);
        l.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return l;
    }

    // ─── Button factory ───────────────────────────────────────────────────────

    private JButton makeActionButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(UIConstants.FONT_BOLD);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, UIConstants.BTN_HEIGHT));
        return btn;
    }

    // ─── Inner DTO: WaiterPollData ────────────────────────────────────────────

    private static final class WaiterPollData {
        final Map<String, List<KitchenDAO.KitchenTicket>> readyMap;
        final List<TableItem>                              dirtyList;

        WaiterPollData(Map<String, List<KitchenDAO.KitchenTicket>> readyMap,
                       List<TableItem> dirtyList) {
            this.readyMap  = readyMap;
            this.dirtyList = dirtyList;
        }
    }

    // ─── Inner classes ────────────────────────────────────────────────────────

    /** Viền bo tròn cho card. */
    private static class RoundedBorder extends AbstractBorder {
        private final int   radius;
        private final Color color;
        RoundedBorder(int radius, Color color) {
            this.radius = radius; this.color = color;
        }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, radius * 2, radius * 2);
            g2.dispose();
        }
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }
    }

    /** FlowLayout tự động xuống dòng. */
    private static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }
        @Override public Dimension minimumLayoutSize(Container target) {
            Dimension min = layoutSize(target, false);
            min.width -= getHgap() + 1;
            return min;
        }
        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
                int hgap = getHgap(), vgap = getVgap();
                Insets insets   = target.getInsets();
                int maxWidth    = targetWidth - (insets.left + insets.right + hgap * 2);
                Dimension dim   = new Dimension(0, 0);
                int rowWidth = 0, rowHeight = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                        if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0; rowHeight = 0;
                        }
                        if (rowWidth != 0) rowWidth += hgap;
                        rowWidth  += d.width;
                        rowHeight  = Math.max(rowHeight, d.height);
                    }
                }
                addRow(dim, rowWidth, rowHeight);
                dim.width  += insets.left + insets.right + hgap * 2;
                dim.height += insets.top  + insets.bottom + vgap * 2;
                return dim;
            }
        }
        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);
            if (dim.height > 0) dim.height += getVgap();
            dim.height += rowHeight;
        }
    }

    // ─── CleanActionRenderer ─────────────────────────────────────────────────

    private static class CleanActionRenderer implements TableCellRenderer {
        private final List<TableItem> tables;

        CleanActionRenderer(List<TableItem> tables) {
            this.tables = tables;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            TableItem item = tables.get(row);
            boolean isDirty = item.getStatus() == TableItem.Status.DIRTY;
            JButton btn = new JButton(isDirty ? "Bắt đầu dọn" : "Dọn xong");
            btn.setFont(UIConstants.FONT_SMALL);
            btn.setBackground(isDirty ? UIConstants.WARNING : UIConstants.SUCCESS);
            btn.setForeground(Color.WHITE);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            return btn;
        }
    }

    // ─── CleanActionEditor ────────────────────────────────────────────────────

    private class CleanActionEditor extends DefaultCellEditor {
        private final List<TableItem> tables;
        private final TableDAO        tableDAO;
        private final Runnable        onRefresh;
        private JButton               btn;
        private TableItem             currentItem;

        CleanActionEditor(List<TableItem> tables, TableDAO tableDAO, Runnable onRefresh) {
            super(new JCheckBox());
            this.tables    = tables;
            this.tableDAO  = tableDAO;
            this.onRefresh = onRefresh;
            setClickCountToStart(1);
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable table, Object value, boolean isSelected, int row, int column) {
            currentItem = tables.get(row);
            boolean isDirty = currentItem.getStatus() == TableItem.Status.DIRTY;

            btn = new JButton(isDirty ? "Bắt đầu dọn" : "Dọn xong");
            btn.setFont(UIConstants.FONT_SMALL);
            btn.setBackground(isDirty ? UIConstants.WARNING : UIConstants.SUCCESS);
            btn.setForeground(Color.WHITE);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);

            btn.addActionListener(e -> {
                fireEditingStopped();
                TableItem.Status next = isDirty
                        ? TableItem.Status.CLEANING
                        : TableItem.Status.RANH;

                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() {
                        tableDAO.updateStatus(currentItem.getId(), next);
                        return null;
                    }
                    @Override
                    protected void done() {
                        try {
                            get();
                        } catch (Exception ex) {
                            showInlineError("Lỗi cập nhật bàn: " + ex.getMessage());
                        }
                        String msg = (next == TableItem.Status.RANH)
                                ? "Bàn " + currentItem.getName() + " đã sẵn sàng phục vụ!"
                                : "Đang dọn bàn " + currentItem.getName();
                        ToastNotification.Type type = (next == TableItem.Status.RANH)
                                ? ToastNotification.Type.SUCCESS
                                : ToastNotification.Type.INFO;
                        ToastNotification.show(null, msg, type);
                        onRefresh.run();
                    }
                }.execute();
            });

            return btn;
        }

        @Override
        public Object getCellEditorValue() { return ""; }
    }
}