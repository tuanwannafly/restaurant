package com.restaurant.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Custom {@link ListCell} for the HomePanel recent-activity list.
 *
 * <p>Visual anatomy per row:
 * <pre>
 *  ┌──────────────────────────────────────────────────────────────┐
 *  │  ●  HH:mm  Mô tả hoạt động...                               │  ← 38 px high
 *  ├──────────────────────────────────────────────────────────────┤
 * </pre>
 *
 * <p>Row backgrounds alternate white / #F9FAFB; selected rows use #EFF6FF.
 * The coloured dot reflects the content category:
 * <ul>
 *   <li>Green  (#10B981) — restaurant / system status</li>
 *   <li>Emerald (#10B981) — new registrations</li>
 *   <li>Blue   (#3B82F6) — order counts</li>
 *   <li>Amber  (#F59E0B) — revenue figures</li>
 * </ul>
 */
public class ActivityListCell extends ListCell<String> {

    // ── Row style constants ────────────────────────────────────────────────

    private static final String S_EVEN = "-fx-background-color: white;";
    private static final String S_ODD  = "-fx-background-color: #F9FAFB;";
    private static final String S_SEL  = "-fx-background-color: #EFF6FF;";
    // Bottom separator shared by all states
    private static final String S_SEP  =
            "-fx-border-color: transparent transparent #EEEEEE transparent;" +
            "-fx-border-width: 0 0 1 0;";

    // ── Dot colour constants ───────────────────────────────────────────────

    private static final Color DOT_GREEN = Color.web("#10B981");
    private static final Color DOT_BLUE  = Color.web("#3B82F6");
    private static final Color DOT_AMBER = Color.web("#F59E0B");
    private static final Color DOT_RED   = Color.web("#EF4444");

    // ── Reusable node graph ────────────────────────────────────────────────

    private final HBox   container;
    private final Circle dot;
    private final Label  textLabel;

    // =========================================================================
    // Constructor — node graph built once, reused across cell recycling
    // =========================================================================

    public ActivityListCell() {
        dot = new Circle(4, DOT_GREEN);
        dot.setMouseTransparent(true);

        textLabel = new Label();
        textLabel.setStyle(
                "-fx-font-family: 'Segoe UI';" +
                "-fx-font-size: 13;" +
                "-fx-text-fill: #374151;");
        textLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textLabel, Priority.ALWAYS);

        container = new HBox(10, dot, textLabel);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(0, 10, 0, 14));
        container.setPrefHeight(38);
        container.setMinHeight(38);
        container.setMouseTransparent(true);    // let the cell handle mouse events

        // Remove default ListCell padding / background so container controls it
        setPadding(Insets.EMPTY);
    }

    // =========================================================================
    // updateItem — called by VirtualFlow on every cell population
    // =========================================================================

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
            setStyle("-fx-background-color: transparent;");
            return;
        }

        // ── Text ─────────────────────────────────────────────────────────
        textLabel.setText(item);
        setText(null);  // graphic-only cell

        // ── Dot colour by content category ───────────────────────────────
        String lower = item.toLowerCase();
        if (lower.contains("doanh thu")) {
            dot.setFill(DOT_AMBER);
        } else if (lower.contains("đơn hàng") || lower.contains("don hang")) {
            dot.setFill(DOT_BLUE);
        } else if (lower.contains("mới") || lower.contains("đăng ký")) {
            dot.setFill(DOT_GREEN);
        } else if (lower.contains("lỗi") || lower.contains("cảnh báo")) {
            dot.setFill(DOT_RED);
        } else {
            dot.setFill(DOT_GREEN);
        }

        // ── Row background ────────────────────────────────────────────────
        if (isSelected()) {
            textLabel.setStyle(
                    "-fx-font-family: 'Segoe UI';" +
                    "-fx-font-size: 13;" +
                    "-fx-text-fill: #1D4ED8;");
            setStyle(S_SEL + S_SEP);
        } else {
            textLabel.setStyle(
                    "-fx-font-family: 'Segoe UI';" +
                    "-fx-font-size: 13;" +
                    "-fx-text-fill: #374151;");
            setStyle((getIndex() % 2 == 0 ? S_EVEN : S_ODD) + S_SEP);
        }

        setGraphic(container);
    }
}