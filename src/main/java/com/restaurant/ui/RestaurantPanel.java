package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
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

import com.restaurant.dao.RestaurantDAO;
import com.restaurant.dao.UserDAO;
import com.restaurant.model.Restaurant;
import com.restaurant.model.Restaurant.Status;
import com.restaurant.session.OperationType;
import com.restaurant.ui.dialog.ConfirmOperationDialog;
import com.restaurant.ui.dialog.RestaurantDialog;
import com.restaurant.ui.dialog.RestaurantDialog.AdminChoice;

/**
 * Panel quản lý danh sách nhà hàng — chỉ hiển thị khi người dùng là SUPER_ADMIN.
 *
 * <p>Khi tạo nhà hàng mới, người dùng có thể đồng thời:
 * <ul>
 *   <li>Chọn một RESTAURANT_ADMIN có sẵn để gán cho nhà hàng vừa tạo</li>
 *   <li>Tạo tài khoản RESTAURANT_ADMIN mới ngay tại chỗ</li>
 *   <li>Bỏ qua — gán admin sau</li>
 * </ul>
 *
 * <p><b>Giai đoạn 3 — Operation Token:</b> thao tác xoá nhà hàng yêu cầu
 * người dùng xác nhận bằng {@link ConfirmOperationDialog} thay cho
 * JOptionPane YES/NO thông thường.
 */
public class RestaurantPanel extends JPanel {

    private final RestaurantDAO          dao           = new RestaurantDAO();
    private final UserDAO                userDAO       = new UserDAO();
    private       DefaultTableModel      tableModel;
    private       StyledTable            table;
    private       List<Restaurant>       displayedItems = new ArrayList<>();

