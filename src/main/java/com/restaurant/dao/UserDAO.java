package com.restaurant.dao;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.mindrot.jbcrypt.BCrypt;

import com.restaurant.db.DBConnection;
import com.restaurant.session.AppSession;
import com.restaurant.session.AuditLogger;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;
import com.restaurant.session.TokenService;

/**
 * DAO xử lý xác thực và quản lý user.
 * Ánh xạ bảng USERS + ROLES trong Oracle DB.
 */
public class UserDAO {

    // ── Inner model ───────────────────────────────────────────────────────────

    /**
     * Thông tin tóm tắt của một user RESTAURANT_ADMIN,
     * dùng để hiển thị trong danh sách chọn admin khi tạo nhà hàng.
     */
    public static class AdminUser {
        private final long   userId;
        private final String name;
        private final String email;
        private final long   restaurantId; // 0 = chưa gán nhà hàng

        public AdminUser(long userId, String name, String email, long restaurantId) {
            this.userId       = userId;
            this.name         = name;
            this.email        = email;
            this.restaurantId = restaurantId;
        }

        public long   getUserId()       { return userId; }
        public String getName()         { return name; }
        public String getEmail()        { return email; }
        public long   getRestaurantId() { return restaurantId; }

        /** Nhãn hiển thị trong JComboBox. */
        @Override
        public String toString() {
            if (restaurantId == 0) {
                return name + " <" + email + ">  [Chưa gán nhà hàng]";
            }
            return name + " <" + email + ">  [Đang quản lý nhà hàng #" + restaurantId + "]";
        }
    }

    // ── Guard ─────────────────────────────────────────────────────────────────

