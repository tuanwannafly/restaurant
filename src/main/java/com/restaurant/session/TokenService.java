package com.restaurant.session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import com.restaurant.db.DBConnection;

/**
 * Quản lý vòng đời của session token trong bảng {@code SESSION_TOKENS}.
 * <p>
 * <b>Thread-safety:</b> Singleton khởi tạo qua initialization-on-demand holder,
 * tất cả method đều stateless (mọi trạng thái lưu trong DB) nên an toàn
 * với cả Swing EDT và background thread.
 * <p>
 * <b>Thời gian sống mặc định:</b> {@value #SESSION_HOURS} giờ kể từ lúc tạo.
 * Thay đổi hằng số này nếu policy thay đổi — không cần sửa DDL.
 * <p>
 * <b>Cách dùng:</b>
 * <pre>{@code
 *   // Sau khi BCrypt xác thực thành công
 *   String token = TokenService.getInstance().generateSessionToken(userId);
 *   AppSession.getInstance().setSessionToken(token);
 *
 *   // Kiểm tra trước mỗi thao tác nhạy cảm
 *   if (!TokenService.getInstance().validateToken(token)) {
 *       throw new SessionExpiredException();
 *   }
 *
 *   // Khi logout
 *   TokenService.getInstance().revokeToken(token);
 *
 *   // Dọn dẹp định kỳ (gọi từ Swing Timer)
 *   TokenService.getInstance().cleanExpiredTokens();
 * }</pre>
 */
public final class TokenService {

    /** Thời gian sống của một session (giờ). */
    private static final int SESSION_HOURS = 8;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private TokenService() {}

    private static final class Holder {
        static final TokenService INSTANCE = new TokenService();
    }

    public static TokenService getInstance() {
        return Holder.INSTANCE;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sinh UUID v4, INSERT vào {@code SESSION_TOKENS} rồi trả về token string.
     * <p>
     * Mỗi lần đăng nhập thành công tạo một row mới — các phiên cũ chưa
     * logout vẫn còn hiệu lực cho đến khi hết hạn hoặc bị {@link #revokeToken}.
     *
     * @param userId {@code users.user_id} của người vừa đăng nhập
     * @return token string (UUID, 36 ký tự); không bao giờ null
     * @throws RuntimeException nếu không INSERT được vào DB
     */
    public String generateSessionToken(long userId) {
        String token = UUID.randomUUID().toString();

        LocalDateTime now     = LocalDateTime.now();
        LocalDateTime expires = now.plusHours(SESSION_HOURS);

        String sql = """
            INSERT INTO session_tokens
                (token_id, user_id, created_at, expires_at, is_active, ip_address)
            VALUES (?, ?, ?, ?, 1, ?)
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString   (1, token);
            ps.setLong     (2, userId);
            ps.setTimestamp(3, Timestamp.valueOf(now));
            ps.setTimestamp(4, Timestamp.valueOf(expires));
            ps.setString   (5, resolveClientAddress());

            ps.executeUpdate();
            return token;

        } catch (Exception e) {
            throw new RuntimeException(
                "[TokenService] Không thể tạo session token: " + e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra token còn hợp lệ: tồn tại trong DB, {@code is_active = 1}
     * và {@code expires_at > SYSTIMESTAMP}.
     *
     * @param token token cần kiểm tra; nếu null/blank → false
     * @return {@code true} nếu token còn hiệu lực
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;

        String sql = """
            SELECT COUNT(1)
            FROM   session_tokens
            WHERE  token_id  = ?
              AND  is_active  = 1
              AND  expires_at > SYSTIMESTAMP
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }

        } catch (Exception e) {
            System.err.println("[TokenService] validateToken lỗi: " + e.getMessage());
            // Fail-safe: coi là không hợp lệ khi không kết nối được DB
            return false;
        }
    }

    /**
     * Thu hồi token khi người dùng đăng xuất ({@code is_active = 0}).
     * <p>
     * Idempotent: gọi nhiều lần trên cùng một token không gây lỗi.
     *
     * @param token token cần thu hồi; nếu null/blank → bỏ qua
     */
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) return;

        String sql = """
            UPDATE session_tokens
            SET    is_active = 0
            WHERE  token_id  = ?
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, token);
            ps.executeUpdate();

        } catch (Exception e) {
            // Không throw — logout phải thành công dù DB lỗi
            System.err.println("[TokenService] revokeToken lỗi: " + e.getMessage());
        }
    }

    /**
     * Xoá tất cả token đã hết hạn khỏi bảng {@code SESSION_TOKENS}.
     * <p>
     * Nên được gọi định kỳ từ Swing Timer trong {@code MainFrame}
     * (ví dụ: mỗi 30 phút). Không ảnh hưởng đến các token đang hoạt động.
     */
    public void cleanExpiredTokens() {
        String sql = """
            DELETE FROM session_tokens
            WHERE  expires_at < SYSTIMESTAMP
               OR  (is_active = 0 AND created_at < SYSTIMESTAMP - INTERVAL '7' DAY)
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                System.out.println("[TokenService] Đã dọn " + deleted + " token hết hạn.");
            }

        } catch (Exception e) {
            System.err.println("[TokenService] cleanExpiredTokens lỗi: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Lấy địa chỉ máy cục bộ để ghi vào {@code ip_address}.
     * Ứng dụng Swing chạy locally nên trả về hostname.
     * Không bao giờ throw — trả về {@code "DESKTOP"} nếu không lấy được.
     */
    private String resolveClientAddress() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "DESKTOP";
        }
    }
}