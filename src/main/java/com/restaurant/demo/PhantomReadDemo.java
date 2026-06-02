package com.restaurant.demo;

import com.restaurant.dao.OrderDAO;
import com.restaurant.db.DBConnection;
import com.restaurant.db.OrderLockService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║         DEMO: Phantom Read & Cách Fix 2 Lớp trên Oracle                    ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                              ║
 * ║  Phantom Read xảy ra khi:                                                   ║
 * ║    Tx A đọc tập hàng → Tx B INSERT hàng mới + COMMIT → Tx A đọc lại        ║
 * ║    → thấy NHIỀU hàng hơn trước!  (hàng "ma" xuất hiện)                     ║
 * ║                                                                              ║
 * ║  Luồng bị ảnh hưởng trong hệ thống:                                         ║
 * ║    • addOrderItems (waiter/tablet thêm món) → tạo ra "phantom rows"         ║
 * ║    • completeOrderSafe (cashier thanh toán)  → nạn nhân bị đọc sai          ║
 * ║                                                                              ║
 * ║  Kiến trúc 2 lớp bảo vệ:                                                    ║
 * ║                                                                              ║
 * ║  [Tầng Application]  Redis SET NX/EX  (OrderLockService)                    ║
 * ║        │  key: order:lock:{orderId}                                          ║
 * ║        │  nếu Redis down → fallback ConcurrentHashMap                       ║
 * ║        ▼                                                                      ║
 * ║  [Tầng DB]  Oracle READ_COMMITTED + SELECT ... FOR UPDATE trên orders        ║
 * ║             → waiter muốn INSERT phải lock hàng orders trước                ║
 * ║             → cashier đang giữ lock → waiter bị BLOCK (không throw)         ║
 * ║             → Phantom Row không thể lọt vào giữa 2 lần đọc của cashier     ║
 * ║                                                                              ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  Tại sao KHÔNG dùng SERIALIZABLE?                                           ║
 * ║  → Oracle SERIALIZABLE ném ORA-08177 khi có write conflict → cần retry loop ║
 * ║  → SELECT FOR UPDATE chỉ block writer, không throw exception               ║
 * ║  → READ_COMMITTED + FOR UPDATE đủ an toàn, Oracle-idiomatic, nhẹ hơn       ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 *
 * <p>Chạy {@code main()} để xem 3 scenario theo thứ tự:</p>
 * <ol>
 *   <li>Giải thích Phantom Read (không cần DB)</li>
 *   <li>Tái hiện lỗi: waiter thêm món trong khi cashier đang thanh toán → phantom!</li>
 *   <li>Demo đã fix: Redis lock + Oracle FOR UPDATE ngăn phantom hoàn toàn</li>
 * </ol>
 *
 * <p>Cần Oracle đang chạy và {@code db.properties} hợp lệ.
 * Đổi {@link #TEST_ORDER_ID} sang {@code order_id} có thật và đang ACTIVE.</p>
 */
public class PhantomReadDemo {

    /**
     * order_id của một đơn hàng đang ACTIVE (trạng thái PENDING / ACCEPTED / COOKING).
     * Đổi sang order_id thực trong DB của bạn.
     * Lưu ý: demo sẽ thêm/xóa order_items tạm thời cho order này.
     */
    private static final String TEST_ORDER_ID = "1";

    /**
     * menu_item_id hợp lệ trong DB để insert order_item test.
     * Đổi sang item_id thực trong bảng menu_items của bạn.
     */
    private static final String TEST_MENU_ITEM_ID = "1";

    // ══════════════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║        PHANTOM READ DEMO — Oracle READ_COMMITTED               ║");
        System.out.println("║        Luồng: addOrderItems  ↔  completeOrderSafe             ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // 1. Giải thích (không cần DB)
        explainPhantomRead();

        System.out.println("\n" + "─".repeat(66) + "\n");

        // 2. Tái hiện lỗi: Phantom Read không có fix
        demoPhantomReadWithoutFix();

        System.out.println("\n" + "─".repeat(66) + "\n");

        // 3. Demo đã fix: 2 lớp bảo vệ
        demoPhantomReadFixed();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCENARIO 0 — Giải thích, không cần DB
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * In bảng minh hoạ Phantom Read trong ngữ cảnh thanh toán nhà hàng.
     */
    static void explainPhantomRead() {
        System.out.println("[ SCENARIO 0 ] Phantom Read là gì?\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │  T1 (Cashier – thanh toán)          T2 (Waiter – thêm món)   │");
        System.out.println("  ├──────────────────────────────────────────────────────────────┤");
        System.out.println("  │  BEGIN TRANSACTION                                            │");
        System.out.println("  │                                                               │");
        System.out.println("  │  SELECT COUNT(*) WHERE item_status                            │");
        System.out.println("  │    IN ('PENDING','COOKING','READY')                           │");
        System.out.println("  │    → 0   ← đọc lần 1: tất cả đã DELIVERED                   │");
        System.out.println("  │                                       BEGIN TRANSACTION        │");
        System.out.println("  │                                       INSERT order_items       │");
        System.out.println("  │                                         (2 món mới, PENDING)  │");
        System.out.println("  │                                       COMMIT ✓               │");
        System.out.println("  │                                                               │");
        System.out.println("  │  SELECT COUNT(*) WHERE item_status    ← cùng transaction      │");
        System.out.println("  │    IN ('PENDING','COOKING','READY')                           │");
        System.out.println("  │    → 2   ← đọc lần 2: CÓ 2 PHANTOM ROWS xuất hiện!          │");
        System.out.println("  │                                                               │");
        System.out.println("  │  UPDATE orders SET status='COMPLETED'                         │");
        System.out.println("  │    ← complete dựa trên lần đọc đầu = lần 1 (đã cũ)          │");
        System.out.println("  │    → 2 món PENDING bị bỏ sót trong đơn đã COMPLETED!        │");
        System.out.println("  │  COMMIT                                                       │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ Khác biệt so với Non-Repeatable Read:");
        System.out.println("    Non-Repeatable Read: CÙNG ROW, khác giá trị (UPDATE bởi T2)");
        System.out.println("    Phantom Read:        KHÁC SỐ LƯỢNG HÀNG (INSERT bởi T2)\n");

        System.out.println("  ★ Hậu quả trong nhà hàng:");
        System.out.println("    Cashier thấy 'tất cả món đã phục vụ' → bấm thanh toán");
        System.out.println("    Nhưng waiter vừa thêm 2 món mới ngay lúc đó");
        System.out.println("    → Đơn COMPLETED nhưng 2 món PENDING không ai biết xử lý");
        System.out.println("    → Khách không bị tính tiền 2 món, nhà bếp không nấu\n");

        System.out.println("  ★ Fix đúng trên Oracle (READ_COMMITTED):");
        System.out.println("    1. [App layer]  Redis lock  key=order:lock:{orderId}");
        System.out.println("       → ngăn waiter addOrderItems khi cashier đang thanh toán");
        System.out.println("    2. [DB layer]   SELECT order_id FROM orders WHERE ... FOR UPDATE");
        System.out.println("       → addOrderItems cũng phải lock hàng orders trước khi INSERT");
        System.out.println("       → cashier giữ lock → waiter BLOCK → không Phantom nào lọt vào");
        System.out.println("       → Khi cashier COMMIT, waiter unblock nhưng phát hiện");
        System.out.println("         order đã COMPLETED → từ chối INSERT (trả false)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCENARIO 1 — Tái hiện Phantom Read không có fix
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Chứng minh Phantom Read thực sự xảy ra khi KHÔNG có lock:
     * <ol>
     *   <li>T1 (Cashier): đọc COUNT pending items → 0  (đọc lần 1)</li>
     *   <li>T2 (Waiter) : INSERT 2 món mới + COMMIT  (phantom rows)</li>
     *   <li>T1 (Cashier): đọc COUNT pending items → 2  (đọc lần 2 = Phantom!)</li>
     *   <li>T1 hoàn tất: complete order với thông tin sai → 2 món bị bỏ sót</li>
     * </ol>
     */
    static void demoPhantomReadWithoutFix() throws Exception {
        System.out.println("[ SCENARIO 1 ] Tái hiện lỗi Phantom Read — KHÔNG có lock\n");

        // Setup: đảm bảo order có ít nhất 1 item, tất cả đều DELIVERED
        cleanupTestItems(TEST_ORDER_ID);
        insertDeliveredItems(TEST_ORDER_ID, 2);
        System.out.println("  Setup: đặt order_id=" + TEST_ORDER_ID + " với 2 item DELIVERED\n");

        CountDownLatch cashierRead1Done   = new CountDownLatch(1); // T1 đọc xong lần 1
        CountDownLatch waiterCommitted    = new CountDownLatch(1); // T2 commit xong
        CountDownLatch cashierCompleted   = new CountDownLatch(1); // T1 complete xong

        AtomicInteger cashierCount1 = new AtomicInteger(-1); // đọc lần 1
        AtomicInteger cashierCount2 = new AtomicInteger(-1); // đọc lần 2
        AtomicBoolean completeResult = new AtomicBoolean(false);

        // ── T1: Cashier mở transaction dài, đọc rồi quyết định complete ─────
        Thread t1Cashier = new Thread(() -> {
            try (Connection conn = DBConnection.getInstance().getConnection()) {
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                conn.setAutoCommit(false);

                // Đọc lần 1: kiểm tra không còn PENDING items
                int count1 = countPendingItems(conn, TEST_ORDER_ID);
                cashierCount1.set(count1);
                System.out.println("  [T1-Cashier] Đọc lần 1: " + count1 + " items PENDING/COOKING/READY");
                System.out.println("               → Quyết định: order sẵn sàng thanh toán");
                System.out.println("               (chưa FOR UPDATE → order row KHÔNG bị lock)\n");

                cashierRead1Done.countDown(); // báo T2 có thể commit
                waiterCommitted.await();      // chờ T2 commit phantom rows

                // Đọc lần 2 (cùng transaction): có thể thấy phantom rows rồi
                int count2 = countPendingItems(conn, TEST_ORDER_ID);
                cashierCount2.set(count2);
                System.out.println("  [T1-Cashier] Đọc lần 2: " + count2 + " items PENDING/COOKING/READY");
                if (count2 > count1) {
                    System.out.println("               ✗ PHANTOM ROWS xuất hiện! (" + (count2 - count1) + " món mới)");
                }

                // Thực tế trong luồng thanh toán thực: T1 đã quyết định dựa trên count1=0
                // và gọi completeOrder — không kiểm tra lại
                updateOrderToCompleted(conn, TEST_ORDER_ID);
                conn.commit();
                completeResult.set(true);
                System.out.println("  [T1-Cashier] COMMIT — order COMPLETED\n");

            } catch (Exception e) {
                System.err.println("  [T1] Lỗi: " + e.getMessage());
                cashierRead1Done.countDown();
            } finally {
                cashierCompleted.countDown();
            }
        }, "T1-Cashier-NoFix");

        // ── T2: Waiter insert phantom rows giữa 2 lần đọc của T1 ───────────
        Thread t2Waiter = new Thread(() -> {
            try {
                cashierRead1Done.await(); // chờ T1 đọc xong lần 1

                // INSERT 2 món mới vào order đang được cashier kiểm tra
                try (Connection conn = DBConnection.getInstance().getConnection()) {
                    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                    conn.setAutoCommit(false);
                    insertPendingItemDirect(conn, TEST_ORDER_ID, TEST_MENU_ITEM_ID, 2);
                    conn.commit();
                    System.out.println("  [T2-Waiter] INSERT 2 món mới (PENDING) + COMMIT ← PHANTOM ROWS tạo ra!");
                }
            } catch (Exception e) {
                System.err.println("  [T2] Lỗi: " + e.getMessage());
            } finally {
                waiterCommitted.countDown();
            }
        }, "T2-Waiter-Insert");

        t1Cashier.start();
        t2Waiter.start();
        t1Cashier.join();
        t2Waiter.join();

        // ── Kiểm tra hậu quả ─────────────────────────────────────────────────
        int orphanedItems = countOrphanedPendingItems(TEST_ORDER_ID);

        System.out.println("  ╔════════════════════════════════════════════════════════════╗");
        boolean phantomOccurred = cashierCount2.get() > cashierCount1.get();
        if (phantomOccurred) {
            System.out.println("  ║ ✗ PHANTOM READ đã xảy ra!                                   ║");
            System.out.printf ("  ║   Lần đọc 1: %d PENDING   Lần đọc 2: %d PENDING               ║%n",
                cashierCount1.get(), cashierCount2.get());
            System.out.println("  ║   Order đã COMPLETED nhưng còn " + orphanedItems + " món PENDING bị bỏ sót! ║");
            System.out.println("  ║   Hậu quả: Khách không trả tiền, bếp không nấu.             ║");
        } else {
            System.out.println("  ║ ⚠ Phantom chưa tái hiện lần này — race window rất nhỏ.      ║");
            System.out.println("  ║   Thử chạy lại một vài lần.                                 ║");
        }
        System.out.println("  ╚════════════════════════════════════════════════════════════╝");

        // Dọn dẹp để chạy scenario tiếp theo
        resetOrderToPending(TEST_ORDER_ID);
        cleanupTestItems(TEST_ORDER_ID);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCENARIO 2 — Đã fix: Redis lock + Oracle FOR UPDATE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Chứng minh fix 2 lớp ngăn Phantom Read hoàn toàn:
     * <ol>
     *   <li>T1 (Cashier): Redis lock + SELECT FOR UPDATE → order row bị lock</li>
     *   <li>T2 (Waiter) : gọi {@code addOrderItems} → Redis lock BUSY → bị từ chối ngay</li>
     *   <li>Nếu Redis down: T2 vào DB → SELECT FOR UPDATE trên orders → BLOCK chờ T1</li>
     *   <li>T1 đọc count → 0, complete trong cùng 1 transaction, COMMIT</li>
     *   <li>T2 (sau khi unblock): phát hiện order đã COMPLETED → trả false</li>
     *   <li>Kết quả: 0 phantom rows, order hoàn thành sạch</li>
     * </ol>
     */
    static void demoPhantomReadFixed() throws Exception {
        System.out.println("[ SCENARIO 2 ] Đã fix — Redis lock + Oracle FOR UPDATE ngăn Phantom\n");

        // Setup
        cleanupTestItems(TEST_ORDER_ID);
        insertDeliveredItems(TEST_ORDER_ID, 2);
        System.out.println("  Setup: đặt order_id=" + TEST_ORDER_ID + " với 2 item DELIVERED\n");

        boolean redisOk = OrderLockService.getInstance().isRedisAvailable();
        System.out.println("  [INFO] Redis: " + (redisOk ? "✓ connected — dùng distributed lock" : "✗ offline — dùng in-memory fallback"));
        System.out.println("  [INFO] Oracle FOR UPDATE: luôn hoạt động (safety-net)\n");

        CountDownLatch cashierLocked   = new CountDownLatch(1); // T1 đã lock
        CountDownLatch cashierDone     = new CountDownLatch(1); // T1 commit xong

        AtomicInteger cashierCount     = new AtomicInteger(-1);
        AtomicBoolean cashierResult    = new AtomicBoolean(false);
        AtomicReference<String> waiterOutcome = new AtomicReference<>();

        // ── T1: Cashier — dùng completeOrderSafe (Redis + FOR UPDATE) ────────
        Thread t1Cashier = new Thread(() -> {
            try {
                System.out.println("  [T1-Cashier] Bắt đầu completeOrderSafe...");
                System.out.println("               → Lấy Redis lock order:lock:" + TEST_ORDER_ID);
                System.out.println("               → SELECT orders FOR UPDATE (Oracle lock)");

                // Chạy completeOrderSafe qua OrderDAO thực
                // Nhưng để demo thấy rõ từng bước, ta chạy thủ công với latch
                OrderLockService orderLock = OrderLockService.getInstance();
                boolean redisLocked = orderLock.tryAcquire(TEST_ORDER_ID);
                System.out.println("               → Redis lock: " + (redisLocked ? "✓ acquired" : "✗ busy"));

                if (!redisLocked) {
                    waiterOutcome.set("Cashier không lấy được Redis lock — thử lại");
                    cashierLocked.countDown();
                    cashierDone.countDown();
                    return;
                }

                try (Connection conn = DBConnection.getInstance().getConnection()) {
                    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                    conn.setAutoCommit(false);

                    // SELECT FOR UPDATE: lock hàng orders
                    try (PreparedStatement lockPs = conn.prepareStatement(
                            "SELECT order_id FROM orders WHERE order_id = ? FOR UPDATE")) {
                        lockPs.setLong(1, Long.parseLong(TEST_ORDER_ID));
                        lockPs.executeQuery();
                        System.out.println("               → Oracle FOR UPDATE: order row đã bị lock ✓");
                    }

                    cashierLocked.countDown(); // báo T2: có thể thử addOrderItems

                    // Giả lập cashier xử lý nghiệp vụ (đọc tổng, in bill, ...)
                    Thread.sleep(600);

                    // Đọc COUNT — an toàn: T2 không thể INSERT vì bị block ở FOR UPDATE
                    int count = countPendingItems(conn, TEST_ORDER_ID);
                    cashierCount.set(count);
                    System.out.println("  [T1-Cashier] COUNT pending items (PHANTOM-FREE): " + count);

                    if (count == 0) {
                        updateOrderToCompleted(conn, TEST_ORDER_ID);
                        conn.commit();
                        cashierResult.set(true);
                        System.out.println("  [T1-Cashier] COMMIT — order COMPLETED ✓  (lock released)");
                    } else {
                        conn.rollback();
                        System.out.println("  [T1-Cashier] ROLLBACK — còn " + count + " món chưa xong");
                    }

                } finally {
                    orderLock.release(TEST_ORDER_ID);
                    System.out.println("  [T1-Cashier] Redis lock released.");
                }

            } catch (Exception e) {
                System.err.println("  [T1] Lỗi: " + e.getMessage());
                cashierLocked.countDown();
            } finally {
                cashierDone.countDown();
            }
        }, "T1-Cashier-Fixed");

        // ── T2: Waiter — gọi addOrderItems thực (đã được fix) ────────────────
        Thread t2Waiter = new Thread(() -> {
            try {
                cashierLocked.await(); // chờ T1 đã lock

                System.out.println("\n  [T2-Waiter] Thử addOrderItems cho orderId=" + TEST_ORDER_ID + "...");
                System.out.println("              (Cashier đang lock — T2 sẽ bị từ chối)");

                // Gọi addOrderItems thực — method đã được fix với Redis + FOR UPDATE
                OrderDAO dao = new OrderDAO();
                List<OrderDAO.CartEntry> entries = List.of(
                    new OrderDAO.CartEntry(TEST_MENU_ITEM_ID, 2, 50000)
                );
                boolean inserted = dao.addOrderItems(TEST_ORDER_ID, entries, 99);

                if (!inserted) {
                    waiterOutcome.set("BLOCKED/REJECTED — không thêm được món (Redis lock busy hoặc order đã đóng)");
                    System.out.println("  [T2-Waiter] addOrderItems trả về false — bị chặn ✓");
                } else {
                    waiterOutcome.set("INSERT THÀNH CÔNG — phantom xảy ra! (fix chưa hoạt động)");
                    System.out.println("  [T2-Waiter] addOrderItems thành công — PHANTOM xảy ra!");
                }

            } catch (Exception e) {
                waiterOutcome.set("ERROR: " + e.getMessage());
                System.err.println("  [T2] Lỗi: " + e.getMessage());
            }
        }, "T2-Waiter-Fixed");

        t1Cashier.start();
        // T2 khởi động sau T1 một chút để T1 lock trước
        Thread.sleep(100);
        t2Waiter.start();

        t1Cashier.join();
        t2Waiter.join();

        // ── Kiểm tra kết quả ─────────────────────────────────────────────────
        int orphanedItems = countOrphanedPendingItems(TEST_ORDER_ID);

        System.out.println();
        System.out.println("  Tóm tắt:");
        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.printf ("  │  T1 Cashier COUNT (phantom-free):  %-5d                      │%n",
            cashierCount.get());
        System.out.printf ("  │  T1 Cashier completeOrderSafe:     %-5s                      │%n",
            cashierResult.get() ? "PASS ✓" : "FAIL ✗");
        System.out.printf ("  │  T2 Waiter addOrderItems:          %-30s │%n",
            waiterOutcome.get() != null ? waiterOutcome.get() : "—");
        System.out.printf ("  │  Phantom items còn sót:            %-5d                      │%n",
            orphanedItems);
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        System.out.println("  ╔════════════════════════════════════════════════════════════╗");
        boolean pass = cashierResult.get() && orphanedItems == 0;
        if (pass) {
            System.out.println("  ║ ✓ PASS: Phantom Read đã được ngăn hoàn toàn.               ║");
            System.out.println("  ║   T1 hoàn thành đơn sạch — 0 phantom items.                ║");
            System.out.println("  ║   T2 bị chặn (Redis) hoặc bị từ chối (order COMPLETED).    ║");
            System.out.println("  ║   Không có ORA-08177 — không cần retry loop.               ║");
        } else {
            System.out.println("  ║ ⚠ Kết quả không như kỳ vọng — kiểm tra lại DB/Redis.       ║");
        }
        System.out.println("  ╚════════════════════════════════════════════════════════════╝\n");

        System.out.println("  Ghi chú — Kiến trúc 2 lớp trong production:");
        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │  Lớp 1 – Redis OrderLockService.tryAcquire(orderId)          │");
        System.out.println("  │    key: order:lock:{orderId}   TTL: 30 giây                  │");
        System.out.println("  │    → Chặn race condition ngay ở tầng Application.            │");
        System.out.println("  │    → Khi Redis down: fallback ConcurrentHashMap tự động.     │");
        System.out.println("  │                                                               │");
        System.out.println("  │  Lớp 2 – Oracle SELECT ... FOR UPDATE trên bảng orders       │");
        System.out.println("  │    • addOrderItems:     lock trước khi INSERT items           │");
        System.out.println("  │    • completeOrderSafe: lock trước khi đọc + complete        │");
        System.out.println("  │    → Safety-net: kể cả khi Redis bị bypass, DB chặn phantom. │");
        System.out.println("  │    → Không throw exception, chỉ BLOCK rồi trả 0/false.       │");
        System.out.println("  │                                                               │");
        System.out.println("  │  Isolation: READ_COMMITTED (giữ nguyên trong DBConnection)   │");
        System.out.println("  │    → KHÔNG dùng SERIALIZABLE (ORA-08177 khi conflict).       │");
        System.out.println("  │    → FOR UPDATE đủ: chỉ lock row cần thiết, nhẹ hơn nhiều.   │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘");

        // Dọn dẹp
        resetOrderToPending(TEST_ORDER_ID);
        cleanupTestItems(TEST_ORDER_ID);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers — setup / teardown / query
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Đếm số order_items còn PENDING/ACCEPTED/COOKING/READY của một order.
     * Dùng trong T1 để kiểm tra điều kiện thanh toán.
     */
    static int countPendingItems(Connection conn, String orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM order_items " +
                "WHERE order_id = ? AND item_status IN ('PENDING','ACCEPTED','COOKING','READY')")) {
            ps.setLong(1, Long.parseLong(orderId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Đếm số order_items PENDING trong đơn đã COMPLETED (phantom items bị bỏ sót).
     * Nếu > 0 → phantom read đã gây hậu quả thực.
     */
    static int countOrphanedPendingItems(String orderId) {
        String sql = "SELECT COUNT(*) FROM order_items oi " +
                     "JOIN orders o ON oi.order_id = o.order_id " +
                     "WHERE oi.order_id = ? AND o.status = 'COMPLETED' " +
                     "AND oi.item_status IN ('PENDING','ACCEPTED','COOKING','READY')";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, Long.parseLong(orderId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            System.err.println("[PhantomReadDemo] countOrphanedPendingItems lỗi: " + e.getMessage());
            return -1;
        }
    }

    /**
     * INSERT order_items với status DELIVERED (đã phục vụ) — trạng thái "sẵn sàng thanh toán".
     */
    static void insertDeliveredItems(String orderId, int count) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status, round_number) " +
                 "VALUES (?, ?, 1, 50000, 'DELIVERED', 0)")) {
            for (int i = 0; i < count; i++) {
                ps.setLong(1, Long.parseLong(orderId));
                ps.setLong(2, Long.parseLong(TEST_MENU_ITEM_ID));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * INSERT order_items với status PENDING trực tiếp (bypass lock) — dùng trong SCENARIO 1
     * để tạo phantom rows mà không bị chặn.
     */
    static void insertPendingItemDirect(Connection conn, String orderId,
                                         String menuItemId, int quantity) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status, round_number) " +
                "VALUES (?, ?, ?, 50000, 'PENDING', 99)")) {
            ps.setLong(1, Long.parseLong(orderId));
            ps.setLong(2, Long.parseLong(menuItemId));
            ps.setInt(3, quantity);
            ps.executeUpdate();
        }
    }

    /**
     * UPDATE order status → COMPLETED (dùng trong demo để giả lập completeOrder cũ — không có lock).
     */
    static void updateOrderToCompleted(Connection conn, String orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET status='COMPLETED', completed_at=SYSTIMESTAMP " +
                "WHERE order_id = ?")) {
            ps.setLong(1, Long.parseLong(orderId));
            ps.executeUpdate();
        }
    }

    /**
     * Reset order về PENDING sau khi chạy demo (dọn dẹp).
     */
    static void resetOrderToPending(String orderId) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE orders SET status='PENDING', completed_at=NULL WHERE order_id=?")) {
            ps.setLong(1, Long.parseLong(orderId));
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[PhantomReadDemo] resetOrderToPending lỗi: " + e.getMessage());
        }
    }

    /**
     * Xóa toàn bộ order_items có round_number=0 hoặc 99 (items do demo tạo).
     * Không xóa items thật của order.
     */
    static void cleanupTestItems(String orderId) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM order_items WHERE order_id=? AND round_number IN (0, 99)")) {
            ps.setLong(1, Long.parseLong(orderId));
            int deleted = ps.executeUpdate();
            if (deleted > 0)
                System.out.println("  [Cleanup] Đã xóa " + deleted + " test items của orderId=" + orderId);
        } catch (Exception e) {
            System.err.println("[PhantomReadDemo] cleanupTestItems lỗi: " + e.getMessage());
        }
    }
}
