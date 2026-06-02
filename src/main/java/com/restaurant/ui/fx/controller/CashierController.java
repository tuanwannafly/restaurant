package com.restaurant.ui.fx.controller;

import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import com.restaurant.dao.OrderDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.Order;
import com.restaurant.model.TableItem;
import com.restaurant.session.AppSession;
import com.restaurant.ui.fx.util.ToastNotificationFx;
import com.restaurant.websocket.RestaurantEventClient;
import com.restaurant.websocket.WsTopic;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * CashierController — Phase 10 · Thu ngân
 * ─────────────────────────────────────────────────────────────────────────────
 * Controller cho {@code fxml/CashierView.fxml}.
 * Tương đương {@code com.restaurant.ui.CashierPanel} (Swing Phase 7C).
 *
 * <h3>Trách nhiệm:</h3>
 * <ul>
 *   <li>Khởi động {@link #pollTimeline} — polling 5 s một lần ({@link #doPoll()})</li>
 *   <li>Load/rebuild 2 cột Kanban:
 *       {@link #rebuildPendingColumn()} và {@link #rebuildProcessingColumn(PaymentRequest, String)}</li>
 *   <li>Mở {@code CashierPaymentDialog} khi click card ({@link #openPaymentDialog(PaymentRequest)})</li>
 *   <li>Di chuyển card từ "Chờ" sang "Đang xử lý" ({@link #moveToInProgress(PaymentRequest, String)})</li>
 *   <li>Hoàn tất thanh toán ({@link #completePayment(PaymentRequest)})</li>
 *   <li>Hiện toast thông qua {@link ToastNotificationFx}</li>
 * </ul>
 *
 * <h3>Vòng đời:</h3>
 * <pre>
 *   initialize() → loadData() → [Timeline 5s] → doPoll() lặp lại
 *                                              ↓
 *                                  rebuildPendingColumn()
 * </pre>
 *
 * <h3>FXML tương ứng:</h3>
 * {@code src/main/resources/fxml/CashierView.fxml}
 */
public class CashierController implements Initializable {

    // ─── FXML Injections ─────────────────────────────────────────────────────

    /** Container cột trái — "Chờ thanh toán". Các card được add/remove động. */
    @FXML private VBox   pendingContainer;

    /** Container cột phải — "Đang xử lý". Các card được add/remove động. */
    @FXML private VBox   processingContainer;

    /**
     * Label spinner (ký tự ⟳) dùng để chỉ báo đang tải.
     * Hiện khi bắt đầu load, ẩn khi load xong.
     * Quay bằng CSS animation {@code .spinner-label}.
     */
    @FXML private Label  spinnerLabel;

    // ─── State ───────────────────────────────────────────────────────────────

    /**
     * Danh sách đơn đang chờ thanh toán (cột trái).
     * Được filter ra từ {@link OrderDAO#getAll()} trong {@link #doPoll()}.
     */
    private final List<PaymentRequest> pendingList    = new ArrayList<>();

    /**
     * Danh sách đơn đang xử lý (cột phải).
     * Được thêm vào khi thu ngân xác nhận qua {@link #moveToInProgress}.
     */
    private final List<PaymentRequest> processingList = new ArrayList<>();

    /**
     * Số lượng đơn pending lần poll trước.
     * Dùng để phát toast khi có đơn mới.
     * Giá trị -1 = chưa poll lần nào (bỏ qua toast lần đầu).
     */
    private int lastPaymentCount = -1;

    // ─── Polling ─────────────────────────────────────────────────────────────

    /**
     * Timeline chạy nền — cứ {@value #POLL_INTERVAL_S} giây gọi {@link #doPoll()} một lần.
     * Dừng khi scene bị unload (override {@code stop()} hoặc dùng sceneProperty listener).
     */
    private Timeline pollTimeline;
    private Runnable cancelWsHandler;

    /** Khoảng cách fallback polling (giây) — WS sẽ kích trigger trước khi đến lượt này. */
    private static final int POLL_INTERVAL_S = 30;

    // ─── DAO ─────────────────────────────────────────────────────────────────

    private final OrderDAO orderDAO = new OrderDAO();
    private final TableDAO tableDAO = new TableDAO();

    // ─── initialize ──────────────────────────────────────────────────────────

    /**
     * Gọi tự động bởi FXMLLoader sau khi FXML được load.
     * Khởi động dữ liệu và Timeline polling.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadData();
        setupWebSocket();
        startPolling();
    }

    // ─── Polling ─────────────────────────────────────────────────────────────

    /**
     * Khởi động {@link #pollTimeline} — INDEFINITE, mỗi {@value #POLL_INTERVAL_S} giây.
     * An toàn để gọi nhiều lần vì Timeline cũ sẽ bị stop trước.
     */
    private void startPolling() {
        if (pollTimeline != null) pollTimeline.stop();
        pollTimeline = new Timeline(
            new KeyFrame(Duration.seconds(POLL_INTERVAL_S), e -> doPoll())
        );
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
    }

    /**
     * Đăng ký WebSocket handler — nhận push event từ ORDERS/BADGE → gọi doPoll() ngay.
     */
    private void setupWebSocket() {
        long myRestaurantId = AppSession.getInstance().getRestaurantId();
        RestaurantEventClient ws = RestaurantEventClient.getInstance();
        cancelWsHandler = ws.addEventHandler(event -> {
            if (event.getRestaurantId() == myRestaurantId
                    && (WsTopic.ORDERS.equals(event.getTopic())
                        || WsTopic.BADGE.equals(event.getTopic()))) {
                doPoll();
            }
        });
        ws.subscribe(WsTopic.ORDERS, WsTopic.BADGE);
    }

    /**
     * Dừng polling — gọi khi view bị ẩn hoặc navigate sang panel khác.
     * Nên bind với sceneProperty hoặc gọi từ parent controller.
     */
    public void stopPolling() {
        if (pollTimeline != null) {
            pollTimeline.stop();
            lastPaymentCount = -1;
        }
        if (cancelWsHandler != null) { cancelWsHandler.run(); cancelWsHandler = null; }
    }

    // ─── doPoll ──────────────────────────────────────────────────────────────

    /**
     * Polling task — chạy trên background thread.
     * Logic giống {@code CashierPanel#doPoll()} (Swing Phase 7C):
     * <ol>
     *   <li>Hiện spinner</li>
     *   <li>Load {@link OrderDAO#getAll()} → filter active</li>
     *   <li>Loại bỏ các orderId đang trong {@link #processingList}</li>
     *   <li>Rebuild {@link #pendingContainer}</li>
     *   <li>Toast nếu có đơn mới hơn lần trước</li>
     * </ol>
     */
    private void doPoll() {
        setSpinnerVisible(true);

        Task<List<PaymentRequest>> task = new Task<>() {
            @Override
            protected List<PaymentRequest> call() {
                return orderDAO.getAll().stream()
                    .filter(CashierController::isActiveForCashier)
                    .map(CashierController::toPaymentRequest)
                    .collect(Collectors.toList());
            }
        };

        task.setOnSucceeded(e -> {
            setSpinnerVisible(false);

            List<PaymentRequest> loaded = task.getValue();

            // Lọc ra những orderId chưa có trong processingList
            List<String> processingIds = processingList.stream()
                .map(r -> r.orderId).collect(Collectors.toList());

            List<PaymentRequest> filtered = loaded.stream()
                .filter(r -> !processingIds.contains(r.orderId))
                .collect(Collectors.toList());

            pendingList.clear();
            pendingList.addAll(filtered);
            rebuildPendingColumn();

            // Toast nếu có đơn thanh toán mới
            int newCount = pendingList.size();
            if (lastPaymentCount >= 0 && newCount > lastPaymentCount) {
                int diff = newCount - lastPaymentCount;
                ToastNotificationFx.showInfo(
                    getStage(),
                    "Có " + diff + " yêu cầu thanh toán mới!"
                );
            }
            lastPaymentCount = newCount;
        });

        task.setOnFailed(e -> {
            setSpinnerVisible(false);
            System.err.println("[CashierController] doPoll lỗi: "
                + task.getException().getMessage());
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ─── loadData ────────────────────────────────────────────────────────────

    /**
     * Load đầy đủ dữ liệu (không delta) — gọi khi khởi tạo hoặc nhấn "Làm mới".
     * Tương đương {@code CashierPanel#loadData()} (Swing Phase 7C).
     */
    public void loadData() {
        setSpinnerVisible(true);

        Task<List<PaymentRequest>> task = new Task<>() {
            @Override
            protected List<PaymentRequest> call() {
                return orderDAO.getAll().stream()
                    .filter(CashierController::isActiveForCashier)
                    .map(CashierController::toPaymentRequest)
                    .collect(Collectors.toList());
            }
        };

        task.setOnSucceeded(e -> {
            setSpinnerVisible(false);

            List<PaymentRequest> loaded = task.getValue();

            List<String> processingIds = processingList.stream()
                .map(r -> r.orderId).collect(Collectors.toList());

            pendingList.clear();
            pendingList.addAll(loaded.stream()
                .filter(r -> !processingIds.contains(r.orderId))
                .collect(Collectors.toList()));

            rebuildPendingColumn();
            lastPaymentCount = pendingList.size();
        });

        task.setOnFailed(e -> {
            setSpinnerVisible(false);
            System.err.println("[CashierController] loadData lỗi: "
                + task.getException().getMessage());
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ─── FXML Action ─────────────────────────────────────────────────────────

    /** Xử lý click nút "↻ Làm mới" trong CashierView.fxml. */
    @FXML
    private void onRefresh() {
        loadData();
    }

    // ─── moveToInProgress ────────────────────────────────────────────────────

    /**
     * Callback từ dialog sau khi xác nhận:
     * chuyển card sang cột "Đang xử lý" với payment method đã xác nhận.
     *
     * @param req               PaymentRequest gốc
     * @param employeeName      nhân viên được chọn
     * @param paymentMethodName tên enum của phương thức thanh toán đã xác nhận
     */
    public void moveToInProgress(PaymentRequest req, String employeeName, String paymentMethodName) {
        // Tạo bản sao với payment method đã được cashier xác nhận
        PaymentRequest.PaymentMethod confirmedMethod;
        try {
            confirmedMethod = PaymentRequest.PaymentMethod.valueOf(paymentMethodName);
        } catch (Exception e) {
            confirmedMethod = req.paymentMethod;
        }
        PaymentRequest confirmed = req.withPaymentMethod(confirmedMethod);

        pendingList.removeIf(r -> r.orderId.equals(req.orderId));
        rebuildPendingColumn();

        processingList.add(confirmed);
        addProcessingCard(confirmed, employeeName);
    }

    // ─── completePayment ─────────────────────────────────────────────────────

    /**
     * Hoàn tất thanh toán: cập nhật DB → remove khỏi processingList → toast.
     * Tương đương {@code CashierPanel#completePayment(PaymentRequest)} (Swing Phase 7C).
     *
     * @param req PaymentRequest cần hoàn tất
     */
    public void completePayment(PaymentRequest req) {
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                String method = req.paymentMethod != null
                    ? req.paymentMethod.name() : "CASH";
                boolean ok = orderDAO.completeOrder(req.orderId, method);
                if (ok) {
                    com.restaurant.session.AuditLogger.getInstance()
                        .logPaymentComplete(req.orderId, req.tableId, method);
                    // Cập nhật trạng thái bàn → DIRTY; wrap try-catch để task
                    // không fail nếu bàn đã được đổi trạng thái bởi tiến trình khác.
                    try {
                        tableDAO.updateStatus(req.tableId, TableItem.Status.DIRTY);
                    } catch (Exception tableEx) {
                        System.err.println("[CashierController] updateStatus lỗi (bỏ qua): "
                            + tableEx.getMessage());
                    }
                    // Broadcast để TableController phía admin tự động refresh (TABLES)
                    // VÀ để tablet WaitingPage nhận tín hiệu thanh toán hoàn tất (ORDERS + table topic)
                    // VÀ để WaiterController nhận signal cần dọn bàn (KITCHEN)
                    try {
                        long rid = com.restaurant.session.AppSession.getInstance().getRestaurantId();
                        com.restaurant.websocket.RestaurantEventServer srv =
                            com.restaurant.websocket.RestaurantEventServer.getInstance();
                        com.restaurant.websocket.RestaurantEventClient cli =
                            com.restaurant.websocket.RestaurantEventClient.getInstance();

                        com.restaurant.websocket.WsEvent tablesEvt =
                            com.restaurant.websocket.WsEvent.of(
                                com.restaurant.websocket.WsTopic.TABLES, rid);
                        com.restaurant.websocket.WsEvent ordersEvt =
                            com.restaurant.websocket.WsEvent.of(
                                com.restaurant.websocket.WsTopic.ORDERS, rid);
                        com.restaurant.websocket.WsEvent badgeEvt =
                            com.restaurant.websocket.WsEvent.of(
                                com.restaurant.websocket.WsTopic.BADGE, rid);
                        // Thêm KITCHEN broadcast để WaiterController nhận signal hiển thị bàn cần dọn
                        com.restaurant.websocket.WsEvent kitchenEvt =
                            com.restaurant.websocket.WsEvent.of(
                                com.restaurant.websocket.WsTopic.KITCHEN, rid);
                        // Topic bàn riêng — tablet đang chờ trên topic này
                        com.restaurant.websocket.WsEvent tableEvt =
                            com.restaurant.websocket.WsEvent.of(
                                com.restaurant.websocket.WsTopic.forTable(
                                    Integer.parseInt(req.tableId)), rid);

                        if (srv.isRunning()) {
                            srv.broadcast(tablesEvt);
                            srv.broadcast(ordersEvt);
                            srv.broadcast(badgeEvt);
                            srv.broadcast(kitchenEvt);
                            srv.broadcast(tableEvt);
                        } else {
                            cli.publishToServer(tablesEvt);
                            cli.publishToServer(ordersEvt);
                            cli.publishToServer(badgeEvt);
                            cli.publishToServer(kitchenEvt);
                            cli.publishToServer(tableEvt);
                        }
                    } catch (Exception wsEx) {
                        System.err.println("[CashierController] broadcast lỗi: " + wsEx.getMessage());
                    }
                }
                return ok;
            }
        };

        task.setOnSucceeded(e -> {
            boolean ok = task.getValue();
            if (ok) {
                processingList.removeIf(r -> r.orderId.equals(req.orderId));
                rebuildProcessingColumnFull();
                ToastNotificationFx.showSuccess(
                    getStage(),
                    req.tableName + " – Thanh toán hoàn tất!"
                );
            } else {
                ToastNotificationFx.showError(
                    getStage(),
                    "Không thể hoàn tất đơn #" + req.orderId
                );
            }
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            System.err.println("[CashierController] completePayment lỗi: "
                + (ex != null ? ex.getMessage() : "unknown"));
            com.restaurant.session.AuditLogger.getInstance()
                .logPaymentFailed(req.orderId,
                    ex != null ? ex.getMessage() : "unknown error");
            ToastNotificationFx.showError(
                getStage(),
                "Lỗi hệ thống khi hoàn tất đơn #" + req.orderId
                    + ".\nVui lòng thử lại hoặc liên hệ quản trị viên."
            );
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ─── openPaymentDialog ───────────────────────────────────────────────────

    /**
     * Mở CashierPaymentDialog khi click vào card "Chờ thanh toán".
     * Load FXML, truyền dữ liệu qua controller, showAndWait().
     *
     * @param req PaymentRequest của card được click
     */
    public void openPaymentDialog(PaymentRequest req) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/dialog/CashierPaymentDialog.fxml"));
            Node root = loader.load();

            CashierPaymentDialogController ctrl = loader.getController();
            // Callback nhận (employeeName, paymentMethodName)
            ctrl.initData(req, (employeeName, paymentMethodName) -> {
                Platform.runLater(() -> moveToInProgress(req, employeeName, paymentMethodName));
            });

            Stage dialog = new Stage();
            dialog.setTitle("Thanh toán – " + req.tableName);
            dialog.setResizable(false);
            dialog.initOwner(getStage());
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialog.setScene(new javafx.scene.Scene((javafx.scene.Parent) root));
            dialog.showAndWait();

        } catch (Exception ex) {
            System.err.println("[CashierController] openPaymentDialog lỗi: " + ex.getMessage());
        }
    }

    // ─── Rebuild columns ─────────────────────────────────────────────────────

    /**
     * Xoá và dựng lại toàn bộ cột "Chờ thanh toán".
     * Mỗi item trong {@link #pendingList} được load từ
     * {@code PaymentRequestCard.fxml} và bind sự kiện click → {@link #openPaymentDialog}.
     */
    private void rebuildPendingColumn() {
        pendingContainer.getChildren().clear();

        if (pendingList.isEmpty()) {
            pendingContainer.getChildren().add(
                buildEmptyLabel("💳  Không có bàn nào chờ thanh toán"));
            return;
        }

        for (PaymentRequest req : pendingList) {
            try {
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/PaymentRequestCard.fxml"));
                Node card = loader.load();

                // Bind dữ liệu vào các fx:id trong PaymentRequestCard.fxml
                bindPendingCard(card, req);

                // Click toàn bộ card → mở dialog
                card.setOnMouseClicked(e -> openPaymentDialog(req));

                pendingContainer.getChildren().add(card);
            } catch (Exception ex) {
                // ex.getMessage() for LoadException is just the FXML path — log the cause for real info
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                System.err.println("[CashierController] rebuildPendingColumn card lỗi: "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            }
        }
    }

    /**
     * Dựng lại toàn bộ cột "Đang xử lý" từ {@link #processingList}.
     * Gọi sau khi một đơn bị remove (completePayment).
     */
    private void rebuildProcessingColumnFull() {
        processingContainer.getChildren().clear();

        if (processingList.isEmpty()) {
            processingContainer.getChildren().add(
                buildEmptyLabel("✅  Chưa có đơn nào đang xử lý"));
            return;
        }

        for (PaymentRequest req : processingList) {
            addProcessingCard(req, null);
        }
    }

    /**
     * Thêm một card "Đang xử lý" vào cuối {@link #processingContainer}.
     * Card có nút "✓ Hoàn tất" gọi {@link #completePayment(PaymentRequest)}.
     *
     * @param req          PaymentRequest
     * @param employeeName tên nhân viên (hiển thị trên card); null → hiện method
     */
    private void addProcessingCard(PaymentRequest req, String employeeName) {
        // Nếu cột đang chỉ chứa EmptyLabel → xoá đi
        if (processingContainer.getChildren().size() == 1
                && processingContainer.getChildren().get(0) instanceof Label) {
            processingContainer.getChildren().clear();
        }

        VBox card = buildProcessingCardNode(req, employeeName);
        processingContainer.getChildren().add(card);
    }

    // ─── Card builders ───────────────────────────────────────────────────────

    /**
     * Bind dữ liệu {@code req} vào các Label trong PaymentRequestCard (lookup by fx:id).
     *
     * @param card Node đã load từ PaymentRequestCard.fxml
     * @param req  dữ liệu cần bind
     */
    private void bindPendingCard(Node card, PaymentRequest req) {
        Label lblTableName = (Label) card.lookup("#lblTableName");
        Label lblAmount    = (Label) card.lookup("#lblAmount");
        Label lblMethod    = (Label) card.lookup("#lblMethod");
        Label lblSep       = (Label) card.lookup("#lblSep");

        if (lblTableName != null) lblTableName.setText(req.tableName);
        if (lblAmount    != null) lblAmount.setText(formatAmount(req.getGrandTotal()) + "đ");
        if (lblMethod    != null) {
            // Hiện "Tiền mặt · 3 món" hoặc "Chuyển khoản · 3 món"
            int itemCount = req.items != null ? req.items.size() : 0;
            String suffix = itemCount > 0 ? " · " + itemCount + " món" : "";
            lblMethod.setText(req.getPaymentMethodLabel() + suffix);
        }
        // Ẩn separator nếu không cần
        if (lblSep != null) lblSep.setVisible(false);
    }

    /**
     * Tạo card "Đang xử lý" bằng code (không dùng FXML riêng).
     * Gồm: tên bàn, tổng tiền, nhân viên, nút "✓ Hoàn tất".
     *
     * @param req          PaymentRequest
     * @param employeeName tên nhân viên; null → dùng payment method label
     * @return VBox node sẵn sàng để add vào processingContainer
     */
    private VBox buildProcessingCardNode(PaymentRequest req, String employeeName) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("payment-card", "payment-card-processing");

        // Header row: tên bàn bên trái, số tiền bên phải
        javafx.scene.layout.HBox headerRow = new javafx.scene.layout.HBox();
        headerRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label lblTable  = new Label(req.tableName);
        lblTable.getStyleClass().add("card-table-name");
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        Label lblAmount = new Label(formatAmount(req.getGrandTotal()) + "đ");
        lblAmount.getStyleClass().add("card-amount");
        headerRow.getChildren().addAll(lblTable, spacer, lblAmount);

        // Nhân viên hoặc phương thức
        String staffText = (employeeName != null && !employeeName.isBlank())
            ? "Nhân viên: " + employeeName
            : req.getPaymentMethodLabel();
        Label lblStaff = new Label(staffText);
        lblStaff.getStyleClass().add("card-method");

        // Nút Hoàn tất
        javafx.scene.control.Button btnDone = new javafx.scene.control.Button("✓  Hoàn tất");
        btnDone.getStyleClass().add("btn-success-full");
        btnDone.setMaxWidth(Double.MAX_VALUE);
        btnDone.setOnAction(e -> completePayment(req));

        card.getChildren().addAll(headerRow, lblStaff, btnDone);
        card.setPadding(new javafx.geometry.Insets(14, 16, 14, 16));
        return card;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Tạo label trạng thái rỗng cho cột. */
    private Label buildEmptyLabel(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("empty-state-label");
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.setAlignment(javafx.geometry.Pos.CENTER);
        return lbl;
    }

    /** Hiện/ẩn spinner. Luôn chạy trên JavaFX Application Thread. */
    private void setSpinnerVisible(boolean visible) {
        Platform.runLater(() -> {
            if (spinnerLabel != null) {
                spinnerLabel.setVisible(visible);
                spinnerLabel.setManaged(visible);
            }
        });
    }

    /**
     * Lấy Stage hiện tại để truyền cho {@link ToastNotificationFx}
     * và initOwner của dialog.
     */
    private Stage getStage() {
        try {
            Window w = pendingContainer.getScene().getWindow();
            return (w instanceof Stage) ? (Stage) w : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ─── Static helpers (giống CashierPanel) ─────────────────────────────────

    /**
     * Chỉ hiện đơn có status {@code PAYMENT_REQUESTED} — khách đã bấm
     * "Yêu cầu thanh toán" từ tablet và đang chờ thu ngân xử lý.
     * Các đơn COOKING, DELIVERED... không thuộc trách nhiệm của cashier màn hình này.
     */
    private static boolean isActiveForCashier(Order o) {
        return o.getStatus() == Order.Status.PAYMENT_REQUESTED;
    }

    /**
     * Chuyển {@link Order} → {@link PaymentRequest}, giữ items + paymentMethod.
     */
    private static PaymentRequest toPaymentRequest(Order o) {
        String tableName = (o.getTableName() != null && !o.getTableName().isBlank())
            ? "Bàn " + o.getTableName()
            : "Bàn #" + o.getTableId();

        // Map payment method từ DB string → enum
        PaymentRequest.PaymentMethod method = parsePaymentMethod(o.getPaymentMethod());

        PaymentRequest req = new PaymentRequest(
            o.getId(),
            o.getTableId(),
            tableName,
            o.getTotalAmount(),
            method
        );
        // [FIX] Loại bỏ món CANCELLED — không hiển thị và không tính tiền
        req.items = o.getItems() == null ? new java.util.ArrayList<>()
                : o.getItems().stream()
                        .filter(i -> i.getItemStatus()
                                != com.restaurant.model.Order.OrderItem.ItemStatus.CANCELLED)
                        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        req.createdTime = o.getCreatedTime();
        return req;
    }

    /** Chuyển DB string → PaymentMethod enum, mặc định CASH nếu null/unknown. */
    private static PaymentRequest.PaymentMethod parsePaymentMethod(String dbMethod) {
        if (dbMethod == null) return PaymentRequest.PaymentMethod.CASH;
        switch (dbMethod.toUpperCase()) {
            case "BANK_TRANSFER": return PaymentRequest.PaymentMethod.BANK_TRANSFER;
            case "CARD":          return PaymentRequest.PaymentMethod.CARD;
            case "MOMO":          return PaymentRequest.PaymentMethod.MOMO;
            case "VNPAY":         return PaymentRequest.PaymentMethod.VNPAY;
            default:              return PaymentRequest.PaymentMethod.CASH;
        }
    }

    /** Định dạng tiền VND (dấu phẩy/chấm theo locale vi_VN). */
    private static String formatAmount(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format((long) amount);
    }

    // ─── Inner DTO: PaymentRequest ────────────────────────────────────────────

    /**
     * DTO gọn mang thông tin một đơn hàng cần thanh toán.
     */
    public static class PaymentRequest {

        public enum PaymentMethod {
            CASH("Tiền mặt"),
            BANK_TRANSFER("Chuyển khoản"),
            CARD("Thẻ"),
            MOMO("MoMo"),
            VNPAY("VNPay");

            private final String label;
            PaymentMethod(String label) { this.label = label; }
            public String getLabel() { return label; }
        }

        // ─── Core fields (final) ──────────────────────────────────────────────
        public final String        orderId;
        public final String        tableId;
        public final String        tableName;
        public final double        totalAmount;   // tổng từ DB (trước VAT nếu DB lưu net, hoặc grand total)
        public final PaymentMethod paymentMethod;

        // ─── Extended fields ──────────────────────────────────────────────────
        /** Danh sách món trong đơn — được set từ Order.getItems(). */
        public java.util.List<com.restaurant.model.Order.OrderItem> items
            = new java.util.ArrayList<>();

        /** Giờ mở bàn / tạo đơn — hiển thị trên dialog. */
        public String createdTime = "";

        // ─── VAT ─────────────────────────────────────────────────────────────
        /** VAT rate — 10% theo quy định Việt Nam hiện hành. */
        public static final double VAT_RATE = 0.10;

        // ─── Constructors ────────────────────────────────────────────────────
        public PaymentRequest(String orderId, String tableId, String tableName,
                              double totalAmount, PaymentMethod paymentMethod) {
            this.orderId       = orderId;
            this.tableId       = tableId;
            this.tableName     = tableName;
            this.totalAmount   = totalAmount;
            this.paymentMethod = paymentMethod;
        }

        /** Tạo bản sao với payment method mới (dùng sau khi cashier xác nhận). */
        public PaymentRequest withPaymentMethod(PaymentMethod m) {
            PaymentRequest copy = new PaymentRequest(orderId, tableId, tableName, totalAmount, m);
            copy.items       = this.items;
            copy.createdTime = this.createdTime;
            return copy;
        }

        // ─── Computed totals ─────────────────────────────────────────────────

        /**
         * Tạm tính = tổng (qty × đơn giá) các món.
         * Nếu items rỗng, dùng totalAmount từ DB làm fallback.
         */
        public double getSubtotal() {
            if (items == null || items.isEmpty()) return totalAmount;
            // [FIX] Chỉ cộng những món chưa bị hủy — CANCELLED không được cộng vào tạm tính
            return items.stream()
                .filter(i -> i.getItemStatus()
                        != com.restaurant.model.Order.OrderItem.ItemStatus.CANCELLED)
                .mapToDouble(com.restaurant.model.Order.OrderItem::getSubtotal)
                .sum();
        }

        /** VAT 10% = subtotal × 0.10. */
        public double getVatAmount() {
            return getSubtotal() * VAT_RATE;
        }

        /**
         * Tổng cộng (đã gồm VAT).
         * Ưu tiên dùng totalAmount từ DB nếu khớp, nếu không tính lại.
         */
        public double getGrandTotal() {
            double computed = getSubtotal() + getVatAmount();
            // Nếu DB total đã gồm VAT và gần bằng computed → dùng DB total (avoid rounding drift)
            if (Math.abs(totalAmount - computed) < 1.0 && totalAmount > 0) return totalAmount;
            return computed;
        }

        /**
         * Tiền khách đưa ước tính (CASH) = làm tròn lên bội 50.000đ gần nhất.
         * Ví dụ: 273.000đ → 300.000đ.
         */
        public long getCashReceived() {
            double grand = getGrandTotal();
            return (long) (Math.ceil(grand / 50_000.0) * 50_000);
        }

        /** Tiền thừa = tiền khách đưa − tổng cộng. */
        public long getChange() {
            return getCashReceived() - (long) getGrandTotal();
        }

        public String getPaymentMethodLabel() {
            return paymentMethod != null ? paymentMethod.getLabel() : "Tiền mặt";
        }
    }
}