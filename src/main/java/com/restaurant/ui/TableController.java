package com.restaurant.ui;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import com.restaurant.dao.OrderDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.Order;
import com.restaurant.model.TableItem;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;
import com.restaurant.ui.dialog.OpenTableDialogController;
import com.restaurant.ui.dialog.PaymentDialogController;
import com.restaurant.ui.dialog.TableDialogController;
import com.restaurant.websocket.RestaurantEventClient;
import com.restaurant.websocket.WsTopic;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.TilePane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * TableController — Phase 5
 *
 * <p>Điều khiển {@code TableView.fxml}:
 * <ul>
 *   <li>Load dữ liệu bàn từ {@link TableDAO} trên background thread.</li>
 *   <li>Dựng lưới card ({@link TableCardController}) trong {@link TilePane}.</li>
 *   <li>Lọc theo tên, sức chứa, trạng thái.</li>
 *   <li>Double-click card: RANH → {@link OpenTableDialogController},
 *       BAN → {@link PaymentDialogController}.</li>
 *   <li>Nút hành động trên card: Xóa / Sửa / Chi tiết.</li>
 * </ul>
 */
public class TableController implements Initializable {

    // ─── FXML fields ──────────────────────────────────────────────────────────

    @FXML private TilePane     tilePaneCards;
    @FXML private TextField    searchField;
    @FXML private ComboBox<String> capacityFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button       btnAdd;
    @FXML private Label        lblCount;

    // ─── State ────────────────────────────────────────────────────────────────

    private final List<TableItem> allItems       = new ArrayList<>();
    private final List<TableItem> displayedItems = new ArrayList<>();

    /** Token để huỷ đăng ký WS handler khi controller bị huỷ. */
    private Runnable cancelWsHandler;

