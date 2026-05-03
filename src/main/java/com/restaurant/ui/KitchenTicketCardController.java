package com.restaurant.ui;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

import com.restaurant.dao.KitchenDAO.KitchenTicket;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * KitchenTicketCardController  ─  Phase 8
 *
 * <p>Controller cho {@code KitchenTicketCard.fxml}.
 * Được khởi tạo bởi {@link KitchenController#buildCard} qua FXMLLoader.
 */
public class KitchenTicketCardController implements Initializable {

    // Urgency thresholds (phút) — giống Swing version
    private static final int WARN_MINUTES   = 10;
    private static final int DANGER_MINUTES = 20;

    private static final String COLOR_SUCCESS = "#10B981";
    private static final String COLOR_WARNING = "#F59E0B";
    private static final String COLOR_DANGER  = "#EF4444";

    // ─── FXML ─────────────────────────────────────────────────────────────────

    @FXML private VBox  cardRoot;
    @FXML private Label itemNameLabel;
    @FXML private Label quantityLabel;
    @FXML private Label waitLabel;
    @FXML private Label staffLabel;

    // ─── Initializable ────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // nothing – data injected via bind()
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Bind dữ liệu cho card PENDING.
     *
     * @param itemName  tên món
     * @param tickets   danh sách tickets thuộc nhóm này
     * @param onClick   callback khi click card
     */
    public void bindPending(String itemName,
                            List<KitchenTicket> tickets,
                            Runnable onClick) {
        itemNameLabel.setText(itemName);

        int totalQty = sumQuantity(tickets);
        quantityLabel.setText("Số lượng chờ: " + totalQty);

        long waitMin = calcWaitMinutes(tickets);
        applyWaitStyle(waitMin);

        staffLabel.setVisible(false);
        staffLabel.setManaged(false);

        if (onClick != null) {
            cardRoot.setOnMouseClicked(e -> onClick.run());
        }
        attachHoverEffect();
    }

    /**
     * Bind dữ liệu cho card COOKING.
     *
     * @param itemName  tên món
     * @param tickets   danh sách tickets thuộc nhóm này
     * @param onClick   callback khi click card
     */
    public void bindCooking(String itemName,
                            List<KitchenTicket> tickets,
                            Runnable onClick) {
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

        if (onClick != null) {
            cardRoot.setOnMouseClicked(e -> onClick.run());
        }
        attachHoverEffect();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

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