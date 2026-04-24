package com.restaurant.model;

import java.time.LocalDateTime;

/**
 * Model ánh xạ bảng REPORTS trong Oracle DB.
 * <p>
 * Ba enum lồng bên trong (ReportType, Severity, Status) tương ứng với
 * các giá trị VARCHAR2 lưu trong DB, giúp tránh typo khi binding SQL.
 */
public class Report {

    // ── Enums lồng bên trong ─────────────────────────────────────────────────

    public enum ReportType {
        INCIDENT,
        MAINTENANCE,
        FEEDBACK
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum Status {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        CLOSED
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private long          reportId;
    private String        title;
    private String        description;
    private ReportType    reportType;
    private Severity      severity;
    private Status        status;
    private long          createdBy;       // user_id người tạo
    private long          restaurantId;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;      // null nếu chưa resolved

    // ── Constructor rỗng (no-arg) ─────────────────────────────────────────────

    public Report() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public long getReportId()                   { return reportId; }
    public void setReportId(long reportId)       { this.reportId = reportId; }

    public String getTitle()                    { return title; }
    public void setTitle(String title)           { this.title = title; }

    public String getDescription()              { return description; }
    public void setDescription(String description) { this.description = description; }

    public ReportType getReportType()           { return reportType; }
    public void setReportType(ReportType reportType) { this.reportType = reportType; }

    public Severity getSeverity()               { return severity; }
    public void setSeverity(Severity severity)   { this.severity = severity; }

    public Status getStatus()                   { return status; }
    public void setStatus(Status status)         { this.status = status; }

    public long getCreatedBy()                  { return createdBy; }
    public void setCreatedBy(long createdBy)     { this.createdBy = createdBy; }

    public long getRestaurantId()               { return restaurantId; }
    public void setRestaurantId(long restaurantId) { this.restaurantId = restaurantId; }

    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getResolvedAt()        { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    // ── Utility methods — hiển thị tiếng Việt ────────────────────────────────

    /**
     * Trả về nhãn tiếng Việt của loại báo cáo.
     */
    public String getReportTypeDisplay() {
        if (reportType == null) return "";
        return switch (reportType) {
            case INCIDENT    -> "Sự cố";
            case MAINTENANCE -> "Bảo trì";
            case FEEDBACK    -> "Phản hồi";
        };
    }

    /**
     * Trả về nhãn tiếng Việt của mức độ nghiêm trọng.
     */
    public String getSeverityDisplay() {
        if (severity == null) return "";
        return switch (severity) {
            case LOW      -> "Thấp";
            case MEDIUM   -> "Trung bình";
            case HIGH     -> "Cao";
            case CRITICAL -> "Nghiêm trọng";
        };
    }

    /**
     * Trả về nhãn tiếng Việt của trạng thái báo cáo.
     */
    public String getStatusDisplay() {
        if (status == null) return "";
        return switch (status) {
            case OPEN        -> "Mở";
            case IN_PROGRESS -> "Đang xử lý";
            case RESOLVED    -> "Đã giải quyết";
            case CLOSED      -> "Đã đóng";
        };
    }

    @Override
    public String toString() {
        return "Report{" +
                "reportId=" + reportId +
                ", title='" + title + '\'' +
                ", reportType=" + reportType +
                ", severity=" + severity +
                ", status=" + status +
                ", createdBy=" + createdBy +
                ", restaurantId=" + restaurantId +
                '}';
    }
}