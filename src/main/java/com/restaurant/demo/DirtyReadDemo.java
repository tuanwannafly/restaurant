package com.restaurant.demo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.db.DBConnection;
import com.restaurant.db.KitchenLockService;
import com.restaurant.model.Order;

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║              DEMO: Dirty Read & Cách Phòng Tránh trên Oracle            ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  Kiến trúc 2 lớp của hệ thống:                                          ║
 * ║                                                                          ║
 * ║  [Tầng Application]  Redis SET NX/EX  (KitchenLockService)              ║
 * ║        │  nếu Redis down → fallback ConcurrentHashMap                   ║
 * ║        ▼                                                                  ║
 * ║  [Tầng DB]  Oracle READ_COMMITTED + Conditional UPDATE                  ║
 * ║             "UPDATE ... WHERE item_status = ?"                           ║
 * ║                                                                          ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  Tại sao KHÔNG dùng SERIALIZABLE?                                       ║
 * ║  - Oracle SERIALIZABLE ném ORA-08177 khi 2 writer đụng nhau            ║
 * ║  - Cần retry logic phức tạp → không cần thiết                          ║
 * ║  - READ_COMMITTED + MVCC đã ngăn dirty read hoàn toàn                  ║
 * ║  - Redis lock chặn race trước khi vào DB                                ║
 * ║  - Conditional UPDATE làm safety-net kể cả khi lock bị bypass           ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * <p>Chạy từng scenario bằng cách gọi {@code main()}.
 * Cần Oracle đang chạy và db.properties hợp lệ.</p>
 */
public class DirtyReadDemo {

    private static final Logger LOG = Logger.getLogger(DirtyReadDemo.class.getName());

    // ── Dùng order_item_id thực từ DB của bạn ─────────────────────────────────
    private static final String TEST_ITEM_ID = "1";   // ← đổi sang ID có thật

    // ══════════════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║       DIRTY READ DEMO — Oracle READ_COMMITTED    ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        // 1. Giải thích khái niệm (không cần DB)
        explainDirtyRead();

        System.out.println("\n" + "─".repeat(60) + "\n");

        // 2. Demo trực tiếp trên Oracle: chứng minh READ_COMMITTED ngăn dirty read
        demoDirtyReadPreventedByOracle();

        System.out.println("\n" + "─".repeat(60) + "\n");

        // 3. Demo lost update: chứng minh conditional UPDATE là safety-net
        demoLostUpdatePreventedByConditionalUpdate();

        System.out.println("\n" + "─".repeat(60) + "\n");

