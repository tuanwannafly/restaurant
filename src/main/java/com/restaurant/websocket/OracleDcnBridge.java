package com.restaurant.websocket;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.restaurant.db.DBConnection;
import com.restaurant.session.AppSession;
import com.restaurant.ui.fx.util.PollManagerFx;

import javafx.application.Platform;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.dcn.DatabaseChangeEvent;
import oracle.jdbc.dcn.DatabaseChangeListener;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import oracle.jdbc.dcn.TableChangeDescription;

/**
 * OracleDcnBridge — cầu nối giữa Oracle Database Change Notification (DCN)
 * và WebSocket push infrastructure của SmartRestaurant.
 *
 * <h3>Cơ chế hoạt động</h3>
 * <ol>
 *   <li>Lấy {@link OracleConnection} từ {@link DBConnection#getInstance()}.</li>
 *   <li>Đăng ký {@link DatabaseChangeRegistration} với Oracle DCN listener.</li>
 *   <li>Thực thi SELECT dummy trên các bảng cần theo dõi để "đính" registration
 *       vào các bảng đó.</li>
 *   <li>Khi Oracle phát hiện thay đổi (INSERT / UPDATE / DELETE), gọi
 *       {@link DatabaseChangeListener#onDatabaseChangeNotification} trên thread
 *       riêng của Oracle JDBC driver.</li>
 *   <li>Bridge map tên bảng → {@link WsTopic} rồi gọi
 *       {@link RestaurantEventServer#broadcast(WsEvent)}.</li>
 * </ol>
 *
 * <h3>Bảng theo dõi và topic tương ứng</h3>
 * <pre>
 *   ORDERS / ORDER_ITEMS        → WsTopic.ORDERS  + WsTopic.BADGE
 *   ORDER_ITEMS (kitchen view)  → WsTopic.KITCHEN + WsTopic.BADGE
 *   RESTAURANT_REQUESTS         → WsTopic.REQUEST_LIST + WsTopic.BADGE
 * </pre>
 *
 * <h3>Yêu cầu Oracle DB</h3>
 * Oracle 11g trở lên và user DB phải có privilege {@code CHANGE NOTIFICATION}:
 * <pre>
 *   -- Chạy trên Oracle với tài khoản DBA:
 *   GRANT CHANGE NOTIFICATION TO your_username;
 *   -- Thay your_username bằng giá trị db.username trong db.properties
 * </pre>
 * Nếu privilege chưa được cấp hoặc Oracle version < 11g, bridge sẽ log warning
 * và fallback im lặng (PollManagerFx vẫn hoạt động bình thường).
 *
 * <h3>Sử dụng</h3>
 * <pre>{@code
 * // Sau khi login thành công
 * OracleDcnBridge.getInstance().start();
 *
 * // Khi logout hoặc đóng app
 * OracleDcnBridge.getInstance().stop();
 * }</pre>
 *
 * <h3>Thread-safety</h3>
 * <ul>
 *   <li>{@link #start()} và {@link #stop()} nên được gọi từ cùng một thread
 *       (FX Application Thread hoặc Launcher Thread).</li>
 *   <li>DCN callback của Oracle chạy trên thread riêng — bridge chỉ gọi
 *       {@link RestaurantEventServer#broadcast} vốn là thread-safe.</li>
 *   <li>Mọi ngoại lệ đều được wrap trong try-catch để không crash app.</li>
 * </ul>
 */
public final class OracleDcnBridge {

    private static final Logger LOGGER = Logger.getLogger(OracleDcnBridge.class.getName());

    // ── Bảng theo dõi ─────────────────────────────────────────────────────────

    /**
     * Danh sách bảng cần đăng ký DCN.
     * Oracle yêu cầu tên bảng UPPER CASE để khớp với dictionary.
     */
    private static final String[] WATCHED_TABLES = {
            "ORDERS",
            "ORDER_ITEMS",
            "RESTAURANT_REQUESTS"
    };

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile OracleDcnBridge instance;

