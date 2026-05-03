package com.restaurant.ui;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.restaurant.data.DataManager;
import com.restaurant.model.MenuItem;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;
import com.restaurant.ui.dialog.MenuDetailDialog;
import com.restaurant.ui.dialog.MenuDialog;
import com.restaurant.ui.dialog.MenuStatDialog;

/**
 * MenuPanel — Phase 3 (mẫu panel cải tiến)
 *
 * Layout (MigLayout, WindowBuilder-compatible):
 *  ┌──────────────────────────────────────────────┐
 *  │  Quản lý Menu                   [Thống kê][+]│  ← topBar
 *  ├──────────────────────────────────────────────┤
 *  │  [🔍 Tìm kiếm...]  Loại: [▾]  Giá: [▾]      │  ← filterCard (white card)
 *  ├──────────────────────────────────────────────┤
 *  │  ID │ Tên món │ Loại │ Giá │  Hành động      │  ← StyledTable (fills rest)
 *  └──────────────────────────────────────────────┘
 *
 * Dependencies: net.miginfocom:miglayout-swing (Maven / Gradle)
 */
public class MenuPanel extends JPanel {

    // ── Model ─────────────────────────────────────────────────────────────
    private DefaultTableModel tableModel;
    private StyledTable       table;
    private RoundedTextField  searchField;
    private JComboBox<String> categoryFilter;
    private JComboBox<String> priceFilter;

    private List<MenuItem> allItems       = new ArrayList<>();
    private List<MenuItem> displayedItems = new ArrayList<>();

    // ── Permission flags (Phase 3) ────────────────────────────────────────
    private boolean canAdd    = false;
    private boolean canEdit   = false;
    private boolean canDelete = false;

    // ── UI refs ───────────────────────────────────────────────────────────
    private RoundedButton btnAdd;

    private static final String[] COLUMNS = {"ID", "Tên món", "Loại", "Giá", "Hành động"};
    private static final int ACTION_COL = 4;

    // ── Colours (local palette — augments UIConstants) ────────────────────
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color CARD_BORDER  = new Color(0xE2E8F0);
    private static final Color BTN_STAT_BG  = new Color(0x6366F1);
    private static final Color BTN_STAT_HOV = new Color(0x4F46E5);

    // ── Constructor ───────────────────────────────────────────────────────

    public MenuPanel() {
        // Root layout: vertical stack, fills the whole area
        setLayout(new MigLayout(
            "insets 24 48 24 48, fill, flowy, gapy 0",
            "[grow]",
            "[]8[]10[grow]"));
        setBackground(UIConstants.BG_PAGE);

        buildTopBar();
        buildFilterCard();
        buildTable();
        loadData();
    }

    // ── UI construction ───────────────────────────────────────────────────

    /** Row 1 — Title + action buttons */
    private void buildTopBar() {
        // MigLayout row: title left, buttons right
        JPanel bar = new JPanel(new MigLayout(
            "insets 0, fillx",
            "[grow][]",
            "[]"));
        bar.setOpaque(false);

        JLabel title = new JLabel("Quản lý Menu");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);

        // Buttons panel (right-aligned)
        JPanel btns = new JPanel(new MigLayout("insets 0", "[]8[]", "[]"));
        btns.setOpaque(false);

        // "Thống kê" — indigo accent
        RoundedButton btnStat = new RoundedButton(
            "📊 Thống kê",
            BTN_STAT_BG, BTN_STAT_HOV,
            Color.WHITE,
            UIConstants.CORNER_RADIUS);
        btnStat.setPreferredSize(new Dimension(130, UIConstants.BTN_HEIGHT));
        btnStat.addActionListener(e ->
            new MenuStatDialog(SwingUtilities.getWindowAncestor(this)).setVisible(true));

        // "Thêm món" — primary green
        btnAdd = new RoundedButton("+ Thêm món");
        btnAdd.setPreferredSize(new Dimension(120, UIConstants.BTN_HEIGHT));
        btnAdd.addActionListener(e -> openAddDialog());
        btnAdd.setVisible(AppSession.getInstance().hasPermission(Permission.ADD_MENU));

        btns.add(btnStat, "");
        btns.add(btnAdd,  "");

        bar.add(title, "growx");
        bar.add(btns,  "right");

