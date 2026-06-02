package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.restaurant.db.DBConnection;
import com.restaurant.model.Order;
import com.restaurant.model.TableItem;

/**
 * DAO phục vụ màn hình bếp (KitchenPanel) và phục vụ bàn (WaiterServicePanel).
 *
 * Phase 7D: bổ sung {@link #getPendingCount(long)} và {@link #getReadyCount(long)}
 * cho badge navigation.
 */
public class KitchenDAO {

    private static final Logger LOGGER = Logger.getLogger(KitchenDAO.class.getName());

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
        public final String                    assignedTo;
        public final LocalDateTime             createdAt;
        public final String                    note;
        public final String                    assignedEmployeeName;

        public KitchenTicket(String itemId, String orderId, String menuItemId,
                             String tableId, String tableName, int roundNumber,
                             String itemName, int quantity,
                             Order.OrderItem.ItemStatus itemStatus,
                             String assignedTo,
                             LocalDateTime createdAt,
                             String note,
                             String assignedEmployeeName) {
            this.itemId               = itemId;
            this.orderId              = orderId;
            this.menuItemId           = menuItemId;
            this.tableId              = tableId;
            this.tableName            = tableName;
            this.roundNumber          = roundNumber;
            this.itemName             = itemName;
            this.quantity             = quantity;
            this.itemStatus           = itemStatus;
            this.assignedTo           = assignedTo;
            this.createdAt            = createdAt;
            this.note                 = note;
            this.assignedEmployeeName = assignedEmployeeName;
        }

        // ── Accessor methods (Phase 7D – dùng trong test và badge logic) ────

        /** Trả về order_item_id (alias của {@code itemId}). */
        public String getOrderItemId()  { return itemId; }

        /** Record-style accessor – alias của {@link #getOrderItemId()}. */
        public String orderItemId()     { return itemId; }

        /** Trả về tên món. */
        public String getItemName()     { return itemName; }

        /** Record-style accessor – alias của {@link #getItemName()}. */
        public String itemName()        { return itemName; }

        /** Trả về trạng thái item hiện tại. */
        public Order.OrderItem.ItemStatus getItemStatus() { return itemStatus; }

        /** Trả về số thứ tự lượt gọi. */
        public int getRoundNumber()     { return roundNumber; }

        /** Trả về orderId của ticket này. */
        public String getOrderId()      { return orderId; }

        /** Trả về tableId của ticket này. */
        public String getTableId()      { return tableId; }

