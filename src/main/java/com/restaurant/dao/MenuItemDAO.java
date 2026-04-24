package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.restaurant.db.DBConnection;
import com.restaurant.model.MenuItem;
import com.restaurant.session.AppSession;
import com.restaurant.session.RbacGuard;

/**
 * DAO thao tác bảng MENU_ITEMS trong Oracle DB.
 *
 * Phase 2A: menu_items có cột restaurant_id trực tiếp — không join qua menus.
 * findOrCreateMenu vẫn giữ để tương thích với FK menu_id (nếu schema còn dùng).
 */
public class MenuItemDAO {

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private long rid() { return AppSession.getInstance().getRestaurantId(); }
    private boolean isSuperAdmin() { return RbacGuard.getInstance().isSuperAdmin(); }

    // ─── READ ─────────────────────────────────────────────────────────────────

    /** Lấy tất cả món của nhà hàng hiện tại, kèm tên category (= tên menu). */
    public List<MenuItem> getAll() {
        List<MenuItem> list = new ArrayList<>();

        String sql = isSuperAdmin()
            ? """
              SELECT mi.item_id, mi.name, mi.description, mi.price,
                     mi.image_url, mi.status, mn.name AS category
              FROM menu_items mi
              JOIN menus mn ON mi.menu_id = mn.menu_id
              ORDER BY mn.name, mi.name
              """
            : """
              SELECT mi.item_id, mi.name, mi.description, mi.price,
                     mi.image_url, mi.status, mn.name AS category
              FROM menu_items mi
              JOIN menus mn ON mi.menu_id = mn.menu_id
              WHERE mi.restaurant_id = ?
              ORDER BY mn.name, mi.name
              """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (!isSuperAdmin()) ps.setLong(1, rid());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (Exception e) {
            System.err.println("[MenuItemDAO] getAll lỗi: " + e.getMessage());
        }
        return list;
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    /** Tạo mới món ăn. Tự động tạo Menu nếu category chưa tồn tại. */
    public MenuItem create(MenuItem item) {
        long menuId = findOrCreateMenu(item.getCategory());
        if (menuId < 0) throw new RuntimeException("Không thể tạo menu category");

        String sql = """
            INSERT INTO menu_items (name, description, price, image_url, status, menu_id, restaurant_id)
            VALUES (?, ?, ?, ?, 'AVAILABLE', ?, ?)
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"item_id"})) {

            ps.setString(1, item.getName());
            ps.setString(2, item.getDescription());
            ps.setBigDecimal(3, java.math.BigDecimal.valueOf(item.getPrice()));
            ps.setString(4, item.getImageUrl());
            ps.setLong(5, menuId);
            ps.setLong(6, rid());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) item.setId(String.valueOf(keys.getLong(1)));
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo món: " + e.getMessage(), e);
        }
        return item;
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    public MenuItem update(MenuItem item) {
        long menuId = findOrCreateMenu(item.getCategory());
        if (menuId < 0) throw new RuntimeException("Không thể tìm menu category");

        String sql = isSuperAdmin()
            ? """
              UPDATE menu_items
              SET name = ?, description = ?, price = ?, image_url = ?, menu_id = ?
              WHERE item_id = ?
              """
            : """
              UPDATE menu_items
              SET name = ?, description = ?, price = ?, image_url = ?, menu_id = ?
              WHERE item_id = ? AND restaurant_id = ?
              """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, item.getName());
            ps.setString(2, item.getDescription());
            ps.setBigDecimal(3, java.math.BigDecimal.valueOf(item.getPrice()));
            ps.setString(4, item.getImageUrl());
            ps.setLong(5, menuId);
            ps.setLong(6, Long.parseLong(item.getId()));
            if (!isSuperAdmin()) ps.setLong(7, rid());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SecurityException(
                    "[MenuItemDAO] update từ chối: item_id=" + item.getId() +
                    " không thuộc restaurant_id=" + rid());
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi cập nhật món: " + e.getMessage(), e);
        }
        return item;
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    public void delete(String id) {
        String sql = isSuperAdmin()
            ? "DELETE FROM menu_items WHERE item_id = ?"
            : "DELETE FROM menu_items WHERE item_id = ? AND restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, Long.parseLong(id));
            if (!isSuperAdmin()) ps.setLong(2, rid());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SecurityException(
                    "[MenuItemDAO] delete từ chối: item_id=" + id +
                    " không thuộc restaurant_id=" + rid());
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xóa món: " + e.getMessage(), e);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private MenuItem map(ResultSet rs) throws SQLException {
        MenuItem m = new MenuItem(
                String.valueOf(rs.getLong("item_id")),
                rs.getString("name"),
                rs.getString("category") != null ? rs.getString("category") : "Chung",
                rs.getBigDecimal("price") != null ? rs.getBigDecimal("price").doubleValue() : 0,
                rs.getString("description") != null ? rs.getString("description") : ""
        );
        m.setImageUrl(rs.getString("image_url"));
        return m;
    }

    /**
     * Tìm menu_id theo tên category trong nhà hàng hiện tại.
     * Nếu chưa có → tạo mới menu đó.
     */
    private long findOrCreateMenu(String category) {
        if (category == null || category.isBlank()) category = "Chung";

        String findSql = "SELECT menu_id FROM menus WHERE LOWER(name) = LOWER(?) AND restaurant_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(findSql)) {
                ps.setString(1, category);
                ps.setLong(2, rid());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }

            // Tạo menu mới
            String createSql = """
                INSERT INTO menus (name, description, status, restaurant_id)
                VALUES (?, ?, 'ACTIVE', ?)
                """;
            try (PreparedStatement ps = conn.prepareStatement(createSql, new String[]{"menu_id"})) {
                ps.setString(1, category);
                ps.setString(2, category);
                ps.setLong(3, rid());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                }
            }
        } catch (Exception e) {
            System.err.println("[MenuItemDAO] findOrCreateMenu lỗi: " + e.getMessage());
        }
        return -1;
    }
}