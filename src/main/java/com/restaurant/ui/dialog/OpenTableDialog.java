package com.restaurant.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.restaurant.dao.OrderDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.TableItem;
import com.restaurant.session.AppSession;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.RoundedTextField;
import com.restaurant.ui.UIConstants;

/**
 * Dialog mở bàn cho khách — Phase 2 (Thu ngân).
 *
 * <p>Dùng modal APPLICATION_MODAL nên caller có thể block sau {@code setVisible(true)}
 * và kiểm tra {@link #isConfirmed()} ngay sau khi dialog đóng.
 *
 * <p><b>Luồng thực hiện khi xác nhận:</b>
 * <ol>
 *   <li>Gọi {@link OrderDAO#createEmptyOrder} để tạo đơn rỗng trong DB.</li>
 *   <li>Gọi {@link TableDAO#updateStatus} để chuyển bàn sang trạng thái {@code BAN}.</li>
 *   <li>Set {@code confirmed = true} rồi {@code dispose()}.</li>
 * </ol>
 *
 * <p>Nếu có lỗi DB, dialog giữ nguyên (không đóng) và hiện thông báo lỗi.
 */
public class OpenTableDialog extends javax.swing.JDialog {

    // ─── State ────────────────────────────────────────────────────────────────

    private final String tableId;
    private final long   restaurantId;

    private RoundedTextField tfName;
    private RoundedTextField tfPhone;

    /** {@code true} khi người dùng nhấn "Mở bàn" và DB thành công. */
    private boolean confirmed = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * @param owner     cửa sổ cha (dùng cho modal + căn giữa)
     * @param tableId   ID bàn cần mở (khoá chính DB)
     * @param tableName tên bàn hiển thị trên tiêu đề
     */
    public OpenTableDialog(Window owner, String tableId, String tableName) {
        super(owner, "Mở bàn – " + tableName, ModalityType.APPLICATION_MODAL);
        this.tableId      = tableId;
        this.restaurantId = AppSession.getInstance().getRestaurantId();
        buildUI(tableName);
        setSize(460, 290);
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    // ─── UI ───────────────────────────────────────────────────────────────────

    private void buildUI(String tableName) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIConstants.BG_PAGE);

        // ── Title ──────────────────────────────────────────────────────────
        JLabel lblTitle = new JLabel("Mở bàn: " + tableName);
        lblTitle.setFont(UIConstants.FONT_TITLE);
        lblTitle.setForeground(UIConstants.TEXT_PRIMARY);
        lblTitle.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        root.add(lblTitle, BorderLayout.NORTH);

        // ── Form ───────────────────────────────────────────────────────────
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UIConstants.BG_PAGE);
        form.setBorder(BorderFactory.createEmptyBorder(4, 24, 4, 24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 6, 8, 6);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        tfName  = new RoundedTextField("(tùy chọn)");
        tfPhone = new RoundedTextField("(tùy chọn)");

        addRow(form, gbc, 0, "Tên khách:",       tfName);
        addRow(form, gbc, 1, "Số điện thoại:",   tfPhone);
        root.add(form, BorderLayout.CENTER);

        // ── Buttons ────────────────────────────────────────────────────────
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnBar.setBackground(UIConstants.BG_PAGE);
        btnBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR));

        RoundedButton btnCancel = RoundedButton.outline("Hủy");
        btnCancel.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnCancel.addActionListener(e -> dispose());

        RoundedButton btnOpen = new RoundedButton("Mở bàn");
        btnOpen.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnOpen.addActionListener(e -> confirm());

        btnBar.add(btnCancel);
        btnBar.add(btnOpen);
        root.add(btnBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void addRow(JPanel form, GridBagConstraints gbc,
                        int row, String labelText, JComponent comp) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(130, 34));
        form.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        comp.setPreferredSize(new Dimension(240, 34));
        form.add(comp, gbc);
    }

    // ─── Logic ────────────────────────────────────────────────────────────────

    private void confirm() {
        String name  = tfName.getText().trim();
        String phone = tfPhone.getText().trim();

        try {
            new OrderDAO().createEmptyOrder(
                tableId,
                restaurantId,
                name.isEmpty()  ? null : name,
                phone.isEmpty() ? null : phone
            );
            new TableDAO().updateStatus(tableId, TableItem.Status.BAN);

            confirmed = true;
            dispose();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Lỗi mở bàn: " + ex.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** @return {@code true} nếu người dùng đã xác nhận và DB thành công. */
    public boolean isConfirmed() { return confirmed; }

    /** @return tên khách (có thể rỗng nếu không nhập). */
    public String getCustomerName() { return tfName.getText().trim(); }

    /** @return số điện thoại (có thể rỗng nếu không nhập). */
    public String getCustomerPhone() { return tfPhone.getText().trim(); }
}