    public static OracleDcnBridge getInstance() {
        if (instance == null) {
            synchronized (OracleDcnBridge.class) {
                if (instance == null) {
                    instance = new OracleDcnBridge();
                }
            }
        }
        return instance;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * Registration Oracle DCN đang hoạt động.
     * {@code null} nếu DCN chưa start hoặc đã stop.
     */
    private DatabaseChangeRegistration registration;

    /**
     * Connection riêng giữ cho DCN.
     * Oracle yêu cầu connection không được đóng trong suốt thời gian DCN hoạt động.
     */
    private OracleConnection dcnConnection;

    private OracleDcnBridge() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Khởi động Oracle DCN.
     *
     * <p>Gọi sau khi login thành công. An toàn khi gọi nhiều lần —
     * nếu đã start rồi thì bỏ qua.
     *
     * <p>Nếu Oracle không hỗ trợ DCN hoặc chưa grant privilege,
     * phương thức log warning và return im lặng (không ném exception).
     */
    public synchronized void start() {
        if (registration != null) {
            LOGGER.fine("[DcnBridge] Đã start rồi, bỏ qua.");
            return;
        }

        try {
            // Bước 1: Lấy OracleConnection từ DBConnection singleton
            Connection rawConn = DBConnection.getInstance().getConnection();
            if (!(rawConn instanceof OracleConnection)) {
                LOGGER.warning("[DcnBridge] Connection không phải OracleConnection — "
                        + "DCN không khả dụng. PollManagerFx vẫn hoạt động.");
                safeClose(rawConn);
                return;
            }
            dcnConnection = (OracleConnection) rawConn;

            // Bước 1b: Kiểm tra Oracle version — DCN yêu cầu 11g trở lên
            int oracleMajor = dcnConnection.getMetaData().getDatabaseMajorVersion();
            if (oracleMajor < 11) {
                LOGGER.log(Level.WARNING,
                        "[DcnBridge] Oracle {0}.x < 11g — DCN không khả dụng."
                        + " Fallback về PollManagerFx badge poll.", oracleMajor);
                safeClose(dcnConnection);
                dcnConnection = null;
                scheduleFallbackBadgePoll();
                return;
            }

            // Bước 2: Cấu hình DCN properties
            Properties props = new Properties();
            // Yêu cầu Oracle trả về ROWID của các hàng bị thay đổi
            props.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, "true");
            // Kích hoạt Query Change Notification (cần CHANGE NOTIFICATION privilege)
            props.setProperty(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, "true");

            // Bước 3: Đăng ký với Oracle
            registration = dcnConnection.registerDatabaseChangeNotification(props);

            // Bước 4: Thêm listener xử lý thay đổi
            registration.addListener(new RestaurantDcnListener());

            LOGGER.log(Level.INFO, "[DcnBridge] Kết nối DCN thành công — regId={0}.",
                    registration.getRegId());

            // Bước 5: "Đính" registration vào từng bảng cần theo dõi
            registerTables();

            LOGGER.log(Level.INFO, "[DcnBridge] Đang theo dõi {0} bảng: {1}",
                    new Object[]{WATCHED_TABLES.length, String.join(", ", WATCHED_TABLES)});

        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("ORA-29970") || msg.contains("29970"))) {
                // ORA-29970: CHANGE NOTIFICATION privilege chưa được cấp
                LOGGER.log(Level.WARNING,
                        "[DCN] Chưa có quyền CHANGE NOTIFICATION — chạy: GRANT CHANGE NOTIFICATION TO {0}",
                        resolveDbUser());
                scheduleFallbackBadgePoll();
            } else if (msg != null && (msg.contains("ORA-29972") || msg.contains("29972"))) {
                // ORA-29972: một số phiên bản Oracle dùng code này cho lỗi privilege
                LOGGER.log(Level.WARNING,
                        "[DCN] Chưa có quyền CHANGE NOTIFICATION — chạy: GRANT CHANGE NOTIFICATION TO {0}",
                        resolveDbUser());
                scheduleFallbackBadgePoll();
            } else if (msg != null && msg.contains("DCN")) {
                LOGGER.warning("[DcnBridge] Oracle không hỗ trợ DCN."
                        + " Fallback về PollManagerFx badge poll.");
                scheduleFallbackBadgePoll();
            } else {
                LOGGER.log(Level.WARNING,
                        "[DcnBridge] Không thể khởi động DCN — fallback về PollManagerFx.",
                        e);
                scheduleFallbackBadgePoll();
            }
            stopInternal();
        }
    }

    /**
     * Dừng Oracle DCN và đóng connection DCN.
     *
     * <p>Gọi khi logout hoặc khi đóng ứng dụng.
     * An toàn khi gọi nhiều lần.
     */
    public synchronized void stop() {
        if (registration == null) {
            LOGGER.fine("[DcnBridge] stop() called but DCN is not active — bỏ qua.");
            return;
        }
        long regId = registration.getRegId();
        stopInternal();
        LOGGER.log(Level.INFO, "[DcnBridge] DCN ngắt kết nối thành công (regId={0}).", regId);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Thực thi SELECT COUNT(*) trên mỗi bảng cần theo dõi.
     * Oracle JDBC driver tự động liên kết registration hiện tại với các bảng
     * xuất hiện trong query được thực thi trên connection DCN.
     */
    private void registerTables() throws Exception {
        // Connection của registration phải được dùng để thực thi query
        try (Statement stmt = dcnConnection.createStatement()) {
            for (String table : WATCHED_TABLES) {
                try {
                    // Query tối giản — chỉ nhằm "đính" bảng vào registration
                    String sql = "SELECT COUNT(*) FROM " + table + " WHERE ROWNUM = 1";
                    ResultSet rs = stmt.executeQuery(sql);
                    if (rs != null) rs.close();
                    LOGGER.log(Level.FINE, "[DcnBridge] Đã đính bảng: {0}", table);
                } catch (java.sql.SQLException e) {
                    LOGGER.log(Level.WARNING,
                            "[DcnBridge] Không thể đính bảng '" + table
                                    + "' vào DCN registration. Bỏ qua.", e);
                }
            }
        }
    }

    /** Dừng registration và đóng connection DCN. Không ném exception. */
    private void stopInternal() {
        if (registration != null) {
            // Bước 1: Huỷ đăng ký phía Oracle server để giải phóng server-side resource
            // Cần làm TRƯỚC khi đóng connection; không thể làm sau khi connection đã đóng.
            if (dcnConnection != null) {
                try {
                    dcnConnection.unregisterDatabaseChangeNotification(registration);
                    LOGGER.log(Level.INFO, "[DcnBridge] DCN registration unregistered (regId={0}).",
                            registration.getRegId());
                } catch (java.sql.SQLException e) {
                    LOGGER.log(Level.FINE,
                            "[DcnBridge] Lỗi khi unregister DCN registration — bỏ qua.", e);
                }
            }
            // Bước 2: unregisterDatabaseChangeNotification() đã giải phóng toàn bộ
            // client-side registration — không cần gọi close() riêng.
            registration = null;
        }
        if (dcnConnection != null) {
            safeClose(dcnConnection);
            dcnConnection = null;
        }
    }

    /**
     * Lên lịch khởi động badge-refresh poll trên FX Application Thread.
     *
     * <p>Gọi khi DCN không khả dụng (thiếu privilege hoặc Oracle &lt; 11g) để đảm
     * bảo badge vẫn được cập nhật định kỳ thay vì hoàn toàn im lặng.</p>
     *
     * <p>{@code Platform.runLater} bắt buộc vì {@link PollManagerFx#register} yêu cầu
     * FX Application Thread, trong khi {@code start()} có thể được gọi từ bất kỳ thread nào.</p>
     */
    @SuppressWarnings("deprecated")
    private static void scheduleFallbackBadgePoll() {
        Platform.runLater(() ->
            PollManagerFx.getInstance().registerBadgeRefresh(30_000)
        );
    }

    /**
     * Đọc username DB từ {@code db.properties} để dùng trong log GRANT.
     * Trả về {@code "<user>"} nếu không đọc được.
     */
    private static String resolveDbUser() {
        try (java.io.InputStream is =
                OracleDcnBridge.class.getClassLoader()
                        .getResourceAsStream("db.properties")) {
            if (is == null) return "<user>";
            Properties p = new Properties();
            p.load(is);
            String u = p.getProperty("db.username", "").trim();
            return u.isEmpty() ? "<user>" : u;
        } catch (Exception e) {
            return "<user>";
        }
    }

    /** Đóng connection không ném exception. */
    private static void safeClose(AutoCloseable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception e) {
            LOGGER.log(Level.FINE, "[DcnBridge] Lỗi đóng connection.", e);
        }
    }

    // ── DCN Listener ─────────────────────────────────────────────────────────

    /**
     * Listener nội bộ xử lý callback từ Oracle DCN.
     *
     * <p>Oracle gọi {@link #onDatabaseChangeNotification} trên một thread riêng
     * của JDBC driver — KHÔNG phải FX Application Thread.
     * Bridge chỉ broadcast WsEvent; client sẽ dispatch lên FX thread khi nhận.
     */
    private static final class RestaurantDcnListener implements DatabaseChangeListener {

        @Override
        public void onDatabaseChangeNotification(DatabaseChangeEvent event) {
            try {
                processChangeEvent(event);
            } catch (Exception e) {
                // Không bao giờ ném exception từ DCN listener — có thể crash driver
                LOGGER.log(Level.WARNING,
                        "[DcnBridge] Lỗi trong DCN callback.", e);
            }
        }

        /**
         * Duyệt qua các bảng thay đổi, map sang topic, broadcast WsEvent.
         */
        private void processChangeEvent(DatabaseChangeEvent event) {
            TableChangeDescription[] tableChanges = event.getTableChangeDescription();
            if (tableChanges == null || tableChanges.length == 0) {
                LOGGER.fine("[DcnBridge] DCN event không có thay đổi bảng nào.");
                return;
            }

            long restaurantId = AppSession.getInstance().getRestaurantId();
            RestaurantEventServer server = RestaurantEventServer.getInstance();

            for (TableChangeDescription tcd : tableChanges) {
                String tableName = tcd.getTableName().toUpperCase();
                LOGGER.log(Level.FINE, "[DcnBridge] Bảng thay đổi: {0}", tableName);

                switch (tableName) {
                    // ── ORDERS / ORDER_ITEMS → thông báo waiter + badge ──────
                    case "ORDERS" -> {
                        server.broadcast(WsEvent.of(WsTopic.ORDERS, restaurantId));
                        server.broadcast(WsEvent.of(WsTopic.BADGE,  restaurantId));
                    }

                    // ── ORDER_ITEMS → thông báo kitchen + badge ──────────────
                    // ORDER_ITEMS phục vụ đồng thời waiter view và kitchen view
                    case "ORDER_ITEMS" -> {
                        server.broadcast(WsEvent.of(WsTopic.ORDERS,  restaurantId));
                        server.broadcast(WsEvent.of(WsTopic.KITCHEN, restaurantId));
                        server.broadcast(WsEvent.of(WsTopic.BADGE,   restaurantId));
                    }

                    // ── RESTAURANT_REQUESTS → thông báo request list + badge ─
                    case "RESTAURANT_REQUESTS" -> {
                        server.broadcast(WsEvent.of(WsTopic.REQUEST_LIST, restaurantId));
                        server.broadcast(WsEvent.of(WsTopic.BADGE,        restaurantId));
                    }

                    default -> LOGGER.log(Level.FINE,
                            "[DcnBridge] Bảng không được map: {0} — bỏ qua.", tableName);
                }
            }
        }
    }
}