        add(bar, "growx, wrap");
    }

    /** Row 2 — Filter card (white rounded card with search + comboboxes) */
    private void buildFilterCard() {
        // Custom-painted card — rounded corners + subtle shadow line
        JPanel card = new JPanel(new MigLayout(
            "insets 10 14 10 14",
            "[]8[200:240:300]20[]6[110:120:140]12[]6[130:140:160]push",
            "[center]")) {

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        card.setOpaque(false);

        // Search icon (non-editable label)
        JLabel iconSearch = new JLabel("🔍");
        iconSearch.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
        iconSearch.setForeground(new Color(0x94A3B8));

        // Search field
        searchField = new RoundedTextField("Tìm kiếm tên món…");
        searchField.setPreferredSize(new Dimension(220, 32));
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { applyFilter(); }
        });

        // Category label + combo
        JLabel catLabel = new JLabel("Loại:");
        catLabel.setFont(UIConstants.FONT_BODY);
        catLabel.setForeground(new Color(0x64748B));

        categoryFilter = styledCombo(new String[]{
            "Tất cả", "Hải sản", "Thịt", "Cơm", "Phở", "Đồ uống"});
        categoryFilter.addActionListener(e -> applyFilter());

        // Price label + combo
        JLabel priceLabel = new JLabel("Giá:");
        priceLabel.setFont(UIConstants.FONT_BODY);
        priceLabel.setForeground(new Color(0x64748B));

        priceFilter = styledCombo(new String[]{
            "Tất cả", "Dưới 100k", "100k – 300k", "Trên 300k"});
        priceFilter.addActionListener(e -> applyFilter());

        card.add(iconSearch,    "");
        card.add(searchField,   "growx");
        card.add(catLabel,      "");
        card.add(categoryFilter,"");
        card.add(priceLabel,    "");
        card.add(priceFilter,   "");

        add(card, "growx, wrap");
    }

    /** Row 3 — StyledTable inside matching JScrollPane */
    private void buildTable() {
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new StyledTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(55);
        table.getColumnModel().getColumn(1).setPreferredWidth(190);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(280);
        table.getColumnModel().getColumn(4).setCellRenderer(new ActionRenderer());

        // Click handling
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;
                if (e.getClickCount() == 2 && col != ACTION_COL) {
                    // Double-click anywhere but action col → edit or detail
                    MenuItem item = displayedItems.get(table.convertRowIndexToModel(row));
                    if (canEdit) openEditDialog(item);
                    else         showDetail(item);
                } else if (col == ACTION_COL) {
                    handleActionClick(e, table.convertRowIndexToModel(row));
                }
            }
            @Override
            public void mouseMoved(MouseEvent e) { /* hover handled in StyledTable */ }
        });

        add(StyledTable.wrap(table), "grow");
    }

    // ── Data loading ──────────────────────────────────────────────────────

    public void loadData() {
        // Show skeleton while fetching
        table.setLoading(true);

        new SwingWorker<List<MenuItem>, Void>() {
            @Override
            protected List<MenuItem> doInBackground() {
                return DataManager.getInstance().getMenuItems();
            }

            @Override
            protected void done() {
                try {
                    allItems = get();
                    refreshPermissions();
                    applyFilter();
                } catch (Exception ex) {
                    System.err.println("[MenuPanel] loadData lỗi: " + ex.getMessage());
                } finally {
                    table.setLoading(false);
                }
            }
        }.execute();
    }

    /** Re-read session permissions and update button/renderer visibility. */
    private void refreshPermissions() {
        AppSession session = AppSession.getInstance();
        canAdd    = session.hasPermission(Permission.ADD_MENU);
        canEdit   = session.hasPermission(Permission.EDIT_MENU);
        canDelete = session.hasPermission(Permission.DELETE_MENU);
        btnAdd.setVisible(canAdd);
        tableModel.fireTableDataChanged(); // re-render action column
    }

    // ── Filtering ─────────────────────────────────────────────────────────

    private void applyFilter() {
        String search = searchField.getText().trim().toLowerCase();
        String cat    = (String) categoryFilter.getSelectedItem();
        String price  = (String) priceFilter.getSelectedItem();

        displayedItems = allItems.stream().filter(m -> {
            boolean matchName = search.isEmpty()
                || m.getName().toLowerCase().contains(search)
                || m.getId().toLowerCase().contains(search);
            boolean matchCat = "Tất cả".equals(cat)
                || m.getCategory().equalsIgnoreCase(cat);
            boolean matchPrice = "Tất cả".equals(price)
                || ("Dưới 100k".equals(price)    && m.getPrice() < 100_000)
                || ("100k – 300k".equals(price)  && m.getPrice() >= 100_000 && m.getPrice() <= 300_000)
                || ("Trên 300k".equals(price)    && m.getPrice() > 300_000);
            return matchName && matchCat && matchPrice;
        }).collect(Collectors.toList());

        tableModel.setRowCount(0);
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        for (MenuItem m : displayedItems) {
            tableModel.addRow(new Object[]{
                m.getId(), m.getName(), m.getCategory(),
                nf.format((long) m.getPrice()) + " ₫", ""});
        }
    }

    // ── Action handling ───────────────────────────────────────────────────

    /**
     * Detect which "sub-button" was clicked inside the action cell.
     * Layout (left→right): [Xóa ~60px] | sep | [Cập nhật ~90px] | sep | [Xem chi tiết ~110px]
     */
    private void handleActionClick(MouseEvent e, int modelRow) {
        Rectangle cell = table.getCellRect(
            table.convertRowIndexToView(modelRow), ACTION_COL, false);
        int x = e.getX() - cell.x;
        MenuItem item = displayedItems.get(modelRow);

        if (canDelete && x < 62) {
            // DELETE
            int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn xóa món \"" + item.getName() + "\"?",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        DataManager.getInstance().deleteMenuItem(item.getId());
                        return null;
                    }
                    @Override protected void done() { loadData(); }
                }.execute();
            }
        } else if (canEdit && x < 162) {
            // EDIT
            openEditDialog(item);
        } else {
            // DETAIL
            showDetail(item);
        }
    }

    private void openEditDialog(MenuItem item) {
        new MenuDialog(SwingUtilities.getWindowAncestor(this), item, saved -> {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    DataManager.getInstance().updateMenuItem(saved);
                    return null;
                }
                @Override protected void done() { loadData(); }
            }.execute();
        }).setVisible(true);
    }

    private void openAddDialog() {
        if (!AppSession.getInstance().hasPermission(Permission.ADD_MENU)) {
            JOptionPane.showMessageDialog(this,
                "Bạn không có quyền thực hiện thao tác này.",
                "Không có quyền", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new MenuDialog(SwingUtilities.getWindowAncestor(this), null, saved -> {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    DataManager.getInstance().addMenuItem(saved);
                    return null;
                }
                @Override protected void done() { loadData(); }
            }.execute();
        }).setVisible(true);
    }

    private void showDetail(MenuItem item) {
        new MenuDetailDialog(SwingUtilities.getWindowAncestor(this), item).setVisible(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static JComboBox<String> styledCombo(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setFont(UIConstants.FONT_BODY);
        cb.setBackground(Color.WHITE);
        cb.setForeground(UIConstants.TEXT_PRIMARY);
        cb.setPreferredSize(new Dimension(130, 32));
        return cb;
    }

    // ── ActionRenderer (non-static → reads canEdit / canDelete) ──────────

    /**
     * Renders the action cell as three labelled "pill" buttons.
     * Buttons hidden based on current permission flags.
     *
     * Layout widths must match thresholds in handleActionClick():
     *   Xóa  ≈ 60 px | Cập nhật ≈ 90 px | Xem chi tiết ≈ 110 px
     */
    private class ActionRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {

            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 6));
            int modelRow = tbl.convertRowIndexToModel(row);
            panel.setBackground(isSelected  ? StyledTable.ROW_SEL
                : row == -1                 ? StyledTable.ROW_EVEN
                : (modelRow % 2 == 0)       ? StyledTable.ROW_EVEN
                                            : StyledTable.ROW_ODD);

            if (canDelete) {
                panel.add(pill("🗑  Xóa", UIConstants.DANGER));
                panel.add(divider());
            }
            if (canEdit) {
                panel.add(pill("✏  Cập nhật", UIConstants.PRIMARY));
                panel.add(divider());
            }
            panel.add(pill("👁  Xem", new Color(0x6366F1)));

            return panel;
        }

        private JLabel pill(String text, Color color) {
            JLabel l = new JLabel(text);
            l.setFont(UIConstants.FONT_SMALL);
            l.setForeground(color);
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.setOpaque(true);
            l.setBackground(alphaColor(color, 12));
            l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(alphaColor(color, 80), 1, true),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
            return l;
        }

        private JSeparator divider() {
            JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
            sep.setPreferredSize(new Dimension(1, 16));
            sep.setForeground(StyledTable.BORDER_COL);
            return sep;
        }

        /** Create a colour with custom alpha (0–255) from an opaque Color. */
        private Color alphaColor(Color base, int alpha) {
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
        }
    }
}