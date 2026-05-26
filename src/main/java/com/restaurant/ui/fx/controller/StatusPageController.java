package com.restaurant.ui.fx.controller;

import com.restaurant.dao.TabletOrderDAO;
import com.restaurant.model.Order;
import com.restaurant.session.AppSession;
import com.restaurant.ui.TableOrderStage;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

public class StatusPageController extends BasePageController {

    @FXML private Label lblTableBadge;
    @FXML private Label lblTotal;
    @FXML private TableView<StatusRow> statusTable;
    @FXML private TableColumn<StatusRow, String> colSTT;
    @FXML private TableColumn<StatusRow, String> colName;
    @FXML private TableColumn<StatusRow, String> colStatus;
    @FXML private TableColumn<StatusRow, Void>   colAction;

    private final ObservableList<StatusRow> rows = FXCollections.observableArrayList();

    /**
     * Flag đánh dấu khách đang TỰ hủy món.
     * Khi true, nếu refreshTable() thấy toàn bộ món bị hủy thì hiện thông báo
     * "bạn đã hủy" — KHÔNG hiện cảnh báo "đơn bị hủy bởi nhân viên".
     */
    private boolean selfCancelInProgress = false;

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
        colName.setCellFactory(col -> new NameCell());

        colStatus.setCellValueFactory(c -> c.getValue().status);
        colStatus.setCellFactory(col -> new StatusCell());

        colAction.setCellFactory(col -> new CancelCell());

