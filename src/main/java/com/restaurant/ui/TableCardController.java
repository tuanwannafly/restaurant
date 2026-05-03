package com.restaurant.ui;

import java.io.IOException;
import java.util.function.Consumer;

import com.restaurant.model.TableItem;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * TableCardController — Phase 5
 *
 * <p>Custom component (fx:root pattern) hiển thị thông tin một bàn dưới dạng card.
 * TableController tạo instance bằng cách load {@code TableCardComponent.fxml},
 * sau đó gọi {@link #setData(TableItem)} và các setter callback.
 *
 * <p>Màu status bar:
 * <ul>
 *   <li>RANH      → #2ECC71 (xanh lá)</li>
 *   <li>BAN        → #E74C3C (đỏ)</li>
 *   <li>DAT_TRUOC  → #3498DB (xanh dương)</li>
 *   <li>DIRTY      → #E67E22 (cam)</li>
 *   <li>CLEANING   → #F1C40F (vàng)</li>
 * </ul>
 */
public class TableCardController extends VBox {

    // ─── FXML fields ──────────────────────────────────────────────────────────

    @FXML private HBox  statusBar;
    @FXML private Label lblName;
    @FXML private Label lblCapacity;
    @FXML private Label lblStatus;
    @FXML private Button btnDelete;
    @FXML private Button btnEdit;
    @FXML private Button btnDetail;

    // ─── State ────────────────────────────────────────────────────────────────

    private TableItem item;

    // ─── Callbacks from TableController ──────────────────────────────────────

    private Consumer<TableItem> onDelete;
    private Consumer<TableItem> onEdit;
    private Consumer<TableItem> onDetail;
    private Consumer<TableItem> onDoubleClick;

    // ─── Constructor (fx:root) ────────────────────────────────────────────────

    public TableCardController() {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/TableCardComponent.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException ex) {
            throw new RuntimeException("Không load được TableCardComponent.fxml", ex);
        }
    }

    // ─── Data binding ─────────────────────────────────────────────────────────

    /**
     * Truyền {@link TableItem} vào card và cập nhật tất cả UI labels/colors.
     *
     * @param tableItem bàn cần hiển thị (non-null)
     */
    public void setData(TableItem tableItem) {
        this.item = tableItem;

        lblName.setText(tableItem.getName());
        lblCapacity.setText(tableItem.getCapacity() + " người");
        lblStatus.setText(tableItem.getStatusDisplay());

        applyStatusStyle(tableItem.getStatus());
        applyRbac();
    }

    // ─── Styling ─────────────────────────────────────────────────────────────

    private void applyStatusStyle(TableItem.Status status) {
        String hex;
        String badgeStyle;

        switch (status) {
            case RANH:
                hex = "#2ECC71";
                badgeStyle = "-fx-background-color: #D5F5E3; -fx-text-fill: #1A9641;";
                break;
            case BAN:
                hex = "#E74C3C";
                badgeStyle = "-fx-background-color: #FADBD8; -fx-text-fill: #C0392B;";
                break;
            case DAT_TRUOC:
                hex = "#3498DB";
                badgeStyle = "-fx-background-color: #D6EAF8; -fx-text-fill: #1A6EA8;";
                break;
            case DIRTY:
                hex = "#E67E22";
                badgeStyle = "-fx-background-color: #FAE5D3; -fx-text-fill: #CA6F1E;";
                break;
            case CLEANING:
                hex = "#F1C40F";
                badgeStyle = "-fx-background-color: #FEF9E7; -fx-text-fill: #B7950B;";
                break;
            default:
                hex = "#9E9E9E";
                badgeStyle = "-fx-background-color: #ECEFF1; -fx-text-fill: #546E7A;";
                break;
        }

        // Status bar top accent
        statusBar.setStyle("-fx-background-color: " + hex + ";");

        // Status badge pill
        lblStatus.setStyle(badgeStyle
                + " -fx-background-radius: 12;"
                + " -fx-padding: 2 10 2 10;"
                + " -fx-font-size: 11px;"
                + " -fx-font-weight: bold;");
    }

    /** Ẩn nút Xóa/Sửa nếu user không có quyền tương ứng. */
    private void applyRbac() {
        boolean canDelete = RbacGuard.getInstance().can(Permission.DELETE_TABLE);
        boolean canEdit   = RbacGuard.getInstance().can(Permission.EDIT_TABLE);

        btnDelete.setVisible(canDelete);
        btnDelete.setManaged(canDelete);
        btnEdit.setVisible(canEdit);
        btnEdit.setManaged(canEdit);
    }

    // ─── FXML handlers ────────────────────────────────────────────────────────

    @FXML
    private void onCardClicked(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY
                && e.getClickCount() == 2
                && onDoubleClick != null) {
            onDoubleClick.accept(item);
        }
    }

    @FXML
    private void onDelete(javafx.event.ActionEvent e) {
        e.consume();            // không bubble lên card click
        if (onDelete != null) onDelete.accept(item);
    }

    @FXML
    private void onEdit(javafx.event.ActionEvent e) {
        e.consume();
        if (onEdit != null) onEdit.accept(item);
    }

    @FXML
    private void onDetail(javafx.event.ActionEvent e) {
        e.consume();
        if (onDetail != null) onDetail.accept(item);
    }

    // ─── Callback setters ─────────────────────────────────────────────────────

    public void setOnDelete(Consumer<TableItem> callback)      { this.onDelete      = callback; }
    public void setOnEdit(Consumer<TableItem> callback)        { this.onEdit        = callback; }
    public void setOnDetail(Consumer<TableItem> callback)      { this.onDetail      = callback; }
    public void setOnDoubleClick(Consumer<TableItem> callback) { this.onDoubleClick = callback; }

    // ─── Getter ───────────────────────────────────────────────────────────────

    public TableItem getItem() { return item; }
}