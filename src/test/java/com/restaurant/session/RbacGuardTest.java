package com.restaurant.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.restaurant.session.Permission.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link RbacGuard} — Phase 5C.
 * <p>
 * Không kết nối DB. AppSession được điều khiển trực tiếp qua
 * {@code login()} / {@code logout()} trong test.
 * Mỗi test độc lập: @BeforeEach reset AppSession về trạng thái logout.
 */
class RbacGuardTest {

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void reset() {
        // Đảm bảo mỗi test bắt đầu từ trạng thái "chưa đăng nhập"
        AppSession session = AppSession.getInstance();
        // Gọi login trước để loggedIn = true rồi mới logout() có tác dụng
        session.login(0L, "", "", "WAITER", 0L);
        session.logout();
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    void login(String role) {
        AppSession.getInstance().login(99L, "Test", "t@t.com", role, 1L);
    }

    boolean can(Permission p) {
        return RbacGuard.getInstance().can(p);
    }

    // ── WAITER ────────────────────────────────────────────────────────────────

    @Test
    void waiterCanViewOrder() {
        login("WAITER");
        assertTrue(can(VIEW_ORDER), "WAITER phải có quyền VIEW_ORDER");
    }

    @Test
    void waiterCannotDeleteOrder() {
        login("WAITER");
        assertFalse(can(DELETE_ORDER), "WAITER không được phép DELETE_ORDER");
    }

    @Test
    void waiterCannotAddEmployee() {
        login("WAITER");
        assertFalse(can(ADD_EMPLOYEE), "WAITER không được phép ADD_EMPLOYEE");
    }

    @Test
    void waiterCannotViewStats() {
        login("WAITER");
        assertFalse(can(VIEW_STATS), "WAITER không được phép VIEW_STATS");
    }

    // ── CHEF ──────────────────────────────────────────────────────────────────

    @Test
    void chefCanViewOrder() {
        login("CHEF");
        assertTrue(can(VIEW_ORDER), "CHEF phải có quyền VIEW_ORDER");
    }

    @Test
    void chefCannotAddEmployee() {
        login("CHEF");
        assertFalse(can(ADD_EMPLOYEE), "CHEF không được phép ADD_EMPLOYEE");
    }

    @Test
    void chefCannotManageRestaurant() {
        login("CHEF");
        assertFalse(can(MANAGE_RESTAURANT), "CHEF không được phép MANAGE_RESTAURANT");
    }

    // ── CASHIER ───────────────────────────────────────────────────────────────

    @Test
    void cashierCanViewStats() {
        login("CASHIER");
        assertTrue(can(VIEW_STATS), "CASHIER phải có quyền VIEW_STATS");
    }

    @Test
    void cashierCannotDeleteOrder() {
        login("CASHIER");
        assertFalse(can(DELETE_ORDER), "CASHIER không được phép DELETE_ORDER");
    }

    @Test
    void cashierCannotAssignRole() {
        login("CASHIER");
        assertFalse(can(ASSIGN_ROLE), "CASHIER không được phép ASSIGN_ROLE");
    }

    // ── RESTAURANT_ADMIN ──────────────────────────────────────────────────────

    @Test
    void adminCanDeleteEmployee() {
        login("RESTAURANT_ADMIN");
        assertTrue(can(DELETE_EMPLOYEE), "RESTAURANT_ADMIN phải có quyền DELETE_EMPLOYEE");
    }

    @Test
    void adminCanAssignRole() {
        login("RESTAURANT_ADMIN");
        assertTrue(can(ASSIGN_ROLE), "RESTAURANT_ADMIN phải có quyền ASSIGN_ROLE");
    }

    @Test
    void adminCannotManageRestaurant() {
        login("RESTAURANT_ADMIN");
        assertFalse(can(MANAGE_RESTAURANT), "RESTAURANT_ADMIN không được phép MANAGE_RESTAURANT");
    }

    // ── SUPER_ADMIN ───────────────────────────────────────────────────────────

    @Test
    void superAdminHasAllPermissions() {
        login("SUPER_ADMIN");
        for (Permission p : Permission.values()) {
            assertTrue(can(p), "SUPER_ADMIN phải có permission: " + p.name());
        }
    }

    // ── NULL / Logged out ─────────────────────────────────────────────────────

    @Test
    void nullRoleDeniesAllPermissions() {
        // Không gọi login() — session đang ở trạng thái logout từ @BeforeEach
        for (Permission p : Permission.values()) {
            assertFalse(can(p), "Không có session phải deny: " + p.name());
        }
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void addSessionListenerNullDoesNotThrow() {
        assertDoesNotThrow(
            () -> AppSession.getInstance().addSessionListener(null),
            "addSessionListener(null) phải bỏ qua, không throw exception"
        );
    }

    @Test
    void doubleLogoutDoesNotCrash() {
        login("WAITER");
        assertDoesNotThrow(() -> {
            AppSession.getInstance().logout();
            AppSession.getInstance().logout(); // lần 2 phải an toàn
        }, "logout() gọi 2 lần liên tiếp phải không crash");
    }

    @Test
    void doubleLogoutNotifiesOnlyOnce() {
        login("WAITER");
        int[] count = {0};
        AppSession.getInstance().addSessionListener(() -> count[0]++);

        AppSession.getInstance().logout();
        AppSession.getInstance().logout(); // lần 2 không trigger listener

        assertEquals(1, count[0], "Listener chỉ được notify đúng 1 lần dù logout() gọi 2 lần");
    }

    @Test
    void canReturnsFalseForNullPermission() {
        login("SUPER_ADMIN");
        assertFalse(
            RbacGuard.getInstance().can(null),
            "can(null) phải trả về false, không throw NullPointerException"
        );
    }
}
