package com.restaurant.ui;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.KitchenDAO.KitchenTicket;
import com.restaurant.data.DataManager;
import com.restaurant.model.MenuItem;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;
import com.restaurant.websocket.RestaurantEventClient;
import com.restaurant.websocket.WsTopic;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * KitchenController  ─  Phase 8
 *
 * <p>Controller cho {@code KitchenView.fxml}.
 *
 * <h3>Cơ chế cập nhật dữ liệu</h3>
 * Dùng WebSocket push thay cho Timer polling.
 * Khi nhận {@link WsTopic#KITCHEN} event khớp restaurantId hiện tại,
 * controller gọi {@code doPoll()} để tải lại dữ liệu.
 * Mỗi lần poll khởi động một {@link Task} trên daemon thread;
 * kết quả được apply trên FX thread qua {@code Platform.runLater}.
 *
 * <h3>Luồng dữ liệu</h3>
 * <pre>
 * WsEvent (KITCHEN, restaurantId)
 *   └─ doPoll()
 *        ├─ spinner.start()
 *        ├─ Task<KitchenData> (background)
 *        │    └─ dao.getActiveTickets(restaurantId)
 *        └─ setOnSucceeded / setOnFailed (FX thread)
 *              ├─ groupByItem()
 *              ├─ applyFilter()
 *              └─ rebuildCards()
 * </pre>
 */
public class KitchenController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(KitchenController.class.getName());

    // Category labels (đồng bộ với Swing version)
    private static final String[] CATEGORIES = {"Tất cả", "Món chính", "Đồ uống", "Tráng miệng"};

    // ─── FXML bindings ────────────────────────────────────────────────────────

    @FXML private HBox     headerBox;
    @FXML private Label    restaurantNameLabel;
    @FXML private Button   btnBack;

    @FXML private HBox     pendingFilterBar;
    @FXML private FlowPane pendingFlowPane;

    @FXML private HBox     cookingFilterBar;
    @FXML private FlowPane cookingFlowPane;

    // Placeholders from fx:include — replaced programmatically
    @FXML private StackPane pendingSpinner;   // SimpleSpinner.fxml root
    @FXML private HBox      errorBar;         // InlineErrorBar.fxml root

    // ─── Runtime components ───────────────────────────────────────────────────

    private SimpleSpinnerFx   spinner;
    private InlineErrorBarFx  inlineError;

    // ─── State ────────────────────────────────────────────────────────────────

    private final KitchenDAO dao = new KitchenDAO();

    private List<MenuItem>                      allMenuItems    = new ArrayList<>();
    private Map<String, List<KitchenTicket>>    allPendingGroups = new LinkedHashMap<>();
    private Map<String, List<KitchenTicket>>    allCookingGroups = new LinkedHashMap<>();

    private String selectedPendingCategory = null;
    private String selectedCookingCategory = null;
    private int    lastPendingCount        = 0;
    private Runnable cancelWsHandler;

    // ─── Initializable ────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Permission check
        if (!AppSession.getInstance().hasPermission(Permission.VIEW_KITCHEN)) {
            showAccessDenied();
            return;
        }

        // Load menu items for category lookup
        try {
            allMenuItems = DataManager.getInstance().getMenuItems();
        } catch (Exception ignored) {}

        // Restaurant name
        try {
            String name = DataManager.getInstance().getMyRestaurant().getName();
            if (name != null && !name.isBlank()) restaurantNameLabel.setText(name);
        } catch (Exception ignored) {}

        // Replace placeholder spinner with real SimpleSpinnerFx
        injectSpinner();

        // Replace placeholder error bar
        injectErrorBar();

        // Build category toggle bars
        buildFilterBar(pendingFilterBar, true);
        buildFilterBar(cookingFilterBar, false);

        // Initial load
        doPoll();

        // Subscribe WebSocket KITCHEN topic — nhận push thay vì poll định kỳ
        long sessionRestaurantId = AppSession.getInstance().getRestaurantId();
        RestaurantEventClient wsClient = RestaurantEventClient.getInstance();
        wsClient.subscribe(WsTopic.KITCHEN);
        cancelWsHandler = wsClient.addEventHandler(event -> {
            if (WsTopic.KITCHEN.equals(event.getTopic())
                    && event.getRestaurantId() == sessionRestaurantId) {
                doPoll();
            }
        });
    }

    // ─── Spinner injection ────────────────────────────────────────────────────

    /**
     * Thay thế StackPane placeholder (từ fx:include SimpleSpinner.fxml)
     * bằng instance {@link SimpleSpinnerFx} thực sự.
     */
    private void injectSpinner() {
        spinner = new SimpleSpinnerFx(20, Color.web("#3B82F6"));

        // pendingSpinner là StackPane từ fx:include → lấy parent HBox (titleRow)
        // và thay thế bằng spinner thực
        if (pendingSpinner != null) {
            // Tìm parent của pendingSpinner trong scene graph
            Node parent = pendingSpinner.getParent();
            if (parent instanceof HBox hb) {
                int idx = hb.getChildren().indexOf(pendingSpinner);
                if (idx >= 0) {
                    hb.getChildren().set(idx, spinner);
                } else {
                    hb.getChildren().add(spinner);
                }
            }
        }
    }

    /**
     * Thay thế HBox placeholder (từ fx:include InlineErrorBar.fxml)
     * bằng instance {@link InlineErrorBarFx} thực sự.
     */
    private void injectErrorBar() {
        inlineError = new InlineErrorBarFx();

        if (errorBar != null) {
            Node parent = errorBar.getParent();
            if (parent instanceof HBox hb) {
                int idx = hb.getChildren().indexOf(errorBar);
                if (idx >= 0) {
                    hb.getChildren().set(idx, inlineError);
                } else {
                    // fallback: thêm vào headerBox trước nút Back
                    int backIdx = headerBox.getChildren().indexOf(btnBack);
                    if (backIdx > 0) {
                        headerBox.getChildren().add(backIdx, inlineError);
                    } else {
                        headerBox.getChildren().add(inlineError);
                    }
                }
            }
        }
    }

    // ─── Filter bar ───────────────────────────────────────────────────────────

    private void buildFilterBar(HBox filterBar, boolean isPending) {
        filterBar.getChildren().clear();
        filterBar.setSpacing(8);
        filterBar.setPadding(new Insets(8, 0, 8, 0));

        ToggleGroup group = new ToggleGroup();

        for (String cat : CATEGORIES) {
            ToggleButton tb = makeCategoryToggle(cat, group);
            if (cat.equals("Tất cả")) tb.setSelected(true);

            tb.setOnAction(e -> {
                String selected = cat.equals("Tất cả") ? null : cat;
                if (isPending) {
                    selectedPendingCategory = selected;
                    applyPendingFilter();
                } else {
                    selectedCookingCategory = selected;
                    applyCookingFilter();
                }
            });

            filterBar.getChildren().add(tb);
        }
    }

    private ToggleButton makeCategoryToggle(String text, ToggleGroup group) {
        ToggleButton tb = new ToggleButton(text);
        tb.setToggleGroup(group);
        tb.setCursor(javafx.scene.Cursor.HAND);

        final String normalStyle =
            "-fx-font-family: 'Segoe UI';" +
            "-fx-font-size: 12px;" +
            "-fx-background-color: #FFFFFF;" +
            "-fx-border-color: #3B82F6;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-text-fill: #3B82F6;" +
            "-fx-padding: 5 14 5 14;" +
            "-fx-cursor: hand;";

        final String selectedStyle =
            "-fx-font-family: 'Segoe UI';" +
            "-fx-font-size: 12px;" +
            "-fx-background-color: #3B82F6;" +
            "-fx-border-color: #3B82F6;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-text-fill: #FFFFFF;" +
            "-fx-padding: 5 14 5 14;" +
            "-fx-cursor: hand;";

        tb.setStyle(normalStyle);

        // Cập nhật style khi selected thay đổi
        tb.selectedProperty().addListener((obs, old, now) ->
            tb.setStyle(now ? selectedStyle : normalStyle)
        );

        return tb;
    }

    // ─── WebSocket lifecycle ──────────────────────────────────────────────────

    /**
     * Hủy đăng ký nhận event KITCHEN (gọi khi panel đóng).
     * Xóa event handler khỏi {@link RestaurantEventClient} để tránh
     * callback vào controller đã bị giải phóng.
     */
    public void stopPolling() {
        if (cancelWsHandler != null) { cancelWsHandler.run(); cancelWsHandler = null; }
    }

    // ─── doPoll ───────────────────────────────────────────────────────────────

    /**
     * Tải dữ liệu bất đồng bộ bằng {@link Task} trên daemon thread.
     * Spinner hiện trong suốt quá trình; InlineErrorBar hiện khi thất bại.
     */
    private void doPoll() {
        long restaurantId = AppSession.getInstance().getRestaurantId();

        // Hiện spinner
        if (spinner != null) spinner.start();

        Task<KitchenData> task = new Task<>() {
            @Override
            protected KitchenData call() {
                List<KitchenTicket> all = dao.getActiveTickets(restaurantId);

                List<KitchenTicket> pending = new ArrayList<>();
                List<KitchenTicket> cooking = new ArrayList<>();

                for (KitchenTicket ticket : all) {
                    switch (ticket.itemStatus) {
                        case PENDING:
                        case ACCEPTED:
                            pending.add(ticket);
                            break;
                        case COOKING:
                            cooking.add(ticket);
                            break;
                        default:
                            break;
                    }
                }
                return new KitchenData(pending, cooking);
            }
        };

        task.setOnSucceeded(e -> {
            if (spinner != null) spinner.stop();

            KitchenData data = task.getValue();

            allPendingGroups = groupByItem(data.pending);
            allCookingGroups = groupByItem(data.cooking);
            applyPendingFilter();
            applyCookingFilter();

            // Toast khi có món mới
            int newCount = data.pending.size();
            if (newCount > lastPendingCount) {
                int diff = newCount - lastPendingCount;
                showToast("Có " + diff + " món mới cần chế biến!");
            }
            lastPendingCount = newCount;
        });

        task.setOnFailed(e -> {
            if (spinner != null) spinner.stop();
            Throwable ex = task.getException();
            LOGGER.log(Level.SEVERE, "[KitchenController] doPoll failed", ex);
            if (inlineError != null) {
                inlineError.show("Lỗi tải dữ liệu bếp: " +
                        (ex != null ? ex.getMessage() : "unknown"));
            }
        });

        Thread thread = new Thread(task, "kitchen-poll");
        thread.setDaemon(true);
        thread.start();
    }

    // ─── groupByItem ──────────────────────────────────────────────────────────

    /**
     * Nhóm danh sách tickets theo tên món (itemName).
     * Giữ thứ tự chèn (LinkedHashMap) như phiên bản Swing.
     */
    private static Map<String, List<KitchenTicket>> groupByItem(List<KitchenTicket> tickets) {
        Map<String, List<KitchenTicket>> map = new LinkedHashMap<>();
        for (KitchenTicket t : tickets) {
            map.computeIfAbsent(t.itemName, k -> new ArrayList<>()).add(t);
        }
        return map;
    }

    // ─── Filter ───────────────────────────────────────────────────────────────

    private void applyPendingFilter() {
        Map<String, List<KitchenTicket>> filtered = filterByCategory(
                allPendingGroups, selectedPendingCategory);
        rebuildCards(pendingFlowPane, filtered, true);
    }

    private void applyCookingFilter() {
        Map<String, List<KitchenTicket>> filtered = filterByCategory(
                allCookingGroups, selectedCookingCategory);
        rebuildCards(cookingFlowPane, filtered, false);
    }

    private Map<String, List<KitchenTicket>> filterByCategory(
            Map<String, List<KitchenTicket>> groups, String category) {
        if (category == null) return groups;
        return groups.entrySet().stream()
                .filter(e -> category.equals(getCategoryOf(e.getKey())))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private String getCategoryOf(String itemName) {
        for (MenuItem item : allMenuItems) {
            if (itemName.equals(item.getName())) {
                String cat = item.getCategory();
                return cat != null ? cat : "Khác";
            }
        }
        return "Khác";
    }

    // ─── rebuildCards ─────────────────────────────────────────────────────────

    /**
     * Xóa và tái tạo các card trong {@link FlowPane} từ {@code grouped}.
     *
     * @param flowPane  target FlowPane (pending hoặc cooking)
     * @param grouped   dữ liệu đã lọc
     * @param isPending true = pending card, false = cooking card
     */
    private void rebuildCards(FlowPane flowPane,
                               Map<String, List<KitchenTicket>> grouped,
                               boolean isPending) {
        flowPane.getChildren().clear();

        if (grouped.isEmpty()) {
            flowPane.getChildren().add(buildEmptyState(isPending));
            return;
        }

        for (Map.Entry<String, List<KitchenTicket>> entry : grouped.entrySet()) {
            Node card = buildCard(entry.getKey(), entry.getValue(), isPending);
            if (card != null) flowPane.getChildren().add(card);
        }
    }

    // ─── Card builder ─────────────────────────────────────────────────────────

    /**
     * Load {@code KitchenTicketCard.fxml}, bind dữ liệu rồi trả về root Node.
     */
    private Node buildCard(String itemName,
                            List<KitchenTicket> tickets,
                            boolean isPending) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/restaurant/ui/KitchenTicketCard.fxml"));
            Node root = loader.load();
            KitchenTicketCardController ctrl = loader.getController();

            if (isPending) {
                ctrl.bindPending(itemName, tickets,
                        () -> openDetailDialog(itemName, tickets, true),
                        this::doPoll);
            } else {
                ctrl.bindCooking(itemName, tickets,
                        () -> openDetailDialog(itemName, tickets, false),
                        this::doPoll);
            }

            return root;

        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Cannot load KitchenTicketCard.fxml", ex);
            // Fallback: plain label
            Label fallback = new Label(itemName);
            fallback.setStyle("-fx-text-fill: #EF4444;");
            return fallback;
        }
    }

    // ─── Empty state ──────────────────────────────────────────────────────────

    private Node buildEmptyState(boolean isPending) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));

        Label icon = new Label(isPending ? "🍽" : "👨‍🍳");
        icon.setStyle("-fx-font-size: 32px;");

        Label msg = new Label(isPending
                ? "Không có món nào đang chờ"
                : "Không có món nào đang chế biến");
        msg.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px;" +
                     "-fx-text-fill: #94A3B8;");

        box.getChildren().addAll(icon, msg);

        if (isPending) {
            Label sub = new Label("Tất cả đã được chế biến ✓");
            sub.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 12px;" +
                         "-fx-text-fill: #10B981;");
            box.getChildren().add(sub);
        }

        // Chiếm toàn bộ FlowPane width
        FlowPane.setMargin(box, new Insets(0));
        return box;
    }

    // ─── Detail Dialog (Phase 8 placeholder) ─────────────────────────────────

    private void openDetailDialog(String itemName,
                                   List<KitchenTicket> tickets,
                                   boolean isPending) {
        // Phase 8 – placeholder; tickets will be used in Phase 9 detail dialog
        LOGGER.log(Level.INFO, "[KitchenController] openDetailDialog: {0} isPending={1}",
                new Object[]{itemName, isPending});
    }

    // ─── Toast ────────────────────────────────────────────────────────────────

    private void showToast(String message) {
        Platform.runLater(() -> {
            if (pendingFlowPane.getScene() == null) {
                LOGGER.log(Level.INFO, "[KitchenController] Toast: {0}", message);
                return;
            }
            try {
                javafx.scene.layout.Pane root =
                    (javafx.scene.layout.Pane) pendingFlowPane.getScene().getRoot();

                Label toast = new Label(message);
                toast.setStyle(
                    "-fx-background-color: #1E293B;" +
                    "-fx-text-fill: #FFFFFF;" +
                    "-fx-font-family: 'Segoe UI';" +
                    "-fx-font-size: 13px;" +
                    "-fx-padding: 10 16;" +
                    "-fx-background-radius: 8;"
                );

                StackPane overlay = new StackPane(toast);
                StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
                overlay.setPadding(new Insets(0, 0, 32, 0));
                overlay.setPickOnBounds(false);
                overlay.setMouseTransparent(true);
                root.getChildren().add(overlay);

                javafx.animation.FadeTransition ft =
                    new javafx.animation.FadeTransition(Duration.seconds(1.0), overlay);
                ft.setFromValue(1.0);
                ft.setToValue(0.0);
                ft.setDelay(Duration.seconds(2.0));
                ft.setOnFinished(ev -> root.getChildren().remove(overlay));
                ft.play();

            } catch (Exception ex) {
                LOGGER.log(Level.INFO, "[KitchenController] Toast: {0}", message);
            }
        });
    }

    // ─── Back button ──────────────────────────────────────────────────────────

    @FXML
    private void onBack() {
        stopPolling();
        try {
            Stage stage = (Stage) btnBack.getScene().getWindow();
            stage.close();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Cannot close stage", ex);
        }
    }

    // ─── Access denied ────────────────────────────────────────────────────────

    private void showAccessDenied() {
        // Xóa tất cả children rồi hiện thông báo
        // (gọi sau initialize nên cần invokeLater)
        Platform.runLater(() -> {
            VBox denied = new VBox();
            denied.setAlignment(Pos.CENTER);
            Label lbl = new Label("Không có quyền truy cập");
            lbl.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 18px;" +
                         "-fx-text-fill: #94A3B8;");
            denied.getChildren().add(lbl);

            // Tìm scene root và thay thế center nếu là BorderPane
            Node root = btnBack.getScene() != null
                    ? btnBack.getScene().getRoot() : null;
            if (root instanceof BorderPane bp) {
                bp.setCenter(denied);
            }
        });
    }

    // ─── Inner class KitchenData ──────────────────────────────────────────────

    private record KitchenData(
            List<KitchenTicket> pending,
            List<KitchenTicket> cooking) {}
}