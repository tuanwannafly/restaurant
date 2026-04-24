package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
import com.restaurant.model.Employee;
import com.restaurant.ui.dialog.EmployeeDetailDialog;
import com.restaurant.ui.dialog.EmployeeDialog;

public class EmployeePanel extends JPanel {

    private DefaultTableModel tableModel;
    private StyledTable table;
    private RoundedTextField searchField;
    private String selectedRole = null;
    private List<Employee> displayedItems = new ArrayList<>();
    private List<Employee> allItems = new ArrayList<>();

    private static final String[] COLUMNS = {"ID", "Tên", "Vai trò", "SDT", "Ngày vào làm", "Hành động"};

    public EmployeePanel() {
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

        JLabel title = new JLabel("Quản lý nhân viên");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setOpaque(false);
        RoundedButton btnAdd = new RoundedButton("+ Thêm nhân viên");
        btnAdd.setPreferredSize(new Dimension(155, UIConstants.BTN_HEIGHT));
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

        JLabel roleLabel = new JLabel("Vai trò");
        roleLabel.setFont(UIConstants.FONT_BODY);

        String[] roles = {"Phục vụ", "Đầu bếp", "Thu ngân", "Quản lý"};
        JPanel rolePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rolePanel.setOpaque(false);
        for (String role : roles) {
            RoundedButton rbtn = RoundedButton.outline(role);
            rbtn.setPreferredSize(new Dimension(80, 30));
            rbtn.addActionListener(e -> {
                selectedRole = role.equals(selectedRole) ? null : role;
                applyFilter();
            });
            rolePanel.add(rbtn);
        }

        filterBar.add(searchField);
        filterBar.add(searchIcon);
        filterBar.add(roleLabel);
        filterBar.add(rolePanel);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(260);
        table.getColumnModel().getColumn(5).setCellRenderer(new ActionRenderer());

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (col == 5 && row >= 0) handleAction(e, row);
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

    /** Load dữ liệu từ BE bằng SwingWorker */
    public void loadData() {
        new SwingWorker<List<Employee>, Void>() {
            @Override
            protected List<Employee> doInBackground() {
                return DataManager.getInstance().getEmployees();
            }
            @Override
            protected void done() {
                try {
                    allItems = get();
                    applyFilter();
                } catch (Exception e) {
                    System.err.println("[EmployeePanel] loadData lỗi: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void applyFilter() {
        String search = searchField.getText().trim().toLowerCase();

        displayedItems = allItems.stream().filter(emp -> {
            boolean matchName = search.isEmpty()
                || emp.getName().toLowerCase().contains(search)
                || emp.getId().toLowerCase().contains(search);
            boolean matchRole = selectedRole == null || emp.getRoleDisplay().equalsIgnoreCase(selectedRole);
            return matchName && matchRole;
        }).collect(Collectors.toList());

        tableModel.setRowCount(0);
        for (Employee emp : displayedItems) {
            tableModel.addRow(new Object[]{emp.getId(), emp.getName(), emp.getRoleDisplay(), emp.getPhone(), emp.getStartDate(), ""});
        }
    }

    private void handleAction(MouseEvent e, int row) {
        Rectangle cellRect = table.getCellRect(row, 5, false);
        int x = e.getX() - cellRect.x;
        Employee item = displayedItems.get(row);

        if (x < 60) {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Xóa nhân viên \"" + item.getName() + "\"?", "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        DataManager.getInstance().deleteEmployee(item.getId());
                        return null;
                    }
                    @Override protected void done() { loadData(); }
                }.execute();
            }
        } else if (x < 155) {
            new EmployeeDialog(SwingUtilities.getWindowAncestor(this), item, saved -> {
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        DataManager.getInstance().updateEmployee(saved);
                        return null;
                    }
                    @Override protected void done() { loadData(); }
                }.execute();
            }).setVisible(true);
        } else {
            new EmployeeDetailDialog(SwingUtilities.getWindowAncestor(this), item).setVisible(true);
        }
    }

    private void openAddDialog() {
        new EmployeeDialog(SwingUtilities.getWindowAncestor(this), null, saved -> {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    DataManager.getInstance().addEmployee(saved);
                    return null;
                }
                @Override protected void done() { loadData(); }
            }.execute();
        }).setVisible(true);
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
