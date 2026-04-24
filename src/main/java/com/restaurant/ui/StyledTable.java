package com.restaurant.ui;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;

public class StyledTable extends JTable {

    public StyledTable(TableModel model) {
        super(model);
        setFont(UIConstants.FONT_BODY);
        setRowHeight(UIConstants.ROW_HEIGHT);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 1));
        setFillsViewportHeight(true);
        setSelectionBackground(UIConstants.ROW_SELECTED);
        setSelectionForeground(UIConstants.TEXT_PRIMARY);
        setBackground(Color.WHITE);
        getTableHeader().setFont(UIConstants.FONT_BOLD);
        getTableHeader().setBackground(UIConstants.HEADER_BG);
        getTableHeader().setForeground(UIConstants.TEXT_PRIMARY);
        getTableHeader().setPreferredSize(new Dimension(0, 36));
        getTableHeader().setBorder(BorderFactory.createEmptyBorder());
        ((DefaultTableCellRenderer) getTableHeader().getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);
        setDefaultRenderer(Object.class, new StripedRenderer());
    }

    private static class StripedRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xF9FAFB));
            }
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            return c;
        }
    }

    public static JScrollPane wrap(StyledTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1));
        sp.getViewport().setBackground(Color.WHITE);
        return sp;
    }
}
