package com.restaurant.email;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Singleton quản lý gửi email qua SMTP.
 * Đọc cấu hình từ {@code email.properties} trong classpath.
 *
 * <p>Thiết kế bám theo pattern của {@link com.restaurant.db.DBConnection}:
 * private constructor, static getInstance(), loadConfig() tách biệt.
 *
 * <p>Tất cả exception đều được bắt và log ra {@code System.err} / Logger
 * — không throw ra ngoài để tránh crash JavaFX thread.
 */
public class EmailService {

    private static final Logger LOGGER = Logger.getLogger(EmailService.class.getName());

    /** Timeout kết nối SMTP (ms). */
    private static final String CONNECT_TIMEOUT_MS = "5000";

    /** Timeout đọc/ghi socket SMTP (ms). */
    private static final String IO_TIMEOUT_MS = "10000";

    // -----------------------------------------------------------------------
    // Singleton
    // -----------------------------------------------------------------------

    private static EmailService instance;

    public static EmailService getInstance() {
        if (instance == null) {
            instance = new EmailService();
        }
        return instance;
    }

    private EmailService() {
        loadConfig();
    }

    // -----------------------------------------------------------------------
    // Cấu hình
    // -----------------------------------------------------------------------

    private Properties smtpProps;   // thuộc tính truyền vào Session
    private String     fromAddress; // địa chỉ From
    private String     username;    // tên đăng nhập SMTP
    private String     password;    // mật khẩu / App Password

    /**
     * Nạp cấu hình từ {@code email.properties} trong classpath.
     * Ném {@link RuntimeException} nếu file không tồn tại.
     */
   private void loadConfig() {
        // Tải email.properties làm fallback (nếu có)
        Properties raw = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("email.properties")) {
            if (in != null) {
                raw.load(in);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "[EmailService] Không thể đọc email.properties — dùng env var", e);
        }
 
        // Ưu tiên: env var → properties file
        this.fromAddress = resolveEmail(raw, "MAIL_FROM",     "mail.from");
        this.username    = resolveEmail(raw, "MAIL_USERNAME", "mail.username");
        this.password    = resolveEmail(raw, "MAIL_PASSWORD", "mail.password");
 
        String smtpHost = resolveEmailDefault(raw, "MAIL_SMTP_HOST", "mail.smtp.host", "smtp.gmail.com");
        String smtpPort = resolveEmailDefault(raw, "MAIL_SMTP_PORT", "mail.smtp.port", "587");
 
