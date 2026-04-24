package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.restaurant.db.DBConnection;
import com.restaurant.model.Order;
import com.restaurant.session.AppSession;
import com.restaurant.session.RbacGuard;

/**
 * DAO thao tác bảng ORDERS + ORDER_ITEMS trong Oracle DB.
 *
 * Ánh xạ trạng thái (DB ↔ Order.Status):
 * <pre>
 *   DB value        → FE Status
 *   ─────────────────────────────────────────────────
 *   PENDING         → PENDING
 *   IN_PROGRESS     → PENDING   (legacy backward-compat)
 *   CONFIRMED       → ACCEPTED  (legacy backward-compat)
 *   SERVED          → DELIVERED (legacy backward-compat)
 *   ACCEPTED        → ACCEPTED
 *   COOKING         → COOKING
 *   READY           → READY
 *   DELIVERING      → DELIVERING
 *   DELIVERED       → DELIVERED
 *   COMPLETED       → COMPLETED
 *   CANCELLED       → CANCELLED
 * </pre>
 *
 * Ánh xạ trạng thái (FE ↔ DB) khi ghi:
 * <pre>
 *   FE Status       → DB value
 *   ─────────────────────────────────────────────────
 *   PENDING         → PENDING
 *   ACCEPTED        → ACCEPTED
 *   COOKING         → COOKING
 *   READY           → READY
 *   DELIVERING      → DELIVERING
 *   DELIVERED       → DELIVERED
 *   COMPLETED       → COMPLETED
 *   CANCELLED       → CANCELLED
 *   DANG_PHUC_VU    → PENDING   (legacy)
 *   HOAN_THANH      → COMPLETED (legacy)
 *   DA_HUY          → CANCELLED (legacy)
 * </pre>
 */
public class OrderDAO {

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private long rid()         { return AppSession.getInstance().getRestaurantId(); }
    private boolean isSuperAdmin() { return RbacGuard.getInstance().isSuperAdmin(); }

    // ─── READ ─────────────────────────────────────────────────────────────────

