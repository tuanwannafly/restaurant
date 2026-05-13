package com.restaurant.ui.cell;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.function.Consumer;

/**
 * ActionTableCell — Phase 4 (JavaFX)
 *
 * <p>A generic, reusable {@link TableCell} that renders three pill-style
 * action buttons inside a table row:
 *
 * <pre>
 *   [ 🗑 Xóa ]  |  [ ✏ Cập nhật ]  |  [ 👁 Xem ]
 * </pre>
 *
 * <p>Buttons are individually shown/hidden based on the {@code canDelete} and
 * {@code canEdit} flags, so the same cell works for all RBAC scenarios.
 *
 * <p>Usage example (in a controller):
 * <pre>{@code
 *   colActions.setCellFactory(col -> new ActionTableCell<>(
 *       canDelete, canEdit,
 *       this::handleDelete,
 *       this::openEditDialog,
 *       this::showDetail
 *   ));
 * }</pre>
 *
 * @param <T> the row type (e.g. {@code MenuItem})
 */
public class ActionTableCell<T> extends TableCell<T, Void> {

    // ── Pill buttons ──────────────────────────────────────────────────────────
    private final Button btnDelete = pill("🗑  Xóa",        "#EF4444", "#FEF2F2");
    private final Button btnEdit   = pill("✏  Cập nhật",   "#3B82F6", "#EFF6FF");
    private final Button btnView   = pill("👁  Xem",        "#6366F1", "#EEF2FF");

    private final HBox container;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param canDelete    whether the delete button should be shown
     * @param canEdit      whether the edit button should be shown
     * @param onDelete     callback invoked with the row item on delete click
     * @param onEdit       callback invoked with the row item on edit click
     * @param onView       callback invoked with the row item on view click
     */
    public ActionTableCell(
            boolean canDelete,
            boolean canEdit,
            Consumer<T> onDelete,
            Consumer<T> onEdit,
            Consumer<T> onView) {

        // Wire up action handlers
        btnDelete.setOnAction(e -> {
            T item = getTableRow().getItem();
            if (item != null) onDelete.accept(item);
        });
        btnEdit.setOnAction(e -> {
            T item = getTableRow().getItem();
            if (item != null) onEdit.accept(item);
        });
        btnView.setOnAction(e -> {
            T item = getTableRow().getItem();
            if (item != null) onView.accept(item);
        });

        // RBAC visibility
        btnDelete.setVisible(canDelete);
        btnDelete.setManaged(canDelete);
        btnEdit  .setVisible(canEdit);
        btnEdit  .setManaged(canEdit);

        // Build container with optional separators between visible pills
        container = new HBox(6);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(4, 8, 4, 8));

        if (canDelete) {
            container.getChildren().add(btnDelete);
            container.getChildren().add(separator());
        }
        if (canEdit) {
            container.getChildren().add(btnEdit);
            container.getChildren().add(separator());
        }
        container.getChildren().add(btnView);
    }

    // ── TableCell lifecycle ───────────────────────────────────────────────────

    @Override
    protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || getTableRow() == null || getTableRow().getItem() == null) {
            setGraphic(null);
        } else {
            setGraphic(container);
        }
        setText(null);
        setPadding(Insets.EMPTY);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a styled pill button.
     *
     * @param text    label shown on the button
     * @param fgHex   foreground / text + border colour (hex)
     * @param bgHex   background colour (hex, semi-transparent tint)
     */
    private static Button pill(String text, String fgHex, String bgHex) {
        Button btn = new Button(text);
        btn.setStyle(String.format(
            "-fx-background-color: %s;" +
            "-fx-text-fill: %s;" +
            "-fx-border-color: %s;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 20;" +
            "-fx-background-radius: 20;" +
            "-fx-font-size: 11px;" +
            "-fx-padding: 4 10 4 10;" +
            "-fx-cursor: hand;",
            bgHex, fgHex, fgHex));

        // Hover effect: slightly darken background
        btn.setOnMouseEntered(e -> btn.setOpacity(0.80));
        btn.setOnMouseExited (e -> btn.setOpacity(1.00));

        return btn;
    }

    /** A thin vertical separator between pills. */
    private static Region separator() {
        Region sep = new Region();
        sep.setPrefWidth(1);
        sep.setPrefHeight(16);
        sep.setStyle("-fx-background-color: #E2E8F0;");
        return sep;
    }
}