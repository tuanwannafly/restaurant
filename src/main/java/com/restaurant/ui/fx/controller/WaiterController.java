package com.restaurant.ui.fx.controller;

import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.TableItem;
import com.restaurant.session.AppSession;
import com.restaurant.websocket.RestaurantEventClient;
import com.restaurant.websocket.WsTopic;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Controller cho WaiterView.fxml — Phase 9 JavaFX.
 *
 * <p>Chức năng:
 * <ul>
 *   <li>Tab 1 – Phục vụ bàn: load card từ {@link DeliveryCardComponent.fxml}</li>
 *   <li>Tab 2 – Dọn bàn: {@link TableView} + action button cell</li>
 *   <li>Tab 3 – Đã hủy: stats bar + {@link TableView}</li>
 *   <li>Polling 5 giây qua {@link Timeline}; toast delta khi count tăng</li>
 * </ul>
 *
 * Đặt vào: {@code src/main/java/com/restaurant/ui/fx/controller/WaiterController.java}
 */
public class WaiterController implements Initializable {

    // ─── FXML injections ──────────────────────────────────────────────────────

    @FXML private Label               lblRestaurant;
    @FXML private Button              btnBack;
    @FXML private TabPane             tabPane;
    @FXML private Tab                 tabDeliver;
    @FXML private Tab                 tabClean;
    @FXML private Tab                 tabCancelled;

    // Tab 1
    @FXML private FlowPane            deliveryCardsPane;
    @FXML private ProgressIndicator   spinner;

    // Tab 2
    @FXML private BorderPane          cleanPane;
    @FXML private TableView<TableItem>              cleanTable;
    @FXML private TableColumn<TableItem, String>    colCleanName;
    @FXML private TableColumn<TableItem, Number>    colCleanCap;
    @FXML private TableColumn<TableItem, String>    colCleanStatus;
    @FXML private TableColumn<TableItem, Void>      colCleanAction;

    // Tab 3
    @FXML private HBox                statsBar;
    @FXML private Label               lblCancelCount;
    @FXML private Label               lblCancelQty;
    @FXML private TableView<KitchenDAO.KitchenTicket>              cancelledTable;
    @FXML private TableColumn<KitchenDAO.KitchenTicket, String>    colCancelTable;
    @FXML private TableColumn<KitchenDAO.KitchenTicket, String>    colCancelItem;
    @FXML private TableColumn<KitchenDAO.KitchenTicket, Number>    colCancelQty;
    @FXML private TableColumn<KitchenDAO.KitchenTicket, Number>    colCancelRound;
    @FXML private TableColumn<KitchenDAO.KitchenTicket, String>    colCancelTime;

    // ─── DAOs ─────────────────────────────────────────────────────────────────

    private final KitchenDAO kitchenDAO = new KitchenDAO();
    private final TableDAO   tableDAO   = new TableDAO();

    // ─── Polling state ────────────────────────────────────────────────────────

    /** Fallback polling 30s — dùng khi WS tạm ngắt. */
    private Timeline pollTimeline;
    private Runnable cancelWsHandler;
    private static final int FALLBACK_INTERVAL_S = 30;

    private int      lastServeCount = -1;
    private int      lastCleanCount = -1;

