package com.restaurant.ui;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.restaurant.dao.KitchenDAO;
import com.restaurant.dao.KitchenDAO.KitchenTicket;
import com.restaurant.model.Order;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * KitchenTicketCardController  ─  Phase 8
 *
 * <p>Controller cho {@code KitchenTicketCard.fxml}.
 * Được khởi tạo bởi {@link KitchenController#buildCard} qua FXMLLoader.
 *
 * <p>Phase 8+: Bổ sung nút hành động cho bếp:
 * <ul>
 *   <li>{@code btnStartCooking} – chuyển tất cả ticket của nhóm từ
 *       PENDING/ACCEPTED sang COOKING (chỉ hiện ở cột Đang chờ).</li>
 *   <li>{@code btnMarkReady} – chuyển tất cả ticket từ COOKING sang
 *       READY (chỉ hiện ở cột Đang chế biến).</li>
 * </ul>
 * Sau khi update thành công, callback {@code onStatusChanged} được gọi
 * để {@link KitchenController} biết cần refresh dữ liệu.
 */
public class KitchenTicketCardController implements Initializable {

    private static final Logger LOGGER =
            Logger.getLogger(KitchenTicketCardController.class.getName());

    // Urgency thresholds (phút) — giống Swing version
    private static final int WARN_MINUTES   = 10;
    private static final int DANGER_MINUTES = 20;

    private static final String COLOR_SUCCESS = "#10B981";
    private static final String COLOR_WARNING = "#F59E0B";
    private static final String COLOR_DANGER  = "#EF4444";

    // ─── FXML ─────────────────────────────────────────────────────────────────

    @FXML private VBox   cardRoot;
    @FXML private Label  itemNameLabel;
    @FXML private Label  quantityLabel;
    @FXML private Label  waitLabel;
    @FXML private Label  staffLabel;
    @FXML private Button btnStartCooking;
    @FXML private Button btnMarkReady;
    @FXML private Button btnRejectItem;

    // ─── State ────────────────────────────────────────────────────────────────

    private final KitchenDAO dao = new KitchenDAO();

    /** Danh sách tickets hiện tại của card này. */
    private List<KitchenTicket> currentTickets;

    /** Callback được gọi sau khi thay đổi trạng thái thành công. */
    private Runnable onStatusChanged;

    // ─── Initializable ────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // nothing – data injected via bind()
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Bind dữ liệu cho card PENDING.
     * Hiện nút "Bắt đầu nấu" để chuyển sang COOKING.
     *
     * @param itemName        tên món
     * @param tickets         danh sách tickets thuộc nhóm này
     * @param onClick         callback khi click card (mở detail)
     * @param onStatusChanged callback khi trạng thái được thay đổi
     */
    public void bindPending(String itemName,
                            List<KitchenTicket> tickets,
                            Runnable onClick,
                            Runnable onStatusChanged) {
        this.currentTickets   = tickets;
        this.onStatusChanged  = onStatusChanged;

        itemNameLabel.setText(itemName);

        int totalQty = sumQuantity(tickets);
        quantityLabel.setText("Số lượng chờ: " + totalQty);

        long waitMin = calcWaitMinutes(tickets);
        applyWaitStyle(waitMin);

        staffLabel.setVisible(false);
        staffLabel.setManaged(false);

        // Hiện nút Bắt đầu nấu và Không nhận món
        showButton(btnStartCooking);
        hideButton(btnMarkReady);
        showButton(btnRejectItem);

        if (onClick != null) {
            cardRoot.setOnMouseClicked(e -> {
                // Nếu click vào nút thì không mở detail
                if (!isButtonEvent(e)) onClick.run();
            });
        }
        attachHoverEffect();
    }

    /**
     * Overload tương thích ngược (không có onStatusChanged).
     */
    public void bindPending(String itemName,
                            List<KitchenTicket> tickets,
                            Runnable onClick) {
        bindPending(itemName, tickets, onClick, null);
    }

    /**
     * Bind dữ liệu cho card COOKING.
     * Hiện nút "Hoàn thành" để chuyển sang READY.
     *
     * @param itemName        tên món
     * @param tickets         danh sách tickets thuộc nhóm này
     * @param onClick         callback khi click card (mở detail)
     * @param onStatusChanged callback khi trạng thái được thay đổi
     */
    public void bindCooking(String itemName,
                            List<KitchenTicket> tickets,
                            Runnable onClick,
                            Runnable onStatusChanged) {
        this.currentTickets  = tickets;
        this.onStatusChanged = onStatusChanged;

        itemNameLabel.setText(itemName);

        int totalQty = sumQuantity(tickets);
        quantityLabel.setText("Số lượng: " + totalQty);

        // Cooking: hiện thời gian đang chế biến
        long cookMin = calcWaitMinutes(tickets);
        applyWaitStyle(cookMin);
        waitLabel.setText("Đang nấu: " + cookMin + " phút");

        // Staff label
        String assignedTo = resolveAssignedTo(tickets);
        staffLabel.setText(assignedTo);
        staffLabel.setVisible(true);
        staffLabel.setManaged(true);

        // Hiện nút Hoàn thành và Không nhận món
        hideButton(btnStartCooking);
        showButton(btnMarkReady);
        showButton(btnRejectItem);

        if (onClick != null) {
            cardRoot.setOnMouseClicked(e -> {
                if (!isButtonEvent(e)) onClick.run();
            });
        }
        attachHoverEffect();
    }

    /**
     * Overload tương thích ngược (không có onStatusChanged).
     */
    public void bindCooking(String itemName,
                            List<KitchenTicket> tickets,
                            Runnable onClick) {
        bindCooking(itemName, tickets, onClick, null);
    }

    // ─── FXML action handlers ─────────────────────────────────────────────────

    /**
     * Xử lý click "Bắt đầu nấu": cập nhật tất cả tickets sang COOKING.
     */
    @FXML
    private void onStartCooking() {
        if (currentTickets == null || currentTickets.isEmpty()) return;
        updateAllTickets(Order.OrderItem.ItemStatus.COOKING, btnStartCooking);
    }

    /**
     * Xử lý click "Hoàn thành": cập nhật tất cả tickets sang READY.
     */
    @FXML
    private void onMarkReady() {
        if (currentTickets == null || currentTickets.isEmpty()) return;
        updateAllTickets(Order.OrderItem.ItemStatus.READY, btnMarkReady);
    }

    /**
     * Xử lý click "Không nhận món": hủy tất cả tickets trong nhóm.
     * Bếp dùng khi không thể chế biến món (hết nguyên liệu, sự cố, ...).
     * Tablet sẽ nhận WS event ORDERS và hiển thị "Đã hủy" cho khách.
     */
    @FXML
    private void onRejectItem() {
        if (currentTickets == null || currentTickets.isEmpty()) return;

        // Xác nhận trước khi hủy — tránh bấm nhầm
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận không nhận món");
        confirm.setHeaderText(null);
        confirm.setContentText(
            "Bếp không thể chế biến món \"" + itemNameLabel.getText() + "\"?\n" +
            "Món sẽ bị hủy và khách hàng sẽ được thông báo."
        );
        confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                updateAllTickets(Order.OrderItem.ItemStatus.CANCELLED, btnRejectItem);
            }
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Cập nhật trạng thái tất cả tickets trong nhóm bất đồng bộ.
     * Nút bị vô hiệu hoá trong khi đang xử lý.
     *
     * @param newStatus  trạng thái mới
     * @param sourceBtn  nút kích hoạt (để disable/enable)
     */
    private void updateAllTickets(Order.OrderItem.ItemStatus newStatus, Button sourceBtn) {
        setButtonLoading(sourceBtn, true);

        List<KitchenTicket> snapshots = List.copyOf(currentTickets);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                boolean allOk = true;
                for (KitchenTicket ticket : snapshots) {
                    boolean ok = dao.updateItemStatus(ticket.itemId, newStatus);
                    if (!ok) {
                        LOGGER.log(Level.WARNING,
                                "[KitchenTicketCard] updateItemStatus failed – itemId={0}, newStatus={1}",
                                new Object[]{ticket.itemId, newStatus});
                        allOk = false;
                    }
                }
                return allOk;
            }
        };

        task.setOnSucceeded(e -> {
            setButtonLoading(sourceBtn, false);
            if (onStatusChanged != null) {
                onStatusChanged.run();
            }
        });

        task.setOnFailed(e -> {
            setButtonLoading(sourceBtn, false);
            LOGGER.log(Level.SEVERE,
                    "[KitchenTicketCard] updateAllTickets failed",
                    task.getException());
        });

        Thread thread = new Thread(task, "kitchen-update-status");
        thread.setDaemon(true);
        thread.start();
    }

    /** Hiển thị nút và bật managed layout. */
    private void showButton(Button btn) {
        if (btn == null) return;
        btn.setVisible(true);
        btn.setManaged(true);
    }

    /** Ẩn nút và tắt managed layout (không chiếm không gian). */
    private void hideButton(Button btn) {
        if (btn == null) return;
        btn.setVisible(false);
        btn.setManaged(false);
    }

    /** Disable nút và đổi text thành "Đang xử lý…" khi loading. */
    private void setButtonLoading(Button btn, boolean loading) {
        if (btn == null) return;
        btn.setDisable(loading);
        if (loading) {
            btn.setUserData(btn.getText()); // lưu text gốc
            btn.setText("Đang xử lý…");
        } else {
            Object orig = btn.getUserData();
            if (orig instanceof String s) btn.setText(s);
        }
    }

    /**
     * Kiểm tra MouseEvent có xuất phát từ một nút con không,
     * để tránh mở dialog khi click nút.
     */
    private boolean isButtonEvent(javafx.scene.input.MouseEvent e) {
        javafx.scene.Node target = (javafx.scene.Node) e.getTarget();
        while (target != null && target != cardRoot) {
            if (target instanceof Button) return true;
            target = target.getParent();
        }
        return false;
    }

    private void applyWaitStyle(long minutes) {
        String color;
        boolean bold;
        if (minutes < WARN_MINUTES) {
            color = COLOR_SUCCESS;
            bold  = false;
            waitLabel.setText("Chờ lâu nhất: " + minutes + " phút");
        } else if (minutes <= DANGER_MINUTES) {
            color = COLOR_WARNING;
            bold  = false;
            waitLabel.setText("Chờ lâu nhất: " + minutes + " phút");
        } else {
            color = COLOR_DANGER;
            bold  = true;
            waitLabel.setText("⚠ Chờ lâu nhất: " + minutes + " phút");
        }
        waitLabel.setStyle(
            "-fx-font-family: 'Segoe UI';" +
            "-fx-font-size: 13px;" +
            (bold ? "-fx-font-weight: bold;" : "") +
            "-fx-text-fill: " + color + ";"
        );
    }

    private void attachHoverEffect() {
        final String normalStyle =
            "-fx-background-color: #FFFFFF;" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: #E2E8F0;" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 4, 0, 0, 2);" +
            "-fx-cursor: hand;";

        final String hoverStyle =
            "-fx-background-color: #F0F9FF;" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: #93C5FD;" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(59,130,246,0.12), 6, 0, 0, 3);" +
            "-fx-cursor: hand;";

        cardRoot.setStyle(normalStyle);
        cardRoot.setOnMouseEntered(e -> cardRoot.setStyle(hoverStyle));
        cardRoot.setOnMouseExited(e  -> cardRoot.setStyle(normalStyle));
    }

    private long calcWaitMinutes(List<KitchenTicket> tickets) {
        return tickets.stream()
                .map(t -> t.createdAt)
                .filter(Objects::nonNull)
                .mapToLong(dt -> Duration.between(dt, LocalDateTime.now()).toMinutes())
                .max()
                .orElse(0L);
    }

    private int sumQuantity(List<KitchenTicket> tickets) {
        return tickets.stream().mapToInt(t -> t.quantity).sum();
    }

    private String resolveAssignedTo(List<KitchenTicket> tickets) {
        if (tickets.isEmpty()) return "Đang chế biến";
        KitchenTicket first = tickets.get(0);
        if (first.assignedTo != null && !first.assignedTo.isBlank()) {
            return first.assignedTo;
        }
        return "Đang chế biến";
    }
}