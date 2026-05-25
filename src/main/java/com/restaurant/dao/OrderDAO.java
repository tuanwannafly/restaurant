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
import com.restaurant.session.AuditLogger;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;

public class OrderDAO {

    // ─── CartEntry DTO ────────────────────────────────────────────────────────

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

    // ─── Auto-migration: payment_method ──────────────────────────────────────

    private static volatile boolean _colChecked = false;

    private void ensurePaymentMethodColumn() {
        if (_colChecked) return;
        synchronized (OrderDAO.class) {
            if (_colChecked) return;
            try (java.sql.Connection conn = DBConnection.getInstance().getConnection();
                 java.sql.Statement  stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE orders ADD (payment_method VARCHAR2(20))");
            } catch (java.sql.SQLException e) {
                if (!e.getMessage().contains("ORA-01430"))
                    System.err.println("[OrderDAO] ensurePaymentMethodColumn lỗi: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[OrderDAO] ensurePaymentMethodColumn lỗi kết nối: " + e.getMessage());
            } finally {
                _colChecked = true;
            }
        }
    }

    // ─── Auto-migration: cancelled_reason / cancelled_at ─────────────────────

    private static volatile boolean _cancelColChecked = false;

    private void ensureCancelColumns() {
        if (_cancelColChecked) return;
        synchronized (OrderDAO.class) {
            if (_cancelColChecked) return;
            try (java.sql.Connection conn = DBConnection.getInstance().getConnection();
                 java.sql.Statement  stmt = conn.createStatement()) {
                try { stmt.execute("ALTER TABLE orders ADD (cancelled_reason VARCHAR2(500))");
                      System.out.println("[OrderDAO] Đã thêm cột cancelled_reason vào bảng orders.");
                } catch (java.sql.SQLException e) {
                    if (!e.getMessage().contains("ORA-01430"))
                        System.err.println("[OrderDAO] ensureCancelColumns (reason) lỗi: " + e.getMessage());
                }
                try { stmt.execute("ALTER TABLE orders ADD (cancelled_at DATE)");
                      System.out.println("[OrderDAO] Đã thêm cột cancelled_at vào bảng orders.");
                } catch (java.sql.SQLException e) {
                    if (!e.getMessage().contains("ORA-01430"))
                        System.err.println("[OrderDAO] ensureCancelColumns (at) lỗi: " + e.getMessage());
                }
            } catch (Exception e) {
                System.err.println("[OrderDAO] ensureCancelColumns lỗi kết nối: " + e.getMessage());
            } finally {
                _cancelColChecked = true;
            }
        }
    }

    // ─── READ ─────────────────────────────────────────────────────────────────