        // 4. Demo toàn bộ 2 lớp bảo vệ: Redis lock + conditional UPDATE
        demoTwoLayerProtection();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCENARIO 0 — Giải thích không cần DB
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * In ra bảng minh hoạ dirty read, không chạy DB.
     */
    static void explainDirtyRead() {
        System.out.println("[ SCENARIO 0 ] Dirty Read là gì?");
        System.out.println();
        System.out.println("  T1 (Chef A)            T2 (Chef B)");
        System.out.println("  ─────────────────────────────────────────────────────");
        System.out.println("  BEGIN TRANSACTION");
        System.out.println("  UPDATE item_status");
        System.out.println("    = 'COOKING'           -- T1 chưa COMMIT");
        System.out.println("  (ngủ 2 giây...)");
        System.out.println("                          SELECT item_status");
        System.out.println("                            → 'COOKING'  ← ĐÂY LÀ DIRTY READ!");
        System.out.println("                            (đọc uncommitted data)");
        System.out.println("  ROLLBACK  ←─ T1 huỷ");
        System.out.println("                          Chef B đã thấy 'COOKING' nhưng");
        System.out.println("                          thực tế item vẫn là 'PENDING' !!!");
        System.out.println();
        System.out.println("  ✗ Hậu quả: Chef B bỏ nhận món vì nghĩ đã có người nhận.");
        System.out.println("             Món thực ra vẫn chưa ai nấu → khách chờ mãi.");
        System.out.println();
        System.out.println("  ★ Oracle READ_COMMITTED dùng MVCC:");
        System.out.println("    T2 luôn đọc snapshot của lần COMMIT cuối cùng.");
        System.out.println("    → T2 thấy 'PENDING' (committed), KHÔNG bao giờ");
        System.out.println("      thấy 'COOKING' chưa commit của T1.");
        System.out.println("    → Dirty read KHÔNG THỂ xảy ra ở tầng DB.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCENARIO 1 — Oracle READ_COMMITTED ngăn dirty read (cần DB)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Chứng minh bằng 2 thread thực:
     * <ol>
     *   <li>T1: UPDATE item_status='COOKING', ngủ 2 giây, rồi ROLLBACK</li>
     *   <li>T2: trong lúc T1 ngủ, đọc item_status → phải thấy giá trị cũ (PENDING)</li>
     * </ol>
     * Nếu T2 thấy 'COOKING' → đó là dirty read → Oracle đang sai (không thể xảy ra).
     * Nếu T2 thấy 'PENDING' → READ_COMMITTED đang hoạt động đúng.
     */
    static void demoDirtyReadPreventedByOracle() throws Exception {
        System.out.println("[ SCENARIO 1 ] Oracle READ_COMMITTED ngăn dirty read");
        System.out.println("  Item ID: " + TEST_ITEM_ID);
        System.out.println();

        // Lấy giá trị ban đầu
        String initialStatus = readItemStatus(TEST_ITEM_ID);
        System.out.println("  Trạng thái ban đầu (committed): " + initialStatus);
        System.out.println();

        // Latch để đồng bộ: T2 đọc đúng lúc T1 đang ngủ (chưa commit)
        CountDownLatch t1Updated  = new CountDownLatch(1); // T1 đã UPDATE, chưa commit
        CountDownLatch t2Done     = new CountDownLatch(1); // T2 đã đọc xong
        AtomicReference<String> t2Saw = new AtomicReference<>();

        // ── Thread 1: writer uncommitted ──────────────────────────────────────
        Thread t1 = new Thread(() -> {
            try (Connection conn = DBConnection.getInstance().getConnection()) {
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                conn.setAutoCommit(false);

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE order_items SET item_status = 'COOKING' " +
                        "WHERE order_item_id = ?")) {
                    ps.setLong(1, Long.parseLong(TEST_ITEM_ID));
                    int rows = ps.executeUpdate();
                    System.out.println("  [T1-Chef A] UPDATE thành công (" + rows
                            + " row), CHƯA COMMIT...");
                }

                t1Updated.countDown();   // báo T2 có thể đọc
                t2Done.await();          // chờ T2 đọc xong

                conn.rollback();         // T1 rollback → item về PENDING
                System.out.println("  [T1-Chef A] ROLLBACK — item trở về trạng thái cũ.");

            } catch (Exception e) {
                System.err.println("  [T1] Lỗi: " + e.getMessage());
                t1Updated.countDown();
            }
        }, "T1-ChefA");

        // ── Thread 2: reader (cùng lúc T1 chưa commit) ───────────────────────
        Thread t2 = new Thread(() -> {
            try {
                t1Updated.await(); // chờ T1 UPDATE xong (nhưng chưa commit)

                String seen = readItemStatus(TEST_ITEM_ID);
                t2Saw.set(seen);
                System.out.println("  [T2-Chef B] Đọc item_status trong lúc T1 chưa commit: "
                        + seen);

            } catch (Exception e) {
                System.err.println("  [T2] Lỗi: " + e.getMessage());
            } finally {
                t2Done.countDown(); // báo T1 có thể rollback
            }
        }, "T2-ChefB");

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════╗");
        if ("COOKING".equals(t2Saw.get())) {
            System.out.println("  ║ ✗ DIRTY READ xảy ra! T2 thấy uncommitted data. ║");
            System.out.println("  ║   Oracle đang KHÔNG chạy READ_COMMITTED ???    ║");
        } else {
            System.out.println("  ║ ✓ PASS: T2 thấy '" + t2Saw.get() + "'              ");
            System.out.println("  ║   (giá trị committed, không phải uncommitted)  ║");
            System.out.println("  ║   READ_COMMITTED + MVCC đang hoạt động đúng!   ║");
        }
        System.out.println("  ╚══════════════════════════════════════════════════╝");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCENARIO 2 — Conditional UPDATE chống lost update (cần DB)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Chứng minh tầng DB safety-net:
     * <ol>
     *   <li>Cả T1 và T2 đều "thấy" item ở PENDING và muốn chuyển sang COOKING.</li>
     *   <li>T1 chạy trước → thành công (1 row).</li>
     *   <li>T2 chạy sau  → WHERE item_status='PENDING' không còn khớp → 0 row → ALREADY_CHANGED.</li>
     * </ol>
     */
    static void demoLostUpdatePreventedByConditionalUpdate() throws Exception {
        System.out.println("[ SCENARIO 2 ] Conditional UPDATE chống Lost Update (Safety-Net DB)");
        System.out.println("  Item ID: " + TEST_ITEM_ID);
        System.out.println();

        // Đặt lại PENDING trước khi test
        resetItemToPending(TEST_ITEM_ID);
        System.out.println("  Đặt lại item_status = PENDING");
        System.out.println();

        KitchenDAO dao = new KitchenDAO();
        CountDownLatch start = new CountDownLatch(1); // cả 2 chef cùng bắt đầu

        AtomicReference<KitchenDAO.UpdateResult> r1 = new AtomicReference<>();
        AtomicReference<KitchenDAO.UpdateResult> r2 = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try { start.await(); } catch (InterruptedException ignored) {}
            r1.set(dao.updateItemStatusSafe(
                    TEST_ITEM_ID,
                    Order.OrderItem.ItemStatus.PENDING,
                    Order.OrderItem.ItemStatus.COOKING));
            System.out.println("  [T1-Chef A] updateItemStatusSafe → " + r1.get());
        }, "T1-ChefA");

