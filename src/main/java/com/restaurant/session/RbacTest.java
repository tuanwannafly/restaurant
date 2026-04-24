package com.restaurant.session;

/**
 * Test thủ công cho Phase 1 RBAC.
 * Chạy: javac + java, không cần JUnit hay Maven.
 *
 *   cd src/main/java
 *   javac com/restaurant/session/*.java com/restaurant/session/RbacTest.java
 *   java  com.restaurant.session.RbacTest
 */
public class RbacTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  SmartRestaurant — RBAC Phase 1 Test  ");
        System.out.println("========================================\n");

        testWaiter();
        testChef();
        testCashier();
        testRestaurantAdmin();
        testSuperAdmin();
        testEdgeCasesNullRole();
        testHelperMethods();
        testAppSessionHasPermission();

        System.out.println("\n========================================");
        System.out.printf("  KẾT QUẢ: %d passed, %d failed%n", passed, failed);
        System.out.println("========================================");

        if (failed > 0) System.exit(1);
    }

    // ── Test cases ────────────────────────────────────────────────────────────

    static void testWaiter() {
        header("WAITER");
        AppSession.getInstance().login(1L, "Nguyen Van A", "a@b.com", "WAITER", 5L);

        ok("VIEW_ORDER",      RbacGuard.getInstance().can(Permission.VIEW_ORDER));
        ok("ADD_ORDER",       RbacGuard.getInstance().can(Permission.ADD_ORDER));
        ok("UPDATE_ORDER_STATUS", RbacGuard.getInstance().can(Permission.UPDATE_ORDER_STATUS));
        ok("VIEW_TABLE",      RbacGuard.getInstance().can(Permission.VIEW_TABLE));
        ok("VIEW_MENU",       RbacGuard.getInstance().can(Permission.VIEW_MENU));
        ok("ADD_REPORT",      RbacGuard.getInstance().can(Permission.ADD_REPORT));

        no("DELETE_ORDER",    RbacGuard.getInstance().can(Permission.DELETE_ORDER));
        no("DELETE_EMPLOYEE", RbacGuard.getInstance().can(Permission.DELETE_EMPLOYEE));
        no("ADD_EMPLOYEE",    RbacGuard.getInstance().can(Permission.ADD_EMPLOYEE));
        no("VIEW_STATS",      RbacGuard.getInstance().can(Permission.VIEW_STATS));
        no("MANAGE_RESTAURANT", RbacGuard.getInstance().can(Permission.MANAGE_RESTAURANT));
        no("ASSIGN_ROLE",     RbacGuard.getInstance().can(Permission.ASSIGN_ROLE));

        no("isManagerOrAbove",  RbacGuard.getInstance().isManagerOrAbove());
        ok("isStaff",           RbacGuard.getInstance().isStaff());
    }

    static void testChef() {
        header("CHEF");
        AppSession.getInstance().login(2L, "Tran B", "b@b.com", "CHEF", 5L);

        ok("VIEW_ORDER",          RbacGuard.getInstance().can(Permission.VIEW_ORDER));
        ok("UPDATE_ORDER_STATUS", RbacGuard.getInstance().can(Permission.UPDATE_ORDER_STATUS));
        ok("VIEW_MENU",           RbacGuard.getInstance().can(Permission.VIEW_MENU));
        ok("ADD_REPORT",          RbacGuard.getInstance().can(Permission.ADD_REPORT));

        no("ADD_ORDER",       RbacGuard.getInstance().can(Permission.ADD_ORDER));
        no("DELETE_ORDER",    RbacGuard.getInstance().can(Permission.DELETE_ORDER));
        no("VIEW_TABLE",      RbacGuard.getInstance().can(Permission.VIEW_TABLE));
        no("VIEW_STATS",      RbacGuard.getInstance().can(Permission.VIEW_STATS));
        no("isManagerOrAbove",  RbacGuard.getInstance().isManagerOrAbove());
        ok("isStaff",           RbacGuard.getInstance().isStaff());
    }

    static void testCashier() {
        header("CASHIER");
        AppSession.getInstance().login(3L, "Le C", "c@b.com", "CASHIER", 5L);

        ok("VIEW_ORDER",          RbacGuard.getInstance().can(Permission.VIEW_ORDER));
        ok("UPDATE_ORDER_STATUS", RbacGuard.getInstance().can(Permission.UPDATE_ORDER_STATUS));
        ok("VIEW_TABLE",          RbacGuard.getInstance().can(Permission.VIEW_TABLE));
        ok("EDIT_TABLE",          RbacGuard.getInstance().can(Permission.EDIT_TABLE));
        ok("VIEW_STATS",          RbacGuard.getInstance().can(Permission.VIEW_STATS));
        ok("ADD_REPORT",          RbacGuard.getInstance().can(Permission.ADD_REPORT));

        no("ADD_ORDER",       RbacGuard.getInstance().can(Permission.ADD_ORDER));
        no("DELETE_ORDER",    RbacGuard.getInstance().can(Permission.DELETE_ORDER));
        no("VIEW_EMPLOYEE",   RbacGuard.getInstance().can(Permission.VIEW_EMPLOYEE));
        no("MANAGE_RESTAURANT", RbacGuard.getInstance().can(Permission.MANAGE_RESTAURANT));
        no("isManagerOrAbove",  RbacGuard.getInstance().isManagerOrAbove());
        ok("isStaff",           RbacGuard.getInstance().isStaff());
    }

    static void testRestaurantAdmin() {
        header("RESTAURANT_ADMIN");
        AppSession.getInstance().login(4L, "Admin", "admin@b.com", "RESTAURANT_ADMIN", 5L);

        ok("DELETE_EMPLOYEE",     RbacGuard.getInstance().can(Permission.DELETE_EMPLOYEE));
        ok("ADD_EMPLOYEE",        RbacGuard.getInstance().can(Permission.ADD_EMPLOYEE));
        ok("DELETE_ORDER",        RbacGuard.getInstance().can(Permission.DELETE_ORDER));
        ok("ADD_MENU",            RbacGuard.getInstance().can(Permission.ADD_MENU));
        ok("DELETE_MENU",         RbacGuard.getInstance().can(Permission.DELETE_MENU));
        ok("VIEW_STATS",          RbacGuard.getInstance().can(Permission.VIEW_STATS));
        ok("VIEW_REPORT",         RbacGuard.getInstance().can(Permission.VIEW_REPORT));

        no("MANAGE_RESTAURANT",   RbacGuard.getInstance().can(Permission.MANAGE_RESTAURANT));
        no("ASSIGN_ROLE",         RbacGuard.getInstance().can(Permission.ASSIGN_ROLE));

        ok("isManagerOrAbove",    RbacGuard.getInstance().isManagerOrAbove());
        ok("isRestaurantAdmin",   RbacGuard.getInstance().isRestaurantAdmin());
        no("isSuperAdmin",        RbacGuard.getInstance().isSuperAdmin());
        no("isStaff",             RbacGuard.getInstance().isStaff());
    }

    static void testSuperAdmin() {
        header("SUPER_ADMIN");
        AppSession.getInstance().login(5L, "Root", "root@b.com", "SUPER_ADMIN", 0L);

        ok("MANAGE_RESTAURANT",   RbacGuard.getInstance().can(Permission.MANAGE_RESTAURANT));
        ok("ASSIGN_ROLE",         RbacGuard.getInstance().can(Permission.ASSIGN_ROLE));
        ok("DELETE_EMPLOYEE",     RbacGuard.getInstance().can(Permission.DELETE_EMPLOYEE));
        ok("DELETE_ORDER",        RbacGuard.getInstance().can(Permission.DELETE_ORDER));
        ok("VIEW_STATS",          RbacGuard.getInstance().can(Permission.VIEW_STATS));

        ok("isSuperAdmin",        RbacGuard.getInstance().isSuperAdmin());
        ok("isManagerOrAbove",    RbacGuard.getInstance().isManagerOrAbove());
        no("isStaff",             RbacGuard.getInstance().isStaff());
    }

    static void testEdgeCasesNullRole() {
        header("EDGE CASE — sau logout (role = null)");
        AppSession.getInstance().logout();

        no("VIEW_ORDER after logout",  RbacGuard.getInstance().can(Permission.VIEW_ORDER));
        no("ADD_ORDER after logout",   RbacGuard.getInstance().can(Permission.ADD_ORDER));
        no("VIEW_STATS after logout",  RbacGuard.getInstance().can(Permission.VIEW_STATS));
        no("isManagerOrAbove",         RbacGuard.getInstance().isManagerOrAbove());
        no("isStaff",                  RbacGuard.getInstance().isStaff());
        no("isSuperAdmin",             RbacGuard.getInstance().isSuperAdmin());
        no("can(null)",                RbacGuard.getInstance().can(null));

        header("EDGE CASE — role không hợp lệ");
        AppSession.getInstance().login(99L, "Ghost", "g@b.com", "UNKNOWN_ROLE", 1L);
        no("VIEW_ORDER (unknown role)", RbacGuard.getInstance().can(Permission.VIEW_ORDER));
        no("ADD_REPORT (unknown role)", RbacGuard.getInstance().can(Permission.ADD_REPORT));
    }

    static void testHelperMethods() {
        header("AppSession — helper methods (refactored)");

        AppSession.getInstance().login(1L, "A", "a@x.com", "WAITER", 1L);
        no("isAdmin (WAITER)",   AppSession.getInstance().isAdmin());
        no("isManager (WAITER)", AppSession.getInstance().isManager());
        no("isCashier (WAITER)", AppSession.getInstance().isCashier());

        AppSession.getInstance().login(2L, "B", "b@x.com", "CASHIER", 1L);
        ok("isCashier (CASHIER)", AppSession.getInstance().isCashier());
        no("isAdmin (CASHIER)",   AppSession.getInstance().isAdmin());

        AppSession.getInstance().login(3L, "C", "c@x.com", "RESTAURANT_ADMIN", 1L);
        ok("isAdmin (RESTAURANT_ADMIN)",   AppSession.getInstance().isAdmin());
        ok("isManager (RESTAURANT_ADMIN)", AppSession.getInstance().isManager());
        ok("isCashier (RESTAURANT_ADMIN)", AppSession.getInstance().isCashier());
    }

    static void testAppSessionHasPermission() {
        header("AppSession#hasPermission()");
        AppSession.getInstance().login(1L, "X", "x@x.com", "WAITER", 1L);
        ok("hasPermission(VIEW_ORDER)",    AppSession.getInstance().hasPermission(Permission.VIEW_ORDER));
        no("hasPermission(DELETE_ORDER)",  AppSession.getInstance().hasPermission(Permission.DELETE_ORDER));
    }

    // ── Assertion helpers ─────────────────────────────────────────────────────

    static void header(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    /** Assert condition == true */
    static void ok(String label, boolean condition) {
        if (condition) {
            System.out.printf("  [PASS] %-36s → true%n", label);
            passed++;
        } else {
            System.out.printf("  [FAIL] %-36s → expected true, got false%n", label);
            failed++;
        }
    }

    /** Assert condition == false */
    static void no(String label, boolean condition) {
        if (!condition) {
            System.out.printf("  [PASS] %-36s → false%n", label);
            passed++;
        } else {
            System.out.printf("  [FAIL] %-36s → expected false, got true%n", label);
            failed++;
        }
    }
}