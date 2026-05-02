package com.restaurant.integration;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.KitchenDAO.KitchenTicket;
import com.restaurant.dao.MenuItemDAO;
import com.restaurant.dao.OrderDAO;
import com.restaurant.dao.OrderDAO.CartEntry;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.MenuItem;
import com.restaurant.model.Order;
import com.restaurant.model.Order.OrderItem.ItemStatus;
import com.restaurant.model.TableItem;
import com.restaurant.session.AppSession;
import com.restaurant.ui.PollManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Integration test — Phase 7D
 *
 * <p>Mô phỏng luồng đầy đủ của một ca phục vụ:
 * <ol>
 *   <li>Thu ngân (CASHIER) đăng nhập và mở bàn</li>
 *   <li>Khách gọi món → {@link OrderDAO#addOrderItems(String, List, int)}</li>
 *   <li>Bếp (CHEF) thấy ticket, chuyển PENDING → ACCEPTED → COOKING → READY</li>
 *   <li>Phục vụ (WAITER) giao món READY → DELIVERING → DELIVERED</li>
 *   <li>Thu ngân thanh toán → order COMPLETED</li>
 *   <li>Verify PollManager sạch sau logout</li>
 * </ol>
 */
public class FullOrderFlowIntegrationTest {

    // ─── Test fixtures ────────────────────────────────────────────────────────

    private static final long   TEST_RESTAURANT_ID = 1L;
    private static final long   TEST_USER_ID       = 1L;
    private static final String TEST_TABLE_ID      = "1";     // dùng numeric ID khớp DB seed
    private static final String TEST_TABLE_NAME    = "Bàn Test 01";

    private OrderDAO   orderDAO;
    private KitchenDAO kitchenDAO;
    private TableDAO   tableDAO;

    private String orderId;   // set ở bước mở bàn, dùng xuyên suốt các step

    // ─── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    public void setUp() throws Exception {
        orderDAO   = new OrderDAO();
        kitchenDAO = new KitchenDAO();
        tableDAO   = new TableDAO();

        // Simulate CASHIER login
        AppSession.getInstance().login(
            TEST_USER_ID, "Test Cashier", "cashier@test.com",
            "CASHIER", TEST_RESTAURANT_ID
        );
    }

    @After
    public void tearDown() throws Exception {
        // Dừng timer và logout — giống flow thực khi user đăng xuất
        runOnEDTAndWait(() -> PollManager.getInstance().stopAll());
        AppSession.getInstance().logout();

        // Verify PollManager sạch
        runOnEDTAndWait(() ->
            assertEquals("PollManager phải rỗng sau stopAll()", 0,
                PollManager.getInstance().activeCount())
        );
    }

    // ─── Full flow test ───────────────────────────────────────────────────────

    /**
     * STEP 1 — Cashier mở bàn.
     */
    @Test
    public void testStep1_CashierOpensTable() {
        System.out.println("\n=== STEP 1: Cashier mở bàn ===");

        orderId = orderDAO.openTable(TEST_TABLE_ID, TEST_RESTAURANT_ID);

        assertNotNull("orderId phải có giá trị sau khi mở bàn", orderId);
        assertFalse("orderId không được rỗng", orderId.isBlank());

        Order order = orderDAO.findById(orderId);
        assertNotNull("Order vừa tạo phải tìm được", order);
        // openTable tạo PENDING — đây là trạng thái "bàn vừa mở"
        assertEquals("Status ban đầu phải là PENDING",
            Order.Status.PENDING, order.getStatus());

        System.out.printf("  ✓ Đã mở bàn '%s', orderId='%s'%n", TEST_TABLE_NAME, orderId);
    }

    /**
     * STEP 2 — Khách gọi món: thêm items vào order.
     */
    @Test
    public void testStep2_GuestOrdersFood() {
        testStep1_CashierOpensTable();
        System.out.println("\n=== STEP 2: Khách gọi món ===");

        List<MenuItem> menu = new MenuItemDAO().getAll(TEST_RESTAURANT_ID);
        assertFalse("Menu phải có ít nhất 1 món", menu.isEmpty());

        MenuItem item1 = menu.get(0);
        int      qty1  = 2;

        // Thêm món vào order (round 1) – dùng CartEntry
        boolean added = orderDAO.addOrderItems(orderId, List.of(
            new CartEntry(item1.getId(), qty1, item1.getPrice())
        ), 1);
        assertTrue("addOrderItems phải trả về true", added);

        // Verify items đã lưu với trạng thái PENDING
        List<Order.OrderItem> items = orderDAO.getItemsWithStatus(orderId);
        assertFalse("Phải có order item sau khi gọi món", items.isEmpty());
        assertTrue("Mọi item vừa gọi phải ở trạng thái PENDING",
            items.stream().allMatch(it -> it.getStatus() == ItemStatus.PENDING));

        System.out.printf("  ✓ Khách gọi %dx '%s' — %d item(s) PENDING%n",
            qty1, item1.getName(), items.size());
    }

    /**
     * STEP 3 — Bếp tiếp nhận và nấu: PENDING → ACCEPTED → COOKING → READY.
     */
    @Test
    public void testStep3_KitchenCooks() {
        testStep2_GuestOrdersFood();
        System.out.println("\n=== STEP 3: Bếp nấu ===");

        AppSession.getInstance().login(
            2L, "Chef Test", "chef@test.com", "CHEF", TEST_RESTAURANT_ID
        );

        List<KitchenTicket> tickets = kitchenDAO.getActiveTickets(TEST_RESTAURANT_ID);
        assertFalse("KitchenPanel phải thấy ticket mới", tickets.isEmpty());

        KitchenTicket ticket = tickets.get(0);

        // Dùng accessor method (Phase 7D)
        kitchenDAO.updateItemStatus(ticket.getOrderItemId(), ItemStatus.ACCEPTED);
        assertStatusEquals(ticket.getOrderItemId(), ItemStatus.ACCEPTED, "sau khi bếp tiếp nhận");

        kitchenDAO.updateItemStatus(ticket.getOrderItemId(), ItemStatus.COOKING);
        assertStatusEquals(ticket.getOrderItemId(), ItemStatus.COOKING, "khi đang nấu");

        kitchenDAO.updateItemStatus(ticket.getOrderItemId(), ItemStatus.READY);
        assertStatusEquals(ticket.getOrderItemId(), ItemStatus.READY, "sau khi nấu xong");

        System.out.printf("  ✓ Bếp đã nấu xong item '%s'%n", ticket.getItemName());
    }

    /**
     * STEP 4 — Phục vụ giao món: READY → DELIVERING → DELIVERED.
     */
    @Test
    public void testStep4_WaiterDelivers() {
        testStep3_KitchenCooks();
        System.out.println("\n=== STEP 4: Phục vụ giao món ===");

        AppSession.getInstance().login(
            3L, "Waiter Test", "waiter@test.com", "WAITER", TEST_RESTAURANT_ID
        );

        Map<String, List<KitchenTicket>> readyMap =
            kitchenDAO.getReadyByTable(TEST_RESTAURANT_ID);
        assertFalse("WaiterServicePanel phải thấy bàn có món READY", readyMap.isEmpty());

        for (List<KitchenTicket> tableTickets : readyMap.values()) {
            for (KitchenTicket t : tableTickets) {
                kitchenDAO.updateItemStatus(t.getOrderItemId(), ItemStatus.DELIVERING);
                assertStatusEquals(t.getOrderItemId(), ItemStatus.DELIVERING, "khi đang giao");

                kitchenDAO.updateItemStatus(t.getOrderItemId(), ItemStatus.DELIVERED);
                assertStatusEquals(t.getOrderItemId(), ItemStatus.DELIVERED, "sau khi giao xong");
            }
        }

        System.out.println("  ✓ Phục vụ đã giao toàn bộ món");
    }

    /**
     * STEP 5 — Cashier thanh toán: Order chuyển sang COMPLETED.
     */
    @Test
    public void testStep5_CashierCheckout() {
        testStep4_WaiterDelivers();
        System.out.println("\n=== STEP 5: Thu ngân thanh toán ===");

        AppSession.getInstance().login(
            TEST_USER_ID, "Test Cashier", "cashier@test.com",
            "CASHIER", TEST_RESTAURANT_ID
        );

        // Đóng đơn với status COMPLETED (tương đương PAID trong business logic)
        boolean closed = orderDAO.closeOrder(orderId, Order.Status.COMPLETED);
        assertTrue("closeOrder phải thành công", closed);

        Order completed = orderDAO.findById(orderId);
        assertNotNull("Order sau thanh toán vẫn phải tìm được", completed);
        assertEquals("Order phải chuyển sang COMPLETED",
            Order.Status.COMPLETED, completed.getStatus());

        // Verify bàn đã được giải phóng (nếu TableDAO hỗ trợ)
        TableItem table = tableDAO.findById(TEST_TABLE_ID, TEST_RESTAURANT_ID);
        if (table != null) {
            assertNotEquals("Bàn phải rời trạng thái OCCUPIED sau thanh toán",
                TableItem.Status.OCCUPIED, table.getStatus());
        }

        System.out.printf("  ✓ Thanh toán hoàn tất — orderId='%s' → COMPLETED%n", orderId);
    }

    /**
     * STEP 6 — PollManager cleanup: verify không còn orphan timer sau logout.
     */
    @Test
    public void testStep6_PollManagerCleanupOnLogout() throws Exception {
        System.out.println("\n=== STEP 6: PollManager cleanup khi logout ===");

        runOnEDTAndWait(() -> {
            PollManager pm = PollManager.getInstance();
            pm.register("kitchen",        () -> {}, 5000);
            pm.register("waiter",         () -> {}, 5000);
            pm.register("tableorder_t01", () -> {}, 5000);
            assertEquals("Phải có 3 timer đang chạy", 3, pm.activeCount());
        });

        // Simulate logout → MainFrame.onLogout() gọi stopAll()
        runOnEDTAndWait(() -> PollManager.getInstance().stopAll());

        runOnEDTAndWait(() -> {
            PollManager pm = PollManager.getInstance();
            assertEquals("Sau stopAll() phải không còn timer nào", 0, pm.activeCount());
            assertFalse("kitchen timer phải dừng",      pm.isRunning("kitchen"));
            assertFalse("waiter timer phải dừng",       pm.isRunning("waiter"));
            assertFalse("tableorder timer phải dừng",   pm.isRunning("tableorder_t01"));
        });

        System.out.println("  ✓ PollManager sạch — không có orphan timer");
    }

    /**
     * STEP 7 — Re-register sau logout không ảnh hưởng phiên mới.
     */
    @Test
    public void testStep7_ReRegisterAfterLogout() throws Exception {
        System.out.println("\n=== STEP 7: Re-register timer sau khi login lại ===");

        // Phiên 1
        runOnEDTAndWait(() -> {
            PollManager.getInstance().register("kitchen", () -> {}, 5000);
            assertTrue(PollManager.getInstance().isRunning("kitchen"));
        });

        // Logout → stopAll
        runOnEDTAndWait(() -> PollManager.getInstance().stopAll());

        // Đăng nhập lại
        AppSession.getInstance().login(
            TEST_USER_ID, "Test Cashier", "cashier@test.com",
            "CASHIER", TEST_RESTAURANT_ID
        );

        // Phiên 2 — register lại không gây exception
        runOnEDTAndWait(() -> {
            PollManager.getInstance().register("kitchen", () -> {}, 5000);
            assertTrue("Timer mới phải chạy sau khi login lại",
                PollManager.getInstance().isRunning("kitchen"));
        });

        System.out.println("  ✓ Re-register sau logout hoạt động đúng");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Chạy {@code action} trên EDT và block cho đến khi hoàn thành. */
    private static void runOnEDTAndWait(Runnable action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            CountDownLatch latch = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                try { action.run(); } finally { latch.countDown(); }
            });
            assertTrue("EDT task timed out", latch.await(5, TimeUnit.SECONDS));
        }
    }

    /**
     * Verify trạng thái của một order_item theo ID.
     * Dùng {@link OrderDAO#getItemStatus(String)} — không gọi trực tiếp field/method
     * trên KitchenTicket để tránh coupling với DB state cũ.
     */
    private void assertStatusEquals(String orderItemId,
                                    ItemStatus expected, String context) {
        ItemStatus actual = orderDAO.getItemStatus(orderItemId);
        assertEquals(
            String.format("Item '%s' %s: expected %s but was %s",
                orderItemId, context, expected, actual),
            expected, actual
        );
    }
}