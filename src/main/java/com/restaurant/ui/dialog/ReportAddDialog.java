package com.restaurant.ui.dialog;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;

import com.restaurant.dao.ReportDAO;
import com.restaurant.model.Report;
import com.restaurant.ui.ReportPanel;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.RoundedTextField;
import com.restaurant.ui.UIConstants;

public class ReportAddDialog extends JDialog {

    /** null khi được nhúng (embedded mode) trong TableOrderFrame – Phase 3B. */
    private final ReportPanel parent;

    /**
     * {@code true} khi {@code parent == null}: dialog không bao giờ được show
     * mà chỉ lấy content pane nhúng vào tab. Trong mode này "Hủy" reset form
     * và "Gửi" không gọi dispose/loadData.
     */
    private final boolean embedded;

    private RoundedTextField  tfTitle;
    private JComboBox<String> cmbType;
    private JComboBox<String> cmbSeverity;
    private JTextArea         taDescription;
    private JLabel            lblError;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /** Constructor gốc – dùng khi mở dialog độc lập từ ReportPanel. */
    public ReportAddDialog(Window owner, ReportPanel parent) {
        super(owner, "Gửi báo cáo mới", ModalityType.APPLICATION_MODAL);
        this.parent   = parent;
        this.embedded = (parent == null);   // Phase 3B: nhúng vào tab khi parent null
        setSize(420, 400);
        setResizable(false);
        setLocationRelativeTo(owner);
        buildUI();
    }

    // ─── UI ───────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(UIConstants.BG_WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));

        // ── Title ─────────────────────────────────────────────────────────────
        JLabel dlgTitle = new JLabel("Gửi báo cáo sự cố");
        dlgTitle.setFont(UIConstants.FONT_TITLE);
        dlgTitle.setForeground(UIConstants.TEXT_PRIMARY);
        dlgTitle.setAlignmentX(LEFT_ALIGNMENT);
        root.add(dlgTitle);
        root.add(Box.createVerticalStrut(16));

        // ── Tiêu đề ───────────────────────────────────────────────────────────
        root.add(fieldLabel("Tiêu đề *"));
        root.add(Box.createVerticalStrut(4));
        tfTitle = new RoundedTextField("Nhập tiêu đề...");
        tfTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        tfTitle.setAlignmentX(LEFT_ALIGNMENT);
        root.add(tfTitle);

        // ── Error label ───────────────────────────────────────────────────────
        lblError = new JLabel(" ");
        lblError.setFont(UIConstants.FONT_SMALL);
        lblError.setForeground(UIConstants.DANGER);
        lblError.setAlignmentX(LEFT_ALIGNMENT);
        root.add(lblError);
        root.add(Box.createVerticalStrut(8));

