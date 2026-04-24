package com.restaurant.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.restaurant.db.DBConnection;
import com.restaurant.model.Employee;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;

/**
 * DAO thao tác bảng EMPLOYEES với tenant isolation theo restaurant_id.
 *
 * <p>Mọi truy vấn đều tự động scope theo {@link AppSession#getRestaurantId()}
 * thông qua helper {@link #rid()}, ngoại trừ SUPER_ADMIN (restaurantId == 0)
 * được phép xem toàn bộ dữ liệu qua {@link #findAll()}.
 *
 * <p>Các thao tác ghi (add / update / delete) luôn đính kèm
 * {@code AND restaurant_id = rid()} — không có ngoại lệ — để ngăn
 * cross-tenant data mutation.
 */
public class EmployeeDAO {

    // ─── Tenant helpers ───────────────────────────────────────────────────────

    /** Trả về restaurant_id của phiên hiện tại (0 nếu là SUPER_ADMIN). */
    private long rid() {
        return AppSession.getInstance().getRestaurantId();
    }

    /** {@code true} nếu người dùng hiện tại là SUPER_ADMIN. */
    private boolean isSuperAdmin() {
        return RbacGuard.getInstance().isSuperAdmin();
    }

    // ─── READ ─────────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách nhân viên.
     * <ul>
     *   <li>SUPER_ADMIN → toàn bộ nhân viên (không WHERE restaurant_id)</li>
     *   <li>Còn lại     → chỉ nhân viên thuộc restaurant_id của phiên</li>
     * </ul>
     */
    public List<Employee> findAll() {
        List<Employee> list = new ArrayList<>();
        boolean superAdmin = isSuperAdmin();

        String sql = superAdmin
                ? "SELECT employee_id, name, cccd, phone, address, start_date, role"
                        + " FROM employees ORDER BY name"
                : "SELECT employee_id, name, cccd, phone, address, start_date, role"
                        + " FROM employees WHERE restaurant_id = ? ORDER BY name";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (!superAdmin) {
                ps.setLong(1, rid());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }

        } catch (Exception e) {
            System.err.println("[EmployeeDAO] findAll lỗi: " + e.getMessage());
        }
        return list;
    }

    // ─── READ (with account status) ──────────────────────────────────────────

    /**
     * Lấy danh sách nhân viên kèm trạng thái tài khoản, dùng một query duy nhất
     * để tránh N+1. Chỉ scope theo restaurant_id hiện tại (SUPER_ADMIN không cần
     * dùng method này vì không hiển thị cột Tài khoản).
     *
     * <p>Kiểm tra {@code user_id IS NOT NULL} trực tiếp trên bảng {@code employees}
     * (user_id là FK sang bảng users, được gán khi tạo tài khoản bằng RegisterStaffDialog).
     *
     * @return danh sách Employee với {@link Employee#isHasAccount()} đã được set đúng
     */
    public List<Employee> findAllWithAccountStatus() {
        List<Employee> list = new ArrayList<>();
        String sql = "SELECT employee_id, name, cccd, phone, address, start_date, role,"
                   + " CASE WHEN user_id IS NOT NULL THEN 'Y' ELSE 'N' END AS has_account"
                   + " FROM employees"
                   + " WHERE restaurant_id = ?"
                   + " ORDER BY name";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, rid());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Employee e = map(rs);
                    e.setHasAccount("Y".equals(rs.getString("has_account")));
                    list.add(e);
                }
            }

        } catch (Exception e) {
            System.err.println("[EmployeeDAO] findAllWithAccountStatus lỗi: " + e.getMessage());
        }
        return list;
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    /**
     * Thêm nhân viên mới vào restaurant hiện tại.
     * restaurant_id luôn được lấy từ {@link #rid()} — UI không truyền vào.
     *
     * @param e đối tượng nhân viên cần thêm (id sẽ được cập nhật sau INSERT)
     * @return {@code e} với id đã được gán từ DB
     */
    public Employee add(Employee e) {
        String sql = "INSERT INTO employees (name, cccd, phone, address, start_date, role, restaurant_id)"
                   + " VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"employee_id"})) {

            ps.setString(1, e.getName());
            ps.setString(2, e.getCccd());
            ps.setString(3, e.getPhone());
            ps.setString(4, e.getAddress());
            ps.setDate  (5, toSqlDate(e.getStartDate()));
            ps.setString(6, toDbRole(e.getRole()));
            ps.setLong  (7, rid());                        // ← tenant tự động

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    e.setId(String.valueOf(keys.getLong(1)));
                }
            }

        } catch (Exception ex) {
            throw new RuntimeException("[EmployeeDAO] Lỗi thêm nhân viên: " + ex.getMessage(), ex);
        }
        return e;
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    /**
     * Cập nhật thông tin nhân viên.
     * Điều kiện {@code AND restaurant_id = rid()} đảm bảo chỉ sửa được
     * nhân viên thuộc restaurant hiện tại.
     *
     * @param e nhân viên cần cập nhật
     * @return {@code e} sau khi cập nhật thành công
     * @throws SecurityException nếu rowsAffected == 0 (nhân viên không thuộc restaurant này)
     */
    public Employee update(Employee e) {
        String sql = "UPDATE employees"
                   + " SET name = ?, cccd = ?, phone = ?, address = ?, start_date = ?, role = ?"
                   + " WHERE employee_id = ? AND restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, e.getName());
            ps.setString(2, e.getCccd());
            ps.setString(3, e.getPhone());
            ps.setString(4, e.getAddress());
            ps.setDate  (5, toSqlDate(e.getStartDate()));
            ps.setString(6, toDbRole(e.getRole()));
            ps.setLong  (7, Long.parseLong(e.getId()));
            ps.setLong  (8, rid());                        // ← tenant guard

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SecurityException("Không có quyền sửa nhân viên này");
            }

        } catch (SecurityException se) {
            throw se;   // không bọc lại SecurityException
        } catch (Exception ex) {
            throw new RuntimeException("[EmployeeDAO] Lỗi cập nhật nhân viên: " + ex.getMessage(), ex);
        }
        return e;
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    /**
     * Xóa nhân viên khỏi DB (hard delete).
     * Điều kiện {@code AND restaurant_id = rid()} ngăn xóa nhân viên
     * của restaurant khác.
     *
     * @param employeeId id của nhân viên cần xóa
     * @throws SecurityException nếu rowsAffected == 0
     */
    public void delete(String employeeId) {
        String sql = "DELETE FROM employees WHERE employee_id = ? AND restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, Long.parseLong(employeeId));
            ps.setLong(2, rid());                          // ← tenant guard

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SecurityException("Không có quyền xóa nhân viên này");
            }

        } catch (SecurityException se) {
            throw se;
        } catch (Exception ex) {
            throw new RuntimeException("[EmployeeDAO] Lỗi xóa nhân viên: " + ex.getMessage(), ex);
        }
    }


    // ─── LOOKUP BY USER_ID ────────────────────────────────────────────────────

    /**
     * Tìm bản ghi nhân viên liên kết với {@code userId} (cột user_id trong employees).
     * Dùng để pre-fill form "Hồ sơ của tôi" (phone, address).
     * Trả về {@code Optional.empty()} nếu user là SUPER_ADMIN hoặc chưa có employee record.
     *
     * @param userId user_id của người dùng hiện tại
     * @return Optional chứa Employee nếu tìm thấy, empty nếu không
     */
    public Optional<Employee> findByUserId(long userId) {
        String sql = "SELECT employee_id, name, cccd, phone, address, start_date, role"
                   + " FROM employees WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }

        } catch (Exception e) {
            System.err.println("[EmployeeDAO] findByUserId lỗi: " + e.getMessage());
        }
        return Optional.empty();
    }

    // ─── ROLE ASSIGNMENT (Phase 5B) ───────────────────────────────────────────

    /**
     * Tìm user_id liên kết với nhân viên.
     *
     * @param employeeId id nhân viên cần tra cứu
     * @return Optional chứa user_id nếu tìm thấy, Optional.empty() nếu không có
     */
    public Optional<Long> findUserId(String employeeId) {
        String sql = "SELECT user_id FROM employees WHERE employee_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, Long.parseLong(employeeId));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long userId = rs.getLong("user_id");
                    if (rs.wasNull()) {
                        return Optional.empty();   // employee tồn tại nhưng user_id IS NULL
                    }
                    return Optional.of(userId);
                }
            }

        } catch (Exception e) {
            System.err.println("[EmployeeDAO] findUserId lỗi: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Cập nhật role của user liên kết với nhân viên.
     * <p>
     * Luồng xử lý:<br>
     * 1) Guard check — RbacGuard.can(ASSIGN_ROLE). Throw SecurityException nếu không có quyền.<br>
     * 2) RESTAURANT_ADMIN không được gán RESTAURANT_ADMIN / SUPER_ADMIN.<br>
     * 3) Kiểm tra nhân viên có user_id — nếu không, throw IllegalStateException.<br>
     * 4) Thực thi UPDATE với điều kiện AND restaurant_id = ? (tenant isolation).<br>
     * 5) 0 row affected → role không hợp lệ → throw IllegalArgumentException.
     *
     * @param employeeId id nhân viên cần đổi role
     * @param newRole    tên role mới (phải khớp cột name trong bảng roles)
     * @throws SecurityException        nếu không có quyền hoặc cố gán role cao hơn
     * @throws IllegalStateException    nếu nhân viên chưa có tài khoản đăng nhập
     * @throws IllegalArgumentException nếu role name không tồn tại trong DB
     */
    public void updateUserRole(String employeeId, String newRole) {

        // Bước 1: Guard check
        if (!RbacGuard.getInstance().can(Permission.ASSIGN_ROLE)) {
            throw new SecurityException("Không có quyền gán role");
        }

        // Bước 2: RESTAURANT_ADMIN không được gán role ngang / cao hơn (lớp bảo vệ ở DAO)
        if (!RbacGuard.getInstance().isSuperAdmin()
                && ("RESTAURANT_ADMIN".equalsIgnoreCase(newRole)
                    || "SUPER_ADMIN".equalsIgnoreCase(newRole))) {
            throw new SecurityException("RESTAURANT_ADMIN không được gán role này");
        }

        // Bước 3: Kiểm tra nhân viên có user_id không
        Optional<Long> userIdOpt = findUserId(employeeId);
        if (userIdOpt.isEmpty()) {
            throw new IllegalStateException("Nhân viên này chưa có tài khoản đăng nhập");
        }

        // Bước 4: Thực thi UPDATE với tenant isolation
        String sql = "UPDATE users "
                   + "SET role_id = (SELECT id FROM roles WHERE name = ?) "
                   + "WHERE user_id = (SELECT user_id FROM employees WHERE employee_id = ?) "
                   + "  AND restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newRole);
            ps.setLong  (2, Long.parseLong(employeeId));
            ps.setLong  (3, AppSession.getInstance().getRestaurantId());

            int rowsAffected = ps.executeUpdate();

            // Bước 5: 0 row affected → role không tồn tại hoặc nhân viên không thuộc restaurant này
            if (rowsAffected == 0) {
                throw new IllegalArgumentException("Role không hợp lệ: " + newRole);
            }

        } catch (SecurityException | IllegalStateException | IllegalArgumentException ex) {
            throw ex;   // không bọc lại các business exception
        } catch (Exception ex) {
            throw new RuntimeException("[EmployeeDAO] Lỗi cập nhật role: " + ex.getMessage(), ex);
        }
    }

    // ─── EXISTS ───────────────────────────────────────────────────────────────

    /**
     * Kiểm tra nhân viên có thuộc restaurant hiện tại không.
     * Dùng để validate trước khi mở dialog chi tiết hoặc thao tác.
     *
     * @param employeeId id nhân viên cần kiểm tra
     * @return {@code true} nếu tồn tại trong restaurant hiện tại
     */
    public boolean existsInRestaurant(String employeeId) {
        String sql = "SELECT 1 FROM employees WHERE employee_id = ? AND restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, Long.parseLong(employeeId));
            ps.setLong(2, rid());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            System.err.println("[EmployeeDAO] existsInRestaurant lỗi: " + e.getMessage());
            return false;
        }
    }

    // ─── Mapping helpers ──────────────────────────────────────────────────────

    private Employee map(ResultSet rs) throws SQLException {
        Date startDate = rs.getDate("start_date");
        String dateStr = (startDate != null) ? startDate.toLocalDate().toString() : "";

        return new Employee(
                String.valueOf(rs.getLong("employee_id")),
                nvl(rs.getString("name")),
                nvl(rs.getString("cccd")),
                nvl(rs.getString("phone")),
                nvl(rs.getString("address")),
                dateStr,
                fromDbRole(rs.getString("role"))
        );
    }

    /** Chuyển String startDate ("yyyy-MM-dd") sang java.sql.Date. */
    private java.sql.Date toSqlDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return java.sql.Date.valueOf(dateStr);
        } catch (IllegalArgumentException e) {
            System.err.println("[EmployeeDAO] Ngày không hợp lệ: " + dateStr);
            return null;
        }
    }

    private String toDbRole(Employee.Role r) {
        if (r == null) return "PHUC_VU";
        return switch (r) {
            case DAU_BEP  -> "DAU_BEP";
            case THU_NGAN -> "THU_NGAN";
            case QUAN_LY  -> "QUAN_LY";
            default       -> "PHUC_VU";
        };
    }

    private Employee.Role fromDbRole(String roleName) {
        if (roleName == null) return Employee.Role.PHUC_VU;
        return switch (roleName.toUpperCase()) {
            case "DAU_BEP"  -> Employee.Role.DAU_BEP;
            case "THU_NGAN" -> Employee.Role.THU_NGAN;
            case "QUAN_LY"  -> Employee.Role.QUAN_LY;
            default         -> Employee.Role.PHUC_VU;
        };
    }

    /** Null-safe: trả về chuỗi rỗng thay vì null. */
    private String nvl(String s) {
        return s != null ? s : "";
    }
}