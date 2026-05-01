package com.restaurant.ui.dialog;

import com.restaurant.dao.EmployeeDAO;
import com.restaurant.dao.KitchenDAO;
import com.restaurant.model.Employee;
import com.restaurant.model.Order;
import com.restaurant.ui.components.RoundedButton;
import com.restaurant.ui.components.ToastNotification;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.restaurant.ui.ToastNotification;
import static com.restaurant.ui.UIConstants.BG_PAGE;
import static com.restaurant.ui.UIConstants.BORDER_COLOR;
import static com.restaurant.ui.UIConstants.FONT_BOLD;
import static com.restaurant.ui.UIConstants.HEADER_BG;
import static com.restaurant.ui.UIConstants.PRIMARY;
import static com.restaurant.ui.theme.AppTheme.*;

/**
 * Phase 3C-1 – Dialog hiển thị chi tiết các ticket đang PENDING của một món,
 * cho phép chọn ticket, gán nhân viên bếp và thực hiện chế biến.
 */
public class KitchenPendingDetailDialog extends JDialog {

    // ─── State ────────────────────────────────────────────────────────────────

    private final String                       itemName;
    private final List<KitchenDAO.KitchenTicket> tickets;
    private final Runnable                     onRefresh;

    // ─── UI components ────────────────────────────────────────────────────────

    private DefaultTableModel tableModel;
    private JTable            table;
    private JCheckBox         chkAll;
    private JComboBox<String> cboEmployee;

    /** Danh sách nhân viên DAU_BEP – giữ để map index → Employee khi cần */
    private List<Employee> cookEmployees;

    // ─── Column indices ───────────────────────────────────────────────────────

    private static final int COL_CHECK    = 0;
    private static final int COL_TABLE    = 1;
    private static final int COL_QTY      = 2;
    private static final int COL_NOTE     = 3;
    private static final int COL_WAIT     = 4;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public KitchenPendingDetailDialog(Window owner,
                                      String itemName,
                                      List<KitchenDAO.KitchenTicket> tickets,
                                      Runnable onRefresh) {
        super(owner, ModalityType.APPLICATION_MODAL);
        this.itemName  = itemName;
        this.tickets   = tickets;
        this.onRefresh = onRefresh;

        setTitle("Chi tiết món: " + itemName);
        setSize(560, 520);
        setResizable(false);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        buildUI();
    }

    // ─── UI build ─────────────────────────────────────────────────────────────

    private void buildUI() {
        setLayout(new BorderLayout());
        add(buildNorthPanel(),  BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildSouthPanel(),  BorderLayout.SOUTH);
    }

    // ── NORTH ─────────────────────────────────────────────────────────────────

