package com.restaurant.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.restaurant.data.DataManager;
import com.restaurant.model.Restaurant;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

/**
 * Panel cho RESTAURANT_ADMIN xem và cập nhật thông tin nhà hàng của mình.
 *
 * <p>Chỉ hiển thị khi người dùng có quyền {@code VIEW_OWN_RESTAURANT}.
 * Dữ liệu load bất đồng bộ bằng SwingWorker để không block EDT.
 *
 * <ul>
 *   <li>Tên, địa chỉ, SĐT, email — có thể chỉnh sửa</li>
 *   <li>Trạng thái, ngày tạo — chỉ đọc</li>
 *   <li>Nút "Lưu thay đổi" gọi {@link DataManager#updateMyRestaurant}</li>
 * </ul>
 */
public class MyRestaurantInfoPanel extends JPanel {

    // ── Form fields ───────────────────────────────────────────────────────────
    private JTextField  tfName;
    private JTextField  tfAddress;
    private JTextField  tfPhone;
    private JTextField  tfEmail;
    private JLabel      lblStatus;
    private JLabel      lblCreatedAt;
    private RoundedButton btnSave;
    private JLabel      lblMsg;

    /** Bản ghi nhà hàng đang hiển thị — dùng để lấy restaurantId khi update. */
    private Restaurant currentRestaurant;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ── Constructor ───────────────────────────────────────────────────────────

