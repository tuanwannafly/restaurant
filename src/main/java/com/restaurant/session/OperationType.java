package com.restaurant.session;

/**
 * Liệt kê các loại thao tác nhạy cảm yêu cầu xác nhận bằng Operation Token.
 *
 * <p>Mỗi giá trị tương ứng với một hành động không thể hoàn tác và
 * phải được người thực hiện xác nhận lại bằng mã token ngắn hạn
 * trước khi thực thi.
 *
 * <p><b>Sử dụng:</b>
 * <pre>{@code
 *   String token = OperationTokenService.getInstance()
 *                      .issueToken(OperationType.DELETE_RESTAURANT, restaurantId);
 *   boolean ok = ConfirmOperationDialog.show(owner, OperationType.DELETE_RESTAURANT, restaurantId);
 * }</pre>
 */
public enum OperationType {

    /** Xoá tài khoản người dùng khỏi hệ thống. */
    DELETE_USER("Xóa tài khoản người dùng"),

    /** Thay đổi vai trò (role) của một người dùng khác. */
    CHANGE_ROLE("Thay đổi vai trò người dùng"),

    /** Xoá hồ sơ nhân viên khỏi hệ thống. */
    DELETE_EMPLOYEE("Xóa nhân viên"),

    /** Xoá nhà hàng cùng toàn bộ dữ liệu liên quan. */
    DELETE_RESTAURANT("Xóa nhà hàng"),

    /** Đặt lại mật khẩu của người dùng khác. */
    RESET_OTHER_PASSWORD("Đặt lại mật khẩu người dùng khác");

    // ── Fields ────────────────────────────────────────────────────────────────

    /** Tên hiển thị thân thiện, dùng trong dialog xác nhận. */
    private final String displayName;

    OperationType(String displayName) {
        this.displayName = displayName;
    }

    /** Tên hiển thị thân thiện, dùng trong dialog xác nhận. */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Chuỗi tương ứng lưu vào cột {@code operation_type} trong DB.
     * Sử dụng {@link #name()} (tên enum) để đảm bảo nhất quán.
     */
    public String toDbValue() {
        return name();
    }
}
