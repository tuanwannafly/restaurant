package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JDialog;
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
import com.restaurant.model.TableItem;
import com.restaurant.ui.dialog.TableDialog;

public class TablePanel extends JPanel {

    private DefaultTableModel tableModel;
    private StyledTable table;
    private RoundedTextField searchField;
    private JComboBox<String> capacityFilter;
    private JComboBox<String> statusFilter;
    private List<TableItem> displayedItems = new ArrayList<>();
    private List<TableItem> allItems = new ArrayList<>();

    private static final String[] COLUMNS = {"ID", "Tên bàn", "Sức chứa", "Trạng thái", "Hành động"};

    public TablePanel() {
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

        JLabel title = new JLabel("Quản lý bàn");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setOpaque(false);
        RoundedButton btnAdd = new RoundedButton("+ Thêm bàn");
        btnAdd.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnAdd.addActionListener(e -> openAddDialog());
        btnPanel.add(btnAdd);

        topBar.add(title, BorderLayout.WEST);
        topBar.add(btnPanel, BorderLayout.EAST);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        searchField = new RoundedTextField("Tìm kiếm");
        searchField.setPreferredSize(new Dimension(240, 34));
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { applyFilter(); }
        });

        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font("Segoe UI", Font.PLAIN, 16));

        JLabel capLabel = new JLabel("Sức chứa:");
        capLabel.setFont(UIConstants.FONT_BODY);
        capacityFilter = new JComboBox<>(new String[]{"Tất cả", "2", "4", "6", "8", "10", "12+"});
        capacityFilter.setFont(UIConstants.FONT_BODY);
        capacityFilter.setPreferredSize(new Dimension(90, 34));
        capacityFilter.addActionListener(e -> applyFilter());

        JLabel stLabel = new JLabel("Trạng thái:");
        stLabel.setFont(UIConstants.FONT_BODY);
        statusFilter = new JComboBox<>(new String[]{"Tất cả", "Rảnh", "Bận", "Đặt trước"});
        statusFilter.setFont(UIConstants.FONT_BODY);
        statusFilter.setPreferredSize(new Dimension(110, 34));
        statusFilter.addActionListener(e -> applyFilter());

        filterBar.add(searchField);
        filterBar.add(searchIcon);
        filterBar.add(capLabel);
        filterBar.add(capacityFilter);
        filterBar.add(stLabel);
        filterBar.add(statusFilter);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(280);
        table.getColumnModel().getColumn(3).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new ActionRenderer());

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (col == 4 && row >= 0) handleAction(e, row);
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

    public void loadData() {
        new SwingWorker<List<TableItem>, Void>() {
            @Override
            protected List<TableItem> doInBackground() {
                return DataManager.getInstance().getTables();
            }
            @Override
            protected void done() {
                try {
                    allItems = get();
                    applyFilter();
                } catch (Exception e) {
                    System.err.println("[TablePanel] loadData lỗi: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void applyFilter() {
        String search = searchField.getText().trim().toLowerCase();
        String cap = (String) capacityFilter.getSelectedItem();
        String status = (String) statusFilter.getSelectedItem();

        displayedItems = allItems.stream().filter(t -> {
            boolean matchName = search.isEmpty() || t.getName().toLowerCase().contains(search);
            boolean matchCap = "Tất cả".equals(cap) ||
                ("12+".equals(cap) && t.getCapacity() >= 12) ||
                (!cap.equals("Tất cả") && !cap.equals("12+") && t.getCapacity() == Integer.parseInt(cap));
            boolean matchStatus = "Tất cả".equals(status) || t.getStatusDisplay().equalsIgnoreCase(status);
            return matchName && matchCap && matchStatus;
        }).collect(Collectors.toList());

        tableModel.setRowCount(0);
        for (TableItem t : displayedItems) {
            tableModel.addRow(new Object[]{t.getId(), t.getName(), t.getCapacity(), t.getStatusDisplay(), ""});
        }
    }

    private void openAddDialog() {
        new TableDialog(SwingUtilities.getWindowAncestor(this), null, saved -> {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    DataManager.getInstance().addTable(saved);
                    return null;
                }
                @Override protected void done() { loadData(); }
            }.execute();
        }).setVisible(true);
    }

    private void handleAction(MouseEvent e, int row) {
        Rectangle cellRect = table.getCellRect(row, 4, false);
        int x = e.getX() - cellRect.x;
        TableItem item = displayedItems.get(row);

        if (x < 60) {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Xóa bàn \"" + item.getName() + "\"?",
                "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        DataManager.getInstance().deleteTable(item.getId());
                        return null;
                    }
                    @Override protected void done() { loadData(); }
                }.execute();
            }
        } else if (x < 155) {
            new TableDialog(SwingUtilities.getWindowAncestor(this), item, saved -> {
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        DataManager.getInstance().updateTable(saved);
                        return null;
                    }
                    @Override protected void done() { loadData(); }
                }.execute();
            }).setVisible(true);
        } else {
            showDetail(item);
        }
    }

    private void showDetail(TableItem item) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Chi tiết bàn", ModalityType.APPLICATION_MODAL);
        dlg.setSize(400, 280);
        dlg.setLocationRelativeTo(this);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        addDetailRow(p, gbc, 0, "ID:", item.getId());
        addDetailRow(p, gbc, 1, "Tên bàn:", item.getName());
        addDetailRow(p, gbc, 2, "Sức chứa:", item.getCapacity() + " người");
        addDetailRow(p, gbc, 3, "Trạng thái:", item.getStatusDisplay());
        dlg.add(p);
        dlg.setVisible(true);
    }

    private void addDetailRow(JPanel p, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row;
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(120, 30));
        p.add(lbl, gbc);
        gbc.gridx = 1;
        JLabel val = new JLabel(value);
        val.setFont(UIConstants.FONT_BODY);
        p.add(val, gbc);
    }

    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            String status = value == null ? "" : value.toString();
            switch (status) {
                case "Rảnh":      lbl.setForeground(UIConstants.SUCCESS); break;
                case "Bận":       lbl.setForeground(UIConstants.DANGER); break;
                case "Đặt trước": lbl.setForeground(UIConstants.WARNING); break;
                default:          lbl.setForeground(UIConstants.TEXT_PRIMARY);
            }
            lbl.setFont(UIConstants.FONT_BOLD);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            return lbl;
        }
    }

    private static class ActionRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            panel.setBackground(isSelected ? UIConstants.ROW_SELECTED : (row % 2 == 0 ? Color.WHITE : new Color(0xF9FAFB)));
            panel.add(styledLabel("🗑 Xóa", UIConstants.DANGER));
            panel.add(sep());
            panel.add(styledLabel("✏ Cập nhật", UIConstants.PRIMARY));
            panel.add(sep());
            panel.add(styledLabel("👁 Xem chi tiết", new Color(0x6366F1)));
            return panel;
        }
        private JLabel styledLabel(String text, Color color) {
            JLabel l = new JLabel(text);
            l.setFont(UIConstants.FONT_SMALL);
            l.setForeground(color);
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
