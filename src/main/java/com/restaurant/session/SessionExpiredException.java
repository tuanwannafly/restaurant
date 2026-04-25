package com.restaurant.session;

/**
 * Ném ra khi session token không hợp lệ hoặc đã hết hạn.
 * <p>
 * Được ném bởi {@link RbacGuard#can(Permission)} trước mỗi kiểm tra quyền.
 * UI layer (MainFrame / các Panel) bắt exception này và chuyển về LoginDialog.
 */
public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException() {
        super("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    }

    public SessionExpiredException(String message) {
        super(message);
    }

    public SessionExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}