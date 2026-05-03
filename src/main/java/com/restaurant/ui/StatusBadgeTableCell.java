package com.restaurant.ui;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;

/**
 * TableCell that renders the order status as a colored badge label.
 * <p>
 * Usage in controller:
 * <pre>
 *   colStatus.setCellFactory(col -> new StatusBadgeTableCell<>());
 * </pre>
 *
 * @param <S> TableView row type
 */
public class StatusBadgeTableCell<S> extends TableCell<S, String> {

    // CSS colour classes — defined in style.css
    private static final String STYLE_SERVING   = "badge-warning";   // yellow
    private static final String STYLE_DONE      = "badge-success";   // green
    private static final String STYLE_CANCELLED = "badge-danger";    // red
    private static final String STYLE_PENDING   = "badge-neutral";   // grey
    private static final String STYLE_COOKING   = "badge-info";      // blue

    private final Label badge = new Label();
    private final HBox  box   = new HBox(badge);

    public StatusBadgeTableCell() {
        badge.getStyleClass().add("status-badge");
        box.setStyle("-fx-alignment: center;");
        setStyle("-fx-alignment: center; -fx-padding: 4 0;");
    }

    @Override
    protected void updateItem(String status, boolean empty) {
        super.updateItem(status, empty);
        if (empty || status == null) {
            setGraphic(null);
            return;
        }

        // Clear previous colour classes
        badge.getStyleClass().removeAll(
            STYLE_SERVING, STYLE_DONE, STYLE_CANCELLED, STYLE_PENDING, STYLE_COOKING
        );

        badge.setText(status);

        switch (status) {
            case "Đang phục vụ":
                badge.getStyleClass().add(STYLE_SERVING);
                break;
            case "Hoàn thành":
            case "COMPLETED":
            case "DELIVERED":
                badge.getStyleClass().add(STYLE_DONE);
                break;
            case "Đã hủy":
            case "CANCELLED":
                badge.getStyleClass().add(STYLE_CANCELLED);
                break;
            case "Đang nấu":
            case "COOKING":
            case "ACCEPTED":
            case "READY":
                badge.getStyleClass().add(STYLE_COOKING);
                break;
            default:
                badge.getStyleClass().add(STYLE_PENDING);
                break;
        }

        setGraphic(box);
    }
}