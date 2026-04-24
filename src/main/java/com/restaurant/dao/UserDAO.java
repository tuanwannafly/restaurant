package com.restaurant.dao;

import com.restaurant.db.DBConnection;
import com.restaurant.session.AppSession;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * DAO xử lý xác thực và quản lý user.
 * Ánh xạ bảng USERS + ROLES trong Oracle DB.
 */
public class UserDAO {

    /**
     * Đăng nhập bằng email + password (BCrypt).
     * Trả về true và ghi vào AppSession nếu thành công.
     */
    public boolean login(String email, String password) {
        String sql = """
            SELECT u.user_id, u.name, u.email, u.password,
                   r.name  AS role_name,
                   u.restaurant_id
            FROM users u
            LEFT JOIN roles r ON u.role_id = r.id
            WHERE LOWER(u.email) = LOWER(?)
              AND u.status = 'ACTIVE'
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;

                String hash = rs.getString("password");
                if (hash == null || !BCrypt.checkpw(password, hash)) return false;

                long   userId       = rs.getLong("user_id");
                String name         = rs.getString("name");
                String roleName     = rs.getString("role_name");
                long   restaurantId = rs.getLong("restaurant_id");

                AppSession.getInstance().login(userId, name, email.trim(), roleName, restaurantId);
                return true;
            }
        } catch (Exception e) {
            System.err.println("[UserDAO] login lỗi: " + e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra email có tồn tại và active không (không kiểm tra password).
     * Dùng để hiện thông báo lỗi phù hợp.
     */
    public boolean emailExists(String email) {
        String sql = "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?) AND ROWNUM = 1";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
