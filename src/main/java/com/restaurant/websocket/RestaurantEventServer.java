package com.restaurant.websocket;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * WebSocket server nội bộ cho SmartRestaurant.
 *
 * <h3>Chức năng chính</h3>
 * <ul>
 *   <li>Lắng nghe kết nối WebSocket tại cổng mặc định 8025
 *       (tuỳ chỉnh qua system property {@code ws.port}).</li>
 *   <li>Quản lý danh sách subscriber theo topic — mỗi topic ánh xạ tới
 *       một {@link CopyOnWriteArraySet} thread-safe.</li>
 *   <li>Nhận lệnh subscribe từ client dạng {@code "SUB:<topic>"}.</li>
 *   <li>Broadcast {@link WsEvent} JSON đến tất cả WebSocket đang subscribe
 *       topic tương ứng.</li>
 * </ul>
 *
 * <h3>Giao thức wire</h3>
 * <pre>
 *   Client → Server  :  "SUB:orders"          (đăng ký topic)
 *   Server → Client  :  {"topic":"orders","restaurantId":1}  (JSON event)
 * </pre>
 *
 * <h3>Sử dụng</h3>
 * <pre>{@code
 * // Khởi động (gọi sau khi login)
 * RestaurantEventServer.getInstance().start();
 *
 * // Broadcast từ OracleDcnBridge
 * RestaurantEventServer.getInstance().broadcast(WsEvent.of(WsTopic.ORDERS, restaurantId));
 *
 * // Dừng (gọi khi logout hoặc đóng app)
 * RestaurantEventServer.getInstance().stop();
 * }</pre>
 *
 * <h3>Thread-safety</h3>
 * <ul>
 *   <li>{@code subscribers} là {@link ConcurrentHashMap} — ghi đồng thời an toàn.</li>
 *   <li>Mỗi Set value là {@link CopyOnWriteArraySet} — iteration an toàn khi
 *       broadcast kể cả có subscribe/unsubscribe đồng thời.</li>
 * </ul>
 */
public final class RestaurantEventServer extends WebSocketServer {

    private static final Logger LOGGER = Logger.getLogger(RestaurantEventServer.class.getName());

    /** Cổng mặc định — ghi đè bằng {@code -Dws.port=<port>}. */
    public static final int DEFAULT_PORT = 8025;

    /** Prefix dùng trong frame subscribe từ client. */
    private static final String SUB_PREFIX = "SUB:";

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile RestaurantEventServer instance;

    /**
     * Lấy singleton instance. Cổng được xác định một lần tại lần gọi đầu tiên.
     *
     * @return singleton RestaurantEventServer
     */
    public static RestaurantEventServer getInstance() {
        if (instance == null) {
            synchronized (RestaurantEventServer.class) {
                if (instance == null) {
                    int port = Integer.getInteger("ws.port", DEFAULT_PORT);
                    instance = new RestaurantEventServer(port);
                }
            }
        }
        return instance;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * Map topic → Set WebSocket đang subscribe.
     * ConcurrentHashMap + CopyOnWriteArraySet đảm bảo thread-safety cho
     * cả thao tác ghi (subscribe) lẫn đọc (broadcast iteration).
     */
    private final Map<String, Set<WebSocket>> subscribers = new ConcurrentHashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    private RestaurantEventServer(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
        LOGGER.info("[WsServer] Khởi tạo tại cổng " + port);
    }

    // ── WebSocketServer lifecycle ─────────────────────────────────────────────

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        LOGGER.fine("[WsServer] Client kết nối: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        LOGGER.fine("[WsServer] Client ngắt kết nối: " + conn.getRemoteSocketAddress()
                + " (code=" + code + ")");
        // Xoá conn khỏi mọi topic để tránh giữ reference stale.
        subscribers.values().forEach(set -> set.remove(conn));
    }

    /**
     * Xử lý frame text từ client.
     *
     * <p>Hiện tại chỉ nhận lệnh subscribe dạng {@code "SUB:<topic>"}.
     * Frame nào không khớp prefix sẽ bị bỏ qua và log ở mức FINE.
     */
    @Override
    public void onMessage(WebSocket conn, String message) {
        if (message != null && message.startsWith(SUB_PREFIX)) {
            String topic = message.substring(SUB_PREFIX.length()).trim();
            subscribe(conn, topic);
        } else {
            LOGGER.fine("[WsServer] Frame không rõ từ " + conn.getRemoteSocketAddress()
                    + ": " + message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String addr = (conn != null) ? String.valueOf(conn.getRemoteSocketAddress()) : "null";
        LOGGER.log(Level.WARNING, "[WsServer] Lỗi WebSocket từ " + addr, ex);
    }

    @Override
    public void onStart() {
        LOGGER.info("[WsServer] Server khởi động thành công tại cổng "
                + getAddress().getPort());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Đăng ký một WebSocket connection vào một topic.
     *
     * <p>Thread-safe: có thể gọi từ bất kỳ thread nào.
     *
     * @param conn  WebSocket client muốn subscribe
     * @param topic tên topic (xem {@link WsTopic})
     */
    public void subscribe(WebSocket conn, String topic) {
        subscribers
                .computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>())
                .add(conn);
        LOGGER.fine("[WsServer] Subscribe: " + conn.getRemoteSocketAddress()
                + " → topic='" + topic + "'");
    }

    /**
     * Broadcast một {@link WsEvent} JSON đến tất cả WebSocket đang subscribe topic.
     *
     * <p>Thread-safe: có thể gọi từ bất kỳ thread nào (ví dụ DCN callback thread
     * của Oracle JDBC).<br>
     * Các connection đã đóng được tự động bỏ qua bởi thư viện Java-WebSocket.
     *
     * @param event sự kiện cần gửi
     */
    public void broadcast(WsEvent event) {
        if (event == null) return;

        Set<WebSocket> targets = subscribers.getOrDefault(
                event.getTopic(), Collections.emptySet());

        if (targets.isEmpty()) {
            LOGGER.fine("[WsServer] Broadcast topic='" + event.getTopic()
                    + "' — không có subscriber.");
            return;
        }

        String json = event.toJson();
        int sent = 0;
        for (WebSocket ws : targets) {
            try {
                if (ws.isOpen()) {
                    ws.send(json);
                    sent++;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "[WsServer] Không thể gửi tới " + ws.getRemoteSocketAddress(), e);
            }
        }
        LOGGER.fine("[WsServer] Broadcast topic='" + event.getTopic()
                + "' → " + sent + " client(s).");
    }

    /**
     * Trả về map read-only cho mục đích debug / monitoring.
     *
     * @return unmodifiable view của subscriber map
     */
    public Map<String, Set<WebSocket>> getSubscribers() {
        return Collections.unmodifiableMap(subscribers);
    }

    /**
     * Dừng WebSocket server, giải phóng cổng.
     * Nên gọi khi logout hoặc khi ứng dụng đóng.
     */
    @Override
    public void stop() {
        try {
            super.stop();
            LOGGER.info("[WsServer] Server đã dừng.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "[WsServer] Bị interrupt khi dừng server.", e);
        }
    }
}