        statusTable.setItems(rows);
        statusTable.setPlaceholder(new Label("Chưa có món nào được gọi"));
    }

    @Override
    public void onNavigatedTo() {
        lblTableBadge.setText("Bàn " + stage.getTableName());
        refreshTable();
    }

    public void refreshTable() {
        stage.loadOrderItems(items -> {
            rows.clear();
            double total = 0;
            int i = 1;
            boolean hasNonCancelled = false;

            for (Order.OrderItem item : items) {
                boolean cancelled = item.getItemStatus() == Order.OrderItem.ItemStatus.CANCELLED;
                rows.add(new StatusRow(
                    i++,
                    item.getMenuItemName(),
                    mapStatus(item.getItemStatus()),
                    cancelled,
                    item.getOrderItemId(),
                    item.isCancellableByCustomer()
                ));
                if (!cancelled) {
                    total += item.getSubtotal();
                    hasNonCancelled = true;
                }
            }

            lblTotal.setText("Tổng cộng: " + fmt(total) + " đ");

            if (!items.isEmpty() && !hasNonCancelled) {
                if (selfCancelInProgress) {
                    // Khách tự hủy hết tất cả món
                    selfCancelInProgress = false;
                    Alert info = new Alert(Alert.AlertType.INFORMATION,
                        "Bạn đã hủy tất cả các món.\nVui lòng gọi thêm món mới hoặc liên hệ nhân viên.",
                        ButtonType.OK);
                    info.setTitle("Đã hủy tất cả món");
                    info.setHeaderText(null);
                    info.showAndWait();
                    stage.navigateTo(TableOrderStage.PAGE_MENU);
                } else {
                    // Đơn bị hủy bởi admin/nhân viên từ bên ngoài
                    stage.showCancelledAlertThenReset();
                }
            } else {
                selfCancelInProgress = false;
            }
        });
    }

    // ── Hủy từng món ──────────────────────────────────────────────────────────

    private void handleCancelItem(StatusRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận hủy món");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có muốn hủy món \"" + row.name.get() + "\" không?");
        confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;

            // ── FIX: Set flag TRƯỚC khi start thread ──────────────────────────
            // broadcastCancelEvent() chạy trên background thread; WS event có thể
            // arrive trên FX thread TRƯỚC setOnSucceeded. Nếu flag chưa set, WS
            // handler thấy selfCancelInProgress=false → hiện "nhân viên đã hủy" SAI.
            // Set flag ngay bây giờ (FX thread) → WS event luôn thấy flag=true.
            selfCancelInProgress = true;

            javafx.concurrent.Task<Boolean> task = new javafx.concurrent.Task<>() {
                @Override protected Boolean call() {
                    long restaurantId = AppSession.getInstance().getRestaurantId();
                    return new TabletOrderDAO(restaurantId).cancelOrderItem(row.orderItemId);
                }
            };
            task.setOnSucceeded(e -> {
                if (Boolean.TRUE.equals(task.getValue())) {
                    // Thông báo hủy thành công cho khách.
                    // Không gọi refreshTable() ở đây — WS event (ORDERS topic) từ
                    // broadcastCancelEvent() sẽ trigger refreshTable() qua setupWsSubscription.
                    // Gọi thêm sẽ tạo 2 refresh đồng thời và làm mất flag.
                    Alert ok = new Alert(Alert.AlertType.INFORMATION,
                        "Đã hủy món \"" + row.name.get() + "\" thành công.",
                        ButtonType.OK);
                    ok.setTitle("Hủy món thành công");
                    ok.setHeaderText(null);
                    ok.showAndWait();
                    // WS event đã được queue trong lúc alert mở (FX thread bị block bởi
                    // showAndWait) → sẽ chạy ngay sau khi alert đóng → refreshTable().
                } else {
                    // Hủy thất bại → reset flag để không chặn cảnh báo external cancel
                    selfCancelInProgress = false;
                    Alert err = new Alert(Alert.AlertType.WARNING,
                        "Không thể hủy món này vì bếp đã tiếp nhận.\n" +
                        "Vui lòng liên hệ nhân viên để được hỗ trợ.",
                        ButtonType.OK);
                    err.setHeaderText(null);
                    err.showAndWait();
                }
            });
            task.setOnFailed(e -> {
                // Task crash → reset flag
                selfCancelInProgress = false;
                System.err.println("[StatusPageController] cancelOrderItem lỗi: "
                        + task.getException().getMessage());
            });
            new Thread(task, "cancel-item").start();
        });
    }

    @FXML private void onBackToMenu()     { stage.navigateTo(TableOrderStage.PAGE_MENU);    }
    @FXML private void onRequestPayment() { stage.navigateTo(TableOrderStage.PAGE_PAYMENT); }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String mapStatus(Order.OrderItem.ItemStatus s) {
        if (s == null) return "Đang chờ";
        return switch (s) {
            case PENDING   -> "Đang chờ";
            case ACCEPTED  -> "Đã xác nhận";
            case COOKING   -> "Đang chế biến";
            case READY     -> "Đã chế biến";
            case DELIVERED -> "Đã nhận";
            case CANCELLED -> "Đã hủy";
        };
    }

    protected static String fmt(double v) {
        return java.text.NumberFormat
                .getInstance(new java.util.Locale("vi", "VN"))
                .format((long) v);
    }

    // ── Inner: StatusRow ──────────────────────────────────────────────────────

    public static class StatusRow {
        final SimpleStringProperty stt;
        final SimpleStringProperty name;
        final SimpleStringProperty status;
        final boolean cancelled;
        final String  orderItemId;
        final boolean cancellable;

        StatusRow(int stt, String name, String status,
                  boolean cancelled, String orderItemId, boolean cancellable) {
            this.stt         = new SimpleStringProperty(String.valueOf(stt));
            this.name        = new SimpleStringProperty(name);
            this.status      = new SimpleStringProperty(status);
            this.cancelled   = cancelled;
            this.orderItemId = orderItemId;
            this.cancellable = cancellable;
        }
    }

    // ── Inner: NameCell ───────────────────────────────────────────────────────

    private static class NameCell extends TableCell<StatusRow, String> {
        @Override
        protected void updateItem(String val, boolean empty) {
            super.updateItem(val, empty);
            if (empty || val == null) { setText(null); setStyle(""); return; }
            setText(val);
            StatusRow row = getTableRow() != null ? (StatusRow) getTableRow().getItem() : null;
            if (row != null && row.cancelled)
                setStyle("-fx-text-fill: #9CA3AF; -fx-strikethrough: true;");
            else
                setStyle("");
        }
    }

    // ── Inner: StatusCell ─────────────────────────────────────────────────────

    private static class StatusCell extends TableCell<StatusRow, String> {
        StatusCell() { setAlignment(Pos.CENTER); }
        @Override
        protected void updateItem(String val, boolean empty) {
            super.updateItem(val, empty);
            if (empty || val == null) { setText(null); setStyle(""); return; }
            setText(val);
            String style = "-fx-font-weight: ";
            style += switch (val) {
                case "Đang chờ"      -> "NORMAL; -fx-text-fill: #6B7280;";
                case "Đã xác nhận"   -> "BOLD;   -fx-text-fill: #3B82F6;";
                case "Đang chế biến" -> "BOLD;   -fx-text-fill: #F59E0B;";
                case "Đã chế biến"   -> "BOLD;   -fx-text-fill: #10B981;";
                case "Đã nhận"       -> "NORMAL; -fx-text-fill: #9CA3AF;";
                case "Đã hủy"        -> "BOLD;   -fx-text-fill: #EF4444;";
                default              -> "NORMAL; -fx-text-fill: #374151;";
            };
            setStyle(style);
        }
    }

    // ── Inner: CancelCell ─────────────────────────────────────────────────────

    private class CancelCell extends TableCell<StatusRow, Void> {
        private final Button btn = new Button("✕ Hủy món");

        CancelCell() {
            btn.setStyle(
                "-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626;" +
                "-fx-font-weight: bold; -fx-background-radius: 6;" +
                "-fx-padding: 4 10 4 10; -fx-cursor: hand;"
            );
            btn.setOnAction(e -> {
                StatusRow row = getTableRow().getItem();
                if (row != null) handleCancelItem(row);
            });
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) { setGraphic(null); return; }
            StatusRow row = getTableRow() != null ? (StatusRow) getTableRow().getItem() : null;
            if (row != null && row.cancellable) {
                HBox box = new HBox(btn);
                box.setAlignment(Pos.CENTER);
                setGraphic(box);
            } else {
                setGraphic(null);
            }
        }
    }
}