    private JPanel buildNorthPanel() {
        JLabel lblTitle = new JLabel(itemName, SwingConstants.CENTER);
        lblTitle.setFont(FONT_BOLD.deriveFont(20f));
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setBorder(new EmptyBorder(16, 24, 16, 24));
        lblTitle.setOpaque(true);
        lblTitle.setBackground(PRIMARY);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PRIMARY);
        panel.add(lblTitle, BorderLayout.CENTER);
        return panel;
    }

    // ── CENTER ────────────────────────────────────────────────────────────────

    private JScrollPane buildCenterPanel() {
        // Model: cột 0 là Boolean (checkbox), các cột còn lại là Object
        tableModel = new DefaultTableModel(
                new Object[]{"Tùy chọn", "Tên bàn", "Số lượng", "Ghi chú", "TG chờ"},
                0
        ) {
            @Override
            public Class<?> getColumnClass(int col) {
                return col == COL_CHECK ? Boolean.class : Object.class;
            }

            @Override
            public boolean isCellEditable(int row, int col) {
                return col == COL_CHECK;
            }
        };

        populateTable();

        table = new JTable(tableModel);
        table.setRowHeight(40);
        table.setShowGrid(true);
        table.setGridColor(BORDER_COLOR);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);

        // Header style
        table.getTableHeader().setBackground(HEADER_BG);
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setFont(FONT_BOLD);
        table.getTableHeader().setReorderingAllowed(false);

        // Column widths
        setColumnWidth(COL_CHECK,  60);
        setColumnWidth(COL_TABLE,  90);
        setColumnWidth(COL_QTY,    80);
        setColumnWidth(COL_NOTE,  130);
        setColumnWidth(COL_WAIT,   90);

        // Centre-align all non-boolean columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int c = COL_TABLE; c <= COL_WAIT; c++) {
            table.getColumnModel().getColumn(c).setCellRenderer(centerRenderer);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private void populateTable() {
        tableModel.setRowCount(0);
        LocalDateTime now = LocalDateTime.now();
        for (KitchenDAO.KitchenTicket t : tickets) {
            long waitMins = Duration.between(t.createdAt, now).toMinutes();
            tableModel.addRow(new Object[]{
                    Boolean.FALSE,
                    t.tableName,
                    t.quantity,
                    t.note != null ? t.note : "",
                    waitMins + " phút"
            });
        }
    }

    private void setColumnWidth(int colIndex, int width) {
        TableColumn col = table.getColumnModel().getColumn(colIndex);
        col.setPreferredWidth(width);
        col.setMinWidth(width);
        col.setMaxWidth(width);
    }

    // ── SOUTH ─────────────────────────────────────────────────────────────────

    private JPanel buildSouthPanel() {
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG_PAGE);
        south.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(12, 16, 12, 16)
        ));

        south.add(buildSouthWest(), BorderLayout.WEST);
        south.add(buildSouthEast(), BorderLayout.EAST);
        return south;
    }

    /** WEST: "Chế biến tất cả" checkbox + label / combobox nhân viên */
    private JPanel buildSouthWest() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        // Row 1 – check-all
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row1.setOpaque(false);
        chkAll = new JCheckBox("Chế biến tất cả");
        chkAll.setOpaque(false);
        chkAll.setFont(FONT_BOLD);
        chkAll.addActionListener(e -> toggleSelectAll(chkAll.isSelected()));
        row1.add(chkAll);

        // Row 2 – employee picker
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row2.setOpaque(false);

        JLabel lblEmp = new JLabel("Tên nhân viên:");
        lblEmp.setFont(FONT_BOLD);
        row2.add(lblEmp);

        cboEmployee = buildEmployeeCombo();
        cboEmployee.setPreferredSize(new Dimension(180, 32));
        row2.add(cboEmployee);

        panel.add(row1);
        panel.add(Box.createVerticalStrut(6));
        panel.add(row2);
        return panel;
    }

    /** EAST: nút "←" + nút "Thực hiện" */
    private JPanel buildSouthEast() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.setOpaque(false);

        JButton btnBack = new JButton("←");
        btnBack.setFont(FONT_BOLD);
        btnBack.setFocusPainted(false);
        btnBack.addActionListener(e -> dispose());

        RoundedButton btnExecute = new RoundedButton("Thực hiện", PRIMARY);
        btnExecute.setPreferredSize(new Dimension(110, 34));
        btnExecute.addActionListener(e -> doExecute());

        panel.add(btnBack);
        panel.add(btnExecute);
        return panel;
    }

    // ─── Employee ComboBox ────────────────────────────────────────────────────

    private JComboBox<String> buildEmployeeCombo() {
        JComboBox<String> combo = new JComboBox<>();

        // Placeholder item (disabled)
        combo.addItem("Chọn nhân viên");

        // Load DAU_BEP employees
        try {
            cookEmployees = new EmployeeDAO()
                    .findAll()
                    .stream()
                    .filter(emp -> Employee.Role.DAU_BEP.equals(emp.getRole()))
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            cookEmployees = List.of();
            System.err.println("[KitchenPendingDetailDialog] loadEmployees error: " + ex.getMessage());
        }

        for (Employee emp : cookEmployees) {
            combo.addItem(emp.getName());
        }

        // Disable the placeholder item in the drop-down list
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                           int index, boolean isSelected,
                                                           boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (index == 0) {
                    c.setEnabled(false);
                    c.setForeground(Color.GRAY);
                }
                return c;
            }
        });

        combo.setSelectedIndex(0);
        return combo;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Tick/untick tất cả checkbox ở cột 0 */
    private void toggleSelectAll(boolean selected) {
        // Commit bất kỳ cell editor đang mở
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            tableModel.setValueAt(selected, r, COL_CHECK);
        }
    }

    // ─── Action ───────────────────────────────────────────────────────────────

    private void doExecute() {
        // Commit cell editor đang mở (nếu có)
        if (table.isEditing()) table.getCellEditor().stopCellEditing();

        // 1. Collect checked rows
        List<String> selectedItemIds = new java.util.ArrayList<>();
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            Boolean checked = (Boolean) tableModel.getValueAt(r, COL_CHECK);
            if (Boolean.TRUE.equals(checked)) {
                selectedItemIds.add(tickets.get(r).itemId);
            }
        }

        // 2. Validate
        if (selectedItemIds.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Chọn ít nhất 1 món",
                    "Thông báo",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // 3. SwingWorker – cập nhật trạng thái sang COOKING
        Window owner = SwingUtilities.getWindowAncestor(this);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                KitchenDAO dao = new KitchenDAO();
                for (String itemId : selectedItemIds) {
                    dao.updateItemStatus(itemId, Order.OrderItem.ItemStatus.COOKING);
                }
                return null;
            }

            @Override
            protected void done() {
                ToastNotification.show(
                        owner,
                        "Đã tiếp nhận " + selectedItemIds.size() + " món!",
                        ToastNotification.Type.SUCCESS
                );
                onRefresh.run();
                dispose();
            }
        }.execute();
    }
}