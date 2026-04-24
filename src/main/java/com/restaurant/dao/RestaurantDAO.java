package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.restaurant.db.DBConnection;
import com.restaurant.model.Restaurant;
import com.restaurant.model.Restaurant.Status;
import com.restaurant.session.AppSession;
import com.restaurant.session.RbacGuard;

/**
 * DAO thao tác bảng RESTAURANTS.
 *
 * <p><b>Bảo mật:</b> Mọi phương thức đều bắt đầu bằng guard check
 * {@link #requireSuperAdmin()} — ném {@link SecurityException} ngay lập tức
 * nếu người dùng hiện tại không phải SUPER_ADMIN.
 *
 * <p><b>Ràng buộc xóa:</b> {@link #delete(long)} kiểm tra xem còn user nào
 * thuộc nhà hàng không trước khi xóa; nếu còn thì ném
 * {@link IllegalStateException} với thông báo rõ ràng.
 */
public class RestaurantDAO {

    // ── Guard ─────────────────────────────────────────────────────────────────

    /**
     * Kiểm tra quyền SUPER_ADMIN — phải gọi đầu MỌI method public.
     *
     * @throws SecurityException nếu không phải SUPER_ADMIN
     */
    private void requireSuperAdmin() {
        if (!RbacGuard.getInstance().isSuperAdmin()) {
            throw new SecurityException("Chỉ SUPER_ADMIN được phép thao tác nhà hàng");
        }
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    /**
     * Lấy toàn bộ danh sách nhà hàng, sắp xếp theo tên.
     *
     * @return danh sách {@link Restaurant}, không bao giờ null
     * @throws SecurityException nếu không phải SUPER_ADMIN
     */
    public List<Restaurant> findAll() {
        requireSuperAdmin();

        List<Restaurant> list = new ArrayList<>();
        String sql = "SELECT restaurant_id, name, address, phone, email, status, created_at"
                   + " FROM restaurants ORDER BY name";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(map(rs));
            }

        } catch (Exception e) {
            System.err.println("[RestaurantDAO] findAll lỗi: " + e.getMessage());
        }
        return list;
    }

    /**
     * Tìm nhà hàng theo ID.
     *
     * @param id restaurant_id cần tìm
     * @return {@link Restaurant} nếu tìm thấy, {@code null} nếu không có
     * @throws SecurityException nếu không phải SUPER_ADMIN
     */
    public Restaurant findById(long id) {
        requireSuperAdmin();

        String sql = "SELECT restaurant_id, name, address, phone, email, status, created_at"
                   + " FROM restaurants WHERE restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }

        } catch (Exception e) {
            System.err.println("[RestaurantDAO] findById lỗi: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lấy thông tin nhà hàng theo ID — dành cho RESTAURANT_ADMIN xem nhà hàng của mình.
     * KHÔNG kiểm tra SUPER_ADMIN. Caller có trách nhiệm đảm bảo chỉ truyền vào
     * restaurantId mà người dùng hiện tại được phép xem (thường lấy từ AppSession).
     *
     * @param id restaurant_id cần tìm
     * @return Restaurant nếu tìm thấy, null nếu không có
     */
    public Restaurant findByIdPublic(long id) {
        String sql = "SELECT restaurant_id, name, address, phone, email, status, created_at"
                   + " FROM restaurants WHERE restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }

        } catch (Exception e) {
            System.err.println("[RestaurantDAO] findByIdPublic lỗi: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cập nhật thông tin nhà hàng — dành cho RESTAURANT_ADMIN cập nhật nhà hàng của mình.
     * Guard: kiểm tra r.getRestaurantId() == AppSession.getRestaurantId()
     * hoặc SUPER_ADMIN. Throw SecurityException nếu vi phạm.
     *
     * @param r nhà hàng cần cập nhật
     * @throws SecurityException nếu cố sửa nhà hàng không thuộc quyền quản lý
     */
    public void updateByAdmin(Restaurant r) {
        RbacGuard guard = RbacGuard.getInstance();
        if (guard.isSuperAdmin()) {
            // SUPER_ADMIN: cho phép mọi restaurantId
        } else if (guard.isRestaurantAdmin()) {
            long myRestaurantId = AppSession.getInstance().getRestaurantId();
            if (r.getRestaurantId() != myRestaurantId) {
                throw new SecurityException("Không có quyền cập nhật nhà hàng không thuộc quyền quản lý");
            }
        } else {
            throw new SecurityException("Không có quyền cập nhật nhà hàng");
        }

        String sql = "UPDATE restaurants"
                   + " SET name = ?, address = ?, phone = ?, email = ?, status = ?"
                   + " WHERE restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nvl(r.getName()));
            ps.setString(2, nvl(r.getAddress()));
            ps.setString(3, nvl(r.getPhone()));
            ps.setString(4, nvl(r.getEmail()));
            ps.setString(5, r.getStatus() != null ? r.getStatus().name() : Status.ACTIVE.name());
            ps.setLong  (6, r.getRestaurantId());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("Không tìm thấy nhà hàng để cập nhật (id=" + r.getRestaurantId() + ")");
            }

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("[RestaurantDAO] Lỗi cập nhật nhà hàng: " + e.getMessage(), e);
        }
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Thêm nhà hàng mới vào DB. {@code restaurant_id} được DB tự sinh;
     * sau INSERT, field {@code restaurantId} của {@code r} sẽ được cập nhật.
     *
     * @param r nhà hàng cần thêm
     * @throws SecurityException nếu không phải SUPER_ADMIN
     * @throws RuntimeException  nếu có lỗi SQL
     */
    public void add(Restaurant r) {
        requireSuperAdmin();

        String sql = "INSERT INTO restaurants (name, address, phone, email, status)"
                   + " VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"restaurant_id"})) {

            ps.setString(1, nvl(r.getName()));
            ps.setString(2, nvl(r.getAddress()));
            ps.setString(3, nvl(r.getPhone()));
            ps.setString(4, nvl(r.getEmail()));
            ps.setString(5, r.getStatus() != null ? r.getStatus().name() : Status.ACTIVE.name());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    r.setRestaurantId(keys.getLong(1));
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("[RestaurantDAO] Lỗi thêm nhà hàng: " + e.getMessage(), e);
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Cập nhật thông tin nhà hàng (name, address, phone, email, status).
     *
     * @param r nhà hàng cần cập nhật (phải có restaurantId hợp lệ)
     * @throws SecurityException nếu không phải SUPER_ADMIN
     * @throws RuntimeException  nếu có lỗi SQL hoặc không tìm thấy bản ghi
     */
    public void update(Restaurant r) {
        requireSuperAdmin();

        String sql = "UPDATE restaurants"
                   + " SET name = ?, address = ?, phone = ?, email = ?, status = ?"
                   + " WHERE restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nvl(r.getName()));
            ps.setString(2, nvl(r.getAddress()));
            ps.setString(3, nvl(r.getPhone()));
            ps.setString(4, nvl(r.getEmail()));
            ps.setString(5, r.getStatus() != null ? r.getStatus().name() : Status.ACTIVE.name());
            ps.setLong  (6, r.getRestaurantId());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("Không tìm thấy nhà hàng để cập nhật (id=" + r.getRestaurantId() + ")");
            }

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("[RestaurantDAO] Lỗi cập nhật nhà hàng: " + e.getMessage(), e);
        }
    }

    // ── UPDATE STATUS ─────────────────────────────────────────────────────────

    /**
     * Chỉ cập nhật cột {@code status} của nhà hàng.
     *
     * @param id     restaurant_id cần thay đổi
     * @param status giá trị mới ("ACTIVE" hoặc "INACTIVE")
     * @throws SecurityException nếu không phải SUPER_ADMIN
     * @throws RuntimeException  nếu có lỗi SQL
     */
    public void updateStatus(long id, String status) {
        requireSuperAdmin();

        String sql = "UPDATE restaurants SET status = ? WHERE restaurant_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setLong  (2, id);
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("[RestaurantDAO] Lỗi cập nhật trạng thái: " + e.getMessage(), e);
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Xóa nhà hàng.
     * <ul>
     *   <li>Trước tiên kiểm tra {@code SELECT COUNT(*) FROM users WHERE restaurant_id = ?}</li>
     *   <li>Nếu count &gt; 0: ném {@link IllegalStateException} với số lượng rõ ràng</li>
     *   <li>Nếu count == 0: thực hiện {@code DELETE}</li>
     * </ul>
     *
     * @param id restaurant_id cần xóa
     * @throws SecurityException     nếu không phải SUPER_ADMIN
     * @throws IllegalStateException nếu nhà hàng còn nhân viên
     * @throws RuntimeException      nếu có lỗi SQL
     */
    public void delete(long id) {
        requireSuperAdmin();

        // Kiểm tra ràng buộc nhân viên
        String countSql = "SELECT COUNT(*) FROM users WHERE restaurant_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count > 0) {
                        throw new IllegalStateException(
                            "Không thể xóa — còn " + count + " nhân viên thuộc nhà hàng này");
                    }
                }
            }

        } catch (IllegalStateException ise) {
            throw ise;   // không bọc lại
        } catch (Exception e) {
            throw new RuntimeException("[RestaurantDAO] Lỗi kiểm tra nhân viên: " + e.getMessage(), e);
        }

        // Thực hiện xóa
        String deleteSql = "DELETE FROM restaurants WHERE restaurant_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {

            ps.setLong(1, id);
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("[RestaurantDAO] Lỗi xóa nhà hàng: " + e.getMessage(), e);
        }
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private Restaurant map(ResultSet rs) throws Exception {
        Restaurant r = new Restaurant();
        r.setRestaurantId(rs.getLong("restaurant_id"));
        r.setName   (nvl(rs.getString("name")));
        r.setAddress(nvl(rs.getString("address")));
        r.setPhone  (nvl(rs.getString("phone")));
        r.setEmail  (nvl(rs.getString("email")));
        r.setStatus (Status.from(rs.getString("status")));

        Timestamp ts = rs.getTimestamp("created_at");
        r.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);

        return r;
    }

    /** Null-safe: trả về chuỗi rỗng thay vì null. */
    private String nvl(String s) {
        return s != null ? s : "";
    }
}