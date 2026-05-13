package com.restaurant.ui;

import com.restaurant.dao.MenuItemDAO;
import com.restaurant.model.MenuItem;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;
import com.restaurant.ui.cell.ActionTableCell;
import com.restaurant.ui.dialog.MenuDetailDialog;
import com.restaurant.ui.dialog.MenuDialog;
import com.restaurant.ui.dialog.MenuStatDialog;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Predicate;

/**
 * MenuController — Phase 4 (JavaFX)
 *
 * Manages the Quản lý Menu screen:
 *  - Loads all menu items via {@link MenuItemDAO} on a background thread.
 *  - Provides live search + category + price filter using {@link FilteredList}.
 *  - Hides add/delete buttons based on RBAC {@link Permission}.
 *  - Opens {@link MenuDialog} for add / edit operations.
 *  - Opens {@link MenuStatDialog} for statistics.
 */
public class MenuController implements Initializable {

    // ── FXML injections ───────────────────────────────────────────────────────

    @FXML private Button                         btnAdd;
    @FXML private Button                         btnStat;

    @FXML private TextField                      tfSearch;
    @FXML private ComboBox<String>               cbCategory;
    @FXML private ComboBox<String>               cbPrice;

    @FXML private TableView<MenuItem>            menuTable;
    @FXML private TableColumn<MenuItem, String>  colId;
    @FXML private TableColumn<MenuItem, String>  colName;
    @FXML private TableColumn<MenuItem, String>  colCategory;
    @FXML private TableColumn<MenuItem, String>  colPrice;
    @FXML private TableColumn<MenuItem, Void>    colActions;

    // ── State ─────────────────────────────────────────────────────────────────