        /** Trả về tên bàn (table_number). */
        public String getTableName()    { return tableName; }
    }

    // ─── SQL ──────────────────────────────────────────────────────────────────

    /**
     * Lấy tickets PENDING/ACCEPTED chưa có đầu bếp nào nhận (assigned_to IS NULL).
     * Đây là danh sách hiển thị trong cột "Đang chờ" trên màn hình bếp.
     */
    private static final String SQL_PENDING_UNASSIGNED =
            "SELECT oi.order_item_id, oi.order_id, oi.menu_item_id, " +
            "       oi.quantity,      oi.item_status, oi.round_number, " +
            "       oi.created_at,    oi.assigned_to, " +
            "       mi.name AS item_name, t.table_number, t.table_id, " +
            "       NULL AS note, NULL AS assigned_employee_name " +
            "FROM   order_items oi " +
            "JOIN   orders           o  ON oi.order_id     = o.order_id " +
            "JOIN   restaurant_tables t  ON o.table_id      = t.table_id " +
            "JOIN   menu_items       mi  ON oi.menu_item_id = mi.item_id " +
            "WHERE  o.restaurant_id = ? " +
            "  AND  oi.item_status IN ('PENDING','ACCEPTED') " +
            "  AND  oi.assigned_to IS NULL " +
            "ORDER  BY t.table_number, oi.round_number, oi.created_at";

    /**
     * Lấy tickets COOKING thuộc về đúng đầu bếp đang đăng nhập (assigned_to = ?).
     * Chỉ hiện món của chính mình trong cột "Đang chế biến".
     */
    private static final String SQL_COOKING_BY_CHEF =
            "SELECT oi.order_item_id, oi.order_id, oi.menu_item_id, " +
            "       oi.quantity,      oi.item_status, oi.round_number, " +
            "       oi.created_at,    oi.assigned_to, " +
            "       mi.name AS item_name, t.table_number, t.table_id, " +
            "       NULL AS note, NULL AS assigned_employee_name " +
            "FROM   order_items oi " +
            "JOIN   orders           o  ON oi.order_id     = o.order_id " +
            "JOIN   restaurant_tables t  ON o.table_id      = t.table_id " +
            "JOIN   menu_items       mi  ON oi.menu_item_id = mi.item_id " +
            "WHERE  o.restaurant_id = ? " +
            "  AND  oi.item_status  = 'COOKING' " +
            "  AND  oi.assigned_to  = ? " +
            "ORDER  BY t.table_number, oi.round_number, oi.created_at";

    /**
     * Gán đầu bếp và chuyển trạng thái sang COOKING trong 1 câu UPDATE.
     * Điều kiện {@code AND item_status IN ('PENDING','ACCEPTED') AND assigned_to IS NULL}
     * đảm bảo chỉ update khi mon thực sự chưa ai nhận.
     */
    private static final String SQL_ASSIGN_AND_START =
            "UPDATE order_items " +
            "SET    item_status = 'COOKING', assigned_to = ? " +
            "WHERE  order_item_id = ? " +
            "  AND  item_status IN ('PENDING','ACCEPTED') " +
            "  AND  assigned_to IS NULL";

    /**
     * Trả món về PENDING và xóa assigned_to (đầu bếp bỏ nhận món).
     * Điều kiện {@code AND item_status = 'COOKING' AND assigned_to = ?}
     * đảm bảo chỉ đầu bếp đang giữ món mới có thể trả lại.
     */
    private static final String SQL_UNASSIGN_AND_RESET =
            "UPDATE order_items " +
            "SET    item_status = 'PENDING', assigned_to = NULL " +
            "WHERE  order_item_id = ? " +
            "  AND  item_status  = 'COOKING' " +
            "  AND  assigned_to  = ?";

    private static final String SQL_ACTIVE_TICKETS =
            "SELECT oi.order_item_id, oi.order_id, oi.menu_item_id, " +
            "       oi.quantity,      oi.item_status, oi.round_number, " +
            "       oi.created_at,    oi.assigned_to, " +
            "       mi.name AS item_name, t.table_number, t.table_id, " +
            "       NULL AS note, NULL AS assigned_employee_name " +
            "FROM   order_items oi " +
            "JOIN   orders           o  ON oi.order_id     = o.order_id " +
            "JOIN   restaurant_tables t  ON o.table_id      = t.table_id " +
            "JOIN   menu_items       mi  ON oi.menu_item_id = mi.item_id " +
            "WHERE  o.restaurant_id = ? " +
            "  AND  oi.item_status IN ('PENDING','ACCEPTED','COOKING') " +
            "ORDER  BY t.table_number, oi.round_number, oi.created_at";

    /**
     * Lấy các lượt (table_id, round_number) mà TẤT CẢ món đều ở trạng thái READY,
     * tức là toàn bộ lượt đó có thể mang ra phục vụ cùng lúc.
     *
     * <p><b>Logic HAVING COUNT:</b><br>
     * – {@code COUNT(*)} = tổng số items trong lượt (table_id, round_number).<br>
     * – {@code COUNT(CASE WHEN item_status = 'READY' THEN 1 END)} = số items đã READY.<br>
     * – Điều kiện {@code HAVING COUNT(*) = COUNT(CASE WHEN ... 'READY' ...)}
     *   chỉ giữ lại những lượt mà <em>mọi</em> item đều READY,
     *   loại bỏ lượt còn item đang PENDING / COOKING / ACCEPTED.<br>
     * – Subquery lọc thêm {@code IN ('PENDING','ACCEPTED','COOKING','READY')} để
     *   không đếm các item đã DELIVERED/CANCELLED vào tổng COUNT(*).
     */
    private static final String SQL_READY_BY_TABLE =
            "SELECT oi.order_item_id, oi.order_id, oi.menu_item_id, " +
            "       oi.quantity,      oi.item_status, oi.round_number, " +
            "       oi.created_at,    oi.assigned_to, " +
            "       mi.name AS item_name, t.table_number, t.table_id, " +
            "       NULL AS note, NULL AS assigned_employee_name " +
            "FROM   order_items oi " +
            "JOIN   orders           o  ON oi.order_id     = o.order_id " +
            "JOIN   restaurant_tables t  ON o.table_id      = t.table_id " +
            "JOIN   menu_items       mi  ON oi.menu_item_id = mi.item_id " +
            "WHERE  o.restaurant_id = ? " +
            "  AND  oi.item_status  = 'READY' " +
            "  AND  (o.table_id, oi.round_number) IN ( " +
            "           SELECT o2.table_id, oi2.round_number " +
            "           FROM   order_items oi2 " +
            "           JOIN   orders o2 ON oi2.order_id = o2.order_id " +
            "           WHERE  o2.restaurant_id = ? " +
            "             AND  oi2.item_status IN ('PENDING','ACCEPTED','COOKING','READY') " +
            "           GROUP  BY o2.table_id, oi2.round_number " +
            /* COUNT(*) = tổng items còn active trong lượt;
               COUNT(CASE WHEN 'READY') = số items đã xong.
               Chỉ trả về lượt khi hai con số bằng nhau → toàn bộ đã READY. */
            "           HAVING COUNT(*) = COUNT(CASE WHEN oi2.item_status = 'READY' THEN 1 END) " +
            "       ) " +
            "ORDER  BY t.table_number, oi.round_number, oi.created_at";

    private static final String SQL_DIRTY_TABLES =
            "SELECT table_id, table_number, capacity, status " +
            "FROM   restaurant_tables " +
            "WHERE  restaurant_id = ? " +
            "  AND  status IN ('DIRTY', 'CLEANING', 'OUT_OF_SERVICE') " +
            "ORDER  BY table_number";

    /**
     * UPDATE có điều kiện: chỉ cập nhật nếu item vẫn còn đúng trạng thái
     * mà đầu bếp nhìn thấy lúc bấm nút (expected_status).
     *
     * <p>Đây là phòng thủ tầng DB chống lost update:
     * <pre>
     *   Đầu bếp A: UPDATE ... SET item_status='READY' WHERE order_item_id=? AND item_status='COOKING'  → 1 row
     *   Đầu bếp B: UPDATE ... SET item_status='READY' WHERE order_item_id=? AND item_status='COOKING'  → 0 row (không còn COOKING)
     * </pre>
     * {@code updateCount == 0} có nghĩa là người khác đã cập nhật trước.
     */
    private static final String SQL_UPDATE_STATUS_CONDITIONAL =
            "UPDATE order_items SET item_status = ? " +
            "WHERE order_item_id = ? AND item_status = ?";

    // Kept for backward-compat callers that don't pass expectedStatus
    private static final String SQL_UPDATE_STATUS =
            "UPDATE order_items SET item_status = ? WHERE order_item_id = ?";

    private static final String SQL_CANCELLED_ITEMS =
            "SELECT oi.order_item_id, oi.order_id, oi.menu_item_id, " +
            "       oi.quantity,      oi.item_status, oi.round_number, " +
            "       oi.created_at,    oi.assigned_to, " +
            "       mi.name AS item_name, t.table_number, t.table_id, " +
            "       NULL AS note, NULL AS assigned_employee_name " +
            "FROM   order_items oi " +
            "JOIN   orders           o  ON oi.order_id     = o.order_id " +
            "JOIN   restaurant_tables t  ON o.table_id      = t.table_id " +
            "JOIN   menu_items       mi  ON oi.menu_item_id = mi.item_id " +
            "WHERE  o.restaurant_id = ? " +
            "  AND  o.status = 'CANCELLED' " +
            "  AND  TRUNC(o.created_at) = TRUNC(SYSDATE) " +
            "ORDER  BY oi.created_at DESC";

    // Phase 7D – badge count queries
    private static final String SQL_PENDING_COUNT =
            "SELECT COUNT(*) FROM order_items oi " +
            "JOIN orders o ON oi.order_id = o.order_id " +
            "WHERE o.restaurant_id = ? AND oi.item_status = 'PENDING'";

    private static final String SQL_READY_COUNT =
            "SELECT COUNT(*) FROM order_items oi " +
            "JOIN orders o ON oi.order_id = o.order_id " +
            "WHERE o.restaurant_id = ? AND oi.item_status = 'READY'";

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Lấy danh sách tất cả ticket đang active (PENDING / ACCEPTED / COOKING).
     */
    public List<KitchenTicket> getActiveTickets(long restaurantId) {
        List<KitchenTicket> list = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ACTIVE_TICKETS)) {
            ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapTicket(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "[KitchenDAO] getActiveTickets error – restaurantId=" + restaurantId, e);
        }
        return list;
    }

    /**
     * Phase 5 – Lấy các lượt bàn mà tất cả items đều READY.
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
            LOGGER.log(Level.SEVERE,
                    "[KitchenDAO] getReadyByTable error – restaurantId=" + restaurantId, e);
        }
        return result;
    }

    /**
     * Phase 5 – Lấy danh sách bàn cần dọn (DIRTY / CLEANING).
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
                    TableItem.Status status = "CLEANING".equalsIgnoreCase(rawSt)
                            ? TableItem.Status.CLEANING
                            : TableItem.Status.DIRTY;
                    list.add(new TableItem(id, name, capacity, status));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "[KitchenDAO] getDirtyTables error – restaurantId=" + restaurantId, e);
        }
        return list;
    }

    /**
     * Phase 5D – Lấy các order_items thuộc đơn CANCELLED trong ngày hôm nay.
     */
    public List<KitchenTicket> getCancelledItems(long restaurantId) {
        List<KitchenTicket> list = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_CANCELLED_ITEMS)) {
            ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapTicket(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "[KitchenDAO] getCancelledItems error – restaurantId=" + restaurantId, e);
        }
        return list;
    }

    /**
     * Cập nhật item_status của một order_item (không có kiểm tra trạng thái cũ).
     * Dùng cho các luồng không phải kitchen (e.g. admin override).
     *
     * @param itemId    order_item_id
     * @param newStatus trạng thái mới
     * @return true nếu update thành công (ít nhất 1 row bị ảnh hưởng)
     */
    public boolean updateItemStatus(String itemId, Order.OrderItem.ItemStatus newStatus) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {
            ps.setString(1, newStatus.name());
            ps.setString(2, itemId);
            return ps.executeUpdate() >= 1;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "[KitchenDAO] updateItemStatus error – itemId=" + itemId
                    + ", newStatus=" + newStatus, e);
            return false;
        }
    }

    // ─── Chef-scoped queries (assigned_to feature) ────────────────────────────

    /**
     * Lấy các tickets PENDING/ACCEPTED chưa có ai nhận ({@code assigned_to IS NULL}).
     * Dùng để render cột "Đang chờ" — chỉ hiện món chưa có đầu bếp nào claim.
     *
     * @param restaurantId ID nhà hàng
     * @return danh sách ticket chưa được gán, có thứ tự theo bàn → lượt → thời gian
     */
    public List<KitchenTicket> getPendingUnassigned(long restaurantId) {
        List<KitchenTicket> list = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_PENDING_UNASSIGNED)) {
            ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapTicket(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "[KitchenDAO] getPendingUnassigned error – restaurantId=" + restaurantId, e);
        }
        return list;
    }

    /**
     * Lấy các tickets COOKING thuộc về đúng đầu bếp ({@code assigned_to = employeeId}).
     * Dùng để render cột "Đang chế biến" — mỗi đầu bếp chỉ thấy món của mình.
     *
     * @param restaurantId ID nhà hàng
     * @param employeeId   user_id của đầu bếp đang đăng nhập
     * @return danh sách ticket COOKING thuộc về đầu bếp này
     */
    public List<KitchenTicket> getCookingByChef(long restaurantId, long employeeId) {
        List<KitchenTicket> list = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_COOKING_BY_CHEF)) {
            ps.setLong(1, restaurantId);
            ps.setString(2, String.valueOf(employeeId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapTicket(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "[KitchenDAO] getCookingByChef error – restaurantId=" + restaurantId
                    + ", employeeId=" + employeeId, e);
        }
        return list;
    }

    /**
     * Nguyên tử: gán đầu bếp {@code employeeId} và chuyển {@code itemId} sang COOKING.
     *
     * <p>Chỉ thành công nếu item vẫn còn PENDING/ACCEPTED VÀ {@code assigned_to IS NULL}
     * tại thời điểm ghi — đây chính là guard chống lost update tầng DB cho "nhận món".</p>
     *
     * @param itemId     order_item_id
     * @param employeeId user_id của đầu bếp nhận món
     * @return {@link UpdateResult#SUCCESS} nếu nhận được,
     *         {@link UpdateResult#ALREADY_CHANGED} nếu đầu bếp khác đã nhận trước,
     *         {@link UpdateResult#ERROR} nếu lỗi SQL
     */
    public UpdateResult assignAndStart(String itemId, long employeeId) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ASSIGN_AND_START)) {

            ps.setString(1, String.valueOf(employeeId));
            ps.setString(2, itemId);

            int rows = ps.executeUpdate();
            if (rows >= 1) {
                LOGGER.log(Level.FINE,
                    "[KitchenDAO] assignAndStart OK — itemId={0}, employeeId={1}",
                    new Object[]{itemId, employeeId});
                return UpdateResult.SUCCESS;
            } else {
                LOGGER.log(Level.INFO,
                    "[KitchenDAO] assignAndStart ALREADY_CHANGED — itemId={0}", itemId);
                return UpdateResult.ALREADY_CHANGED;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                "[KitchenDAO] assignAndStart ERROR — itemId=" + itemId, e);
            return UpdateResult.ERROR;
        }
    }

    /**
     * Trả món về PENDING và xóa {@code assigned_to} (đầu bếp bỏ nhận món).
     *
     * <p>Chỉ đầu bếp đang giữ món ({@code assigned_to = employeeId}) mới có thể trả lại.
     * Sau khi trả lại, món xuất hiện lại ở cột "Đang chờ" cho đầu bếp khác nhận.</p>
     *
     * @param itemId     order_item_id
     * @param employeeId user_id của đầu bếp muốn trả món
     * @return true nếu trả thành công (1 row bị ảnh hưởng)
     */
    public boolean unassignAndReset(String itemId, long employeeId) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UNASSIGN_AND_RESET)) {

            ps.setString(1, itemId);
            ps.setString(2, String.valueOf(employeeId));

            int rows = ps.executeUpdate();
            if (rows >= 1) {
                LOGGER.log(Level.FINE,
                    "[KitchenDAO] unassignAndReset OK — itemId={0}, employeeId={1}",
                    new Object[]{itemId, employeeId});
                return true;
            } else {
                LOGGER.log(Level.INFO,
                    "[KitchenDAO] unassignAndReset 0 rows — itemId={0} " +
                    "(mon khong con COOKING hoac khong thuoc chef nay)", itemId);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                "[KitchenDAO] unassignAndReset ERROR — itemId=" + itemId, e);
            return false;
        }
    }

    /**
     * Cập nhật item_status AN TOÀN: chỉ update nếu item vẫn còn ở
     * {@code expectedStatus} tại thời điểm ghi.
     *
     * <p>Phòng chống lost update khi 2 đầu bếp cùng bấm "Hoàn thành" một món:
     * <ul>
     *   <li>Đầu bếp A bấm trước → WHERE item_status='COOKING' khớp → 1 row → {@link UpdateResult#SUCCESS}</li>
     *   <li>Đầu bếp B bấm sau  → WHERE item_status='COOKING' không còn khớp → 0 row → {@link UpdateResult#ALREADY_CHANGED}</li>
     * </ul>
     *
     * @param itemId         order_item_id
     * @param expectedStatus trạng thái đầu bếp đang nhìn thấy (COOKING → READY, PENDING → COOKING, ...)
     * @param newStatus      trạng thái muốn chuyển sang
     * @return kết quả update (xem {@link UpdateResult})
     */
    public UpdateResult updateItemStatusSafe(String itemId,
                                             Order.OrderItem.ItemStatus expectedStatus,
                                             Order.OrderItem.ItemStatus newStatus) {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            // READ_COMMITTED đảm bảo chỉ đọc dữ liệu đã commit.
            // Kết hợp với conditional UPDATE (WHERE item_status = ?)
            // tạo thành optimistic locking an toàn mà không cần SERIALIZABLE.
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS_CONDITIONAL)) {
                ps.setString(1, newStatus.name());
                ps.setString(2, itemId);
                ps.setString(3, expectedStatus.name());

                int rows = ps.executeUpdate();

                if (rows >= 1) {
                    LOGGER.log(Level.FINE,
                        "[KitchenDAO] updateItemStatusSafe OK — itemId={0} {1}→{2}",
                        new Object[]{itemId, expectedStatus, newStatus});
                    return UpdateResult.SUCCESS;
                } else {
                    // 0 row: item không còn ở expectedStatus → đã bị người khác cập nhật
                    LOGGER.log(Level.INFO,
                        "[KitchenDAO] updateItemStatusSafe ALREADY_CHANGED — itemId={0} expected={1}",
                        new Object[]{itemId, expectedStatus});
                    return UpdateResult.ALREADY_CHANGED;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                "[KitchenDAO] updateItemStatusSafe ERROR — itemId=" + itemId, e);
            return UpdateResult.ERROR;
        }
    }

    /**
     * Kết quả của {@link #updateItemStatusSafe}.
     */
    public enum UpdateResult {
        /** Update thành công — row đã được ghi. */
        SUCCESS,
        /**
         * Không có row nào bị ảnh hưởng — item đã được đầu bếp khác cập nhật trước.
         * UI nên hiển thị thông báo "Món đã được xử lý bởi người khác".
         */
        ALREADY_CHANGED,
        /** Lỗi SQL hoặc kết nối DB. */
        ERROR
    }

    // ─── Phase 7D: Badge count methods ───────────────────────────────────────

    /**
     * Đếm số order_items đang ở trạng thái PENDING thuộc nhà hàng.
     * Dùng để cập nhật badge số đỏ trên nút Bếp.
     *
     * @param restaurantId ID nhà hàng
     * @return số items PENDING (≥ 0), 0 nếu lỗi
     */
    public int getPendingCount(long restaurantId) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_PENDING_COUNT)) {
            ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "[KitchenDAO] getPendingCount error – restaurantId=" + restaurantId, e);
        }
        return 0;
    }

    /**
     * Đếm số order_items đang ở trạng thái READY thuộc nhà hàng.
     * Dùng để cập nhật badge số đỏ trên nút Phục vụ.
     *
     * @param restaurantId ID nhà hàng
     * @return số items READY (≥ 0), 0 nếu lỗi
     */
    public int getReadyCount(long restaurantId) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_READY_COUNT)) {
            ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "[KitchenDAO] getReadyCount error – restaurantId=" + restaurantId, e);
        }
        return 0;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private KitchenTicket mapTicket(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = (ts != null) ? ts.toLocalDateTime() : LocalDateTime.now();
        String assignedTo   = rs.getString("assigned_to");
        String note         = rs.getString("note");
        String assignedName = rs.getString("assigned_employee_name");
        String rawStatus    = rs.getString("item_status");
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
                status,
                assignedTo,
                createdAt,
                note,
                assignedName
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