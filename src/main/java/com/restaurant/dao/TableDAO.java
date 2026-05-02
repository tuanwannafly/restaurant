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

public class TableDAO {

    private long    rid()          { return AppSession.getInstance().getRestaurantId(); }
    private boolean isSuperAdmin() { return RbacGuard.getInstance().isSuperAdmin(); }

    // ─── READ ─────────────────────────────────────────────────────────────────

    public List<TableItem> getAll() {
        List<TableItem> list = new ArrayList<>();
        String sql = isSuperAdmin()
            ? "SELECT table_id, table_number, capacity, status FROM restaurant_tables ORDER BY table_number"
            : "SELECT table_id, table_number, capacity, status FROM restaurant_tables WHERE restaurant_id = ? ORDER BY table_number";
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

    // ─── FIND BY ID ──────────────────────────────────────────────────────────

    public TableItem findById(String tableId, long restaurantId) {
        String sql = """
            SELECT table_id, table_number, capacity, status
            FROM restaurant_tables
            WHERE table_id = ? AND restaurant_id = ?
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, Long.parseLong(tableId));
            ps.setLong(2, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (Exception e) {
            System.err.println("[TableDAO] findById lỗi: " + e.getMessage());
        }
        return null;
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
            ? "UPDATE restaurant_tables SET table_number = ?, capacity = ?, status = ? WHERE table_id = ?"
            : "UPDATE restaurant_tables SET table_number = ?, capacity = ?, status = ? WHERE table_id = ? AND restaurant_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getName());
            ps.setInt(2, t.getCapacity());
            ps.setString(3, toDbStatus(t.getStatus()));
            ps.setLong(4, Long.parseLong(t.getId()));
            if (!isSuperAdmin()) ps.setLong(5, rid());
            int rows = ps.executeUpdate();
            if (rows == 0) throw new SecurityException("[TableDAO] update từ chối: table_id=" + t.getId());
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi cập nhật bàn: " + e.getMessage(), e);
        }
        return t;
    }

    // ─── UPDATE STATUS ────────────────────────────────────────────────────────

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
            if (rows == 0) throw new SecurityException("[TableDAO] updateStatus từ chối: table_id=" + tableId);
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
            if (rows == 0) throw new SecurityException("[TableDAO] delete từ chối: table_id=" + id);
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

    private TableItem.Status fromDbStatus(String s) {
        if (s == null) return TableItem.Status.RANH;
        switch (s) {
            case "OCCUPIED":        return TableItem.Status.BAN;
            case "RESERVED":        return TableItem.Status.DAT_TRUOC;
            case "DIRTY":           return TableItem.Status.DIRTY;
            case "CLEANING":        return TableItem.Status.CLEANING;
            case "OUT_OF_SERVICE":  return TableItem.Status.DIRTY;
            case "AVAILABLE":
            default:                return TableItem.Status.RANH;
        }
    }
}