        Thread t2 = new Thread(() -> {
            try { start.await(); } catch (InterruptedException ignored) {}
            r2.set(dao.updateItemStatusSafe(
                    TEST_ITEM_ID,
                    Order.OrderItem.ItemStatus.PENDING,
                    Order.OrderItem.ItemStatus.COOKING));
            System.out.println("  [T2-Chef B] updateItemStatusSafe → " + r2.get());
        }, "T2-ChefB");

        t1.start();
        t2.start();
        start.countDown(); // cả 2 bắt đầu cùng lúc
        t1.join();
        t2.join();

        System.out.println();
        boolean oneSuccess  = KitchenDAO.UpdateResult.SUCCESS.equals(r1.get())
                           != KitchenDAO.UpdateResult.SUCCESS.equals(r2.get());
        System.out.println("  ╔══════════════════════════════════════════════════╗");
        if (oneSuccess) {
            System.out.println("  ║ ✓ PASS: Đúng 1 chef nhận được món,              ║");
            System.out.println("  ║         1 chef nhận ALREADY_CHANGED.            ║");
            System.out.println("  ║   Lost update KHÔNG xảy ra.                     ║");
        } else {
            System.out.println("  ║ ✗ CẢ HAI cùng SUCCESS hoặc cùng FAIL          ║");
            System.out.println("  ║   → cần kiểm tra lại logic.                     ║");
        }
        System.out.println("  ╚══════════════════════════════════════════════════╝");

