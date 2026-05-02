package com.restaurant.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton quản lý kết nối Oracle JDBC.
 * Đọc thông tin kết nối từ db.properties trong classpath.
 *
 * <p>Phase 7D: Bổ sung network timeout 10 giây và
 * {@link #isConnectionValid(Connection)} để caller kiểm tra
 * trước khi tái sử dụng connection.
 */
public class DBConnection {

    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());

    /** Timeout mạng cho mỗi Connection, đơn vị millisecond. */
    private static final int NETWORK_TIMEOUT_MS = 10_000;

    /** Timeout kiểm tra connection còn sống, đơn vị giây. */
    private static final int VALIDATION_TIMEOUT_S = 3;

    private static DBConnection instance;

    private String url;
    private String username;
    private String password;

    private DBConnection() {
        loadConfig();
    }

    public static DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    private void loadConfig() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new RuntimeException("Không tìm thấy db.properties trong classpath!");
            }
            Properties props = new Properties();
            props.load(in);
            this.url      = props.getProperty("db.url");
            this.username = props.getProperty("db.username");
            this.password = props.getProperty("db.password");

            // Load driver (Oracle JDBC tự đăng ký, nhưng load tường minh cho an toàn)
            Class.forName(props.getProperty("db.driver", "oracle.jdbc.OracleDriver"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Oracle JDBC driver không tìm thấy: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi đọc cấu hình DB: " + e.getMessage(), e);
        }
    }

    /**
     * Lấy một Connection mới với network timeout 10 giây.
     * Caller có trách nhiệm đóng connection sau khi dùng
     * (khuyến nghị dùng try-with-resources).
     *
     * <p>Network timeout ngắt connection nếu DB không phản hồi
     * trong {@value #NETWORK_TIMEOUT_MS} ms — tránh thread bị block vô thời hạn.
     *
     * @return Connection đã được set network timeout
     * @throws SQLException nếu không thể kết nối hoặc set timeout thất bại
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url, username, password);
        // Đặt network timeout để tránh thread bị block vô hạn khi DB treo.
        // Executor dùng single thread vì chỉ phục vụ abort callback của JDBC.
        conn.setNetworkTimeout(Executors.newSingleThreadExecutor(), NETWORK_TIMEOUT_MS);
        return conn;
    }

    /**
     * Kiểm tra một Connection có còn hợp lệ và dùng được không.
     *
     * <p>Dùng trước khi tái sử dụng connection lấy từ cache / pool thủ công.
     * Không cần gọi khi lấy connection mới từ {@link #getConnection()}.
     *
     * @param conn connection cần kiểm tra (có thể null)
     * @return {@code true} nếu conn != null, chưa đóng, và phản hồi trong
     *         {@value #VALIDATION_TIMEOUT_S} giây
     */
    public boolean isConnectionValid(Connection conn) {
        if (conn == null) return false;
        try {
            return !conn.isClosed() && conn.isValid(VALIDATION_TIMEOUT_S);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                    "[DBConnection] isConnectionValid – không thể kiểm tra connection", e);
            return false;
        }
    }

    /**
     * Kiểm tra kết nối DB tổng thể có hoạt động không.
     * Mở connection mới, ping DB, rồi đóng lại ngay.
     *
     * @return {@code true} nếu DB phản hồi trong {@value #VALIDATION_TIMEOUT_S} giây
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return isConnectionValid(conn);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "[DBConnection] testConnection – không thể kết nối DB", e);
            return false;
        }
    }

    /** Cập nhật cấu hình runtime (dùng cho tính năng cài đặt DB trong UI). */
    public void updateConfig(String url, String username, String password) {
        this.url      = url;
        this.username = username;
        this.password = password;
    }

    public String getUrl()      { return url; }
    public String getUsername() { return username; }
}