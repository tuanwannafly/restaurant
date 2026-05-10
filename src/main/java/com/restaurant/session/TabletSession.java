package com.restaurant.session;

import java.io.InputStream;
import java.util.Properties;

/**
 * Session dùng riêng cho tablet khách tại bàn.
 * Không cần đăng nhập — đọc tableId và restaurantId từ tablet.properties.
 *
 * <p>Tablet mode bypass hoàn toàn AppSession, RbacGuard, TokenService.
 * DAO được gọi trực tiếp với restaurantId từ config.
 *
 * <h3>Cấu hình tablet.properties</h3>
 * <pre>
 * tablet.mode=true
 * tablet.tableId=3
 * tablet.restaurantId=1
 * </pre>
 *
 * <p>Đặt file tablet.properties trong classpath (src/main/resources/).
 * Mỗi bàn cần một JAR riêng hoặc sửa file trước khi deploy.
 */
public class TabletSession {

    private static TabletSession instance;

    private boolean tabletMode   = false;
    private String  tableId      = null;
    private long    restaurantId = 0L;

    private TabletSession() {
        load();
    }

    public static TabletSession getInstance() {
        if (instance == null) {
            instance = new TabletSession();
        }
        return instance;
    }

    private void load() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("tablet.properties")) {
            if (in == null) {
                // Không có file config → không phải tablet mode
                return;
            }
            Properties p = new Properties();
            p.load(in);
            this.tabletMode   = "true".equalsIgnoreCase(p.getProperty("tablet.mode", "false"));
            this.tableId      = p.getProperty("tablet.tableId");
            this.restaurantId = Long.parseLong(p.getProperty("tablet.restaurantId", "0"));

            System.out.println("[TabletSession] Loaded: mode=" + tabletMode
                    + ", tableId=" + tableId + ", restaurantId=" + restaurantId);
        } catch (Exception e) {
            System.err.println("[TabletSession] Không đọc được tablet.properties: " + e.getMessage());
        }
    }

    public boolean isTabletMode()    { return tabletMode; }
    public String  getTableId()      { return tableId; }
    public long    getRestaurantId() { return restaurantId; }

    /**
     * Kiểm tra cấu hình tablet có hợp lệ không.
     *
     * @return true nếu tabletMode=true và có đủ tableId + restaurantId hợp lệ
     */
    public boolean isValid() {
        return tabletMode
            && tableId != null && !tableId.isBlank()
            && restaurantId > 0;
    }
}
