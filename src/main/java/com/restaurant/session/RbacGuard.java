package com.restaurant.session;

/**
 * RBAC Guard — kiểm tra quyền của người dùng đang đăng nhập.
 * <p>
 * <b>Thread-safety:</b> Singleton khởi tạo bằng initialization-on-demand holder,
 * an toàn với cả Swing EDT và các worker thread.
 * Mọi thao tác đều đọc từ {@link AppSession} (volatile fields) nên không
 * cần synchronized thêm.
 * <p>
 * <b>Cách dùng:</b>
 * <pre>{@code
 *   if (RbacGuard.getInstance().can(Permission.ADD_ORDER)) {
 *       // hiện nút "Thêm đơn"
 *   }
 * }</pre>
 */
public final class RbacGuard {

    // ── Singleton (initialization-on-demand holder) ───────────────────────────
    private RbacGuard() {}

    private static final class Holder {
        static final RbacGuard INSTANCE = new RbacGuard();
    }

    public static RbacGuard getInstance() {
        return Holder.INSTANCE;
    }

    // ── Core permission check ─────────────────────────────────────────────────

    /**
     * Kiểm tra người dùng hiện tại có quyền {@code p} hay không.
     *
     * @param p permission cần kiểm tra (non-null)
     * @return {@code true} nếu role hiện tại có quyền đó
     */
    /**
     * Kiểm tra người dùng hiện tại có quyền {@code p} hay không.
     * <p>
     * <b>Phase 6 — Token Guard:</b> Trước khi kiểm tra role, xác nhận session token
     * còn hiệu lực qua {@link TokenService#validateToken(String)}.
     * Nếu token không hợp lệ (hết hạn / bị thu hồi / null), ném
     * {@link SessionExpiredException} thay vì trả về {@code false}.
     * Điều này buộc UI layer phải bắt exception và chuyển về LoginDialog
     * thay vì chỉ ẩn nút bấm.
     *
     * @param p permission cần kiểm tra (non-null)
     * @return {@code true} nếu role hiện tại có quyền đó
     * @throws SessionExpiredException nếu session token không hợp lệ
     */
    public boolean can(Permission p) {
        if (p == null) return false;

        // Phase 6: Kiểm tra session token còn hợp lệ trước khi kiểm tra RBAC
        String token = AppSession.getInstance().getSessionToken();
        if (!TokenService.getInstance().validateToken(token)) {
            throw new SessionExpiredException();
        }

        String role = AppSession.getInstance().getUserRole();
        return Permission.forRole(role).contains(p);
    }

    // ── Role-level convenience helpers ────────────────────────────────────────

    /** {@code true} nếu đang là SUPER_ADMIN. */
    public boolean isSuperAdmin() {
        String role = currentRole();
        return "SUPER_ADMIN".equalsIgnoreCase(role);
    }

    /** {@code true} nếu đang là RESTAURANT_ADMIN (hoặc ADMIN / QUAN_LY). */
    public boolean isRestaurantAdmin() {
        String role = currentRole();
        return role != null && (
                "RESTAURANT_ADMIN".equalsIgnoreCase(role) ||
                "ADMIN".equalsIgnoreCase(role)            ||
                "QUAN_LY".equalsIgnoreCase(role)
        );
    }

    /**
     * {@code true} nếu SUPER_ADMIN hoặc RESTAURANT_ADMIN (bao gồm alias ADMIN / QUAN_LY).
     * Dùng để kiểm soát các chức năng quản trị cấp nhà hàng.
     */
    public boolean isManagerOrAbove() {
        return isSuperAdmin() || isRestaurantAdmin();
    }

    /**
     * {@code true} nếu là nhân viên vận hành (WAITER, CHEF, CASHIER và alias).
     */
    public boolean isStaff() {
        String role = currentRole();
        if (role == null) return false;
        return switch (role.toUpperCase()) {
            case "WAITER", "PHUC_VU",
                 "CHEF",   "DAU_BEP",
                 "CASHIER","THU_NGAN" -> true;
            default                   -> false;
        };
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String currentRole() {
        return AppSession.getInstance().getUserRole();
    }
}