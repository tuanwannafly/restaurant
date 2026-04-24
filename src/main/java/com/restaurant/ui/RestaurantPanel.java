package com.restaurant.ui;

import com.restaurant.dao.RestaurantDAO;
import com.restaurant.model.Restaurant;
import com.restaurant.model.Restaurant.Status;
import com.restaurant.ui.dialog.RestaurantDialog;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel quản lý danh sách nhà hàng — chỉ hiển thị khi người dùng là SUPER_ADMIN.
 *
 * <p>Cấu trúc giống {@link EmployeePanel}:
 * <ul>
 *   <li>JTable hiển thị danh sách (ID, Tên, Địa chỉ, SĐT, Email, Trạng thái)</li>
 *   <li>Nút "Thêm nhà hàng" → mở {@link RestaurantDialog}</li>
 *   <li>Cột Actions: [Sửa] [Kích hoạt / Vô hiệu hóa] [Xóa]</li>
 * </ul>
 */
public class RestaurantPanel extends JPanel {

    private final RestaurantDAO          dao           = new RestaurantDAO();
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
        // ── Top bar ──
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

        // ── Table ──
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

    /** Tải dữ liệu từ DB bằng SwingWorker (không block EDT). */
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
                ""   // placeholder cho ActionRenderer
            });
        }
    }

    // ── Action handler ────────────────────────────────────────────────────────

    /**
     * Xác định hành động dựa trên vị trí click trong cột Actions.
     * Phân vùng X:
     *   [0  – 64)  → Sửa
     *   [64 – 180) → Toggle status
     *   [180 – ∞)  → Xóa
     */
    private void handleAction(MouseEvent e, int row) {
        Rectangle cellRect = table.getCellRect(row, 6, false);
        int relX = e.getX() - cellRect.x;
        Restaurant item = displayedItems.get(row);

        if (relX < 64) {
            // ── Sửa ──
            openEditDialog(item);
        } else if (relX < 180) {
            // ── Toggle status ──
            toggleStatus(item);
        } else {
            // ── Xóa ──
            deleteRestaurant(item);
        }
    }

    // ── CRUD operations ───────────────────────────────────────────────────────

    private void openAddDialog() {
        new RestaurantDialog(SwingUtilities.getWindowAncestor(this), null, saved -> {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    dao.add(saved);
                    return null;
                }
                @Override protected void done() {
                    try { get(); loadData(); }
                    catch (Exception ex) {
                        JOptionPane.showMessageDialog(RestaurantPanel.this,
                            "Lỗi thêm nhà hàng: " + getRootMessage(ex),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }).setVisible(true);
    }

    private void openEditDialog(Restaurant item) {
        // Refresh từ DB trước khi mở dialog
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

    private void deleteRestaurant(Restaurant item) {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Xóa nhà hàng \"" + item.getName() + "\"?\nThao tác này không thể hoàn tác.",
            "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

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
                    // Hiện thông báo ràng buộc nhân viên rõ ràng
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
        try {
            return dao.findById(id);
        } catch (Exception e) {
            return null;
        }
    }

    /** Lấy message gốc từ exception (unwrap RuntimeException wrapper). */
    private String getRootMessage(Exception ex) {
        Throwable cause = ex.getCause();
        if (cause != null) return cause.getMessage();
        return ex.getMessage();
    }

    // ── Cell Renderers ────────────────────────────────────────────────────────

    /** Tô màu cột Trạng thái: xanh = ACTIVE, đỏ = INACTIVE. */
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

    /**
     * Renderer cột Actions: [Sửa] [Kích hoạt/Vô hiệu hóa] [Xóa].
     * Label nút toggle thay đổi theo trạng thái của dòng hiện tại.
     */
    private class ActionRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            Color bg = isSelected ? UIConstants.ROW_SELECTED
                                  : (row % 2 == 0 ? Color.WHITE : new Color(0xF9FAFB));
            panel.setBackground(bg);

            // Xác định nhãn nút toggle
            String toggleLabel = "Kích hoạt";
            if (row < displayedItems.size()) {
                Restaurant r = displayedItems.get(row);
                toggleLabel = (r.getStatus() == Status.ACTIVE) ? "Vô hiệu hóa" : "Kích hoạt";
            }
            Color toggleColor = "Vô hiệu hóa".equals(toggleLabel)
                    ? UIConstants.WARNING : UIConstants.SUCCESS;

            panel.add(styledLabel("✏ Sửa",          UIConstants.PRIMARY));
            panel.add(sep());
            panel.add(styledLabel("⇄ " + toggleLabel, toggleColor));
            panel.add(sep());
            panel.add(styledLabel("🗑 Xóa",          UIConstants.DANGER));
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