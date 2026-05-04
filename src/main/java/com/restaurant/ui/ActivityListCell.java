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
 * Redesigned ActivityListCell — cleaner rows with colour-coded dot indicator
 * and separated timestamp label.
 */
public class ActivityListCell extends ListCell<String> {

    private static final Color DOT_GREEN = Color.web("#10B981");
    private static final Color DOT_BLUE  = Color.web("#3B82F6");
    private static final Color DOT_AMBER = Color.web("#F59E0B");
    private static final Color DOT_RED   = Color.web("#EF4444");

    private final HBox   container;
    private final Circle dot;
    private final Label  timeLabel;
    private final Label  textLabel;

    public ActivityListCell() {
        dot = new Circle(4.5, DOT_GREEN);
        dot.setMouseTransparent(true);

        timeLabel = new Label();
        timeLabel.setMinWidth(38);
        timeLabel.setPrefWidth(38);
        timeLabel.setStyle(
            "-fx-font-family: 'Segoe UI';" +
            "-fx-font-size: 11.5;" +
            "-fx-text-fill: #94A3B8;");

        textLabel = new Label();
        textLabel.setStyle(
            "-fx-font-family: 'Segoe UI';" +
            "-fx-font-size: 13;" +
            "-fx-text-fill: #334155;");
        textLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textLabel, Priority.ALWAYS);

        container = new HBox(12, dot, timeLabel, textLabel);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(0, 16, 0, 18));
        container.setPrefHeight(46);
        container.setMinHeight(46);
        container.setMouseTransparent(true);

        setPadding(Insets.EMPTY);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
            setStyle("-fx-background-color: transparent;");
            return;
        }

        // Extract "HH:mm" prefix if present
        String time = "";
        String text = item;
        if (item.length() >= 5 && item.charAt(2) == ':') {
            time = item.substring(0, 5);
            text = item.substring(5).trim();
        }
        timeLabel.setText(time);
        textLabel.setText(text);
        setText(null);

        // Dot colour by content
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

        String sep = "-fx-border-color: transparent transparent #F1F5F9 transparent;" +
                     "-fx-border-width: 0 0 1 0;";
        if (isSelected()) {
            textLabel.setStyle(
                "-fx-font-family: 'Segoe UI';" +
                "-fx-font-size: 13;" +
                "-fx-text-fill: #1D4ED8;");
            setStyle("-fx-background-color: #EFF6FF;" + sep);
        } else {
            textLabel.setStyle(
                "-fx-font-family: 'Segoe UI';" +
                "-fx-font-size: 13;" +
                "-fx-text-fill: #334155;");
            setStyle((getIndex() % 2 == 0
                    ? "-fx-background-color: white;"
                    : "-fx-background-color: #F8FAFC;") + sep);
        }

        setGraphic(container);
    }
}
