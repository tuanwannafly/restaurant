package com.restaurant.ui.dialog;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.restaurant.model.MenuItem;
import com.restaurant.ui.AppComboBox;
import com.restaurant.ui.AppTextField;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.UIConstants;

/**
 * MenuDialog — Phase 4 (redesigned)
 *
 * <p>Extends {@link AppDialog}. Inherits the coloured header, ESC / Enter
 * shortcuts, and the 2-column {@link AppDialog.FormBuilder} layout.
 *
 * <p>Phase 6B image picking behaviour is preserved and integrated into
 * the form builder row system.
 */
public class MenuDialog extends AppDialog {

    // ── Form fields ───────────────────────────────────────────────────────────
    private AppTextField        tfName, tfPrice, tfDesc;
    private AppComboBox<String> cbCategory;

    // Error labels (attached in buildBody)
    private JLabel errName, errPrice;

    // ── Phase 6B: image ───────────────────────────────────────────────────────
    private JLabel     imgPreview;
    private String     imageUrlValue = ""; // internal state

    // ── Data ──────────────────────────────────────────────────────────────────
    private final MenuItem         item;
    private final Consumer<MenuItem> onSave;

    // ─────────────────────────────────────────────────────────────────────────

    public MenuDialog(Window owner, MenuItem item, Consumer<MenuItem> onSave) {
        super(owner);
        this.item   = item;
        this.onSave = onSave;

        setSize(520, 500);
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    // ── AppDialog contract ───────────────────────────────────────────────────

    @Override
    protected String getDialogTitle() {
        return item == null ? "Thêm món mới" : "Cập nhật thông tin món";
    }

    @Override
    protected String getSaveLabel() {
        return item == null ? "Thêm món" : "Lưu";
    }

    @Override
    protected JPanel buildBody() {
        FormBuilder fb = new FormBuilder();

        // ── Name ──────────────────────────────────────────────────────────
        tfName = new AppTextField("VD: Cơm gà hội an...");
        errName = fb.addRow("Tên món *:", tfName);
        tfName.attachErrorLabel(errName);

        // ── Category ──────────────────────────────────────────────────────
        cbCategory = new AppComboBox<>(new String[]{
                "Hải sản", "Thịt", "Cơm", "Phở", "Đồ uống", "Khác"});
        cbCategory.setFont(UIConstants.FONT_BODY);
        fb.addRow("Loại:", cbCategory);

        // ── Price ─────────────────────────────────────────────────────────
        tfPrice = new AppTextField("VD: 85000");
        errPrice = fb.addRow("Giá (VND) *:", tfPrice);
        tfPrice.attachErrorLabel(errPrice);

        // ── Description ───────────────────────────────────────────────────
        tfDesc = new AppTextField("Mô tả ngắn...");
        fb.addRow("Mô tả:", tfDesc);

        // ── Image (Phase 6B) ──────────────────────────────────────────────
        fb.addFreeRow("Hình ảnh:", buildImagePanel());

        // Pre-fill when editing
        if (item != null) fillData();

        return fb.getPanel();
    }

    // ── Image panel (Phase 6B) ───────────────────────────────────────────────

    private JPanel buildImagePanel() {
        // Preview box
        imgPreview = new JLabel("+", SwingConstants.CENTER);
        imgPreview.setFont(UIConstants.FONT_TITLE.deriveFont(Font.PLAIN, 28f));
        imgPreview.setForeground(new Color(0xCBD5E1));
        imgPreview.setPreferredSize(new Dimension(100, 100));
        imgPreview.setHorizontalAlignment(SwingConstants.CENTER);
        imgPreview.setVerticalAlignment(SwingConstants.CENTER);
        imgPreview.setOpaque(true);
        imgPreview.setBackground(new Color(0xF8FAFC));
        imgPreview.setBorder(BorderFactory.createDashedBorder(new Color(0xCBD5E1), 4f, 4f, 4f, false));
        imgPreview.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        imgPreview.setToolTipText("Nhấn để chọn ảnh");
        imgPreview.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { pickImage(); }
            @Override public void mouseEntered(MouseEvent e) {
                imgPreview.setBackground(new Color(0xEFF6FF));
            }
            @Override public void mouseExited(MouseEvent e) {
                imgPreview.setBackground(new Color(0xF8FAFC));
            }
        });