    private static final String[] COLUMNS = {
        "ID", "Tên nhà hàng", "Địa chỉ", "SĐT", "Email", "Trạng thái", "Hành động"
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public RestaurantPanel() {
        setBackground(UIConstants.BG_PAGE);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(24, 48, 24, 48));
        buildUI();
        loadData();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        JLabel titleLbl = new JLabel("Quản lý nhà hàng");
        titleLbl.setFont(UIConstants.FONT_TITLE);
        titleLbl.setForeground(UIConstants.TEXT_PRIMARY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setOpaque(false);
        RoundedButton btnAdd = new RoundedButton("+ Thêm nhà hàng");
        btnAdd.setPreferredSize(new Dimension(160, UIConstants.BTN_HEIGHT));
        btnAdd.addActionListener(e -> openAddDialog());
        btnPanel.add(btnAdd);

        topBar.add(titleLbl, BorderLayout.WEST);
        topBar.add(btnPanel, BorderLayout.EAST);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(160);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
        table.getColumnModel().getColumn(6).setPreferredWidth(260);
        table.getColumnModel().getColumn(5).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(6).setCellRenderer(new ActionRenderer());

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (col == 6 && row >= 0) handleAction(e, row);
            }
        });

        add(topBar, BorderLayout.NORTH);
        add(StyledTable.wrap(table), BorderLayout.CENTER);
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    public void loadData() {
        new SwingWorker<List<Restaurant>, Void>() {
            @Override
            protected List<Restaurant> doInBackground() {
                return dao.findAll();
            }
            @Override
            protected void done() {
                try {
                    displayedItems = get();
                    refreshTable();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(RestaurantPanel.this,
                        "Lỗi tải danh sách nhà hàng: " + ex.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (Restaurant r : displayedItems) {
            tableModel.addRow(new Object[]{
                r.getRestaurantId(),
                r.getName(),
                r.getAddress(),
                r.getPhone(),
                r.getEmail(),
                r.getStatus() != null ? r.getStatus().label() : "",
                ""
            });
        }
    }

    // ── Action handler ────────────────────────────────────────────────────────

    private void handleAction(MouseEvent e, int row) {
        Rectangle cellRect = table.getCellRect(row, 6, false);
        int relX = e.getX() - cellRect.x;
        Restaurant item = displayedItems.get(row);

        if (relX < 64) {
            openEditDialog(item);
        } else if (relX < 180) {
            toggleStatus(item);
        } else {
            deleteRestaurant(item);
        }
    }

    // ── CRUD operations ───────────────────────────────────────────────────────

    private void openAddDialog() {
        new RestaurantDialog(SwingUtilities.getWindowAncestor(this), null,
            (Restaurant saved, AdminChoice adminChoice) -> {
                new SwingWorker<String, Void>() {

                    @Override
                    protected String doInBackground() throws Exception {
                        dao.add(saved);
                        long restaurantId = saved.getRestaurantId();

                        return switch (adminChoice.mode) {
                            case EXISTING -> {
                                userDAO.assignAdminToRestaurant(
                                    adminChoice.existingUserId, restaurantId);
                                yield "Nhà hàng đã được tạo và gán admin thành công!";
                            }
                            case NEW -> {
                                userDAO.registerRestaurantAdmin(
                                    adminChoice.newName,
                                    adminChoice.newEmail,
                                    adminChoice.newPassword,
                                    restaurantId);
                                yield "Nhà hàng đã được tạo!\n"
                                    + "Tài khoản admin mới: " + adminChoice.newEmail;
                            }
                            case SKIP ->
                                "Nhà hàng đã được tạo. Nhớ gán admin sau!";
                        };
                    }

                    @Override
                    protected void done() {
                        try {
                            String msg = get();
                            loadData();
                            JOptionPane.showMessageDialog(RestaurantPanel.this,
                                msg, "Thành công", JOptionPane.INFORMATION_MESSAGE);
                        } catch (java.util.concurrent.ExecutionException ee) {
                            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                            loadData();
                            JOptionPane.showMessageDialog(RestaurantPanel.this,
                                "Nhà hàng đã tạo nhưng gán admin thất bại:\n"
                                + cause.getMessage()
                                + "\n\nVui lòng gán admin sau.",
                                "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(RestaurantPanel.this,
                                "Lỗi thêm nhà hàng: " + getRootMessage(ex),
                                "Lỗi", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }.execute();
            }
        ).setVisible(true);
    }

    private void openEditDialog(Restaurant item) {
        Restaurant fresh = findByIdSafe(item.getRestaurantId());
        if (fresh == null) {
            JOptionPane.showMessageDialog(this,
                "Không tìm thấy nhà hàng (id=" + item.getRestaurantId() + ")",
                "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            loadData();
            return;
        }

        new RestaurantDialog(SwingUtilities.getWindowAncestor(this), fresh, saved -> {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    dao.update(saved);
                    return null;
                }
                @Override protected void done() {
                    try { get(); loadData(); }
                    catch (Exception ex) {
                        JOptionPane.showMessageDialog(RestaurantPanel.this,
                            "Lỗi cập nhật nhà hàng: " + getRootMessage(ex),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }).setVisible(true);
    }

    private void toggleStatus(Restaurant item) {
        Status newStatus = (item.getStatus() == Status.ACTIVE) ? Status.INACTIVE : Status.ACTIVE;
        String label     = (newStatus == Status.ACTIVE) ? "kích hoạt" : "vô hiệu hóa";

        int confirm = JOptionPane.showConfirmDialog(this,
            "Bạn có muốn " + label + " nhà hàng \"" + item.getName() + "\"?",
            "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                dao.updateStatus(item.getRestaurantId(), newStatus.name());
                return null;
            }
            @Override protected void done() {
                try { get(); loadData(); }
                catch (Exception ex) {
                    JOptionPane.showMessageDialog(RestaurantPanel.this,
                        "Lỗi thay đổi trạng thái: " + getRootMessage(ex),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Xoá nhà hàng sau khi người dùng xác nhận bằng Operation Token.
     *
     * <p>Thay thế JOptionPane YES/NO thông thường bằng {@link ConfirmOperationDialog}
     * để buộc người dùng nhập lại mã xác nhận ngắn hạn trước khi thực thi.
     */
    private void deleteRestaurant(Restaurant item) {
        // ── Operation Token: yêu cầu xác nhận bằng mã ngắn hạn ────────────
        boolean confirmed = ConfirmOperationDialog.show(
            SwingUtilities.getWindowAncestor(this),
            OperationType.DELETE_RESTAURANT,
            item.getRestaurantId());
        if (!confirmed) return;

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                dao.delete(item.getRestaurantId());
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    loadData();
                } catch (java.util.concurrent.ExecutionException ee) {
                    Throwable cause = ee.getCause();
                    if (cause instanceof IllegalStateException) {
                        JOptionPane.showMessageDialog(RestaurantPanel.this,
                            cause.getMessage(),
                            "Không thể xóa", JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(RestaurantPanel.this,
                            "Lỗi xóa nhà hàng: " + (cause != null ? cause.getMessage() : ee.getMessage()),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(RestaurantPanel.this,
                        "Lỗi xóa nhà hàng: " + ex.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Restaurant findByIdSafe(long id) {
        try { return dao.findById(id); } catch (Exception e) { return null; }
    }

    private String getRootMessage(Exception ex) {
        Throwable cause = ex.getCause();
        return cause != null ? cause.getMessage() : ex.getMessage();
    }

    // ── Cell Renderers ────────────────────────────────────────────────────────

    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            String txt = value != null ? value.toString() : "";
            if ("Hoạt động".equals(txt)) {
                lbl.setForeground(UIConstants.SUCCESS);
                lbl.setFont(UIConstants.FONT_BOLD);
            } else {
                lbl.setForeground(UIConstants.DANGER);
                lbl.setFont(UIConstants.FONT_BOLD);
            }
            return lbl;
        }
    }

    private class ActionRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            Color bg = isSelected ? UIConstants.ROW_SELECTED
                                  : (row % 2 == 0 ? Color.WHITE : new Color(0xF9FAFB));
            panel.setBackground(bg);

            String toggleLabel = "Kích hoạt";
            if (row < displayedItems.size()) {
                Restaurant r = displayedItems.get(row);
                toggleLabel = (r.getStatus() == Status.ACTIVE) ? "Vô hiệu hóa" : "Kích hoạt";
            }
            Color toggleColor = "Vô hiệu hóa".equals(toggleLabel)
                    ? UIConstants.WARNING : UIConstants.SUCCESS;

            panel.add(styledLabel("✏ Sửa",           UIConstants.PRIMARY));
            panel.add(sep());
            panel.add(styledLabel("⇄ " + toggleLabel, toggleColor));
            panel.add(sep());
            panel.add(styledLabel("🗑 Xóa",           UIConstants.DANGER));
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
