package com.restaurant.ui.dialog;

import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import com.restaurant.model.Order;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

public class OrderDetailController implements Initializable {

    // ─── FXML ────────────────────────────────────────────────────────────────

    @FXML private Label lblId;
    @FXML private Label lblTable;
    @FXML private Label lblStatus;
    @FXML private Label lblTime;
    @FXML private Label lblTotal;

    @FXML private TableView<Order.OrderItem>          itemTable;
    @FXML private TableColumn<Order.OrderItem,String> colItemName;
    @FXML private TableColumn<Order.OrderItem,String> colItemQty;
    @FXML private TableColumn<Order.OrderItem,String> colItemPrice;
    @FXML private TableColumn<Order.OrderItem,String> colItemTotal;

    // ─── State ───────────────────────────────────────────────────────────────

    private static final NumberFormat NF =
        NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // ─── Init ─────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colItemName.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getMenuItemName()));

        colItemQty.setCellValueFactory(d ->
            new SimpleStringProperty(String.valueOf(d.getValue().getQuantity())));

        colItemPrice.setCellValueFactory(d ->
            new SimpleStringProperty(NF.format((long) d.getValue().getUnitPrice()) + " đ"));

        colItemTotal.setCellValueFactory(d ->
            new SimpleStringProperty(NF.format((long) d.getValue().getSubtotal()) + " đ"));
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Called by {@link com.restaurant.ui.OrderController} before showing the stage.
     */
    public void setOrder(Order order) {
        lblId.setText(order.getId());
        lblTable.setText(order.getTableName());
        lblStatus.setText(order.getStatusDisplay());
        lblTime.setText(order.getCreatedTime());
        lblTotal.setText(NF.format((long) order.getTotalAmount()) + " đ");

        itemTable.getItems().setAll(order.getItems());
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    @FXML
    private void onClose() {
        ((Stage) lblId.getScene().getWindow()).close();
    }
}