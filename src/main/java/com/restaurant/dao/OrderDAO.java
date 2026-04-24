package com.restaurant.dao;

import com.restaurant.db.DBConnection;
import com.restaurant.model.Order;
import com.restaurant.session.AppSession;
import com.restaurant.session.RbacGuard;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO thao tác bảng ORDERS + ORDER_ITEMS trong Oracle DB.
 *
 * Ánh xạ trạng thái:
 *   DB status          ↔  FE Order.Status
 *   PENDING / IN_PROGRESS / CONFIRMED / READY / SERVED  ↔  DANG_PHUC_VU
 *   COMPLETED          ↔  HOAN_THANH
 *   CANCELLED          ↔  DA_HUY
 */
public class OrderDAO {

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private long rid() { return AppSession.getInstance().getRestaurantId(); }
    private boolean isSuperAdmin() { return RbacGuard.getInstance().isSuperAdmin(); }

    // ─── READ ─────────────────────────────────────────────────────────────────

    public List<Order> getAll() {
        List<Order> list = new ArrayList<>();

        String sql = isSuperAdmin()
            ? """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
                     t.table_number, t.table_id
              FROM orders o
              JOIN restaurant_tables t ON o.table_id = t.table_id
              ORDER BY o.created_at DESC
              """
            : """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
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
            INSERT INTO orders (status, total_amount, table_id, restaurant_id, created_at)
            VALUES (?, ?, ?, ?, SYSTIMESTAMP)
            """;

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long orderId;
                try (PreparedStatement ps = conn.prepareStatement(sql, new String[]{"order_id"})) {
                    ps.setString(1, toDbStatus(o.getStatus()));
                    ps.setBigDecimal(2, java.math.BigDecimal.valueOf(o.getTotalAmount()));
                    long tableId = parseLongOrDefault(o.getTableId(), 0);
                    ps.setLong(3, tableId);
                    ps.setLong(4, rid());
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
                // Xóa order items trước
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM order_items WHERE order_id = ?")) {
                    ps.setLong(1, Long.parseLong(id));
                    ps.executeUpdate();
                }

                // Xóa order chính, có guard restaurant_id
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

        return new Order(
                String.valueOf(rs.getLong("order_id")),
                String.valueOf(rs.getLong("table_id")),
                rs.getString("table_number"),
                rs.getBigDecimal("total_amount") != null
                        ? rs.getBigDecimal("total_amount").doubleValue() : 0,
                fromDbStatus(rs.getString("status")),
                createdAt
        );
    }

    private List<Order.OrderItem> getOrderItems(Connection conn, long orderId) throws SQLException {
        List<Order.OrderItem> items = new ArrayList<>();
        String sql = """
            SELECT oi.quantity, oi.price, mi.name AS item_name, mi.item_id
            FROM order_items oi
            JOIN menu_items mi ON oi.menu_item_id = mi.item_id
            WHERE oi.order_id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new Order.OrderItem(
                            String.valueOf(rs.getLong("item_id")),
                            rs.getString("item_name"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("price").doubleValue()
                    ));
                }
            }
        }
        return items;
    }

    private void insertOrderItems(Connection conn, long orderId,
                                  List<Order.OrderItem> items) throws SQLException {
        if (items == null || items.isEmpty()) return;
        String sql = """
            INSERT INTO order_items (order_id, menu_item_id, quantity, price, status)
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

    private String toDbStatus(Order.Status s) {
        if (s == null) return "PENDING";
        return switch (s) {
            case HOAN_THANH -> "COMPLETED";
            case DA_HUY     -> "CANCELLED";
            default         -> "IN_PROGRESS";
        };
    }

    private Order.Status fromDbStatus(String s) {
        if (s == null) return Order.Status.DANG_PHUC_VU;
        return switch (s) {
            case "COMPLETED" -> Order.Status.HOAN_THANH;
            case "CANCELLED" -> Order.Status.DA_HUY;
            default          -> Order.Status.DANG_PHUC_VU;
        };
    }

    private long parseLongOrDefault(String s, long def) {
        if (s == null || s.isBlank()) return def;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return def; }
    }
}