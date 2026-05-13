package com.restaurant.model;

import java.time.LocalDateTime;

/**
 * Model đại diện cho một đơn đăng ký mở nhà hàng.
 *
 * <p><b>Vòng đời:</b>
 * <ol>
 *   <li>Chủ nhà hàng điền form (public, không cần login) → status = {@link RequestStatus#PENDING}</li>
 *   <li>SUPER_ADMIN xem danh sách, mở chi tiết và phê duyệt hoặc từ chối.</li>
 *   <li>Khi APPROVED → RestaurantRequestDAO tự động tạo record RESTAURANTS + USERS.</li>
 *   <li>Khi REJECTED → lưu {@link #rejectReason} để chủ nhà hàng biết lý do.</li>
 * </ol>
 *
 * <p><b>Khác biệt với {@link Restaurant}:</b>
 * RestaurantRequest là đơn <em>đề xuất</em>, không phải nhà hàng đang hoạt động.
 * Hai model có vòng đời riêng biệt và được lưu trên bảng khác nhau (RESTAURANT_REQUESTS).
 */
public class RestaurantRequest {

    // ── Inner enum ────────────────────────────────────────────────────────────

    /**
     * Trạng thái xử lý của đơn đăng ký.
     */
    public enum RequestStatus {
        /** Vừa nộp, chờ SUPER_ADMIN xem xét. */
        PENDING,
        /** Đã được SUPER_ADMIN chấp thuận — nhà hàng + tài khoản đã được tạo. */
        APPROVED,
        /** Bị từ chối — xem {@link RestaurantRequest#getRejectReason()} để biết lý do. */
        REJECTED;

        /** Nhãn tiếng Việt để hiển thị trên UI. */
        public String label() {
            return switch (this) {
                case PENDING  -> "Chờ duyệt";
                case APPROVED -> "Đã duyệt";
                case REJECTED -> "Từ chối";
            };
        }

        /**
         * Parse từ String DB → enum.
         * Trả về {@link #PENDING} nếu không nhận ra giá trị.
         */
        public static RequestStatus from(String s) {
            if (s == null) return PENDING;
            return switch (s.trim().toUpperCase()) {
                case "APPROVED" -> APPROVED;
                case "REJECTED" -> REJECTED;
                default         -> PENDING;
            };
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private long          requestId;

    // Thông tin chủ nhà hàng
    private String        ownerName;
    private String        ownerEmail;
    private String        ownerPhone;
    /** BCrypt hash — KHÔNG bao giờ expose ra UI. Chỉ dùng khi tạo tài khoản lúc approve. */
    private String        ownerPasswordHash;

    // Thông tin nhà hàng đề xuất
    private String        restaurantName;
    private String        restaurantAddress;
    private String        restaurantPhone;
    private String        restaurantEmail;

    // File đính kèm (nullable)
    private String        logoPath;
    private String        documentPath;

    // Trạng thái
    private RequestStatus status;
    private String        rejectReason;

    // Timestamps
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;   // nullable
    private long          reviewedBy;   // 0 nếu chưa review (user_id của admin)

    // ── Constructors ──────────────────────────────────────────────────────────

    public RestaurantRequest() {
        this.status = RequestStatus.PENDING;
    }

    /**
     * Constructor tiện lợi để tạo đơn mới từ form đăng ký (public).
     * reviewedAt và reviewedBy để trống (chưa xử lý).
     */
    public RestaurantRequest(String ownerName, String ownerEmail, String ownerPhone,
                             String ownerPasswordHash,
                             String restaurantName, String restaurantAddress,
                             String restaurantPhone, String restaurantEmail) {
        this.ownerName          = ownerName;
        this.ownerEmail         = ownerEmail;
        this.ownerPhone         = ownerPhone;
        this.ownerPasswordHash  = ownerPasswordHash;
        this.restaurantName     = restaurantName;
        this.restaurantAddress  = restaurantAddress;
        this.restaurantPhone    = restaurantPhone;
        this.restaurantEmail    = restaurantEmail;
        this.status             = RequestStatus.PENDING;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public long          getRequestId()                          { return requestId; }
    public void          setRequestId(long requestId)           { this.requestId = requestId; }

    public String        getOwnerName()                          { return ownerName; }
    public void          setOwnerName(String ownerName)         { this.ownerName = ownerName; }

    public String        getOwnerEmail()                         { return ownerEmail; }
    public void          setOwnerEmail(String ownerEmail)       { this.ownerEmail = ownerEmail; }

    public String        getOwnerPhone()                         { return ownerPhone; }
    public void          setOwnerPhone(String ownerPhone)       { this.ownerPhone = ownerPhone; }

    /** BCrypt hash — chỉ dùng nội bộ khi tạo tài khoản, không hiển thị UI. */
    public String        getOwnerPasswordHash()                  { return ownerPasswordHash; }
    public void          setOwnerPasswordHash(String hash)      { this.ownerPasswordHash = hash; }

    public String        getRestaurantName()                     { return restaurantName; }
    public void          setRestaurantName(String name)         { this.restaurantName = name; }

    public String        getRestaurantAddress()                  { return restaurantAddress; }
    public void          setRestaurantAddress(String address)   { this.restaurantAddress = address; }

    public String        getRestaurantPhone()                    { return restaurantPhone; }
    public void          setRestaurantPhone(String phone)       { this.restaurantPhone = phone; }

    public String        getRestaurantEmail()                    { return restaurantEmail; }
    public void          setRestaurantEmail(String email)       { this.restaurantEmail = email; }

    public String        getLogoPath()                           { return logoPath; }
    public void          setLogoPath(String logoPath)           { this.logoPath = logoPath; }

    public String        getDocumentPath()                       { return documentPath; }
    public void          setDocumentPath(String documentPath)   { this.documentPath = documentPath; }

    public RequestStatus getStatus()                             { return status; }
    public void          setStatus(RequestStatus status)        { this.status = status; }

    public String        getRejectReason()                       { return rejectReason; }
    public void          setRejectReason(String rejectReason)   { this.rejectReason = rejectReason; }

    public LocalDateTime getSubmittedAt()                        { return submittedAt; }
    public void          setSubmittedAt(LocalDateTime t)        { this.submittedAt = t; }

    public LocalDateTime getReviewedAt()                         { return reviewedAt; }
    public void          setReviewedAt(LocalDateTime t)         { this.reviewedAt = t; }

    /** user_id của admin đã xử lý; 0 nếu chưa review. */
    public long          getReviewedBy()                         { return reviewedBy; }
    public void          setReviewedBy(long reviewedBy)         { this.reviewedBy = reviewedBy; }

    // ── Convenience helpers ───────────────────────────────────────────────────

    /** {@code true} nếu đơn đang chờ xét duyệt. */
    public boolean isPending()  { return RequestStatus.PENDING  == status; }
    /** {@code true} nếu đơn đã được chấp thuận. */
    public boolean isApproved() { return RequestStatus.APPROVED == status; }
    /** {@code true} nếu đơn đã bị từ chối. */
    public boolean isRejected() { return RequestStatus.REJECTED == status; }

    @Override
    public String toString() {
        return "RestaurantRequest{id=" + requestId
                + ", owner=" + ownerName
                + ", restaurant=" + restaurantName
                + ", status=" + status
                + "}";
    }
}