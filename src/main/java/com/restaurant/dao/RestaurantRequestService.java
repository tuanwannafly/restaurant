package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.restaurant.db.DBConnection;
import com.restaurant.model.RestaurantRequest;
import com.restaurant.session.AuditLogger;
import com.restaurant.session.RbacGuard;

/**
 * Service xử lý phê duyệt đơn đăng ký nhà hàng với Oracle transaction.
 *
 * <p>Tách biệt logic nghiệp vụ phức tạp (multi-table transaction) khỏi
 * {@link RestaurantRequestDAO} — giữ nguyên DAO đơn giản / đơn trách nhiệm.
 *
 * <h2>Luồng APPROVE (atomic)</h2>
 * <pre>
 *   BEGIN TRANSACTION
 *     1. INSERT INTO restaurants  → lấy restaurant_id mới
 *     2. INSERT INTO users         → dùng owner_password_hash từ request (đã BCrypt)
 *     3. UPDATE restaurant_requests SET status = 'APPROVED'
 *   COMMIT  (hoặc ROLLBACK nếu bất kỳ bước nào thất bại)
 * </pre>
 *
 * <p><b>Quan trọng:</b> {@code owner_password_hash} trong request đã được BCrypt
 * bởi {@link RestaurantRegistrationController} khi nộp đơn — KHÔNG hash lại ở đây.
 *
 * <h2>REJECT</h2>
 * Delegate sang {@link RestaurantRequestDAO#reject} vì chỉ cần cập nhật một bảng.
 *
 * <p><b>Thread-safety:</b> Stateless — an toàn khi dùng trên background Task.
 *
 * <p><b>Phase 4</b> — SmartRestaurant JavaFX/Oracle
 */
public class RestaurantRequestService {

    // ── Guard ─────────────────────────────────────────────────────────────────

    private void requireReviewPermission() {
        if (!RbacGuard.getInstance().can(
                com.restaurant.session.Permission.REVIEW_RESTAURANT_REQUEST)) {
            throw new SecurityException(
                    "Không đủ quyền: cần REVIEW_RESTAURANT_REQUEST để phê duyệt đơn đăng ký");
        }
    }

    // ── APPROVE — Oracle atomic transaction ───────────────────────────────────

    /**
     * Phê duyệt đơn đăng ký nhà hàng trong một Oracle transaction duy nhất.
     *
     * <p>Ba thao tác được thực hiện trên cùng một {@link Connection} với
     * {@code autoCommit = false}. Nếu bất kỳ bước nào ném exception, toàn bộ
     * transaction sẽ bị ROLLBACK — không có dữ liệu nào bị ghi vào DB.
     *
     * <h3>Bước thực hiện:</h3>
     * <ol>
     *   <li>INSERT INTO restaurants (name, address, phone, email, status='ACTIVE', logo_url)
     *       → trả về {@code restaurant_id} mới qua generated key.</li>
     *   <li>INSERT INTO users (name, email, password=ownerPasswordHash,
     *       role_id=(SELECT id FROM roles WHERE name='RESTAURANT_ADMIN'),
     *       restaurant_id=mới, status='ACTIVE')</li>
     *   <li>UPDATE restaurant_requests SET status='APPROVED', reviewed_at=SYSTIMESTAMP,
     *       reviewed_by=reviewedBy WHERE request_id=? AND status='PENDING'</li>
     * </ol>
     *
     * <h3>Sau khi COMMIT:</h3>
     * Ghi AuditLog {@code APPROVE_RESTAURANT_REQUEST} (không thuộc transaction —
     * thất bại audit không rollback nghiệp vụ).
     *
     * @param request    đơn đăng ký cần phê duyệt (phải ở trạng thái PENDING)
     * @param reviewedBy user_id của SUPER_ADMIN đang thực hiện hành động
     * @return restaurant_id của nhà hàng vừa được tạo
     * @throws SecurityException     nếu không có quyền REVIEW_RESTAURANT_REQUEST
     * @throws IllegalStateException nếu đơn không ở trạng thái PENDING
     * @throws RuntimeException      nếu bất kỳ lỗi DB nào xảy ra (đã rollback)
     */
    public long approveWithTransaction(RestaurantRequest request, long reviewedBy) {
        requireReviewPermission();

        if (!request.isPending()) {
            throw new IllegalStateException(
                    "Đơn #" + request.getRequestId() + " không ở trạng thái PENDING");
        }

        // SQL statements
        final String sqlInsertRestaurant =
                "INSERT INTO restaurants (name, address, phone, email, status, logo_url)"
              + " VALUES (?, ?, ?, ?, 'ACTIVE', ?)";

        final String sqlInsertUser =
                "INSERT INTO users (name, email, password, role_id, restaurant_id, status)"
              + " VALUES (?, ?, ?, (SELECT id FROM roles WHERE name = 'RESTAURANT_ADMIN'), ?, 'ACTIVE')";

        final String sqlApproveRequest =
                "UPDATE restaurant_requests"
              + "   SET status = 'APPROVED', reviewed_at = SYSTIMESTAMP, reviewed_by = ?"
              + " WHERE request_id = ? AND status = 'PENDING'";

        Connection conn = null;
        long newRestaurantId;
        long newUserId;

        try {
            conn = DBConnection.getInstance().getConnection();
            conn.setAutoCommit(false); // ← begin transaction

            // ── Step 1: INSERT restaurant ─────────────────────────────────────
            try (PreparedStatement ps = conn.prepareStatement(
                    sqlInsertRestaurant, new String[]{"restaurant_id"})) {

                ps.setString(1, request.getRestaurantName());
                ps.setString(2, request.getRestaurantAddress());
                ps.setString(3, nullIfBlank(request.getRestaurantPhone()));
                ps.setString(4, nullIfBlank(request.getRestaurantEmail()));
                ps.setString(5, nullIfBlank(request.getLogoPath()));
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new RuntimeException("Không lấy được restaurant_id sau INSERT");
                    }
                    newRestaurantId = keys.getLong(1);
                }
            }