        smtpProps = new Properties();
        smtpProps.put("mail.smtp.host",             smtpHost);
        smtpProps.put("mail.smtp.port",             smtpPort);
        smtpProps.put("mail.smtp.auth",             raw.getProperty("mail.smtp.auth", "true"));
        smtpProps.put("mail.smtp.starttls.enable",  raw.getProperty("mail.smtp.starttls.enable", "true"));
        smtpProps.put("mail.smtp.connectiontimeout", CONNECT_TIMEOUT_MS);
        smtpProps.put("mail.smtp.timeout",           IO_TIMEOUT_MS);
        smtpProps.put("mail.smtp.writetimeout",      IO_TIMEOUT_MS);
    }

    // -----------------------------------------------------------------------
    // Tạo Session có xác thực
    // -----------------------------------------------------------------------

    private Session createSession() {
        return Session.getInstance(smtpProps, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }
    
    private String resolveEmail(Properties props, String envKey, String propKey) {
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        return props.getProperty(propKey);
    }
 
    private String resolveEmailDefault(Properties props, String envKey, String propKey, String defaultVal) {
        String v = resolveEmail(props, envKey, propKey);
        return (v != null && !v.isBlank()) ? v : defaultVal;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Gửi email HTML bất đồng bộ trên daemon thread.
     *
     * <p>Phương thức trả về ngay lập tức — không block JavaFX Application Thread.
     * Lỗi gửi (mạng, xác thực, …) được log ra {@code System.err} và Logger;
     * không có callback kết quả. Nếu cần biết kết quả, hãy dùng overload
     * nhận {@code Consumer<Boolean>} callback.
     *
     * @param toEmail   địa chỉ người nhận, không được null/rỗng
     * @param subject   tiêu đề thư
     * @param htmlBody  nội dung HTML đầy đủ
     */
    public void sendHtml(String toEmail, String subject, String htmlBody) {
        if (toEmail == null || toEmail.isBlank()) {
            LOGGER.warning("[EmailService] sendHtml bị bỏ qua: toEmail trống.");
            return;
        }

        Thread worker = new Thread(() -> {
            try {
                Session session = createSession();
                MimeMessage msg = new MimeMessage(session);

                msg.setFrom(new InternetAddress(fromAddress));
                msg.setRecipients(Message.RecipientType.TO,
                        InternetAddress.parse(toEmail, false));
                msg.setSubject(subject, "UTF-8");
                msg.setContent(htmlBody, "text/html; charset=UTF-8");

                Transport.send(msg);
                LOGGER.info("[EmailService] Đã gửi email tới: " + toEmail);

            } catch (MessagingException e) {
                LOGGER.log(Level.SEVERE,
                        "[EmailService] Gửi email thất bại tới " + toEmail, e);
                System.err.println("[EmailService] Gửi email thất bại: " + e.getMessage());
            }
        });

        // Daemon thread: JVM không chờ thread này khi shutdown
        worker.setDaemon(true);
        worker.setName("email-sender");
        worker.start();
    }

    /**
     * Kiểm tra kết nối SMTP có hoạt động không.
     *
     * <p>Mở một {@link Transport}, xác thực, rồi đóng lại ngay.
     * Toàn bộ exception được bắt nội bộ; không throw ra ngoài.
     *
     * @return {@code true} nếu kết nối và xác thực thành công,
     *         {@code false} nếu có lỗi bất kỳ
     */
    public boolean testConnection() {
        try {
            Session session = createSession();
            Transport transport = session.getTransport("smtp");
            transport.connect(
                    smtpProps.getProperty("mail.smtp.host"),
                    Integer.parseInt(smtpProps.getProperty("mail.smtp.port")),
                    username,
                    password);
            transport.close();
            LOGGER.info("[EmailService] testConnection – kết nối SMTP thành công.");
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "[EmailService] testConnection – kết nối SMTP thất bại", e);
            System.err.println("[EmailService] testConnection thất bại: " + e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Các mẫu email nghiệp vụ (high-level API)
    // -----------------------------------------------------------------------

    /**
     * Gửi email thông báo cho admin hiện có khi được gán quản lý nhà hàng mới.
     *
     * @param toEmail        địa chỉ email admin
     * @param adminName      họ tên admin
     * @param restaurantName tên nhà hàng vừa được gán
     */
    public void sendAdminAssignedEmail(String toEmail,
                                       String adminName,
                                       String restaurantName) {
        String subject = "📋 Bạn đã được gán quản lý nhà hàng " + restaurantName;

        String html = "<!DOCTYPE html>"
            + "<html lang=\"vi\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<style>"
            + "  body{margin:0;padding:0;background:#f4f4f7;font-family:Arial,sans-serif;color:#333}"
            + "  .wrapper{max-width:600px;margin:32px auto;background:#fff;border-radius:8px;"
            + "            box-shadow:0 2px 8px rgba(0,0,0,.1);overflow:hidden}"
            + "  .header{background:#7C3AED;padding:32px 24px;text-align:center}"
            + "  .header h1{margin:0;color:#fff;font-size:22px;letter-spacing:.5px}"
            + "  .header p{margin:6px 0 0;color:#DDD6FE;font-size:14px}"
            + "  .body{padding:32px 24px}"
            + "  .highlight{background:#F5F3FF;border-left:4px solid #7C3AED;padding:16px 20px;"
            + "              border-radius:4px;margin:20px 0}"
            + "  .highlight p{margin:6px 0;font-size:15px}"
            + "  .highlight strong{color:#5B21B6}"
            + "  .btn{display:inline-block;margin:24px 0;padding:12px 32px;"
            + "        background:#7C3AED;color:#fff!important;text-decoration:none;"
            + "        border-radius:6px;font-size:15px;font-weight:bold}"
            + "  .footer{background:#f8fafc;padding:16px 24px;text-align:center;"
            + "           font-size:12px;color:#94a3b8;border-top:1px solid #e2e8f0}"
            + "</style></head><body>"
            + "<div class=\"wrapper\">"
            + "  <div class=\"header\">"
            + "    <h1>📋 Bạn được gán quản lý nhà hàng mới!</h1>"
            + "    <p>SmartRestaurant — Hệ thống quản lý nhà hàng</p>"
            + "  </div>"
            + "  <div class=\"body\">"
            + "    <p>Xin chào <strong>" + escapeHtml(adminName) + "</strong>,</p>"
            + "    <p>Quản trị viên hệ thống vừa gán bạn làm <strong>Quản lý (Restaurant Admin)</strong> cho nhà hàng sau:</p>"
            + "    <div class=\"highlight\">"
            + "      <p>🏪 <strong>Nhà hàng:</strong> " + escapeHtml(restaurantName) + "</p>"
            + "      <p>👤 <strong>Vai trò:</strong> Restaurant Admin</p>"
            + "    </div>"
            + "    <p>Đăng nhập bằng tài khoản hiện tại của bạn để bắt đầu quản lý nhà hàng này.</p>"
            + "    <a href=\"https://smartrestaurant.example.com/login\" class=\"btn\">Đăng nhập ngay →</a>"
            + "    <p style=\"margin-top:24px\">Trân trọng,<br><strong>Đội ngũ SmartRestaurant</strong></p>"
            + "  </div>"
            + "  <div class=\"footer\">© 2025 SmartRestaurant. Email này được gửi tự động, vui lòng không trả lời.</div>"
            + "</div>"
            + "</body></html>";

        sendHtml(toEmail, subject, html);
    }

    /**
     * Gửi email thông báo tài khoản RESTAURANT_ADMIN mới được tạo bởi hệ thống admin.
     *
     * <p>Email HTML tiếng Việt, bao gồm:
     * <ul>
     *   <li>Tên admin và tên nhà hàng được gán</li>
     *   <li>Thông tin đăng nhập (email + mật khẩu ban đầu)</li>
     *   <li>Lời nhắc đổi mật khẩu sau lần đăng nhập đầu tiên</li>
     * </ul>
     *
     * <p>Thực thi bất đồng bộ (fire-and-forget daemon thread) — không block caller.
     *
     * @param toEmail        địa chỉ email người nhận (admin nhà hàng)
     * @param adminName      họ tên admin
     * @param restaurantName tên nhà hàng được gán
     * @param loginEmail     email dùng để đăng nhập
     * @param plainPassword  mật khẩu ban đầu (plain text, trước khi hash)
     */
    public void sendNewAdminAccountEmail(String toEmail,
                                         String adminName,
                                         String restaurantName,
                                         String loginEmail,
                                         String plainPassword) {
        String subject = "🔑 Tài khoản quản lý nhà hàng " + restaurantName + " đã được tạo";

        String html = "<!DOCTYPE html>"
            + "<html lang=\"vi\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<style>"
            + "  body{margin:0;padding:0;background:#f4f4f7;font-family:Arial,sans-serif;color:#333}"
            + "  .wrapper{max-width:600px;margin:32px auto;background:#fff;border-radius:8px;"
            + "            box-shadow:0 2px 8px rgba(0,0,0,.1);overflow:hidden}"
            + "  .header{background:#2563EB;padding:32px 24px;text-align:center}"
            + "  .header h1{margin:0;color:#fff;font-size:22px;letter-spacing:.5px}"
            + "  .header p{margin:6px 0 0;color:#BFDBFE;font-size:14px}"
            + "  .body{padding:32px 24px}"
            + "  .greeting{font-size:16px;margin-bottom:16px}"
            + "  .highlight{background:#EFF6FF;border-left:4px solid #2563EB;padding:16px 20px;"
            + "              border-radius:4px;margin:20px 0}"
            + "  .highlight p{margin:6px 0;font-size:15px}"
            + "  .highlight strong{color:#1D4ED8}"
            + "  .info-box{background:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;"
            + "             padding:16px 20px;margin:20px 0}"
            + "  .info-box p{margin:6px 0;font-size:14px}"
            + "  .info-box .label{color:#64748b;font-size:12px;text-transform:uppercase;"
            + "                    letter-spacing:.5px;margin-bottom:2px}"
            + "  .info-box .value{font-weight:bold;color:#1e293b;font-size:15px;"
            + "                    font-family:monospace}"
            + "  .warn-box{background:#FFFBEB;border:1px solid #FDE68A;border-radius:6px;"
            + "             padding:14px 18px;margin:20px 0;font-size:14px;color:#92400E}"
            + "  .btn{display:inline-block;margin:24px 0;padding:12px 32px;"
            + "        background:#2563EB;color:#fff!important;text-decoration:none;"
            + "        border-radius:6px;font-size:15px;font-weight:bold}"
            + "  .footer{background:#f8fafc;padding:16px 24px;text-align:center;"
            + "           font-size:12px;color:#94a3b8;border-top:1px solid #e2e8f0}"
            + "</style></head><body>"
            + "<div class=\"wrapper\">"
            + "  <div class=\"header\">"
            + "    <h1>🔑 Tài khoản quản lý của bạn đã sẵn sàng!</h1>"
            + "    <p>SmartRestaurant — Hệ thống quản lý nhà hàng</p>"
            + "  </div>"
            + "  <div class=\"body\">"
            + "    <p class=\"greeting\">Xin chào <strong>" + escapeHtml(adminName) + "</strong>,</p>"
            + "    <p>Tài khoản <strong>Quản lý nhà hàng (Restaurant Admin)</strong> đã được tạo cho bạn bởi quản trị viên hệ thống.</p>"
            + "    <div class=\"highlight\">"
            + "      <p>🏪 <strong>Nhà hàng:</strong> " + escapeHtml(restaurantName) + "</p>"
            + "      <p>👤 <strong>Vai trò:</strong> Restaurant Admin</p>"
            + "    </div>"
            + "    <p>Sử dụng thông tin bên dưới để đăng nhập vào hệ thống:</p>"
            + "    <div class=\"info-box\">"
            + "      <p class=\"label\">Email đăng nhập</p>"
            + "      <p class=\"value\">" + escapeHtml(loginEmail) + "</p>"
            + "      <p class=\"label\" style=\"margin-top:12px\">Mật khẩu ban đầu</p>"
            + "      <p class=\"value\">" + escapeHtml(plainPassword) + "</p>"
            + "    </div>"
            + "    <div class=\"warn-box\">"
            + "      ⚠️ <strong>Quan trọng:</strong> Vui lòng đổi mật khẩu ngay sau lần đăng nhập đầu tiên để bảo mật tài khoản."
            + "    </div>"
            + "    <a href=\"https://smartrestaurant.example.com/login\" class=\"btn\">Đăng nhập ngay →</a>"
            + "    <p style=\"margin-top:24px\">Trân trọng,<br><strong>Đội ngũ SmartRestaurant</strong></p>"
            + "  </div>"
            + "  <div class=\"footer\">© 2025 SmartRestaurant. Email này được gửi tự động, vui lòng không trả lời.</div>"
            + "</div>"
            + "</body></html>";

        sendHtml(toEmail, subject, html);
    }

    /**
     * Gửi email thông báo đơn đăng ký nhà hàng đã được SUPER_ADMIN phê duyệt.
     *
     * <p>Email HTML tiếng Việt, bao gồm:
     * <ul>
     *   <li>Lời chúc mừng và tên nhà hàng được duyệt</li>
     *   <li>Thông tin đăng nhập hệ thống (email + hướng dẫn mật khẩu)</li>
     *   <li>Link placeholder vào hệ thống</li>
     * </ul>
     *
     * <p>Thực thi bất đồng bộ (fire-and-forget daemon thread) — không block
     * caller. Thất bại chỉ được log warn, không ném exception ra ngoài.
     *
     * @param ownerEmail     địa chỉ email người nhận (chủ nhà hàng)
     * @param ownerName      họ tên chủ nhà hàng
     * @param restaurantName tên nhà hàng được phê duyệt
     * @param loginEmail     email dùng để đăng nhập hệ thống (thường = ownerEmail)
     */
    public void sendRestaurantApprovalEmail(String ownerEmail,
                                            String ownerName,
                                            String restaurantName,
                                            String loginEmail) {
        String subject = "🎉 Chúc mừng! Nhà hàng " + restaurantName + " đã được phê duyệt";

        String html = "<!DOCTYPE html>"
            + "<html lang=\"vi\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<style>"
            + "  body{margin:0;padding:0;background:#f4f4f7;font-family:Arial,sans-serif;color:#333}"
            + "  .wrapper{max-width:600px;margin:32px auto;background:#fff;border-radius:8px;"
            + "            box-shadow:0 2px 8px rgba(0,0,0,.1);overflow:hidden}"
            + "  .header{background:#16a34a;padding:32px 24px;text-align:center}"
            + "  .header h1{margin:0;color:#fff;font-size:22px;letter-spacing:.5px}"
            + "  .header p{margin:6px 0 0;color:#bbf7d0;font-size:14px}"
            + "  .body{padding:32px 24px}"
            + "  .greeting{font-size:16px;margin-bottom:16px}"
            + "  .highlight{background:#f0fdf4;border-left:4px solid #16a34a;padding:16px 20px;"
            + "              border-radius:4px;margin:20px 0}"
            + "  .highlight p{margin:6px 0;font-size:15px}"
            + "  .highlight strong{color:#15803d}"
            + "  .info-box{background:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;"
            + "             padding:16px 20px;margin:20px 0}"
            + "  .info-box p{margin:6px 0;font-size:14px}"
            + "  .info-box .label{color:#64748b;font-size:12px;text-transform:uppercase;"
            + "                    letter-spacing:.5px;margin-bottom:2px}"
            + "  .info-box .value{font-weight:bold;color:#1e293b;font-size:15px}"
            + "  .btn{display:inline-block;margin:24px 0;padding:12px 32px;"
            + "        background:#16a34a;color:#fff!important;text-decoration:none;"
            + "        border-radius:6px;font-size:15px;font-weight:bold}"
            + "  .note{font-size:13px;color:#64748b;margin-top:8px}"
            + "  .footer{background:#f8fafc;padding:16px 24px;text-align:center;"
            + "           font-size:12px;color:#94a3b8;border-top:1px solid #e2e8f0}"
            + "</style></head><body>"
            + "<div class=\"wrapper\">"
            + "  <div class=\"header\">"
            + "    <h1>🎉 Đơn đăng ký đã được phê duyệt!</h1>"
            + "    <p>SmartRestaurant — Hệ thống quản lý nhà hàng</p>"
            + "  </div>"
            + "  <div class=\"body\">"
            + "    <p class=\"greeting\">Xin chào <strong>" + escapeHtml(ownerName) + "</strong>,</p>"
            + "    <p>Chúng tôi vui mừng thông báo rằng đơn đăng ký của bạn đã được <strong>phê duyệt thành công</strong>!</p>"
            + "    <div class=\"highlight\">"
            + "      <p>🏪 <strong>Nhà hàng:</strong> " + escapeHtml(restaurantName) + "</p>"
            + "      <p>✅ <strong>Trạng thái:</strong> Đã được phê duyệt &amp; kích hoạt</p>"
            + "    </div>"
            + "    <p>Tài khoản của bạn đã được tạo trong hệ thống. Sử dụng thông tin bên dưới để đăng nhập:</p>"
            + "    <div class=\"info-box\">"
            + "      <p class=\"label\">Email đăng nhập</p>"
            + "      <p class=\"value\">" + escapeHtml(loginEmail) + "</p>"
            + "      <p class=\"label\" style=\"margin-top:12px\">Mật khẩu</p>"
            + "      <p class=\"value\">Mật khẩu bạn đã đặt khi điền đơn đăng ký</p>"
            + "    </div>"
            + "    <p>Nhấn nút bên dưới để truy cập hệ thống quản lý nhà hàng:</p>"
            + "    <a href=\"https://smartrestaurant.example.com/login\" class=\"btn\">Đăng nhập ngay →</a>"
            + "    <p class=\"note\">⚠️ Nếu bạn quên mật khẩu, hãy dùng chức năng <em>Quên mật khẩu</em> tại trang đăng nhập.</p>"
            + "    <p style=\"margin-top:24px\">Chúc bạn kinh doanh thành công!<br><strong>Đội ngũ SmartRestaurant</strong></p>"
            + "  </div>"
            + "  <div class=\"footer\">© 2025 SmartRestaurant. Email này được gửi tự động, vui lòng không trả lời.</div>"
            + "</div>"
            + "</body></html>";

        sendHtml(ownerEmail, subject, html);
    }

    /**
     * Gửi email thông báo đơn đăng ký nhà hàng bị SUPER_ADMIN từ chối.
     *
     * <p>Email HTML tiếng Việt, bao gồm tên nhà hàng bị từ chối và lý do
     * cụ thể do admin cung cấp.
     *
     * <p>Thực thi bất đồng bộ (fire-and-forget daemon thread) — không block
     * caller. Thất bại chỉ được log warn, không ném exception ra ngoài.
     *
     * @param ownerEmail     địa chỉ email người nhận (chủ nhà hàng)
     * @param ownerName      họ tên chủ nhà hàng
     * @param restaurantName tên nhà hàng bị từ chối
     * @param reason         lý do từ chối do SUPER_ADMIN nhập
     */
    public void sendRestaurantRejectionEmail(String ownerEmail,
                                             String ownerName,
                                             String restaurantName,
                                             String reason) {
        String subject = "Thông báo về đơn đăng ký nhà hàng " + restaurantName + "";

        String html = "<!DOCTYPE html>"
            + "<html lang=\"vi\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<style>"
            + "  body{margin:0;padding:0;background:#f4f4f7;font-family:Arial,sans-serif;color:#333}"
            + "  .wrapper{max-width:600px;margin:32px auto;background:#fff;border-radius:8px;"
            + "            box-shadow:0 2px 8px rgba(0,0,0,.1);overflow:hidden}"
            + "  .header{background:#dc2626;padding:32px 24px;text-align:center}"
            + "  .header h1{margin:0;color:#fff;font-size:22px;letter-spacing:.5px}"
            + "  .header p{margin:6px 0 0;color:#fecaca;font-size:14px}"
            + "  .body{padding:32px 24px}"
            + "  .greeting{font-size:16px;margin-bottom:16px}"
            + "  .reason-box{background:#fef2f2;border-left:4px solid #dc2626;padding:16px 20px;"
            + "               border-radius:4px;margin:20px 0}"
            + "  .reason-box p{margin:6px 0;font-size:15px}"
            + "  .reason-box .reason-text{color:#7f1d1d;font-style:italic;white-space:pre-wrap}"
            + "  .contact-box{background:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;"
            + "                padding:16px 20px;margin:20px 0;font-size:14px}"
            + "  .footer{background:#f8fafc;padding:16px 24px;text-align:center;"
            + "           font-size:12px;color:#94a3b8;border-top:1px solid #e2e8f0}"
            + "</style></head><body>"
            + "<div class=\"wrapper\">"
            + "  <div class=\"header\">"
            + "    <h1>Kết quả xét duyệt đơn đăng ký</h1>"
            + "    <p>SmartRestaurant — Hệ thống quản lý nhà hàng</p>"
            + "  </div>"
            + "  <div class=\"body\">"
            + "    <p class=\"greeting\">Xin chào <strong>" + escapeHtml(ownerName) + "</strong>,</p>"
            + "    <p>Sau khi xem xét, chúng tôi rất tiếc phải thông báo rằng đơn đăng ký nhà hàng "
            +        "<strong>\"" + escapeHtml(restaurantName) + "\"</strong> của bạn <strong>chưa được phê duyệt</strong> lần này.</p>"
            + "    <div class=\"reason-box\">"
            + "      <p><strong>📋 Lý do từ chối:</strong></p>"
            + "      <p class=\"reason-text\">" + escapeHtml(reason) + "</p>"
            + "    </div>"
            + "    <p>Bạn có thể xem xét lại các thông tin và gửi lại đơn đăng ký sau khi đã bổ sung/điều chỉnh theo lý do trên.</p>"
            + "    <div class=\"contact-box\">"
            + "      <p>💬 Nếu bạn có thắc mắc hoặc cần hỗ trợ thêm, vui lòng liên hệ với chúng tôi qua email hỗ trợ.</p>"
            + "    </div>"
            + "    <p style=\"margin-top:24px\">Trân trọng,<br><strong>Đội ngũ SmartRestaurant</strong></p>"
            + "  </div>"
            + "  <div class=\"footer\">© 2025 SmartRestaurant. Email này được gửi tự động, vui lòng không trả lời.</div>"
            + "</div>"
            + "</body></html>";

        sendHtml(ownerEmail, subject, html);
    }

    /**
     * Escape ký tự đặc biệt HTML để tránh XSS trong email template.
     */
    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;")
                .replace("'",  "&#39;");
    }

    // -----------------------------------------------------------------------
    // Getters hỗ trợ tính năng cài đặt trong UI (tuỳ chọn)
    // -----------------------------------------------------------------------

    /** Cập nhật thông tin xác thực runtime mà không cần khởi động lại ứng dụng. */
    public void updateCredentials(String fromAddress, String username, String password) {
        this.fromAddress = fromAddress;
        this.username    = username;
        this.password    = password;
        smtpProps.put("mail.smtp.from", fromAddress);
    }

    public String getFromAddress() { return fromAddress; }
    public String getUsername()    { return username; }
    public String getSmtpHost()    { return smtpProps.getProperty("mail.smtp.host"); }
    public String getSmtpPort()    { return smtpProps.getProperty("mail.smtp.port"); }
}