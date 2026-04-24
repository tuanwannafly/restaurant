package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.restaurant.db.DBConnection;
import com.restaurant.model.Order;

/**
 * DAO phục vụ màn hình bếp (KitchenPanel).
 * Query order_items theo status PENDING / ACCEPTED / COOKING,
 * join orders, restaurant_tables, menu_items.
 */
public class KitchenDAO {

    // ─── Inner data class ─────────────────────────────────────────────────────

    public static class KitchenTicket {
        public final String itemId;
        public final String orderId;
        public final String menuItemId;
        public final String tableId;
        public final String tableName;
        public final int    roundNumber;
        public final String itemName;
        public final int    quantity;
        public final Order.OrderItem.ItemStatus itemStatus;

        public KitchenTicket(String itemId, String orderId, String menuItemId,
                             String tableId, String tableName, int roundNumber,
                             String itemName, int quantity,
                             Order.OrderItem.ItemStatus itemStatus) {
            this.itemId      = itemId;
            this.orderId     = orderId;
            this.menuItemId  = menuItemId;
            this.tableId     = tableId;
            this.tableName   = tableName;
            this.roundNumber = roundNumber;
            this.itemName    = itemName;
            this.quantity    = quantity;
            this.itemStatus  = itemStatus;
        }
    }

    // ─── SQL ──────────────────────────────────────────────────────────────────

    private static final String SQL_ACTIVE_TICKETS =
            "SELECT oi.order_item_id, oi.order_id, oi.menu_item_id, " +
            "       oi.quantity,      oi.item_status, oi.round_number, " +
            "       mi.item_name,     t.table_number, t.table_id " +
            "FROM   order_items oi " +
            "JOIN   orders           o  ON oi.order_id    = o.order_id " +
            "JOIN   restaurant_tables t  ON o.table_id     = t.table_id " +
            "JOIN   menu_items       mi  ON oi.menu_item_id = mi.item_id " +
            "WHERE  o.restaurant_id = ? " +
            "  AND  oi.item_status IN ('PENDING','ACCEPTED','COOKING') " +
            "ORDER  BY t.table_number, oi.round_number, oi.created_at";

    private static final String SQL_UPDATE_STATUS =
            "UPDATE order_items SET item_status = ? WHERE order_item_id = ?";

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Lấy danh sách tất cả ticket đang active (PENDING / ACCEPTED / COOKING)
     * thuộc nhà hàng {@code restaurantId}.
     */
    public List<KitchenTicket> getActiveTickets(long restaurantId) {
        List<KitchenTicket> list = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ACTIVE_TICKETS)) {

            ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String rawStatus = rs.getString("item_status");
                    Order.OrderItem.ItemStatus status = parseStatus(rawStatus);

                    KitchenTicket ticket = new KitchenTicket(
                            rs.getString("order_item_id"),
                            rs.getString("order_id"),
                            rs.getString("menu_item_id"),
                            rs.getString("table_id"),
                            rs.getString("table_number"),
                            rs.getInt("round_number"),
                            rs.getString("item_name"),
                            rs.getInt("quantity"),
                            status
                    );
                    list.add(ticket);
                }
            }
        } catch (SQLException e) {
            System.err.println("[KitchenDAO] getActiveTickets error: " + e.getMessage());
        }
        return list;
    }

    /**
     * Cập nhật item_status của một order_item_id.
     *
     * @return true nếu update thành công (affectedRows >= 1)
     */
    public boolean updateItemStatus(String itemId, Order.OrderItem.ItemStatus newStatus) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, newStatus.name());
            ps.setString(2, itemId);
            return ps.executeUpdate() >= 1;

        } catch (SQLException e) {
            System.err.println("[KitchenDAO] updateItemStatus error: " + e.getMessage());
            return false;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Order.OrderItem.ItemStatus parseStatus(String raw) {
        if (raw == null) return Order.OrderItem.ItemStatus.PENDING;
        try {
            return Order.OrderItem.ItemStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Order.OrderItem.ItemStatus.PENDING;
        }
    }
}