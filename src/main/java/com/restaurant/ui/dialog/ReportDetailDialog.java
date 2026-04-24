package com.restaurant.ui.dialog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;

import com.restaurant.dao.ReportDAO;
import com.restaurant.model.Report;
import com.restaurant.session.RbacGuard;
import com.restaurant.ui.ReportPanel;
import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.UIConstants;

public class ReportDetailDialog extends JDialog {

    private final Report      report;
    private final ReportPanel parent;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public ReportDetailDialog(Window owner, Report report, ReportPanel parent) {
        super(owner, "Chi tiết báo cáo", ModalityType.APPLICATION_MODAL);
        this.report = report;
        this.parent = parent;
        setSize(480, 500);
        setResizable(false);
        setLocationRelativeTo(owner);
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(UIConstants.BG_WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));

        boolean isManager = RbacGuard.getInstance().isManagerOrAbove();

        // ── Row 1: Tiêu đề ────────────────────────────────────────────────────
        root.add(infoRow("Tiêu đề:", report.getTitle(), true));
        root.add(Box.createVerticalStrut(8));

        // ── Row 2: Loại | Mức độ ─────────────────────────────────────────────
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        row2.setOpaque(false);
        row2.setAlignmentX(LEFT_ALIGNMENT);
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row2.add(labelPair("Loại:", report.getReportTypeDisplay(), false));
        row2.add(severityLabel(report));
        root.add(row2);
        root.add(Box.createVerticalStrut(8));

        // ── Row 3: Trạng thái ─────────────────────────────────────────────────
        root.add(infoRow("Trạng thái:", report.getStatusDisplay(), false));
        root.add(Box.createVerticalStrut(8));

        // ── Row 4: Người gửi (chỉ manager) ──────────────────────────────────
        if (isManager) {
            root.add(infoRow("Người gửi:", "User #" + report.getCreatedBy(), false));
            root.add(Box.createVerticalStrut(8));
        }

        // ── Row 5: Ngày tạo ───────────────────────────────────────────────────
        String createdAtStr = report.getCreatedAt() != null ? report.getCreatedAt().format(FMT) : "—";
        root.add(infoRow("Ngày tạo:", createdAtStr, false));
        root.add(Box.createVerticalStrut(8));

        // ── Row 6: Ngày giải quyết (nếu RESOLVED / CLOSED) ─────────────────
        if (report.getStatus() == Report.Status.RESOLVED || report.getStatus() == Report.Status.CLOSED) {
            String resolvedStr = report.getResolvedAt() != null ? report.getResolvedAt().format(FMT) : "—";
            root.add(infoRow("Ngày giải quyết:", resolvedStr, false));
            root.add(Box.createVerticalStrut(8));
        }

        // ── Row 7: Separator ──────────────────────────────────────────────────
        JSeparator sep1 = new JSeparator();
        sep1.setForeground(UIConstants.BORDER_COLOR);
        sep1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep1.setAlignmentX(LEFT_ALIGNMENT);
        root.add(sep1);
        root.add(Box.createVerticalStrut(10));

        // ── Row 8: Mô tả label ────────────────────────────────────────────────
        JLabel descLabel = new JLabel("Mô tả:");
        descLabel.setFont(UIConstants.FONT_BOLD);
        descLabel.setForeground(UIConstants.TEXT_PRIMARY);
        descLabel.setAlignmentX(LEFT_ALIGNMENT);
        root.add(descLabel);
        root.add(Box.createVerticalStrut(4));

        // ── Row 9: Mô tả content ─────────────────────────────────────────────
        JTextArea taDesc = new JTextArea(report.getDescription() != null ? report.getDescription() : "");
        taDesc.setFont(UIConstants.FONT_BODY);
        taDesc.setForeground(UIConstants.TEXT_PRIMARY);
        taDesc.setBackground(UIConstants.BG_PAGE);
        taDesc.setLineWrap(true);
        taDesc.setWrapStyleWord(true);
        taDesc.setEditable(false);
        taDesc.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        JScrollPane scrollDesc = new JScrollPane(taDesc);
        scrollDesc.setAlignmentX(LEFT_ALIGNMENT);
        scrollDesc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        scrollDesc.setBorder(BorderFactory.createEmptyBorder());
        root.add(scrollDesc);

