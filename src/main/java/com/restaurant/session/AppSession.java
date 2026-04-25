package com.restaurant.session;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton lưu thông tin phiên làm việc sau khi đăng nhập thành công.
 * Thay thế hoàn toàn AuthManager + JWT token từ phiên bản REST API.
 * <p>
 * Phase 1: Tích hợp RBAC — {@link #hasPermission(Permission)} delegate
 * sang {@link RbacGuard}; các helper isAdmin/isManager/isCashier refactor
 * để dùng {@link Permission#forRole(String)} thay vì so sánh string thô.
 * <p>
 * Phase 5C: Thêm {@link SessionListener} với WeakReference để tránh memory leak.
 * <p>
 * Phase 6 (Session Token): Thêm field {@link #sessionToken} — được gán bởi
 * {@link com.restaurant.dao.UserDAO#login} sau khi BCrypt xác thực thành công,
 * và được thu hồi tự động khi {@link #logout()} được gọi qua
 * {@link TokenService#revokeToken(String)}.
 */
public class AppSession {

    // ── SessionListener ───────────────────────────────────────────────────────

    /**
     * Interface để nhận thông báo khi người dùng đăng xuất.
     * Dùng WeakReference để tránh memory leak khi listener (e.g. JFrame) bị dispose.
     */
    public interface SessionListener {
        void onLogout();
    }

    private static AppSession instance;

    private long    userId;
    private String  userName;
    private String  userEmail;
    /**
     * Tên role lưu đúng theo DB (SUPER_ADMIN | RESTAURANT_ADMIN | WAITER | CHEF | CASHIER).
     * Có thể null sau khi logout.
     */
    private String  userRole;
    private long    restaurantId;
    private boolean loggedIn = false;

    /**
     * Session token hiện tại — được gán bởi {@code UserDAO.login()} ngay sau khi
     * xác thực BCrypt thành công.  Null khi chưa đăng nhập hoặc sau khi logout.
     *
     * @see TokenService#generateSessionToken(long)
     * @see TokenService#revokeToken(String)
     */
    private String sessionToken;

    /** Danh sách listener dùng WeakReference để tránh memory leak. */
    private final List<WeakReference<SessionListener>> listeners = new ArrayList<>();

    private AppSession() {}

    public static AppSession getInstance() {
        if (instance == null) instance = new AppSession();
        return instance;
    }

    // ── Đăng nhập / Đăng xuất ─────────────────────────────────────────────────

    public void login(long userId, String name, String email, String role, long restaurantId) {
        this.userId       = userId;
        this.userName     = name;
        this.userEmail    = email;
        this.userRole     = role;
        this.restaurantId = restaurantId;
        this.loggedIn     = true;
        // sessionToken được gán riêng qua setSessionToken() từ UserDAO.login()
        // sau khi TokenService.generateSessionToken() thành công.
    }

    /**
     * Gán session token vừa được tạo bởi {@link TokenService#generateSessionToken(long)}.
     * Được gọi từ {@code UserDAO.login()} ngay sau {@link #login}.
     *
     * @param token UUID token 36 ký tự; null được chấp nhận nhưng sẽ khiến
     *              {@link RbacGuard#can} từ chối mọi quyền.
     */
    public void setSessionToken(String token) {
        this.sessionToken = token;
    }

    /**
     * Trả về session token hiện tại.
     * Null nếu chưa đăng nhập hoặc token chưa được gán.
     */
    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Đăng ký một {@link SessionListener}.
     * Dùng WeakReference nên caller không cần unregister thủ công.
     * Nếu {@code l} là null thì bỏ qua, không throw.
     */
    public void addSessionListener(SessionListener l) {
        if (l == null) return;
        listeners.add(new WeakReference<>(l));
    }

    /**
     * Thông báo tất cả listener còn sống (chưa bị GC) rằng session đã logout.
     * Tự động dọn sạch các WeakReference đã chết.
     */
    private void notifyLogout() {
        listeners.removeIf(ref -> ref.get() == null);
        listeners.forEach(ref -> {
            SessionListener l = ref.get();
            if (l != null) l.onLogout();
        });
    }

    /**
     * Đăng xuất: thu hồi session token, reset tất cả fields về giá trị mặc định
     * rồi notify listeners.
     * <p>
     * Gọi 2 lần liên tiếp an toàn — chỉ thu hồi token và notify 1 lần (lần đầu tiên).
     */
    public void logout() {
        if (!loggedIn) return;   // idempotent: không notify lần 2

        // Thu hồi token trong DB trước khi reset state
        TokenService.getInstance().revokeToken(this.sessionToken);

        this.userId       = 0L;
        this.userName     = null;
        this.userEmail    = null;
        this.userRole     = null;
        this.restaurantId = 0L;
        this.loggedIn     = false;
        this.sessionToken = null;

        notifyLogout();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isLoggedIn()      { return loggedIn; }
    public long    getUserId()       { return userId; }
    public String  getUserName()     { return userName; }
    public String  getUserEmail()    { return userEmail; }
    public String  getUserRole()     { return userRole; }
    public long    getRestaurantId() { return restaurantId; }

    // ── RBAC ──────────────────────────────────────────────────────────────────

    /**
     * Kiểm tra người dùng hiện tại có quyền {@code p} hay không.
     * Delegate sang {@link RbacGuard} — không query DB.
     *
     * @param p permission cần kiểm tra
     * @return {@code true} nếu role hiện tại được phép
     * @throws SessionExpiredException nếu session token không còn hợp lệ
     */
    public boolean hasPermission(Permission p) {
        return RbacGuard.getInstance().can(p);
    }

    // ── Role convenience (refactored — delegate sang Permission.forRole) ───────

    /**
     * {@code true} nếu là SUPER_ADMIN hoặc RESTAURANT_ADMIN.
     * Refactored: delegate sang RbacGuard thay vì so sánh string thô.
     */
    public boolean isAdmin() {
        return RbacGuard.getInstance().isSuperAdmin()
            || RbacGuard.getInstance().isRestaurantAdmin();
    }

    /**
     * {@code true} nếu là manager trở lên (SUPER_ADMIN hoặc RESTAURANT_ADMIN).
     * Refactored: delegate sang RbacGuard#isManagerOrAbove().
     */
    public boolean isManager() {
        return RbacGuard.getInstance().isManagerOrAbove();
    }

    /**
     * {@code true} nếu có quyền xem thống kê (CASHIER+).
     * Refactored: dùng Permission check thay vì chuỗi string lồng nhau.
     */
    public boolean isCashier() {
        return isManager()
            || Permission.forRole(userRole).contains(Permission.VIEW_STATS);
    }

    // ── Display helpers ───────────────────────────────────────────────────────

    /** Nhãn hiển thị tiếng Việt của role hiện tại. */
    public String getRoleLabel() {
        if (userRole == null) return "Phục vụ";
        return switch (userRole.toUpperCase()) {
            case "SUPER_ADMIN"                    -> "Admin";
            case "ADMIN", "RESTAURANT_ADMIN",
                 "QUAN_LY"                        -> "Quản lý";
            case "CASHIER", "THU_NGAN"            -> "Thu ngân";
            case "CHEF",    "DAU_BEP"             -> "Đầu bếp";
            case "WAITER",  "PHUC_VU"             -> "Phục vụ";
            default                               -> userRole;
        };
    }
}