        // ── Loại ─────────────────────────────────────────────────────────────
        root.add(fieldLabel("Loại báo cáo"));
        root.add(Box.createVerticalStrut(4));
        cmbType = new JComboBox<>(new String[]{"INCIDENT", "MAINTENANCE", "FEEDBACK"});
        cmbType.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if      ("INCIDENT".equals(value))    setText("Sự cố");
                else if ("MAINTENANCE".equals(value)) setText("Bảo trì");
                else if ("FEEDBACK".equals(value))    setText("Phản hồi");
                return this;
            }
        });
        cmbType.setFont(UIConstants.FONT_BODY);
        cmbType.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        cmbType.setAlignmentX(LEFT_ALIGNMENT);
        root.add(cmbType);
        root.add(Box.createVerticalStrut(10));

        // ── Mức độ ───────────────────────────────────────────────────────────
        root.add(fieldLabel("Mức độ"));
        root.add(Box.createVerticalStrut(4));
        cmbSeverity = new JComboBox<>(new String[]{"LOW", "MEDIUM", "HIGH", "CRITICAL"});
        cmbSeverity.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if      ("LOW".equals(value))      setText("Thấp");
                else if ("MEDIUM".equals(value))   setText("Trung bình");
                else if ("HIGH".equals(value))     setText("Cao");
                else if ("CRITICAL".equals(value)) setText("Nghiêm trọng");
                return this;
            }
        });
        cmbSeverity.setFont(UIConstants.FONT_BODY);
        cmbSeverity.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        cmbSeverity.setAlignmentX(LEFT_ALIGNMENT);
        root.add(cmbSeverity);
        root.add(Box.createVerticalStrut(10));

        // ── Mô tả ─────────────────────────────────────────────────────────────
        root.add(fieldLabel("Mô tả (không bắt buộc)"));
        root.add(Box.createVerticalStrut(4));
        taDescription = new JTextArea(4, 20);
        taDescription.setFont(UIConstants.FONT_BODY);
        taDescription.setLineWrap(true);
        taDescription.setWrapStyleWord(true);
        taDescription.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        JScrollPane scrollDesc = new JScrollPane(taDescription);
        scrollDesc.setAlignmentX(LEFT_ALIGNMENT);
        scrollDesc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        root.add(scrollDesc);
        root.add(Box.createVerticalStrut(16));

        // ── Buttons ───────────────────────────────────────────────────────────
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setOpaque(false);
        btnPanel.setAlignmentX(LEFT_ALIGNMENT);
        btnPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JButton btnCancel = new JButton(embedded ? "Xóa form" : "Hủy");
        btnCancel.setFont(UIConstants.FONT_BODY);
        btnCancel.setForeground(UIConstants.TEXT_SECONDARY);
        btnCancel.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true));
        btnCancel.setFocusPainted(false);
        btnCancel.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnCancel.addActionListener(e -> {
            if (embedded) {
                // Embedded mode: reset form thay vì đóng dialog
                resetForm();
            } else {
                dispose();
            }
        });

        RoundedButton btnSubmit = new RoundedButton("Gửi");
        btnSubmit.setPreferredSize(new Dimension(80, UIConstants.BTN_HEIGHT));
        btnSubmit.addActionListener(e -> doSubmit());

        btnPanel.add(btnCancel);
        btnPanel.add(btnSubmit);
        root.add(btnPanel);

        setContentPane(root);
    }

    // ─── Submit ───────────────────────────────────────────────────────────────

    private void doSubmit() {
        String titleText = tfTitle.getText().trim();
        if (titleText.isEmpty()) {
            lblError.setText("⚠ Tiêu đề không được để trống!");
            tfTitle.requestFocus();
            shakeField(tfTitle);
            return;
        }
        lblError.setText(" ");

        Report r = new Report();
        r.setTitle(titleText);
        r.setDescription(taDescription.getText().trim());
        r.setReportType(Report.ReportType.valueOf(cmbType.getSelectedItem().toString()));
        r.setSeverity(Report.Severity.valueOf(cmbSeverity.getSelectedItem().toString()));

        try {
            new ReportDAO().add(r);

            if (embedded) {
                // ── Embedded mode (Phase 3B): không dispose, reset form ────────
                // Dùng getRootPane() làm parent của JOptionPane vì dialog chưa show
                JOptionPane.showMessageDialog(
                    getRootPane(),
                    "✅  Đã gửi báo cáo thành công!",
                    "Thành công",
                    JOptionPane.INFORMATION_MESSAGE
                );
                resetForm();
            } else {
                // ── Standalone mode: đóng dialog và reload danh sách ─────────
                JOptionPane.showMessageDialog(this, "Đã gửi báo cáo thành công!");
                dispose();
                if (parent != null) parent.loadData(); // null-safe guard
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                embedded ? getRootPane() : this,
                "Lỗi: " + ex.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /** Reset toàn bộ trường nhập về giá trị mặc định (dùng trong embedded mode). */
    private void resetForm() {
        tfTitle.setText("");
        taDescription.setText("");
        cmbType.setSelectedIndex(0);
        cmbSeverity.setSelectedIndex(0);
        lblError.setText(" ");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Hiệu ứng rung nhẹ để báo lỗi validation. */
    private void shakeField(JComponent comp) {
        Point origin = comp.getLocation();
        Timer timer  = new Timer(30, null);
        int[] step   = {0};
        int[] dx     = {-6, 6, -5, 5, -3, 3, -1, 1, 0};
        timer.addActionListener(e -> {
            if (step[0] < dx.length) {
                comp.setLocation(origin.x + dx[step[0]], origin.y);
                step[0]++;
            } else {
                comp.setLocation(origin);
                timer.stop();
            }
        });
        timer.start();
    }

    private JLabel fieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UIConstants.FONT_BOLD);
        lbl.setForeground(UIConstants.TEXT_PRIMARY);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        return lbl;
    }
}