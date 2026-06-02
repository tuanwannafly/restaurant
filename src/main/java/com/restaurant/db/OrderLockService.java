package com.restaurant.db;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OrderLockService – Distributed lock tầng Application cho các thao tác trên Order.
 *
 * <p><b>Mục đích:</b> Phòng chống <b>Phantom Read</b> khi 2 luồng đồng thời:
 * <ul>
 *   <li>Waiter / Tablet thêm món ({@code addOrderItems}) → INSERT rows mới vào order_items</li>
 *   <li>Cashier thanh toán ({@code completeOrderSafe} / {@code requestPaymentSafe}) →
 *       đọc danh sách items rồi quyết định đóng đơn</li>
 * </ul>
 *
 * <p><b>Phantom Read scenario:</b>
 * <pre>
 *   T1 (Cashier):  SELECT COUNT(*) WHERE order_id=5 AND item_status='PENDING' → 0   ← đọc lần 1
 *   T2 (Waiter) :  INSERT order_items (món mới, PENDING) → COMMIT
 *   T1 (Cashier):  SELECT COUNT(*) WHERE order_id=5 AND item_status='PENDING' → 1   ← PHANTOM!
 *                  Nhưng T1 đã quyết định complete dựa trên lần đọc đầu → bỏ sót món!
 * </pre>
 *
 * <p><b>Chiến lược 2 lớp:</b>
 * <ol>
 *   <li><b>Tầng Application (lớp này)</b>: Redis SET NX/EX với key {@code order:lock:{orderId}}
 *       → chặn race ngay từ đầu; fallback ConcurrentHashMap khi Redis down</li>
 *   <li><b>Tầng DB</b>: {@code SELECT ... FOR UPDATE} trên hàng {@code orders}
 *       → safety-net: bất kỳ transaction nào cũng phải lock hàng orders trước khi thao tác items
 *       → T2 bị BLOCK đến khi T1 COMMIT</li>
 * </ol>
 *
 * <p><b>Cách dùng điển hình (OrderDAO):</b>
 * <pre>{@code
 * OrderLockService lock = OrderLockService.getInstance();
 * boolean redisLocked = lock.tryAcquire(orderId);
 * try (Connection conn = ...) {
 *     conn.setAutoCommit(false);
 *     // DB layer: lock order row (safety-net khi Redis down)
 *     lockOrderRow(conn, orderId);
 *     // ... đọc / ghi items
 *     conn.commit();
 * } finally {
 *     if (redisLocked) lock.release(orderId);
 * }
 * }</pre>
 *
 * <p><b>Redis dependency (pom.xml):</b> cùng với KitchenLockService – không cần thêm.
 */
public class OrderLockService {

    private static final Logger LOGGER = Logger.getLogger(OrderLockService.class.getName());

    /** TTL của lock (giây) – đủ cho luồng thanh toán / thêm món hoàn thành. */
    private static final int LOCK_TTL_SECONDS = 30;

    /** Prefix Redis – tránh xung đột với KitchenLockService. */
    private static final String KEY_PREFIX = "order:lock:";

    // ── Singleton ──────────────────────────────────────────────────────────────

    private static volatile OrderLockService INSTANCE;

    public static OrderLockService getInstance() {
        if (INSTANCE == null) {
            synchronized (OrderLockService.class) {
                if (INSTANCE == null) INSTANCE = new OrderLockService();
            }
        }
        return INSTANCE;
    }

    // ── State ──────────────────────────────────────────────────────────────────

    /** Fallback in-memory lock khi Redis không khả dụng. */
    private final ConcurrentHashMap<String, Boolean> localLocks = new ConcurrentHashMap<>();

    private volatile redis.clients.jedis.Jedis jedis;
    private volatile boolean redisAvailable = false;

    private OrderLockService() {
        initRedis();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Thử lấy lock cho {@code orderId}.
     *
     * @param orderId order_id cần khoá
     * @return {@code true} nếu lấy được lock, {@code false} nếu đã bị giữ bởi luồng khác
     */
    public boolean tryAcquire(String orderId) {
        if (orderId == null || orderId.isBlank()) return false;
        return redisAvailable ? tryAcquireRedis(orderId) : tryAcquireLocal(orderId);
    }

    /**
     * Giải phóng lock của {@code orderId}. Luôn gọi trong {@code finally} block.
     *
     * @param orderId order_id cần giải phóng
     */
    public void release(String orderId) {
        if (orderId == null || orderId.isBlank()) return;
        if (redisAvailable) {
            releaseRedis(orderId);
        } else {
            releaseLocal(orderId);
        }
    }

    /** Kiểm tra lock hiện có đang dùng Redis hay in-memory fallback. */
    public boolean isRedisAvailable() { return redisAvailable; }

    // ── Redis ──────────────────────────────────────────────────────────────────

    private void initRedis() {
        try {
            jedis = new redis.clients.jedis.Jedis("localhost", 6379);
            jedis.ping();
            redisAvailable = true;
            LOGGER.info("[OrderLockService] Redis connected — dùng distributed lock cho order.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                "[OrderLockService] Redis không khả dụng — fallback in-memory. " +
                "Phantom Read vẫn được chặn bởi Oracle SELECT FOR UPDATE.", e);
            redisAvailable = false;
            if (jedis != null) {
                try { jedis.close(); } catch (Exception ignored) {}
                jedis = null;
            }
        }
    }

    private boolean tryAcquireRedis(String orderId) {
        try {
            String key = KEY_PREFIX + orderId;
            redis.clients.jedis.params.SetParams params =
                redis.clients.jedis.params.SetParams.setParams().nx().ex(LOCK_TTL_SECONDS);
            String result = jedis.set(key, "1", params);
            boolean acquired = "OK".equals(result);
            if (!acquired)
                LOGGER.log(Level.INFO, "[OrderLockService] Redis lock BUSY — orderId={0}", orderId);
            return acquired;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                "[OrderLockService] Redis lỗi trong tryAcquire — fallback local. orderId=" + orderId, e);
            redisAvailable = false;
            return tryAcquireLocal(orderId);
        }
    }

    private void releaseRedis(String orderId) {
        try {
            jedis.del(KEY_PREFIX + orderId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                "[OrderLockService] Redis lỗi trong release — orderId=" + orderId, e);
            redisAvailable = false;
            releaseLocal(orderId);
        }
    }

    // ── In-memory fallback ─────────────────────────────────────────────────────

    private boolean tryAcquireLocal(String orderId) {
        boolean acquired = (localLocks.putIfAbsent(orderId, Boolean.TRUE) == null);
        if (!acquired)
            LOGGER.log(Level.INFO, "[OrderLockService] Local lock BUSY — orderId={0}", orderId);
        return acquired;
    }

    private void releaseLocal(String orderId) {
        localLocks.remove(orderId);
    }
}