package com.restaurant.ui.fx.controller;

import java.util.List;
import java.util.stream.Collectors;

import com.restaurant.model.MenuItem;
import com.restaurant.ui.ImageLoader;
import com.restaurant.ui.TableOrderStage.CartEntry;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class TableOrderSingleController extends BasePageController {
    @FXML private Label lblRestaurantName, lblTableBadge, lblTotalAmount;
    @FXML private TextField tfSearch;
    @FXML private HBox categoryBar;
    @FXML private FlowPane menuGrid;
    @FXML private ListView<CartEntry> lvCart;
    @FXML private Button btnSendOrder;

    private ObservableList<MenuItem> allMenuItems = FXCollections.observableArrayList();
    private String selectedCategory = "Tất cả";

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        lvCart.setCellFactory(lv -> new CartItemCell());
        tfSearch.textProperty().addListener((obs, o, n) -> renderMenuGrid());
    }

    @Override
    public void onNavigatedTo() {
        lblRestaurantName.setText(stage.getRestaurantName());
        lblTableBadge.setText("Bàn " + stage.getTableName());
        loadMenu();
        refreshCartUI();
    }

    // ── Menu loading ──────────────────────────────────────────────────────────

    private void loadMenu() {
        stage.loadMenuItems(items -> {
            allMenuItems.setAll(items);
            renderCategoryBar();
            renderMenuGrid();
        }, () -> System.err.println("[Menu] Lỗi tải thực đơn"));
    }

    private void renderCategoryBar() {
        categoryBar.getChildren().clear();
        List<String> cats = allMenuItems.stream()
                .map(MenuItem::getCategory).distinct().collect(Collectors.toList());
        cats.add(0, "Tất cả");

        ToggleGroup tg = new ToggleGroup();
        for (String cat : cats) {
            ToggleButton btn = new ToggleButton(cat);
            btn.setToggleGroup(tg);
            btn.getStyleClass().add("category-chip");
            btn.setSelected(cat.equals(selectedCategory));
            btn.setOnAction(e -> { selectedCategory = cat; renderMenuGrid(); });
            categoryBar.getChildren().add(btn);
        }
    }

    private void renderMenuGrid() {
        menuGrid.getChildren().clear();
        String q = tfSearch.getText().toLowerCase().trim();
        allMenuItems.stream()
            .filter(m -> ("Tất cả".equals(selectedCategory) || selectedCategory.equals(m.getCategory()))
                      && (q.isEmpty() || m.getName().toLowerCase().contains(q)))
            .forEach(m -> menuGrid.getChildren().add(createDishCard(m)));
    }

    // ── Card builder ──────────────────────────────────────────────────────────

    private VBox createDishCard(MenuItem item) {
        // ── Outer card ──────────────────────────────────────────────────────
        VBox card = new VBox();
        card.getStyleClass().add("menu-card");
        card.setPrefWidth(180);
        card.setMaxWidth(180);

        // ── Image area ──────────────────────────────────────────────────────
        StackPane imgBox = new StackPane();
        imgBox.setPrefHeight(130);
        imgBox.setStyle("-fx-background-color: #F3F4F6;"
                + " -fx-background-radius: 12 12 0 0;");

        ImageView iv = new ImageView();
        iv.setFitWidth(180);
        iv.setFitHeight(130);
        iv.setPreserveRatio(false);
        iv.setSmooth(true);
        ImageLoader.loadAsync(item.getImageUrl(), iv);

        // Badge số lượng trong giỏ (hiện khi > 0)
        Label badgeQty = new Label();
        badgeQty.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white;"
                + " -fx-font-size: 11px; -fx-font-weight: bold;"
                + " -fx-background-radius: 10; -fx-padding: 2 7 2 7;");
        badgeQty.setVisible(false);
        StackPane.setAlignment(badgeQty, Pos.TOP_RIGHT);
        StackPane.setMargin(badgeQty, new Insets(8, 8, 0, 0));

        // Cập nhật badge mỗi lần cart thay đổi
        updateBadge(item, badgeQty);

        imgBox.getChildren().addAll(iv, badgeQty);

        // ── Info section ────────────────────────────────────────────────────
        VBox info = new VBox(5);
        info.setPadding(new Insets(10, 12, 12, 12));

        Label name = new Label(item.getName());
        name.getStyleClass().add("card-name");
        name.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #111827;"
                + " -fx-wrap-text: true;");
        name.setMaxWidth(156);

        // Mô tả ngắn (nếu có)
        String desc = item.getDescription();
        if (desc != null && !desc.isBlank()) {
            Label descLbl = new Label(desc.length() > 40 ? desc.substring(0, 40) + "…" : desc);
            descLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF;");
            descLbl.setMaxWidth(156);
            info.getChildren().add(descLbl);
        }

        // Price row
        HBox priceRow = new HBox();
        priceRow.setAlignment(Pos.CENTER_LEFT);
        priceRow.setPadding(new Insets(4, 0, 0, 0));

        Label price = new Label(fmt(item.getPrice()) + "đ");
        price.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #3B82F6;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+");
        addBtn.getStyleClass().add("btn-add-item");
        addBtn.setOnAction(e -> {
            addToCart(item);
            updateBadge(item, badgeQty);
        });

        priceRow.getChildren().addAll(price, spacer, addBtn);

        info.getChildren().addAll(name, priceRow);
        card.getChildren().addAll(imgBox, info);
        return card;
    }

    /** Cập nhật badge số lượng trên ảnh card. */
    private void updateBadge(MenuItem item, Label badge) {
        int qty = stage.getCartItems().stream()
                .filter(c -> c.menuItemId.equals(item.getId()))
                .mapToInt(c -> c.quantity).sum();
        if (qty > 0) {
            badge.setText(String.valueOf(qty));
            badge.setVisible(true);
        } else {
            badge.setVisible(false);
        }
    }

    // ── Cart ──────────────────────────────────────────────────────────────────

    private void addToCart(MenuItem item) {
        List<CartEntry> cart = stage.getCartItems();
        CartEntry ex = cart.stream()
                .filter(c -> c.menuItemId.equals(item.getId())).findFirst().orElse(null);
        if (ex != null) ex.quantity++;
        else cart.add(new CartEntry(item.getId(), item.getName(), item.getPrice()));
        refreshCartUI();
    }

    private void refreshCartUI() {
        lvCart.getItems().setAll(stage.getCartItems());
        double total = stage.getCartItems().stream().mapToDouble(CartEntry::subtotal).sum();
        lblTotalAmount.setText(fmt(total) + " đ");
        btnSendOrder.setDisable(stage.getCartItems().isEmpty());
    }

    // ── Custom cart cell (tên + qty controls + subtotal) ──────────────────────

    private class CartItemCell extends ListCell<CartEntry> {
        private final HBox  row    = new HBox(8);
        private final VBox  left   = new VBox(2);
        private final Label lName  = new Label();
        private final Label lSub   = new Label();
        private final HBox  qtyBox = new HBox(4);
        private final Button btnM  = new Button("−");
        private final Label  lQty  = new Label();
        private final Button btnP  = new Button("+");

        CartItemCell() {
            // Name + subtotal stacked on left
            lName.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #111827;");
            lSub.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
            left.getChildren().addAll(lName, lSub);
            HBox.setHgrow(left, Priority.ALWAYS);

            // Qty stepper on right
            String btnStyle = "-fx-background-color: #EFF6FF; -fx-text-fill: #3B82F6;"
                    + " -fx-font-weight: bold; -fx-background-radius: 6;"
                    + " -fx-min-width: 28; -fx-min-height: 28;"
                    + " -fx-max-width: 28; -fx-max-height: 28; -fx-cursor: hand;";
            btnM.setStyle(btnStyle);
            btnP.setStyle(btnStyle);
            lQty.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-min-width: 20;"
                    + " -fx-alignment: center;");
            qtyBox.setAlignment(Pos.CENTER);
            qtyBox.getChildren().addAll(btnM, lQty, btnP);

            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 4, 8, 4));
            row.getChildren().addAll(left, qtyBox);

            btnM.setOnAction(e -> {
                CartEntry item = getItem();
                if (item == null) return;
                if (item.quantity > 1) item.quantity--;
                else stage.getCartItems().remove(item);
                refreshCartUI();
            });
            btnP.setOnAction(e -> {
                CartEntry item = getItem();
                if (item == null) return;
                item.quantity++;
                refreshCartUI();
            });

            setStyle("-fx-background-color: transparent;");
            setGraphic(null);
        }

        @Override
        protected void updateItem(CartEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            lName.setText(item.name);
            lSub.setText(fmt(item.unitPrice) + "đ × " + item.quantity
                    + " = " + fmt(item.subtotal()) + "đ");
            lQty.setText(String.valueOf(item.quantity));
            setGraphic(row);
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @FXML
    private void onSendOrder() {
        stage.sendOrder(() -> {
            Alert ok = new Alert(Alert.AlertType.INFORMATION,
                    "✅  Đơn đã gửi vào bếp thành công!");
            ok.setHeaderText(null);
            ok.setTitle("Gửi món");
            ok.show();
            refreshCartUI();
        }, () -> {
            Alert err = new Alert(Alert.AlertType.ERROR, "Gửi món thất bại, vui lòng thử lại.");
            err.setHeaderText(null);
            err.show();
        });
    }

    @FXML private void onViewStatus()     { stage.navigateTo("status");  }
    @FXML private void onRequestPayment() { stage.navigateTo("payment"); }
}