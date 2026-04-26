package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.restaurant.dao.EmployeeDAO;
import com.restaurant.data.DataManager;
import com.restaurant.model.Employee;
import com.restaurant.session.AppSession;
import com.restaurant.session.OperationType;
import com.restaurant.session.Permission;
import com.restaurant.ui.dialog.ConfirmOperationDialog;
import com.restaurant.ui.dialog.EmployeeDetailDialog;
import com.restaurant.ui.dialog.EmployeeDialog;
import com.restaurant.ui.dialog.RegisterStaffDialog;

public class EmployeePanel extends JPanel {

    private DefaultTableModel tableModel;
    private StyledTable table;
    private RoundedTextField searchField;
    private String selectedRole = null;
    private List<Employee> displayedItems = new ArrayList<>();
    private List<Employee> allItems = new ArrayList<>();

    /**
     * true khi người dùng hiện tại là RESTAURANT_ADMIN (có quyền REGISTER_STAFF).
     * Dùng để quyết định có hiển thị cột "Tài khoản" và nút "Tạo tài khoản" hay không.
     */
    private final boolean showAccountCol;

    /**
     * Index cột "Hành động" — phụ thuộc vào showAccountCol:
     * 6 khi có cột Tài khoản, 5 khi không.
     */
    private final int ACTION_COL;

    /** Danh sách tên cột xây dựng động theo quyền của user hiện tại. */
    private final String[] COLUMNS;

    /** Dùng để tra user_id khi phát hành operation token. */
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    public EmployeePanel() {
        showAccountCol = AppSession.getInstance().hasPermission(Permission.REGISTER_STAFF);
        ACTION_COL     = showAccountCol ? 6 : 5;
        COLUMNS        = showAccountCol
                ? new String[]{"ID", "Tên", "Vai trò", "SDT", "Ngày vào làm", "Tài khoản", "Hành động"}
                : new String[]{"ID", "Tên", "Vai trò", "SDT", "Ngày vào làm", "Hành động"};

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

        if (AppSession.getInstance().hasPermission(Permission.REGISTER_STAFF)) {
            RoundedButton btnRegister = RoundedButton.outline("👤 Tạo tài khoản");
            btnRegister.setPreferredSize(new Dimension(140, UIConstants.BTN_HEIGHT));
            btnRegister.addActionListener(e -> openRegisterDialog());
            btnPanel.add(Box.createHorizontalStrut(8));
            btnPanel.add(btnRegister);
        }

        RoundedButton btnAdd = new RoundedButton("+ Thêm nhân viên");
        btnAdd.setPreferredSize(new Dimension(155, UIConstants.BTN_HEIGHT));
        btnAdd.addActionListener(e -> openAddDialog());
        btnPanel.add(Box.createHorizontalStrut(8));
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
        if (showAccountCol) {
            table.getColumnModel().getColumn(5).setPreferredWidth(130);
            table.getColumnModel().getColumn(5).setCellRenderer(new AccountStatusRenderer());
            table.getColumnModel().getColumn(6).setPreferredWidth(260);
            table.getColumnModel().getColumn(6).setCellRenderer(new ActionRenderer());
        } else {
            table.getColumnModel().getColumn(5).setPreferredWidth(260);
            table.getColumnModel().getColumn(5).setCellRenderer(new ActionRenderer());
        }

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (col == ACTION_COL && row >= 0) handleAction(e, row);
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
        new SwingWorker<List<Employee>, Void>() {
            @Override
            protected List<Employee> doInBackground() {
                return showAccountCol
                        ? DataManager.getInstance().getEmployeesWithAccountStatus()
                        : DataManager.getInstance().getEmployees();
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
            if (showAccountCol) {
                String accountStatus = emp.isHasAccount() ? "✅ Có tài khoản" : "⬜ Chưa có";
                tableModel.addRow(new Object[]{
                    emp.getId(), emp.getName(), emp.getRoleDisplay(),
                    emp.getPhone(), emp.getStartDate(), accountStatus, ""});
            } else {
                tableModel.addRow(new Object[]{
                    emp.getId(), emp.getName(), emp.getRoleDisplay(),
                    emp.getPhone(), emp.getStartDate(), ""});
            }
        }
    }

    private void handleAction(MouseEvent e, int row) {
        Rectangle cellRect = table.getCellRect(row, ACTION_COL, false);
        int x = e.getX() - cellRect.x;
        Employee item = displayedItems.get(row);

        if (x < 60) {
            // ── Operation Token: xác nhận trước khi xoá nhân viên ──────────
            long targetId = resolveEmployeeTargetId(item.getId());
            boolean confirmed = ConfirmOperationDialog.show(
                SwingUtilities.getWindowAncestor(this),
                OperationType.DELETE_EMPLOYEE,
                targetId);
            if (!confirmed) return;

            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    DataManager.getInstance().deleteEmployee(item.getId());
                    return null;
                }
                @Override protected void done() { loadData(); }
            }.execute();

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

    /**
     * Lấy user_id liên kết với nhân viên để dùng làm targetId cho Operation Token.
     * Nếu nhân viên chưa có tài khoản, dùng hashCode của employeeId làm proxy.
     */
    private long resolveEmployeeTargetId(String employeeId) {
        try {
            java.util.Optional<Long> opt = employeeDAO.findUserId(employeeId);
            return opt.isPresent() ? opt.get() : (long) employeeId.hashCode();
        } catch (Exception ignored) {
            return (long) employeeId.hashCode();
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

    private void openRegisterDialog() {
        RegisterStaffDialog dlg = new RegisterStaffDialog(
            (Frame) SwingUtilities.getWindowAncestor(this));
        dlg.setVisible(true);
        if (dlg.isSuccess()) {
            loadData();
            ToastNotification.show(
                SwingUtilities.getWindowAncestor(this),
                "Tạo tài khoản thành công!", ToastNotification.Type.SUCCESS);
        }
    }

    private static class AccountStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(UIConstants.FONT_BODY);
            String text = value != null ? value.toString() : "";
            setForeground(text.startsWith("✅") ? new Color(0x16A34A) : new Color(0x9CA3AF));
            return this;
        }
    }

    private static class ActionRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            panel.setBackground(isSelected ? UIConstants.ROW_SELECTED
                : (row % 2 == 0 ? Color.WHITE : new Color(0xF9FAFB)));
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
