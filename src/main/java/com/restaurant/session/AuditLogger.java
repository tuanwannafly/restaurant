package com.restaurant.session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.restaurant.db.DBConnection;

/**
 * Singleton ghi nhật ký kiểm toán (audit log) cho mọi thao tác nhạy cảm.
 * <p>
 * <b>Cách dùng:</b>
 * <pre>{@code
 *   AuditLogger.getInstance().log("DELETE_EMPLOYEE", employeeId, "SUCCESS", "Xoá NV #123");
 *   AuditLogger.getInstance().logLogin("admin@example.com", true);
 * }</pre>
 *
 * <p><b>Thread-safety:</b> Singleton initialization-on-demand holder.
 * Tất cả state trong DB — an toàn với Swing EDT và worker thread.
 *
 * <p><b>Phase 5 — Audit Log Token:</b>
 * <ul>
 *   <li>Ghi kèm {@code session_token} từ {@link AppSession#getSessionToken()}.</li>
 *   <li>Ghi kèm {@code op_token} (8 ký tự) nếu thao tác có OperationToken.</li>
 *   <li>Brute-force detection: đếm LOGIN FAIL trong 15 phút → khoá tài khoản.</li>
 * </ul>
 */
public final class AuditLogger {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private AuditLogger() {}

    private static final class Holder {
        static final AuditLogger INSTANCE = new AuditLogger();
    }

    public static AuditLogger getInstance() {
        return Holder.INSTANCE;
    }

    // ── Model ─────────────────────────────────────────────────────────────────

    /**
     * Bản ghi audit log để hiển thị trong UI.
     */
    public static class AuditEntry {
        public final long   logId;
        public final String action;
        public final Long   actorUserId;
        public final Long   targetId;
        public final String sessionToken;
        public final String opToken;
        public final String result;
        public final String detail;
        public final LocalDateTime loggedAt;

        public AuditEntry(long logId, String action, Long actorUserId, Long targetId,
                          String sessionToken, String opToken, String result,
                          String detail, LocalDateTime loggedAt) {
            this.logId        = logId;
            this.action       = action;
            this.actorUserId  = actorUserId;
            this.targetId     = targetId;
            this.sessionToken = sessionToken;
            this.opToken      = opToken;
            this.result       = result;
            this.detail       = detail;
            this.loggedAt     = loggedAt;
        }
    }

    // ── Core log method ───────────────────────────────────────────────────────

    /**
     * Ghi một bản ghi audit log vào DB.
     *
     * @param action   hành động (e.g. "LOGIN", "DELETE_EMPLOYEE", "CHANGE_ROLE")
     * @param targetId ID đối tượng bị tác động (0 nếu không áp dụng)
     * @param result   "SUCCESS" hoặc "FAIL"
     * @param detail   mô tả chi tiết (tối đa 500 ký tự)
     */
    public void log(String action, long targetId, String result, String detail) {
        AppSession session     = AppSession.getInstance();
        long       actorId     = session.isLoggedIn() ? session.getUserId() : 0L;
        String     sessionTok  = session.getSessionToken();

        insertLog(action, actorId > 0 ? actorId : null,
                  targetId > 0 ? targetId : null,
                  sessionTok, null, result, truncate(detail, 500));
    }

    /**
     * Ghi log với op_token (operation token) đính kèm.
     * Dùng cho các thao tác đã qua {@link OperationTokenService}.
     *
     * @param action   hành động
     * @param targetId ID đối tượng bị tác động
     * @param opToken  operation token 8 ký tự
     * @param result   "SUCCESS" hoặc "FAIL"
     * @param detail   mô tả chi tiết
     */
    public void logWithOpToken(String action, long targetId, String opToken,
                                String result, String detail) {
        AppSession session    = AppSession.getInstance();
        long       actorId    = session.isLoggedIn() ? session.getUserId() : 0L;
        String     sessionTok = session.getSessionToken();

        insertLog(action, actorId > 0 ? actorId : null,
                  targetId > 0 ? targetId : null,
                  sessionTok, opToken, result, truncate(detail, 500));
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    /**
     * Ghi sự kiện đăng nhập (thành công hoặc thất bại).
     *
     * @param email   email người dùng đăng nhập
     * @param userId  user_id nếu tìm thấy trong DB (0 = không tìm thấy)
     * @param success {@code true} nếu đăng nhập thành công
     */
    public void logLogin(String email, long userId, boolean success) {
        String result = success ? "SUCCESS" : "FAIL";
        String detail = success
                ? "Đăng nhập thành công: " + email
                : "Đăng nhập thất bại: " + email;
        insertLog("LOGIN",
                  userId > 0 ? userId : null,
                  null,
                  null,   // chưa có session token lúc login fail
                  null,
                  result,
                  detail);
    }

    /**
     * Ghi sự kiện khoá tài khoản do brute-force.
     *
     * @param email  email bị khoá
     * @param userId user_id nếu biết (0 nếu không)
     */
    public void logAccountLocked(String email, long userId) {
        insertLog("ACCOUNT_LOCKED",
                  userId > 0 ? userId : null,
                  userId > 0 ? userId : null,
                  null, null,
                  "LOCKED",
                  "Khoá tạm thời 15 phút do đăng nhập sai quá 5 lần: " + email);
    }

    /**
     * Ghi sự kiện đổi mật khẩu.
     *
     * @param userId user đổi mật khẩu
     */
    public void logPasswordChange(long userId) {
        log("CHANGE_PASSWORD", userId, "SUCCESS",
            "Đổi mật khẩu cho user #" + userId);
    }

    /**
     * Ghi sự kiện đăng xuất.
     */
    public void logLogout() {
        AppSession session = AppSession.getInstance();
        if (session.isLoggedIn()) {
            log("LOGOUT", session.getUserId(), "SUCCESS",
                "Đăng xuất: " + session.getUserEmail());
        }
    }

    // ── Brute-force detection ─────────────────────────────────────────────────

    /**
     * Đếm số lần đăng nhập thất bại của {@code email} trong 15 phút vừa qua.
     * Query trực tiếp bảng {@code security_audit_log}.
     *
     * @param email email cần kiểm tra
     * @return số lần thất bại; -1 nếu lỗi query
     */
    public int countRecentLoginFailures(String email) {
        String sql = """
            SELECT COUNT(*) FROM security_audit_log
             WHERE action     = 'LOGIN'
               AND result     = 'FAIL'
               AND detail LIKE ?
               AND logged_at  > SYSTIMESTAMP - 15/(24*60)
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%Đăng nhập thất bại: " + email + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            System.err.println("[AuditLogger] countRecentLoginFailures lỗi: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Kiểm tra tài khoản có đang bị khoá (ACCOUNT_LOCKED trong 15 phút) không.
     *
     * @param email email cần kiểm tra
     * @return {@code true} nếu tài khoản đang bị khoá
     */
    public boolean isAccountLocked(String email) {
        String sql = """
            SELECT COUNT(*) FROM security_audit_log
             WHERE action   = 'ACCOUNT_LOCKED'
               AND detail  LIKE ?
               AND logged_at > SYSTIMESTAMP - 15/(24*60)
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + email + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            System.err.println("[AuditLogger] isAccountLocked lỗi: " + e.getMessage());
        }
        return false;
    }

