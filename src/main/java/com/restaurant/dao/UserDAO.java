package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.mindrot.jbcrypt.BCrypt;

import com.restaurant.db.DBConnection;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;

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

    /**
     * Đăng ký tài khoản mới cho staff — chỉ RESTAURANT_ADMIN được gọi.
     *
     * Logic:
     * 1. Guard: RbacGuard.can(Permission.REGISTER_STAFF) — throw SecurityException nếu không có quyền
     * 2. Kiểm tra email trùng — throw IllegalArgumentException nếu đã tồn tại
     * 3. Validate roleName: RESTAURANT_ADMIN không được tạo role RESTAURANT_ADMIN hoặc SUPER_ADMIN
     * 4. Hash password bằng BCrypt
     * 5. INSERT INTO users
     * 6. Lấy generated user_id
     * 7. INSERT INTO employees
     * 8. Trả về user_id vừa tạo
     *
     * @param name         tên hiển thị
     * @param email        email đăng nhập (unique)
     * @param password     mật khẩu plain-text (sẽ được hash)
     * @param roleName     role của staff (WAITER / CHEF / CASHIER)
     * @param restaurantId nhà hàng mà tài khoản này thuộc về
     * @return user_id vừa được tạo
     * @throws SecurityException        nếu không có quyền hoặc cố tạo role cao hơn
     * @throws IllegalArgumentException nếu email đã tồn tại hoặc role không hợp lệ
     */
    public long registerStaff(String name, String email, String password,
                               String roleName, long restaurantId) {
        // 1. Guard
        if (!RbacGuard.getInstance().can(Permission.REGISTER_STAFF)) {
            throw new SecurityException("Không có quyền tạo tài khoản staff");
        }

        // 2. Kiểm tra email trùng
        if (emailExists(email)) {
            throw new IllegalArgumentException("Email đã tồn tại: " + email);
        }

        // 3. Validate roleName — RESTAURANT_ADMIN không được tạo role vượt quyền
        if (RbacGuard.getInstance().isRestaurantAdmin()) {
            String upper = roleName != null ? roleName.toUpperCase() : "";
            if (upper.equals("RESTAURANT_ADMIN") || upper.equals("ADMIN")
                    || upper.equals("QUAN_LY") || upper.equals("SUPER_ADMIN")) {
                throw new SecurityException(
                        "RESTAURANT_ADMIN không được tạo tài khoản với role: " + roleName);
            }
        }

        // Validate role hợp lệ (chỉ WAITER / CHEF / CASHIER và alias)
        String employeeRole = mapRoleToEmployeeRole(roleName);
        if (employeeRole == null) {
            throw new IllegalArgumentException("Role không hợp lệ: " + roleName
                    + ". Chỉ chấp nhận: WAITER, CHEF, CASHIER");
        }

        // 4. Hash password
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        // 5 & 6. INSERT users và lấy generated key
        String insertUserSql =
                "INSERT INTO users (name, email, password, role_id, restaurant_id, status)"
              + " VALUES (?, ?, ?, (SELECT id FROM roles WHERE name = ?), ?, 'ACTIVE')";

        long userId;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     insertUserSql, new String[]{"user_id"})) {

            ps.setString(1, name);
            ps.setString(2, email.trim().toLowerCase());
            ps.setString(3, hashedPassword);
            ps.setString(4, roleName.toUpperCase());
            ps.setLong  (5, restaurantId);

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RuntimeException("Không lấy được user_id sau INSERT");
                }
                userId = keys.getLong(1);
            }

        } catch (SecurityException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi tạo tài khoản staff: " + e.getMessage(), e);
        }

        // 7. INSERT employees
        String insertEmpSql =
                "INSERT INTO employees (name, phone, address, start_date, role, restaurant_id, user_id)"
              + " VALUES (?, '', '', SYSDATE, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(insertEmpSql)) {

            ps.setString(1, name);
            ps.setString(2, employeeRole);
            ps.setLong  (3, restaurantId);
            ps.setLong  (4, userId);

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi tạo employee record: " + e.getMessage(), e);
        }

        // 8. Trả về user_id
        return userId;
    }

    /**
     * Cập nhật thông tin cá nhân của người dùng (name, phone, address).
     * KHÔNG cho phép sửa: email, role, restaurant_id.
     *
     * Guard: userId phải == AppSession.getUserId() HOẶC là SUPER_ADMIN/RESTAURANT_ADMIN.
     *
     * @param userId  user_id cần cập nhật
     * @param name    tên mới
     * @param phone   SĐT mới
     * @param address địa chỉ mới
     * @throws SecurityException nếu userId != AppSession.getUserId() và không phải admin
     */
    public void updateOwnProfile(long userId, String name, String phone, String address) {
        // Guard
        AppSession session = AppSession.getInstance();
        RbacGuard guard = RbacGuard.getInstance();
        if (userId != session.getUserId() && !guard.isManagerOrAbove()) {
            throw new SecurityException("Chỉ được cập nhật thông tin cá nhân của chính mình");
        }

        // UPDATE users
        String updateUserSql = "UPDATE users SET name = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(updateUserSql)) {
            ps.setString(1, name);
            ps.setLong  (2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi cập nhật users: " + e.getMessage(), e);
        }

        // UPDATE employees
        String updateEmpSql =
                "UPDATE employees SET name = ?, phone = ?, address = ?"
              + " WHERE user_id = ? AND restaurant_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(updateEmpSql)) {
            ps.setString(1, name);
            ps.setString(2, phone  != null ? phone   : "");
            ps.setString(3, address != null ? address : "");
            ps.setLong  (4, userId);
            ps.setLong  (5, session.getRestaurantId());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi cập nhật employees: " + e.getMessage(), e);
        }
    }

    /**
     * Đổi mật khẩu cá nhân.
     * Guard: userId phải == AppSession.getUserId().
     * Logic: verify oldPassword với BCrypt trước, rồi update hash mới.
     *
     * @param userId      user_id cần đổi mật khẩu
     * @param oldPassword mật khẩu hiện tại (plain-text để verify)
     * @param newPassword mật khẩu mới (plain-text, sẽ được hash)
     * @throws SecurityException        nếu userId != AppSession.getUserId()
     * @throws IllegalArgumentException nếu oldPassword không đúng
     */
    public void changeOwnPassword(long userId, String oldPassword, String newPassword) {
        // Guard: chỉ được đổi password của chính mình
        if (userId != AppSession.getInstance().getUserId()) {
            throw new SecurityException("Chỉ được đổi mật khẩu của chính mình");
        }

        // Lấy hash hiện tại
        String selectSql = "SELECT password FROM users WHERE user_id = ?";
        String currentHash;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Không tìm thấy user id=" + userId);
                }
                currentHash = rs.getString("password");
            }
        } catch (IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi lấy mật khẩu hiện tại: " + e.getMessage(), e);
        }

        // Verify oldPassword
        if (currentHash == null || !BCrypt.checkpw(oldPassword, currentHash)) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng");
        }

        // Hash mật khẩu mới và update
        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        String updateSql = "UPDATE users SET password = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, newHash);
            ps.setLong  (2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi cập nhật mật khẩu: " + e.getMessage(), e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Map roleName (từ DB/UI) sang giá trị role trong bảng employees.
     * Trả về null nếu role không hợp lệ cho staff.
     */
    private String mapRoleToEmployeeRole(String roleName) {
        if (roleName == null) return null;
        return switch (roleName.toUpperCase()) {
            case "WAITER",  "PHUC_VU"  -> "PHUC_VU";
            case "CHEF",    "DAU_BEP"  -> "DAU_BEP";
            case "CASHIER", "THU_NGAN" -> "THU_NGAN";
            default                    -> null;
        };
    }
}