    private final MenuItemDAO                dao          = new MenuItemDAO();
    private final ObservableList<MenuItem>   masterList   = FXCollections.observableArrayList();
    private FilteredList<MenuItem>           filteredList;
    private final NumberFormat               vndFormat    =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // ── Initializable ─────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initFilterControls();
        initColumns();
        applyRbac();
        loadData();
    }

    // ── Initialisation helpers ────────────────────────────────────────────────

    private void initFilterControls() {
        cbCategory.setItems(FXCollections.observableArrayList(
                "Tất cả", "Hải sản", "Thịt", "Cơm", "Phở", "Đồ uống", "Khác"));
        cbCategory.setValue("Tất cả");

        cbPrice.setItems(FXCollections.observableArrayList(
                "Tất cả", "Dưới 100k", "100k – 300k", "Trên 300k"));
        cbPrice.setValue("Tất cả");

        // Wrap master list in a FilteredList
        filteredList = new FilteredList<>(masterList, p -> true);
        menuTable.setItems(filteredList);

        // Live-bind search field
        tfSearch.textProperty().addListener((obs, o, n) -> refreshPredicate());
    }

    private void initColumns() {
        // Simple property bindings
        colId      .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getId()));
        colId.setCellFactory(col -> centeredMenuCell());

        colName    .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
        // colName: left-align (food name)

        colCategory.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCategory()));
        colCategory.setCellFactory(col -> centeredMenuCell());

        colPrice   .setCellValueFactory(cd -> {
            long price = (long) cd.getValue().getPrice();
            return new SimpleStringProperty(vndFormat.format(price) + " ₫");
        });
        colPrice.setCellFactory(col -> {
            TableCell<MenuItem, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setAlignment(Pos.CENTER_RIGHT);
            return cell;
        });

        // Action column — reusable cell factory
        boolean canEdit   = AppSession.getInstance().hasPermission(Permission.EDIT_MENU);
        boolean canDelete = AppSession.getInstance().hasPermission(Permission.DELETE_MENU);

        colActions.setCellFactory(col ->
            new ActionTableCell<>(
                canDelete, canEdit,
                this::handleDelete,
                this::openEditDialog,
                this::showDetail
            )
        );

        // Placeholder label shown when table is empty
        menuTable.setPlaceholder(new Label("Không có món ăn nào phù hợp…"));
    }

    private void applyRbac() {
        boolean canAdd = AppSession.getInstance().hasPermission(Permission.ADD_MENU);
        btnAdd.setVisible(canAdd);
        btnAdd.setManaged(canAdd);
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    /**
     * Loads menu items on a background thread so the EDT (JavaFX Application
     * Thread) is never blocked.
     */
    public void loadData() {
        menuTable.setPlaceholder(new Label("Đang tải…"));

        Task<List<MenuItem>> task = new Task<>() {
            @Override
            protected List<MenuItem> call() {
                return dao.getAll();
            }
        };

        task.setOnSucceeded(e -> {
            masterList.setAll(task.getValue());
            refreshPredicate();
            menuTable.setPlaceholder(new Label("Không có món ăn nào phù hợp…"));
        });

        task.setOnFailed(e ->
            menuTable.setPlaceholder(
                new Label("⚠ Lỗi tải dữ liệu: " + task.getException().getMessage()))
        );

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // ── Filter predicate ──────────────────────────────────────────────────────

    @FXML
    private void onFilterChanged() {
        refreshPredicate();
    }

    private void refreshPredicate() {
        String  search = tfSearch.getText().trim().toLowerCase();
        String  cat    = cbCategory.getValue();
        String  price  = cbPrice.getValue();

        filteredList.setPredicate(buildPredicate(search, cat, price));
    }

    private Predicate<MenuItem> buildPredicate(String search, String cat, String price) {
        return item -> {
            boolean matchName = search.isEmpty()
                || item.getName().toLowerCase().contains(search)
                || item.getId().toLowerCase().contains(search);

            boolean matchCat = "Tất cả".equals(cat)
                || item.getCategory().equalsIgnoreCase(cat);

            boolean matchPrice = "Tất cả".equals(price)
                || ("Dưới 100k".equals(price)   && item.getPrice() < 100_000)
                || ("100k – 300k".equals(price) && item.getPrice() >= 100_000 && item.getPrice() <= 300_000)
                || ("Trên 300k".equals(price)   && item.getPrice() > 300_000);

            return matchName && matchCat && matchPrice;
        };
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    @FXML
    private void onAddClicked() {
        if (!AppSession.getInstance().hasPermission(Permission.ADD_MENU)) {
            showAlert(Alert.AlertType.WARNING,
                "Không có quyền", "Bạn không có quyền thêm món ăn.");
            return;
        }
        openDialog(null);
    }

    @FXML
    private void onStatClicked() {
        Stage owner = (Stage) btnStat.getScene().getWindow();
        new MenuStatDialog(owner).show();
    }

    // ── CRUD actions (called by ActionTableCell) ──────────────────────────────

    private void handleDelete(MenuItem item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Bạn có chắc muốn xóa món \"" + item.getName() + "\"?",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận xóa");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                Task<Void> task = new Task<>() {
                    @Override protected Void call() {
                        dao.delete(item.getId());
                        return null;
                    }
                };
                task.setOnSucceeded(e -> loadData());
                task.setOnFailed(e ->
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR,
                        "Lỗi", "Không thể xóa món: " + task.getException().getMessage()))
                );
                new Thread(task, "delete-worker").start();
            }
        });
    }

    private void openEditDialog(MenuItem item) {
        openDialog(item);
    }

    private void showDetail(MenuItem item) {
        Stage owner = (Stage) menuTable.getScene().getWindow();
        new MenuDetailDialog(owner, item).show();
    }

    // ── Shared dialog opener ──────────────────────────────────────────────────

    private void openDialog(MenuItem item) {
        Stage owner = (Stage) menuTable.getScene().getWindow();
        new MenuDialog(owner, item, saved -> {
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    if (saved.getId() == null || saved.getId().isBlank()) {
                        dao.create(saved);
                    } else {
                        dao.update(saved);
                    }
                    return null;
                }
            };
            task.setOnSucceeded(e -> Platform.runLater(this::loadData));
            task.setOnFailed(e ->
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR,
                    "Lỗi", task.getException().getMessage()))
            );
            new Thread(task, "save-worker").start();
        }).show();
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
    private static TableCell<MenuItem, String> centeredMenuCell() {
        TableCell<MenuItem, String> cell = new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
        cell.setAlignment(Pos.CENTER);
        return cell;
    }
}