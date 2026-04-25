package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.restaurant.db.DBConnection;
import com.restaurant.model.Report;
import com.restaurant.model.Report.ReportType;
import com.restaurant.model.Report.Severity;
import com.restaurant.model.Report.Status;
import com.restaurant.session.AppSession;
import com.restaurant.session.RbacGuard;

/**
 * DAO thao tác bảng REPORTS với tenant isolation theo restaurant_id.
 *
 * <p>Mọi truy vấn đều scope theo {@link AppSession#getRestaurantId()}.
 * Nhân viên (non-manager) chỉ thấy báo cáo do chính mình tạo;
 * Manager trở lên thấy toàn bộ báo cáo của nhà hàng.
 *
 * <p>Không dùng bất kỳ annotation Spring nào.
 */
public class ReportDAO {

    // ═════════════════════════════════════════════════════════════════════════
    // PUBLIC METHODS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách báo cáo theo phân quyền:
     * <ul>
     *   <li>SUPER_ADMIN → toàn bộ báo cáo của mọi nhà hàng (kèm tên nhà hàng)</li>
     *   <li>Manager trở lên → toàn bộ báo cáo của nhà hàng mình</li>
     *   <li>Nhân viên thường → chỉ báo cáo do mình tạo</li>
     * </ul>
     *
     * @return danh sách Report, sắp xếp mới nhất trước; rỗng nếu không có
     */
    public List<Report> findByCurrentUser() {
        // SUPER_ADMIN: không có restaurant_id — query ALL reports across all restaurants
        if (RbacGuard.getInstance().isSuperAdmin()) {
            return findAllForSuperAdmin();
        }

        long    rid       = AppSession.getInstance().getRestaurantId();
        long    userId    = AppSession.getInstance().getUserId();
        boolean isManager = RbacGuard.getInstance().isManagerOrAbove();

        List<Report> list = new ArrayList<>();

        String sql;
        if (isManager) {
            sql = "SELECT report_id, title, description, report_type, severity, status," +
                  "       created_by, restaurant_id, created_at, resolved_at" +
                  "  FROM reports" +
                  " WHERE restaurant_id = ?" +
                  " ORDER BY created_at DESC";
        } else {
            sql = "SELECT report_id, title, description, report_type, severity, status," +
                  "       created_by, restaurant_id, created_at, resolved_at" +
                  "  FROM reports" +
                  " WHERE restaurant_id = ? AND created_by = ?" +
                  " ORDER BY created_at DESC";
        }

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, rid);
            if (!isManager) {
                ps.setLong(2, userId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }

        } catch (Exception e) {
            System.err.println("[ReportDAO] findByCurrentUser lỗi: " + e.getMessage());
            throw new RuntimeException("[ReportDAO] Không thể tải danh sách báo cáo: " + e.getMessage(), e);
        }

