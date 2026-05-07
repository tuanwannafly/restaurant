package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;

import com.restaurant.db.DBConnection;
import com.restaurant.email.EmailService;

/**
 * DAO quản lý luồng OTP quên mật khẩu.
 *
 * <p>Ánh xạ bảng {@code PASSWORD_RESET_TOKENS}.
 * Không có trạng thái nội bộ — mọi dữ liệu đều được đọc/ghi trực tiếp từ DB.
 *
 * <h3>Luồng sử dụng</h3>
 * <pre>
 *  1. UI gọi generateOtp(email)  → nhận OTP (đã gửi mail tự động)
 *  2. User nhập OTP vào form
 *  3. UI gọi UserDAO.resetPassword(email, otp, newPassword)
 *     ├─ gọi verifyOtp(email, otp)  → true/false
 *     ├─ BCrypt hash + UPDATE users.password
 *     └─ gọi markUsed(email, otp)
 * </pre>
 */
public class PasswordResetDAO {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static PasswordResetDAO instance;

    private PasswordResetDAO() {}

    public static PasswordResetDAO getInstance() {
        if (instance == null) {
            instance = new PasswordResetDAO();
        }
        return instance;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Thời gian OTP có hiệu lực (phút). */
    private static final int OTP_TTL_MINUTES = 15;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sinh OTP 6 số cho tài khoản có địa chỉ {@code email}, lưu vào DB
     * và gửi email thông báo cho người dùng.
     *
     * <p>Các bước thực hiện:
     * <ol>
     *   <li>Tra cứu {@code user_id} từ bảng {@code users} theo email.</li>
     *   <li>Vô hiệu hoá toàn bộ token cũ chưa dùng của cùng user
     *       (UPDATE {@code used = 'Y'}).</li>
     *   <li>Sinh mã 6 số ngẫu nhiên, zero-padded.</li>
     *   <li>INSERT bản ghi mới vào {@code PASSWORD_RESET_TOKENS}
     *       với {@code expires_at = SYSTIMESTAMP + 15 phút}.</li>
     *   <li>Gửi email HTML chứa OTP qua {@link EmailService}.</li>
     * </ol>
     *
     * @param email địa chỉ email đã đăng ký, không được null/rỗng
     * @return mã OTP 6 chữ số dạng chuỗi (ví dụ {@code "047293"})
     * @throws IllegalArgumentException nếu email không tồn tại trong hệ thống
     * @throws RuntimeException         nếu lỗi DB
     */
    public String generateOtp(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email không được để trống.");
        }
        String normalizedEmail = email.trim().toLowerCase();

        // 1. Tìm user_id theo email (chỉ tài khoản ACTIVE)
        long userId = findActiveUserId(normalizedEmail);

        // 2. Vô hiệu hoá token cũ chưa dùng của user này
        invalidateOldTokens(userId);

        // 3. Sinh mã OTP 6 số, zero-padded
        String otp = String.format("%06d", new Random().nextInt(1_000_000));

        // 4. INSERT token mới vào DB
        insertOtp(userId, otp);

        // 5. Gửi email HTML (bất đồng bộ, không block)
        String subject  = "[SmartRestaurant] Mã xác nhận đặt lại mật khẩu";
        String htmlBody = buildEmailHtml(otp);
        EmailService.getInstance().sendHtml(normalizedEmail, subject, htmlBody);

        return otp;
    }