        // Pick button
        RoundedButton btnPick = RoundedButton.outline("Chọn ảnh...");
        btnPick.setPreferredSize(new Dimension(120, UIConstants.BTN_HEIGHT));
        btnPick.addActionListener(e -> pickImage());

        // Hint label
        JLabel hint = new JLabel("JPG, PNG, WebP — tối đa 5 MB");
        hint.setFont(UIConstants.FONT_BODY.deriveFont(Font.PLAIN, 11.5f));
        hint.setForeground(new Color(0x94A3B8));

        // Right-side text column
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setOpaque(false);
        rightCol.add(Box.createVerticalStrut(8));
        rightCol.add(btnPick);
        rightCol.add(Box.createVerticalStrut(6));
        rightCol.add(hint);

        // Wrapper
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        panel.setOpaque(false);
        panel.add(imgPreview);
        panel.add(rightCol);
        return panel;
    }

    private void pickImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn ảnh món ăn");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Image files (*.jpg, *.png, *.webp)", "jpg", "jpeg", "png", "webp"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File src = chooser.getSelectedFile();
        if (src.length() > 5L * 1024 * 1024) {
            JOptionPane.showMessageDialog(this,
                "File vuot qua 5 MB. Vui long chon anh nho hon.",
                "Anh qua lon", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            File destDir = new File("assets/menu_images");
            destDir.mkdirs();
            String fileName = System.currentTimeMillis() + "_" + src.getName();
            File   dest     = new File(destDir, fileName);
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            imageUrlValue = "assets/menu_images/" + fileName;
            loadPreview(dest.getPath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Khong the copy anh: " + ex.getMessage(),
                "Loi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadPreview(String path) {
        try {
            ImageIcon icon = new ImageIcon(
                new ImageIcon(path).getImage()
                    .getScaledInstance(100, 100, Image.SCALE_SMOOTH));
            imgPreview.setIcon(icon);
            imgPreview.setText("");
        } catch (Exception ignored) {}
    }

    // ── Fill data ────────────────────────────────────────────────────────────

    private void fillData() {
        tfName.setText(item.getName());
        cbCategory.setSelectedItem(item.getCategory());
        tfPrice.setText(String.valueOf((long) item.getPrice()));
        tfDesc.setText(item.getDescription());

        String url = item.getImageUrl();
        if (url != null && !url.isBlank()) {
            imageUrlValue = url;
            File f = new File(url);
            if (f.exists()) loadPreview(f.getPath());
        }
    }

    // ── Validation + Save ────────────────────────────────────────────────────

    @Override
    protected void onSave() {
        boolean valid = true;

        String name = tfName.getText().trim();
        if (name.isEmpty()) {
            tfName.setError("Vui long nhap ten mon an");
            valid = false;
        } else {
            tfName.setError(null);
        }

        String priceStr = tfPrice.getText().trim();
        double price    = 0;
        if (priceStr.isEmpty()) {
            tfPrice.setError("Vui long nhap gia");
            valid = false;
        } else {
            try {
                price = Double.parseDouble(priceStr.replaceAll("[,.]", ""));
                if (price <= 0) throw new NumberFormatException();
                tfPrice.setError(null);
            } catch (NumberFormatException ex) {
                tfPrice.setError("Gia phai la so nguyen duong");
                valid = false;
            }
        }

        if (!valid) return;

        String cat  = (String) cbCategory.getSelectedItem();
        String desc = tfDesc.getText().trim();

        MenuItem saved = item == null
            ? new MenuItem("", name, cat, price, desc)
            : new MenuItem(item.getId(), name, cat, price, desc);
        saved.setImageUrl(imageUrlValue);

        onSave.accept(saved);
        close();
    }
}