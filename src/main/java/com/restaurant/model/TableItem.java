package com.restaurant.model;

public class TableItem {

    /**
     * Trạng thái bàn.
     *
     * Các giá trị RANH / BAN / DAT_TRUOC giữ nguyên.
     * Thêm DIRTY (cần dọn) và CLEANING (đang dọn) để phản ánh đúng DB.
     */
    public enum Status {
        /** Bàn trống, sẵn sàng phục vụ */
        RANH,
        /** Bàn đang có khách */
        BAN,
        /** Bàn đã được đặt trước */
        DAT_TRUOC,
        /** Bàn vừa dùng xong, cần dọn dẹp (DB: DIRTY hoặc OUT_OF_SERVICE) */
        DIRTY,
        /** Nhân viên đang dọn bàn (DB: CLEANING) */
        CLEANING
    }

    // ─── Fields ───────────────────────────────────────────────────────────────

    private String id;
    private String name;
    private int    capacity;
    private Status status;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public TableItem(String id, String name, int capacity, Status status) {
        this.id       = id;
        this.name     = name;
        this.capacity = capacity;
        this.status   = status;
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public String getId()                        { return id; }
    public void   setId(String id)               { this.id = id; }

    public String getName()                      { return name; }
    public void   setName(String name)           { this.name = name; }

    public int    getCapacity()                  { return capacity; }
    public void   setCapacity(int capacity)      { this.capacity = capacity; }

    public Status getStatus()                    { return status; }
    public void   setStatus(Status status)       { this.status = status; }

    // ─── Display helper ───────────────────────────────────────────────────────

    public String getStatusDisplay() {
        if (status == null) return "";
        switch (status) {
            case RANH:      return "Rảnh";
            case BAN:       return "Bận";
            case DAT_TRUOC: return "Đặt trước";
            case DIRTY:     return "Cần dọn";
            case CLEANING:  return "Đang dọn";
            default:        return "";
        }
    }
}