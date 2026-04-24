package com.restaurant.model;

import java.time.LocalDateTime;

/**
 * Model đại diện cho một nhà hàng trong hệ thống.
 * Chỉ SUPER_ADMIN mới có quyền thao tác (kiểm tra ở RestaurantDAO).
 */
public class Restaurant {

    public enum Status {
        ACTIVE, INACTIVE;

        /** Nhãn tiếng Việt để hiển thị trên UI. */
        public String label() {
            return this == ACTIVE ? "Hoạt động" : "Vô hiệu hóa";
        }

        public static Status from(String s) {
            if (s == null) return INACTIVE;
            return "ACTIVE".equalsIgnoreCase(s.trim()) ? ACTIVE : INACTIVE;
        }
    }

    private long          restaurantId;
    private String        name;
    private String        address;
    private String        phone;
    private String        email;
    private Status        status;
    private LocalDateTime createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Restaurant() {}

    public Restaurant(long restaurantId, String name, String address,
                      String phone, String email, Status status,
                      LocalDateTime createdAt) {
        this.restaurantId = restaurantId;
        this.name         = name;
        this.address      = address;
        this.phone        = phone;
        this.email        = email;
        this.status       = status;
        this.createdAt    = createdAt;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public long          getRestaurantId()              { return restaurantId; }
    public void          setRestaurantId(long id)       { this.restaurantId = id; }

    public String        getName()                      { return name; }
    public void          setName(String name)           { this.name = name; }

    public String        getAddress()                   { return address; }
    public void          setAddress(String address)     { this.address = address; }

    public String        getPhone()                     { return phone; }
    public void          setPhone(String phone)         { this.phone = phone; }

    public String        getEmail()                     { return email; }
    public void          setEmail(String email)         { this.email = email; }

    public Status        getStatus()                    { return status; }
    public void          setStatus(Status status)       { this.status = status; }

    public LocalDateTime getCreatedAt()                 { return createdAt; }
    public void          setCreatedAt(LocalDateTime t)  { this.createdAt = t; }

    @Override
    public String toString() {
        return name != null ? name : "(nhà hàng #" + restaurantId + ")";
    }
}