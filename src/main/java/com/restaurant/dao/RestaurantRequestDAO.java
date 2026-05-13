package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.restaurant.db.DBConnection;
import com.restaurant.model.RestaurantRequest;
import com.restaurant.model.RestaurantRequest.RequestStatus;
import com.restaurant.session.AuditLogger;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;

/**
 * DAO thao tác bảng RESTAURANT_REQUESTS.
 *
 * <p><b>Phân quyền:</b>
 * <ul>
 *   <li>{@link #submit} — <em>public</em>, không yêu cầu đăng nhập.
 *       Đây là điểm vào duy nhất cho chủ nhà hàng đăng ký.</li>
 *   <li>Mọi method còn lại — yêu cầu {@link Permission#REVIEW_RESTAURANT_REQUEST}
 *       (hiện chỉ SUPER_ADMIN có quyền này).</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> Mỗi method mở connection riêng từ pool —
 * an toàn khi gọi từ nhiều thread (JavaFX Task, worker thread, v.v.).
 */
public class RestaurantRequestDAO {

    // ── Guard helpers ─────────────────────────────────────────────────────────

    /**
     * Kiểm tra quyền REVIEW_RESTAURANT_REQUEST.
     * Gọi đầu mọi method cần bảo vệ (không áp dụng cho {@link #submit}).
     *
     * @throws SecurityException nếu người dùng hiện tại không có quyền
     */
    private void requireReviewPermission() {
        if (!RbacGuard.getInstance().can(Permission.REVIEW_RESTAURANT_REQUEST)) {
            throw new SecurityException(
                    "Không đủ quyền: cần REVIEW_RESTAURANT_REQUEST để xem/xử lý đơn đăng ký");
        }
    }

    // ── WRITE (public — không cần đăng nhập) ─────────────────────────────────

