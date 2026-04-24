package com.restaurant.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton quản lý kết nối Oracle JDBC.
 * Đọc thông tin kết nối từ db.properties trong classpath.
 */
public class DBConnection {

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
     * Lấy một Connection mới. Caller có trách nhiệm đóng connection sau khi dùng.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * Kiểm tra kết nối DB có hoạt động không.
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && conn.isValid(3);
        } catch (Exception e) {
            System.err.println("[DBConnection] Không thể kết nối DB: " + e.getMessage());
            return false;
        }
    }

    /** Cập nhật cấu hình runtime (dùng cho tính năng cài đặt DB trong UI) */
    public void updateConfig(String url, String username, String password) {
        this.url      = url;
        this.username = username;
        this.password = password;
    }

    public String getUrl()      { return url; }
    public String getUsername() { return username; }
}
