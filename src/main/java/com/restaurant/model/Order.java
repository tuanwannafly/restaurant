package com.restaurant.model;

import java.util.ArrayList;
import java.util.List;

public class Order {
    public enum Status { DANG_PHUC_VU, HOAN_THANH, DA_HUY }

    private String id;
    private String tableId;
    private String tableName;
    private double totalAmount;
    private Status status;
    private String createdTime;
    private List<OrderItem> items;

    public Order(String id, String tableId, String tableName, double totalAmount, Status status, String createdTime) {
        this.id = id;
        this.tableId = tableId;
        this.tableName = tableName;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdTime = createdTime;
        this.items = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTableId() { return tableId; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getCreatedTime() { return createdTime; }
    public void setCreatedTime(String createdTime) { this.createdTime = createdTime; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public String getStatusDisplay() {
        switch (status) {
            case DANG_PHUC_VU: return "Đang phục vụ";
            case HOAN_THANH: return "Hoàn thành";
            case DA_HUY: return "Đã hủy";
            default: return "";
        }
    }

    public static class OrderItem {
        private String menuItemId;
        private String menuItemName;
        private int quantity;
        private double unitPrice;

        public OrderItem(String menuItemName, int quantity, double unitPrice) {
            this.menuItemName = menuItemName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public OrderItem(String menuItemId, String menuItemName, int quantity, double unitPrice) {
            this.menuItemId = menuItemId;
            this.menuItemName = menuItemName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public String getMenuItemId() { return menuItemId; }
        public void setMenuItemId(String menuItemId) { this.menuItemId = menuItemId; }
        public String getMenuItemName() { return menuItemName; }
        public int getQuantity() { return quantity; }
        public double getUnitPrice() { return unitPrice; }
        public double getSubtotal() { return quantity * unitPrice; }
    }
}
