package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import com.restaurant.db.DBConnection;
import com.restaurant.model.Order;
import com.restaurant.websocket.RestaurantEventClient;
import com.restaurant.websocket.RestaurantEventServer;
import com.restaurant.websocket.WsEvent;
import com.restaurant.websocket.WsTopic;

/**
 * DAO cho tablet khách — không phụ thuộc AppSession hay RbacGuard.
 * Nhận restaurantId trực tiếp từ {@link com.restaurant.session.TabletSession}.
 */
public class TabletOrderDAO {

    private final long restaurantId;

    public TabletOrderDAO(long restaurantId) {
        this.restaurantId = restaurantId;
    }

    // ─── Table name ───────────────────────────────────────────────────────────

    public String getTableName(String tableId) {
        String sql = """
            SELECT table_number FROM restaurant_tables
            WHERE table_id = ? AND restaurant_id = ?
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, Long.parseLong(tableId));
            ps.setLong(2, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("table_number");
            }
        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] getTableName lỗi: " + e.getMessage());
        }
        return "Bàn " + tableId;
    }

    // ─── Get or create active order ───────────────────────────────────────────

    public Order getOrCreateActiveOrder(String tableId) {
        String sqlFind = """
            SELECT o.order_id, o.status, o.total_amount, o.created_at,
                   o.customer_name, o.customer_phone,
                   t.table_number, t.table_id
            FROM orders o
            JOIN restaurant_tables t ON o.table_id = t.table_id
            WHERE o.table_id = ?
              AND o.restaurant_id = ?
              AND o.status NOT IN ('COMPLETED', 'CANCELLED')
            ORDER BY o.created_at DESC
            FETCH FIRST 1 ROWS ONLY
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlFind)) {
            ps.setLong(1, Long.parseLong(tableId));
            ps.setLong(2, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Order o = mapOrder(rs);
                    System.out.println("[TabletOrderDAO] Found active order: " + o.getId());
                    markTableOccupied(tableId);
                    return o;
                }
            }
        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] getOrCreateActiveOrder lỗi: " + e.getMessage());
            return null;
        }

        System.out.println("[TabletOrderDAO] No active order, creating new for table " + tableId);
        Order newOrder = createPendingOrder(tableId);
        if (newOrder != null) {
            markTableOccupied(tableId);
            com.restaurant.session.AuditLogger.getInstance()
                .logOpenTable(tableId, getTableName(tableId), Long.parseLong(newOrder.getId()));
        }
        return newOrder;
    }

    // ─── CANCEL ORDER (từ tablet khách) ──────────────────────────────────────

    /**
     * Khách huỷ đơn từ tablet.
     *
     * <p>Chỉ cho phép khi đơn đang ở trạng thái {@code PENDING} hoặc
     * {@code ACCEPTED} — tức là chưa được bếp bắt đầu chế biến.
     *
     * <p>Khi huỷ thành công:
     * <ol>
     *   <li>{@code orders.status} → {@code CANCELLED}</li>
     *   <li>Toàn bộ {@code order_items.item_status} → {@code CANCELLED}
     *       (trừ những món đã DELIVERED) — bếp sẽ không còn thấy các món này.</li>
     *   <li>Broadcast {@link WsTopic#KITCHEN} và {@link WsTopic#ORDERS} để
     *       màn hình bếp và quản lý tự refresh ngay lập tức.</li>
     * </ol>
     *
     * <p>Không đụng vào trạng thái bàn ({@code restaurant_tables}).
     *
     * @param orderId ID đơn cần huỷ
     * @return {@code true} nếu huỷ thành công;
     *         {@code false} nếu đơn đã vào bếp hoặc không tìm thấy
     */
    public boolean cancelOrder(String orderId) {
        long oid = parseLongOrDefault(orderId, 0);
        if (oid == 0) {
            System.err.println("[TabletOrderDAO] cancelOrder: orderId không hợp lệ=" + orderId);
            return false;
        }

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {

                // 1. Lấy trạng thái hiện tại
                String currentStatus;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT status FROM orders WHERE order_id=? AND restaurant_id=?")) {
                    ps.setLong(1, oid);
                    ps.setLong(2, restaurantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            System.err.println("[TabletOrderDAO] cancelOrder: không tìm thấy order=" + orderId);
                            conn.rollback();
                            return false;
                        }
                        currentStatus = rs.getString("status");
                    }
                }

                // 2. Chỉ cho huỷ khi PENDING hoặc ACCEPTED (chưa vào bếp nấu)
                if (!"PENDING".equals(currentStatus) && !"ACCEPTED".equals(currentStatus)) {
                    System.err.println("[TabletOrderDAO] cancelOrder từ chối: "
                            + "đơn đang " + currentStatus + " — chỉ huỷ được khi PENDING hoặc ACCEPTED");
                    conn.rollback();
                    return false;
                }

                // 3. Cập nhật orders → CANCELLED
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE orders SET status='CANCELLED' WHERE order_id=? AND restaurant_id=?")) {
                    ps.setLong(1, oid);
                    ps.setLong(2, restaurantId);
                    if (ps.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                // 4. Huỷ toàn bộ order_items chưa hoàn thành
                //    → bếp lọc theo item_status nên sẽ biến mất ngay sau khi refresh
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE order_items SET item_status='CANCELLED' " +
                        "WHERE order_id=? AND item_status NOT IN ('DELIVERED','CANCELLED')")) {
                    ps.setLong(1, oid);
                    int n = ps.executeUpdate();
                    System.out.println("[TabletOrderDAO] cancelOrder: đã huỷ " + n
                            + " order_item(s) của đơn " + orderId);
                }

                conn.commit();

                // 5. Broadcast để bếp và màn hình quản lý tự refresh
                broadcastCancelEvent();

                System.out.println("[TabletOrderDAO] cancelOrder: huỷ thành công đơn=" + orderId);
                return true;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] cancelOrder lỗi: " + e.getMessage());
            return false;
        }
    }

    /**
     * Khách hủy một <b>món cụ thể</b> từ tablet.
     *
     * <p>Chỉ cho phép khi {@code item_status} còn {@code PENDING} hoặc
     * {@code ACCEPTED} — tức là bếp chưa bắt đầu nấu món đó.
     *
     * <p>Sau khi hủy thành công, broadcast {@link WsTopic#KITCHEN} và
     * {@link WsTopic#ORDERS} để màn hình bếp và quản lý tự refresh.
     *
     * @param orderItemId PK của {@code order_items} (từ {@code Order.OrderItem.getOrderItemId()})
     * @return {@code true} nếu hủy thành công;
     *         {@code false} nếu món đã vào bếp hoặc không tìm thấy
     */
    public boolean cancelOrderItem(String orderItemId) {
        long oid = parseLongOrDefault(orderItemId, 0);
        if (oid == 0) {
            System.err.println("[TabletOrderDAO] cancelOrderItem: orderItemId không hợp lệ=" + orderItemId);
            return false;
        }

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            // Chỉ UPDATE khi item_status còn PENDING hoặc ACCEPTED VÀ thuộc nhà hàng này
            // (join orders để kiểm tra restaurant_id → tránh hủy nhầm đơn nhà hàng khác)
            String sql = """
                UPDATE order_items oi
                SET oi.item_status = 'CANCELLED'
                WHERE oi.order_item_id = ?
                  AND oi.item_status IN ('PENDING', 'ACCEPTED')
                  AND EXISTS (
                      SELECT 1 FROM orders o
                      WHERE o.order_id = oi.order_id
                        AND o.restaurant_id = ?
                  )
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, oid);
                ps.setLong(2, restaurantId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    System.err.println("[TabletOrderDAO] cancelOrderItem từ chối: "
                            + "món đã vào bếp hoặc không tìm thấy, orderItemId=" + orderItemId);
                    return false;
                }
            }

            // Broadcast để bếp và màn hình quản lý tự refresh
            broadcastCancelEvent();
            System.out.println("[TabletOrderDAO] cancelOrderItem: hủy thành công món orderItemId=" + orderItemId);
            return true;

        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] cancelOrderItem lỗi: " + e.getMessage());
            return false;
        }
    }

    /**
     * Broadcast KITCHEN + ORDERS topic để màn hình bếp và quản lý đơn tự refresh.
     */
    private void broadcastCancelEvent() {
        try {
            RestaurantEventServer srv = RestaurantEventServer.getInstance();
            if (srv.isRunning()) {
                srv.broadcast(WsEvent.of(WsTopic.KITCHEN, restaurantId));
                srv.broadcast(WsEvent.of(WsTopic.ORDERS,  restaurantId));
            } else {
                RestaurantEventClient client = RestaurantEventClient.getInstance();
                client.publishToServer(WsEvent.of(WsTopic.KITCHEN, restaurantId));
                client.publishToServer(WsEvent.of(WsTopic.ORDERS,  restaurantId));
            }
        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] broadcastCancelEvent lỗi: " + e.getMessage());
        }
    }

    // ─── Table status helpers ─────────────────────────────────────────────────

    public void markTableDirty(String tableId) {
        String sql = """
            UPDATE restaurant_tables
               SET status = 'OUT_OF_SERVICE'
             WHERE table_id = ? AND restaurant_id = ? AND status = 'OCCUPIED'
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, Long.parseLong(tableId));
            ps.setLong(2, restaurantId);
            if (ps.executeUpdate() > 0) {
                System.out.println("[TabletOrderDAO] Bàn " + tableId + " → OUT_OF_SERVICE");
                broadcastTableChange();
            }
        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] markTableDirty lỗi: " + e.getMessage());
        }
    }

    public void markTableAvailable(String tableId) {
        String sql = """
            UPDATE restaurant_tables
               SET status = 'AVAILABLE'
             WHERE table_id = ? AND restaurant_id = ? AND status = 'OCCUPIED'
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, Long.parseLong(tableId));
            ps.setLong(2, restaurantId);
            if (ps.executeUpdate() > 0) {
                System.out.println("[TabletOrderDAO] Bàn " + tableId + " → AVAILABLE");
                broadcastTableChange();
            }
        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] markTableAvailable lỗi: " + e.getMessage());
        }
    }

    private void markTableOccupied(String tableId) {
        String sql = """
            UPDATE restaurant_tables
               SET status = 'OCCUPIED'
             WHERE table_id = ? AND restaurant_id = ? AND status IN ('AVAILABLE','RESERVED')
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, Long.parseLong(tableId));
            ps.setLong(2, restaurantId);
            if (ps.executeUpdate() > 0) {
                System.out.println("[TabletOrderDAO] Bàn " + tableId + " → OCCUPIED");
                broadcastTableChange();
            }
        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] markTableOccupied lỗi: " + e.getMessage());
        }
    }

    private void broadcastTableChange() {
        try {
            WsEvent evt = WsEvent.of(WsTopic.TABLES, restaurantId);
            RestaurantEventServer srv = RestaurantEventServer.getInstance();
            if (srv.isRunning()) srv.broadcast(evt);
            else RestaurantEventClient.getInstance().publishToServer(evt);
        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] broadcastTableChange lỗi: " + e.getMessage());
        }
    }

    // ─── Create pending order ─────────────────────────────────────────────────

    private Order createPendingOrder(String tableId) {
        String sql = """
            INSERT INTO orders (status, total_amount, table_id, restaurant_id, created_at)
            VALUES ('PENDING', 0, ?, ?, SYSTIMESTAMP)
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"order_id"})) {
            ps.setLong(1, Long.parseLong(tableId));
            ps.setLong(2, restaurantId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    String orderId = String.valueOf(keys.getLong(1));
                    System.out.println("[TabletOrderDAO] Created order " + orderId + " for table " + tableId);
                    return new Order(orderId, tableId, getTableName(tableId), 0,
                                     Order.Status.PENDING, "", null, null);
                }
            }
        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] createPendingOrder lỗi: " + e.getMessage());
        }
        return null;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Order mapOrder(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        String createdAt = "";
        if (ts != null) {
            createdAt = ts.toLocalDateTime().toString().replace("T", " ");
            if (createdAt.length() > 16) createdAt = createdAt.substring(0, 16);
        }
        String statusStr = rs.getString("status");
        Order.Status status = Order.Status.PENDING;
        try { status = Order.Status.valueOf(statusStr.toUpperCase()); } catch (Exception ignored) {}
        return new Order(
                String.valueOf(rs.getLong("order_id")),
                String.valueOf(rs.getLong("table_id")),
                rs.getString("table_number"),
                rs.getBigDecimal("total_amount") != null
                        ? rs.getBigDecimal("total_amount").doubleValue() : 0,
                status, createdAt,
                safeGetString(rs, "customer_name"),
                safeGetString(rs, "customer_phone")
        );
    }

    private String safeGetString(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (SQLException e) { return null; }
    }

    private long parseLongOrDefault(String s, long def) {
        if (s == null || s.isBlank()) return def;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return def; }
    }
}