        // Dọn dẹp
        resetItemToPending(TEST_ITEM_ID);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCENARIO 3 — Kiến trúc 2 lớp hoàn chỉnh (cần Redis + DB)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Chứng minh toàn bộ pipeline bảo vệ:
     * <pre>
     *   [App Layer] Redis SET NX/EX → chặn race trước khi vào DB
     *       ↓ (nếu Redis bị bypass / fail)
     *   [DB Layer]  Oracle READ_COMMITTED + Conditional UPDATE → safety-net
     * </pre>
     *
     * T1 giữ Redis lock + đang UPDATE DB.
     * T2 thử vào: bị Redis từ chối ngay ở tầng Application → không vào DB.
     */
    static void demoTwoLayerProtection() throws Exception {
        System.out.println("[ SCENARIO 3 ] Kiến trúc 2 lớp: Redis Lock + Conditional UPDATE");
        System.out.println("  Item ID: " + TEST_ITEM_ID);
        System.out.println();

        resetItemToPending(TEST_ITEM_ID);

        KitchenLockService lock = KitchenLockService.getInstance();
        KitchenDAO         dao  = new KitchenDAO();

        CountDownLatch t1Locked = new CountDownLatch(1);
        CountDownLatch t2Done   = new CountDownLatch(1);

        AtomicReference<Boolean>                  t2LockResult  = new AtomicReference<>();
        AtomicReference<KitchenDAO.UpdateResult>  t2DbResult    = new AtomicReference<>();

        // ── T1: giữ lock + update DB ──────────────────────────────────────────
        Thread t1 = new Thread(() -> {
            if (!lock.tryAcquire(TEST_ITEM_ID)) {
                System.out.println("  [T1] Không lấy được lock (lạ!) — abort");
                t1Locked.countDown();
                return;
            }
            System.out.println("  [T1-Chef A] Đã lấy Redis lock.");
            t1Locked.countDown();  // báo T2 thử vào

            try {
                t2Done.await(); // chờ T2 thử xong rồi mới update
                KitchenDAO.UpdateResult res = dao.updateItemStatusSafe(
                        TEST_ITEM_ID,
                        Order.OrderItem.ItemStatus.PENDING,
                        Order.OrderItem.ItemStatus.COOKING);
                System.out.println("  [T1-Chef A] DB update → " + res);
            } catch (Exception e) {
                System.err.println("  [T1] Lỗi: " + e.getMessage());
            } finally {
                lock.release(TEST_ITEM_ID);
                System.out.println("  [T1-Chef A] Đã giải phóng Redis lock.");
            }
        }, "T1-ChefA");

        // ── T2: bị Redis chặn ở tầng Application ─────────────────────────────
        Thread t2 = new Thread(() -> {
            try {
                t1Locked.await(); // chờ T1 đã giữ lock

                System.out.println("  [T2-Chef B] Thử lấy Redis lock...");
                boolean acquired = lock.tryAcquire(TEST_ITEM_ID);
                t2LockResult.set(acquired);

                if (!acquired) {
                    System.out.println("  [T2-Chef B] ✓ Bị Redis từ chối — không vào được DB.");
                    System.out.println("              UI sẽ hiện: \"Món đang được xử lý\".");
                    t2DbResult.set(null); // không vào DB
                } else {
                    // Lock bị bypass (không bình thường) → vào DB, conditional UPDATE sẽ xử lý
                    System.out.println("  [T2-Chef B] Lock bị bypass! Thử conditional UPDATE...");
                    KitchenDAO.UpdateResult res = dao.updateItemStatusSafe(
                            TEST_ITEM_ID,
                            Order.OrderItem.ItemStatus.PENDING,
                            Order.OrderItem.ItemStatus.COOKING);
                    t2DbResult.set(res);
                    lock.release(TEST_ITEM_ID);
                }
            } catch (Exception e) {
                System.err.println("  [T2] Lỗi: " + e.getMessage());
            } finally {
                t2Done.countDown(); // báo T1 tiến hành update
            }
        }, "T2-ChefB");

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println();
        System.out.println("  Tóm tắt kết quả:");
        System.out.println("  ┌───────────────────────────────────────────────────────┐");
        System.out.println("  │ Tầng 1 (Redis): T2 bị chặn = " + !Boolean.TRUE.equals(t2LockResult.get())
                + "                         │");
        System.out.println("  │ Tầng 2 (DB):    T2 vào DB  = " + (t2DbResult.get() != null)
                + "                         │");
        System.out.println("  └───────────────────────────────────────────────────────┘");

        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════════╗");
        if (!Boolean.TRUE.equals(t2LockResult.get())) {
            System.out.println("  ║ ✓ PASS: Tầng Application (Redis) đã chặn race     ║");
            System.out.println("  ║         trước khi vào DB.                          ║");
            System.out.println("  ║   Dirty read và lost update đều không xảy ra.     ║");
        } else {
            System.out.println("  ║ ⚠ Redis không sẵn sàng → fallback sang tầng DB   ║");
            System.out.println("  ║   Conditional UPDATE đã xử lý: "
                    + t2DbResult.get() + "        ║");
        }
        System.out.println("  ╚══════════════════════════════════════════════════════╝");

        resetItemToPending(TEST_ITEM_ID);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Đọc item_status với READ_COMMITTED — đây là cách T2 (Chef B) nhìn thấy DB.
     */
    static String readItemStatus(String itemId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT item_status FROM order_items WHERE order_item_id = ?")) {
                ps.setLong(1, Long.parseLong(itemId));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString("item_status") : "(not found)";
                }
            }
        }
    }

    /**
     * Đặt lại item về PENDING để tái sử dụng cho các scenario.
     */
    static void resetItemToPending(String itemId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE order_items SET item_status = 'PENDING', assigned_to = NULL " +
                     "WHERE order_item_id = ?")) {
            ps.setLong(1, Long.parseLong(itemId));
            ps.executeUpdate();
        }
    }
}
