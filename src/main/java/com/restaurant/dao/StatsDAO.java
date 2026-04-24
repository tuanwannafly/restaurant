package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.restaurant.db.DBConnection;
import com.restaurant.session.AppSession;
import com.restaurant.session.RbacGuard;

/**
 * DAO thống kê doanh thu, top món, trạng thái bàn.
 * Chỉ RESTAURANT_ADMIN trở lên mới được gọi các method này.
 */
public class StatsDAO {

    // ── Inner classes ──────────────────────────────────────────────────────────

    public static class RevenueStats {
        public long totalRevenue;  // tổng VND
        public int  orderCount;
        public long avgPerOrder;   // totalRevenue / orderCount, hoặc 0
    }

    public static class TopItem {
        public String itemName;
        public int    totalQty;
        public long   totalRevenue;
    }

    public static class TableStats {
        public int available;
        public int occupied;
        public int reserved;
        public int total;
    }

    // ── Guard helper ───────────────────────────────────────────────────────────

    private void requireManager() {
        if (!RbacGuard.getInstance().isManagerOrAbove()) {
            throw new SecurityException("Chỉ quản lý mới xem được thống kê");
        }
    }

    // ── Method 1: getRevenue ───────────────────────────────────────────────────

    public RevenueStats getRevenue(LocalDate from, LocalDate to) {
        requireManager();

        long restaurantId = AppSession.getInstance().getRestaurantId();

        String sql =
            "SELECT NVL(SUM(total_amount), 0) AS revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM orders " +
            "WHERE restaurant_id = ? " +
            "  AND status = 'COMPLETED' " +
            "  AND TRUNC(created_at) >= ? " +
            "  AND TRUNC(created_at) <= ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, restaurantId);
            ps.setDate(2, java.sql.Date.valueOf(from));
            ps.setDate(3, java.sql.Date.valueOf(to));

            try (ResultSet rs = ps.executeQuery()) {
                RevenueStats stats = new RevenueStats();
                if (rs.next()) {
                    stats.totalRevenue = rs.getLong("revenue");
                    stats.orderCount   = rs.getInt("order_count");
                    stats.avgPerOrder  = stats.orderCount > 0
                            ? stats.totalRevenue / stats.orderCount : 0;
                }
                return stats;
            }

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[StatsDAO] getRevenue lỗi: " + e.getMessage());
            throw new RuntimeException("[StatsDAO] Không thể tải doanh thu: " + e.getMessage(), e);
        }
    }

    // ── Method 2: getTopItems ─────────────────────────────────────────────────

    public List<TopItem> getTopItems(LocalDate from, LocalDate to, int limit) {
        requireManager();

        long restaurantId = AppSession.getInstance().getRestaurantId();

        String sql =
            "SELECT mi.name, " +
            "       SUM(oi.quantity)            AS total_qty, " +
            "       SUM(oi.quantity * oi.price) AS total_rev " +
            "FROM order_items oi " +
            "JOIN menu_items mi ON oi.menu_item_id = mi.item_id " +
            "JOIN menus mn      ON mi.menu_id = mn.menu_id " +
            "JOIN orders o      ON oi.order_id = o.order_id " +
            "WHERE o.restaurant_id = ? " +
            "  AND o.status = 'COMPLETED' " +
            "  AND TRUNC(o.created_at) >= ? " +
            "  AND TRUNC(o.created_at) <= ? " +
            "GROUP BY mi.name " +
            "ORDER BY total_qty DESC " +
            "FETCH FIRST ? ROWS ONLY";

        List<TopItem> items = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, restaurantId);
            ps.setDate(2, java.sql.Date.valueOf(from));
            ps.setDate(3, java.sql.Date.valueOf(to));
            ps.setInt(4, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TopItem item = new TopItem();
                    item.itemName     = rs.getString("name");
                    item.totalQty     = rs.getInt("total_qty");
                    item.totalRevenue = rs.getLong("total_rev");
                    items.add(item);
                }
            }

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[StatsDAO] getTopItems lỗi: " + e.getMessage());
            throw new RuntimeException("[StatsDAO] Không thể tải top món: " + e.getMessage(), e);
        }

        return items;
    }

    // ── Method 3: getTableStats ───────────────────────────────────────────────

    public TableStats getTableStats() {
        requireManager();

        long restaurantId = AppSession.getInstance().getRestaurantId();

        String sql =
            "SELECT status, COUNT(*) AS cnt " +
            "FROM restaurant_tables " +
            "WHERE restaurant_id = ? " +
            "GROUP BY status";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, restaurantId);

            TableStats ts = new TableStats();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    int    cnt    = rs.getInt("cnt");
                    if (status == null) continue;
                    switch (status.toUpperCase()) {
                        case "AVAILABLE" -> ts.available = cnt;
                        case "OCCUPIED"  -> ts.occupied  = cnt;
                        case "RESERVED"  -> ts.reserved  = cnt;
                    }
                }
            }
            ts.total = ts.available + ts.occupied + ts.reserved;
            return ts;

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[StatsDAO] getTableStats lỗi: " + e.getMessage());
            throw new RuntimeException("[StatsDAO] Không thể tải trạng thái bàn: " + e.getMessage(), e);
        }
    }
}
