package com.restaurant.ui.dialog;

import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import com.restaurant.model.Order;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
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
        // colItemName: left-align (food name)

        colItemQty.setCellValueFactory(d ->
            new SimpleStringProperty(String.valueOf(d.getValue().getQuantity())));
        colItemQty.setCellFactory(col -> centeredDetailCell());

        colItemPrice.setCellValueFactory(d ->
            new SimpleStringProperty(NF.format((long) d.getValue().getUnitPrice()) + " đ"));
        colItemPrice.setCellFactory(col -> rightDetailCell());

        colItemTotal.setCellValueFactory(d ->
            new SimpleStringProperty(NF.format((long) d.getValue().getSubtotal()) + " đ"));
        colItemTotal.setCellFactory(col -> rightDetailCell());
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
    private static TableCell<Order.OrderItem, String> centeredDetailCell() {
        TableCell<Order.OrderItem, String> cell = new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
        cell.setAlignment(Pos.CENTER);
        return cell;
    }

    private static TableCell<Order.OrderItem, String> rightDetailCell() {
        TableCell<Order.OrderItem, String> cell = new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
        cell.setAlignment(Pos.CENTER_RIGHT);
        return cell;
    }
}