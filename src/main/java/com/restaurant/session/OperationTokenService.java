package com.restaurant.session;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import com.restaurant.db.DBConnection;

/**
 * Quản lý vòng đời của Operation Token — mã xác nhận ngắn hạn (5 phút)
 * dùng để bảo vệ các thao tác nhạy cảm không thể hoàn tác.
 *
 * <p><b>Luồng sử dụng điển hình:</b>
 * <ol>
 *   <li>Người dùng bấm nút Delete / Change Role.</li>
 *   <li>Caller gọi {@link #issueToken(OperationType, long)} → nhận token 8 ký tự.</li>
 *   <li>Hiện {@code ConfirmOperationDialog} để người dùng nhập lại token.</li>
 *   <li>Nếu người dùng nhập đúng, {@link #confirmToken(String, OperationType, long)}
 *       trả {@code true} và đánh dấu {@code used = 1}.</li>
 *   <li>Caller thực thi DAO chỉ khi {@code confirmToken} = {@code true}.</li>
 * </ol>
 *
 * <p><b>Thread-safety:</b> Singleton khởi tạo qua initialization-on-demand holder.
 * Tất cả state lưu trong DB — an toàn với Swing EDT và background thread.
 */
public final class OperationTokenService {

    /** Thời gian sống của một operation token (phút). */
    private static final int TOKEN_MINUTES = 5;

    /** Độ dài token (ký tự alphanumeric in hoa). */
    private static final int TOKEN_LENGTH = 8;

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // loại bỏ O/0, I/1 dễ nhầm

    private final SecureRandom rng = new SecureRandom();

    // ── Singleton ─────────────────────────────────────────────────────────────

    private OperationTokenService() {}

    private static final class Holder {
        static final OperationTokenService INSTANCE = new OperationTokenService();
    }

    public static OperationTokenService getInstance() {
        return Holder.INSTANCE;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sinh token mới, INSERT vào {@code operation_tokens} và trả về token string.
     *
     * <p>Mỗi lần gọi tạo một row mới. Token cũ chưa dùng vẫn còn hiệu lực cho đến
     * khi hết hạn, nhưng {@code confirmToken} sẽ chỉ chấp nhận token khớp đúng
     * {@code type} + {@code targetId}.
     *
     * @param type     loại thao tác nhạy cảm
     * @param targetId ID của đối tượng bị tác động (user_id, restaurant_id, v.v.)
     * @return token 8 ký tự uppercase alphanumeric; không bao giờ null
     * @throws RuntimeException nếu không INSERT được vào DB
     */
    public String issueToken(OperationType type, long targetId) {
        String token = generateRawToken();

        LocalDateTime now     = LocalDateTime.now();
        LocalDateTime expires = now.plusMinutes(TOKEN_MINUTES);

        long actorUserId = AppSession.getInstance().getUserId();

        String sql = """
            INSERT INTO operation_tokens
                (token, operation_type, actor_user_id, target_id, created_at, expires_at, used)
            VALUES (?, ?, ?, ?, ?, ?, 0)
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString   (1, token);
            ps.setString   (2, type.toDbValue());
            ps.setLong     (3, actorUserId);
            ps.setLong     (4, targetId);
            ps.setTimestamp(5, Timestamp.valueOf(now));
            ps.setTimestamp(6, Timestamp.valueOf(expires));

            ps.executeUpdate();
            return token;

        } catch (Exception e) {
            throw new RuntimeException(
                "[OperationTokenService] Không thể phát hành token: " + e.getMessage(), e);
        }
    }

    /**
     * Xác nhận token: kiểm tra token tồn tại, khớp {@code type} + {@code targetId},
     * chưa dùng, và chưa hết hạn. Nếu hợp lệ thì đánh dấu {@code used = 1}.
     *
     * @param token    token người dùng nhập lại (so sánh case-insensitive)
     * @param type     loại thao tác cần khớp
     * @param targetId ID đối tượng cần khớp
     * @return {@code true} nếu token hợp lệ và đã được đánh dấu đã dùng
     */
    public boolean confirmToken(String token, OperationType type, long targetId) {
        if (token == null || token.isBlank()) return false;

        String normalized = token.trim().toUpperCase();

        String selectSql = """
            SELECT token
            FROM   operation_tokens
            WHERE  token          = ?
              AND  operation_type = ?
              AND  target_id      = ?
              AND  used           = 0
              AND  expires_at     > SYSTIMESTAMP
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {

            ps.setString(1, normalized);
            ps.setString(2, type.toDbValue());
            ps.setLong  (3, targetId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false; // không tìm thấy hoặc không hợp lệ
                }
            }

            // Token hợp lệ → đánh dấu đã dùng
            String updateSql = """
                UPDATE operation_tokens
                SET    used = 1
                WHERE  token = ?
                """;
            try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                upd.setString(1, normalized);
                upd.executeUpdate();
            }

            return true;

        } catch (Exception e) {
            System.err.println("[OperationTokenService] confirmToken lỗi: " + e.getMessage());
            return false;
        }
    }

    /**
     * Xoá các token đã hết hạn hoặc đã dùng quá 24 giờ khỏi bảng.
     * Nên gọi định kỳ từ Swing Timer trong {@code MainFrame}.
     */
    public void cleanExpiredTokens() {
        String sql = """
            DELETE FROM operation_tokens
            WHERE  expires_at < SYSTIMESTAMP
               OR  (used = 1 AND created_at < SYSTIMESTAMP - INTERVAL '1' DAY)
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                System.out.println("[OperationTokenService] Đã dọn " + deleted + " operation token.");
            }

        } catch (Exception e) {
            System.err.println("[OperationTokenService] cleanExpiredTokens lỗi: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Sinh chuỗi ngẫu nhiên {@value #TOKEN_LENGTH} ký tự từ {@link #ALPHABET}. */
    private String generateRawToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(ALPHABET.charAt(rng.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
