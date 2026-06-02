package com.restaurant.db;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * KitchenLockService – Application-level distributed lock cho kitchen panel.
 *
 * <p><b>Mục đích:</b> Safety-net tầng Application để phòng lost update khi
 * 2 đầu bếp cùng bấm "Hoàn thành" / "Bắt đầu nấu" một món cùng lúc.
 *
 * <p><b>Chiến lược 2 lớp:</b>
 * <ol>
 *   <li><b>Tầng DB</b>: {@code KitchenDAO#updateItemStatusSafe} dùng
 *       {@code UPDATE ... WHERE item_status = ?} để đảm bảo chỉ 1 row bị ảnh hưởng.</li>
 *   <li><b>Tầng Application (lớp này)</b>: Redis SET NX/EX làm distributed lock
 *       nếu Redis có sẵn; fallback sang in-memory {@link java.util.concurrent.ConcurrentHashMap}
 *       nếu không kết nối được Redis (môi trường dev / chưa cài Redis).</li>
 * </ol>
 *
 * <p><b>Cách dùng điển hình:</b>
 * <pre>{@code
 * KitchenLockService lock = KitchenLockService.getInstance();
 * if (!lock.tryAcquire(itemId)) {
 *     showToast("Món đang được xử lý bởi người khác");
 *     return;
 * }
 * try {
 *     KitchenDAO.UpdateResult result =
 *         dao.updateItemStatusSafe(itemId, expectedStatus, newStatus);
 *     if (result == UpdateResult.ALREADY_CHANGED) {
 *         showToast("Món đã được đầu bếp khác cập nhật");
 *     }
 * } finally {
 *     lock.release(itemId);
 * }
 * }</pre>
 *
 * <p><b>Redis dependency (pom.xml):</b>
 * <pre>{@code
 * <dependency>
 *   <groupId>redis.clients</groupId>
 *   <artifactId>jedis</artifactId>
 *   <version>5.1.0</version>
 * </dependency>
 * }</pre>
 */
public class KitchenLockService {

    private static final Logger LOGGER =
            Logger.getLogger(KitchenLockService.class.getName());

    /** TTL của lock (giây) — đủ cho 1 network round-trip + DB write. */
    private static final int LOCK_TTL_SECONDS = 5;

    /** Prefix để tránh xung đột key với các service khác trong Redis. */
    private static final String KEY_PREFIX = "kitchen:lock:item:";

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile KitchenLockService INSTANCE;

    public static KitchenLockService getInstance() {
        if (INSTANCE == null) {
            synchronized (KitchenLockService.class) {
                if (INSTANCE == null) INSTANCE = new KitchenLockService();
            }
        }
        return INSTANCE;
    }

    // ── Internal state ────────────────────────────────────────────────────────

    /**
     * Fallback in-memory lock set.
     * Key = itemId, Value = true (locked).
     * Dùng khi Redis không khả dụng.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> localLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Redis client — null nếu không kết nối được.
     * Lazy-init trong {@link #tryAcquire}.
     */
    private volatile redis.clients.jedis.Jedis jedis;

    /** Trạng thái kết nối Redis. false = chạy fallback in-memory. */
    private volatile boolean redisAvailable = false;

    private KitchenLockService() {
        initRedis();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Thử lấy lock cho {@code itemId}.
     *
     * <p>Nếu lock đã bị giữ bởi đầu bếp khác → trả về {@code false} ngay.
     * Nếu không có ai giữ → lấy lock và trả về {@code true}.
     *
     * @param itemId order_item_id cần khoá
     * @return {@code true} nếu lấy lock thành công, {@code false} nếu đã bị khoá
     */
    public boolean tryAcquire(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;

        if (redisAvailable) {
            return tryAcquireRedis(itemId);
        } else {
            return tryAcquireLocal(itemId);
        }
    }

    /**
     * Giải phóng lock của {@code itemId}.
     * Luôn gọi trong {@code finally} block sau khi {@link #tryAcquire} thành công.
     *
     * @param itemId order_item_id cần giải phóng
     */
    public void release(String itemId) {
        if (itemId == null || itemId.isBlank()) return;

        if (redisAvailable) {
            releaseRedis(itemId);
        } else {
            releaseLocal(itemId);
        }
    }

    // ── Redis implementation ──────────────────────────────────────────────────

    private void initRedis() {
        try {
            jedis = new redis.clients.jedis.Jedis("localhost", 6379);
            jedis.ping(); // throws if Redis not running
            redisAvailable = true;
            LOGGER.info("[KitchenLockService] Redis connected — using distributed lock.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                "[KitchenLockService] Redis not available — falling back to in-memory lock. " +
                "Lost-update protection still active via DB conditional UPDATE.", e);
            redisAvailable = false;
            if (jedis != null) {
                try { jedis.close(); } catch (Exception ignored) {}
                jedis = null;
            }
        }
    }

    private boolean tryAcquireRedis(String itemId) {
        try {
            String key = KEY_PREFIX + itemId;
            // SET key "1" NX EX ttl — atomic: chỉ set nếu key chưa tồn tại
            redis.clients.jedis.params.SetParams params =
                redis.clients.jedis.params.SetParams.setParams()
                    .nx()
                    .ex(LOCK_TTL_SECONDS);
            String result = jedis.set(key, "1", params);
            boolean acquired = "OK".equals(result);
            if (!acquired) {
                LOGGER.log(Level.INFO,
                    "[KitchenLockService] Redis lock BUSY — itemId={0}", itemId);
            }
            return acquired;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                "[KitchenLockService] Redis error during tryAcquire — falling back to local. itemId=" + itemId, e);
            redisAvailable = false;
            return tryAcquireLocal(itemId);
        }
    }

    private void releaseRedis(String itemId) {
        try {
            jedis.del(KEY_PREFIX + itemId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                "[KitchenLockService] Redis error during release — itemId=" + itemId, e);
            redisAvailable = false;
            releaseLocal(itemId);
        }
    }

    // ── In-memory fallback ────────────────────────────────────────────────────

    private boolean tryAcquireLocal(String itemId) {
        // putIfAbsent trả về null khi key chưa tồn tại (tức là lấy được lock)
        Boolean prev = localLocks.putIfAbsent(itemId, Boolean.TRUE);
        boolean acquired = (prev == null);
        if (!acquired) {
            LOGGER.log(Level.INFO,
                "[KitchenLockService] Local lock BUSY — itemId={0}", itemId);
        }
        return acquired;
    }

    private void releaseLocal(String itemId) {
        localLocks.remove(itemId);
    }
}
