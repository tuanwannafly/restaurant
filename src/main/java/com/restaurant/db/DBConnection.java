package com.restaurant.db;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Singleton quản lý connection pool Oracle JDBC dùng HikariCP.
 *
 * <h3>Thứ tự ưu tiên cấu hình</h3>
 * <ol>
 *   <li>Environment variable  (ưu tiên cao nhất — dùng trong Docker)</li>
 *   <li>File db.properties    (classpath hoặc filesystem)</li>
 * </ol>
 *
 * <h3>Biến môi trường được hỗ trợ</h3>
 * <pre>
 *   DB_URL            jdbc:oracle:thin:@//host:1521/ORCL
 *   DB_USERNAME       tên đăng nhập Oracle
 *   DB_PASSWORD       mật khẩu
 *   DB_DRIVER         oracle.jdbc.OracleDriver  (mặc định)
 *   DB_POOL_MAX       5    — số connection tối đa trong pool
 *   DB_POOL_MIN       1    — số connection idle tối thiểu
 *   DB_POOL_TIMEOUT   10000  — ms chờ lấy connection từ pool
 *   DB_POOL_MAXLIFE   600000 — ms connection sống tối đa
 *   DB_POOL_IDLE      300000 — ms idle trước khi đóng
 *   INSTANCE_ID       1    — hậu tố tên pool, dùng khi scale nhiều container
 * </pre>
 *
 * <h3>Chạy nhiều Docker container</h3>
 * <pre>
 *   docker compose up --scale app=7
 * </pre>
 * Mỗi container dùng env var riêng, cùng Oracle user — không cần user riêng.
 *
 * <h3>Backward compatibility</h3>
 * Tất cả caller hiện tại vẫn dùng:
 * <pre>
 *   try (Connection conn = DBConnection.getInstance().getConnection()) { ... }
 * </pre>
 * không cần thay đổi gì — getConnection() nay lấy từ pool thay vì tạo mới.
 */
