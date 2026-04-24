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
    VIEW_WAITER_SERVICE,

    // ── Phase 2 additions ─────────────────────────────────────────────────────

    /**
     * Cho phép RESTAURANT_ADMIN tạo tài khoản đăng nhập cho staff của nhà hàng mình.
     * <p>
     * Tách riêng khỏi {@code ADD_EMPLOYEE} vì tạo <em>tài khoản hệ thống</em> (credentials +
     * role assignment) là hành động nhạy cảm hơn việc thêm thông tin nhân viên vào danh sách.
     * RESTAURANT_ADMIN chỉ được tạo tài khoản cho nhà hàng của chính mình;
     * không thể cấp role vượt quá quyền hạn hiện tại (không thể tạo SUPER_ADMIN).
     */
    REGISTER_STAFF,

    /**
     * Cho phép mọi user đã đăng nhập chỉnh sửa thông tin cá nhân của chính mình
     * (tên hiển thị, số điện thoại, địa chỉ, mật khẩu).
     * <p>
     * <strong>Phạm vi giới hạn:</strong> KHÔNG bao gồm thay đổi role hoặc restaurant.
     * Các trường nhạy cảm đó chỉ được chỉnh sửa qua {@code ASSIGN_ROLE} /
     * {@code MANAGE_RESTAURANT} bởi admin cấp cao hơn.
     * Tách riêng khỏi {@code EDIT_EMPLOYEE} để đảm bảo một staff bình thường
     * không vô tình (hoặc cố ý) chỉnh sửa thông tin của người khác.
     */
    EDIT_OWN_PROFILE,

    /**
     * Cho phép RESTAURANT_ADMIN xem thông tin chi tiết của nhà hàng mà mình đang quản lý.
     * <p>
     * Tách riêng khỏi {@code MANAGE_RESTAURANT} (chỉ SUPER_ADMIN) vì:
     * MANAGE_RESTAURANT cho phép thao tác trên <em>mọi</em> nhà hàng trong hệ thống
     * (thêm, xóa, xem tất cả), trong khi permission này chỉ áp dụng cho đúng nhà hàng
     * mà admin đang thuộc về. Nguyên tắc least-privilege đòi hỏi phân tách rõ ràng này.
     */
    VIEW_OWN_RESTAURANT,

    /**
     * Cho phép RESTAURANT_ADMIN cập nhật thông tin của nhà hàng mà mình đang quản lý
     * (tên, địa chỉ, số điện thoại, giờ hoạt động, v.v.).
     * <p>
     * Tách riêng khỏi {@code VIEW_OWN_RESTAURANT} theo nguyên tắc read/write separation,
     * và tách riêng khỏi {@code MANAGE_RESTAURANT} (SUPER_ADMIN) để đảm bảo RESTAURANT_ADMIN
     * không thể can thiệp vào dữ liệu của nhà hàng khác trong cùng hệ thống.
     */
    EDIT_OWN_RESTAURANT;

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
                    OPEN_TABLE, UPDATE_ITEM_STATUS, VIEW_KITCHEN, VIEW_WAITER_SERVICE,
                    // Phase 2
                    REGISTER_STAFF, EDIT_OWN_PROFILE, VIEW_OWN_RESTAURANT, EDIT_OWN_RESTAURANT
                    // MANAGE_RESTAURANT và ASSIGN_ROLE bị loại trừ theo đặc tả
            ));

    private static final Set<Permission> WAITER_PERMS =
            Collections.unmodifiableSet(EnumSet.of(
                    VIEW_ORDER,  ADD_ORDER, UPDATE_ORDER_STATUS,
                    VIEW_TABLE,
                    VIEW_MENU,
                    ADD_REPORT,
                    // Phase 1
                    VIEW_WAITER_SERVICE, UPDATE_ITEM_STATUS,
                    // Phase 2
                    EDIT_OWN_PROFILE
            ));

    private static final Set<Permission> CHEF_PERMS =
            Collections.unmodifiableSet(EnumSet.of(
                    VIEW_ORDER, UPDATE_ORDER_STATUS,
                    VIEW_MENU,
                    ADD_REPORT,
                    // Phase 1
                    VIEW_KITCHEN, UPDATE_ITEM_STATUS,
                    // Phase 2
                    EDIT_OWN_PROFILE
            ));

    private static final Set<Permission> CASHIER_PERMS =
            Collections.unmodifiableSet(EnumSet.of(
                    VIEW_ORDER, UPDATE_ORDER_STATUS,
                    VIEW_TABLE, EDIT_TABLE,
                    VIEW_STATS,
                    ADD_REPORT,
                    // Phase 1
                    OPEN_TABLE,
                    // Phase 2
                    EDIT_OWN_PROFILE
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