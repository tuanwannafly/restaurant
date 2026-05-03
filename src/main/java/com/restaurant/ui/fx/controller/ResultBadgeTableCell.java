package com.restaurant.ui.fx.controller;

// 📁 VỊ TRÍ: src/main/java/com/restaurant/ui/fx/controller/ResultBadgeTableCell.java

import com.restaurant.session.AuditLogger.AuditEntry;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.StackPane;

/**
 * TableCell hiển thị cột "Kết quả" dưới dạng pill badge có màu nền.
 *
 * <pre>
 *   SUCCESS → nền xanh  (#D1FAE5), chữ đậm (#065F46)
 *   FAIL    → nền đỏ   (#FEE2E2), chữ đậm (#991B1B)
 *   LOCKED  → nền vàng (#FEF3C7), chữ đậm (#92400E)
 * </pre>
 *
 * <p><b>Cách dùng trong controller:</b>
 * <pre>{@code
 *   colResult.setCellFactory(col -> new ResultBadgeTableCell());
 * }</pre>
 */
public class ResultBadgeTableCell extends TableCell<AuditEntry, String> {

    // ── Badge config ──────────────────────────────────────────────────────────

    private static final record BadgeStyle(String text, String bg, String fg) {}

    private static BadgeStyle styleFor(String result) {
        if (result == null) return new BadgeStyle("—", "#F3F4F6", "#374151");
        return switch (result) {
            case "SUCCESS" -> new BadgeStyle("✓ SUCCESS", "#D1FAE5", "#065F46");
            case "FAIL"    -> new BadgeStyle("✗ FAIL",    "#FEE2E2", "#991B1B");
            case "LOCKED"  -> new BadgeStyle("⚠ LOCKED",  "#FEF3C7", "#92400E");
            default        -> new BadgeStyle(result,      "#F3F4F6", "#374151");
        };
    }

    // ── UI nodes (tái dùng để tránh tạo lại mỗi lần render) ─────────────────

    private final Label     badge;
    private final StackPane pill;

    public ResultBadgeTableCell() {
        badge = new Label();
        badge.setStyle(
            "-fx-font-family: 'Segoe UI'; " +
            "-fx-font-size: 12px; " +
            "-fx-font-weight: bold;"
        );

        pill = new StackPane(badge);
        pill.setPadding(new Insets(3, 10, 3, 10));
        // Bo tròn pill qua -fx-background-radius
        pill.setMaxWidth(120);
        pill.setAlignment(Pos.CENTER);

        setAlignment(Pos.CENTER);
        setGraphic(null);
    }

    // ── updateItem ────────────────────────────────────────────────────────────

    @Override
    protected void updateItem(String value, boolean empty) {
        super.updateItem(value, empty);

        if (empty || value == null) {
            setGraphic(null);
            setText(null);
            return;
        }

        BadgeStyle style = styleFor(value);

        badge.setText(style.text());
        badge.setStyle(
            "-fx-font-family: 'Segoe UI'; " +
            "-fx-font-size: 12px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: " + style.fg() + ";"
        );

        pill.setStyle(
            "-fx-background-color: " + style.bg() + "; " +
            "-fx-background-radius: 10px;"
        );

        setText(null);
        setGraphic(pill);
    }
}