public class DBConnection {

    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());
    private static final int VALIDATION_TIMEOUT_S = 3;

    // ── Singleton (initialization-on-demand holder) ───────────────────────────
    private static final class Holder {
        static final DBConnection INSTANCE = new DBConnection();
    }

    public static DBConnection getInstance() {
        return Holder.INSTANCE;
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private volatile HikariDataSource dataSource;
    private volatile Properties       loadedProps;

    // ── Constructor ───────────────────────────────────────────────────────────
    private DBConnection() {
        Properties props = loadPropertiesFile();
        this.loadedProps = props;
        this.dataSource  = buildPool(props);

        Runtime.getRuntime().addShutdownHook(
            new Thread(this::shutdown, "db-shutdown-hook"));

        LOGGER.info(String.format(
            "[DBConnection] Pool ready — instance=%s  user=%s  maxPool=%s",
            resolveOrDefault(props, "INSTANCE_ID", "app.profile", "1"),
            resolveOrDefault(props, "DB_USERNAME",  "db.username", "?"),
            resolveOrDefault(props, "DB_POOL_MAX",  "db.pool.maxSize", "5")
        ));
    }

    // ── Properties file loader ────────────────────────────────────────────────

    /**
     * Tải db.properties theo thứ tự:
     * 1. Đường dẫn từ env var DB_CONFIG hoặc system property db.config
     * 2. "db.properties" mặc định từ classpath
     * 3. Trả về Properties rỗng — env var sẽ là nguồn duy nhất (Docker mode)
     */
    private Properties loadPropertiesFile() {
        Properties props = new Properties();

        String configPath = System.getenv("DB_CONFIG");
        if (configPath == null || configPath.isBlank()) {
            configPath = System.getProperty("db.config", "db.properties");
        }

        // 1. Thử classpath trước
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(configPath)) {
            if (in != null) {
                props.load(in);
                LOGGER.info("[DBConnection] Config loaded from classpath: " + configPath);
                return props;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[DBConnection] Cannot load classpath resource: " + configPath, e);
        }

        // 2. Fallback: filesystem
        File f = new File(configPath);
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                props.load(in);
                LOGGER.info("[DBConnection] Config loaded from filesystem: " + f.getAbsolutePath());
                return props;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "[DBConnection] Cannot load file: " + configPath, e);
            }
        }

        // 3. Không tìm thấy file — Docker mode: dùng hoàn toàn env var
        LOGGER.info("[DBConnection] No properties file found — using environment variables only");
        return props;
    }

    // ── Config resolution ─────────────────────────────────────────────────────

    /**
     * Ưu tiên: 1) Environment variable → 2) .properties key
     * Trả về null nếu không tìm thấy ở cả hai nguồn.
     */
    private String resolve(Properties props, String envKey, String propKey) {
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();

        String fromProp = props.getProperty(propKey);
        if (fromProp != null && !fromProp.isBlank()) return fromProp.trim();

        return null;
    }

    private String resolveOrDefault(Properties props, String envKey, String propKey, String defaultVal) {
        String v = resolve(props, envKey, propKey);
        return (v != null) ? v : defaultVal;
    }

    // ── Pool builder ──────────────────────────────────────────────────────────
    private HikariDataSource buildPool(Properties props) {
        String url      = resolve(props, "DB_URL",      "db.url");
        String username = resolve(props, "DB_USERNAME", "db.username");
        String password = resolve(props, "DB_PASSWORD", "db.password");
        String driver   = resolveOrDefault(props, "DB_DRIVER",        "db.driver",        "oracle.jdbc.OracleDriver");
        int maxSize  = parseInt(resolveOrDefault(props, "DB_POOL_MAX",     "db.pool.maxSize",  "5"));
        int minIdle  = parseInt(resolveOrDefault(props, "DB_POOL_MIN",     "db.pool.minIdle",  "1"));
        int timeout  = parseInt(resolveOrDefault(props, "DB_POOL_TIMEOUT", "db.pool.timeout",  "10000"));
        int maxLife  = parseInt(resolveOrDefault(props, "DB_POOL_MAXLIFE", "db.pool.maxLife",  "600000"));
        int idleTime = parseInt(resolveOrDefault(props, "DB_POOL_IDLE",    "db.pool.idleTime", "300000"));

        if (url == null || url.isBlank()) {
            throw new RuntimeException(
                "[DBConnection] DB_URL is required.\n" +
                "  Docker: set env var DB_URL\n" +
                "  Local:  set db.url in db.properties");
        }
        if (username == null || username.isBlank()) {
            throw new RuntimeException(
                "[DBConnection] DB_USERNAME is required.\n" +
                "  Docker: set env var DB_USERNAME\n" +
                "  Local:  set db.username in db.properties");
        }

        // Tên pool gắn INSTANCE_ID — dễ trace trong log khi chạy nhiều container
        String instanceId = resolveOrDefault(props, "INSTANCE_ID", "app.profile", "1");
        String poolName   = "Restaurant-P" + instanceId;

        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password != null ? password : "");
        config.setDriverClassName(driver);

        config.setMaximumPoolSize(maxSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(timeout);
        config.setMaxLifetime(maxLife);
        config.setIdleTimeout(idleTime);

        // Oracle: dùng SELECT 1 FROM DUAL để kiểm tra connection còn sống
        config.setConnectionTestQuery("SELECT 1 FROM DUAL");

        return new HikariDataSource(config);
    }

    // ── Public API (backward compatible — không thay đổi signature) ───────────

    /**
     * Lấy một Connection từ pool.
     * Caller dùng try-with-resources như cũ — trả về pool tự động khi đóng.
     *
     * @return Connection sẵn sàng sử dụng
     * @throws SQLException nếu pool hết hoặc DB không phản hồi
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Kiểm tra một Connection có còn hợp lệ không.
     * Giữ nguyên để tương thích với code cũ.
     */
    public boolean isConnectionValid(Connection conn) {
        if (conn == null) return false;
        try {
            return !conn.isClosed() && conn.isValid(VALIDATION_TIMEOUT_S);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "[DBConnection] isConnectionValid error", e);
            return false;
        }
    }

    /**
     * Kiểm tra DB tổng thể có phản hồi không.
     * Lấy connection từ pool, ping, rồi trả về pool ngay.
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return isConnectionValid(conn);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[DBConnection] testConnection failed", e);
            return false;
        }
    }

    /**
     * Cập nhật cấu hình DB runtime (dùng cho tính năng cài đặt DB trong UI).
     * Đóng pool cũ và tạo pool mới. Thread-safe.
     */
    public synchronized void updateConfig(String url, String username, String password) {
        LOGGER.info("[DBConnection] updateConfig — rebuilding pool");
        Properties newProps = new Properties(loadedProps);
        newProps.setProperty("db.url",      url);
        newProps.setProperty("db.username", username);
        newProps.setProperty("db.password", password);

        HikariDataSource oldDs = this.dataSource;
        this.dataSource  = buildPool(newProps);
        this.loadedProps = newProps;

        if (oldDs != null && !oldDs.isClosed()) {
            oldDs.close();
            LOGGER.info("[DBConnection] Old pool closed");
        }
    }

    /** URL hiện tại — dùng cho UI hiển thị thông tin kết nối. */
    public String getUrl() {
        String fromEnv = System.getenv("DB_URL");
        return (fromEnv != null && !fromEnv.isBlank()) ? fromEnv : loadedProps.getProperty("db.url");
    }

    /** Username hiện tại. */
    public String getUsername() {
        String fromEnv = System.getenv("DB_USERNAME");
        return (fromEnv != null && !fromEnv.isBlank()) ? fromEnv : loadedProps.getProperty("db.username");
    }

    /**
     * Thống kê pool hiện tại (active / idle / waiting / max).
     * Hữu ích khi debug hoặc monitoring.
     */
    public String getPoolStats() {
        if (dataSource == null || dataSource.isClosed()) return "Pool not initialized";
        return String.format(
            "pool=%s  active=%d  idle=%d  waiting=%d  max=%d",
            dataSource.getPoolName(),
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
            dataSource.getMaximumPoolSize()
        );
    }

    /**
     * Đóng pool và giải phóng tất cả connection.
     * Tự động được gọi qua JVM shutdown hook. An toàn nếu gọi nhiều lần.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("[DBConnection] Pool closed");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning("[DBConnection] Invalid integer value: '" + s + "', using default 5");
            return 5;
        }
    }
}