            // ── Step 2: INSERT user (RESTAURANT_ADMIN) ────────────────────────
            // Dùng owner_password_hash đã có từ request (BCrypt bởi Registration form)
            // KHÔNG hash lại — tránh double-hashing phá hỏng mật khẩu
            try (PreparedStatement ps = conn.prepareStatement(
                    sqlInsertUser, new String[]{"user_id"})) {

                ps.setString(1, request.getOwnerName());
                ps.setString(2, request.getOwnerEmail().trim().toLowerCase());
                ps.setString(3, request.getOwnerPasswordHash()); // already BCrypt hashed
                ps.setLong  (4, newRestaurantId);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new RuntimeException("Không lấy được user_id sau INSERT");
                    }
                    newUserId = keys.getLong(1);
                }
            }

            // ── Step 3: UPDATE request status → APPROVED ─────────────────────
            try (PreparedStatement ps = conn.prepareStatement(sqlApproveRequest)) {
                ps.setLong(1, reviewedBy);
                ps.setLong(2, request.getRequestId());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new IllegalStateException(
                            "Đơn #" + request.getRequestId()
                            + " không tồn tại hoặc không ở trạng thái PENDING (đã bị thay đổi?)");
                }
            }

            // ── COMMIT ────────────────────────────────────────────────────────
            conn.commit();

            // ── Gửi email thông báo phê duyệt (fire-and-forget, sau commit) ──
            // Chạy trên daemon thread riêng để không block transaction.
            // Thất bại gửi mail chỉ log warn — KHÔNG rollback nghiệp vụ.
            final RestaurantRequest committedRequest = request;
            Thread emailThread = new Thread(() -> {
                try {
                    com.restaurant.email.EmailService.getInstance()
                            .sendRestaurantApprovalEmail(
                                    committedRequest.getOwnerEmail(),
                                    committedRequest.getOwnerName(),
                                    committedRequest.getRestaurantName(),
                                    committedRequest.getOwnerEmail());
                } catch (Exception emailEx) {
                    System.err.println(
                            "[RestaurantRequestService] Cảnh báo: gửi email phê duyệt thất bại"
                            + " cho đơn #" + committedRequest.getRequestId()
                            + ": " + emailEx.getMessage());
                }
            });
            emailThread.setDaemon(true);
            emailThread.setName("email-approval-" + request.getRequestId());
            emailThread.start();

        } catch (IllegalStateException | SecurityException e) {
            // Business errors — rollback và re-throw
            rollbackQuietly(conn);
            throw e;
        } catch (Exception e) {
            rollbackQuietly(conn);
            throw new RuntimeException(
                    "Lỗi phê duyệt đơn #" + request.getRequestId()
                    + " — đã rollback: " + e.getMessage(), e);
        } finally {
            restoreAndClose(conn);
        }

        // ── Audit log (ngoài transaction — thất bại không rollback nghiệp vụ) ──
        try {
            AuditLogger.getInstance().log(
                    "APPROVE_RESTAURANT_REQUEST",
                    request.getRequestId(),
                    "SUCCESS",
                    "Phê duyệt đơn #" + request.getRequestId()
                    + " → nhà hàng #" + newRestaurantId
                    + ", user #" + newUserId
                    + " bởi admin #" + reviewedBy);
        } catch (Exception auditEx) {
            System.err.println("[RestaurantRequestService] Cảnh báo: ghi audit log thất bại: "
                    + auditEx.getMessage());
        }

        return newRestaurantId;
    }

    // ── REJECT — delegate to DAO ──────────────────────────────────────────────

    /**
     * Từ chối đơn đăng ký với lý do bắt buộc.
     *
     * <p>Delegate sang {@link RestaurantRequestDAO#reject} —
     * chỉ cần cập nhật một bảng nên không cần transaction riêng.
     *
     * @param requestId  request_id cần từ chối
     * @param reviewedBy user_id của SUPER_ADMIN đang thực hiện
     * @param reason     lý do từ chối (không được trống)
     * @throws SecurityException        nếu không có quyền
     * @throws IllegalArgumentException nếu reason trống
     * @throws IllegalStateException    nếu đơn không PENDING
     * @throws RuntimeException         nếu lỗi DB
     */
    public void reject(long requestId, long reviewedBy, String reason) {
        new RestaurantRequestDAO().reject(requestId, reviewedBy, reason);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static void rollbackQuietly(Connection conn) {
        if (conn == null) return;
        try {
            conn.rollback();
        } catch (Exception ex) {
            System.err.println("[RestaurantRequestService] Rollback thất bại: " + ex.getMessage());
        }
    }

    private static void restoreAndClose(Connection conn) {
        if (conn == null) return;
        try {
            conn.setAutoCommit(true);
        } catch (Exception ignored) {}
        try {
            conn.close();
        } catch (Exception ignored) {}
    }
}