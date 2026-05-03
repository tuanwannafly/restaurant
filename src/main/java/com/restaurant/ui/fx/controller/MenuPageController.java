package com.restaurant.ui.fx.controller;

import com.restaurant.model.MenuItem;
import com.restaurant.ui.TableOrderStage;
import com.restaurant.ui.TableOrderStage.CartEntry;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MenuPageController — Phase 15.
 *
 * <p>Điều khiển màn hình chọn món: search bar, category filter chips,
 * FlowPane card grid, footer subtotal + cart button.
 */
public class MenuPageController extends BasePageController {

    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private Label       lblRestaurantName;
    @FXML private Label       lblTableBadge;
    @FXML private TextField   tfSearch;
    @FXML private HBox        categoryBar;
    @FXML private FlowPane    menuGrid;
    @FXML private Label       lblSubtotal;
    @FXML private Button      btnShowCart;
    @FXML private Button      btnViewStatus;
    @FXML private Label       lblLoading;

    // ── State ─────────────────────────────────────────────────────────────────
    private List<MenuItem> allItems      = new ArrayList<>();
    private List<MenuItem> filteredItems = new ArrayList<>();
    private String selectedCategory      = "Tất cả";

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        tfSearch.textProperty().addListener((obs, o, n) -> filterMenu());
    }

    @Override
    public void onNavigatedTo() {
        lblTableBadge.setText("Bàn " + stage.getTableName());
        lblRestaurantName.setText(stage.getRestaurantName());
        if (allItems.isEmpty()) loadMenu();
        else refreshCartSummary();
    }

    // ── Load menu ─────────────────────────────────────────────────────────────

    private void loadMenu() {
        lblLoading.setVisible(true);
        menuGrid.getChildren().clear();

        stage.loadMenuItems(items -> {
            allItems      = items;
            filteredItems = new ArrayList<>(items);

            List<String> cats = items.stream()
                    .map(MenuItem::getCategory)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            buildCategoryBar(cats);
            rebuildGrid();
            lblLoading.setVisible(false);
            refreshCartSummary();
        }, () -> {
            lblLoading.setVisible(false);
            lblLoading.setText("Không tải được thực đơn!");
            lblLoading.setStyle("-fx-text-fill: #EF4444;");
        });
    }

    // ── Category bar ──────────────────────────────────────────────────────────

    private void buildCategoryBar(List<String> cats) {
        categoryBar.getChildren().clear();
        List<String> all = new ArrayList<>();
        all.add("Tất cả");
        all.addAll(cats);

        ToggleGroup tg = new ToggleGroup();
        for (String cat : all) {
            ToggleButton btn = new ToggleButton(cat);
            btn.setToggleGroup(tg);
            btn.getStyleClass().add("category-chip");
            if (cat.equals(selectedCategory)) btn.setSelected(true);
            btn.setOnAction(e -> {
                selectedCategory = cat;
                filterMenu();
            });
            categoryBar.getChildren().add(btn);
        }
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    private void filterMenu() {
        String q = tfSearch.getText().trim().toLowerCase();
        filteredItems = allItems.stream()
                .filter(m -> {
                    boolean cat = "Tất cả".equals(selectedCategory)
                            || selectedCategory.equals(m.getCategory());
                    boolean name = q.isEmpty()
                            || m.getName().toLowerCase().contains(q);
                    return cat && name;
                })
                .collect(Collectors.toList());
        rebuildGrid();
    }

    // ── Grid ──────────────────────────────────────────────────────────────────

    private void rebuildGrid() {
        menuGrid.getChildren().clear();
        if (filteredItems.isEmpty()) {
            Label empty = new Label("Không tìm thấy món phù hợp");
            empty.getStyleClass().add("text-secondary");
            menuGrid.getChildren().add(empty);
            return;
        }
        for (MenuItem item : filteredItems) {
            menuGrid.getChildren().add(buildItemCard(item));
        }
    }

    private VBox buildItemCard(MenuItem item) {
        VBox card = new VBox(0);
        card.getStyleClass().add("menu-card");
        card.setPrefWidth(160);
        card.setPrefHeight(200);
        card.setCursor(javafx.scene.Cursor.HAND);

        // ── Image ────────────────────────────────────────────────────────────
        ImageView img = new ImageView();
        img.setFitWidth(160);
        img.setFitHeight(110);
        img.setPreserveRatio(false);
        img.getStyleClass().add("menu-card-img");
        if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
            try {
                Image image = new Image(item.getImageUrl(), 160, 110, false, true, true);
                img.setImage(image);
            } catch (Exception ignored) {}
        }
        card.getChildren().add(img);

        // ── Info ─────────────────────────────────────────────────────────────
        VBox info = new VBox(4);
        info.setPadding(new Insets(8, 10, 8, 10));

        Label lblName = new Label(item.getName());
        lblName.getStyleClass().add("card-name");
        lblName.setWrapText(true);
        lblName.setMaxWidth(140);

        Label lblPrice = new Label(fmt(item.getPrice()) + " đ");
        lblPrice.getStyleClass().add("card-price");

        // ── Add button ────────────────────────────────────────────────────────
        Button btnAdd = new Button("+");
        btnAdd.getStyleClass().add("btn-add-item");
        btnAdd.setOnAction(e -> { e.consume(); addToCart(item); });

        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getChildren().add(btnAdd);

        info.getChildren().addAll(lblName, lblPrice, footer);
        card.getChildren().add(info);

        // ── Click card body ───────────────────────────────────────────────────
        card.setOnMouseClicked(e -> addToCart(item));

        return card;
    }

    // ── Cart logic ────────────────────────────────────────────────────────────

    private void addToCart(MenuItem item) {
        List<CartEntry> cart = stage.getCartItems();
        cart.stream()
                .filter(c -> c.menuItemId.equals(item.getId()))
                .findFirst()
                .ifPresentOrElse(
                        c -> c.quantity++,
                        () -> cart.add(new CartEntry(
                                item.getId(), item.getName(), item.getPrice()))
                );
        refreshCartSummary();
    }

    private void refreshCartSummary() {
        List<CartEntry> cart = stage.getCartItems();
        double subtotal = cart.stream().mapToDouble(CartEntry::subtotal).sum();
        int    count    = cart.stream().mapToInt(c -> c.quantity).sum();
        lblSubtotal.setText("Tạm tính: " + fmt(subtotal) + " đ");
        btnShowCart.setText("🛒  Giỏ hàng (" + count + " món)");
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    @FXML
    private void onShowCart() {
        if (stage.getCartItems().isEmpty()) {
            showToast("Giỏ hàng đang trống, hãy thêm món!", "info");
            return;
        }
        stage.getCartController().syncFromCart();
        stage.navigateTo(TableOrderStage.PAGE_CART);
    }

    @FXML
    private void onViewStatus() {
        stage.navigateTo(TableOrderStage.PAGE_STATUS);
    }

    // ── Toast ─────────────────────────────────────────────────────────────────

    private void showToast(String msg, String type) {
        // Sử dụng ToastFx nếu có; tạm thời dùng Alert
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
