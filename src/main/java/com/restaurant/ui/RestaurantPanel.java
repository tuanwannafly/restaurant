package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
 * <p>Phase 2 — Redesign: thêm search bar, filter dropdowns, nút Lọc và
 * 3 nút hành động riêng biệt (Xóa | Cập nhật | Xem chi tiết).
 * Thêm pagination footer. Giữ nguyên toàn bộ logic DAO và SwingWorker.
 *
 * <p><b>Giai đoạn 3 — Operation Token:</b> thao tác xoá nhà hàng yêu cầu
 * người dùng xác nhận bằng {@link ConfirmOperationDialog}.
 */
public class RestaurantPanel extends JPanel {

    // ── DAO & Data ────────────────────────────────────────────────────────────

    private final RestaurantDAO     dao            = new RestaurantDAO();
    private final UserDAO           userDAO        = new UserDAO();
    private       DefaultTableModel tableModel;
    private       StyledTable       table;
    private       List<Restaurant>  allItems       = new ArrayList<>();
    private       List<Restaurant>  displayedItems = new ArrayList<>();

    // ── Pagination ────────────────────────────────────────────────────────────

    private static final int PAGE_SIZE   = 10;
    private int currentPage  = 1;
    private int totalPages   = 1;
    private JPanel paginationPanel;

    // ── Filter controls ───────────────────────────────────────────────────────

    private RoundedTextField txtSearch;
    private JComboBox<String> cboStatus;
    private JComboBox<String> cboSort;

    // ── Table columns ─────────────────────────────────────────────────────────

