package com.restaurant.ui.dialog;

import com.restaurant.data.DataManager;
import com.restaurant.model.MenuItem;
import com.restaurant.ui.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.*;

public class MenuStatDialog extends JDialog {
    public MenuStatDialog(Window owner) {
        super(owner, "Thống kê Menu", ModalityType.APPLICATION_MODAL);
        setSize(560, 400);
        setLocationRelativeTo(owner);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 0, 24));

        JLabel title = new JLabel("Thống kê Menu");
        title.setFont(UIConstants.FONT_TITLE);
        root.add(title, BorderLayout.NORTH);

        // Stats
        DataManager dm = DataManager.getInstance();
        java.util.List<MenuItem> items = dm.getMenuItems();
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (MenuItem m : items) {
            byCategory.merge(m.getCategory(), 1L, Long::sum);
        }
        double avgPrice = items.stream().mapToDouble(MenuItem::getPrice).average().orElse(0);
        double maxPrice = items.stream().mapToDouble(MenuItem::getPrice).max().orElse(0);
        double minPrice = items.stream().mapToDouble(MenuItem::getPrice).min().orElse(0);

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi","VN"));
        String[] cols = {"Loại", "Số lượng"};
        Object[][] data = new Object[byCategory.size() + 3][2];
        int i = 0;
        for (Map.Entry<String, Long> en : byCategory.entrySet()) {
            data[i][0] = en.getKey(); data[i][1] = en.getValue(); i++;
        }
        data[i][0] = "— Tổng món"; data[i][1] = items.size(); i++;
        data[i][0] = "— Giá trung bình"; data[i][1] = nf.format((long)avgPrice) + " đ"; i++;
        data[i][0] = "— Giá cao nhất / thấp nhất";
        data[i][1] = nf.format((long)maxPrice) + " / " + nf.format((long)minPrice) + " đ";

        DefaultTableModel model = new DefaultTableModel(data, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        StyledTable table = new StyledTable(model);
        root.add(StyledTable.wrap(table), BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnBar.setBackground(Color.WHITE);
        RoundedButton btn = RoundedButton.outline("Đóng");
        btn.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btn.addActionListener(e -> dispose());
        btnBar.add(btn);
        root.add(btnBar, BorderLayout.SOUTH);
        setContentPane(root);
    }
}
