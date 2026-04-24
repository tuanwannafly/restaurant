package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.restaurant.data.DataManager;
import com.restaurant.model.MenuItem;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;
import com.restaurant.ui.dialog.MenuDetailDialog;
import com.restaurant.ui.dialog.MenuDialog;
import com.restaurant.ui.dialog.MenuStatDialog;

public class MenuPanel extends JPanel {

    private DefaultTableModel tableModel;
    private StyledTable table;
    private RoundedTextField searchField;
    private JComboBox<String> categoryFilter;
    private JComboBox<String> priceFilter;
    private List<MenuItem> displayedItems = new ArrayList<>();
    private List<MenuItem> allItems = new ArrayList<>();

    // Phase 3 — permission fields
    private RoundedButton btnAdd;
    private boolean canAdd    = false;
    private boolean canEdit   = false;
    private boolean canDelete = false;

    private static final String[] COLUMNS = {"ID", "Tên món", "Loại", "Giá", "Hành động"};

    public MenuPanel() {
        setBackground(UIConstants.BG_PAGE);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(24, 48, 24, 48));
        buildUI();
        loadData();
    }

    private void buildUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        JLabel title = new JLabel("Quản lý Menu");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setOpaque(false);
        btnAdd = new RoundedButton("+ Thêm món");
        btnAdd.setPreferredSize(new Dimension(120, UIConstants.BTN_HEIGHT));
        btnAdd.addActionListener(e -> openAddDialog());
        // Phase 3 — ẩn theo quyền; sẽ re-evaluate trong applyMenuRoleFilter()
        btnAdd.setVisible(AppSession.getInstance().hasPermission(Permission.ADD_MENU));

        RoundedButton btnStat = new RoundedButton("Xem thống kê", new Color(0x6366F1), new Color(0x4F46E5), Color.WHITE, UIConstants.CORNER_RADIUS);
        btnStat.setPreferredSize(new Dimension(130, UIConstants.BTN_HEIGHT));
        btnStat.addActionListener(e -> new MenuStatDialog(SwingUtilities.getWindowAncestor(this)).setVisible(true));

        btnPanel.add(btnAdd);
        btnPanel.add(btnStat);
        topBar.add(title, BorderLayout.WEST);
        topBar.add(btnPanel, BorderLayout.EAST);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        searchField = new RoundedTextField("Tìm kiếm");
        searchField.setPreferredSize(new Dimension(260, 34));
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { applyFilter(); }
        });

        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font("Segoe UI", Font.PLAIN, 16));

        JLabel catLabel = new JLabel("Loại:");
        catLabel.setFont(UIConstants.FONT_BODY);
        categoryFilter = new JComboBox<>(new String[]{"Tất cả", "Hải sản", "Thịt", "Cơm", "Phở", "Đồ uống"});
        categoryFilter.setFont(UIConstants.FONT_BODY);
        categoryFilter.setPreferredSize(new Dimension(110, 34));
        categoryFilter.addActionListener(e -> applyFilter());

        JLabel priceLabel = new JLabel("Giá");
        priceLabel.setFont(UIConstants.FONT_BODY);
        priceFilter = new JComboBox<>(new String[]{"Tất cả", "Dưới 100k", "100k - 300k", "Trên 300k"});
        priceFilter.setFont(UIConstants.FONT_BODY);
        priceFilter.setPreferredSize(new Dimension(130, 34));
        priceFilter.addActionListener(e -> applyFilter());

        filterBar.add(searchField);
        filterBar.add(searchIcon);
        filterBar.add(catLabel);
        filterBar.add(categoryFilter);
        filterBar.add(priceLabel);
        filterBar.add(priceFilter);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(260);
        table.getColumnModel().getColumn(4).setCellRenderer(new ActionRenderer());

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;
                if (e.getClickCount() == 2 && col != 4) {
                    MenuItem item = displayedItems.get(row);
                    if (canEdit) openEditDialog(item);
                    else         new MenuDetailDialog(SwingUtilities.getWindowAncestor(MenuPanel.this), item).setVisible(true);
                } else if (col == 4) {
                    handleAction(e, row);
                }
            }
        });

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(topBar);
        header.add(filterBar);

        add(header, BorderLayout.NORTH);
        add(StyledTable.wrap(table), BorderLayout.CENTER);
    }

    /** Load dữ liệu từ BE bằng SwingWorker (không block giao diện) */
    public void loadData() {
        new SwingWorker<List<MenuItem>, Void>() {
            @Override
            protected List<MenuItem> doInBackground() {
                return DataManager.getInstance().getMenuItems();
            }
            @Override
            protected void done() {
                try {
                    allItems = get();
                    applyMenuRoleFilter();   // Phase 3 — refresh permission flags
                    applyFilter();
                } catch (Exception e) {
                    System.err.println("[MenuPanel] loadData lỗi: " + e.getMessage());
                }
            }
        }.execute();
    }

    // Phase 3 — cập nhật permission flags và ẩn/hiện controls
    private void applyMenuRoleFilter() {
        AppSession session = AppSession.getInstance();
        canAdd    = session.hasPermission(Permission.ADD_MENU);
        canEdit   = session.hasPermission(Permission.EDIT_MENU);
        canDelete = session.hasPermission(Permission.DELETE_MENU);
        btnAdd.setVisible(canAdd);
        tableModel.fireTableDataChanged(); // re-render cột action
    }

    /** Lọc trên dữ liệu đã có trong bộ nhớ */
    private void applyFilter() {
        String search = searchField.getText().trim().toLowerCase();
        String cat = (String) categoryFilter.getSelectedItem();
        String price = (String) priceFilter.getSelectedItem();

        displayedItems = allItems.stream().filter(m -> {
            boolean matchName = search.isEmpty() || m.getName().toLowerCase().contains(search);
            boolean matchCat = "Tất cả".equals(cat) || m.getCategory().equalsIgnoreCase(cat);
            boolean matchPrice = "Tất cả".equals(price) ||
                ("Dưới 100k".equals(price) && m.getPrice() < 100000) ||
                ("100k - 300k".equals(price) && m.getPrice() >= 100000 && m.getPrice() <= 300000) ||
                ("Trên 300k".equals(price) && m.getPrice() > 300000);
            return matchName && matchCat && matchPrice;
        }).collect(Collectors.toList());

        tableModel.setRowCount(0);
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        for (MenuItem m : displayedItems) {
            tableModel.addRow(new Object[]{m.getId(), m.getName(), m.getCategory(), nf.format((long) m.getPrice()), ""});
        }
    }

    private void handleAction(MouseEvent e, int row) {
        Rectangle cellRect = table.getCellRect(row, 4, false);
        int x = e.getX() - cellRect.x;
        MenuItem item = displayedItems.get(row);

        if (x < 60) {
            // Phase 3 — guard xóa
            if (!canDelete) return;
            int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn xóa món \"" + item.getName() + "\"?",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        DataManager.getInstance().deleteMenuItem(item.getId());
                        return null;
                    }
                    @Override protected void done() { loadData(); }
                }.execute();
            }
        } else if (x < 150) {
            // Phase 3 — guard sửa
            if (!canEdit) return;
            new MenuDialog(SwingUtilities.getWindowAncestor(this), item, saved -> {
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        DataManager.getInstance().updateMenuItem(saved);
                        return null;
                    }
                    @Override protected void done() { loadData(); }
                }.execute();
            }).setVisible(true);
        } else {
            new MenuDetailDialog(SwingUtilities.getWindowAncestor(this), item).setVisible(true);
        }
    }

    /** Helper dùng cho double-click khi canEdit = true. */
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
        // Phase 3 — guard
        if (!AppSession.getInstance().hasPermission(Permission.ADD_MENU)) {
            JOptionPane.showMessageDialog(this, "Bạn không có quyền thực hiện thao tác này.");
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

    /** Non-static inner class để đọc canEdit/canDelete từ outer panel. */
    private class ActionRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            panel.setBackground(isSelected ? UIConstants.ROW_SELECTED : (row % 2 == 0 ? Color.WHITE : new Color(0xF9FAFB)));
            if (canDelete) {
                panel.add(styledLabel("🗑 Xóa", UIConstants.DANGER));
                panel.add(sep());
            }
            if (canEdit) {
                panel.add(styledLabel("✏ Cập nhật", UIConstants.PRIMARY));
                panel.add(sep());
            }
            panel.add(styledLabel("👁 Xem chi tiết", new Color(0x6366F1)));
            return panel;
        }
        private JLabel styledLabel(String text, Color color) {
            JLabel l = new JLabel(text);
            l.setFont(UIConstants.FONT_SMALL);
            l.setForeground(color);
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
            return l;
        }
        private JSeparator sep() {
            JSeparator s = new JSeparator(SwingConstants.VERTICAL);
            s.setPreferredSize(new Dimension(1, 16));
            s.setForeground(UIConstants.BORDER_COLOR);
            return s;
        }
    }
}
