package com.restaurant.model;

import java.util.ArrayList;
import java.util.List;

public class Order {

    /**
     * Trạng thái đơn hàng.
     *
     * Các giá trị cũ (DANG_PHUC_VU, HOAN_THANH, DA_HUY) được giữ nguyên để
     * backward-compatible với UI hiện tại cho đến khi các phase tiếp theo cập nhật UI.
     * Các giá trị mới phản ánh luồng trạng thái chi tiết hơn từ DB.
     */
    public enum Status {
        // ── Legacy – giữ để UI cũ không bị compile error ──────────────────────
        /** @deprecated Dùng PENDING hoặc ACCEPTED thay thế */
        @Deprecated DANG_PHUC_VU,
        /** @deprecated Dùng COMPLETED thay thế */
        @Deprecated HOAN_THANH,
        /** @deprecated Dùng CANCELLED thay thế */
        @Deprecated DA_HUY,

        // ── New extended statuses ──────────────────────────────────────────────
        /** Chờ nhà hàng xác nhận */
        PENDING,
        /** Nhà hàng đã xác nhận, chờ bếp */
        ACCEPTED,
        /** Bếp đang chế biến */
        COOKING,
        /** Món đã sẵn sàng, chờ phục vụ */
        READY,
        /** Nhân viên đang mang món đến bàn */
        DELIVERING,
        /** Món đã giao đến bàn */
        DELIVERED,
        /** Đơn hàng hoàn thành, đã thanh toán */
        COMPLETED,
        /** Đơn hàng bị huỷ */
        CANCELLED
    }

    // ─── Fields ───────────────────────────────────────────────────────────────

    private String id;
    private String tableId;
    private String tableName;
    private double totalAmount;
    private Status status;
    private String createdTime;
    private List<OrderItem> items;

    /** Tên khách hàng – nullable (đặt qua app / điện thoại) */
    private String customerName;

    /** Số điện thoại khách hàng – nullable */
    private String customerPhone;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /** Constructor gốc – giữ nguyên để không break code hiện tại */
    public Order(String id, String tableId, String tableName,
                 double totalAmount, Status status, String createdTime) {
        this.id          = id;
        this.tableId     = tableId;
        this.tableName   = tableName;
        this.totalAmount = totalAmount;
        this.status      = status;
        this.createdTime = createdTime;
        this.items       = new ArrayList<>();
    }

    /** Constructor mở rộng – thêm customerName và customerPhone */
    public Order(String id, String tableId, String tableName,
                 double totalAmount, Status status, String createdTime,
                 String customerName, String customerPhone) {
        this(id, tableId, tableName, totalAmount, status, createdTime);
        this.customerName  = customerName;
        this.customerPhone = customerPhone;
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public String getId()                          { return id; }
    public void   setId(String id)                 { this.id = id; }

    public String getTableId()                     { return tableId; }

    public String getTableName()                   { return tableName; }
    public void   setTableName(String tableName)   { this.tableName = tableName; }

    public double getTotalAmount()                 { return totalAmount; }
    public void   setTotalAmount(double v)         { this.totalAmount = v; }

    public Status getStatus()                      { return status; }
    public void   setStatus(Status status)         { this.status = status; }

    public String getCreatedTime()                 { return createdTime; }
    public void   setCreatedTime(String v)         { this.createdTime = v; }

    public List<OrderItem> getItems()              { return items; }
    public void            setItems(List<OrderItem> items) { this.items = items; }

    public String getCustomerName()                { return customerName; }
    public void   setCustomerName(String v)        { this.customerName = v; }

    public String getCustomerPhone()               { return customerPhone; }
    public void   setCustomerPhone(String v)       { this.customerPhone = v; }

    // ─── Display helper ───────────────────────────────────────────────────────

    public String getStatusDisplay() {
        if (status == null) return "";
        switch (status) {
            // Legacy labels
            case DANG_PHUC_VU: return "Đang phục vụ";
            case HOAN_THANH:   return "Hoàn thành";
            case DA_HUY:       return "Đã hủy";
            // New labels
            case PENDING:      return "Chờ xác nhận";
            case ACCEPTED:     return "Đã xác nhận";
            case COOKING:      return "Đang nấu";
            case READY:        return "Sẵn sàng";
            case DELIVERING:   return "Đang giao";
            case DELIVERED:    return "Đã giao";
            case COMPLETED:    return "Hoàn thành";
            case CANCELLED:    return "Đã hủy";
            default:           return "";
        }
    }

    // ─── Inner class: OrderItem ───────────────────────────────────────────────

    public static class OrderItem {

        /**
         * Trạng thái của từng món trong đơn hàng.
         * Phản ánh cột {@code item_status} trong bảng {@code order_items}.
         */
        public enum ItemStatus {
            PENDING,
            ACCEPTED,
            COOKING,
            READY,
            DELIVERING,
            DELIVERED
        }

        private String     menuItemId;
        private String     menuItemName;
        private int        quantity;
        private double     unitPrice;
        private ItemStatus itemStatus = ItemStatus.PENDING;

        // ── Constructors (giữ nguyên signature cũ) ────────────────────────────

        public OrderItem(String menuItemName, int quantity, double unitPrice) {
            this.menuItemName = menuItemName;
            this.quantity     = quantity;
            this.unitPrice    = unitPrice;
        }

        public OrderItem(String menuItemId, String menuItemName,
                         int quantity, double unitPrice) {
            this.menuItemId   = menuItemId;
            this.menuItemName = menuItemName;
            this.quantity     = quantity;
            this.unitPrice    = unitPrice;
        }

        public OrderItem(String menuItemId, String menuItemName,
                         int quantity, double unitPrice, ItemStatus itemStatus) {
            this(menuItemId, menuItemName, quantity, unitPrice);
            this.itemStatus = (itemStatus != null) ? itemStatus : ItemStatus.PENDING;
        }

        // ── Getters / Setters ──────────────────────────────────────────────────

        public String     getMenuItemId()                    { return menuItemId; }
        public void       setMenuItemId(String v)            { this.menuItemId = v; }

        public String     getMenuItemName()                  { return menuItemName; }

        public int        getQuantity()                      { return quantity; }

        public double     getUnitPrice()                     { return unitPrice; }

        public double     getSubtotal()                      { return quantity * unitPrice; }

        public ItemStatus getItemStatus()                    { return itemStatus; }
        public void       setItemStatus(ItemStatus v)        {
            this.itemStatus = (v != null) ? v : ItemStatus.PENDING;
        }
    }
}