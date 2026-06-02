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

    // ─── Auto-migration: recovered_at / recovery_note ────────────────────────

    private static volatile boolean _recoveryColChecked = false;

    private void ensureRecoveryColumns() {
        if (_recoveryColChecked) return;
        synchronized (OrderDAO.class) {
            if (_recoveryColChecked) return;
            try (java.sql.Connection conn = DBConnection.getInstance().getConnection();
                 java.sql.Statement  stmt = conn.createStatement()) {
                try { stmt.execute("ALTER TABLE orders ADD (recovered_at DATE)");
                      System.out.println("[OrderDAO] Đã thêm cột recovered_at vào bảng orders.");
                } catch (java.sql.SQLException e) {
                    if (!e.getMessage().contains("ORA-01430"))
                        System.err.println("[OrderDAO] ensureRecoveryColumns (recovered_at) lỗi: " + e.getMessage());
                }
                try { stmt.execute("ALTER TABLE orders ADD (recovery_note VARCHAR2(500))");
                      System.out.println("[OrderDAO] Đã thêm cột recovery_note vào bảng orders.");
                } catch (java.sql.SQLException e) {
                    if (!e.getMessage().contains("ORA-01430"))
                        System.err.println("[OrderDAO] ensureRecoveryColumns (recovery_note) lỗi: " + e.getMessage());
                }
            } catch (Exception e) {
                System.err.println("[OrderDAO] ensureRecoveryColumns lỗi kết nối: " + e.getMessage());
            } finally {
                _recoveryColChecked = true;
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
            // READ_COMMITTED: SELECT status bên dưới chỉ đọc dữ liệu đã COMMIT
            // → không bao giờ thấy trạng thái uncommitted của transaction khác
            //   (Oracle MVCC trả về snapshot của lần commit cuối cùng)
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
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

    // ─── RECOVER CANCELLED ORDER (PL/SQL-backed) ─────────────────────────────

    /**
     * Phục hồi đơn hàng từ trạng thái {@code CANCELLED} về {@code PENDING}.
     *
     * <p><b>Khi nào dùng:</b> Admin nhận ra đơn bị huỷ nhầm (nhân viên bấm nhầm,
     * sự cố mạng gây rollback sai) → cần kích hoạt lại đơn để bếp tiếp tục xử lý.</p>
     *
     * <p><b>Điều kiện:</b>
     * <ul>
     *   <li>Người dùng phải có quyền {@link com.restaurant.session.Permission#RECOVER_ORDER}
     *       (RESTAURANT_ADMIN hoặc SUPER_ADMIN).</li>
     *   <li>Order phải đang ở trạng thái {@code CANCELLED}
     *       (không áp dụng cho {@code COMPLETED}).</li>
     * </ul></p>
     *
     * <p><b>2-layer lock (giống completeOrderViaPLSQL):</b>
     * <ol>
     *   <li>Layer 1 – Redis: chặn race với luồng cancel/complete đồng thời.</li>
     *   <li>Layer 2 – Oracle PL/SQL {@code pkg_order.recover_order}: {@code FOR UPDATE}
     *       + atomic restore trong 1 transaction; xử lý {@code ORA-00060} bằng retry.</li>
     * </ol></p>
     *
     * <p><b>Oracle Flashback (tra cứu thủ công):</b>
     * <pre>{@code
     * -- Xem trạng thái order tại thời điểm trước khi cancel (thay timestamp tương ứng)
     * SELECT * FROM orders AS OF TIMESTAMP (SYSTIMESTAMP - INTERVAL '10' MINUTE)
     *  WHERE order_id = <id>;
     * }</pre></p>
     *
     * @param orderId order_id cần phục hồi
     * @param note    ghi chú lý do phục hồi (lưu vào {@code orders.recovery_note})
     * @return {@code true} nếu phục hồi thành công
     */
    public boolean recoverCancelledOrder(String orderId, String note) {
        ensureRecoveryColumns();

        // 1. Kiểm tra quyền RECOVER_ORDER
        if (!RbacGuard.getInstance().can(Permission.RECOVER_ORDER)) {
            System.err.println("[OrderDAO] recoverCancelledOrder từ chối: thiếu quyền RECOVER_ORDER, orderId=" + orderId);
            return false;
        }

        // 2. Layer 1 – Redis lock (cùng key với cancel/complete → mutual exclusion)
        com.restaurant.db.OrderLockService orderLock =
                com.restaurant.db.OrderLockService.getInstance();
        boolean redisLocked = orderLock.tryAcquire(orderId);
        if (!redisLocked) {
            System.err.println("[OrderDAO] recoverCancelledOrder từ chối: order đang bị lock, orderId=" + orderId);
            return false;
        }

        // 3. Layer 2 – Oracle PL/SQL pkg_order.recover_order
        final int MAX_RETRY = 2;
        int attempt = 0;
        try {
            while (attempt < MAX_RETRY) {
                attempt++;
                try (java.sql.Connection conn = DBConnection.getInstance().getConnection();
                     java.sql.CallableStatement cs = conn.prepareCall(
                             "{ CALL pkg_order.recover_order(?, ?, ?, ?) }")) {

                    cs.setLong(1, parseLongOrDefault(orderId, 0));
                    cs.setString(2, note != null ? note : "");
                    cs.setLong(3, AppSession.getInstance().isLoggedIn()
                                 ? AppSession.getInstance().getUserId() : 0L);
                    cs.registerOutParameter(4, java.sql.Types.VARCHAR);
                    cs.execute();

                    String result = cs.getString(4);
                    System.out.println("[OrderDAO] recoverCancelledOrder result=" + result
                            + " orderId=" + orderId + " attempt=" + attempt);

                    switch (result == null ? "ERROR" : result) {
                        case "OK":
                            AuditLogger.getInstance().logRecovery(orderId, note);
                            return true;
                        case "NOT_CANCELLED":
                            AuditLogger.getInstance().logRecoveryFailed(orderId, "order không ở trạng thái CANCELLED");
                            System.err.println("[OrderDAO] recoverCancelledOrder: order không bị CANCELLED, orderId=" + orderId);
                            return false;
                        case "COMPLETED_CANT_RECOVER":
                            AuditLogger.getInstance().logRecoveryFailed(orderId, "order đã COMPLETED — không thể phục hồi");
                            System.err.println("[OrderDAO] recoverCancelledOrder: order đã COMPLETED, không thể phục hồi, orderId=" + orderId);
                            return false;
                        case "NOT_FOUND":
                            AuditLogger.getInstance().logRecoveryFailed(orderId, "order không tìm thấy");
                            System.err.println("[OrderDAO] recoverCancelledOrder: không tìm thấy orderId=" + orderId);
                            return false;
                        case "DEADLOCK":
                            System.err.println("[OrderDAO] recoverCancelledOrder: deadlock, retry " + attempt + "/" + MAX_RETRY);
                            if (attempt < MAX_RETRY) {
                                try { Thread.sleep(200L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            }
                            break;
                        default:
                            AuditLogger.getInstance().logRecoveryFailed(orderId, result);
                            System.err.println("[OrderDAO] recoverCancelledOrder lỗi PL/SQL: " + result);
                            return false;
                    }
                }
            }
            System.err.println("[OrderDAO] recoverCancelledOrder: vẫn deadlock sau " + MAX_RETRY + " lần retry, orderId=" + orderId);
            return false;
        } catch (Exception e) {
            System.err.println("[OrderDAO] recoverCancelledOrder lỗi: " + e.getMessage());
            return false;
        } finally {
            orderLock.release(orderId);
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
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
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

    /**
     * Thêm danh sách món vào đơn hàng đang mở (PENDING / ACCEPTED / COOKING / READY).
     *
     * <p><b>Phantom Read fix – chiến lược 2 lớp:</b>
     * <ol>
     *   <li><b>Tầng Application (Redis)</b>: {@link com.restaurant.db.OrderLockService#tryAcquire}
     *       với key {@code order:lock:{orderId}} → chặn 2 luồng đồng thời thao tác cùng đơn.
     *       Nếu Redis down, fallback ConcurrentHashMap tự động.</li>
     *   <li><b>Tầng DB (Oracle safety-net)</b>: {@code SELECT ... FOR UPDATE} trên hàng {@code orders}
     *       → bất kỳ transaction nào (kể cả cashier đang thanh toán) cũng phải lock hàng này trước
     *       → T2 INSERT bị BLOCK đến khi T1 thanh toán COMMIT xong → không có Phantom Row nào lọt vào.</li>
     * </ol>
     *
     * @param orderId     order_id của đơn hàng đang mở
     * @param entries     danh sách món cần thêm
     * @param roundNumber số thứ tự lượt gọi món
     * @return {@code true} nếu thêm thành công; {@code false} nếu đơn đã đóng, bị lock, hoặc lỗi DB
     */
    public boolean addOrderItems(String orderId, List<CartEntry> entries, int roundNumber) {
        if (entries == null || entries.isEmpty()) return false;

        // ── Tầng Application: Redis lock phòng Phantom Read ──────────────────
        // Ngăn waiter/tablet thêm món trong khi cashier đang kiểm tra và thanh toán.
        // Nếu Redis down → fallback in-memory → DB layer vẫn là safety-net cuối cùng.
        com.restaurant.db.OrderLockService orderLock =
                com.restaurant.db.OrderLockService.getInstance();
        boolean redisLocked = orderLock.tryAcquire(orderId);
        if (!redisLocked) {
            // Lock đang bị giữ bởi luồng khác (waiter/cashier) → từ chối thêm món ngay
            System.err.println("[OrderDAO] addOrderItems từ chối: order đang bị lock (Redis/local), orderId=" + orderId);
            return false;
        }

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
        // ── Tầng DB: Oracle FOR UPDATE — safety-net khi Redis bị bypass ──────
        // Lock hàng orders trước khi INSERT items → cashier cũng lock hàng này
        // → hai luồng không thể đồng thời thao tác items của cùng 1 order.
        String lockSql = isSuperAdmin()
            ? "SELECT order_id FROM orders WHERE order_id = ? AND status NOT IN ('COMPLETED','CANCELLED') FOR UPDATE"
            : "SELECT order_id FROM orders WHERE order_id = ? AND restaurant_id = ? AND status NOT IN ('COMPLETED','CANCELLED') FOR UPDATE";

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conn.setAutoCommit(false);
            try {
                // 1. Lock hàng orders (safety-net DB layer)
                try (PreparedStatement lockPs = conn.prepareStatement(lockSql)) {
                    lockPs.setLong(1, parseLongOrDefault(orderId, 0));
                    if (!isSuperAdmin()) lockPs.setLong(2, rid());
                    try (java.sql.ResultSet lockRs = lockPs.executeQuery()) {
                        if (!lockRs.next()) {
                            // Order không tồn tại hoặc đã COMPLETED/CANCELLED
                            conn.rollback();
                            System.err.println("[OrderDAO] addOrderItems từ chối: order không tồn tại " +
                                "hoặc đã đóng (COMPLETED/CANCELLED), orderId=" + orderId);
                            return false;
                        }
                    }
                }

                // 2. INSERT items — an toàn vì đã hold lock trên order row
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

                // 3. Cập nhật tổng tiền
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
        } finally {
            // Luôn giải phóng Redis lock dù thành công hay thất bại
            orderLock.release(orderId);
        }
    }

    // ─── COMPLETE ORDER SAFE (Phantom-Read-Safe) ──────────────────────────────

    /**
     * Thanh toán đơn hàng an toàn: lock order trước, kiểm tra không còn món PENDING,
     * rồi mới đánh dấu COMPLETED – tất cả trong 1 transaction.
     *
     * <p><b>Vấn đề Phantom Read cần giải quyết:</b>
     * <pre>
     *   T1 (Cashier):  đọc items → 0 PENDING → quyết định complete
     *   T2 (Waiter) :  INSERT món mới (PENDING) → COMMIT   ← Phantom Row
     *   T1 (Cashier):  complete → đơn COMPLETED nhưng có món PENDING bị bỏ sót!
     * </pre>
     *
     * <p><b>Fix 2 lớp:</b>
     * <ol>
     *   <li>Redis lock (Application): ngăn waiter thêm món trong khi cashier đang thanh toán.</li>
     *   <li>Oracle FOR UPDATE (DB): serialise access tại DB layer → dù Redis down, không bao giờ
     *       có phantom items lọt vào giữa "đọc" và "complete".</li>
     * </ol>
     *
     * @param orderId       order_id cần thanh toán
     * @param paymentMethod phương thức thanh toán
     * @return {@code true} nếu thanh toán thành công;
     *         {@code false} nếu còn món chưa xong, order đang bị lock, hoặc lỗi
     */
    public boolean completeOrderSafe(String orderId, String paymentMethod) {
        // ── Tầng Application: Redis lock ──────────────────────────────────────
        com.restaurant.db.OrderLockService orderLock =
                com.restaurant.db.OrderLockService.getInstance();
        boolean redisLocked = orderLock.tryAcquire(orderId);
        if (!redisLocked) {
            System.err.println("[OrderDAO] completeOrderSafe từ chối: order đang bị lock, orderId=" + orderId);
            return false;
        }

        // ── Tầng DB: Oracle FOR UPDATE + check + complete trong 1 transaction ──
        String lockSql = isSuperAdmin()
            ? "SELECT order_id, status FROM orders WHERE order_id = ? FOR UPDATE"
            : "SELECT order_id, status FROM orders WHERE order_id = ? AND restaurant_id = ? FOR UPDATE";
        String checkPendingSql =
            "SELECT COUNT(*) FROM order_items " +
            "WHERE order_id = ? AND item_status IN ('PENDING','ACCEPTED','COOKING','READY')";
        String completeSql = isSuperAdmin()
            ? "UPDATE orders SET status='COMPLETED', completed_at=SYSTIMESTAMP, payment_method=? WHERE order_id=?"
            : "UPDATE orders SET status='COMPLETED', completed_at=SYSTIMESTAMP, payment_method=? WHERE order_id=? AND restaurant_id=?";

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conn.setAutoCommit(false);
            try {
                // 1. Lock hàng orders — waiter nếu gọi addOrderItems sẽ bị BLOCK tại đây
                String currentStatus;
                try (PreparedStatement lockPs = conn.prepareStatement(lockSql)) {
                    lockPs.setLong(1, parseLongOrDefault(orderId, 0));
                    if (!isSuperAdmin()) lockPs.setLong(2, rid());
                    try (java.sql.ResultSet rs = lockPs.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            System.err.println("[OrderDAO] completeOrderSafe: order không tìm thấy, orderId=" + orderId);
                            return false;
                        }
                        currentStatus = rs.getString("status");
                    }
                }

                if ("COMPLETED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
                    conn.rollback();
                    System.err.println("[OrderDAO] completeOrderSafe từ chối: order đã " + currentStatus);
                    return false;
                }

                // 2. Sau khi lock — đọc số món còn đang xử lý
                //    Đây là lần đọc PHANTOM-FREE vì bất kỳ INSERT mới nào cũng bị block
                //    bởi FOR UPDATE ở trên (addOrderItems cũng cần lock hàng orders trước)
                int pendingCount;
                try (PreparedStatement checkPs = conn.prepareStatement(checkPendingSql)) {
                    checkPs.setLong(1, parseLongOrDefault(orderId, 0));
                    try (java.sql.ResultSet rs = checkPs.executeQuery()) {
                        pendingCount = rs.next() ? rs.getInt(1) : 0;
                    }
                }

                if (pendingCount > 0) {
                    conn.rollback();
                    System.out.println("[OrderDAO] completeOrderSafe: còn " + pendingCount +
                        " món chưa hoàn thành, không thể complete, orderId=" + orderId);
                    return false;
                }

                // 3. Hoàn tất đơn — an toàn, không Phantom
                try (PreparedStatement completePs = conn.prepareStatement(completeSql)) {
                    completePs.setString(1, paymentMethod);
                    completePs.setLong(2, parseLongOrDefault(orderId, 0));
                    if (!isSuperAdmin()) completePs.setLong(3, rid());
                    completePs.executeUpdate();
                }

                conn.commit();
                AuditLogger.getInstance().log("COMPLETE_ORDER_SAFE", parseLongOrDefault(orderId, 0),
                    "SUCCESS", "orderId=" + orderId + " payment=" + paymentMethod);
                return true;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            System.err.println("[OrderDAO] completeOrderSafe lỗi: " + e.getMessage());
            return false;
        } finally {
            orderLock.release(orderId);
        }
    }

    /**
     * Yêu cầu thanh toán an toàn – tương tự {@link #completeOrderSafe} nhưng chuyển sang
     * trạng thái {@code PAYMENT_REQUESTED} thay vì {@code COMPLETED}.
     *
     * <p>Lock 2 lớp (Redis + Oracle FOR UPDATE) ngăn waiter thêm món trong khi khách
     * đang yêu cầu thanh toán.</p>
     */
    public boolean requestPaymentSafe(String orderId, String paymentMethod) {
        com.restaurant.db.OrderLockService orderLock =
                com.restaurant.db.OrderLockService.getInstance();
        boolean redisLocked = orderLock.tryAcquire(orderId);
        if (!redisLocked) {
            System.err.println("[OrderDAO] requestPaymentSafe từ chối: order đang bị lock, orderId=" + orderId);
            return false;
        }

        String lockSql = isSuperAdmin()
            ? "SELECT order_id FROM orders WHERE order_id = ? FOR UPDATE"
            : "SELECT order_id FROM orders WHERE order_id = ? AND restaurant_id = ? FOR UPDATE";
        String updateSql = isSuperAdmin()
            ? "UPDATE orders SET status='PAYMENT_REQUESTED', payment_method=? WHERE order_id=?"
            : "UPDATE orders SET status='PAYMENT_REQUESTED', payment_method=? WHERE order_id=? AND restaurant_id=?";

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conn.setAutoCommit(false);
            try {
                // Lock order row — waiter bị block tại addOrderItems
                try (PreparedStatement lockPs = conn.prepareStatement(lockSql)) {
                    lockPs.setLong(1, parseLongOrDefault(orderId, 0));
                    if (!isSuperAdmin()) lockPs.setLong(2, rid());
                    try (java.sql.ResultSet rs = lockPs.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, paymentMethod);
                    ps.setLong(2, parseLongOrDefault(orderId, 0));
                    if (!isSuperAdmin()) ps.setLong(3, rid());
                    boolean ok = ps.executeUpdate() > 0;
                    if (ok) conn.commit(); else conn.rollback();
                    return ok;
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            System.err.println("[OrderDAO] requestPaymentSafe lỗi: " + e.getMessage());
            return false;
        } finally {
            orderLock.release(orderId);
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

    // ─── COMPLETE ORDER VIA PL/SQL (Phantom-Read-Safe + ORA-00060 handler) ──────

    /**
     * Thanh toán đơn hàng qua Oracle PL/SQL package {@code pkg_order.complete_order_safe}.
     *
     * <p><b>2-layer lock:</b>
     * <ol>
     *   <li>Layer 1 – Redis {@link com.restaurant.db.OrderLockService}: chặn race ngay tầng Application.</li>
     *   <li>Layer 2 – Oracle {@code FOR UPDATE} bên trong procedure: safety-net khi Redis down,
     *       đảm bảo không có Phantom Read và tự xử lý {@code ORA-00060} (deadlock) bằng retry.</li>
     * </ol>
     *
     * @param orderId       order_id cần thanh toán
     * @param paymentMethod phương thức thanh toán ("CASH" / "TRANSFER")
     * @return {@code true} nếu thanh toán thành công
     */
    public boolean completeOrderViaPLSQL(String orderId, String paymentMethod) {
        // ── Layer 1: Redis lock ──────────────────────────────────────────────
        com.restaurant.db.OrderLockService orderLock =
                com.restaurant.db.OrderLockService.getInstance();
        boolean redisLocked = orderLock.tryAcquire(orderId);
        if (!redisLocked) {
            System.err.println("[OrderDAO] completeOrderViaPLSQL từ chối: order đang bị lock, orderId=" + orderId);
            return false;
        }

        // ── Layer 2: Oracle PL/SQL pkg_order.complete_order_safe ────────────
        final int MAX_RETRY = 2;
        int attempt = 0;
        try {
            while (attempt < MAX_RETRY) {
                attempt++;
                try (java.sql.Connection conn = com.restaurant.db.DBConnection.getInstance().getConnection();
                     java.sql.CallableStatement cs = conn.prepareCall(
                             "{ CALL pkg_order.complete_order_safe(?, ?, ?) }")) {

                    cs.setLong(1, parseLongOrDefault(orderId, 0));
                    cs.setString(2, paymentMethod);
                    cs.registerOutParameter(3, java.sql.Types.VARCHAR);
                    cs.execute();

                    String result = cs.getString(3);
                    System.out.println("[OrderDAO] completeOrderViaPLSQL result=" + result
                            + " orderId=" + orderId + " attempt=" + attempt);

                    switch (result == null ? "ERROR" : result) {
                        case "OK":
                            AuditLogger.getInstance().log(
                                "COMPLETE_ORDER_PLSQL", parseLongOrDefault(orderId, 0),
                                "SUCCESS", "orderId=" + orderId + " payment=" + paymentMethod);
                            return true;
                        case "HAS_PENDING":
                            System.err.println("[OrderDAO] completeOrderViaPLSQL: còn món chưa xong, orderId=" + orderId);
                            return false;
                        case "ALREADY_CLOSED":
                            System.err.println("[OrderDAO] completeOrderViaPLSQL: đơn đã đóng, orderId=" + orderId);
                            return false;
                        case "NOT_FOUND":
                            System.err.println("[OrderDAO] completeOrderViaPLSQL: không tìm thấy đơn, orderId=" + orderId);
                            return false;
                        case "DEADLOCK":
                            // ORA-00060: Oracle đã rollback tự động — retry sau 200ms
                            System.err.println("[OrderDAO] completeOrderViaPLSQL: deadlock detected, retry " + attempt + "/" + MAX_RETRY);
                            if (attempt < MAX_RETRY) {
                                try { Thread.sleep(200L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            }
                            break; // tiếp tục vòng while
                        default:
                            System.err.println("[OrderDAO] completeOrderViaPLSQL lỗi PL/SQL: " + result);
                            return false;
                    }
                }
            }
            System.err.println("[OrderDAO] completeOrderViaPLSQL: vẫn deadlock sau " + MAX_RETRY + " lần retry, orderId=" + orderId);
            return false;
        } catch (Exception e) {
            System.err.println("[OrderDAO] completeOrderViaPLSQL lỗi: " + e.getMessage());
            return false;
        } finally {
            orderLock.release(orderId);
        }
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