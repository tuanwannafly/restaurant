package com.restaurant.ui.fx.controller;

import com.restaurant.ui.TableOrderStage;
import com.restaurant.ui.TableOrderStage.CartEntry;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TableCell;
import javafx.geometry.Pos;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.util.converter.IntegerStringConverter;

import java.util.List;

/**
 * CartPageController — Phase 15.
 *
 * <p>Giỏ hàng: TableView với cột STT / Tên / Đơn giá / Thành tiền / Ghi chú / SL.
 * Inline edit ghi chú và số lượng. Xoá item khi SL về 0.
 * Gửi món → gọi {@link TableOrderStage#sendOrder}.
 */
public class CartPageController extends BasePageController {

    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private Label              lblTableBadge;
    @FXML private TableView<CartRow> cartTable;
    @FXML private TableColumn<CartRow, Integer> colSTT;
    @FXML private TableColumn<CartRow, String>  colName;
    @FXML private TableColumn<CartRow, String>  colUnit;
    @FXML private TableColumn<CartRow, String>  colTotal;
    @FXML private TableColumn<CartRow, String>  colNote;
    @FXML private TableColumn<CartRow, Integer> colQty;
    @FXML private Label              lblCartTotal;

    // ── Observable data ───────────────────────────────────────────────────────
    private final ObservableList<CartRow> rows = FXCollections.observableArrayList();