        return list;
    }

    /**
     * Lấy TOÀN BỘ báo cáo từ mọi nhà hàng — chỉ dùng cho SUPER_ADMIN.
     * JOIN với bảng restaurants để lấy tên nhà hàng hiển thị trong UI.
     *
     * @return danh sách Report đã điền {@code restaurantName}, mới nhất trước
     */
    private List<Report> findAllForSuperAdmin() {
        List<Report> list = new ArrayList<>();

        String sql = "SELECT r.report_id, r.title, r.description, r.report_type, r.severity," +
                     "       r.status, r.created_by, r.restaurant_id, r.created_at, r.resolved_at," +
                     "       NVL(rest.name, 'N/A') AS restaurant_name" +
                     "  FROM reports r" +
                     "  LEFT JOIN restaurants rest ON r.restaurant_id = rest.restaurant_id" +
                     " ORDER BY r.created_at DESC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Report rep = map(rs);
                rep.setRestaurantName(rs.getString("restaurant_name"));
                list.add(rep);
            }

        } catch (Exception e) {
            System.err.println("[ReportDAO] findAllForSuperAdmin lỗi: " + e.getMessage());
            throw new RuntimeException("[ReportDAO] Không thể tải báo cáo toàn hệ thống: " + e.getMessage(), e);
        }

        return list;
    }

    /**
     * Thêm mới một báo cáo.
     * <p>
     * {@code createdBy} và {@code restaurantId} luôn lấy từ {@link AppSession},
     * không nhận từ caller. Status mặc định là {@code 'OPEN'}.
     * Sau khi insert, set {@code reportId} sinh ra vào object {@code r}.
     *
     * @param r Report cần thêm (phải có title, reportType, severity)
     */
    public void add(Report r) {
        long userId       = AppSession.getInstance().getUserId();
        long restaurantId = AppSession.getInstance().getRestaurantId();

        String sql = "INSERT INTO reports" +
                     "  (title, description, report_type, severity, status," +
                     "   created_by, restaurant_id, created_at)" +
                     " VALUES (?, ?, ?, ?, 'OPEN', ?, ?, SYSTIMESTAMP)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"REPORT_ID"})) {

            ps.setString(1, r.getTitle());
            ps.setString(2, r.getDescription());
            ps.setString(3, toDbType(r.getReportType()));
            ps.setString(4, toDbSeverity(r.getSeverity()));
            ps.setLong(5, userId);
            ps.setLong(6, restaurantId);

            ps.executeUpdate();

            // Lấy generated key (report_id do sequence + trigger sinh ra)
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    r.setReportId(generatedKeys.getLong(1));
                }
            }

        } catch (Exception e) {
            System.err.println("[ReportDAO] add lỗi: " + e.getMessage());
            throw new RuntimeException("[ReportDAO] Không thể thêm báo cáo: " + e.getMessage(), e);
        }
    }

    /**
     * Cập nhật trạng thái báo cáo.
     * <p>
     * Chỉ Manager trở lên mới được phép gọi method này.
     * Khi chuyển sang {@code RESOLVED}, {@code resolved_at} được set thành SYSTIMESTAMP;
     * khi chuyển sang trạng thái khác, {@code resolved_at} bị xoá (NULL).
     *
     * @param reportId  ID báo cáo cần cập nhật
     * @param newStatus trạng thái mới
     * @throws SecurityException nếu không có quyền, hoặc báo cáo không thuộc nhà hàng hiện tại
     */
    public void updateStatus(long reportId, Status newStatus) {
        // ── Guard: chỉ manager trở lên ──────────────────────────────────────
        if (!RbacGuard.getInstance().isManagerOrAbove()) {
            throw new SecurityException(
                    "Chỉ quản lý trở lên mới được cập nhật trạng thái báo cáo");
        }

        long rid = AppSession.getInstance().getRestaurantId();
        boolean isSuperAdmin = RbacGuard.getInstance().isSuperAdmin();

        // SUPER_ADMIN không bị ràng buộc restaurant_id → cập nhật bất kỳ báo cáo nào
        String sql;
        if (isSuperAdmin) {
            sql = "UPDATE reports" +
                  "   SET status = ?," +
                  "       resolved_at = CASE WHEN ? = 'RESOLVED'" +
                  "                         THEN SYSTIMESTAMP" +
                  "                         ELSE NULL END" +
                  " WHERE report_id = ?";
        } else {
            sql = "UPDATE reports" +
                  "   SET status = ?," +
                  "       resolved_at = CASE WHEN ? = 'RESOLVED'" +
                  "                         THEN SYSTIMESTAMP" +
                  "                         ELSE NULL END" +
                  " WHERE report_id = ?" +
                  "   AND restaurant_id = ?";
        }

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String dbStatus = toDbStatus(newStatus);
            ps.setString(1, dbStatus);
            ps.setString(2, dbStatus);  // dùng lại cho CASE WHEN
            ps.setLong(3, reportId);
            if (!isSuperAdmin) {
                ps.setLong(4, rid);
            }

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected == 0) {
                throw new SecurityException(
                        "Báo cáo không tồn tại hoặc không thuộc nhà hàng này");
            }

        } catch (SecurityException e) {
            // Re-throw SecurityException không wrap để giữ đúng type
            throw e;
        } catch (Exception e) {
            System.err.println("[ReportDAO] updateStatus lỗi: " + e.getMessage());
            throw new RuntimeException("[ReportDAO] Không thể cập nhật trạng thái: " + e.getMessage(), e);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS — Enum ↔ DB string mapping
    // ═════════════════════════════════════════════════════════════════════════

    private String toDbType(ReportType t) {
        if (t == null) return "INCIDENT";
        return switch (t) {
            case INCIDENT    -> "INCIDENT";
            case MAINTENANCE -> "MAINTENANCE";
            case FEEDBACK    -> "FEEDBACK";
        };
    }

    private String toDbSeverity(Severity s) {
        if (s == null) return "LOW";
        return switch (s) {
            case LOW      -> "LOW";
            case MEDIUM   -> "MEDIUM";
            case HIGH     -> "HIGH";
            case CRITICAL -> "CRITICAL";
        };
    }

    private String toDbStatus(Status s) {
        if (s == null) return "OPEN";
        return switch (s) {
            case OPEN        -> "OPEN";
            case IN_PROGRESS -> "IN_PROGRESS";
            case RESOLVED    -> "RESOLVED";
            case CLOSED      -> "CLOSED";
        };
    }

    private ReportType fromDbType(String s) {
        if (s == null) return ReportType.INCIDENT;
        return switch (s.toUpperCase()) {
            case "MAINTENANCE" -> ReportType.MAINTENANCE;
            case "FEEDBACK"    -> ReportType.FEEDBACK;
            default            -> ReportType.INCIDENT;
        };
    }

    private Severity fromDbSeverity(String s) {
        if (s == null) return Severity.LOW;
        return switch (s.toUpperCase()) {
            case "MEDIUM"   -> Severity.MEDIUM;
            case "HIGH"     -> Severity.HIGH;
            case "CRITICAL" -> Severity.CRITICAL;
            default         -> Severity.LOW;
        };
    }

    private Status fromDbStatus(String s) {
        if (s == null) return Status.OPEN;
        return switch (s.toUpperCase()) {
            case "IN_PROGRESS" -> Status.IN_PROGRESS;
            case "RESOLVED"    -> Status.RESOLVED;
            case "CLOSED"      -> Status.CLOSED;
            default            -> Status.OPEN;
        };
    }

    /**
     * Map một hàng ResultSet → Report object.
     * Timestamp null (resolved_at chưa set) được xử lý an toàn → null LocalDateTime.
     */
    private Report map(ResultSet rs) {
        Report r = new Report();
        try {
            r.setReportId(rs.getLong("report_id"));
            r.setTitle(rs.getString("title"));
            r.setDescription(rs.getString("description"));
            r.setReportType(fromDbType(rs.getString("report_type")));
            r.setSeverity(fromDbSeverity(rs.getString("severity")));
            r.setStatus(fromDbStatus(rs.getString("status")));
            r.setCreatedBy(rs.getLong("created_by"));
            r.setRestaurantId(rs.getLong("restaurant_id"));

            Timestamp createdAt = rs.getTimestamp("created_at");
            r.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);

            Timestamp resolvedAt = rs.getTimestamp("resolved_at");
            r.setResolvedAt(resolvedAt != null ? resolvedAt.toLocalDateTime() : null);

        } catch (SQLException e) {
            System.err.println("[ReportDAO] map lỗi khi đọc ResultSet: " + e.getMessage());
            throw new RuntimeException("[ReportDAO] Lỗi map ResultSet → Report: " + e.getMessage(), e);
        }
        return r;
    }
}