    /** Daemon thread pool – chỉ 1 thread để tránh race condition */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "waiter-poll-thread");
        t.setDaemon(true);
        return t;
    });

    // ─── initialize ───────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Restaurant name
        try {
            String rName = com.restaurant.data.DataManager.getInstance()
                    .getMyRestaurant().getName();
            lblRestaurant.setText(rName);
        } catch (Exception ignored) {}

        setupCleanTable();
        setupCancelledTable();
        setupTabChangeListener();

        // Initial load
        loadData();

        // ── WebSocket real-time push ──────────────────────────────────────────
        long myRestaurantId = AppSession.getInstance().getRestaurantId();
        RestaurantEventClient ws = RestaurantEventClient.getInstance();
        cancelWsHandler = ws.addEventHandler(event -> {
            if (event.getRestaurantId() == myRestaurantId
                    && (WsTopic.KITCHEN.equals(event.getTopic())
                        || WsTopic.ORDERS.equals(event.getTopic())
                        || WsTopic.BADGE.equals(event.getTopic()))) {
                doPoll();
            }
        });
        ws.subscribe(WsTopic.KITCHEN, WsTopic.ORDERS, WsTopic.BADGE);

        // Fallback polling 30s (bắt cập nhật khi WS tạm ngắt)
        pollTimeline = new Timeline(
                new KeyFrame(Duration.seconds(FALLBACK_INTERVAL_S), e -> doPoll()));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
    }

    // ─── Helpers — centering ─────────────────────────────────────────────────

    private static TableCell<KitchenDAO.KitchenTicket, String> centeredStringCell() {
        TableCell<KitchenDAO.KitchenTicket, String> cell = new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
        cell.setAlignment(javafx.geometry.Pos.CENTER);
        return cell;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /** Gọi khi đóng cửa sổ / navigate away để dừng poll và thu hồi thread. */
    public void onDestroy() {
        if (pollTimeline != null) {
            pollTimeline.stop();
        }
        if (cancelWsHandler != null) { cancelWsHandler.run(); cancelWsHandler = null; }
        executor.shutdownNow();
    }

    @FXML
    private void onBack() {
        onDestroy();
        if (btnBack.getScene() != null && btnBack.getScene().getWindow() != null) {
            btnBack.getScene().getWindow().hide();
        }
    }

    // ─── Column setup ─────────────────────────────────────────────────────────

    private void setupCleanTable() {
        colCleanName.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getName()));

        colCleanCap.setCellValueFactory(cell ->
            (ObservableValue<Number>) (ObservableValue<?>)
                new SimpleIntegerProperty(cell.getValue().getCapacity()).asObject()
        );
        colCleanCap.setCellFactory(col -> {
            TableCell<TableItem, Number> cell = new TableCell<>() {
                @Override protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : String.valueOf(item.intValue()));
                }
            };
            cell.setAlignment(javafx.geometry.Pos.CENTER);
            return cell;
        });

        colCleanStatus.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getStatusDisplay()));
        colCleanStatus.setCellFactory(col -> new TableCell<>() {
            { setAlignment(javafx.geometry.Pos.CENTER); }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getIndex() < 0) {
                    setGraphic(null); setText(null);
                    return;
                }
                TableItem ti = getTableView().getItems().get(getIndex());
                Label badge = new Label(item);
                badge.getStyleClass().add("status-badge");
                badge.getStyleClass().add(
                        ti.getStatus() == TableItem.Status.DIRTY
                                ? "badge-dirty" : "badge-cleaning");
                setGraphic(badge);
                setText(null);
            }
        });

        colCleanAction.setCellFactory(col -> new CleanActionCell());
    }

    private void setupCancelledTable() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm  dd/MM");

        colCancelTable.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().tableName));
        colCancelTable.setCellFactory(col -> centeredStringCell());

        colCancelItem.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().itemName));
        // colCancelItem: left-align (item name text)

        colCancelQty.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().quantity)
        );
        colCancelQty.setCellFactory(col -> {
            TableCell<KitchenDAO.KitchenTicket, Number> cell = new TableCell<>() {
                @Override protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : String.valueOf(item.intValue()));
                }
            };
            cell.setAlignment(javafx.geometry.Pos.CENTER);
            return cell;
        });

        colCancelRound.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().roundNumber)
        );
        colCancelRound.setCellFactory(col -> {
            TableCell<KitchenDAO.KitchenTicket, Number> cell = new TableCell<>() {
                @Override protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : String.valueOf(item.intValue()));
                }
            };
            cell.setAlignment(javafx.geometry.Pos.CENTER);
            return cell;
        });

        colCancelTime.setCellValueFactory(cell -> {
            String t = (cell.getValue().createdAt != null)
                    ? cell.getValue().createdAt.format(fmt) : "—";
            return new SimpleStringProperty(t);
        });
        colCancelTime.setCellFactory(col -> centeredStringCell());
    }

    private void setupTabChangeListener() {
        tabPane.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> {
                    if (newTab == tabCancelled) {
                        loadCancelledAsync();
                    }
                });
    }

    // ─── loadData (initial full load) ────────────────────────────────────────

    public void loadData() {
        showSpinner(true);

        Task<FullData> task = new Task<>() {
            @Override
            protected FullData call() {
                long rid = AppSession.getInstance().getRestaurantId();
                return new FullData(
                        kitchenDAO.getReadyByTable(rid),
                        kitchenDAO.getDirtyTables(rid),
                        kitchenDAO.getCancelledItems(rid));
            }
        };

        task.setOnSucceeded(e -> {
            showSpinner(false);
            FullData d = task.getValue();
            rebuildDeliveryCards(d.readyMap);
            rebuildCleanTable(d.dirtyList);
            rebuildCancelledTab(d.cancelledList);
            lastServeCount = (d.readyMap  != null) ? d.readyMap.size()  : 0;
            lastCleanCount = (d.dirtyList != null) ? d.dirtyList.size() : 0;
        });

        task.setOnFailed(e -> {
            showSpinner(false);
            showError("Lỗi tải dữ liệu: " + task.getException().getMessage());
        });

        executor.submit(task);
    }

    private void loadCancelledAsync() {
        Task<List<KitchenDAO.KitchenTicket>> task = new Task<>() {
            @Override
            protected List<KitchenDAO.KitchenTicket> call() {
                return kitchenDAO.getCancelledItems(
                        AppSession.getInstance().getRestaurantId());
            }
        };
        task.setOnSucceeded(e -> rebuildCancelledTab(task.getValue()));
        task.setOnFailed(e -> showError(
                "Không thể tải danh sách đã hủy: " + task.getException().getMessage()));
        executor.submit(task);
    }

    // ─── doPoll ───────────────────────────────────────────────────────────────

    private void doPoll() {
        Task<PollData> task = new Task<>() {
            @Override
            protected PollData call() {
                long rid = AppSession.getInstance().getRestaurantId();
                return new PollData(
                        kitchenDAO.getReadyByTable(rid),
                        kitchenDAO.getDirtyTables(rid));
            }
        };

        task.setOnSucceeded(e -> {
            PollData d = task.getValue();

            rebuildDeliveryCards(d.readyMap);
            rebuildCleanTable(d.dirtyList);

            // Toast delta – "Phục vụ bàn"
            int newServe = (d.readyMap != null) ? d.readyMap.size() : 0;
            if (lastServeCount >= 0 && newServe > lastServeCount) {
                showToast("Có " + (newServe - lastServeCount) + " bàn cần phục vụ!",
                        ToastType.INFO);
            }
            lastServeCount = newServe;

            // Toast delta – "Dọn bàn"
            int newClean = (d.dirtyList != null) ? d.dirtyList.size() : 0;
            if (lastCleanCount >= 0 && newClean > lastCleanCount) {
                showToast("Có " + (newClean - lastCleanCount) + " bàn cần dọn!",
                        ToastType.INFO);
            }
            lastCleanCount = newClean;
        });

        task.setOnFailed(e ->
                showError("Lỗi polling: " + task.getException().getMessage()));

        executor.submit(task);
    }

    // ─── Rebuild Tab 1 – Delivery Cards ──────────────────────────────────────

    private void rebuildDeliveryCards(
            Map<String, List<KitchenDAO.KitchenTicket>> map) {

        deliveryCardsPane.getChildren().clear();

        if (map == null || map.isEmpty()) {
            Label empty = new Label(
                    "🛎  Không có bàn nào cần phục vụ\nTất cả đang ổn ✓");
            empty.getStyleClass().add("empty-label");
            empty.setWrapText(true);
            deliveryCardsPane.getChildren().add(empty);
            return;
        }

        for (Map.Entry<String, List<KitchenDAO.KitchenTicket>> entry : map.entrySet()) {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/fxml/DeliveryCardComponent.fxml"));
                VBox card = loader.load();
                DeliveryCardController ctrl = loader.getController();
                ctrl.setData(entry.getValue(), kitchenDAO, this::loadData);
                deliveryCardsPane.getChildren().add(card);
            } catch (IOException ex) {
                ex.printStackTrace();
                showError("Không load được card: " + ex.getMessage());
            }
        }
    }

    // ─── Rebuild Tab 2 – Clean Table ─────────────────────────────────────────

    private void rebuildCleanTable(List<TableItem> tables) {
        List<TableItem> data = (tables != null) ? tables : List.of();
        cleanTable.setItems(FXCollections.observableArrayList(data));
        cleanTable.refresh();
    }

    // ─── Rebuild Tab 3 – Cancelled ───────────────────────────────────────────

    private void rebuildCancelledTab(List<KitchenDAO.KitchenTicket> items) {
        List<KitchenDAO.KitchenTicket> data = (items != null) ? items : List.of();

        boolean hasData = !data.isEmpty();
        statsBar.setVisible(hasData);
        statsBar.setManaged(hasData);

        if (hasData) {
            int totalQty = data.stream().mapToInt(t -> t.quantity).sum();
            lblCancelCount.setText("Hôm nay: " + data.size() + " đơn hủy");
            lblCancelQty.setText("Tổng số lượng: " + totalQty + " món");
        }

        cancelledTable.setItems(FXCollections.observableArrayList(data));
    }

    // ─── Helpers – spinner, toast, error ─────────────────────────────────────

    private void showSpinner(boolean visible) {
        spinner.setVisible(visible);
        spinner.setManaged(visible);
    }

    /**
     * Toast overlay nhẹ: label mờ dần ở góc dưới-phải,
     * tự ẩn sau 3 giây.
     */
    public void showToast(String message, ToastType type) {
        Platform.runLater(() -> {
            // Tìm root StackPane (nếu scene root là StackPane/Pane)
            if (deliveryCardsPane.getScene() == null) return;
            if (!(deliveryCardsPane.getScene().getRoot() instanceof Pane root)) return;

            Label toast = new Label(message);
            toast.getStyleClass().add("toast-label");
            toast.getStyleClass().add(switch (type) {
                case SUCCESS -> "toast-success";
                case WARNING -> "toast-warning";
                default       -> "toast-info";
            });
            toast.setPadding(new Insets(10, 18, 10, 18));
            toast.setWrapText(false);
            toast.setOpacity(1.0);

            // Đặt vị trí góc dưới-phải
            toast.setLayoutX(root.getWidth()  - 320);
            toast.setLayoutY(root.getHeight() - 70);
            root.widthProperty().addListener((ob, ov, nv) ->
                    toast.setLayoutX(nv.doubleValue() - 320));
            root.heightProperty().addListener((ob, ov, nv) ->
                    toast.setLayoutY(nv.doubleValue() - 70));

            root.getChildren().add(toast);

            // Fade out sau 2.5s → remove sau 3s
            PauseTransition pause = new PauseTransition(Duration.seconds(2.5));
            pause.setOnFinished(ev -> {
                FadeTransition fade = new FadeTransition(Duration.millis(500), toast);
                fade.setFromValue(1.0);
                fade.setToValue(0.0);
                fade.setOnFinished(done -> root.getChildren().remove(toast));
                fade.play();
            });
            pause.play();
        });
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            alert.setHeaderText("Lỗi");
            alert.showAndWait();
        });
    }

    // ─── Inner DTOs ───────────────────────────────────────────────────────────

    private static final class FullData {
        final Map<String, List<KitchenDAO.KitchenTicket>> readyMap;
        final List<TableItem>                              dirtyList;
        final List<KitchenDAO.KitchenTicket>               cancelledList;

        FullData(Map<String, List<KitchenDAO.KitchenTicket>> readyMap,
                 List<TableItem> dirtyList,
                 List<KitchenDAO.KitchenTicket> cancelledList) {
            this.readyMap      = readyMap;
            this.dirtyList     = dirtyList;
            this.cancelledList = cancelledList;
        }
    }

    private static final class PollData {
        final Map<String, List<KitchenDAO.KitchenTicket>> readyMap;
        final List<TableItem>                              dirtyList;

        PollData(Map<String, List<KitchenDAO.KitchenTicket>> readyMap,
                 List<TableItem> dirtyList) {
            this.readyMap  = readyMap;
            this.dirtyList = dirtyList;
        }
    }

    public enum ToastType { INFO, SUCCESS, WARNING }

    // ─── CleanActionCell ──────────────────────────────────────────────────────

    /**
     * Cell button cho cột "Hành động" trong bảng dọn bàn.
     * DIRTY → "Bắt đầu dọn" → trạng thái CLEANING
     * CLEANING → "Dọn xong"  → trạng thái RANH (sẵn sàng)
     */
    private class CleanActionCell extends TableCell<TableItem, Void> {

        private final Button btn = new Button();

        CleanActionCell() {
            btn.getStyleClass().add("action-btn");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> handleCleanAction());
        }

        private void handleCleanAction() {
            int idx = getIndex();
            if (idx < 0 || idx >= getTableView().getItems().size()) return;
            TableItem item = getTableView().getItems().get(idx);
            boolean   isDirty = item.getStatus() == TableItem.Status.DIRTY;
            TableItem.Status next = isDirty
                    ? TableItem.Status.CLEANING
                    : TableItem.Status.RANH;

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    tableDAO.updateStatus(item.getId(), next);
                    return null;
                }
            };
            task.setOnSucceeded(ev -> {
                String msg = (next == TableItem.Status.RANH)
                        ? "Bàn " + item.getName() + " đã sẵn sàng phục vụ!"
                        : "Đang dọn bàn " + item.getName();
                showToast(msg, next == TableItem.Status.RANH
                        ? ToastType.SUCCESS : ToastType.INFO);
                loadData();
            });
            task.setOnFailed(ev ->
                    showError("Lỗi cập nhật bàn: " + task.getException().getMessage()));
            executor.submit(task);
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getIndex() < 0
                    || getIndex() >= getTableView().getItems().size()) {
                setGraphic(null);
                return;
            }
            TableItem ti      = getTableView().getItems().get(getIndex());
            boolean   isDirty = ti.getStatus() == TableItem.Status.DIRTY;
            btn.setText(isDirty ? "Bắt đầu dọn" : "Dọn xong");
            btn.getStyleClass().removeAll("btn-warning", "btn-success");
            btn.getStyleClass().add(isDirty ? "btn-warning" : "btn-success");
            setGraphic(btn);
            setText(null);
        }
    }
}