    // ── FXML initialize ────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        setupColumns();
        cartTable.setItems(rows);
        cartTable.setEditable(true);
        cartTable.setPlaceholder(new Label("Giỏ hàng trống"));
    }

    private void setupColumns() {
        colSTT.setCellValueFactory(c -> c.getValue().sttProperty().asObject());
        colSTT.setCellFactory(col -> {
            TableCell<CartRow, Integer> cell = new TableCell<>() {
                @Override protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : String.valueOf(item));
                }
            };
            cell.setAlignment(Pos.CENTER);
            return cell;
        });

        colName.setCellValueFactory(c -> c.getValue().nameProperty());

        colUnit.setCellValueFactory(c -> c.getValue().unitPriceProperty());
        colUnit.setCellFactory(col -> {
            TableCell<CartRow, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setAlignment(Pos.CENTER_RIGHT);
            return cell;
        });

        colTotal.setCellValueFactory(c -> c.getValue().subtotalProperty());
        colTotal.setCellFactory(col -> {
            TableCell<CartRow, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setAlignment(Pos.CENTER_RIGHT);
            return cell;
        });

        // ── Ghi chú: inline edit ────────────────────────────────────────────
        colNote.setCellValueFactory(c -> c.getValue().noteProperty());
        colNote.setCellFactory(col -> {
            TextFieldTableCell<CartRow, String> cell = new TextFieldTableCell<>();
            cell.setStyle("-fx-font-style: italic; -fx-text-fill: #9CA3AF;");
            return cell;
        });
        colNote.setOnEditCommit(e -> {
            CartRow row = e.getRowValue();
            row.noteProperty().set(e.getNewValue());
            // Đẩy về cartItems gốc
            syncNoteBack(row.getMenuItemId(), e.getNewValue());
        });

        // ── Số lượng: SpinnerTableCell ───────────────────────────────────────
        colQty.setCellValueFactory(c -> c.getValue().quantityProperty().asObject());
        colQty.setCellFactory(col -> new SpinnerCell());
        colQty.setOnEditCommit(e -> {
            int newQty = e.getNewValue();
            CartRow row = e.getRowValue();
            if (newQty <= 0) {
                removeItem(row.getMenuItemId());
            } else {
                row.quantityProperty().set(newQty);
                syncQtyBack(row.getMenuItemId(), newQty);
                refreshTotals();
            }
        });
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void onNavigatedTo() {
        lblTableBadge.setText("Bàn " + stage.getTableName());
        syncFromCart();
    }

    /** Đồng bộ rows từ stage.getCartItems(). Gọi khi navigate đến cart. */
    public void syncFromCart() {
        rows.clear();
        List<CartEntry> cart = stage.getCartItems();
        for (int i = 0; i < cart.size(); i++) {
            CartEntry ci = cart.get(i);
            rows.add(new CartRow(i + 1, ci));
        }
        refreshTotals();
    }

    // ── Totals ────────────────────────────────────────────────────────────────

    private void refreshTotals() {
        double total = stage.getCartItems().stream()
                .mapToDouble(CartEntry::subtotal).sum();
        lblCartTotal.setText("Tổng cộng: " + fmt(total) + " đ");

        // Cập nhật lại STT và subtotal cho từng row
        List<CartEntry> cart = stage.getCartItems();
        for (int i = 0; i < rows.size() && i < cart.size(); i++) {
            CartRow row = rows.get(i);
            CartEntry ci = cart.get(i);
            row.sttProperty().set(i + 1);
            row.subtotalProperty().set(fmt(ci.subtotal()) + " đ");
        }
    }

    // ── Sync back helpers ─────────────────────────────────────────────────────

    private void syncNoteBack(String menuItemId, String note) {
        stage.getCartItems().stream()
                .filter(c -> c.menuItemId.equals(menuItemId))
                .findFirst()
                .ifPresent(c -> c.note = note);
    }

    private void syncQtyBack(String menuItemId, int qty) {
        stage.getCartItems().stream()
                .filter(c -> c.menuItemId.equals(menuItemId))
                .findFirst()
                .ifPresent(c -> c.quantity = qty);
    }

    private void removeItem(String menuItemId) {
        stage.getCartItems().removeIf(c -> c.menuItemId.equals(menuItemId));
        syncFromCart();
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    @FXML
    private void onBackToMenu() {
        stage.navigateTo(TableOrderStage.PAGE_MENU);
    }

    @FXML
    private void onSendOrder() {
        if (stage.getCartItems().isEmpty()) {
            showInfo("Giỏ hàng trống!");
            return;
        }

        // Commit bất kỳ edit đang mở
        if (cartTable.getEditingCell() != null) {
            cartTable.getSelectionModel().clearSelection();
        }

        stage.sendOrder(
            () -> {
                syncFromCart();
                stage.navigateTo(TableOrderStage.PAGE_MENU);
                stage.getMenuController().onNavigatedTo(); // refresh summary
                showInfo("✅  Đã gửi order! Bếp đang xử lý.");
            },
            () -> showInfo("❌  Gửi order thất bại, vui lòng thử lại.")
        );
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner classes
    // ═══════════════════════════════════════════════════════════════════════════

    // ── CartRow — Observable wrapper ──────────────────────────────────────────

    public static class CartRow {
        private final String              menuItemId;
        private final IntegerProperty     stt      = new SimpleIntegerProperty();
        private final StringProperty      name     = new SimpleStringProperty();
        private final StringProperty      unitPrice = new SimpleStringProperty();
        private final StringProperty      subtotal  = new SimpleStringProperty();
        private final StringProperty      note     = new SimpleStringProperty();
        private final IntegerProperty     quantity  = new SimpleIntegerProperty();

        CartRow(int stt, CartEntry ci) {
            this.menuItemId = ci.menuItemId;
            this.stt.set(stt);
            this.name.set(ci.name);
            this.unitPrice.set(TableOrderStage.formatPrice(ci.unitPrice) + " đ");
            this.subtotal.set(TableOrderStage.formatPrice(ci.subtotal()) + " đ");
            this.note.set(ci.note);
            this.quantity.set(ci.quantity);
        }

        public String          getMenuItemId()  { return menuItemId;  }
        public IntegerProperty sttProperty()    { return stt;         }
        public StringProperty  nameProperty()   { return name;        }
        public StringProperty  unitPriceProperty() { return unitPrice; }
        public StringProperty  subtotalProperty()  { return subtotal;  }
        public StringProperty  noteProperty()   { return note;        }
        public IntegerProperty quantityProperty() { return quantity;  }
    }

    // ── SpinnerCell — inline +/- editor ──────────────────────────────────────

    private static class SpinnerCell extends TableCell<CartRow, Integer> {
        private final Spinner<Integer> spinner = new Spinner<>(0, 999, 1);

        SpinnerCell() {
            spinner.setEditable(true);
            spinner.setPrefWidth(90);
            spinner.valueProperty().addListener((obs, o, n) -> {
                if (isEditing()) commitEdit(n);
            });
        }

        @Override
        public void startEdit() {
            super.startEdit();
            CartRow row = getTableRow().getItem();
            if (row == null) return;
            spinner.getValueFactory().setValue(row.quantityProperty().get());
            setGraphic(spinner);
            setText(null);
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            updateItem(getItem(), false);
        }

        @Override
        protected void updateItem(Integer qty, boolean empty) {
            super.updateItem(qty, empty);
            if (empty || qty == null) { setText(null); setGraphic(null); return; }
            if (isEditing()) {
                spinner.getValueFactory().setValue(qty);
                setGraphic(spinner);
                setText(null);
            } else {
                setText(String.valueOf(qty));
                setGraphic(null);
                setAlignment(Pos.CENTER);
            }
        }
    }
}
