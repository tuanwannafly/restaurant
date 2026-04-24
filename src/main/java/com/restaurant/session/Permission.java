package com.restaurant.session;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Enum liệt kê toàn bộ actions trong hệ thống SmartRestaurant.
 * <p>
 * Sử dụng {@link #forRole(String)} để lấy tập quyền tương ứng với một role.
 * Mọi Set trả về đều là unmodifiable để đảm bảo immutability.
 */
public enum Permission {

    // ── Employee ──────────────────────────────────────────────────────────────
    VIEW_EMPLOYEE,
    ADD_EMPLOYEE,
    EDIT_EMPLOYEE,
    DELETE_EMPLOYEE,

    // ── Order ─────────────────────────────────────────────────────────────────
    VIEW_ORDER,
    ADD_ORDER,
    UPDATE_ORDER_STATUS,
    DELETE_ORDER,

    // ── Menu ──────────────────────────────────────────────────────────────────
    VIEW_MENU,
    ADD_MENU,
    EDIT_MENU,
    DELETE_MENU,

    // ── Table ─────────────────────────────────────────────────────────────────
    VIEW_TABLE,
    ADD_TABLE,
    EDIT_TABLE,
    DELETE_TABLE,

    // ── Stats / Reports ───────────────────────────────────────────────────────
    VIEW_STATS,
    VIEW_REPORT,
    ADD_REPORT,

    // ── System-level ──────────────────────────────────────────────────────────
    MANAGE_RESTAURANT,
    ASSIGN_ROLE,

    // ── Phase 1 additions ─────────────────────────────────────────────────────

    /**
     * Cho phép mở bàn (chuyển trạng thái bàn từ RANH/DIRTY sang BAN).
     * Cần thiết cho luồng waiter / cashier khi nhận khách.
     */
    OPEN_TABLE,

    /**
     * Cho phép cập nhật trạng thái từng món trong đơn hàng
     * (OrderItem.ItemStatus: PENDING → ACCEPTED → COOKING → READY → DELIVERING → DELIVERED).
     */
    UPDATE_ITEM_STATUS,

    /**
     * Cho phép truy cập màn hình Kitchen (bếp) – hiển thị danh sách
     * món đang chờ nấu và cần phục vụ.
     */
    VIEW_KITCHEN,

    /**
     * Cho phép truy cập màn hình Waiter Service – phục vụ bàn,
     * xem danh sách bàn cần phục vụ và giao món.
     */
    VIEW_WAITER_SERVICE;

    // ── Role → Permission mapping ─────────────────────────────────────────────

    private static final Set<Permission> SUPER_ADMIN_PERMS =
            Collections.unmodifiableSet(EnumSet.allOf(Permission.class));

    private static final Set<Permission> RESTAURANT_ADMIN_PERMS =
            Collections.unmodifiableSet(EnumSet.of(
                    VIEW_EMPLOYEE,  ADD_EMPLOYEE,    EDIT_EMPLOYEE,       DELETE_EMPLOYEE,
                    VIEW_ORDER,     ADD_ORDER,        UPDATE_ORDER_STATUS, DELETE_ORDER,
                    VIEW_MENU,      ADD_MENU,         EDIT_MENU,           DELETE_MENU,
                    VIEW_TABLE,     ADD_TABLE,        EDIT_TABLE,          DELETE_TABLE,
                    VIEW_STATS,     VIEW_REPORT,      ADD_REPORT,
                    // Phase 1
                    OPEN_TABLE, UPDATE_ITEM_STATUS, VIEW_KITCHEN, VIEW_WAITER_SERVICE
                    // MANAGE_RESTAURANT và ASSIGN_ROLE bị loại trừ theo đặc tả
            ));

    private static final Set<Permission> WAITER_PERMS =
            Collections.unmodifiableSet(EnumSet.of(
                    VIEW_ORDER,  ADD_ORDER, UPDATE_ORDER_STATUS,
                    VIEW_TABLE,
                    VIEW_MENU,
                    ADD_REPORT,
                    // Phase 1
                    VIEW_WAITER_SERVICE, UPDATE_ITEM_STATUS
            ));

    private static final Set<Permission> CHEF_PERMS =
            Collections.unmodifiableSet(EnumSet.of(
                    VIEW_ORDER, UPDATE_ORDER_STATUS,
                    VIEW_MENU,
                    ADD_REPORT,
                    // Phase 1
                    VIEW_KITCHEN, UPDATE_ITEM_STATUS
            ));

    private static final Set<Permission> CASHIER_PERMS =
            Collections.unmodifiableSet(EnumSet.of(
                    VIEW_ORDER, UPDATE_ORDER_STATUS,
                    VIEW_TABLE, EDIT_TABLE,
                    VIEW_STATS,
                    ADD_REPORT,
                    // Phase 1
                    OPEN_TABLE
            ));

    private static final Set<Permission> EMPTY_PERMS =
            Collections.unmodifiableSet(EnumSet.noneOf(Permission.class));

    /**
     * Trả về tập quyền (unmodifiable) tương ứng với {@code roleName}.
     * <p>
     * Nếu {@code roleName} null hoặc không nhận ra → trả về empty set (deny all).
     *
     * @param roleName tên role lưu trong DB / AppSession (case-insensitive)
     * @return unmodifiable {@link Set} of {@link Permission}
     */
    public static Set<Permission> forRole(String roleName) {
        if (roleName == null) return EMPTY_PERMS;

        return switch (roleName.toUpperCase()) {
            case "SUPER_ADMIN"                      -> SUPER_ADMIN_PERMS;
            case "ADMIN", "RESTAURANT_ADMIN",
                 "QUAN_LY"                          -> RESTAURANT_ADMIN_PERMS;
            case "WAITER", "PHUC_VU"                -> WAITER_PERMS;
            case "CHEF",   "DAU_BEP"                -> CHEF_PERMS;
            case "CASHIER", "THU_NGAN"              -> CASHIER_PERMS;
            default                                 -> EMPTY_PERMS;
        };
    }
}