    private void requireSuperAdmin() {
        if (!RbacGuard.getInstance().isSuperAdmin()) {
            throw new SecurityException("Chỉ SUPER_ADMIN được phép thực hiện thao tác này");
        }
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Đăng nhập bằng email + password (BCrypt).
     * Trả về true và ghi vào AppSession nếu thành công.
     */
    public boolean login(String email, String password) {
        AuditLogger audit = AuditLogger.getInstance();

        // Phase 5 — Brute-force: kiểm tra khoá trước khi truy vấn DB
        if (audit.isAccountLocked(email.trim())) {
            System.err.println("[UserDAO] Tài khoản bị khoá tạm thời: " + email);
            return false;
        }

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
                if (!rs.next()) {
                    // Email không tồn tại → ghi FAIL, không lộ thông tin
                    audit.logLogin(email.trim(), 0L, false);
                    return false;
                }

                long   userId       = rs.getLong("user_id");
                String name         = rs.getString("name");
                String roleName     = rs.getString("role_name");
                long   restaurantId = rs.getLong("restaurant_id");
                String hash         = rs.getString("password");

                if (hash == null || !BCrypt.checkpw(password, hash)) {
                    // Mật khẩu sai → ghi FAIL và kiểm tra ngưỡng brute-force
                    audit.logLogin(email.trim(), userId, false);

                    int failures = audit.countRecentLoginFailures(email.trim());
                    if (failures >= 5) {
                        audit.logAccountLocked(email.trim(), userId);
                        System.err.println("[UserDAO] Khoá tài khoản 15 phút: " + email);
                    }
                    return false;
                }

                // Đăng nhập thành công → ghi vào AppSession
                AppSession.getInstance().login(userId, name, email.trim(), roleName, restaurantId);

                // Phase 6: Sinh session token và lưu vào AppSession
                try {
                    String token = TokenService.getInstance().generateSessionToken(userId);
                    AppSession.getInstance().setSessionToken(token);
                } catch (Exception tokenEx) {
                    System.err.println("[UserDAO] Cảnh báo: không tạo được session token: "
                            + tokenEx.getMessage());
                }

                // Ghi audit log LOGIN SUCCESS (sau khi có session)
                audit.logLogin(email.trim(), userId, true);
                return true;
            }
        } catch (Exception e) {
            System.err.println("[UserDAO] login lỗi: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đăng nhập theo user_id — dùng cho silent re-auth sau khi refresh token hợp lệ.
     * Truy vấn DB lấy thông tin user rồi load vào {@link com.restaurant.session.AppSession}.
     *
     * @param userId user_id của user cần load session
     * @return {@code true} nếu user tồn tại và ACTIVE; {@code false} nếu không tìm thấy
     */
    public boolean loginByUserId(long userId) {
        String sql = """
            SELECT u.user_id, u.name, u.email,
                   r.name AS role_name,
                   u.restaurant_id
            FROM   users u
            LEFT JOIN roles r ON u.role_id = r.id
            WHERE  u.user_id = ?
              AND  u.status  = 'ACTIVE'
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;

                String name         = rs.getString("name");
                String email        = rs.getString("email");
                String roleName     = rs.getString("role_name");
                long   restaurantId = rs.getLong("restaurant_id");

                com.restaurant.session.AppSession.getInstance()
                        .login(userId, name, email, roleName, restaurantId);

                // Sinh session token mới cho phiên vừa restore
                try {
                    String token = com.restaurant.session.TokenService
                            .getInstance().generateSessionToken(userId);
                    com.restaurant.session.AppSession.getInstance().setSessionToken(token);
                } catch (Exception tokenEx) {
                    System.err.println("[UserDAO] Cảnh báo: không tạo session token khi silent login: "
                            + tokenEx.getMessage());
                }
                return true;
            }
        } catch (Exception e) {
            System.err.println("[UserDAO] loginByUserId lỗi: " + e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra email có tồn tại và active không (không kiểm tra password).
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

    // ── Admin management (SUPER_ADMIN only) ───────────────────────────────────

    /**
     * Lấy danh sách tất cả user có role RESTAURANT_ADMIN.
     * Dùng để hiển thị dropdown khi SUPER_ADMIN tạo / sửa nhà hàng.
     *
     * @return danh sách AdminUser, không bao giờ null
     * @throws SecurityException nếu không phải SUPER_ADMIN
     */
    public List<AdminUser> findRestaurantAdmins() {
        requireSuperAdmin();

        String sql = """
            SELECT u.user_id, u.name, u.email, u.restaurant_id
            FROM users u
            JOIN roles r ON u.role_id = r.id
            WHERE r.name = 'RESTAURANT_ADMIN'
              AND u.status = 'ACTIVE'
            ORDER BY u.name
            """;

        List<AdminUser> list = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new AdminUser(
                    rs.getLong("user_id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getLong("restaurant_id")   // 0 nếu NULL trong DB
                ));
            }

        } catch (Exception e) {
            System.err.println("[UserDAO] findRestaurantAdmins lỗi: " + e.getMessage());
            throw new RuntimeException("Lỗi tải danh sách admin: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Tạo tài khoản RESTAURANT_ADMIN mới — chỉ SUPER_ADMIN được gọi.
     *
     * <p>Các bước:
     * <ol>
     *   <li>Guard SUPER_ADMIN</li>
     *   <li>Kiểm tra email trùng</li>
     *   <li>Hash password (BCrypt)</li>
     *   <li>INSERT INTO users với role RESTAURANT_ADMIN</li>
     *   <li>INSERT INTO employees với role QUAN_LY (nếu có restaurantId)</li>
     * </ol>
     *
     * @param name         tên hiển thị
     * @param email        email đăng nhập (unique)
     * @param password     mật khẩu plain-text
     * @param restaurantId nhà hàng cần gán ngay (0 = chưa gán)
     * @return user_id vừa tạo
     * @throws SecurityException        nếu không phải SUPER_ADMIN
     * @throws IllegalArgumentException nếu email đã tồn tại
     */
    public long registerRestaurantAdmin(String name, String email,
                                        String password, long restaurantId) {
        requireSuperAdmin();

        if (emailExists(email)) {
            throw new IllegalArgumentException("Email đã tồn tại: " + email);
        }

        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        String insertUserSql =
                "INSERT INTO users (name, email, password, role_id, restaurant_id, status)"
              + " VALUES (?, ?, ?, (SELECT id FROM roles WHERE name = 'RESTAURANT_ADMIN'), ?, 'ACTIVE')";

        long userId;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     insertUserSql, new String[]{"user_id"})) {

            ps.setString(1, name);
            ps.setString(2, email.trim().toLowerCase());
            ps.setString(3, hashedPassword);
            if (restaurantId > 0) ps.setLong(4, restaurantId);
            else                  ps.setNull(4, java.sql.Types.NUMERIC);

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("Không lấy được user_id sau INSERT");
                userId = keys.getLong(1);
            }

        } catch (IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi tạo tài khoản admin: " + e.getMessage(), e);
        }

        // INSERT employees record nếu có restaurantId
        if (restaurantId > 0) {
            String insertEmpSql =
                    "INSERT INTO employees (name, phone, address, start_date, role, restaurant_id, user_id)"
                  + " VALUES (?, '', '', SYSDATE, 'QUAN_LY', ?, ?)";

            try (Connection conn = DBConnection.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(insertEmpSql)) {

                ps.setString(1, name);
                ps.setLong  (2, restaurantId);
                ps.setLong  (3, userId);
                ps.executeUpdate();

            } catch (Exception e) {
                throw new RuntimeException("[UserDAO] Lỗi tạo employee record cho admin mới: " + e.getMessage(), e);
            }
        }

        return userId;
    }

    /**
     * Gán nhà hàng cho một user RESTAURANT_ADMIN đã tồn tại.
     * Đồng thời tạo bản ghi employees nếu chưa có.
     *
     * @param userId       user_id của admin cần gán
     * @param restaurantId nhà hàng cần gán
     * @throws SecurityException nếu không phải SUPER_ADMIN
     */
    public void assignAdminToRestaurant(long userId, long restaurantId) {
        requireSuperAdmin();

        // Cập nhật restaurant_id trong bảng users
        String updateUserSql = "UPDATE users SET restaurant_id = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(updateUserSql)) {
            ps.setLong(1, restaurantId);
            ps.setLong(2, userId);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new RuntimeException("Không tìm thấy user id=" + userId);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi gán nhà hàng cho admin: " + e.getMessage(), e);
        }

        // Kiểm tra đã có employee record chưa
        String checkSql = "SELECT COUNT(*) FROM employees WHERE user_id = ? AND restaurant_id = ?";
        boolean empExists = false;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setLong(1, userId);
            ps.setLong(2, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) empExists = rs.getInt(1) > 0;
            }
        } catch (Exception ignored) {}

        if (!empExists) {
            // Lấy tên user để tạo employee record
            String adminName = "Admin";
            String nameSql = "SELECT name FROM users WHERE user_id = ?";
            try (Connection conn = DBConnection.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(nameSql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) adminName = rs.getString("name");
                }
            } catch (Exception ignored) {}

            String insertEmpSql =
                    "INSERT INTO employees (name, phone, address, start_date, role, restaurant_id, user_id)"
                  + " VALUES (?, '', '', SYSDATE, 'QUAN_LY', ?, ?)";
            try (Connection conn = DBConnection.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(insertEmpSql)) {
                ps.setString(1, adminName);
                ps.setLong  (2, restaurantId);
                ps.setLong  (3, userId);
                ps.executeUpdate();
            } catch (Exception e) {
                System.err.println("[UserDAO] Lỗi tạo employee cho admin đã có: " + e.getMessage());
            }
        }
    }

    // ── Staff management (RESTAURANT_ADMIN) ───────────────────────────────────

    /**
     * Đăng ký tài khoản mới cho staff — chỉ RESTAURANT_ADMIN được gọi.
     *
     * @param name         tên hiển thị
     * @param email        email đăng nhập (unique)
     * @param password     mật khẩu plain-text (sẽ được hash)
     * @param roleName     role của staff (WAITER / CHEF / CASHIER)
     * @param restaurantId nhà hàng mà tài khoản này thuộc về
     * @return user_id vừa được tạo
     */
    public long registerStaff(String name, String email, String password,
                               String roleName, long restaurantId) {
        if (!RbacGuard.getInstance().can(Permission.REGISTER_STAFF)) {
            throw new SecurityException("Không có quyền tạo tài khoản staff");
        }

        if (emailExists(email)) {
            throw new IllegalArgumentException("Email đã tồn tại: " + email);
        }

        if (RbacGuard.getInstance().isRestaurantAdmin()) {
            String upper = roleName != null ? roleName.toUpperCase() : "";
            if (upper.equals("RESTAURANT_ADMIN") || upper.equals("ADMIN")
                    || upper.equals("QUAN_LY") || upper.equals("SUPER_ADMIN")) {
                throw new SecurityException(
                        "RESTAURANT_ADMIN không được tạo tài khoản với role: " + roleName);
            }
        }

        String employeeRole = mapRoleToEmployeeRole(roleName);
        if (employeeRole == null) {
            throw new IllegalArgumentException("Role không hợp lệ: " + roleName
                    + ". Chỉ chấp nhận: WAITER, CHEF, CASHIER");
        }

        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

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
                if (!keys.next()) throw new RuntimeException("Không lấy được user_id sau INSERT");
                userId = keys.getLong(1);
            }

        } catch (SecurityException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi tạo tài khoản staff: " + e.getMessage(), e);
        }

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

        return userId;
    }

    // ── Profile management ────────────────────────────────────────────────────

    public void updateOwnProfile(long userId, String name, String phone, String address) {
        AppSession session = AppSession.getInstance();
        RbacGuard guard = RbacGuard.getInstance();
        if (userId != session.getUserId() && !guard.isManagerOrAbove()) {
            throw new SecurityException("Chỉ được cập nhật thông tin cá nhân của chính mình");
        }

        String updateUserSql = "UPDATE users SET name = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(updateUserSql)) {
            ps.setString(1, name);
            ps.setLong  (2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi cập nhật users: " + e.getMessage(), e);
        }

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

    public void changeOwnPassword(long userId, String oldPassword, String newPassword) {
        if (userId != AppSession.getInstance().getUserId()) {
            throw new SecurityException("Chỉ được đổi mật khẩu của chính mình");
        }

        String selectSql = "SELECT password FROM users WHERE user_id = ?";
        String currentHash;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy user id=" + userId);
                currentHash = rs.getString("password");
            }
        } catch (IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi lấy mật khẩu hiện tại: " + e.getMessage(), e);
        }

        if (currentHash == null || !BCrypt.checkpw(oldPassword, currentHash)) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng");
        }

        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        String updateSql = "UPDATE users SET password = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, newHash);
            ps.setLong  (2, userId);
            ps.executeUpdate();
            // Phase 5: Ghi audit log đổi mật khẩu
            AuditLogger.getInstance().logPasswordChange(userId);
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi cập nhật mật khẩu: " + e.getMessage(), e);
        }
    }

    // ── Password Reset Token ──────────────────────────────────────────────────

    /**
     * Tạo token đặt lại mật khẩu một lần, hết hạn sau 15 phút.
     *
     * <p>Sinh 32 byte ngẫu nhiên → hex-64 ký tự, INSERT vào bảng
     * {@code PASSWORD_RESET_TOKENS} với {@code expires_at = SYSTIMESTAMP + 15 phút}.
     *
     * @param email email của user cần đặt lại mật khẩu
     * @return token hex-64 ký tự, hoặc {@code null} nếu email không tồn tại
     * @throws RuntimeException nếu xảy ra lỗi DB
     */
    public String generatePasswordResetToken(String email) {
        // 1. Lấy user_id theo email
        String lookupSql = "SELECT user_id FROM users WHERE LOWER(email) = LOWER(?) AND status = 'ACTIVE'";
        long userId;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(lookupSql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;   // email không tồn tại / bị khoá
                userId = rs.getLong("user_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi tìm user theo email: " + e.getMessage(), e);
        }

        // 2. Sinh token hex-64
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        String token = sb.toString();

        // 3. INSERT token vào DB (expires_at = bây giờ + 15 phút)
        String insertSql =
                "INSERT INTO password_reset_tokens (token, user_id, expires_at) "
              + "VALUES (?, ?, SYSTIMESTAMP + INTERVAL '15' MINUTE)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, token);
            ps.setLong  (2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi lưu reset token: " + e.getMessage(), e);
        }

        return token;
    }

    /**
     * Kiểm tra token có hợp lệ không (tồn tại, {@code used=0}, chưa hết hạn).
     *
     * @param token token cần kiểm tra
     * @return {@code true} nếu hợp lệ
     */
    public boolean validateResetToken(String token) {
        if (token == null || token.isBlank()) return false;
        String sql = "SELECT 1 FROM password_reset_tokens "
                   + "WHERE token = ? AND used = 0 AND expires_at > SYSTIMESTAMP";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("[UserDAO] validateResetToken lỗi: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đặt lại mật khẩu bằng token một lần.
     *
     * <p>Thực hiện trong một transaction:
     * <ol>
     *   <li>Xác thực token (tồn tại, used=0, chưa hết hạn)</li>
     *   <li>Hash mật khẩu mới bằng BCrypt</li>
     *   <li>UPDATE {@code users.password}</li>
     *   <li>UPDATE {@code password_reset_tokens.used = 1} để huỷ token ngay lập tức</li>
     * </ol>
     *
     * @param token       token hợp lệ từ {@link #generatePasswordResetToken}
     * @param newPassword mật khẩu mới plain-text (chưa hash)
     * @return {@code true} nếu thành công, {@code false} nếu token không hợp lệ
     * @throws RuntimeException nếu lỗi DB
     */
    public boolean resetPasswordWithToken(String token, String newPassword) {
        if (token == null || token.isBlank()) return false;

        String findSql = "SELECT user_id FROM password_reset_tokens "
                       + "WHERE token = ? AND used = 0 AND expires_at > SYSTIMESTAMP";

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Lấy user_id và kiểm tra token
                long userId;
                try (PreparedStatement ps = conn.prepareStatement(findSql)) {
                    ps.setString(1, token.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;    // token không tồn tại / đã dùng / hết hạn
                        }
                        userId = rs.getLong("user_id");
                    }
                }

                // 2. Hash mật khẩu mới
                String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());

                // 3. Cập nhật mật khẩu
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET password = ? WHERE user_id = ?")) {
                    ps.setString(1, newHash);
                    ps.setLong  (2, userId);
                    ps.executeUpdate();
                }

                // 4. Đánh dấu token đã dùng — chống replay ngay lập tức
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE password_reset_tokens SET used = 1 WHERE token = ?")) {
                    ps.setString(1, token.trim());
                    ps.executeUpdate();
                }

                conn.commit();
                return true;

            } catch (Exception ex) {
                conn.rollback();
                throw new RuntimeException("[UserDAO] Lỗi resetPasswordWithToken: " + ex.getMessage(), ex);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi kết nối khi reset mật khẩu: " + e.getMessage(), e);
        }
    }

    /**
     * Đổi mật khẩu khi đã đăng nhập — xác thực mật khẩu cũ trước khi đổi.
     *
     * <p>Đây là alias rõ ràng cho {@link #changeOwnPassword} nhưng không ràng buộc
     * phiên đăng nhập, phù hợp để gọi từ flow quên mật khẩu nội bộ.
     *
     * @param userId      user cần đổi mật khẩu
     * @param oldPassword mật khẩu cũ plain-text
     * @param newPassword mật khẩu mới plain-text
     * @return {@code true} nếu thành công
     * @throws IllegalArgumentException nếu mật khẩu cũ không đúng
     */
    public boolean changePassword(long userId, String oldPassword, String newPassword) {
        // Lấy hash hiện tại
        String selectSql = "SELECT password FROM users WHERE user_id = ?";
        String currentHash;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                currentHash = rs.getString("password");
            }
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi lấy mật khẩu hiện tại: " + e.getMessage(), e);
        }

        if (currentHash == null || !BCrypt.checkpw(oldPassword, currentHash)) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng");
        }

        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        String updateSql = "UPDATE users SET password = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, newHash);
            ps.setLong  (2, userId);
            ps.executeUpdate();
            // Phase 5: Ghi audit log
            AuditLogger.getInstance().logPasswordChange(userId);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("[UserDAO] Lỗi cập nhật mật khẩu: " + e.getMessage(), e);
        }
    }

    /**
     * Dọn dẹp token hết hạn hoặc đã dùng — gọi khi ứng dụng khởi động.
     * Không ném exception nếu lỗi (log warning là đủ).
     */
    public void cleanupExpiredResetTokens() {
        String sql = "DELETE FROM password_reset_tokens "
                   + "WHERE expires_at <= SYSTIMESTAMP OR used = 1";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                System.out.println("[UserDAO] Đã xoá " + deleted + " reset token hết hạn/đã dùng.");
            }
        } catch (Exception e) {
            System.err.println("[UserDAO] cleanupExpiredResetTokens lỗi (không nghiêm trọng): "
                    + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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