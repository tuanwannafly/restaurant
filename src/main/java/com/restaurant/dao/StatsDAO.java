package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Trả về trạng thái hiện tại của các bàn trong nhà hàng.
     *
     * @return {@link TableStats} – không bao giờ null
     */
    public TableStats getTableStats() {
        requireManager();

        long restaurantId = AppSession.getInstance().getRestaurantId();

        String sql =
            "SELECT " +
            "  SUM(CASE WHEN status = 'AVAILABLE' THEN 1 ELSE 0 END) AS available, " +
            "  SUM(CASE WHEN status = 'OCCUPIED'  THEN 1 ELSE 0 END) AS occupied,  " +
            "  SUM(CASE WHEN status = 'RESERVED'  THEN 1 ELSE 0 END) AS reserved,  " +
            "  COUNT(*) AS total " +
            "FROM restaurant_tables " +
            "WHERE restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, restaurantId);

            try (ResultSet rs = ps.executeQuery()) {
                TableStats ts = new TableStats();
                if (rs.next()) {
                    ts.available = rs.getInt("available");
                    ts.occupied  = rs.getInt("occupied");
                    ts.reserved  = rs.getInt("reserved");
                    ts.total     = rs.getInt("total");
                }
                return ts;
            }

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[StatsDAO] getTableStats lỗi: " + e.getMessage());
            throw new RuntimeException("[StatsDAO] Không thể tải trạng thái bàn: " + e.getMessage(), e);
        }
    }

    // ── Method 4: getDashboardStats ───────────────────────────────────────────

    /**
     * Trả về Map thống kê dashboard real-time với các keys:<br>
     * {@code tables_available}, {@code tables_occupied}, {@code tables_dirty},
     * {@code orders_pending}, {@code orders_completed_today}.
     *
     * <p>Sử dụng single query với CASE WHEN thay vì 5 query riêng để giảm round-trip DB.
     *
     * @param restaurantId ID nhà hàng cần lấy số liệu
     * @return Map&lt;String, Integer&gt; – không bao giờ null; giá trị mặc định là 0
     */
    public Map<String, Integer> getDashboardStats(long restaurantId) {
        // NOTE: không gọi requireManager() – HomePanel cần hiển thị cho mọi role
        String sql =
            "SELECT " +
            "  SUM(CASE WHEN src='T' AND status='AVAILABLE'                     THEN cnt ELSE 0 END) AS tables_available, " +
            "  SUM(CASE WHEN src='T' AND status='OCCUPIED'                      THEN cnt ELSE 0 END) AS tables_occupied, " +
            "  SUM(CASE WHEN src='T' AND status IN ('DIRTY','CLEANING')         THEN cnt ELSE 0 END) AS tables_dirty, " +
            "  SUM(CASE WHEN src='O' AND status IN ('PENDING','ACCEPTED','COOKING') THEN cnt ELSE 0 END) AS orders_pending, " +
            "  SUM(CASE WHEN src='C' AND status='COMPLETED'                     THEN cnt ELSE 0 END) AS orders_completed_today " +
            "FROM ( " +
            "  SELECT 'T' AS src, status, COUNT(*) AS cnt " +
            "  FROM restaurant_tables " +
            "  WHERE restaurant_id = ? " +
            "  GROUP BY status " +
            "  UNION ALL " +
            "  SELECT 'O' AS src, item_status AS status, COUNT(*) AS cnt " +
            "  FROM order_items oi " +
            "  JOIN orders o ON oi.order_id = o.order_id " +
            "  WHERE o.restaurant_id = ? " +
            "    AND oi.item_status IN ('PENDING','ACCEPTED','COOKING') " +
            "  GROUP BY item_status " +
            "  UNION ALL " +
            "  SELECT 'C' AS src, status, COUNT(*) AS cnt " +
            "  FROM orders " +
            "  WHERE restaurant_id = ? " +
            "    AND status = 'COMPLETED' " +
            "    AND TRUNC(completed_at) = TRUNC(SYSDATE) " +
            "  GROUP BY status " +
            ")";

        Map<String, Integer> result = new HashMap<>();
        result.put("tables_available",      0);
        result.put("tables_occupied",       0);
        result.put("tables_dirty",          0);
        result.put("orders_pending",        0);
        result.put("orders_completed_today",0);

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, restaurantId);
            ps.setLong(2, restaurantId);
            ps.setLong(3, restaurantId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("tables_available",       rs.getInt("tables_available"));
                    result.put("tables_occupied",        rs.getInt("tables_occupied"));
                    result.put("tables_dirty",           rs.getInt("tables_dirty"));
                    result.put("orders_pending",         rs.getInt("orders_pending"));
                    result.put("orders_completed_today", rs.getInt("orders_completed_today"));
                }
            }

        } catch (Exception e) {
            System.err.println("[StatsDAO] getDashboardStats lỗi: " + e.getMessage());
        }

        return result;
    }

    // ── Method 5: getAdminDashboardStats ──────────────────────────────────────

    /**
     * Thống kê tổng quan toàn hệ thống dành cho màn hình HomePanel (SUPER_ADMIN).<br>
     * Trả về Map với các keys:
     * <ul>
     *   <li>{@code active_restaurants}  – số nhà hàng đang hoạt động (status = 'ACTIVE')</li>
     *   <li>{@code new_restaurants}     – số nhà hàng mới tạo trong ngày hôm nay</li>
     *   <li>{@code revenue_today}       – tổng doanh thu hôm nay, VND (kiểu Long)</li>
     *   <li>{@code orders_today}        – số đơn hoàn tất hôm nay</li>
     * </ul>
     *
     * <p>Dùng scalar subquery trên {@code DUAL} để lấy 4 chỉ số trong một round-trip DB.
     * Không có guard quyền — cùng chiến lược với {@link #getDashboardStats(long)}.
     *
     * @return Map&lt;String, Long&gt; – không bao giờ null; giá trị mặc định là 0
     */
    public Map<String, Long> getAdminDashboardStats() {
        // NOTE: không gọi requireManager() – HomePanel hiển thị cho mọi admin đăng nhập
        String sql =
            "SELECT " +
            "  (SELECT COUNT(*) " +
            "     FROM restaurants " +
            "    WHERE status = 'ACTIVE')                             AS active_restaurants, " +
            "  (SELECT COUNT(*) " +
            "     FROM restaurants " +
            "    WHERE TRUNC(created_at) = TRUNC(SYSDATE))            AS new_restaurants, " +
            "  (SELECT NVL(SUM(total_amount), 0) " +
            "     FROM orders " +
            "    WHERE status = 'COMPLETED' " +
            "      AND TRUNC(completed_at) = TRUNC(SYSDATE))          AS revenue_today, " +
            "  (SELECT COUNT(*) " +
            "     FROM orders " +
            "    WHERE status = 'COMPLETED' " +
            "      AND TRUNC(completed_at) = TRUNC(SYSDATE))          AS orders_today " +
            "FROM DUAL";

        Map<String, Long> result = new HashMap<>();
        result.put("active_restaurants", 0L);
        result.put("new_restaurants",    0L);
        result.put("revenue_today",      0L);
        result.put("orders_today",       0L);

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                result.put("active_restaurants", rs.getLong("active_restaurants"));
                result.put("new_restaurants",    rs.getLong("new_restaurants"));
                result.put("revenue_today",      rs.getLong("revenue_today"));
                result.put("orders_today",       rs.getLong("orders_today"));
            }

        } catch (Exception e) {
            System.err.println("[StatsDAO] getAdminDashboardStats lỗi: " + e.getMessage());
        }

        return result;
    }