    public List<Order> getAll() {
        List<Order> list = new ArrayList<>();

        String sql = isSuperAdmin()
            ? """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
                     o.customer_name, o.customer_phone,
                     t.table_number, t.table_id
              FROM orders o
              JOIN restaurant_tables t ON o.table_id = t.table_id
              ORDER BY o.created_at DESC
              """
            : """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
                     o.customer_name, o.customer_phone,
                     t.table_number, t.table_id
              FROM orders o
              JOIN restaurant_tables t ON o.table_id = t.table_id
              WHERE o.restaurant_id = ?
              ORDER BY o.created_at DESC
              """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (!isSuperAdmin()) ps.setLong(1, rid());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order o = mapOrder(rs);
                    o.setItems(getOrderItems(conn, rs.getLong("order_id")));
                    list.add(o);
                }
            }
        } catch (Exception e) {
            System.err.println("[OrderDAO] getAll lỗi: " + e.getMessage());
        }
        return list;
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    public Order create(Order o) {
        String sql = """
            INSERT INTO orders (status, total_amount, table_id, restaurant_id,
                                customer_name, customer_phone, created_at)
            VALUES (?, ?, ?, ?, ?, ?, SYSTIMESTAMP)
            """;

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long orderId;
                try (PreparedStatement ps = conn.prepareStatement(sql, new String[]{"order_id"})) {
                    ps.setString(1, toDbStatus(o.getStatus()));
                    ps.setBigDecimal(2, java.math.BigDecimal.valueOf(o.getTotalAmount()));
                    ps.setLong(3, parseLongOrDefault(o.getTableId(), 0));
                    ps.setLong(4, rid());
                    ps.setString(5, o.getCustomerName());
                    ps.setString(6, o.getCustomerPhone());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Không lấy được order_id");
                        orderId = keys.getLong(1);
                        o.setId(String.valueOf(orderId));
                    }
                }

                insertOrderItems(conn, orderId, o.getItems());
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo đơn hàng: " + e.getMessage(), e);
        }
        return o;
    }

    // ─── CREATE EMPTY ORDER (Phase 2 – Thu ngân mở bàn) ─────────────────────

    /**
     * Tạo đơn hàng rỗng khi thu ngân / admin mở bàn cho khách.
     *
     * <p>INSERT một dòng vào {@code orders} với:
     * <ul>
     *   <li>{@code status = 'PENDING'}</li>
     *   <li>{@code total_amount = 0}</li>
     *   <li>{@code customer_name} và {@code customer_phone} – nullable</li>
     * </ul>
     *
     * @param tableId      khoá chính bàn (String → parse sang long)
     * @param restaurantId restaurant_id của phiên hiện tại
     * @param customerName tên khách – có thể {@code null}
     * @param customerPhone số điện thoại khách – có thể {@code null}
     * @return {@link Order} với {@code id} vừa được DB sinh ra
     * @throws RuntimeException nếu lỗi SQL
     */
    public Order createEmptyOrder(String tableId, long restaurantId,
                                  String customerName, String customerPhone) {
        String sql = """
            INSERT INTO orders (status, total_amount, table_id, restaurant_id,
                                customer_name, customer_phone, created_at)
            VALUES ('PENDING', 0, ?, ?, ?, ?, SYSTIMESTAMP)
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"order_id"})) {

            ps.setLong(1, Long.parseLong(tableId));
            ps.setLong(2, restaurantId);
            ps.setString(3, customerName);
            ps.setString(4, customerPhone);
            ps.executeUpdate();

            long orderId;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Không lấy được order_id");
                orderId = keys.getLong(1);
            }

            Order o = new Order(
                String.valueOf(orderId),
                tableId,
                null,                     // tableName – chưa cần thiết cho đơn rỗng
                0,
                Order.Status.PENDING,
                "",
                customerName,
                customerPhone
            );
            o.setItems(new java.util.ArrayList<>());
            return o;

        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo đơn rỗng: " + e.getMessage(), e);
        }
    }

    // ─── UPDATE STATUS ────────────────────────────────────────────────────────

    /**
     * Cập nhật trạng thái đơn hàng.
     * SUPER_ADMIN bỏ qua điều kiện restaurant_id.
     * rowsAffected == 0 → SecurityException (order không thuộc nhà hàng này).
     */
    public void updateStatus(String id, Order.Status status) {
        String sql = isSuperAdmin()
            ? "UPDATE orders SET status = ? WHERE order_id = ?"
            : "UPDATE orders SET status = ? WHERE order_id = ? AND restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, toDbStatus(status));
            ps.setLong(2, Long.parseLong(id));
            if (!isSuperAdmin()) ps.setLong(3, rid());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SecurityException(
                    "[OrderDAO] updateStatus từ chối: order_id=" + id +
                    " không thuộc restaurant_id=" + rid());
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi cập nhật trạng thái đơn hàng: " + e.getMessage(), e);
        }
    }

    // ─── UPDATE (status + total_amount) ──────────────────────────────────────

    public Order update(Order o) {
        String sql = isSuperAdmin()
            ? "UPDATE orders SET status = ?, total_amount = ? WHERE order_id = ?"
            : "UPDATE orders SET status = ?, total_amount = ? WHERE order_id = ? AND restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, toDbStatus(o.getStatus()));
            ps.setBigDecimal(2, java.math.BigDecimal.valueOf(o.getTotalAmount()));
            ps.setLong(3, Long.parseLong(o.getId()));
            if (!isSuperAdmin()) ps.setLong(4, rid());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SecurityException(
                    "[OrderDAO] update từ chối: order_id=" + o.getId() +
                    " không thuộc restaurant_id=" + rid());
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi cập nhật đơn hàng: " + e.getMessage(), e);
        }
        return o;
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    public void delete(String id) {
        String deleteOrderSql = isSuperAdmin()
            ? "DELETE FROM orders WHERE order_id = ?"
            : "DELETE FROM orders WHERE order_id = ? AND restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM order_items WHERE order_id = ?")) {
                    ps.setLong(1, Long.parseLong(id));
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(deleteOrderSql)) {
                    ps.setLong(1, Long.parseLong(id));
                    if (!isSuperAdmin()) ps.setLong(2, rid());

                    int rows = ps.executeUpdate();
                    if (rows == 0) {
                        throw new SecurityException(
                            "[OrderDAO] delete từ chối: order_id=" + id +
                            " không thuộc restaurant_id=" + rid());
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xóa đơn hàng: " + e.getMessage(), e);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Order mapOrder(ResultSet rs) throws SQLException {
        String createdAt = "";
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            createdAt = ts.toLocalDateTime().toString().replace("T", " ");
            if (createdAt.length() > 16) createdAt = createdAt.substring(0, 16);
        }

        // customer_name / customer_phone có thể null (cột mới, nullable)
        String customerName  = safeGetString(rs, "customer_name");
        String customerPhone = safeGetString(rs, "customer_phone");

        return new Order(
                String.valueOf(rs.getLong("order_id")),
                String.valueOf(rs.getLong("table_id")),
                rs.getString("table_number"),
                rs.getBigDecimal("total_amount") != null
                        ? rs.getBigDecimal("total_amount").doubleValue() : 0,
                fromDbStatus(rs.getString("status")),
                createdAt,
                customerName,
                customerPhone
        );
    }

    /**
     * Lấy danh sách món của một đơn hàng.
     * SELECT thêm cột {@code item_status} từ order_items và map sang
     * {@link Order.OrderItem.ItemStatus}.
     */
    private List<Order.OrderItem> getOrderItems(Connection conn, long orderId) throws SQLException {
        List<Order.OrderItem> items = new ArrayList<>();
        String sql = """
            SELECT oi.quantity, oi.price, oi.item_status,
                   mi.name AS item_name, mi.item_id
            FROM order_items oi
            JOIN menu_items mi ON oi.menu_item_id = mi.item_id
            WHERE oi.order_id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order.OrderItem item = new Order.OrderItem(
                            String.valueOf(rs.getLong("item_id")),
                            rs.getString("item_name"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("price").doubleValue(),
                            fromDbItemStatus(safeGetString(rs, "item_status"))
                    );
                    items.add(item);
                }
            }
        }
        return items;
    }

    private void insertOrderItems(Connection conn, long orderId,
                                  List<Order.OrderItem> items) throws SQLException {
        if (items == null || items.isEmpty()) return;
        String sql = """
            INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status)
            VALUES (?, ?, ?, ?, 'PENDING')
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Order.OrderItem item : items) {
                ps.setLong(1, orderId);
                ps.setLong(2, parseLongOrDefault(item.getMenuItemId(), 0));
                ps.setInt(3, item.getQuantity());
                ps.setBigDecimal(4, java.math.BigDecimal.valueOf(item.getUnitPrice()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ─── Status mapping ───────────────────────────────────────────────────────

    /**
     * FE Order.Status → DB string.
     * Legacy enum values (DANG_PHUC_VU, HOAN_THANH, DA_HUY) được map về giá
     * trị DB tương đương để không break code cũ gọi updateStatus/create.
     */
    private String toDbStatus(Order.Status s) {
        if (s == null) return "PENDING";
        switch (s) {
            case PENDING:      return "PENDING";
            case ACCEPTED:     return "ACCEPTED";
            case COOKING:      return "COOKING";
            case READY:        return "READY";
            case DELIVERING:   return "DELIVERING";
            case DELIVERED:    return "DELIVERED";
            case COMPLETED:    return "COMPLETED";
            case CANCELLED:    return "CANCELLED";
            // Legacy
            case DANG_PHUC_VU: return "PENDING";
            case HOAN_THANH:   return "COMPLETED";
            case DA_HUY:       return "CANCELLED";
            default:           return "PENDING";
        }
    }

    /**
     * DB string → FE Order.Status.
     * Backward-compat: các giá trị DB cũ (IN_PROGRESS, CONFIRMED, SERVED)
     * được map về trạng thái new tương đương.
     */
    private Order.Status fromDbStatus(String s) {
        if (s == null) return Order.Status.PENDING;
        switch (s) {
            case "PENDING":      return Order.Status.PENDING;
            case "ACCEPTED":
            case "CONFIRMED":    return Order.Status.ACCEPTED;   // CONFIRMED = legacy
            case "COOKING":      return Order.Status.COOKING;
            case "READY":        return Order.Status.READY;
            case "DELIVERING":   return Order.Status.DELIVERING;
            case "DELIVERED":
            case "SERVED":       return Order.Status.DELIVERED;  // SERVED = legacy
            case "COMPLETED":    return Order.Status.COMPLETED;
            case "CANCELLED":    return Order.Status.CANCELLED;
            // Legacy IN_PROGRESS → PENDING (chờ xử lý tiếp)
            case "IN_PROGRESS":
            default:             return Order.Status.PENDING;
        }
    }

    // ─── ItemStatus mapping ───────────────────────────────────────────────────

    private Order.OrderItem.ItemStatus fromDbItemStatus(String s) {
        if (s == null) return Order.OrderItem.ItemStatus.PENDING;
        switch (s) {
            case "PENDING":    return Order.OrderItem.ItemStatus.PENDING;
            case "ACCEPTED":   return Order.OrderItem.ItemStatus.ACCEPTED;
            case "COOKING":    return Order.OrderItem.ItemStatus.COOKING;
            case "READY":      return Order.OrderItem.ItemStatus.READY;
            case "DELIVERING": return Order.OrderItem.ItemStatus.DELIVERING;
            case "DELIVERED":  return Order.OrderItem.ItemStatus.DELIVERED;
            default:           return Order.OrderItem.ItemStatus.PENDING;
        }
    }

    // ─── Misc helpers ─────────────────────────────────────────────────────────

    /** Đọc cột String – trả về null nếu cột không tồn tại hoặc giá trị NULL */
    private String safeGetString(ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (SQLException e) {
            return null; // cột chưa có trong schema cũ
        }
    }

    private long parseLongOrDefault(String s, long def) {
        if (s == null || s.isBlank()) return def;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return def; }
    }
}