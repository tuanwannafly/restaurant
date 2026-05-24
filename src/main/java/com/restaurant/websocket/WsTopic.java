package com.restaurant.websocket;

/**
 * Hằng số định nghĩa các topic WebSocket dùng trong hệ thống SmartRestaurant.
 *
 * <h3>Topic routing</h3>
 * <pre>
 *   KITCHEN      – thay đổi order_items (kitchen queue)
 *   ORDERS       – thay đổi bảng orders / order_items
 *   BADGE        – cập nhật badge đếm số (đồng thời với ORDERS / KITCHEN / REQUEST_LIST)
 *   REQUEST_LIST – thay đổi bảng restaurant_requests
 *   table:{id}   – event riêng cho một bàn cụ thể (dùng {@link #forTable(int)})
 * </pre>
 *
 * <p>Client subscribe bằng cách gửi frame dạng {@code "SUB:<topic>"}.
 * Server broadcast WsEvent JSON đến tất cả WebSocket đang subscribe topic đó.
 */
public final class WsTopic {

    // ── Broadcast topics ──────────────────────────────────────────────────────

    /** Kitchen panel — order_items thay đổi trạng thái. */
    public static final String KITCHEN      = "kitchen";

    /** Order panel — orders hoặc order_items thay đổi. */
    public static final String ORDERS       = "orders";

    /**
     * Badge refresh — gửi kèm với mọi thay đổi dữ liệu để client cập nhật
     * số đếm hiển thị trên thanh điều hướng (pending kitchen, payment request …).
     */
    public static final String BADGE        = "badge";

    /** Waiter request list — restaurant_requests thay đổi. */
    public static final String REQUEST_LIST = "request_list";

    /**
     * Trạng thái bàn thay đổi — broadcast mỗi khi bàn chuyển AVAILABLE/OCCUPIED/DIRTY…
     * {@code TableController} subscribe topic này để tự động gọi {@code loadData()}.
     */
    public static final String TABLES = "tables";

    // ── Dynamic per-table topic ───────────────────────────────────────────────

    /**
     * Tạo topic riêng cho một bàn cụ thể.
     *
     * <p>Format: {@code "table:<tableId>"}
     *
     * <p>Ví dụ: bàn số 7 → {@code "table:7"}
     *
     * @param tableId ID bàn (primary key trong DB)
     * @return chuỗi topic dạng {@code "table:<tableId>"}
     */
    public static String forTable(int tableId) {
        return "table:" + tableId;
    }

    // Không cho khởi tạo — đây là lớp hằng số thuần tuý.
    private WsTopic() {}
}