// ── Method 6: getSuperAdminStats ──────────────────────────────────────────

    /**
     * Thống kê toàn hệ thống dành cho {@code AdminStatsPanel} (SUPER_ADMIN).<br>
     * Trả về Map với các keys:
     * <ul>
     *   <li>{@code total_restaurants} – số nhà hàng đang ACTIVE (không phụ thuộc date range)</li>
     *   <li>{@code total_revenue}     – tổng doanh thu các đơn COMPLETED trong khoảng [from, to]</li>
     *   <li>{@code total_orders}      – số đơn COMPLETED trong khoảng [from, to]</li>
     * </ul>
     *
     * @param from ngày bắt đầu (inclusive)
     * @param to   ngày kết thúc (inclusive)
     * @return Map&lt;String, Long&gt; — không bao giờ null; giá trị mặc định là 0
     */
    public Map<String, Long> getSuperAdminStats(LocalDate from, LocalDate to) {
        // Scalar subquery cho restaurants ACTIVE (không lọc theo ngày),
        // aggregate trực tiếp cho revenue và orders trong khoảng from–to.
        String sql =
            "SELECT " +
            "  (SELECT COUNT(*) " +
            "     FROM restaurants " +
            "    WHERE status = 'ACTIVE')             AS total_restaurants, " +
            "  NVL(SUM(o.total_amount), 0)            AS total_revenue, " +
            "  COUNT(o.order_id)                      AS total_orders " +
            "FROM orders o " +
            "WHERE o.status = 'COMPLETED' " +
            "  AND TRUNC(o.created_at) >= ? " +
            "  AND TRUNC(o.created_at) <= ?";

        Map<String, Long> result = new HashMap<>();
        result.put("total_restaurants", 0L);
        result.put("total_revenue",     0L);
        result.put("total_orders",      0L);

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, java.sql.Date.valueOf(from));
            ps.setDate(2, java.sql.Date.valueOf(to));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("total_restaurants", rs.getLong("total_restaurants"));
                    result.put("total_revenue",     rs.getLong("total_revenue"));
                    result.put("total_orders",      rs.getLong("total_orders"));
                }
            }

        } catch (Exception e) {
            System.err.println("[StatsDAO] getSuperAdminStats lỗi: " + e.getMessage());
        }

        return result;
    }
}