    /**
     * Nộp đơn đăng ký mở nhà hàng mới.
     *
     * <p>Đây là method <b>duy nhất không yêu cầu đăng nhập</b> trong toàn DAO.
     * UI form đăng ký công khai gọi trực tiếp method này.
     *
     * <p>Sau khi insert thành công, {@code request.getRequestId()} sẽ được
     * cập nhật với ID vừa được Oracle sequence sinh ra.
     *
     * @param request đơn đăng ký đã điền đầy đủ thông tin (status sẽ bị
     *                đặt về PENDING bất kể giá trị truyền vào)
     * @throws IllegalArgumentException nếu các trường bắt buộc còn trống
     * @throws RuntimeException         nếu lỗi DB
     */
    public void submit(RestaurantRequest request) {
        validateForSubmit(request);

        // Ép status = PENDING, bất kể caller truyền gì
        request.setStatus(RequestStatus.PENDING);

        String sql = """
                INSERT INTO restaurant_requests (
                    request_id,
                    owner_name, owner_email, owner_phone, owner_password_hash,
                    restaurant_name, restaurant_address, restaurant_phone, restaurant_email,
                    logo_path, document_path,
                    status, submitted_at
                ) VALUES (
                    seq_restaurant_request_id.NEXTVAL,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?,
                    'PENDING', SYSTIMESTAMP
                )
                """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql,
                     new String[]{"request_id"})) {

            ps.setString(1,  request.getOwnerName());
            ps.setString(2,  request.getOwnerEmail());
            ps.setString(3,  request.getOwnerPhone());
            ps.setString(4,  request.getOwnerPasswordHash());
            ps.setString(5,  request.getRestaurantName());
            ps.setString(6,  request.getRestaurantAddress());
            ps.setString(7,  nullIfBlank(request.getRestaurantPhone()));
            ps.setString(8,  nullIfBlank(request.getRestaurantEmail()));
            ps.setString(9,  nullIfBlank(request.getLogoPath()));
            ps.setString(10, nullIfBlank(request.getDocumentPath()));

            ps.executeUpdate();

            // Lấy lại generated key để cập nhật object
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    request.setRequestId(keys.getLong(1));
                }
            }

        } catch (Exception e) {
            System.err.println("[RestaurantRequestDAO] submit lỗi: " + e.getMessage());
            throw new RuntimeException("Lỗi nộp đơn đăng ký nhà hàng: " + e.getMessage(), e);
        }
    }

    // ── READ (yêu cầu REVIEW_RESTAURANT_REQUEST) ──────────────────────────────

    /**
     * Lấy toàn bộ đơn đăng ký, sắp xếp mới nhất trước.
     *
     * @return danh sách {@link RestaurantRequest}, không bao giờ null
     * @throws SecurityException nếu không có quyền REVIEW_RESTAURANT_REQUEST
     */
    public List<RestaurantRequest> findAll() {
        requireReviewPermission();

        List<RestaurantRequest> list = new ArrayList<>();
        String sql = """
                SELECT request_id,
                       owner_name, owner_email, owner_phone, owner_password_hash,
                       restaurant_name, restaurant_address, restaurant_phone, restaurant_email,
                       logo_path, document_path,
                       status, reject_reason,
                       submitted_at, reviewed_at, reviewed_by
                  FROM restaurant_requests
                 ORDER BY submitted_at DESC
                """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(map(rs));
            }

        } catch (Exception e) {
            System.err.println("[RestaurantRequestDAO] findAll lỗi: " + e.getMessage());
            throw new RuntimeException("Lỗi tải danh sách đơn đăng ký: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Tìm đơn đăng ký theo ID.
     *
     * @param id request_id cần tìm
     * @return {@link RestaurantRequest} nếu tồn tại, {@code null} nếu không có
     * @throws SecurityException nếu không có quyền REVIEW_RESTAURANT_REQUEST
     */
    public RestaurantRequest findById(long id) {
        requireReviewPermission();

        String sql = """
                SELECT request_id,
                       owner_name, owner_email, owner_phone, owner_password_hash,
                       restaurant_name, restaurant_address, restaurant_phone, restaurant_email,
                       logo_path, document_path,
                       status, reject_reason,
                       submitted_at, reviewed_at, reviewed_by
                  FROM restaurant_requests
                 WHERE request_id = ?
                """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }

        } catch (Exception e) {
            System.err.println("[RestaurantRequestDAO] findById lỗi: " + e.getMessage());
            throw new RuntimeException("Lỗi tải đơn đăng ký #" + id + ": " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Lấy danh sách đơn đang ở trạng thái PENDING, mới nhất trước.
     * Dùng để hiển thị badge "chờ duyệt" và danh sách ưu tiên cho admin.
     *
     * @return danh sách PENDING {@link RestaurantRequest}, không bao giờ null
     * @throws SecurityException nếu không có quyền REVIEW_RESTAURANT_REQUEST
     */
    public List<RestaurantRequest> findPending() {
        requireReviewPermission();

        List<RestaurantRequest> list = new ArrayList<>();
        String sql = """
                SELECT request_id,
                       owner_name, owner_email, owner_phone, owner_password_hash,
                       restaurant_name, restaurant_address, restaurant_phone, restaurant_email,
                       logo_path, document_path,
                       status, reject_reason,
                       submitted_at, reviewed_at, reviewed_by
                  FROM restaurant_requests
                 WHERE status = 'PENDING'
                 ORDER BY submitted_at DESC
                """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(map(rs));
            }

        } catch (Exception e) {
            System.err.println("[RestaurantRequestDAO] findPending lỗi: " + e.getMessage());
            throw new RuntimeException("Lỗi tải danh sách đơn chờ duyệt: " + e.getMessage(), e);
        }
        return list;
    }

    // ── WRITE — phê duyệt / từ chối (yêu cầu REVIEW_RESTAURANT_REQUEST) ──────

    /**
     * Chấp thuận đơn đăng ký.
     *
     * <p>Cập nhật {@code status = 'APPROVED'}, ghi {@code reviewed_at} và
     * {@code reviewed_by} vào DB. Ghi audit log hành động.
     *
     * <p><b>Lưu ý:</b> Method này chỉ cập nhật trạng thái. Việc tạo record
     * RESTAURANTS và USERS tương ứng là trách nhiệm của tầng service/controller
     * gọi sau method này (Phase 2+).
     *
     * @param id         request_id cần phê duyệt
     * @param reviewedBy user_id của SUPER_ADMIN đang thực hiện hành động
     * @throws SecurityException    nếu không có quyền REVIEW_RESTAURANT_REQUEST
     * @throws IllegalStateException nếu đơn không ở trạng thái PENDING
     * @throws RuntimeException     nếu lỗi DB
     */
    public void approve(long id, long reviewedBy) {
        requireReviewPermission();

        String sql = """
                UPDATE restaurant_requests
                   SET status      = 'APPROVED',
                       reviewed_at = SYSTIMESTAMP,
                       reviewed_by = ?
                 WHERE request_id  = ?
                   AND status      = 'PENDING'
                """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, reviewedBy);
            ps.setLong(2, id);
            int rows = ps.executeUpdate();

            if (rows == 0) {
                throw new IllegalStateException(
                        "Đơn #" + id + " không tồn tại hoặc không ở trạng thái PENDING");
            }

            AuditLogger.getInstance().log(
                    "APPROVE_RESTAURANT_REQUEST", id, "SUCCESS",
                    "Phê duyệt đơn đăng ký nhà hàng #" + id + " bởi user #" + reviewedBy);

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[RestaurantRequestDAO] approve lỗi: " + e.getMessage());
            throw new RuntimeException("Lỗi phê duyệt đơn #" + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Từ chối đơn đăng ký với lý do cụ thể.
     *
     * <p>Cập nhật {@code status = 'REJECTED'}, ghi lý do từ chối,
     * {@code reviewed_at} và {@code reviewed_by}. Ghi audit log.
     *
     * @param id           request_id cần từ chối
     * @param reviewedBy   user_id của SUPER_ADMIN đang thực hiện
     * @param reason       lý do từ chối (không được trống)
     * @throws SecurityException     nếu không có quyền REVIEW_RESTAURANT_REQUEST
     * @throws IllegalArgumentException nếu reason trống
     * @throws IllegalStateException  nếu đơn không ở trạng thái PENDING
     * @throws RuntimeException      nếu lỗi DB
     */
    public void reject(long id, long reviewedBy, String reason) {
        requireReviewPermission();

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Lý do từ chối không được để trống");
        }

        String sql = """
                UPDATE restaurant_requests
                   SET status        = 'REJECTED',
                       reject_reason = ?,
                       reviewed_at   = SYSTIMESTAMP,
                       reviewed_by   = ?
                 WHERE request_id    = ?
                   AND status        = 'PENDING'
                """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, reason.trim());
            ps.setLong(2,   reviewedBy);
            ps.setLong(3,   id);
            int rows = ps.executeUpdate();

            if (rows == 0) {
                throw new IllegalStateException(
                        "Đơn #" + id + " không tồn tại hoặc không ở trạng thái PENDING");
            }

            AuditLogger.getInstance().log(
                    "REJECT_RESTAURANT_REQUEST", id, "SUCCESS",
                    "Từ chối đơn #" + id + " bởi user #" + reviewedBy + " — lý do: " + reason);

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[RestaurantRequestDAO] reject lỗi: " + e.getMessage());
            throw new RuntimeException("Lỗi từ chối đơn #" + id + ": " + e.getMessage(), e);
        }
    }

    // ── AGGREGATE ─────────────────────────────────────────────────────────────

    /**
     * Đếm số đơn theo trạng thái. Dùng để hiển thị badge trên menu admin.
     *
     * <p>Ví dụ: {@code countByStatus("PENDING")} → số đơn đang chờ duyệt.
     *
     * @param status một trong: {@code "PENDING"}, {@code "APPROVED"}, {@code "REJECTED"}
     * @return số lượng đơn có status tương ứng; 0 nếu không có hoặc lỗi DB
     * @throws SecurityException nếu không có quyền REVIEW_RESTAURANT_REQUEST
     */
    public int countByStatus(String status) {
        requireReviewPermission();

        String sql = "SELECT COUNT(*) FROM restaurant_requests WHERE status = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status == null ? "PENDING" : status.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }

        } catch (Exception e) {
            System.err.println("[RestaurantRequestDAO] countByStatus lỗi: " + e.getMessage());
        }
        return 0;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Map một dòng ResultSet → {@link RestaurantRequest}.
     * Dùng {@code safeGetTimestamp} để không crash nếu cột nullable là NULL.
     */
    private RestaurantRequest map(ResultSet rs) throws Exception {
        RestaurantRequest r = new RestaurantRequest();

        r.setRequestId(rs.getLong("request_id"));

        r.setOwnerName(rs.getString("owner_name"));
        r.setOwnerEmail(rs.getString("owner_email"));
        r.setOwnerPhone(rs.getString("owner_phone"));
        r.setOwnerPasswordHash(rs.getString("owner_password_hash"));

        r.setRestaurantName(rs.getString("restaurant_name"));
        r.setRestaurantAddress(rs.getString("restaurant_address"));
        r.setRestaurantPhone(rs.getString("restaurant_phone"));
        r.setRestaurantEmail(rs.getString("restaurant_email"));

        r.setLogoPath(rs.getString("logo_path"));
        r.setDocumentPath(rs.getString("document_path"));

        r.setStatus(RequestStatus.from(rs.getString("status")));
        r.setRejectReason(rs.getString("reject_reason"));

        r.setSubmittedAt(toLocalDateTime(rs.getTimestamp("submitted_at")));
        r.setReviewedAt(toLocalDateTime(rs.getTimestamp("reviewed_at")));

        long reviewedBy = rs.getLong("reviewed_by");
        r.setReviewedBy(rs.wasNull() ? 0L : reviewedBy);

        return r;
    }

    /** Chuyển {@link Timestamp} Oracle → {@link LocalDateTime}; trả về null nếu ts = null. */
    private LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** Trả về null nếu s null hoặc rỗng sau trim — giúp lưu NULL thay vì chuỗi rỗng vào DB. */
    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * Validate các trường bắt buộc trước khi INSERT.
     *
     * @throws IllegalArgumentException nếu thiếu trường bắt buộc
     */
    private void validateForSubmit(RestaurantRequest r) {
        if (r == null) throw new IllegalArgumentException("request không được null");
        requireNonBlank(r.getOwnerName(),         "Tên chủ nhà hàng");
        requireNonBlank(r.getOwnerEmail(),        "Email chủ nhà hàng");
        requireNonBlank(r.getOwnerPhone(),        "Số điện thoại chủ nhà hàng");
        requireNonBlank(r.getOwnerPasswordHash(), "Mật khẩu (hash)");
        requireNonBlank(r.getRestaurantName(),    "Tên nhà hàng");
        requireNonBlank(r.getRestaurantAddress(), "Địa chỉ nhà hàng");
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " không được để trống");
        }
    }
}