    // ─── Initializable ────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupFilters();
        setupSearchListener();
        applyRbac();
        loadData();
        setupWsSubscription();
        // Polling fallback: làm mới trạng thái bàn mỗi 8 giây
        // (bổ sung cho WS để đảm bảo luôn cập nhật dù WS gặp vấn đề)
        javafx.application.Platform.runLater(() ->
            com.restaurant.ui.fx.util.PollManagerFx.getInstance().register(
                "table_status_poll", this::loadData, 8_000)
        );
    }

    // ─── WebSocket subscription ────────────────────────────────────────────────

    /**
     * Subscribe WsTopic.TABLES: mỗi khi có thay đổi trạng thái bàn (tablet mở,
     * thanh toán xong…), server broadcast → TableController gọi loadData() tự động.
     */
    private void setupWsSubscription() {
        long myRid = com.restaurant.session.AppSession.getInstance().getRestaurantId();
        boolean isSuperAdmin = RbacGuard.getInstance().isSuperAdmin();
        RestaurantEventClient ws = RestaurantEventClient.getInstance();

        cancelWsHandler = ws.addEventHandler(event -> {
            if (event == null) return;
            if (!WsTopic.TABLES.equals(event.getTopic())) return;
            // SUPER_ADMIN nhận mọi event; nhà hàng chỉ nhận event của mình
            if (!isSuperAdmin && event.getRestaurantId() != myRid) return;
            Platform.runLater(this::loadData);
        });

        ws.subscribe(WsTopic.TABLES);
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private void setupFilters() {
        capacityFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "2", "4", "6", "8", "10", "12+"));
        capacityFilter.getSelectionModel().selectFirst();

        statusFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Rảnh", "Bận", "Đặt trước", "Cần dọn", "Đang dọn"));
        statusFilter.getSelectionModel().selectFirst();
    }

    private void setupSearchListener() {
        // Live filter on every keystroke
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
    }

    /** Ẩn nút "Thêm bàn" nếu user không có quyền {@link Permission#ADD_TABLE}. */
    private void applyRbac() {
        btnAdd.setVisible(RbacGuard.getInstance().can(Permission.ADD_TABLE));
        btnAdd.setManaged(btnAdd.isVisible());
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    /**
     * Load tất cả bàn của nhà hàng hiện tại trên background thread,
     * sau đó cập nhật UI trên JavaFX Application Thread.
     */
    public void loadData() {
        Task<List<TableItem>> task = new Task<>() {
            @Override
            protected List<TableItem> call() {
                return new TableDAO().getAll();
            }
        };

        task.setOnSucceeded(e -> {
            allItems.clear();
            allItems.addAll(task.getValue());
            applyFilter();
        });

        task.setOnFailed(e ->
            System.err.println("[TableController] loadData lỗi: "
                    + task.getException().getMessage())
        );

        new Thread(task, "table-loader").start();
    }

    // ─── Filter ───────────────────────────────────────────────────────────────

    @FXML
    private void onFilterChanged() {
        applyFilter();
    }

    private void applyFilter() {
        String search = searchField.getText().trim().toLowerCase();
        String cap    = capacityFilter.getValue();
        String status = statusFilter.getValue();

        displayedItems.clear();
        displayedItems.addAll(allItems.stream().filter(t -> {
            boolean matchName = search.isEmpty()
                    || t.getName().toLowerCase().contains(search);

            boolean matchCap = "Tất cả".equals(cap)
                    || ("12+".equals(cap) && t.getCapacity() >= 12)
                    || (!cap.equals("Tất cả") && !cap.equals("12+")
                            && t.getCapacity() == Integer.parseInt(cap));

            boolean matchStatus = "Tất cả".equals(status)
                    || t.getStatusDisplay().equalsIgnoreCase(status);

            return matchName && matchCap && matchStatus;
        }).collect(Collectors.toList()));

        rebuildCards();

        int n = displayedItems.size();
        lblCount.setText(n + " bàn");
    }

    // ─── Card grid ────────────────────────────────────────────────────────────

    /** Xóa TilePane và tạo lại card cho từng item trong {@code displayedItems}. */
    private void rebuildCards() {
        tilePaneCards.getChildren().clear();

        for (TableItem item : displayedItems) {
            try {
                TableCardController ctrl = new TableCardController();

                ctrl.setData(item);
                ctrl.setOnDelete(this::handleDelete);
                ctrl.setOnEdit(this::handleEdit);
                ctrl.setOnDetail(this::handleDetail);
                ctrl.setOnDoubleClick(this::handleDoubleClick);

                tilePaneCards.getChildren().add(ctrl);

            } catch (RuntimeException ex) {
                System.err.println("[TableController] rebuildCards lỗi: " + ex.getMessage());
            }
        }
    }

    // ─── FXML handlers ────────────────────────────────────────────────────────

    @FXML
    private void onAddTable() {
        openTableFormDialog(null);
    }

    // ─── Card callbacks ───────────────────────────────────────────────────────

    private void handleDoubleClick(TableItem item) {
        switch (item.getStatus()) {
            case RANH:
                if (RbacGuard.getInstance().can(Permission.OPEN_TABLE))
                    openTableForGuest(item);
                break;
            case BAN:
                if (RbacGuard.getInstance().can(Permission.UPDATE_ORDER_STATUS))
                    openPaymentDialog(item);
                break;
            default:
                // no action for other statuses
                break;
        }
    }

    private void handleDelete(TableItem item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Xóa bàn \"" + item.getName() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                Task<Void> task = new Task<>() {
                    @Override protected Void call() {
                        new TableDAO().delete(item.getId());
                        return null;
                    }
                };
                task.setOnSucceeded(e -> loadData());
                task.setOnFailed(e -> showError("Xóa bàn thất bại: "
                        + task.getException().getMessage()));
                new Thread(task, "table-delete").start();
            }
        });
    }

    private void handleEdit(TableItem item) {
        openTableFormDialog(item);
    }

    private void handleDetail(TableItem item) {
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("Chi tiết bàn");
        dlg.setHeaderText("Bàn: " + item.getName());
        dlg.setContentText(
                "ID       : " + item.getId()            + "\n" +
                "Tên bàn  : " + item.getName()          + "\n" +
                "Sức chứa : " + item.getCapacity() + " người\n" +
                "Trạng thái: " + item.getStatusDisplay()
        );
        dlg.showAndWait();
    }

    // ─── Dialog openers ───────────────────────────────────────────────────────

    /**
     * Mở dialog thêm/sửa bàn (dùng lại {@code TableDialog.fxml}).
     *
     * @param item {@code null} = thêm mới; non-null = sửa.
     */
    private void openTableFormDialog(TableItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/TableDialog.fxml"));
            Parent root = loader.load();
            TableDialogController ctrl = loader.getController();
            ctrl.setItem(item);

            // Edit mode: pre-fill existing loginId so admin can see it
            if (item != null) {
                long restaurantId = com.restaurant.session.AppSession.getInstance().getRestaurantId();
                String existingLoginId = new com.restaurant.dao.UserDAO()
                        .getTabletLoginId(item.getId(), restaurantId);
                ctrl.setExistingLoginId(existingLoginId);
            }

            Stage stage = new Stage();
            stage.setTitle(item == null ? "Thêm bàn" : "Cập nhật bàn");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(getOwnerWindow());
            stage.setResizable(false);
            stage.showAndWait();

            if (ctrl.isSaved()) {
                TableItem savedItem   = ctrl.getResult();
                String loginId        = ctrl.getLoginId();
                String password       = ctrl.getPassword();
                long restaurantId     = com.restaurant.session.AppSession.getInstance().getRestaurantId();

                Task<Void> task = new Task<>() {
                    @Override protected Void call() {
                        com.restaurant.dao.UserDAO userDAO = new com.restaurant.dao.UserDAO();
                        if (item == null) {
                            // Create table first, get generated ID
                            TableItem created = new TableDAO().create(savedItem);
                            // Create tablet user account
                            userDAO.createTabletUser(created.getId(), restaurantId, loginId, password);
                        } else {
                            new TableDAO().update(savedItem);
                            // Update credentials: always update loginId; update password only if provided
                            if (!password.isEmpty()) {
                                userDAO.updateTabletUserPassword(savedItem.getId(), restaurantId, password);
                            }
                        }
                        return null;
                    }
                };
                task.setOnSucceeded(e -> loadData());
                task.setOnFailed(e -> showError("Lưu bàn thất bại: "
                        + task.getException().getMessage()));
                new Thread(task, "table-save").start();
            }

        } catch (IOException ex) {
            showError("Không mở được dialog: " + ex.getMessage());
        }
    }

    /**
     * Mở {@link OpenTableDialogController} để mở bàn cho khách.
     * Chỉ gọi khi status == RANH và user có quyền OPEN_TABLE.
     */
    private void openTableForGuest(TableItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/dialog/OpenTableDialog.fxml"));
            Parent root = loader.load();
            OpenTableDialogController ctrl = loader.getController();
            ctrl.init(item.getId(), item.getName());

            Stage stage = new Stage();
            stage.setTitle("Mở bàn – " + item.getName());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(getOwnerWindow());
            stage.setResizable(false);
            stage.showAndWait();

            if (ctrl.isConfirmed()) loadData();

        } catch (IOException ex) {
            showError("Không mở được dialog mở bàn: " + ex.getMessage());
        }
    }

    /**
     * Tìm đơn hàng đang hoạt động của bàn trên background thread,
     * sau đó mở {@link PaymentDialogController}.
     * Chỉ gọi khi status == BAN và user có quyền UPDATE_ORDER_STATUS.
     */
    private void openPaymentDialog(TableItem item) {
        Task<Order> task = new Task<>() {
            @Override
            protected Order call() {
                return new OrderDAO().getActiveOrderByTable(item.getId());
            }
        };

        task.setOnSucceeded(e -> {
            Order activeOrder = task.getValue();
            if (activeOrder == null) {
                showWarning("Không tìm thấy đơn hàng đang hoạt động cho bàn "
                        + item.getName() + ".");
                return;
            }
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/fxml/dialog/PaymentDialog.fxml"));
                Parent root = loader.load();
                PaymentDialogController ctrl = loader.getController();
                ctrl.init(item.getId(), item.getName(), activeOrder.getId());

                Stage stage = new Stage();
                stage.setTitle("Thanh toán — Bàn " + item.getName());
                stage.setScene(new Scene(root));
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.initOwner(getOwnerWindow());
                stage.setResizable(false);
                stage.showAndWait();

                if (ctrl.isPaymentCompleted()) loadData();

            } catch (IOException ex) {
                showError("Không mở được dialog thanh toán: " + ex.getMessage());
            }
        });

        task.setOnFailed(e ->
            showError("Lỗi khi tải đơn hàng: " + task.getException().getMessage())
        );

        new Thread(task, "order-fetch").start();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Window getOwnerWindow() {
        return tilePaneCards.getScene() != null
                ? tilePaneCards.getScene().getWindow()
                : null;
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            a.setHeaderText("Lỗi");
            a.showAndWait();
        });
    }

    private void showWarning(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
            a.setHeaderText("Thông báo");
            a.showAndWait();
        });
    }
}