package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.restaurant.db.DBConnection;
import com.restaurant.model.TableItem;
import com.restaurant.session.AppSession;
import com.restaurant.session.RbacGuard;

/**
 * DAO thao tác bảng RESTAURANT_TABLES trong Oracle DB.
 *
 * Ánh xạ trạng thái (DB → FE):
 * <pre>
 *   AVAILABLE        → RANH
 *   OCCUPIED         → BAN
 *   RESERVED         → DAT_TRUOC
 *   DIRTY            → DIRTY
 *   CLEANING         → CLEANING
 *   OUT_OF_SERVICE   → DIRTY   (không phân biệt được, coi là cần dọn)
 * </pre>
 *
 * Ánh xạ ngược (FE → DB):
 * <pre>
 *   RANH      → AVAILABLE
 *   BAN       → OCCUPIED
 *   DAT_TRUOC → RESERVED
 *   DIRTY     → DIRTY
 *   CLEANING  → CLEANING
 * </pre>
 */
public class TableDAO {

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private long    rid()          { return AppSession.getInstance().getRestaurantId(); }
    private boolean isSuperAdmin() { return RbacGuard.getInstance().isSuperAdmin(); }

    // ─── READ ─────────────────────────────────────────────────────────────────

    public List<TableItem> getAll() {
        List<TableItem> list = new ArrayList<>();

        String sql = isSuperAdmin()
            ? """
              SELECT table_id, table_number, capacity, status
              FROM restaurant_tables
              ORDER BY table_number
              """
            : """
              SELECT table_id, table_number, capacity, status
              FROM restaurant_tables
              WHERE restaurant_id = ?
              ORDER BY table_number
              """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (!isSuperAdmin()) ps.setLong(1, rid());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (Exception e) {
            System.err.println("[TableDAO] getAll lỗi: " + e.getMessage());
        }
        return list;
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    public TableItem create(TableItem t) {
        String sql = """
            INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id, created_at)
            VALUES (?, ?, ?, ?, SYSTIMESTAMP)
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"table_id"})) {

            ps.setString(1, t.getName());
            ps.setInt(2, t.getCapacity());
            ps.setString(3, toDbStatus(t.getStatus()));
            ps.setLong(4, rid());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) t.setId(String.valueOf(keys.getLong(1)));
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo bàn: " + e.getMessage(), e);
        }
        return t;
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    public TableItem update(TableItem t) {
        String sql = isSuperAdmin()
            ? """
              UPDATE restaurant_tables
              SET table_number = ?, capacity = ?, status = ?
              WHERE table_id = ?
              """
            : """
              UPDATE restaurant_tables
              SET table_number = ?, capacity = ?, status = ?
              WHERE table_id = ? AND restaurant_id = ?
              """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, t.getName());
            ps.setInt(2, t.getCapacity());
            ps.setString(3, toDbStatus(t.getStatus()));
            ps.setLong(4, Long.parseLong(t.getId()));
            if (!isSuperAdmin()) ps.setLong(5, rid());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SecurityException(
                    "[TableDAO] update từ chối: table_id=" + t.getId() +
                    " không thuộc restaurant_id=" + rid());
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi cập nhật bàn: " + e.getMessage(), e);
        }
        return t;
    }

    // ─── UPDATE STATUS (Phase 2 – Thu ngân mở bàn) ───────────────────────────

    /**
     * Cập nhật trạng thái bàn theo {@code tableId}.
     *
     * <p>SUPER_ADMIN bỏ qua điều kiện {@code restaurant_id}; các role khác
     * chỉ cập nhật được bàn thuộc nhà hàng của phiên hiện tại.
     *
     * @param tableId   khoá chính bàn
     * @param newStatus trạng thái mới
     * @return {@code true} nếu cập nhật thành công (affected row > 0)
     * @throws RuntimeException nếu lỗi SQL hoặc bàn không thuộc nhà hàng này
     */
    public boolean updateStatus(String tableId, TableItem.Status newStatus) {
        String sql = isSuperAdmin()
            ? "UPDATE restaurant_tables SET status = ? WHERE table_id = ?"
            : "UPDATE restaurant_tables SET status = ? WHERE table_id = ? AND restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, toDbStatus(newStatus));
            ps.setLong(2, Long.parseLong(tableId));
            if (!isSuperAdmin()) ps.setLong(3, rid());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SecurityException(
                    "[TableDAO] updateStatus từ chối: table_id=" + tableId +
                    " không thuộc restaurant_id=" + rid());
            }
            return true;

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi cập nhật trạng thái bàn: " + e.getMessage(), e);
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    public void delete(String id) {
        String sql = isSuperAdmin()
            ? "DELETE FROM restaurant_tables WHERE table_id = ?"
            : "DELETE FROM restaurant_tables WHERE table_id = ? AND restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, Long.parseLong(id));
            if (!isSuperAdmin()) ps.setLong(2, rid());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SecurityException(
                    "[TableDAO] delete từ chối: table_id=" + id +
                    " không thuộc restaurant_id=" + rid());
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xóa bàn: " + e.getMessage(), e);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private TableItem map(ResultSet rs) throws SQLException {
        return new TableItem(
                String.valueOf(rs.getLong("table_id")),
                rs.getString("table_number"),
                rs.getInt("capacity"),
                fromDbStatus(rs.getString("status"))
        );
    }

    /**
     * FE TableItem.Status → DB string.
     * DIRTY → "DIRTY", CLEANING → "CLEANING" (viết đúng vào DB thay vì "AVAILABLE").
     */
    private String toDbStatus(TableItem.Status s) {
        if (s == null) return "AVAILABLE";
        switch (s) {
            case BAN:       return "OCCUPIED";
            case DAT_TRUOC: return "RESERVED";
            case DIRTY:     return "DIRTY";
            case CLEANING:  return "CLEANING";
            case RANH:
            default:        return "AVAILABLE";
        }
    }

    /**
     * DB string → FE TableItem.Status.
     * DIRTY → DIRTY, CLEANING → CLEANING, OUT_OF_SERVICE → DIRTY (phân biệt riêng biệt).
     */
    private TableItem.Status fromDbStatus(String s) {
        if (s == null) return TableItem.Status.RANH;
        switch (s) {
            case "OCCUPIED":        return TableItem.Status.BAN;
            case "RESERVED":        return TableItem.Status.DAT_TRUOC;
            case "DIRTY":           return TableItem.Status.DIRTY;
            case "CLEANING":        return TableItem.Status.CLEANING;
            case "OUT_OF_SERVICE":  return TableItem.Status.DIRTY;  // không phân biệt → cần dọn
            case "AVAILABLE":
            default:                return TableItem.Status.RANH;
        }
    }
}