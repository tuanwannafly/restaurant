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
import com.restaurant.dao.KitchenDAO.UpdateResult;
import com.restaurant.db.KitchenLockService;
import com.restaurant.model.Order;
import com.restaurant.session.AppSession;

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
    private final KitchenLockService lockService = KitchenLockService.getInstance();

    /** Danh sách tickets hiện tại của card này. */
    private List<KitchenTicket> currentTickets;

    /** Callback được gọi sau khi thay đổi trạng thái thành công. */
    private Runnable onStatusChanged;

    /**
     * true = card này đang ở cột "Đang chế biến" (COOKING).
     * false = card đang ở cột "Đang chờ" (PENDING).
     * Dùng để phân biệt hành vi của nút "Không nhận món".
     */
    private boolean isCookingCard = false;

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
        this.isCookingCard    = false;

        itemNameLabel.setText(itemName);

        int totalQty = sumQuantity(tickets);
        quantityLabel.setText("Số lượng chờ: " + totalQty);

        long waitMin = calcWaitMinutes(tickets);
        applyWaitStyle(waitMin);

        staffLabel.setVisible(false);
        staffLabel.setManaged(false);

        // Hiện nút Bắt đầu nấu và Không nhận món (pending → cancelled)
        showButton(btnStartCooking);
        hideButton(btnMarkReady);
        showButton(btnRejectItem);
        btnRejectItem.setText("✕ Không nhận món");

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
        this.isCookingCard   = true;

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

        // Hiện nút Hoàn thành và nút Trả lại (cooking → pending)
        hideButton(btnStartCooking);
        showButton(btnMarkReady);
        showButton(btnRejectItem);
        btnRejectItem.setText("↩ Trả lại hàng chờ");

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
     * Xử lý click "Bắt đầu nấu": nguyên tử gán chef và chuyển sang COOKING.
     * Dùng {@link KitchenDAO#assignAndStart} thay vì updateItemStatusSafe.
     */
    @FXML
    private void onStartCooking() {
        if (currentTickets == null || currentTickets.isEmpty()) return;

        long employeeId = com.restaurant.session.AppSession.getInstance().getUserId();
        setButtonLoading(btnStartCooking, true);

        List<KitchenTicket> snapshots = List.copyOf(currentTickets);

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                int success = 0, alreadyChanged = 0, error = 0;

                for (KitchenTicket ticket : snapshots) {
                    String itemId = ticket.itemId;

                    // Tầng Application: Redis / in-memory distributed lock
                    if (!lockService.tryAcquire(itemId)) {
                        alreadyChanged++;
                        continue;
                    }
                    try {
                        // Tầng DB: atomic assign + status change
                        UpdateResult result = dao.assignAndStart(itemId, employeeId);
                        switch (result) {
                            case SUCCESS        -> success++;
                            case ALREADY_CHANGED -> alreadyChanged++;
                            case ERROR          -> error++;
                        }
                    } finally {
                        lockService.release(itemId);
                    }
                }

                if (alreadyChanged > 0 && success == 0) return "ALREADY_CHANGED";
                if (error > 0 && success == 0)          return "ERROR";
                return "OK";
            }
        };

        task.setOnSucceeded(e -> {
            setButtonLoading(btnStartCooking, false);
            if ("ALREADY_CHANGED".equals(task.getValue())) showConflictAlert();
            if (onStatusChanged != null) onStatusChanged.run();
        });
        task.setOnFailed(e -> {
            setButtonLoading(btnStartCooking, false);
            LOGGER.log(Level.SEVERE, "[KitchenTicketCard] onStartCooking failed", task.getException());
            if (onStatusChanged != null) onStatusChanged.run();
        });

        new Thread(task, "kitchen-assign").setDaemon(true);
        Thread t = new Thread(task, "kitchen-assign");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Xử lý click "Hoàn thành": cập nhật tất cả tickets sang READY.
     */
    /**
     * Xử lý click "Hoàn thành": chuyển tất cả tickets từ COOKING sang READY.
     * Chỉ món của chính đầu bếp này (đã được filter từ getCookingByChef) mới xuất hiện ở đây.
     */
    @FXML
    private void onMarkReady() {
        if (currentTickets == null || currentTickets.isEmpty()) return;
        updateAllTickets(Order.OrderItem.ItemStatus.COOKING,
                         Order.OrderItem.ItemStatus.READY, btnMarkReady);
    }

    /**
     * Xử lý click "Không nhận món":
     * <ul>
     *   <li>Nếu đang ở cột COOKING (isCooking=true): trả món về PENDING,
     *       xóa assigned_to → món quay lại cột chờ cho đầu bếp khác nhận.</li>
     *   <li>Nếu đang ở cột PENDING (isCooking=false): hủy mon (CANCELLED)
     *       — bếp không thể chế biến.</li>
     * </ul>
     */
    @FXML
    private void onRejectItem() {
        if (currentTickets == null || currentTickets.isEmpty()) return;

        if (isCookingCard) {
            // Trả món về PENDING
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Trả món lại");
            confirm.setHeaderText(null);
            confirm.setContentText(
                "Bạn muốn trả món \"" + itemNameLabel.getText() + "\" về hàng chờ?\n" +
                "Đầu bếp khác có thể nhận món này."
            );
            confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) unassignAllTickets();
            });
        } else {
            // Hủy món (không thể chế biến)
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
                    Order.OrderItem.ItemStatus expected = currentTickets.get(0).itemStatus;
                    updateAllTickets(expected, Order.OrderItem.ItemStatus.CANCELLED, btnRejectItem);
                }
            });
        }
    }

    /**
     * Trả tất cả tickets trong nhóm về PENDING, xóa assigned_to.
     * Chỉ gọi từ cột COOKING.
     */
    private void unassignAllTickets() {
        long employeeId = com.restaurant.session.AppSession.getInstance().getUserId();
        setButtonLoading(btnRejectItem, true);

        List<KitchenTicket> snapshots = List.copyOf(currentTickets);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (KitchenTicket ticket : snapshots) {
                    boolean ok = dao.unassignAndReset(ticket.itemId, employeeId);
                    if (!ok) {
                        LOGGER.log(Level.WARNING,
                            "[KitchenTicketCard] unassignAndReset failed — itemId={0}", ticket.itemId);
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setButtonLoading(btnRejectItem, false);
            if (onStatusChanged != null) onStatusChanged.run();
        });
        task.setOnFailed(e -> {
            setButtonLoading(btnRejectItem, false);
            LOGGER.log(Level.SEVERE, "[KitchenTicketCard] unassignAllTickets failed", task.getException());
            if (onStatusChanged != null) onStatusChanged.run();
        });

        Thread t = new Thread(task, "kitchen-unassign");
        t.setDaemon(true);
        t.start();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Cập nhật trạng thái tất cả tickets trong nhóm bất đồng bộ.
     * Dùng {@link KitchenLockService} (Redis / in-memory fallback) để chặn
     * 2 đầu bếp cùng xử lý, và {@link KitchenDAO#updateItemStatusSafe}
     * để bảo vệ tầng DB bằng conditional UPDATE.
     *
     * @param expectedStatus trạng thái hiện tại mà đầu bếp đang nhìn thấy
     * @param newStatus      trạng thái muốn chuyển sang
     * @param sourceBtn      nút kích hoạt (để disable/enable)
     */
    private void updateAllTickets(Order.OrderItem.ItemStatus expectedStatus,
                                  Order.OrderItem.ItemStatus newStatus,
                                  Button sourceBtn) {
        setButtonLoading(sourceBtn, true);

        List<KitchenTicket> snapshots = List.copyOf(currentTickets);

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                int success = 0, alreadyChanged = 0, error = 0;

                for (KitchenTicket ticket : snapshots) {
                    String itemId = ticket.itemId;

                    // ── Tầng Application: Redis / in-memory distributed lock ──
                    if (!lockService.tryAcquire(itemId)) {
                        // Lock đang bị giữ bởi đầu bếp khác → bỏ qua ticket này
                        LOGGER.log(Level.INFO,
                            "[KitchenTicketCard] lock BUSY — itemId={0}, skipping", itemId);
                        alreadyChanged++;
                        continue;
                    }

                    try {
                        // ── Tầng DB: conditional UPDATE chống lost update ──
                        UpdateResult result =
                            dao.updateItemStatusSafe(itemId, expectedStatus, newStatus);

                        switch (result) {
                            case SUCCESS       -> success++;
                            case ALREADY_CHANGED -> {
                                alreadyChanged++;
                                LOGGER.log(Level.INFO,
                                    "[KitchenTicketCard] ALREADY_CHANGED — itemId={0}", itemId);
                            }
                            case ERROR         -> error++;
                        }
                    } finally {
                        lockService.release(itemId);
                    }
                }

                // Tóm tắt kết quả để trả về UI
                if (alreadyChanged > 0 && success == 0) {
                    return "ALREADY_CHANGED";
                } else if (error > 0 && success == 0) {
                    return "ERROR";
                } else {
                    return "OK";
                }
            }
        };

        task.setOnSucceeded(e -> {
            setButtonLoading(sourceBtn, false);
            String outcome = task.getValue();
            if ("ALREADY_CHANGED".equals(outcome)) {
                showConflictAlert();
            } else if ("ERROR".equals(outcome)) {
                LOGGER.log(Level.SEVERE, "[KitchenTicketCard] updateAllTickets returned ERROR");
            }
            if (onStatusChanged != null) {
                onStatusChanged.run();
            }
        });

        task.setOnFailed(e -> {
            setButtonLoading(sourceBtn, false);
            LOGGER.log(Level.SEVERE,
                    "[KitchenTicketCard] updateAllTickets task failed",
                    task.getException());
            if (onStatusChanged != null) onStatusChanged.run();
        });

        Thread thread = new Thread(task, "kitchen-update-status");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Hiển thị thông báo khi món đã được đầu bếp khác xử lý trước.
     */
    private void showConflictAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Món đã được xử lý");
        alert.setHeaderText(null);
        alert.setContentText(
            "Món \"" + itemNameLabel.getText() + "\" đã được đầu bếp khác cập nhật.\n" +
            "Danh sách sẽ được làm mới."
        );
        alert.showAndWait();
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