    /**
     * Kiểm tra OTP có hợp lệ không.
     *
     * <p>OTP hợp lệ khi thoả mãn đồng thời:
     * <ul>
     *   <li>Khớp với {@code otp_code} trong bảng.</li>
     *   <li>Thuộc về user có {@code email} tương ứng.</li>
     *   <li>Chưa được dùng ({@code used = 'N'}).</li>
     *   <li>Chưa hết hạn ({@code expires_at > SYSTIMESTAMP}).</li>
     * </ul>
     *
     * @param email địa chỉ email của tài khoản
     * @param otp   mã OTP 6 số người dùng nhập vào
     * @return {@code true} nếu OTP hợp lệ, {@code false} trong mọi trường hợp còn lại
     */
    public boolean verifyOtp(String email, String otp) {
        if (email == null || email.isBlank() || otp == null || otp.isBlank()) {
            return false;
        }

        String sql = """
            SELECT 1
            FROM   password_reset_tokens prt
            JOIN   users u ON u.user_id = prt.user_id
            WHERE  LOWER(u.email)  = LOWER(?)
              AND  prt.otp_code    = ?
              AND  prt.used        = 'N'
              AND  prt.expires_at  > SYSTIMESTAMP
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email.trim());
            ps.setString(2, otp.trim());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            System.err.println("[PasswordResetDAO] verifyOtp lỗi: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đánh dấu OTP đã được dùng để ngăn replay attack.
     *
     * <p>Cập nhật {@code used = 'Y'} cho token khớp với cặp
     * ({@code email}, {@code otp}) và vẫn còn trạng thái {@code 'N'}.
     *
     * @param email địa chỉ email của tài khoản
     * @param otp   mã OTP 6 số cần huỷ
     * @throws RuntimeException nếu lỗi DB
     */
    public void markUsed(String email, String otp) {
        if (email == null || email.isBlank() || otp == null || otp.isBlank()) {
            return;
        }

        String sql = """
            UPDATE password_reset_tokens prt
            SET    prt.used = 'Y'
            WHERE  prt.otp_code = ?
              AND  prt.used     = 'N'
              AND  prt.user_id  = (
                       SELECT user_id
                       FROM   users
                       WHERE  LOWER(email) = LOWER(?)
                   )
            """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, otp.trim());
            ps.setString(2, email.trim());
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException(
                "[PasswordResetDAO] markUsed lỗi: " + e.getMessage(), e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Tìm {@code user_id} của tài khoản ACTIVE theo email.
     *
     * @throws IllegalArgumentException nếu không tìm thấy
     */
    private long findActiveUserId(String normalizedEmail) {
        String sql = "SELECT user_id FROM users "
                   + "WHERE LOWER(email) = ? AND status = 'ACTIVE'";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, normalizedEmail);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException(
                        "Không tìm thấy tài khoản ACTIVE với email: " + normalizedEmail);
                }
                return rs.getLong("user_id");
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                "[PasswordResetDAO] Lỗi tìm user theo email: " + e.getMessage(), e);
        }
    }

    /**
     * Vô hiệu hoá tất cả token OTP chưa dùng ({@code used = 'N'})
     * của {@code userId} trước khi tạo token mới.
     */
    private void invalidateOldTokens(long userId) {
        String sql = "UPDATE password_reset_tokens "
                   + "SET used = 'Y' "
                   + "WHERE user_id = ? AND used = 'N'";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.executeUpdate();

        } catch (Exception e) {
            System.err.println("[PasswordResetDAO] invalidateOldTokens lỗi (không nghiêm trọng): "
                    + e.getMessage());
        }
    }

    /**
     * Chèn bản ghi OTP mới vào {@code PASSWORD_RESET_TOKENS}.
     * {@code expires_at} được tính bởi DB: {@code SYSTIMESTAMP + 15 phút}.
     */
    private void insertOtp(long userId, String otp) {
        String sql = "INSERT INTO password_reset_tokens "
                   + "    (user_id, otp_code, expires_at) "
                   + "VALUES (?, ?, SYSTIMESTAMP + INTERVAL '"
                   + OTP_TTL_MINUTES + "' MINUTE)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong  (1, userId);
            ps.setString(2, otp);
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException(
                "[PasswordResetDAO] Lỗi lưu OTP vào DB: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo nội dung HTML email chứa mã OTP.
     * Template tiếng Việt, thương hiệu SmartRestaurant.
     */
    private String buildEmailHtml(String otp) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Đặt lại mật khẩu – SmartRestaurant</title>
            </head>
            <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,Helvetica,sans-serif;">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                     style="background:#f5f5f5;padding:40px 0;">
                <tr>
                  <td align="center">
                    <!-- Card container -->
                    <table role="presentation" width="520" cellspacing="0" cellpadding="0"
                           style="background:#ffffff;border-radius:12px;
                                  box-shadow:0 2px 12px rgba(0,0,0,0.08);overflow:hidden;">

                      <!-- Header -->
                      <tr>
                        <td align="center"
                            style="background:#d32f2f;padding:28px 32px;">
                          <span style="font-size:22px;font-weight:700;
                                       color:#ffffff;letter-spacing:1px;">
                            🍽️ SmartRestaurant
                          </span>
                        </td>
                      </tr>

                      <!-- Body -->
                      <tr>
                        <td style="padding:36px 40px;">
                          <p style="margin:0 0 12px;font-size:16px;color:#333;">
                            Xin chào,
                          </p>
                          <p style="margin:0 0 24px;font-size:15px;color:#555;line-height:1.6;">
                            Chúng tôi nhận được yêu cầu <strong>đặt lại mật khẩu</strong>
                            cho tài khoản của bạn tại hệ thống <strong>SmartRestaurant</strong>.
                            Vui lòng sử dụng mã xác nhận dưới đây:
                          </p>

                          <!-- OTP box -->
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                            <tr>
                              <td align="center" style="padding:8px 0 28px;">
                                <span style="display:inline-block;
                                             font-size:40px;font-weight:700;
                                             letter-spacing:14px;
                                             color:#d32f2f;
                                             background:#fff8f8;
                                             border:2px dashed #d32f2f;
                                             border-radius:10px;
                                             padding:16px 32px;">
                                  %s
                                </span>
                              </td>
                            </tr>
                          </table>

                          <p style="margin:0 0 8px;font-size:14px;color:#777;text-align:center;">
                            ⏱️ Mã này <strong>hết hạn sau 15 phút</strong>.
                          </p>
                          <p style="margin:0 0 28px;font-size:13px;color:#aaa;text-align:center;">
                            Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email và
                            mật khẩu của bạn sẽ không bị thay đổi.
                          </p>

                          <hr style="border:none;border-top:1px solid #eee;margin:0 0 24px;" />

                          <p style="margin:0;font-size:13px;color:#aaa;text-align:center;">
                            Đây là email tự động — vui lòng không trả lời.
                          </p>
                        </td>
                      </tr>

                      <!-- Footer -->
                      <tr>
                        <td align="center"
                            style="background:#fafafa;border-top:1px solid #eee;
                                   padding:18px 32px;">
                          <p style="margin:0;font-size:12px;color:#bbb;">
                            © 2025 SmartRestaurant. Mọi quyền được bảo lưu.
                          </p>
                        </td>
                      </tr>

                    </table>
                    <!-- /Card container -->
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(otp);
    }
}