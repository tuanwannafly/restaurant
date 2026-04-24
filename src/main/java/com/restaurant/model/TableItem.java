package com.restaurant.model;

public class TableItem {
    public enum Status { RANH, BAN, DAT_TRUOC }

    private String id;
    private String name;
    private int capacity;
    private Status status;

    public TableItem(String id, String name, int capacity, Status status) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getStatusDisplay() {
        switch (status) {
            case RANH: return "Rảnh";
            case BAN: return "Bận";
            case DAT_TRUOC: return "Đặt trước";
            default: return "";
        }
    }
}
