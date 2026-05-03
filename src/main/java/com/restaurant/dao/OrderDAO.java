package com.restaurant.dao;

import java.math.BigDecimal;
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
 * Phase 7D: bổ sung:
 * <ul>
 *   <li>{@link CartEntry} – DTO gọn cho giỏ hàng</li>
 *   <li>{@link #openTable(String, long)} – mở bàn nhanh cho test / cashier</li>
 *   <li>{@link #findById(String)} – tìm order theo ID</li>
 *   <li>{@link #closeOrder(String, Order.Status)} – đóng đơn với status bất kỳ</li>
 *   <li>{@link #getItemStatus(String)} – lấy status của 1 order_item</li>
 *   <li>{@link #getPaymentRequestedCount(long)} – badge Thu ngân</li>
 *   <li>{@link #addOrderItems(String, List, int)} overload nhận {@link CartEntry}</li>
 * </ul>
 */
public class OrderDAO {

    // ─── CartEntry DTO ────────────────────────────────────────────────────────

    /**
     * DTO đơn giản đại diện một món trong giỏ hàng khi gọi
     * {@link #addOrderItems(String, List, int)}.
     */
    public static class CartEntry {
        public final String menuItemId;
        public final int    quantity;
        public final double price;

        public CartEntry(String menuItemId, int quantity, double price) {
            this.menuItemId = menuItemId;
            this.quantity   = quantity;
            this.price      = price;
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private long rid()             { return AppSession.getInstance().getRestaurantId(); }
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

    // ─── Phase 7D: FIND BY ID ─────────────────────────────────────────────────

    /**
     * Tìm một đơn hàng theo {@code order_id}.
     *
     * @param orderId ID đơn hàng (String)
     * @return {@link Order} nếu tìm thấy; {@code null} nếu không tồn tại hoặc lỗi
     */
    public Order findById(String orderId) {
        String sql = isSuperAdmin()
            ? """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
                     o.customer_name, o.customer_phone,
                     t.table_number, t.table_id
              FROM orders o
              JOIN restaurant_tables t ON o.table_id = t.table_id
              WHERE o.order_id = ?
              """
            : """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
                     o.customer_name, o.customer_phone,
                     t.table_number, t.table_id
              FROM orders o
              JOIN restaurant_tables t ON o.table_id = t.table_id
              WHERE o.order_id = ? AND o.restaurant_id = ?
              """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, parseLongOrDefault(orderId, 0));
            if (!isSuperAdmin()) ps.setLong(2, rid());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Order o = mapOrder(rs);
                    o.setItems(getOrderItems(conn, rs.getLong("order_id")));
                    return o;
                }
            }
        } catch (Exception e) {
            System.err.println("[OrderDAO] findById lỗi: " + e.getMessage());
        }
        return null;
    }

    // ─── Phase 7D: OPEN TABLE (alias ngắn cho createEmptyOrder) ──────────────

    /**
     * Mở bàn: tạo đơn hàng PENDING rỗng, trả về orderId dạng String.
     * Dùng trong integration test và flow cashier mở bàn nhanh.
     *
     * @param tableId      ID bàn (String)
     * @param restaurantId ID nhà hàng
     * @return orderId String vừa được DB sinh ra; {@code null} nếu lỗi
     */
    public String openTable(String tableId, long restaurantId) {
        String sql = """
            INSERT INTO orders (status, total_amount, table_id, restaurant_id,
                                customer_name, customer_phone, created_at)
            VALUES ('PENDING', 0, ?, ?, NULL, NULL, SYSTIMESTAMP)
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"order_id"})) {
            ps.setLong(1, parseLongOrDefault(tableId, 0));
            ps.setLong(2, restaurantId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return String.valueOf(keys.getLong(1));
            }
        } catch (Exception e) {
            System.err.println("[OrderDAO] openTable lỗi: " + e.getMessage());
        }
        return null;
    }

    // ─── Phase 7D: CLOSE ORDER ────────────────────────────────────────────────

    /**
     * Đóng đơn hàng với trạng thái tuỳ ý (thường là COMPLETED).
     * Ghi nhận {@code completed_at = SYSTIMESTAMP} nếu status là COMPLETED.
     *
     * @param orderId ID đơn hàng
     * @param status  trạng thái đích (vd. {@code Order.Status.COMPLETED})
     * @return {@code true} nếu update thành công
     */
    public boolean closeOrder(String orderId, Order.Status status) {
        boolean isCompleted = status == Order.Status.COMPLETED
                || status == Order.Status.HOAN_THANH;

        String sql = isSuperAdmin()
            ? (isCompleted
               ? "UPDATE orders SET status = ?, completed_at = SYSTIMESTAMP WHERE order_id = ?"
               : "UPDATE orders SET status = ? WHERE order_id = ?")
            : (isCompleted
               ? "UPDATE orders SET status = ?, completed_at = SYSTIMESTAMP WHERE order_id = ? AND restaurant_id = ?"
               : "UPDATE orders SET status = ? WHERE order_id = ? AND restaurant_id = ?");

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toDbStatus(status));
            ps.setLong(2, parseLongOrDefault(orderId, 0));
            if (!isSuperAdmin()) ps.setLong(3, rid());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[OrderDAO] closeOrder lỗi: " + e.getMessage());
            return false;
        }
    }

    // ─── Phase 7D: GET ITEM STATUS ────────────────────────────────────────────

    /**
     * Lấy {@code item_status} hiện tại của một {@code order_item_id}.
     * Dùng trong integration test để assert trạng thái sau mỗi bước.
     *
     * @param orderItemId ID của order_item
     * @return {@link Order.OrderItem.ItemStatus} hiện tại;
     *         {@code PENDING} nếu không tìm thấy hoặc lỗi
     */
    public Order.OrderItem.ItemStatus getItemStatus(String orderItemId) {
        String sql = "SELECT item_status FROM order_items WHERE order_item_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, parseLongOrDefault(orderItemId, 0));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return fromDbItemStatus(safeGetString(rs, "item_status"));
                }
            }
        } catch (Exception e) {
            System.err.println("[OrderDAO] getItemStatus lỗi: " + e.getMessage());
        }
        return Order.OrderItem.ItemStatus.PENDING;
    }

    // ─── Phase 7D: PAYMENT REQUESTED COUNT (badge Thu ngân) ──────────────────

    /**
     * Đếm số đơn hàng có trạng thái {@code PAYMENT_REQUESTED} thuộc nhà hàng.
     * Dùng để cập nhật badge đỏ trên nút Thu ngân.
     *
     * <p><b>Lưu ý:</b> Nếu DB chưa có giá trị {@code PAYMENT_REQUESTED},
     * hãy dùng {@code PENDING} làm fallback hoặc thêm status vào schema.
     *
     * @param restaurantId ID nhà hàng
     * @return số đơn PAYMENT_REQUESTED (≥ 0), 0 nếu lỗi
     */
    public int getPaymentRequestedCount(long restaurantId) {
        String sql = isSuperAdmin()
            ? "SELECT COUNT(*) FROM orders WHERE status = 'PAYMENT_REQUESTED'"
            : "SELECT COUNT(*) FROM orders WHERE status = 'PAYMENT_REQUESTED' AND restaurant_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!isSuperAdmin()) ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            System.err.println("[OrderDAO] getPaymentRequestedCount lỗi: " + e.getMessage());
        }
        return 0;
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
                    ps.setBigDecimal(2, BigDecimal.valueOf(o.getTotalAmount()));
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

    // ─── CREATE EMPTY ORDER ───────────────────────────────────────────────────

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
                String.valueOf(orderId), tableId, null, 0,
                Order.Status.PENDING, "", customerName, customerPhone);
            o.setItems(new java.util.ArrayList<>());
            return o;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo đơn rỗng: " + e.getMessage(), e);
        }
    }

    // ─── ADD ORDER ITEMS – overload nhận List<Order.OrderItem> ───────────────

    // /**
    //  * Chèn danh sách {@link Order.OrderItem} vào đơn hàng.
    //  */
    // public boolean addOrderItems(String orderId, List<Order.OrderItem> items, int roundNumber) {
    //     if (items == null || items.isEmpty()) return false;
    //     String insertSql = """
    //         INSERT INTO order_items
    //             (order_id, menu_item_id, quantity, price, item_status, round_number)
    //         VALUES (?, ?, ?, ?, 'PENDING', ?)
    //         """;
    //     String updateTotalSql = """
    //         UPDATE orders SET total_amount = (
    //             SELECT SUM(quantity * price) FROM order_items WHERE order_id = ?
    //         ) WHERE order_id = ?
    //         """;
    //     try (Connection conn = DBConnection.getInstance().getConnection()) {
    //         conn.setAutoCommit(false);
    //         try {
    //             try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
    //                 for (Order.OrderItem item : items) {
    //                     ps.setLong(1, parseLongOrDefault(orderId, 0));
    //                     ps.setLong(2, parseLongOrDefault(item.getMenuItemId(), 0));
    //                     ps.setInt(3, item.getQuantity());
    //                     ps.setBigDecimal(4, BigDecimal.valueOf(item.getUnitPrice()));
    //                     ps.setInt(5, roundNumber);
    //                     ps.addBatch();
    //                 }
    //                 ps.executeBatch();
    //             }
    //             try (PreparedStatement ps = conn.prepareStatement(updateTotalSql)) {
    //                 long oid = parseLongOrDefault(orderId, 0);
    //                 ps.setLong(1, oid);
    //                 ps.setLong(2, oid);
    //                 ps.executeUpdate();
    //             }
    //             conn.commit();
    //             return true;
    //         } catch (Exception e) {
    //             conn.rollback();
    //             throw e;
    //         } finally {
    //             conn.setAutoCommit(true);
    //         }
    //     } catch (Exception e) {
    //         System.err.println("[OrderDAO] addOrderItems(OrderItem) lỗi: " + e.getMessage());
    //         return false;
    //     }
    // }

    // ─── Phase 7D: ADD ORDER ITEMS – overload nhận List<CartEntry> ───────────

    /**
     * Overload nhận {@link CartEntry} – dùng trong integration test và
     * flow thêm món từ tablet / cashier.
     */
    public boolean addOrderItems(String orderId, List<CartEntry> entries, int roundNumber) {
        if (entries == null || entries.isEmpty()) return false;
        String insertSql = """
            INSERT INTO order_items
                (order_id, menu_item_id, quantity, price, item_status, round_number)
            VALUES (?, ?, ?, ?, 'PENDING', ?)
            """;
        String updateTotalSql = """
            UPDATE orders SET total_amount = (
                SELECT SUM(quantity * price) FROM order_items WHERE order_id = ?
            ) WHERE order_id = ?
            """;
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (CartEntry e : entries) {
                        ps.setLong(1, parseLongOrDefault(orderId, 0));
                        ps.setLong(2, parseLongOrDefault(e.menuItemId, 0));
                        ps.setInt(3, e.quantity);
                        ps.setBigDecimal(4, BigDecimal.valueOf(e.price));
                        ps.setInt(5, roundNumber);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = conn.prepareStatement(updateTotalSql)) {
                    long oid = parseLongOrDefault(orderId, 0);
                    ps.setLong(1, oid);
                    ps.setLong(2, oid);
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            System.err.println("[OrderDAO] addOrderItems(CartEntry) lỗi: " + e.getMessage());
            return false;
        }
    }

    // ─── GET ITEMS WITH STATUS ────────────────────────────────────────────────

    /**
     * Lấy toàn bộ order_items của một đơn, kèm {@code item_status} và
     * {@code round_number}.
     */
    public List<Order.OrderItem> getItemsWithStatus(String orderId) {
        List<Order.OrderItem> list = new ArrayList<>();
        String sql = """
            SELECT oi.menu_item_id, oi.quantity, oi.price,
                   oi.item_status, oi.round_number,
                   mi.name AS item_name
            FROM order_items oi
            JOIN menu_items mi ON oi.menu_item_id = mi.item_id
            WHERE oi.order_id = ?
            ORDER BY oi.round_number, oi.created_at
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, parseLongOrDefault(orderId, 0));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order.OrderItem item = new Order.OrderItem(
                        String.valueOf(rs.getLong("menu_item_id")),
                        rs.getString("item_name"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("price").doubleValue(),
                        fromDbItemStatus(safeGetString(rs, "item_status"))
                    );
                    list.add(item);
                }
            }
        } catch (Exception e) {
            System.err.println("[OrderDAO] getItemsWithStatus lỗi: " + e.getMessage());
        }
        return list;
    }

    // ─── GET ACTIVE ORDER BY TABLE ────────────────────────────────────────────

    public Order getActiveOrderByTable(String tableId) {
        String sql = isSuperAdmin()
            ? """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
                     o.customer_name, o.customer_phone,
                     t.table_number, t.table_id
              FROM orders o
              JOIN restaurant_tables t ON o.table_id = t.table_id
              WHERE o.table_id = ?
                AND o.status NOT IN ('COMPLETED', 'CANCELLED')
              ORDER BY o.created_at DESC
              FETCH FIRST 1 ROWS ONLY
              """
            : """
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
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, parseLongOrDefault(tableId, 0));
            if (!isSuperAdmin()) ps.setLong(2, rid());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Order o = mapOrder(rs);
                    o.setItems(getOrderItems(conn, rs.getLong("order_id")));
                    return o;
                }
            }
        } catch (Exception e) {
            System.err.println("[OrderDAO] getActiveOrderByTable lỗi: " + e.getMessage());
        }
        return null;
    }

    // ─── COMPLETE ORDER ───────────────────────────────────────────────────────

    public boolean completeOrder(String orderId) {
        String sql = isSuperAdmin()
            ? "UPDATE orders SET status = 'COMPLETED', completed_at = SYSTIMESTAMP WHERE order_id = ?"
            : "UPDATE orders SET status = 'COMPLETED', completed_at = SYSTIMESTAMP WHERE order_id = ? AND restaurant_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, parseLongOrDefault(orderId, 0));
            if (!isSuperAdmin()) ps.setLong(2, rid());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[OrderDAO] completeOrder lỗi: " + e.getMessage());
            return false;
        }
    }

    // ─── UPDATE STATUS ────────────────────────────────────────────────────────

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
            if (rows == 0)
                throw new SecurityException("[OrderDAO] updateStatus từ chối: order_id=" + id);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi cập nhật trạng thái đơn hàng: " + e.getMessage(), e);
        }
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    public Order update(Order o) {
        String sql = isSuperAdmin()
            ? "UPDATE orders SET status = ?, total_amount = ? WHERE order_id = ?"
            : "UPDATE orders SET status = ?, total_amount = ? WHERE order_id = ? AND restaurant_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toDbStatus(o.getStatus()));
            ps.setBigDecimal(2, BigDecimal.valueOf(o.getTotalAmount()));
            ps.setLong(3, Long.parseLong(o.getId()));
            if (!isSuperAdmin()) ps.setLong(4, rid());
            int rows = ps.executeUpdate();
            if (rows == 0)
                throw new SecurityException("[OrderDAO] update từ chối: order_id=" + o.getId());
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
                    if (rows == 0)
                        throw new SecurityException("[OrderDAO] delete từ chối: order_id=" + id);
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
                createdAt,
                safeGetString(rs, "customer_name"),
                safeGetString(rs, "customer_phone")
        );
    }

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
                    items.add(new Order.OrderItem(
                            String.valueOf(rs.getLong("item_id")),
                            rs.getString("item_name"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("price").doubleValue(),
                            fromDbItemStatus(safeGetString(rs, "item_status"))
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
            INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status)
            VALUES (?, ?, ?, ?, 'PENDING')
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Order.OrderItem item : items) {
                ps.setLong(1, orderId);
                ps.setLong(2, parseLongOrDefault(item.getMenuItemId(), 0));
                ps.setInt(3, item.getQuantity());
                ps.setBigDecimal(4, BigDecimal.valueOf(item.getUnitPrice()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ─── Status mapping ───────────────────────────────────────────────────────

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
            case DANG_PHUC_VU: return "PENDING";
            case HOAN_THANH:   return "COMPLETED";
            case DA_HUY:       return "CANCELLED";
            default:           return "PENDING";
        }
    }

    private Order.Status fromDbStatus(String s) {
        if (s == null) return Order.Status.PENDING;
        switch (s) {
            case "PENDING":         return Order.Status.PENDING;
            case "ACCEPTED":
            case "CONFIRMED":       return Order.Status.ACCEPTED;
            case "COOKING":         return Order.Status.COOKING;
            case "READY":           return Order.Status.READY;
            case "DELIVERING":      return Order.Status.DELIVERING;
            case "DELIVERED":
            case "SERVED":          return Order.Status.DELIVERED;
            case "COMPLETED":       return Order.Status.COMPLETED;
            case "CANCELLED":       return Order.Status.CANCELLED;
            case "IN_PROGRESS":
            default:                return Order.Status.PENDING;
        }
    }

    private Order.OrderItem.ItemStatus fromDbItemStatus(String s) {
        if (s == null) return Order.OrderItem.ItemStatus.PENDING;
        switch (s) {
            case "ACCEPTED":   return Order.OrderItem.ItemStatus.ACCEPTED;
            case "COOKING":    return Order.OrderItem.ItemStatus.COOKING;
            case "READY":      return Order.OrderItem.ItemStatus.READY;
            case "DELIVERING": return Order.OrderItem.ItemStatus.DELIVERING;
            case "DELIVERED":  return Order.OrderItem.ItemStatus.DELIVERED;
            default:           return Order.OrderItem.ItemStatus.PENDING;
        }
    }

    private String safeGetString(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (SQLException e) { return null; }
    }

    private long parseLongOrDefault(String s, long def) {
        if (s == null || s.isBlank()) return def;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return def; }
    }
}