    public MyRestaurantInfoPanel() {
        setBackground(UIConstants.BG_PAGE);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(32, 64, 32, 64));
        buildUI();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        // ── Page title ──
        JLabel title = new JLabel("Nhà hàng của tôi");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 24, 0));
        add(title, BorderLayout.NORTH);

        // ── Card container ──
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UIConstants.BG_WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(32, 40, 32, 40)));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(8, 0, 8, 16);
        lc.gridx  = 0;

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill    = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets  = new Insets(8, 0, 8, 0);
        fc.gridx   = 1;

        int row = 0;

        // Tên nhà hàng
        lc.gridy = row; fc.gridy = row++;
        card.add(fieldLabel("Tên nhà hàng:"), lc);
        tfName = styledField();
        card.add(tfName, fc);

        // Địa chỉ
        lc.gridy = row; fc.gridy = row++;
        card.add(fieldLabel("Địa chỉ:"), lc);
        tfAddress = styledField();
        card.add(tfAddress, fc);

        // SĐT
        lc.gridy = row; fc.gridy = row++;
        card.add(fieldLabel("SĐT:"), lc);
        tfPhone = styledField();
        card.add(tfPhone, fc);

        // Email
        lc.gridy = row; fc.gridy = row++;
        card.add(fieldLabel("Email:"), lc);
        tfEmail = styledField();
        card.add(tfEmail, fc);

        // Trạng thái (readonly)
        lc.gridy = row; fc.gridy = row++;
        card.add(fieldLabel("Trạng thái:"), lc);
        lblStatus = new JLabel("—");
        lblStatus.setFont(UIConstants.FONT_BODY);
        card.add(lblStatus, fc);

        // Ngày tạo (readonly)
        lc.gridy = row; fc.gridy = row++;
        card.add(fieldLabel("Ngày tạo:"), lc);
        lblCreatedAt = new JLabel("—");
        lblCreatedAt.setFont(UIConstants.FONT_BODY);
        lblCreatedAt.setForeground(UIConstants.TEXT_SECONDARY);
        card.add(lblCreatedAt, fc);

        // ── Message label ──
        GridBagConstraints mc = new GridBagConstraints();
        mc.gridx    = 0; mc.gridy = row++;
        mc.gridwidth = 2;
        mc.anchor   = GridBagConstraints.WEST;
        mc.insets   = new Insets(4, 0, 4, 0);
        lblMsg = new JLabel(" ");
        lblMsg.setFont(UIConstants.FONT_SMALL);
        card.add(lblMsg, mc);

        // ── Save button ──
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx     = 0; bc.gridy = row;
        bc.gridwidth = 2;
        bc.anchor    = GridBagConstraints.EAST;
        bc.insets    = new Insets(16, 0, 0, 0);

        btnSave = new RoundedButton("Lưu thay đổi");
        btnSave.setPreferredSize(new Dimension(150, UIConstants.BTN_HEIGHT));
        btnSave.setEnabled(AppSession.getInstance()
                .hasPermission(Permission.EDIT_OWN_RESTAURANT));
        btnSave.addActionListener(e -> doSave());
        card.add(btnSave, bc);

        // ── Wrap card in a centering panel ──
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        center.setOpaque(false);
        card.setPreferredSize(new Dimension(560, card.getPreferredSize().height));
        center.add(card);
        add(center, BorderLayout.CENTER);
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    /**
     * Tải thông tin nhà hàng từ DB bất đồng bộ.
     * Gọi khi panel được hiển thị (từ {@link MainFrame#navigateTo}).
     */
    public void loadData() {
        lblMsg.setText(" ");
        btnSave.setEnabled(false);

        new SwingWorker<Restaurant, Void>() {
            @Override
            protected Restaurant doInBackground() {
                return DataManager.getInstance().getMyRestaurant();
            }

            @Override
            protected void done() {
                try {
                    currentRestaurant = get();
                    if (currentRestaurant == null) {
                        showMsg("Không tìm thấy thông tin nhà hàng.", false);
                        return;
                    }
                    populate(currentRestaurant);
                    btnSave.setEnabled(AppSession.getInstance()
                            .hasPermission(Permission.EDIT_OWN_RESTAURANT));
                } catch (Exception ex) {
                    showMsg("Lỗi tải dữ liệu: " + ex.getMessage(), false);
                }
            }
        }.execute();
    }

    /** Đẩy dữ liệu từ model vào các field. */
    private void populate(Restaurant r) {
        tfName.setText(r.getName()    != null ? r.getName()    : "");
        tfAddress.setText(r.getAddress() != null ? r.getAddress() : "");
        tfPhone.setText(r.getPhone()   != null ? r.getPhone()   : "");
        tfEmail.setText(r.getEmail()   != null ? r.getEmail()   : "");

        if (r.getStatus() != null) {
            boolean active = r.getStatus() == Restaurant.Status.ACTIVE;
            lblStatus.setText(r.getStatus().label());
            lblStatus.setForeground(active ? UIConstants.SUCCESS : UIConstants.DANGER);
            lblStatus.setFont(UIConstants.FONT_BOLD);
        } else {
            lblStatus.setText("—");
            lblStatus.setForeground(UIConstants.TEXT_SECONDARY);
        }

        lblCreatedAt.setText(r.getCreatedAt() != null
                ? r.getCreatedAt().format(DATE_FMT) : "—");
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void doSave() {
        if (currentRestaurant == null) return;

        String name    = tfName.getText().trim();
        String address = tfAddress.getText().trim();
        String phone   = tfPhone.getText().trim();
        String email   = tfEmail.getText().trim();

        if (name.isEmpty()) {
            showMsg("Tên nhà hàng không được để trống.", false);
            tfName.requestFocus();
            return;
        }

        // Build updated model — giữ nguyên id, status, createdAt
        Restaurant updated = new Restaurant(
                currentRestaurant.getRestaurantId(),
                name, address, phone, email,
                currentRestaurant.getStatus(),
                currentRestaurant.getCreatedAt());

        btnSave.setEnabled(false);
        lblMsg.setText("Đang lưu…");
        lblMsg.setForeground(UIConstants.TEXT_SECONDARY);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                DataManager.getInstance().updateMyRestaurant(updated);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    currentRestaurant = updated;
                    showMsg("Lưu thay đổi thành công!", true);
                    ToastNotification.show(
                            MyRestaurantInfoPanel.this,
                            "Thông tin nhà hàng đã được cập nhật.",
                            ToastNotification.Type.SUCCESS);
                } catch (Exception ex) {
                    showMsg("Lỗi khi lưu: " + ex.getMessage(), false);
                    ToastNotification.show(
                            MyRestaurantInfoPanel.this,
                            "Lưu thất bại: " + ex.getMessage(),
                            ToastNotification.Type.ERROR);
                } finally {
                    btnSave.setEnabled(AppSession.getInstance()
                            .hasPermission(Permission.EDIT_OWN_RESTAURANT));
                }
            }
        }.execute();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JLabel fieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        lbl.setPreferredSize(new Dimension(130, 28));
        return lbl;
    }

    private JTextField styledField() {
        JTextField tf = new JTextField();
        tf.setFont(UIConstants.FONT_BODY);
        tf.setPreferredSize(new Dimension(320, 32));
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return tf;
    }

    private void showMsg(String msg, boolean success) {
        lblMsg.setText(msg);
        lblMsg.setForeground(success ? UIConstants.SUCCESS : UIConstants.DANGER);
    }
}