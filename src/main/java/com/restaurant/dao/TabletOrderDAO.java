package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import com.restaurant.db.DBConnection;
import com.restaurant.model.Order;

/**
 * DAO cho tablet khách — không phụ thuộc AppSession hay RbacGuard.
 * Nhận restaurantId trực tiếp từ {@link com.restaurant.session.TabletSession}.
 *
 * <p>Các phương thức chính:
 * <ul>
 *   <li>{@link #getTableName(String)} — lấy tên bàn từ tableId</li>
 *   <li>{@link #getOrCreateActiveOrder(String)} — lấy order đang active,
 *       hoặc tạo mới PENDING nếu chưa có</li>
 * </ul>
 */
public class TabletOrderDAO {

    private final long restaurantId;

    public TabletOrderDAO(long restaurantId) {
        this.restaurantId = restaurantId;
    }

    // ─── Table name ───────────────────────────────────────────────────────────

    /**
     * Lấy tên bàn (table_number) từ tableId.
     *
     * @param tableId ID bàn
     * @return tên bàn, hoặc "Bàn {tableId}" nếu không tìm thấy
     */
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
                if (rs.next()) {
                    return rs.getString("table_number");
                }
            }
        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] getTableName lỗi: " + e.getMessage());
        }
        return "Bàn " + tableId;
    }

    // ─── Order ────────────────────────────────────────────────────────────────

    /**
     * Lấy order đang active của bàn (status khác COMPLETED và CANCELLED).
     * Nếu chưa có order → tạo mới tự động với status PENDING.
     *
     * @param tableId ID bàn
     * @return Order đang active, hoặc null nếu lỗi DB
     */
    public Order getOrCreateActiveOrder(String tableId) {
        // 1. Tìm order đang active
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
                    return o;
                }
            }
        } catch (Exception e) {
            System.err.println("[TabletOrderDAO] getOrCreateActiveOrder lỗi: " + e.getMessage());
            return null;
        }

        // 2. Chưa có order → tạo mới PENDING
        System.out.println("[TabletOrderDAO] No active order, creating new for table " + tableId);
        return createPendingOrder(tableId);
    }

    /**
     * Tạo order mới với status PENDING, total_amount = 0.
     *
     * @param tableId ID bàn
     * @return Order vừa tạo, hoặc null nếu lỗi
     */
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
                    String tableName = getTableName(tableId);
                    System.out.println("[TabletOrderDAO] Created order " + orderId
                            + " for table " + tableId);
                    return new Order(
                            orderId,
                            tableId,
                            tableName,
                            0,
                            Order.Status.PENDING,
                            "",
                            null,
                            null
                    );
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
            if (createdAt.length() > 16) {
                createdAt = createdAt.substring(0, 16);
            }
        }

        String statusStr = rs.getString("status");
        Order.Status status = Order.Status.PENDING;
        try {
            status = Order.Status.valueOf(statusStr.toUpperCase());
        } catch (Exception ignored) {}

        return new Order(
                String.valueOf(rs.getLong("order_id")),
                String.valueOf(rs.getLong("table_id")),
                rs.getString("table_number"),
                rs.getBigDecimal("total_amount") != null
                        ? rs.getBigDecimal("total_amount").doubleValue()
                        : 0,
                status,
                createdAt,
                safeGetString(rs, "customer_name"),
                safeGetString(rs, "customer_phone")
        );
    }

    private String safeGetString(ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (SQLException e) {
            return null;
        }
    }
}