    private static final String[] COLUMNS = {
        "ID", "Tên nhà hàng", "Email", "Ngày tạo", "Trạng thái", "Hành động"
    };
    // Column indices
    private static final int COL_ID      = 0;
    private static final int COL_NAME    = 1;
    private static final int COL_EMAIL   = 2;
    private static final int COL_DATE    = 3;
    private static final int COL_STATUS  = 4;
    private static final int COL_ACTION  = 5;

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
        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildTableArea(), BorderLayout.CENTER);
        add(buildFooter(),    BorderLayout.SOUTH);
    }

    /** Row-1: title + "+ Thêm nhà hàng" | Row-2: search + filter */
    private JPanel buildTopBar() {
        JPanel topBar = new JPanel(new BorderLayout(0, 10));
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        // ── Row 1 ─────────────────────────────────────────────────────────────
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);

        JLabel titleLbl = new JLabel("Quản lý nhà hàng");
        titleLbl.setFont(UIConstants.FONT_TITLE);
        titleLbl.setForeground(UIConstants.TEXT_PRIMARY);

        RoundedButton btnAdd = new RoundedButton("+ Thêm nhà hàng");
        btnAdd.setPreferredSize(new Dimension(160, UIConstants.BTN_HEIGHT));
        btnAdd.addActionListener(e -> openAddDialog());

        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnWrap.setOpaque(false);
        btnWrap.add(btnAdd);

        row1.add(titleLbl, BorderLayout.WEST);
        row1.add(btnWrap,  BorderLayout.EAST);

        // ── Row 2 ─────────────────────────────────────────────────────────────
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row2.setOpaque(false);

        // Search field
        txtSearch = new RoundedTextField("Tìm kiếm");
        txtSearch.setPreferredSize(new Dimension(300, UIConstants.BTN_HEIGHT));
        txtSearch.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) applyFilter();
            }
        });

        // Search button
        RoundedButton btnSearch = RoundedButton.outline("🔍");
        btnSearch.setPreferredSize(new Dimension(UIConstants.BTN_HEIGHT, UIConstants.BTN_HEIGHT));
        btnSearch.addActionListener(e -> applyFilter());

        // Status filter
        cboStatus = new JComboBox<>(new String[]{"Tất cả", "Hoạt động", "Vô hiệu hóa"});
        cboStatus.setFont(UIConstants.FONT_BODY);
        cboStatus.setPreferredSize(new Dimension(140, UIConstants.BTN_HEIGHT));

        // Sort filter
        cboSort = new JComboBox<>(new String[]{"Mới nhất", "Tên A-Z", "Tên Z-A"});
        cboSort.setFont(UIConstants.FONT_BODY);
        cboSort.setPreferredSize(new Dimension(130, UIConstants.BTN_HEIGHT));

        // Lọc button
        RoundedButton btnFilter = RoundedButton.outline("Lọc");
        btnFilter.setPreferredSize(new Dimension(60, UIConstants.BTN_HEIGHT));
        btnFilter.addActionListener(e -> applyFilter());

        row2.add(txtSearch);
        row2.add(btnSearch);
        row2.add(Box.createHorizontalStrut(8));
        row2.add(cboStatus);
        row2.add(cboSort);
        row2.add(btnFilter);

        topBar.add(row1, BorderLayout.NORTH);
        topBar.add(row2, BorderLayout.SOUTH);
        return topBar;
    }

    private JPanel buildTableArea() {
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        table.getColumnModel().getColumn(COL_ID)     .setPreferredWidth(50);
        table.getColumnModel().getColumn(COL_NAME)   .setPreferredWidth(200);
        table.getColumnModel().getColumn(COL_EMAIL)  .setPreferredWidth(200);
        table.getColumnModel().getColumn(COL_DATE)   .setPreferredWidth(100);
        table.getColumnModel().getColumn(COL_STATUS) .setPreferredWidth(100);
        table.getColumnModel().getColumn(COL_ACTION) .setPreferredWidth(280);

        table.getColumnModel().getColumn(COL_STATUS).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(COL_ACTION).setCellRenderer(new ActionRenderer());

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (col == COL_ACTION && row >= 0) handleAction(e, row);
            }
        });

        return StyledTable.wrap(table);
    }

    /** Pagination footer: "1 | 2 | 3 | … | Xem thêm" */
    private JPanel buildFooter() {
        paginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        paginationPanel.setOpaque(false);
        return paginationPanel;
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
                    allItems       = get();
                    displayedItems = new ArrayList<>(allItems);
                    currentPage    = 1;
                    updatePagination(displayedItems.size());
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
        int from = (currentPage - 1) * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, displayedItems.size());
        for (int i = from; i < to; i++) {
            Restaurant r = displayedItems.get(i);
            tableModel.addRow(new Object[]{
                r.getRestaurantId(),
                r.getName(),
                r.getEmail(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString().substring(0, 10) : "",
                r.getStatus() != null ? r.getStatus().label() : "",
                ""   // placeholder for ActionRenderer
            });
        }
    }

    // ── Filter / Sort ─────────────────────────────────────────────────────────

    private void applyFilter() {
        String query      = txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        String statusSel  = (String) cboStatus.getSelectedItem();
        String sortSel    = (String) cboSort.getSelectedItem();

        List<Restaurant> result = allItems.stream()
            .filter(r -> {
                if (query.isEmpty()) return true;
                String name  = r.getName()  != null ? r.getName().toLowerCase(Locale.ROOT)  : "";
                String email = r.getEmail() != null ? r.getEmail().toLowerCase(Locale.ROOT) : "";
                return name.contains(query) || email.contains(query);
            })
            .filter(r -> {
                if ("Tất cả".equals(statusSel))         return true;
                if ("Hoạt động".equals(statusSel))      return r.getStatus() == Status.ACTIVE;
                if ("Vô hiệu hóa".equals(statusSel))   return r.getStatus() == Status.INACTIVE;
                return true;
            })
            .collect(Collectors.toList());

        if ("Tên A-Z".equals(sortSel)) {
            result.sort(Comparator.comparing(r -> r.getName() != null ? r.getName() : ""));
        } else if ("Tên Z-A".equals(sortSel)) {
            result.sort(Comparator.comparing((Restaurant r) -> r.getName() != null ? r.getName() : "").reversed());
        }
        // "Mới nhất" → keep insertion order (descending ID from DAO)

        displayedItems = result;
        currentPage    = 1;
        updatePagination(displayedItems.size());
        refreshTable();
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    private void updatePagination(int totalItems) {
        totalPages = (totalItems == 0) ? 1 : (int) Math.ceil((double) totalItems / PAGE_SIZE);
        paginationPanel.removeAll();

        for (int p = 1; p <= Math.min(totalPages, 3); p++) {
            final int page = p;
            if (p == currentPage) {
                RoundedButton btn = new RoundedButton(String.valueOf(p));
                btn.setPreferredSize(new Dimension(34, UIConstants.BTN_HEIGHT));
                btn.addActionListener(e -> { currentPage = page; refreshTable(); });
                paginationPanel.add(btn);
            } else {
                RoundedButton btn = RoundedButton.outline(String.valueOf(p));
                btn.setPreferredSize(new Dimension(34, UIConstants.BTN_HEIGHT));
                btn.addActionListener(e -> { currentPage = page; refreshTable(); });
                paginationPanel.add(btn);
            }
        }

        if (totalPages > 3) {
            JLabel ellipsis = new JLabel("...");
            ellipsis.setFont(UIConstants.FONT_BODY);
            ellipsis.setForeground(UIConstants.TEXT_SECONDARY);
            paginationPanel.add(ellipsis);

            RoundedButton btnMore = RoundedButton.outline("Xem thêm");
            btnMore.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
            btnMore.addActionListener(e -> {
                if (currentPage < totalPages) {
                    currentPage++;
                    updatePagination(displayedItems.size());
                    refreshTable();
                }
            });
            paginationPanel.add(btnMore);
        }

        paginationPanel.revalidate();
        paginationPanel.repaint();
    }

    // ── Action handler ────────────────────────────────────────────────────────

    /**
     * Phân biệt 3 vùng click trong cột Hành động dựa trên relX:
     * <pre>
     *   relX &lt;  74  → Xóa
     *   relX &lt; 168  → Cập nhật
     *   else        → Xem chi tiết
     * </pre>
     */
    private void handleAction(MouseEvent e, int row) {
        // row is visual row inside current page
        int dataIndex = (currentPage - 1) * PAGE_SIZE + row;
        if (dataIndex < 0 || dataIndex >= displayedItems.size()) return;

        Rectangle cellRect = table.getCellRect(row, COL_ACTION, false);
        int relX = e.getX() - cellRect.x;
        Restaurant item = displayedItems.get(dataIndex);

        if (relX < 74) {
            deleteRestaurant(item);
        } else if (relX < 168) {
            openEditDialog(item);
        } else {
            openDetailView(item);
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
                            case SKIP -> "Nhà hàng đã được tạo. Nhớ gán admin sau!";
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
                @Override protected Void doInBackground() { dao.update(saved); return null; }
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

    /**
     * Xem chi tiết nhà hàng — placeholder cho Phase 3.
     * Phase 3 sẽ thay bằng điều hướng sang RestaurantDetailPanel.
     */
    private void openDetailView(Restaurant item) {
        // TODO Phase 3: điều hướng sang màn hình chi tiết nhà hàng
        JOptionPane.showMessageDialog(this,
            "Chi tiết nhà hàng: " + item.getName() + " (sẽ implement ở Phase 3)",
            "Xem chi tiết", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Xoá nhà hàng sau khi người dùng xác nhận bằng Operation Token.
     */
    private void deleteRestaurant(Restaurant item) {
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

    /**
     * Renderer cho cột Hành động — 3 nút riêng biệt:
     * 🗑 Xóa (DANGER) | ✏ Cập nhật (PRIMARY) | 👁 Xem chi tiết (outline)
     *
     * <p>Thứ tự bố cục và khoảng cách phải khớp với ngưỡng relX trong
     * {@link #handleAction} để click detection chính xác:
     * <pre>
     *   [gap4] [Xóa:70] [gap4] [Cập nhật:90] [gap4] [Xem chi tiết:110]
     *           ^-- relX=4..73      ^-- 78..167          ^-- 172+
     * </pre>
     */
    private class ActionRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {

            JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 3));
            Color bg = isSelected ? UIConstants.ROW_SELECTED
                                  : (row % 2 == 0 ? Color.WHITE : new Color(0xF9FAFB));
            panel.setBackground(bg);

            // 🗑 Xóa — DANGER, width 70
            JLabel btnDelete = actionLabel("🗑 Xóa", UIConstants.DANGER);
            btnDelete.setPreferredSize(new Dimension(70, 26));

            // ✏ Cập nhật — PRIMARY, width 90
            JLabel btnEdit = actionLabel("✏ Cập nhật", UIConstants.PRIMARY);
            btnEdit.setPreferredSize(new Dimension(90, 26));

            // 👁 Xem chi tiết — TEXT_SECONDARY outline, width 110
            JLabel btnDetail = actionLabel("👁 Xem chi tiết", UIConstants.TEXT_SECONDARY);
            btnDetail.setPreferredSize(new Dimension(110, 26));

            panel.add(btnDelete);
            panel.add(btnEdit);
            panel.add(btnDetail);
            return panel;
        }

        private JLabel actionLabel(String text, Color color) {
            JLabel l = new JLabel(text, SwingConstants.CENTER);
            l.setFont(UIConstants.FONT_SMALL);
            l.setForeground(color);
            l.setOpaque(false);
            l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
            l.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            return l;
        }
    }
}