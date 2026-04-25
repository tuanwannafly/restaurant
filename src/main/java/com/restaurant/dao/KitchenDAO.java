package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.restaurant.db.DBConnection;
import com.restaurant.model.Order;
import com.restaurant.model.TableItem;

/**
 * DAO phục vụ màn hình bếp (KitchenPanel) và phục vụ bàn (WaiterServicePanel).
 * <p>
 * Phase 5: bổ sung {@link #getReadyByTable(long)} và {@link #getDirtyTables(long)}
 * để WaiterServicePanel lấy dữ liệu mà không cần DAO mới.
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
            "       mi.name AS item_name,     t.table_number, t.table_id " +
            "FROM   order_items oi " +
            "JOIN   orders           o  ON oi.order_id    = o.order_id " +
            "JOIN   restaurant_tables t  ON o.table_id     = t.table_id " +
            "JOIN   menu_items       mi  ON oi.menu_item_id = mi.item_id " +
            "WHERE  o.restaurant_id = ? " +
            "  AND  oi.item_status IN ('PENDING','ACCEPTED','COOKING') " +
            "ORDER  BY t.table_number, oi.round_number, oi.created_at";

    /**
     * Chỉ lấy các bàn/lượt mà TẤT CẢ items đều READY.
     * HAVING đảm bảo: số item trong lượt = số item có status READY.
     */
    private static final String SQL_READY_BY_TABLE =
            "SELECT oi.order_item_id, oi.order_id, oi.menu_item_id, " +
            "       oi.quantity,      oi.item_status, oi.round_number, " +
            "       mi.name AS item_name,     t.table_number, t.table_id " +
            "FROM   order_items oi " +
            "JOIN   orders           o  ON oi.order_id     = o.order_id " +
            "JOIN   restaurant_tables t  ON o.table_id      = t.table_id " +
            "JOIN   menu_items       mi  ON oi.menu_item_id = mi.item_id " +
            "WHERE  o.restaurant_id = ? " +
            "  AND  oi.item_status  = 'READY' " +
            "  AND  (o.table_id, oi.round_number) IN ( " +
            "           SELECT oi2.order_id, oi2.round_number " +  // reuse sub-query key
            "           FROM   order_items oi2 " +
            "           JOIN   orders o2 ON oi2.order_id = o2.order_id " +
            "           WHERE  o2.restaurant_id = ? " +
            "             AND  oi2.item_status IN ('PENDING','ACCEPTED','COOKING','READY') " +
            "           GROUP  BY o2.table_id, oi2.round_number " +
            "           HAVING COUNT(*) = COUNT(CASE WHEN oi2.item_status = 'READY' THEN 1 END) " +
            "       ) " +
            "ORDER  BY t.table_number, oi.round_number, oi.created_at";

    /**
     * Lấy tất cả bàn đang DIRTY hoặc CLEANING của nhà hàng.
     */
    private static final String SQL_DIRTY_TABLES =
            "SELECT table_id, table_number, capacity, status " +
            "FROM   restaurant_tables " +
            "WHERE  restaurant_id = ? " +
            "  AND  status IN ('DIRTY', 'CLEANING') " +
            "ORDER  BY table_number";

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
                    list.add(mapTicket(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[KitchenDAO] getActiveTickets error: " + e.getMessage());
        }
        return list;
    }

    /**
     * Phase 5 – WaiterServicePanel: lấy các lượt bàn mà <em>tất cả</em> items đều READY.
     * <p>
     * Key của Map: {@code "<tableId>_<roundNumber>"} – dùng để group card.<br>
     * Value: danh sách {@link KitchenTicket} thuộc lượt đó.
     *
     * @param restaurantId nhà hàng hiện tại
     * @return map (bảo toàn thứ tự nhập), rỗng nếu không có gì
     */
    public Map<String, List<KitchenTicket>> getReadyByTable(long restaurantId) {
        Map<String, List<KitchenTicket>> result = new LinkedHashMap<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_READY_BY_TABLE)) {

            ps.setLong(1, restaurantId);
            ps.setLong(2, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    KitchenTicket ticket = mapTicket(rs);
                    String key = ticket.tableId + "_" + ticket.roundNumber;
                    result.computeIfAbsent(key, k -> new ArrayList<>()).add(ticket);
                }
            }
        } catch (SQLException e) {
            System.err.println("[KitchenDAO] getReadyByTable error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Phase 5 – WaiterServicePanel: lấy danh sách bàn cần dọn (DIRTY / CLEANING).
     *
     * @param restaurantId nhà hàng hiện tại
     * @return danh sách {@link TableItem}, rỗng nếu không có
     */
    public List<TableItem> getDirtyTables(long restaurantId) {
        List<TableItem> list = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DIRTY_TABLES)) {

            ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String  id       = rs.getString("table_id");
                    String  name     = rs.getString("table_number");
                    int     capacity = rs.getInt("capacity");
                    String  rawSt    = rs.getString("status");
                    TableItem.Status status =
                            "CLEANING".equalsIgnoreCase(rawSt)
                            ? TableItem.Status.CLEANING
                            : TableItem.Status.DIRTY;
                    list.add(new TableItem(id, name, capacity, status));
                }
            }
        } catch (SQLException e) {
            System.err.println("[KitchenDAO] getDirtyTables error: " + e.getMessage());
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

    private KitchenTicket mapTicket(ResultSet rs) throws SQLException {
        String rawStatus = rs.getString("item_status");
        Order.OrderItem.ItemStatus status = parseStatus(rawStatus);
        return new KitchenTicket(
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
    }

    private Order.OrderItem.ItemStatus parseStatus(String raw) {
        if (raw == null) return Order.OrderItem.ItemStatus.PENDING;
        try {
            return Order.OrderItem.ItemStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Order.OrderItem.ItemStatus.PENDING;
        }
    }
}