package com.restaurant.ui.fx.controller;

import com.restaurant.model.Order;
import com.restaurant.ui.TableOrderStage;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;

import java.util.List;

/**
 * StatusPageController — Phase 15.
 *
 * <p>Hiển thị trạng thái từng món của đơn hiện tại.
 * Auto-refresh mỗi 5s qua {@link com.restaurant.ui.PollManagerFx}.
 */
public class StatusPageController extends BasePageController {

    @FXML private Label lblTableBadge;
    @FXML private Label lblTotal;
    @FXML private TableView<StatusRow> statusTable;
    @FXML private TableColumn<StatusRow, String> colSTT;
    @FXML private TableColumn<StatusRow, String> colName;
    @FXML private TableColumn<StatusRow, String> colStatus;

    private final ObservableList<StatusRow> rows = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        colSTT.setCellValueFactory(c -> c.getValue().stt);
        colSTT.setCellFactory(col -> {
            TableCell<StatusRow, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setAlignment(Pos.CENTER);
            return cell;
        });

        colName.setCellValueFactory(c -> c.getValue().name);

        colStatus.setCellValueFactory(c -> c.getValue().status);
        colStatus.setCellFactory(col -> new StatusCell());


        statusTable.setItems(rows);
        statusTable.setPlaceholder(new Label("Chưa có món nào được gọi"));
    }

    @Override
    public void onNavigatedTo() {
        lblTableBadge.setText("Bàn " + stage.getTableName());
        refreshTable();
    }

    /** Gọi bởi PollManagerFx mỗi 5s khi đang ở status page. */
    public void refreshTable() {
        stage.loadOrderItems(items -> {
            rows.clear();
            double total = 0;
            int i = 1;
            for (Order.OrderItem item : items) {
                rows.add(new StatusRow(i++, item.getMenuItemName(),
                        mapStatus(item.getItemStatus())));
                total += item.getSubtotal();
            }
            lblTotal.setText("Tổng cộng: " + fmt(total) + " đ");
        });
    }

    @FXML private void onBackToMenu()    { stage.navigateTo(TableOrderStage.PAGE_MENU);    }
    @FXML private void onRequestPayment(){ stage.navigateTo(TableOrderStage.PAGE_PAYMENT); }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String mapStatus(Order.OrderItem.ItemStatus s) {
        if (s == null) return "Đang chờ";
        return switch (s) {
            case PENDING    -> "Đang chờ";
            case ACCEPTED,
                 COOKING    -> "Đang chế biến";
            case READY      -> "Đã chế biến";
            case DELIVERING -> "Đang mang lên";
            case DELIVERED  -> "Đã nhận";
            default         -> "Đang chờ";
        };
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    public static class StatusRow {
        final SimpleStringProperty stt;
        final SimpleStringProperty name;
        final SimpleStringProperty status;

        StatusRow(int stt, String name, String status) {
            this.stt    = new SimpleStringProperty(String.valueOf(stt));
            this.name   = new SimpleStringProperty(name);
            this.status = new SimpleStringProperty(status);
        }
    }

    // ── Status cell with color coding ─────────────────────────────────────────

    private static class StatusCell extends TableCell<StatusRow, String> {
        StatusCell() { setAlignment(Pos.CENTER); }
        @Override
        protected void updateItem(String val, boolean empty) {
            super.updateItem(val, empty);
            if (empty || val == null) { setText(null); setStyle(""); return; }
            setText(val);
            String style = "-fx-font-weight: ";
            style += switch (val) {
                case "Đang chờ"       -> "NORMAL; -fx-text-fill: #6B7280;";
                case "Đang chế biến"  -> "BOLD;   -fx-text-fill: #F59E0B;";
                case "Đã chế biến"    -> "BOLD;   -fx-text-fill: #10B981;";
                case "Đang mang lên"  -> "BOLD;   -fx-text-fill: #3B82F6;";
                case "Đã nhận"        -> "NORMAL; -fx-text-fill: #9CA3AF;";
                default               -> "NORMAL; -fx-text-fill: #374151;";
            };
            setStyle(style);
        }
    }
}