    public List<Order> getAll() {
        ensurePaymentMethodColumn();
        List<Order> list = new ArrayList<>();
        String sql = isSuperAdmin()
            ? """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
                     o.customer_name, o.customer_phone, o.payment_method,
                     t.table_number, t.table_id
              FROM orders o
              JOIN restaurant_tables t ON o.table_id = t.table_id
              ORDER BY o.created_at DESC
              """
            : """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
                     o.customer_name, o.customer_phone, o.payment_method,
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

    public Order findById(String orderId) {
        ensurePaymentMethodColumn();
        String sql = isSuperAdmin()
            ? """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
                     o.customer_name, o.customer_phone, o.payment_method,
                     t.table_number, t.table_id
              FROM orders o
              JOIN restaurant_tables t ON o.table_id = t.table_id
              WHERE o.order_id = ?
              """
            : """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
                     o.customer_name, o.customer_phone, o.payment_method,
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

    public boolean closeOrder(String orderId, Order.Status status) {
        boolean isCompleted = status == Order.Status.COMPLETED || status == Order.Status.HOAN_THANH;
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

    // ─── CANCEL ORDER ─────────────────────────────────────────────────────────

    /**
     * Huỷ đơn hàng theo role:
     * - WAITER       : chỉ huỷ được PENDING
     * - ADMIN/SUPER  : huỷ được PENDING, ACCEPTED, COOKING, READY
     *
     * Khi huỷ thành công:
     * 1. orders.status           → CANCELLED
     * 2. order_items.item_status → CANCELLED (trừ DELIVERED)
     *    → bếp và phục vụ không còn thấy các món này
     * 3. Ghi audit log
     *
     * Không đụng vào trạng thái bàn (restaurant_tables).
     */
    public boolean cancelOrder(String orderId, String reason) {
        ensureCancelColumns();

        // 1. Kiểm tra quyền
        if (!RbacGuard.getInstance().can(Permission.CANCEL_ORDER)) {
            System.err.println("[OrderDAO] cancelOrder từ chối: thiếu quyền CANCEL_ORDER, orderId=" + orderId);
            return false;
        }

        long oid = parseLongOrDefault(orderId, 0);

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {

                // 2. Lấy trạng thái hiện tại
                String currentStatus;
                String selectSql = isSuperAdmin()
                    ? "SELECT status FROM orders WHERE order_id = ?"
                    : "SELECT status FROM orders WHERE order_id = ? AND restaurant_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setLong(1, oid);
                    if (!isSuperAdmin()) ps.setLong(2, rid());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            System.err.println("[OrderDAO] cancelOrder: không tìm thấy order_id=" + orderId);
                            conn.rollback();
                            return false;
                        }
                        currentStatus = rs.getString("status");
                    }
                }

                // 3. Kiểm tra trạng thái theo role
                String role = AppSession.getInstance().getUserRole() == null
                        ? "" : AppSession.getInstance().getUserRole().toUpperCase();
                boolean isWaiter       = "WAITER".equals(role) || "PHUC_VU".equals(role);
                boolean isAdminOrAbove = RbacGuard.getInstance().isManagerOrAbove();

                if (isWaiter) {
                    if (!"PENDING".equals(currentStatus)) {
                        System.err.println("[OrderDAO] cancelOrder từ chối (WAITER): chỉ huỷ PENDING, hiện=" + currentStatus);
                        conn.rollback();
                        return false;
                    }
                } else if (isAdminOrAbove) {
                    if ("COMPLETED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
                        System.err.println("[OrderDAO] cancelOrder từ chối (ADMIN): đơn đã " + currentStatus);
                        conn.rollback();
                        return false;
                    }
                } else {
                    System.err.println("[OrderDAO] cancelOrder từ chối: role không xác định=" + role);
                    conn.rollback();
                    return false;
                }

                // 4. Cập nhật orders → CANCELLED
                String updateSql = isSuperAdmin()
                    ? "UPDATE orders SET status='CANCELLED', cancelled_reason=?, cancelled_at=SYSDATE WHERE order_id=?"
                    : "UPDATE orders SET status='CANCELLED', cancelled_reason=?, cancelled_at=SYSDATE WHERE order_id=? AND restaurant_id=?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, reason);
                    ps.setLong(2, oid);
                    if (!isSuperAdmin()) ps.setLong(3, rid());
                    if (ps.executeUpdate() == 0) {
                        System.err.println("[OrderDAO] cancelOrder: UPDATE orders không ảnh hưởng hàng nào");
                        conn.rollback();
                        return false;
                    }
                }

