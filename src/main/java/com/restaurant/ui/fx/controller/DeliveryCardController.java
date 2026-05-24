package com.restaurant.ui.fx.controller;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.model.Order;
import com.restaurant.session.AppSession;
import com.restaurant.websocket.RestaurantEventServer;
import com.restaurant.websocket.WsEvent;
import com.restaurant.websocket.WsTopic;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Controller cho DeliveryCardComponent.fxml — Phase 9 JavaFX.
 *
 * <p>Được load động trong {@link WaiterController#rebuildDeliveryCards(java.util.Map)}.
 * {@link #setData(List, KitchenDAO, Runnable)} phải được gọi ngay sau khi load.
 *
 * <p>Luồng trạng thái hợp lệ theo constraint DB (CHK_ITEM_ORD_STATUS):
 * PENDING → ACCEPTED → COOKING → READY → DELIVERED (hoặc CANCELLED).
 * Không có trạng thái DELIVERING — nút "Đã giao xong" chuyển thẳng READY → DELIVERED.
 *
 * Đặt vào: {@code src/main/java/com/restaurant/ui/fx/controller/DeliveryCardController.java}
 */
public class DeliveryCardController implements Initializable {

    // ─── FXML injections ──────────────────────────────────────────────────────

    @FXML private VBox   cardRoot;
    @FXML private Label  lblCardTitle;
    @FXML private VBox   itemsBox;
    @FXML private Button btnDelivered;

    // ─── State ────────────────────────────────────────────────────────────────

    private List<KitchenDAO.KitchenTicket> tickets;
    private KitchenDAO                     kitchenDAO;
    private Runnable                       onRefresh;

    /** Executor riêng cho card – daemon để tránh block JVM shutdown */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "card-exec");
        t.setDaemon(true);
        return t;
    });

    // ─── initialize ───────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cardRoot.setOnMouseEntered(e -> cardRoot.getStyleClass().add("delivery-card-hover"));
        cardRoot.setOnMouseExited(e  -> cardRoot.getStyleClass().remove("delivery-card-hover"));
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Gán dữ liệu cho card và render nội dung.
     *
     * @param tickets    danh sách tickets trong cùng (tableId, roundNumber)
     * @param kitchenDAO DAO để updateItemStatus
     * @param onRefresh  callback gọi sau khi cập nhật (thường là {@code loadData()})
     */
    public void setData(List<KitchenDAO.KitchenTicket> tickets,
                        KitchenDAO kitchenDAO,
                        Runnable   onRefresh) {
        this.tickets    = tickets;
        this.kitchenDAO = kitchenDAO;
        this.onRefresh  = onRefresh;

        render();
    }

    // ─── Render ───────────────────────────────────────────────────────────────

    private void render() {
        if (tickets == null || tickets.isEmpty()) return;

        KitchenDAO.KitchenTicket first = tickets.get(0);

        // Tiêu đề
        lblCardTitle.setText(
                "Bàn " + first.tableName + "  ·  Lượt " + first.roundNumber);

        // Item rows
        itemsBox.getChildren().clear();
        for (KitchenDAO.KitchenTicket t : tickets) {
            itemsBox.getChildren().add(buildItemRow(t));
        }

        // Disable nút nếu tất cả đã DELIVERED
        boolean allDelivered = tickets.stream().allMatch(
                t -> t.itemStatus == Order.OrderItem.ItemStatus.DELIVERED);
        btnDelivered.setDisable(allDelivered);

        // Điều chỉnh chiều cao card theo số món
        double height = 46 + tickets.size() * 34.0 + 44;
        cardRoot.setPrefHeight(height);
    }

    // ─── Item row factory ─────────────────────────────────────────────────────

    private HBox buildItemRow(KitchenDAO.KitchenTicket t) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        Label nameQty = new Label(t.itemName + " × " + t.quantity);
        nameQty.getStyleClass().add("item-name-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = makeBadge(t.itemStatus);

        row.getChildren().addAll(nameQty, spacer, badge);
        return row;
    }

    // ─── Badge factory ────────────────────────────────────────────────────────

    private Label makeBadge(Order.OrderItem.ItemStatus status) {
        Label badge = new Label();
        badge.getStyleClass().add("item-badge");
        badge.setPadding(new Insets(2, 8, 2, 8));

        switch (status) {
            case READY -> {
                badge.setText("Sẵn sàng");
                badge.getStyleClass().add("badge-ready");
            }
            case DELIVERED -> {
                badge.setText("Đã giao");
                badge.getStyleClass().add("badge-delivered");
            }
            default -> {
                badge.setText("Sẵn sàng");
                badge.getStyleClass().add("badge-ready");
            }
        }
        return badge;
    }

    // ─── Button handler ───────────────────────────────────────────────────────

    /**
     * "✔ Đã giao xong" — chuyển tất cả món READY → DELIVERED.
     * Đây là bước duy nhất: constraint DB (CHK_ITEM_ORD_STATUS) không có DELIVERING.
     */
    @FXML
    private void onDelivered() {
        btnDelivered.setDisable(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (KitchenDAO.KitchenTicket t : tickets) {
                    if (t.itemStatus != Order.OrderItem.ItemStatus.DELIVERED) {
                        kitchenDAO.updateItemStatus(
                                t.itemId, Order.OrderItem.ItemStatus.DELIVERED);
                    }
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            broadcastDelivery(tickets);
            onRefresh.run();
        });
        task.setOnFailed(e   -> {
            btnDelivered.setDisable(false);
            showCardError("Lỗi cập nhật: " + task.getException().getMessage());
        });
        executor.submit(task);
    }

    // ─── Broadcast WS sau khi giao xong ──────────────────────────────────────

    /**
     * Broadcast WebSocket events sau khi phục vụ đánh dấu "Đã giao xong".
     *
     * <p>Học theo cơ chế {@code KitchenController.broadcastStatusChange()}:
     * gửi {@link WsTopic#ORDERS} và {@link WsTopic#BADGE} cho toàn nhà hàng,
     * cộng thêm topic riêng từng bàn bị ảnh hưởng ({@code WsTopic.forTable(id)}).
     *
     * <p>Nhờ đó {@link com.restaurant.ui.TableOrderStage#setupWsSubscription()}
     * sẽ nhận event và dispatch đến {@link StatusPageController#refreshTable()}
     * để màn hình tablet cập nhật ngay trạng thái "Đã nhận" mà không cần chờ
     * fallback poll.</p>
     *
     * @param tickets danh sách ticket vừa được chuyển sang DELIVERED
     */
    private void broadcastDelivery(List<KitchenDAO.KitchenTicket> tickets) {
        long restaurantId = AppSession.getInstance().getRestaurantId();
        RestaurantEventServer srv = RestaurantEventServer.getInstance();

        // Broadcast ORDERS — TableOrderStage subscribe topic này để refresh
        srv.broadcast(WsEvent.of(WsTopic.ORDERS, restaurantId));
        srv.broadcast(WsEvent.of(WsTopic.BADGE,  restaurantId));

        // Broadcast topic riêng từng bàn bị ảnh hưởng để refresh nhanh hơn
        if (tickets != null) {
            tickets.stream()
                   .map(t -> t.tableId)
                   .distinct()
                   .forEach(tid -> {
                       try {
                           int tableIdInt = Integer.parseInt(tid);
                           srv.broadcast(WsEvent.of(WsTopic.forTable(tableIdInt), restaurantId));
                       } catch (NumberFormatException ignored) {}
                   });
        }
    }

    // ─── Error helper ─────────────────────────────────────────────────────────

    private void showCardError(String msg) {
        Label err = new Label("⚠ " + msg);
        err.getStyleClass().add("card-error-label");
        err.setWrapText(true);
        if (!cardRoot.getChildren().contains(err)) {
            cardRoot.getChildren().add(err);
        }
    }
}