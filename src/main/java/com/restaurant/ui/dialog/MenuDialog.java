package com.restaurant.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.restaurant.model.MenuItem;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.UIConstants;

public class MenuDialog extends JDialog {
    private Consumer<MenuItem> onSave;
    private MenuItem item;

    // ─── Fields ───────────────────────────────────────────────────────────────
    private JTextField tfName, tfPrice, tfDesc;
    private JComboBox<String> cbCategory;

    // ─── Phase 6B: Image fields ───────────────────────────────────────────────
    private JLabel     imgPreview;
    private JTextField tfImageUrl = new JTextField(); // hidden, không add vào form

    public MenuDialog(Window owner, MenuItem item, Consumer<MenuItem> onSave) {
        super(owner, item == null ? "Thêm món mới" : "Cập nhật món",
                ModalityType.APPLICATION_MODAL);
        this.item   = item;
        this.onSave = onSave;
        buildUI();
        if (item != null) fillData();
        setSize(480, 460); // tăng chiều cao để chứa row ảnh
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    // ─── UI Builder ───────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Color.WHITE);

        // ── Title ──
        JLabel title = new JLabel(item == null ? "Thêm món mới" : "Cập nhật thông tin món");
        title.setFont(UIConstants.FONT_TITLE);
        title.setForeground(UIConstants.TEXT_PRIMARY);
        title.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        root.add(title, BorderLayout.NORTH);

        // ── Form ──
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(0, 24, 12, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(7, 6, 7, 6);
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.anchor  = GridBagConstraints.WEST;

        // Existing fields
        tfName     = field();
        cbCategory = new JComboBox<>(new String[]{
                "Hải sản", "Thịt", "Cơm", "Phở", "Đồ uống", "Khác"});
        cbCategory.setFont(UIConstants.FONT_BODY);
        tfPrice = field();
        tfDesc  = field();

        addRow(form, gbc, 0, "Tên món:",  tfName);
        addRow(form, gbc, 1, "Loại:",     cbCategory);
        addRow(form, gbc, 2, "Giá (đ):",  tfPrice);
        addRow(form, gbc, 3, "Mô tả:",    tfDesc);

        // Phase 6B — row ảnh
        initImagePreview();
        addRowFreeHeight(form, gbc, 4, "Hình ảnh:", buildImagePanel());

        root.add(form, BorderLayout.CENTER);

        // ── Button bar ──
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnBar.setBackground(Color.WHITE);
        btnBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.BORDER_COLOR));

        RoundedButton btnCancel = RoundedButton.outline("Hủy");
        btnCancel.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnCancel.addActionListener(e -> dispose());

        RoundedButton btnSave = new RoundedButton(item == null ? "Thêm món" : "Lưu");
        btnSave.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnSave.addActionListener(e -> save());