                // 5. Huỷ tất cả order_items chưa hoàn thành
                //    → bếp và phục vụ lọc theo item_status nên sẽ biến mất ngay
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE order_items SET item_status='CANCELLED' " +
                        "WHERE order_id=? AND item_status NOT IN ('DELIVERED','CANCELLED')")) {
                    ps.setLong(1, oid);
                    int n = ps.executeUpdate();
                    System.out.println("[OrderDAO] cancelOrder: đã huỷ " + n + " order_item(s) của đơn " + orderId);
                }

                conn.commit();

                // 6. Audit log (ngoài transaction)
                AuditLogger.getInstance().log("CANCEL_ORDER", oid, "SUCCESS", orderId + " reason=" + reason);

                return true;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception e) {
            System.err.println("[OrderDAO] cancelOrder lỗi: " + e.getMessage());
            return false;
        }
    }

    // ─── GET ITEM STATUS ──────────────────────────────────────────────────────

    public Order.OrderItem.ItemStatus getItemStatus(String orderItemId) {
        String sql = "SELECT item_status FROM order_items WHERE order_item_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, parseLongOrDefault(orderItemId, 0));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return fromDbItemStatus(safeGetString(rs, "item_status"));
            }
        } catch (Exception e) {
            System.err.println("[OrderDAO] getItemStatus lỗi: " + e.getMessage());
        }
        return Order.OrderItem.ItemStatus.PENDING;
    }

    // ─── PAYMENT REQUESTED COUNT ──────────────────────────────────────────────

    public int getPaymentRequestedCount(long restaurantId) {
        String sql = isSuperAdmin()
            ? "SELECT COUNT(*) FROM orders WHERE status='PAYMENT_REQUESTED'"
            : "SELECT COUNT(*) FROM orders WHERE status='PAYMENT_REQUESTED' AND restaurant_id=?";
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
            Order o = new Order(String.valueOf(orderId), tableId, null, 0,
                                Order.Status.PENDING, "", customerName, customerPhone);
            o.setItems(new java.util.ArrayList<>());
            return o;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo đơn rỗng: " + e.getMessage(), e);
        }
    }

    // ─── ADD ORDER ITEMS ──────────────────────────────────────────────────────

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
            System.err.println("[OrderDAO] addOrderItems lỗi: " + e.getMessage());
            return false;
        }
    }

    // ─── GET ITEMS WITH STATUS ────────────────────────────────────────────────

    public List<Order.OrderItem> getItemsWithStatus(String orderId) {
        List<Order.OrderItem> list = new ArrayList<>();
        String sql = """
            SELECT oi.order_item_id, oi.menu_item_id, oi.quantity, oi.price,
                   oi.item_status, oi.round_number, mi.name AS item_name
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
                    // Gán orderItemId để tablet có thể hủy món riêng lẻ
                    item.setOrderItemId(String.valueOf(rs.getLong("order_item_id")));
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
              WHERE o.table_id = ? AND o.status NOT IN ('COMPLETED','CANCELLED')
              ORDER BY o.created_at DESC FETCH FIRST 1 ROWS ONLY
              """
            : """
              SELECT o.order_id, o.status, o.total_amount, o.created_at,
                     o.customer_name, o.customer_phone,
                     t.table_number, t.table_id
              FROM orders o
              JOIN restaurant_tables t ON o.table_id = t.table_id
              WHERE o.table_id = ? AND o.restaurant_id = ?
                AND o.status NOT IN ('COMPLETED','CANCELLED')
              ORDER BY o.created_at DESC FETCH FIRST 1 ROWS ONLY
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

    public boolean completeOrder(String orderId, String paymentMethod) {
        String sql = isSuperAdmin()
            ? "UPDATE orders SET status='COMPLETED', completed_at=SYSTIMESTAMP, payment_method=? WHERE order_id=?"
            : "UPDATE orders SET status='COMPLETED', completed_at=SYSTIMESTAMP, payment_method=? WHERE order_id=? AND restaurant_id=?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, paymentMethod);
            ps.setLong(2, parseLongOrDefault(orderId, 0));
            if (!isSuperAdmin()) ps.setLong(3, rid());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[OrderDAO] completeOrder lỗi: " + e.getMessage());
            return false;
        }
    }

    public boolean completeOrder(String orderId) { return completeOrder(orderId, null); }

    // ─── REQUEST PAYMENT ──────────────────────────────────────────────────────

    public boolean requestPayment(String orderId, String paymentMethod) {
        String sql = isSuperAdmin()
            ? "UPDATE orders SET status='PAYMENT_REQUESTED', payment_method=? WHERE order_id=?"
            : "UPDATE orders SET status='PAYMENT_REQUESTED', payment_method=? WHERE order_id=? AND restaurant_id=?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, paymentMethod);
            ps.setLong(2, parseLongOrDefault(orderId, 0));
            if (!isSuperAdmin()) ps.setLong(3, rid());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[OrderDAO] requestPayment lỗi: " + e.getMessage());
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
            if (rows == 0) throw new SecurityException("[OrderDAO] updateStatus từ chối: order_id=" + id);
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
            if (rows == 0) throw new SecurityException("[OrderDAO] update từ chối: order_id=" + o.getId());
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi cập nhật đơn hàng: " + e.getMessage(), e);
        }
        return o;
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    /**
     * Xoá đơn hàng và toàn bộ order_items trong một transaction.
     * Sau khi xoá, bếp sẽ không còn thấy các món vì order_items bị xoá khỏi DB.
     * Caller (OrderController) có trách nhiệm broadcast WsTopic.KITCHEN sau khi xoá.
     */
    public void delete(String id) {
        String deleteOrderSql = isSuperAdmin()
            ? "DELETE FROM orders WHERE order_id = ?"
            : "DELETE FROM orders WHERE order_id = ? AND restaurant_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Xoá items trước (FK constraint)
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM order_items WHERE order_id = ?")) {
                    ps.setLong(1, Long.parseLong(id));
                    ps.executeUpdate();
                }
                // Xoá đơn
                try (PreparedStatement ps = conn.prepareStatement(deleteOrderSql)) {
                    ps.setLong(1, Long.parseLong(id));
                    if (!isSuperAdmin()) ps.setLong(2, rid());
                    int rows = ps.executeUpdate();
                    if (rows == 0) throw new SecurityException("[OrderDAO] delete từ chối: order_id=" + id);
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
        Order o = new Order(
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
        o.setPaymentMethod(safeGetString(rs, "payment_method"));
        return o;
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
            case PENDING:            return "PENDING";
            case ACCEPTED:           return "ACCEPTED";
            case COOKING:            return "COOKING";
            case READY:              return "READY";
            case DELIVERING:         return "DELIVERING";
            case DELIVERED:          return "DELIVERED";
            case PAYMENT_REQUESTED:  return "PAYMENT_REQUESTED";
            case COMPLETED:          return "COMPLETED";
            case CANCELLED:          return "CANCELLED";
            case DANG_PHUC_VU:       return "PENDING";
            case HOAN_THANH:         return "COMPLETED";
            case DA_HUY:             return "CANCELLED";
            default:                 return "PENDING";
        }
    }

    private Order.Status fromDbStatus(String s) {
        if (s == null) return Order.Status.PENDING;
        switch (s) {
            case "PENDING":           return Order.Status.PENDING;
            case "ACCEPTED":
            case "CONFIRMED":         return Order.Status.ACCEPTED;
            case "COOKING":           return Order.Status.COOKING;
            case "READY":             return Order.Status.READY;
            case "DELIVERING":        return Order.Status.DELIVERING;
            case "DELIVERED":
            case "SERVED":            return Order.Status.DELIVERED;
            case "PAYMENT_REQUESTED": return Order.Status.PAYMENT_REQUESTED;
            case "COMPLETED":         return Order.Status.COMPLETED;
            case "CANCELLED":         return Order.Status.CANCELLED;
            case "IN_PROGRESS":
            default:                  return Order.Status.PENDING;
        }
    }

    private Order.OrderItem.ItemStatus fromDbItemStatus(String s) {
        if (s == null) return Order.OrderItem.ItemStatus.PENDING;
        switch (s) {
            case "ACCEPTED":   return Order.OrderItem.ItemStatus.ACCEPTED;
            case "COOKING":    return Order.OrderItem.ItemStatus.COOKING;
            case "READY":      return Order.OrderItem.ItemStatus.READY;
            case "DELIVERED":  return Order.OrderItem.ItemStatus.DELIVERED;
            case "CANCELLED":  return Order.OrderItem.ItemStatus.CANCELLED; // [FIX] không còn fall-through về PENDING
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