package com.restaurant.session;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.HexFormat;

import com.restaurant.db.DBConnection;

/**
 * Quản lý vòng đời của <b>refresh token</b> trong bảng {@code REFRESH_TOKENS}.
 *
 * <p>Refresh token là token dài hạn (30 ngày) được lưu trên máy client
 * ({@link TokenStorage}) và dùng để tự động gia hạn session khi session token
 * hết hạn — người dùng không cần đăng nhập lại.
 *
 * <h3>Luồng hoạt động</h3>
 * <ol>
 *   <li>Đăng nhập thành công + checkbox "Ghi nhớ" → {@link #generateRefreshToken(long)}
 *       sinh token và lưu vào DB + file mã hoá trên disk.</li>
 *   <li>Khởi động app → {@link TokenStorage#loadRefreshToken()} → nếu có →
 *       {@link #validateAndRotate(String)} kiểm tra + xoay token mới → load session.</li>
 *   <li>Đổi mật khẩu → {@link #revokeAllForUser(long)} thu hồi mọi thiết bị.</li>
 *   <li>Đăng xuất bình thường → {@link #revokeToken(String)} thu hồi token thiết bị hiện tại.</li>
 * </ol>
 *
 * <p><b>Token rotation:</b> mỗi lần {@link #validateAndRotate} thành công, token cũ
 * bị revoke và token mới được sinh ra — giảm thiểu nguy cơ replay attack.
 *
 * <p><b>Thread-safety:</b> Singleton initialization-on-demand; mọi trạng thái
 * trong DB nên stateless và an toàn cho cả EDT lẫn background thread.
 */
public final class RefreshTokenService {

    /** Số byte ngẫu nhiên để sinh token (128 byte → 256 hex chars, cắt còn 128). */
    private static final int  TOKEN_BYTES   = 64;   // 64 bytes → 128 hex chars = VARCHAR2(128)
    /** Thời hạn sống của một refresh token (ngày). */
    private static final int  TOKEN_DAYS    = 30;

    private static final SecureRandom RNG = new SecureRandom();

    // ── Singleton ─────────────────────────────────────────────────────────────

    private RefreshTokenService() {}

    private static final class Holder {
        static final RefreshTokenService INSTANCE = new RefreshTokenService();
    }

    public static RefreshTokenService getInstance() {
        return Holder.INSTANCE;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sinh refresh token 128-hex-char, INSERT vào {@code REFRESH_TOKENS}
     * với {@code expires_at = SYSTIMESTAMP + 30 ngày}.
     *
     * @param userId {@code users.user_id} của người vừa đăng nhập
     * @return token string (128 ký tự hex); không bao giờ null
     * @throws RuntimeException nếu không INSERT được vào DB
     */
    public String generateRefreshToken(long userId) {
        String token = randomHexToken();

        LocalDateTime now     = LocalDateTime.now();
        LocalDateTime expires = now.plusDays(TOKEN_DAYS);
        String        device  = resolveDeviceName();

        String sql = """
            INSERT INTO refresh_tokens
                (token, user_id, device_name, created_at, expires_at, revoked)
            VALUES (?, ?, ?, ?, ?, 0)
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString   (1, token);
            ps.setLong     (2, userId);
            ps.setString   (3, device);
            ps.setTimestamp(4, Timestamp.valueOf(now));
            ps.setTimestamp(5, Timestamp.valueOf(expires));

            ps.executeUpdate();
            return token;

        } catch (Exception e) {
            throw new RuntimeException(
                "[RefreshTokenService] Không thể tạo refresh token: " + e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra refresh token hợp lệ, chưa revoked, chưa hết hạn.
     * Nếu hợp lệ: revoke token cũ → sinh token mới → trả về {@code Optional<Long>} chứa userId.
     * Nếu không hợp lệ: trả về {@code Optional.empty()}.
     *
     * <p><b>Token rotation</b> đảm bảo mỗi token chỉ dùng được một lần —
     * nếu token bị đánh cắp, kẻ tấn công và người dùng hợp lệ sẽ có token khác nhau,
     * hệ thống phát hiện được ngay lần tiếp theo.
     *
     * @param token refresh token cần kiểm tra; null/blank → empty
     * @return Optional chứa userId nếu hợp lệ, ngược lại empty
     */
    public Optional<Long> validateAndRotate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        String selectSql = """
            SELECT user_id FROM refresh_tokens
            WHERE  token      = ?
              AND  revoked    = 0
              AND  expires_at > SYSTIMESTAMP
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {

            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                long userId = rs.getLong("user_id");

                // Revoke token cũ (rotation — dùng cùng connection để atomic hơn)
                revokeTokenOnConn(conn, token);

                // Sinh token mới và lưu DB
                String newToken = generateRefreshToken(userId);

                // Lưu token mới vào disk (replace file cũ)
                TokenStorage.getInstance().saveRefreshToken(newToken);

                return Optional.of(userId);
            }

        } catch (Exception e) {
            System.err.println("[RefreshTokenService] validateAndRotate lỗi: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Thu hồi toàn bộ refresh token của một user (dùng khi đổi mật khẩu
     * hoặc bấm "Đăng xuất tất cả thiết bị").
     *
     * @param userId user_id cần thu hồi
     */
    public void revokeAllForUser(long userId) {
        String sql = """
            UPDATE refresh_tokens SET revoked = 1
            WHERE  user_id = ?
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            int rows = ps.executeUpdate();
            System.out.println("[RefreshTokenService] Đã thu hồi " + rows
                    + " refresh token của user_id=" + userId);

        } catch (Exception e) {
            System.err.println("[RefreshTokenService] revokeAllForUser lỗi: " + e.getMessage());
        }
    }

    /**
     * Thu hồi một refresh token cụ thể (dùng khi đăng xuất bình thường từ thiết bị hiện tại).
     *
     * @param token token cần thu hồi; null/blank → bỏ qua
     */
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) return;

        String sql = """
            UPDATE refresh_tokens SET revoked = 1
            WHERE  token = ?
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, token);
            ps.executeUpdate();

        } catch (Exception e) {
            // Không throw — logout phải thành công dù DB lỗi
            System.err.println("[RefreshTokenService] revokeToken lỗi: " + e.getMessage());
        }
    }

    /**
     * Xoá các refresh token đã hết hạn hoặc revoked lâu hơn 7 ngày (dọn dẹp DB định kỳ).
     */
    public void cleanExpiredTokens() {
        String sql = """
            DELETE FROM refresh_tokens
            WHERE  expires_at < SYSTIMESTAMP
               OR  (revoked = 1
                    AND created_at < SYSTIMESTAMP - INTERVAL '7' DAY)
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                System.out.println("[RefreshTokenService] Đã dọn " + deleted
                        + " refresh token hết hạn.");
            }

        } catch (Exception e) {
            System.err.println("[RefreshTokenService] cleanExpiredTokens lỗi: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Sinh chuỗi hex ngẫu nhiên 128 ký tự (64 bytes). */
    private static String randomHexToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes); // Java 17+
    }

    /** Lấy hostname của máy để ghi vào device_name; fallback về "DESKTOP". */
    private static String resolveDeviceName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "DESKTOP";
        }
    }

    /**
     * Revoke token dùng connection đã có sẵn (tránh mở connection mới trong token rotation).
     * Internal use only.
     */
    private static void revokeTokenOnConn(Connection conn, String token) throws Exception {
        String sql = "UPDATE refresh_tokens SET revoked = 1 WHERE token = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
    }
}
