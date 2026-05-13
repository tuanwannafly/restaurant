package com.restaurant.websocket;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * WebSocket client cho SmartRestaurant — nhận push event từ
 * {@link RestaurantEventServer} và dispatch callback trên FX Application Thread.
 *
 * <h3>Tính năng</h3>
 * <ul>
 *   <li>Kết nối tới {@code ws://localhost:{port}} (port từ system property
 *       {@code ws.port}, mặc định 8025).</li>
 *   <li>Subscribe một hoặc nhiều topic bằng {@link #subscribe(String...)}.</li>
 *   <li>Nhận {@link WsEvent} và dispatch consumer callback
 *       trên FX Application Thread ({@link Platform#runLater}).</li>
 *   <li>Auto-reconnect với exponential backoff:
 *       1 s → 2 s → 4 s → 8 s → 16 s → 30 s (tối đa).</li>
 * </ul>
 *
 * <h3>Sử dụng</h3>
 * <pre>{@code
 * RestaurantEventClient client = RestaurantEventClient.getInstance();
 *
 * // Đăng ký handler TRƯỚC khi connect (hoặc sau cũng được — handler persist qua reconnect)
 * client.onEvent(event -> {
 *     if (WsTopic.ORDERS.equals(event.getTopic())) refreshOrderList();
 * });
 *
 * // Kết nối và subscribe
 * client.connect();
 * client.subscribe(WsTopic.ORDERS, WsTopic.BADGE);
 *
 * // Khi logout
 * client.disconnect();
 * }</pre>
 *
 * <h3>Thread-safety</h3>
 * <p>Mọi callback {@code eventHandler} đều được gọi qua {@link Platform#runLater}
 * nên an toàn để cập nhật JavaFX node trực tiếp.
 */
public final class RestaurantEventClient extends WebSocketClient {

    private static final Logger LOGGER = Logger.getLogger(RestaurantEventClient.class.getName());

    // ── Reconnect backoff config ───────────────────────────────────────────────

    /** Delay đầu tiên khi reconnect (ms). */
    private static final long BACKOFF_INITIAL_MS = 1_000L;

    /** Giới hạn tối đa delay reconnect (ms). */
    private static final long BACKOFF_MAX_MS     = 30_000L;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile RestaurantEventClient instance;

    /**
     * Lấy singleton instance. URI được tính từ system property {@code ws.port}.
     *
     * @return singleton RestaurantEventClient
     */
    public static RestaurantEventClient getInstance() {
        if (instance == null) {
            synchronized (RestaurantEventClient.class) {
                if (instance == null) {
                    int port = Integer.getInteger("ws.port", RestaurantEventServer.DEFAULT_PORT);
                    URI uri  = URI.create("ws://localhost:" + port);
                    instance = new RestaurantEventClient(uri);
                }
            }
        }
        return instance;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * Danh sách handler nhận event — hỗ trợ nhiều controller cùng đăng ký.
     * CopyOnWriteArrayList đảm bảo thread-safe khi iterate + add/remove đồng thời.
     */
    private final CopyOnWriteArrayList<Consumer<WsEvent>> eventHandlers =
            new CopyOnWriteArrayList<>();

    /** Topics đã subscribe — được gửi lại sau mỗi lần reconnect thành công. */
    private volatile String[] pendingTopics = new String[0];

    /** Delay hiện tại của backoff (ms). */
    private long backoffMs = BACKOFF_INITIAL_MS;

    /**
     * Flag ngừng reconnect — đặt thành {@code true} khi {@link #disconnect()} được gọi
     * để tránh reconnect vô tận sau khi logout.
     */
    private volatile boolean intentionalClose = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    private RestaurantEventClient(URI serverUri) {
        super(serverUri);
        setConnectionLostTimeout(30); // Java-WebSocket built-in ping/pong (giây)
    }

    // ── WebSocketClient callbacks ─────────────────────────────────────────────

    @Override
    public void onOpen(ServerHandshake handshake) {
        LOGGER.info("[WsClient] Kết nối thành công tới " + getURI());
        backoffMs = BACKOFF_INITIAL_MS; // reset backoff khi kết nối thành công

        // Gửi lại tất cả topic subscribe sau mỗi lần (re)connect
        String[] topics = pendingTopics;
        if (topics.length > 0) {
            sendSubscribeFrames(topics);
        }
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.isBlank()) return;

        try {
            WsEvent event = WsEvent.fromJson(message);
            // Snapshot handlers để tránh ConcurrentModificationException
            Consumer<WsEvent>[] handlers = eventHandlers.toArray(new Consumer[0]);
            if (handlers.length > 0) {
                // Luôn dispatch trên FX Application Thread để controller cập nhật UI an toàn
                Platform.runLater(() -> {
                    for (Consumer<WsEvent> handler : handlers) {
                        try {
                            handler.accept(event);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING,
                                    "[WsClient] Lỗi trong event handler với event: " + event, e);
                        }
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "[WsClient] Không thể parse message: " + message, e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        LOGGER.info("[WsClient] Kết nối đóng — code=" + code
                + ", reason=" + reason + ", remote=" + remote);

        if (!intentionalClose) {
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        LOGGER.log(Level.WARNING, "[WsClient] Lỗi WebSocket", ex);
        // scheduleReconnect() sẽ được gọi từ onClose() — không gọi lại ở đây
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Đăng ký danh sách topic cần nhận event.
     *
     * <p>Nếu đang kết nối, gửi frame subscribe ngay lập tức.
     * Nếu chưa kết nối, lưu lại và gửi sau khi {@link #onOpen} được gọi.
     *
     * <p>Gọi lại {@code subscribe()} sẽ <em>thêm</em> topic (không thay thế).
     *
     * @param topics một hoặc nhiều topic (xem {@link WsTopic})
     */
    public void subscribe(String... topics) {
        if (topics == null || topics.length == 0) return;

        // Merge với pendingTopics hiện tại
        String[] current = pendingTopics;
        String[] merged  = Arrays.copyOf(current, current.length + topics.length);
        System.arraycopy(topics, 0, merged, current.length, topics.length);
        pendingTopics = merged;

        // Gửi ngay nếu đang kết nối
        if (isOpen()) {
            sendSubscribeFrames(topics);
        }
    }

    /**
     * Đăng ký consumer nhận {@link WsEvent}. Hỗ trợ nhiều handler đồng thời.
     *
     * <p>Consumer luôn được gọi trên FX Application Thread nên có thể cập nhật
     * UI trực tiếp mà không cần {@code Platform.runLater()} bổ sung.
     *
     * <p>Trả về một {@link Runnable} cancel-token — gọi nó để huỷ đăng ký
     * handler này mà không ảnh hưởng đến handler của controller khác.
     *
     * <pre>{@code
     * // Trong initialize():
     * cancelWs = RestaurantEventClient.getInstance().addEventHandler(event -> { ... });
     *
     * // Trong cleanup():
     * if (cancelWs != null) { cancelWs.run(); cancelWs = null; }
     * }</pre>
     *
     * @param handler consumer nhận WsEvent
     * @return Runnable để huỷ đăng ký handler này
     */
    public Runnable addEventHandler(Consumer<WsEvent> handler) {
        if (handler == null) return () -> {};
        eventHandlers.add(handler);
        return () -> eventHandlers.remove(handler);
    }

    /**
     * Relay event đến WsServer đang chạy (có thể ở process khác) bằng cách
     * gửi frame {@code "PUB:<json>"} qua kết nối WebSocket hiện tại.
     *
     * <p>Dùng khi {@link RestaurantEventServer} trong process này không chạy được
     * (port bị chiếm). WsClient đã kết nối đến server của process kia — gửi PUB
     * để server đó broadcast đến tất cả subscriber, bao gồm TableOrderStage.</p>
     *
     * @param event sự kiện cần relay
     */
    public void publishToServer(WsEvent event) {
        if (event == null) return;
        if (!isOpen()) {
            LOGGER.warning("[WsClient] publishToServer: kết nối chưa sẵn sàng, bỏ qua event: " + event);
            return;
        }
        try {
            send("PUB:" + event.toJson());
            LOGGER.fine("[WsClient] PUB → server: topic='" + event.getTopic() + "'");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[WsClient] publishToServer thất bại: " + event, e);
        }
    }

    /**
     * @deprecated Dùng {@link #addEventHandler(Consumer)} thay thế.
     * Phương thức này XÓA TOÀN BỘ handler và thêm handler mới (hoặc xóa hết nếu null).
     * Không dùng khi nhiều controller cùng subscribe.
     */
    @Deprecated
    public void onEvent(Consumer<WsEvent> handler) {
        eventHandlers.clear();
        if (handler != null) eventHandlers.add(handler);
    }

    /**
     * Ngắt kết nối và vô hiệu hoá auto-reconnect.
     * Gọi khi logout hoặc đóng ứng dụng.
     */
    public void disconnect() {
        intentionalClose = true;
        pendingTopics     = new String[0];
        eventHandlers.clear();
        if (isOpen()) {
            close();
        }
        LOGGER.info("[WsClient] Đã ngắt kết nối theo yêu cầu.");
    }

    /**
     * Kết nối (hoặc kết nối lại) tới server.
     *
     * <p>Reset flag {@link #intentionalClose} để auto-reconnect hoạt động trở lại.
     * An toàn khi gọi nhiều lần.
     */
    @Override
    public void connect() {
        intentionalClose = false;
        backoffMs        = BACKOFF_INITIAL_MS;
        super.connect();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Gửi frame subscribe cho từng topic.
     * Format: {@code "SUB:<topic>"}
     */
    private void sendSubscribeFrames(String[] topics) {
        for (String topic : topics) {
            if (topic != null && !topic.isBlank()) {
                try {
                    send("SUB:" + topic);
                    LOGGER.fine("[WsClient] Đã subscribe topic='" + topic + "'");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                            "[WsClient] Không thể gửi frame subscribe cho topic: " + topic, e);
                }
            }
        }
    }

    /**
     * Lên lịch reconnect với exponential backoff.
     *
     * <p>Backoff tăng gấp đôi mỗi lần thất bại cho đến khi đạt {@link #BACKOFF_MAX_MS}.
     * Thread được đặt là daemon để không chặn JVM shutdown.
     */
    private void scheduleReconnect() {
        long delay = backoffMs;
        // Tăng backoff cho lần tiếp theo, giới hạn tại BACKOFF_MAX_MS
        backoffMs = Math.min(backoffMs * 2, BACKOFF_MAX_MS);

        LOGGER.info("[WsClient] Sẽ thử reconnect sau " + delay + " ms…");

        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delay);
                if (!intentionalClose && !isOpen()) {
                    LOGGER.info("[WsClient] Đang thử reconnect…");
                    reconnect();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "[WsClient] Reconnect thất bại.", e);
                // scheduleReconnect lại sẽ được trigger bởi onClose()
            }
        }, "ws-reconnect");
        t.setDaemon(true);
        t.start();
    }
}