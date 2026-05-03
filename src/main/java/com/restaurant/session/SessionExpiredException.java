package com.restaurant.session;

/**
 * Ném khi session token đã bị thu hồi hoặc hết hạn trong DB.
 *
 * <p>Được throw bởi {@link RbacGuard#can(Permission)} nếu token không còn
 * hợp lệ (ví dụ: đăng nhập trên thiết bị khác, đổi mật khẩu, hoặc timeout).
 *
 * <p>Bắt tại {@link com.restaurant.ui.LoginController#doLogin()} để chuyển
 * người dùng về màn hình đăng nhập thay vì hiện dialog lỗi chung.
 */
public class SessionExpiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Lý do mặc định — dùng khi không có thêm thông tin. */
    public SessionExpiredException() {
        super("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    }

    /**
     * @param message mô tả nguyên nhân cụ thể (token revoked, timeout, v.v.)
     */
    public SessionExpiredException(String message) {
        super(message);
    }

    /**
     * @param message mô tả nguyên nhân
     * @param cause   exception gốc (ví dụ: {@code SQLException})
     */
    public SessionExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}