        // ── Manager section: Cập nhật trạng thái ─────────────────────────────
        if (isManager) {
            root.add(Box.createVerticalStrut(12));

            // Row 10: Separator
            JSeparator sep2 = new JSeparator();
            sep2.setForeground(UIConstants.BORDER_COLOR);
            sep2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            sep2.setAlignmentX(LEFT_ALIGNMENT);
            root.add(sep2);
            root.add(Box.createVerticalStrut(10));

            // Row 11: Label
            JLabel lblUpdate = new JLabel("Cập nhật trạng thái:");
            lblUpdate.setFont(UIConstants.FONT_BOLD);
            lblUpdate.setForeground(UIConstants.TEXT_PRIMARY);
            lblUpdate.setAlignmentX(LEFT_ALIGNMENT);
            root.add(lblUpdate);
            root.add(Box.createVerticalStrut(6));

            // Row 12: ComboBox
            JComboBox<Report.Status> cmbNewStatus = new JComboBox<>(Report.Status.values());
            cmbNewStatus.setSelectedItem(report.getStatus());
            cmbNewStatus.setFont(UIConstants.FONT_BODY);
            cmbNewStatus.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value,
                        int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof Report.Status s) {
                        setText(switch (s) {
                            case OPEN        -> "Mở";
                            case IN_PROGRESS -> "Đang xử lý";
                            case RESOLVED    -> "Đã giải quyết";
                            case CLOSED      -> "Đã đóng";
                        });
                    }
                    return this;
                }
            });
            cmbNewStatus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            cmbNewStatus.setAlignmentX(LEFT_ALIGNMENT);
            root.add(cmbNewStatus);
            root.add(Box.createVerticalStrut(10));

            // Row 13: Nút Cập nhật
            RoundedButton btnUpdate = new RoundedButton("Cập nhật");
            btnUpdate.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
            btnUpdate.setAlignmentX(LEFT_ALIGNMENT);
            btnUpdate.addActionListener(e -> {
                Report.Status chosen = (Report.Status) cmbNewStatus.getSelectedItem();
                try {
                    new ReportDAO().updateStatus(report.getReportId(), chosen);
                    JOptionPane.showMessageDialog(this, "Đã cập nhật!");
                    dispose();
                    parent.loadData();
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });
            root.add(btnUpdate);
        }

        JScrollPane scrollRoot = new JScrollPane(root);
        scrollRoot.setBorder(BorderFactory.createEmptyBorder());
        scrollRoot.getVerticalScrollBar().setUnitIncrement(12);
        setContentPane(scrollRoot);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JPanel infoRow(String labelText, String value, boolean valueBold) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_SECONDARY);

        JLabel val = new JLabel(value != null ? value : "—");
        val.setFont(valueBold ? UIConstants.FONT_BOLD : UIConstants.FONT_BODY);
        val.setForeground(UIConstants.TEXT_PRIMARY);

        row.add(lbl);
        row.add(val);
        return row;
    }

    private JPanel labelPair(String labelText, String value, boolean bold) {
        JPanel pair = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pair.setOpaque(false);

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_SECONDARY);

        JLabel val = new JLabel(value != null ? value : "—");
        val.setFont(bold ? UIConstants.FONT_BOLD : UIConstants.FONT_BODY);
        val.setForeground(UIConstants.TEXT_PRIMARY);

        pair.add(lbl);
        pair.add(val);
        return pair;
    }

    private JPanel severityLabel(Report r) {
        JPanel pair = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pair.setOpaque(false);

        JLabel lbl = new JLabel("Mức độ:");
        lbl.setFont(UIConstants.FONT_BODY);
        lbl.setForeground(UIConstants.TEXT_SECONDARY);

        Color fg = UIConstants.TEXT_PRIMARY;
        if (r.getSeverity() != null) {
            fg = switch (r.getSeverity()) {
                case CRITICAL -> Color.decode("#E24B4A");
                case HIGH     -> Color.decode("#FB923C");
                case MEDIUM   -> Color.decode("#D97706");
                case LOW      -> Color.decode("#16A34A");
            };
        }

        JLabel val = new JLabel(r.getSeverityDisplay());
        val.setFont(UIConstants.FONT_BOLD);
        val.setForeground(fg);

        pair.add(lbl);
        pair.add(val);
        return pair;
    }
}