        btnBar.add(btnCancel);
        btnBar.add(btnSave);
        root.add(btnBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ─── Phase 6B: init imgPreview ────────────────────────────────────────────

    private void initImagePreview() {
        imgPreview = new JLabel("🍽", SwingConstants.CENTER);
        imgPreview.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        imgPreview.setForeground(new Color(0xD1D5DB));
        imgPreview.setPreferredSize(new Dimension(120, 120));
        imgPreview.setHorizontalAlignment(SwingConstants.CENTER);
        imgPreview.setVerticalAlignment(SwingConstants.CENTER);
        imgPreview.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true));
        imgPreview.setBackground(new Color(0xF3F4F6));
        imgPreview.setOpaque(true);
    }

    // ─── Phase 6B: buildImagePanel ────────────────────────────────────────────

    private JPanel buildImagePanel() {
        RoundedButton btnPickImage = RoundedButton.outline("🖼 Chọn ảnh...");
        btnPickImage.setPreferredSize(new Dimension(130, UIConstants.BTN_HEIGHT));
        btnPickImage.addActionListener(e -> pickImage());

        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        p.add(imgPreview);
        p.add(btnPickImage);
        return p;
    }

    // ─── Phase 6B: pickImage ─────────────────────────────────────────────────

    private void pickImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn ảnh món ăn");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Image files (*.jpg, *.png, *.webp)", "jpg", "jpeg", "png", "webp"));

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File src = chooser.getSelectedFile();
        try {
            // Đảm bảo thư mục đích tồn tại
            File destDir = new File("assets/menu_images");
            destDir.mkdirs();

            // Đổi tên theo timestamp để tránh trùng
            String fileName = System.currentTimeMillis() + "_" + src.getName();
            File dest = new File(destDir, fileName);
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Cập nhật trường path nội bộ
            String relativePath = "assets/menu_images/" + fileName;
            tfImageUrl.setText(relativePath);

            // Cập nhật preview: scale về 120×120
            ImageIcon icon = new ImageIcon(
                    new ImageIcon(dest.getPath())
                            .getImage()
                            .getScaledInstance(120, 120, Image.SCALE_SMOOTH));
            imgPreview.setIcon(icon);
            imgPreview.setText(""); // xóa emoji placeholder

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Không thể copy ảnh: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ─── fillData (khi edit) ──────────────────────────────────────────────────

    private void fillData() {
        tfName.setText(item.getName());
        cbCategory.setSelectedItem(item.getCategory());
        tfPrice.setText(String.valueOf((long) item.getPrice()));
        tfDesc.setText(item.getDescription());

        // Phase 6B: khôi phục ảnh khi edit
        String url = item.getImageUrl();
        if (url != null && !url.isBlank()) {
            tfImageUrl.setText(url);
            try {
                File f = new File(url);
                if (f.exists()) {
                    ImageIcon icon = new ImageIcon(
                            new ImageIcon(f.getPath())
                                    .getImage()
                                    .getScaledInstance(120, 120, Image.SCALE_SMOOTH));
                    imgPreview.setIcon(icon);
                    imgPreview.setText(""); // xóa emoji placeholder
                }
            } catch (Exception ignored) {}
        }
    }

    // ─── save ─────────────────────────────────────────────────────────────────

    private void save() {
        String name     = tfName.getText().trim();
        String cat      = (String) cbCategory.getSelectedItem();
        String priceStr = tfPrice.getText().trim();
        String desc     = tfDesc.getText().trim();

        if (name.isEmpty() || priceStr.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập đầy đủ thông tin!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Giá không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        MenuItem saved = item == null
                ? new MenuItem("", name, cat, price, desc)
                : new MenuItem(item.getId(), name, cat, price, desc);

        // Phase 6B: gán imageUrl vào MenuItem trước khi callback
        saved.setImageUrl(tfImageUrl.getText().trim());

        onSave.accept(saved);
        dispose();
    }

    // ─── Layout helpers ───────────────────────────────────────────────────────

    /** Row có chiều cao cố định 34px cho các field thông thường. */
    private void addRow(JPanel form, GridBagConstraints gbc,
                        int row, String label, JComponent comp) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(100, 32));
        form.add(lbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        comp.setPreferredSize(new Dimension(290, 34));
        form.add(comp, gbc);
    }

    /**
     * Row không ép chiều cao — dùng cho panel ảnh (120px preview).
     * Label căn top để không bị kéo giãn theo chiều dọc.
     */
    private void addRowFreeHeight(JPanel form, GridBagConstraints gbc,
                                  int row, String label, JComponent comp) {
        GridBagConstraints lblGbc = (GridBagConstraints) gbc.clone();
        lblGbc.gridx   = 0;
        lblGbc.gridy   = row;
        lblGbc.weightx = 0;
        lblGbc.anchor  = GridBagConstraints.NORTHWEST;
        lblGbc.fill    = GridBagConstraints.NONE;
        lblGbc.insets  = new Insets(14, 6, 7, 6); // căn top với preview
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setPreferredSize(new Dimension(100, 32));
        form.add(lbl, lblGbc);

        GridBagConstraints compGbc = (GridBagConstraints) gbc.clone();
        compGbc.gridx   = 1;
        compGbc.gridy   = row;
        compGbc.weightx = 1;
        compGbc.fill    = GridBagConstraints.HORIZONTAL;
        compGbc.anchor  = GridBagConstraints.WEST;
        form.add(comp, compGbc);
    }

    private JTextField field() {
        JTextField tf = new JTextField();
        tf.setFont(UIConstants.FONT_BODY);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        return tf;
    }
}