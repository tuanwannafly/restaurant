package com.restaurant.ui.dialog;

import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import com.restaurant.dao.OrderDAO;
import com.restaurant.model.Order;
import com.restaurant.ui.StatusBadgeTableCell;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

public class OrderStatController implements Initializable {

    // ─── FXML ────────────────────────────────────────────────────────────────

    @FXML private Label lblServing;
    @FXML private Label lblDone;
    @FXML private Label lblCancelled;
    @FXML private Label lblRevenue;

    @FXML private TableView<Order>          statTable;
    @FXML private TableColumn<Order,String> colId;
    @FXML private TableColumn<Order,String> colTable;
    @FXML private TableColumn<Order,String> colTotal;
    @FXML private TableColumn<Order,String> colStatus;
    @FXML private TableColumn<Order,String> colTime;

    // ─── State ───────────────────────────────────────────────────────────────

    private final OrderDAO dao = new OrderDAO();

    private static final NumberFormat NF =
        NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // ─── Init ─────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupColumns();
        loadStats();
    }

    // ─── Column setup ─────────────────────────────────────────────────────────

    private void setupColumns() {
        colId.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getId()));
        colId.setCellFactory(col -> centeredStatCell());

        colTable.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getTableName()));
        colTable.setCellFactory(col -> centeredStatCell());

        colTotal.setCellValueFactory(d ->
            new SimpleStringProperty(
                NF.format((long) d.getValue().getTotalAmount()) + " đ"));
        colTotal.setCellFactory(col -> rightStatCell());

        colStatus.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getStatusDisplay()));
        colStatus.setCellFactory(col -> new StatusBadgeTableCell<>());

        colTime.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getCreatedTime()));
        colTime.setCellFactory(col -> centeredStatCell());
    }

    // ─── Load ─────────────────────────────────────────────────────────────────

    private void loadStats() {
        Task<List<Order>> task = new Task<>() {
            @Override protected List<Order> call() { return dao.getAll(); }
        };
        task.setOnSucceeded(e -> {
            List<Order> orders = task.getValue();

            long serving   = orders.stream()
                .filter(o -> o.getStatus() == Order.Status.DANG_PHUC_VU
                          || o.getStatus() == Order.Status.PENDING
                          || o.getStatus() == Order.Status.COOKING
                          || o.getStatus() == Order.Status.DELIVERING)
                .count();
            long done      = orders.stream()
                .filter(o -> o.getStatus() == Order.Status.HOAN_THANH
                          || o.getStatus() == Order.Status.COMPLETED)
                .count();
            long cancelled = orders.stream()
                .filter(o -> o.getStatus() == Order.Status.DA_HUY
                          || o.getStatus() == Order.Status.CANCELLED)
                .count();
            double revenue = orders.stream()
                .filter(o -> o.getStatus() == Order.Status.HOAN_THANH
                          || o.getStatus() == Order.Status.COMPLETED)
                .mapToDouble(Order::getTotalAmount).sum();

            lblServing.setText(String.valueOf(serving));
            lblDone.setText(String.valueOf(done));
            lblCancelled.setText(String.valueOf(cancelled));
            lblRevenue.setText(NF.format((long) revenue) + " đ");

            statTable.setItems(FXCollections.observableArrayList(orders));
        });
        task.setOnFailed(e ->
            System.err.println("[OrderStatController] loadStats lỗi: "
                + task.getException().getMessage()));
        new Thread(task, "stat-load").start();
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    @FXML
    private void onClose() {
        ((Stage) statTable.getScene().getWindow()).close();
    }
    private static TableCell<Order, String> centeredStatCell() {
        TableCell<Order, String> cell = new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
        cell.setAlignment(Pos.CENTER);
        return cell;
    }

    private static TableCell<Order, String> rightStatCell() {
        TableCell<Order, String> cell = new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
        cell.setAlignment(Pos.CENTER_RIGHT);
        return cell;
    }
}