package com.restaurant.ui.dialog;

import com.restaurant.dao.KitchenDAO;
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
import java.util.List;

import com.restaurant.ui.ToastNotification;
import static com.restaurant.ui.UIConstants.BG_PAGE;
import static com.restaurant.ui.UIConstants.BORDER_COLOR;
import static com.restaurant.ui.UIConstants.FONT_BODY;
import static com.restaurant.ui.UIConstants.FONT_BOLD;
import static com.restaurant.ui.UIConstants.HEADER_BG;
import static com.restaurant.ui.UIConstants.PRIMARY;
import static com.restaurant.ui.UIConstants.TEXT_SECONDARY;
import static com.restaurant.ui.theme.AppTheme.*;

/**
 * Phase 3C-2 – Dialog hiển thị các ticket đang COOKING của một món,
 * cho phép đánh dấu tất cả hoàn thành (→ READY).
 */
public class KitchenCookingDetailDialog extends JDialog {

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final Color COLOR_DONE = new Color(0x10B981);

    private static final int COL_TABLE = 0;
    private static final int COL_QTY   = 1;
    private static final int COL_NOTE  = 2;

    // ─── State ────────────────────────────────────────────────────────────────

    private final String                         itemName;
    private final List<KitchenDAO.KitchenTicket> tickets;
    private final Runnable                       onRefresh;
    private final KitchenDAO                     kitchenDAO = new KitchenDAO();

    // ─── Constructor ──────────────────────────────────────────────────────────

    public KitchenCookingDetailDialog(Window owner,
                                      String itemName,
                                      List<KitchenDAO.KitchenTicket> tickets,
                                      Runnable onRefresh) {
        super(owner, ModalityType.APPLICATION_MODAL);
        this.itemName  = itemName;
        this.tickets   = tickets;
        this.onRefresh = onRefresh;

        setTitle("Đang chế biến: " + itemName);
        setSize(480, 420);
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
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"Tên bàn", "Số lượng", "Ghi chú"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;   // toàn bộ bảng read-only
            }
        };

        for (KitchenDAO.KitchenTicket t : tickets) {
            model.addRow(new Object[]{
                    t.tableName,
                    t.quantity,
                    t.note != null ? t.note : ""
            });
        }

        JTable table = new JTable(model);
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
        setColumnWidth(table, COL_TABLE, 120);
        setColumnWidth(table, COL_QTY,    90);
        setColumnWidth(table, COL_NOTE,  180);

        // Centre-align all columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int c = 0; c < model.getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setCellRenderer(centerRenderer);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private void setColumnWidth(JTable table, int colIndex, int width) {
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

    /** WEST: nhãn nhân viên đang thực hiện */
    private JPanel buildSouthWest() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setOpaque(false);

        // Lấy tên nhân viên từ ticket đầu tiên, fallback nếu null
        String empName = (!tickets.isEmpty() && tickets.get(0).assignedEmployeeName != null)
                ? tickets.get(0).assignedEmployeeName
                : "Nguyễn Thị Thanh";

        JLabel lbl = new JLabel("Nhân viên thực hiện: " + empName);
        lbl.setFont(FONT_BODY);
        lbl.setForeground(TEXT_SECONDARY);
        panel.add(lbl);
        return panel;
    }

    /** EAST: nút "←" + nút "Đã hoàn thành" */
    private JPanel buildSouthEast() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.setOpaque(false);

        JButton btnBack = new JButton("←");
        btnBack.setFont(FONT_BOLD);
        btnBack.setFocusPainted(false);
        btnBack.addActionListener(e -> dispose());

        com.restaurant.ui.RoundedButton btnDone = new RoundedButton("Đã hoàn thành", COLOR_DONE);
        btnDone.setPreferredSize(new Dimension(130, 34));
        btnDone.addActionListener(e -> doComplete());

        panel.add(btnBack);
        panel.add(btnDone);
        return panel;
    }

    // ─── Action ───────────────────────────────────────────────────────────────

    private void doComplete() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                for (KitchenDAO.KitchenTicket ticket : tickets) {
                    kitchenDAO.updateItemStatus(ticket.itemId, Order.OrderItem.ItemStatus.READY);
                }
                return null;
            }

            @Override
            protected void done() {
                ToastNotification.show(
                        owner,
                        "Đã hoàn thành " + itemName + "!",
                        ToastNotification.Type.SUCCESS
                );
                onRefresh.run();
                dispose();
            }
        }.execute();
    }
}