    // ── Read for SUPER_ADMIN ──────────────────────────────────────────────────

    /**
     * Lấy danh sách audit log gần nhất, giới hạn {@code limit} bản ghi.
     * Chỉ nên gọi khi người dùng là SUPER_ADMIN.
     *
     * @param limit  số bản ghi tối đa trả về (tối đa 500)
     * @return danh sách {@link AuditEntry}, mới nhất đứng đầu
     */
    public List<AuditEntry> getRecentLogs(int limit) {
        return queryLogs(null, null, null, Math.min(limit, 500));
    }

    /**
     * Lấy danh sách audit log với bộ lọc.
     *
     * @param action    lọc theo action (null = tất cả)
     * @param fromTime  từ thời điểm (null = không giới hạn)
     * @param toTime    đến thời điểm (null = không giới hạn)
     * @param limit     số bản ghi tối đa (tối đa 500)
     * @return danh sách {@link AuditEntry}
     */
    public List<AuditEntry> getFilteredLogs(String action,
                                             LocalDateTime fromTime,
                                             LocalDateTime toTime,
                                             int limit) {
        return queryLogs(action, fromTime, toTime, Math.min(limit, 500));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void insertLog(String action, Long actorUserId, Long targetId,
                           String sessionToken, String opToken,
                           String result, String detail) {
        String sql = """
            INSERT INTO security_audit_log
                (action, actor_user_id, target_id, session_token, op_token, result, detail)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, truncate(action, 50));
            if (actorUserId != null) ps.setLong  (2, actorUserId); else ps.setNull(2, java.sql.Types.NUMERIC);
            if (targetId    != null) ps.setLong  (3, targetId);    else ps.setNull(3, java.sql.Types.NUMERIC);
            ps.setString(4, sessionToken != null ? truncate(sessionToken, 36) : null);
            ps.setString(5, opToken      != null ? truncate(opToken,      16) : null);
            ps.setString(6, truncate(result, 10));
            ps.setString(7, detail);

            ps.executeUpdate();

        } catch (Exception e) {
            // Không bao giờ để lỗi audit log chặn luồng chính
            System.err.println("[AuditLogger] insertLog lỗi: " + e.getMessage());
        }
    }

    private List<AuditEntry> queryLogs(String actionFilter,
                                        LocalDateTime from,
                                        LocalDateTime to,
                                        int limit) {
        List<AuditEntry> list = new ArrayList<>();

        StringBuilder sb = new StringBuilder("""
            SELECT log_id, action, actor_user_id, target_id,
                   session_token, op_token, result, detail, logged_at
              FROM security_audit_log
             WHERE 1=1
            """);

        if (actionFilter != null && !actionFilter.isBlank())
            sb.append(" AND action = ?");
        if (from != null)
            sb.append(" AND logged_at >= ?");
        if (to != null)
            sb.append(" AND logged_at <= ?");

        sb.append(" ORDER BY logged_at DESC");
        sb.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY");

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {

            int idx = 1;
            if (actionFilter != null && !actionFilter.isBlank())
                ps.setString(idx++, actionFilter);
            if (from != null)
                ps.setTimestamp(idx++, Timestamp.valueOf(from));
            if (to != null)
                ps.setTimestamp(idx++, Timestamp.valueOf(to));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long        logId    = rs.getLong("log_id");
                    String      action   = rs.getString("action");
                    long        actorRaw = rs.getLong("actor_user_id");
                    Long        actor    = rs.wasNull() ? null : actorRaw;
                    long        tgtRaw   = rs.getLong("target_id");
                    Long        target   = rs.wasNull() ? null : tgtRaw;
                    String      sessTok  = rs.getString("session_token");
                    String      opTok    = rs.getString("op_token");
                    String      result   = rs.getString("result");
                    String      detail   = rs.getString("detail");
                    Timestamp   ts       = rs.getTimestamp("logged_at");
                    LocalDateTime ldt    = ts != null ? ts.toLocalDateTime() : null;

                    list.add(new AuditEntry(logId, action, actor, target,
                                            sessTok, opTok, result, detail, ldt));
                }
            }
        } catch (Exception e) {
            System.err.println("[AuditLogger] queryLogs lỗi: " + e.getMessage());
        }
        return list;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}