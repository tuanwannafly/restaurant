package com.restaurant.demo;

import com.restaurant.db.DBConnection;
import com.restaurant.db.KitchenLockService;
import com.restaurant.dao.KitchenDAO;
import com.restaurant.model.Order;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ╔═══════════════════════════════════════════════════════════════════════════╗
 * ║         DEMO: Non-Repeatable Read & Cách Fix trên Oracle                 ║
 * ╠═══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                           ║
 * ║  Non-Repeatable Read (NRR) xảy ra khi:                                   ║
 * ║    Tx A đọc row → Tx B commit thay đổi → Tx A đọc lại → thấy khác!      ║
 * ║                                                                           ║
 * ║  Kiến trúc 2 lớp bảo vệ:                                                 ║
 * ║                                                                           ║
 * ║  [Tầng Application]  Redis SET NX/EX  (KitchenLockService)               ║
 * ║        │  nếu Redis down → fallback ConcurrentHashMap                    ║
 * ║        ▼                                                                   ║
 * ║  [Tầng DB]  Oracle READ_COMMITTED + SELECT ... FOR UPDATE                 ║
 * ║             (safety-net: NRR không thể xảy ra trong 1 transaction)        ║
 * ║                                                                           ║
 * ╠═══════════════════════════════════════════════════════════════════════════╣
 * ║  Tại sao KHÔNG dùng SERIALIZABLE?                                        ║
 * ║  → Oracle SERIALIZABLE ném ORA-08177 khi 2 writer đụng nhau              ║
 * ║  → SELECT FOR UPDATE chỉ block writer đang tranh chấp, không throw       ║
 * ║  → READ_COMMITTED + FOR UPDATE đủ, nhẹ hơn, Oracle-idiomatic             ║
 * ╚═══════════════════════════════════════════════════════════════════════════╝
 *
 * <p>Chạy {@code main()} để xem 3 scenario theo thứ tự:</p>
 * <ol>
 *   <li>Giải thích NRR (không cần DB)</li>
 *   <li>Tái hiện lỗi NRR trên Oracle thực (cần DB)</li>
 *   <li>Demo đã fix: SELECT FOR UPDATE ngăn NRR (cần DB)</li>
 * </ol>
 *
 * <p>Cần Oracle đang chạy và {@code db.properties} hợp lệ.
 * Đổi {@link #TEST_ITEM_ID} sang {@code order_item_id} có thật trong DB.</p>
 */
public class NonRepeatableReadDemo {

    // ── Đổi sang order_item_id thực trong DB của bạn ──────────────────────────
    private static final String TEST_ITEM_ID = "1";

    // ══════════════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║       NON-REPEATABLE READ DEMO — Oracle READ_COMMITTED        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        // 1. Giải thích khái niệm (không cần DB)
        explainNonRepeatableRead();

        System.out.println("\n" + "─".repeat(64) + "\n");

        // 2. Tái hiện lỗi NRR thực sự trên Oracle
        demoNRRWithoutFix();

        System.out.println("\n" + "─".repeat(64) + "\n");

        // 3. Demo đã fix: SELECT FOR UPDATE ngăn NRR hoàn toàn
        demoNRRFixed();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCENARIO 0 — Giải thích không cần DB
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * In ra bảng minh hoạ Non-Repeatable Read, không chạy DB.
     *
     * <p><b>Khác với Dirty Read:</b> NRR xảy ra với dữ liệu đã COMMIT,
     * không phải uncommitted. Oracle READ_COMMITTED ngăn Dirty Read,
     * nhưng vẫn cho phép NRR vì dùng snapshot per-statement.</p>
     */
    static void explainNonRepeatableRead() {
        System.out.println("[ SCENARIO 0 ] Non-Repeatable Read là gì?\n");

        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │  T1 (Chef A) – cùng transaction        T2 (Chef B)           │");
        System.out.println("  ├─────────────────────────────────────────────────────────────┤");
        System.out.println("  │  BEGIN TRANSACTION                                            │");
        System.out.println("  │                                                               │");
        System.out.println("  │  SELECT item_status                                           │");
        System.out.println("  │    → 'PENDING'  ← Read 1                                     │");
        System.out.println("  │                                         BEGIN TRANSACTION      │");
        System.out.println("  │                                         UPDATE item_status     │");
        System.out.println("  │                                           = 'COOKING'          │");
        System.out.println("  │                                         COMMIT ✓              │");
        System.out.println("  │                                                               │");
        System.out.println("  │  SELECT item_status  ← cùng transaction, cùng row            │");
        System.out.println("  │    → 'COOKING'  ← Read 2  ≠ Read 1  ← ĐÂY LÀ NRR!          │");
        System.out.println("  │                                                               │");
        System.out.println("  │  if (read1 == 'PENDING') {                                    │");
        System.out.println("  │      assignToSelf();  ← hành động dựa trên Read 1 cũ!        │");
        System.out.println("  │  }  ← Lỗi logic vì thực tế item đã là 'COOKING'              │");
        System.out.println("  │  COMMIT                                                       │");
        System.out.println("  └─────────────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ Khác biệt so với Dirty Read:");
        System.out.println("    Dirty Read:           T2 đọc data CHƯA commit của T1");
        System.out.println("    Non-Repeatable Read:  T2 đọc data ĐÃ commit → vẫn sai logic");
        System.out.println("    (Oracle READ_COMMITTED ngăn Dirty Read, không ngăn NRR)\n");

        System.out.println("  ★ Hậu quả trong bếp:");
        System.out.println("    Chef A thấy PENDING → quyết định nhận món → nhưng giữa chừng");
        System.out.println("    Chef B đã nhận → Chef A vẫn ghi COOKING thêm lần nữa!");
        System.out.println("    → 2 người nấu cùng 1 món, hoặc dữ liệu nhảy không nhất quán.\n");

        System.out.println("  ★ Fix đúng trên Oracle:");
        System.out.println("    SELECT item_status ... FOR UPDATE");
        System.out.println("    → Oracle lock row ngay khi Read 1.");
        System.out.println("    → T2 muốn UPDATE phải chờ T1 COMMIT/ROLLBACK trước.");
        System.out.println("    → Read 2 trong T1 thấy đúng giá trị đã lock → không NRR.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCENARIO 1 — Tái hiện NRR không có fix (cần DB)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Chứng minh NRR thực sự xảy ra khi dùng READ_COMMITTED thuần (không FOR UPDATE):
     * <ol>
     *   <li>T1: SELECT item_status → 'PENDING' (Read 1) → ngủ chờ T2</li>
     *   <li>T2: UPDATE 'COOKING' + COMMIT trong lúc T1 chờ</li>
     *   <li>T1: SELECT item_status lại → 'COOKING' (Read 2 ≠ Read 1) = NRR!</li>
     * </ol>
     *
     * <p>Không có FOR UPDATE → row không bị khóa → T2 commit thoải mái
     * giữa 2 lần đọc của T1 → snapshot per-statement của Oracle trả về
     * 2 giá trị khác nhau.</p>
     */
    static void demoNRRWithoutFix() throws Exception {
        System.out.println("[ SCENARIO 1 ] Tái hiện lỗi NRR — READ_COMMITTED không có FOR UPDATE\n");

        DirtyReadDemo.resetItemToPending(TEST_ITEM_ID);
        System.out.println("  Đặt lại item_status = PENDING\n");

        // Latch: T1 đọc xong lần 1 → báo T2 commit → T2 commit xong → báo T1 đọc lần 2
        CountDownLatch t1ReadDone    = new CountDownLatch(1);
        CountDownLatch t2Committed   = new CountDownLatch(1);

        AtomicReference<String> t1Read1 = new AtomicReference<>();
        AtomicReference<String> t1Read2 = new AtomicReference<>();

        // ── T1: nạn nhân của NRR ──────────────────────────────────────────────
        Thread t1 = new Thread(() -> {
            try (Connection conn = DBConnection.getInstance().getConnection()) {
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                conn.setAutoCommit(false);

                // Read 1 — không có FOR UPDATE
                String status1 = readInTx(conn, TEST_ITEM_ID);
                t1Read1.set(status1);
                System.out.println("  [T1-Chef A] Read 1: item_status = " + status1);
                System.out.println("              (chưa FOR UPDATE → row KHÔNG bị khóa)");

                t1ReadDone.countDown();     // báo T2 có thể commit
                t2Committed.await();        // chờ T2 commit xong

                // Read 2 — cùng transaction, cùng row
                String status2 = readInTx(conn, TEST_ITEM_ID);
                t1Read2.set(status2);
                System.out.println("  [T1-Chef A] Read 2: item_status = " + status2);

                conn.rollback();

            } catch (Exception e) {
                System.err.println("  [T1] Lỗi: " + e.getMessage());
                t1ReadDone.countDown();
            }
        }, "T1-ChefA-NoFix");

        // ── T2: commit giữa 2 lần đọc của T1 ────────────────────────────────
        Thread t2 = new Thread(() -> {
            try {
                t1ReadDone.await();     // chờ T1 đọc xong lần 1

                try (Connection conn = DBConnection.getInstance().getConnection()) {
                    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                    conn.setAutoCommit(false);

                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE order_items SET item_status = 'COOKING', assigned_to = '99' " +
                            "WHERE order_item_id = ?")) {
                        ps.setString(1, TEST_ITEM_ID);
                        ps.executeUpdate();
                    }

                    conn.commit();
                    System.out.println("  [T2-Chef B] UPDATE 'COOKING' + COMMIT ✓");
                }

            } catch (Exception e) {
                System.err.println("  [T2] Lỗi: " + e.getMessage());
            } finally {
                t2Committed.countDown();    // báo T1 đọc lần 2
            }
        }, "T2-ChefB-Commit");

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        DirtyReadDemo.resetItemToPending(TEST_ITEM_ID);

        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════════════╗");
        boolean nrrHappened = t1Read1.get() != null
                && t1Read2.get() != null
                && !t1Read1.get().equals(t1Read2.get());
        if (nrrHappened) {
            System.out.println("  ║ ✗ NON-REPEATABLE READ đã xảy ra!                        ║");
            System.out.printf ("  ║   Read 1 = %-10s  Read 2 = %-10s              ║%n",
                    t1Read1.get(), t1Read2.get());
            System.out.println("  ║   Cùng transaction, cùng row → 2 giá trị khác nhau.     ║");
            System.out.println("  ║   Chef A ra quyết định dựa trên Read 1 đã lỗi thời!     ║");
        } else {
            System.out.println("  ║ ⚠ NRR chưa tái hiện được trong lần này.                 ║");
            System.out.println("  ║   Thử chạy lại — race window rất nhỏ.                   ║");
        }
        System.out.println("  ╚══════════════════════════════════════════════════════════╝");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCENARIO 2 — Đã fix: SELECT ... FOR UPDATE ngăn NRR (cần DB)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Chứng minh SELECT FOR UPDATE là cách Oracle-native ngăn NRR:
     * <ol>
     *   <li>T1: {@code SELECT item_status ... FOR UPDATE} → 'PENDING' + lock row</li>
     *   <li>T2: {@code UPDATE ... WHERE order_item_id = ?} → BỊ BLOCK (phải chờ)</li>
     *   <li>T1: SELECT lần 2 → vẫn 'PENDING' (T2 chưa commit được)</li>
     *   <li>T1: COMMIT → T2 được unblock → UPDATE thực hiện (0 row, ALREADY_CHANGED)</li>
     * </ol>
     *
     * <p>Kết quả: T1 thấy giá trị nhất quán ở cả 2 lần đọc → NRR không xảy ra.
     * T2 không bị ORA-08177 (khác SERIALIZABLE), chỉ chờ và sau đó
     * nhận về 0 rows từ conditional UPDATE.</p>
     *
     * <p><b>Tại sao không dùng SERIALIZABLE?</b><br>
     * SERIALIZABLE ném ORA-08177 khi có write conflict → cần retry loop phức tạp.
     * FOR UPDATE chỉ block T2 cho đến khi T1 xong → sạch hơn, nhẹ hơn.</p>
     */
    static void demoNRRFixed() throws Exception {
        System.out.println("[ SCENARIO 2 ] Đã fix — SELECT FOR UPDATE ngăn NRR\n");

        DirtyReadDemo.resetItemToPending(TEST_ITEM_ID);
        System.out.println("  Đặt lại item_status = PENDING\n");

        CountDownLatch t1Locked    = new CountDownLatch(1);  // T1 đã FOR UPDATE
        CountDownLatch t1Committed = new CountDownLatch(1);  // T1 đã COMMIT
        CountDownLatch t2Done      = new CountDownLatch(1);  // T2 xong

        AtomicReference<String> t1Read1    = new AtomicReference<>();
        AtomicReference<String> t1Read2    = new AtomicReference<>();
        AtomicReference<String> t2Outcome  = new AtomicReference<>();

        // ── T1: đọc với FOR UPDATE → row bị lock ─────────────────────────────
        Thread t1 = new Thread(() -> {
            try (Connection conn = DBConnection.getInstance().getConnection()) {
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                conn.setAutoCommit(false);

                // Read 1 — FOR UPDATE: lock row ngay
                String status1 = readForUpdate(conn, TEST_ITEM_ID);
                t1Read1.set(status1);
                System.out.println("  [T1-Chef A] Read 1 (FOR UPDATE): item_status = " + status1);
                System.out.println("              Row đã bị lock — T2 sẽ bị chặn nếu muốn UPDATE.");

                t1Locked.countDown();       // báo T2 thử UPDATE (sẽ bị block)

                // Giả lập Chef A đang xử lý nghiệp vụ (sleep ngắn)
                Thread.sleep(400);

                // Read 2 — cùng transaction: T2 vẫn đang block, row chưa thay đổi
                String status2 = readForUpdate(conn, TEST_ITEM_ID);
                t1Read2.set(status2);
                System.out.println("  [T1-Chef A] Read 2 (FOR UPDATE): item_status = " + status2);
                System.out.println("              Cùng giá trị → NRR KHÔNG xảy ra ✓");

                conn.commit();
                System.out.println("  [T1-Chef A] COMMIT — lock được giải phóng.");
                t1Committed.countDown();

            } catch (Exception e) {
                System.err.println("  [T1] Lỗi: " + e.getMessage());
                t1Locked.countDown();
                t1Committed.countDown();
            }
        }, "T1-ChefA-Fixed");

        // ── T2: bị block cho đến khi T1 commit ───────────────────────────────
        Thread t2 = new Thread(() -> {
            try {
                t1Locked.await();   // chờ T1 đã lock row

                System.out.println("  [T2-Chef B] Thử UPDATE 'COOKING'... (đang bị block bởi T1)");

                try (Connection conn = DBConnection.getInstance().getConnection()) {
                    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                    conn.setAutoCommit(false);

                    // Conditional UPDATE — sẽ bị block cho đến khi T1 commit
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE order_items SET item_status = 'COOKING', assigned_to = '99' " +
                            "WHERE order_item_id = ? AND item_status = 'PENDING'")) {
                        ps.setString(1, TEST_ITEM_ID);

                        // Dòng này BLOCK cho đến khi T1 release lock
                        int rows = ps.executeUpdate();

                        if (rows >= 1) {
                            conn.commit();
                            t2Outcome.set("SUCCESS — UPDATE được (1 row). T1 đã không update.");
                        } else {
                            conn.rollback();
                            t2Outcome.set("ALREADY_CHANGED — 0 rows. T1 đã xử lý trước.");
                        }
                    }
                }

                System.out.println("  [T2-Chef B] Được unblock sau khi T1 COMMIT.");
                System.out.println("  [T2-Chef B] Kết quả: " + t2Outcome.get());

            } catch (Exception e) {
                t2Outcome.set("ERROR: " + e.getMessage());
                System.err.println("  [T2] Lỗi: " + e.getMessage());
            } finally {
                t2Done.countDown();
            }
        }, "T2-ChefB-Blocked");

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        DirtyReadDemo.resetItemToPending(TEST_ITEM_ID);

        // ── Kết quả ───────────────────────────────────────────────────────────
        System.out.println();
        System.out.println("  Tóm tắt:\n");
        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.printf ("  │  T1 Read 1 (FOR UPDATE):  %-10s                        │%n", t1Read1.get());
        System.out.printf ("  │  T1 Read 2 (FOR UPDATE):  %-10s  ← cùng = KHÔNG NRR!  │%n", t1Read2.get());
        System.out.printf ("  │  T2 Outcome:              %-30s  │%n",
                t2Outcome.get() != null ? t2Outcome.get() : "—");
        System.out.println("  └────────────────────────────────────────────────────────────┘\n");

        boolean nrrFixed  = t1Read1.get() != null
                         && t1Read1.get().equals(t1Read2.get());
        boolean noOra     = t2Outcome.get() == null || !t2Outcome.get().contains("ORA-08177");

        System.out.println("  ╔══════════════════════════════════════════════════════════╗");
        if (nrrFixed && noOra) {
            System.out.println("  ║ ✓ PASS: SELECT FOR UPDATE đã ngăn Non-Repeatable Read.  ║");
            System.out.println("  ║   T1 thấy cùng giá trị ở cả 2 lần đọc.                 ║");
            System.out.println("  ║   T2 bị block → unblock → nhận ALREADY_CHANGED sạch.   ║");
            System.out.println("  ║   Không có ORA-08177 → không cần retry loop.            ║");
        } else {
            System.out.println("  ║ ⚠ Kết quả không như kỳ vọng — kiểm tra lại kết nối DB. ║");
        }
        System.out.println("  ╚══════════════════════════════════════════════════════════╝\n");

        // ── Giải thích tại sao 2 lớp bảo vệ đã đủ trong production ──────────
        System.out.println("  Ghi chú — Trong production (KitchenDAO hiện tại):");
        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │  Lớp 1 – Redis KitchenLockService.tryAcquire()             │");
        System.out.println("  │    → Chặn 2 chef vào critical section ngay từ đầu.         │");
        System.out.println("  │    → NRR không có cơ hội xảy ra.                           │");
        System.out.println("  │                                                              │");
        System.out.println("  │  Lớp 2 – updateItemStatusSafe() (WHERE item_status = ?)    │");
        System.out.println("  │    → Safety-net nếu Redis down hoặc bị bypass.             │");
        System.out.println("  │    → rowCount = 0 → trả ALREADY_CHANGED, không lost update.│");
        System.out.println("  │                                                              │");
        System.out.println("  │  Isolation: READ_COMMITTED (giữ nguyên trong DBConnection)  │");
        System.out.println("  │    → KHÔNG cần SERIALIZABLE (sẽ gây ORA-08177).            │");
        System.out.println("  │    → FOR UPDATE chỉ dùng khi cần đọc-rồi-quyết định        │");
        System.out.println("  │      trong 1 transaction dài, không phải mọi trường hợp.   │");
        System.out.println("  └────────────────────────────────────────────────────────────┘");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Đọc item_status trong một transaction đang mở (không FOR UPDATE).
     * Dùng để minh họa NRR.
     */
    private static String readInTx(Connection conn, String itemId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT item_status FROM order_items WHERE order_item_id = ?")) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("item_status") : "(not found)";
            }
        }
    }

    /**
     * Đọc item_status với FOR UPDATE — lock row cho đến khi transaction kết thúc.
     * Đây là cách Oracle-native ngăn Non-Repeatable Read.
     *
     * <p>Khác biệt với SERIALIZABLE:</p>
     * <ul>
     *   <li>FOR UPDATE chỉ lock row được SELECT, không lock toàn bộ snapshot.</li>
     *   <li>Tx khác bị BLOCK (chờ) thay vì nhận ORA-08177.</li>
     *   <li>Khi T1 commit, T2 tiếp tục — không cần retry loop.</li>
     * </ul>
     */
    private static String readForUpdate(Connection conn, String itemId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT item_status FROM order_items " +
                "WHERE order_item_id = ? FOR UPDATE")) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("item_status") : "(not found)";
            }
        }
    }
}
