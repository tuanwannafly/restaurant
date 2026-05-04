package com.restaurant.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.restaurant.dao.EmployeeDAO;
import com.restaurant.data.DataManager;
import com.restaurant.model.Employee;
import com.restaurant.session.AppSession;
import com.restaurant.session.OperationType;
import com.restaurant.session.Permission;
import com.restaurant.ui.dialog.ConfirmOperationDialogController;
import com.restaurant.ui.dialog.EmployeeDialogController;
import com.restaurant.ui.dialog.RegisterStaffDialogController;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

/**
 * JavaFX controller for EmployeeView.fxml.
 *
 * <p>Mirrors the behaviour of the Swing {@code EmployeePanel}:
 * <ul>
 *   <li>Shows the "Tài khoản" column and "Tạo tài khoản" button only when the
 *       current user holds the {@link Permission#REGISTER_STAFF} permission.</li>
 *   <li>Loads employee data via a background {@link Task} to keep the UI responsive.</li>
 *   <li>Supports real-time search + role-filter.</li>
 *   <li>Delete requires an Operation Token confirmation via
 *       {@link ConfirmOperationDialogController#show}.</li>
 * </ul>
 */
public class EmployeeController {

    // ── FXML injections ───────────────────────────────────────────────────────

    @FXML private Button                       btnRegister;
    @FXML private Button                       btnAdd;
    @FXML private TextField                    searchField;
    @FXML private HBox                         roleFilterBox;

    @FXML private TableView<Employee>          tableView;
    @FXML private TableColumn<Employee, String> colId;
    @FXML private TableColumn<Employee, String> colName;
    @FXML private TableColumn<Employee, String> colRole;
    @FXML private TableColumn<Employee, String> colPhone;
    @FXML private TableColumn<Employee, String> colStartDate;
    @FXML private TableColumn<Employee, String> colAccount;
    @FXML private TableColumn<Employee, Void>   colActions;

    // ── State ─────────────────────────────────────────────────────────────────

    private final boolean showAccountCol =
            AppSession.getInstance().hasPermission(Permission.REGISTER_STAFF);

    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    private List<Employee>                   allItems       = new ArrayList<>();
    private final ObservableList<Employee>   displayedItems = FXCollections.observableArrayList();

    private String selectedRole = null;

    private static final String[] ROLE_LABELS = {"Phục vụ", "Đầu bếp", "Thu ngân", "Quản lý"};

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        applyPermissionVisibility();
        wireColumns();
        buildRoleFilterButtons();
        tableView.setItems(displayedItems);
        loadData();
    }

    // ── Permission-based visibility ───────────────────────────────────────────

    private void applyPermissionVisibility() {
        colAccount.setVisible(showAccountCol);
        btnRegister.setVisible(showAccountCol);
        btnRegister.setManaged(showAccountCol);
    }

    // ── Column setup ──────────────────────────────────────────────────────────

    private void wireColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colRole.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getRoleDisplay()));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colStartDate.setCellValueFactory(new PropertyValueFactory<>("startDate"));

        // ── Account status column ──
        if (showAccountCol) {
            colAccount.setCellValueFactory(c ->
                    new SimpleStringProperty(
                            c.getValue().isHasAccount() ? "✅ Có tài khoản" : "⬜ Chưa có"));
            colAccount.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String val, boolean empty) {
                    super.updateItem(val, empty);
                    if (empty || val == null) { setText(null); setStyle(""); return; }
                    setText(val);
                    setAlignment(Pos.CENTER);
                    setStyle(val.startsWith("✅")
                            ? "-fx-text-fill:#16A34A;-fx-font-weight:bold;"
                            : "-fx-text-fill:#9CA3AF;");
                }
            });
        }

        // ── Actions column ──
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnDel  = actionBtn("🗑 Xóa",       "#EF4444", "#FEF2F2");
            private final Button btnEdit = actionBtn("✏ Cập nhật",   "#3B82F6", "#EFF6FF");
            private final Button btnView = actionBtn("👁 Xem chi tiết","#6366F1", "#EEF2FF");
            private final HBox   bar     = new HBox(6, btnDel, btnEdit, btnView);
            {
                bar.setAlignment(Pos.CENTER_LEFT);
                bar.setPadding(new Insets(4, 6, 4, 6));
                btnDel.setOnAction(e  -> handleDelete(getItem(getIndex())));
                btnEdit.setOnAction(e -> handleEdit(getItem(getIndex())));
                btnView.setOnAction(e -> handleViewDetail(getItem(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : bar);
            }
            /** Safe row-lookup: returns null if index is out of bounds. */
            private Employee getItem(int index) {
                return (index >= 0 && index < tableView.getItems().size())
                        ? tableView.getItems().get(index) : null;
            }
        });
    }

    // ── Role filter buttons ───────────────────────────────────────────────────

    private void buildRoleFilterButtons() {
        ToggleGroup group = new ToggleGroup();
        for (String role : ROLE_LABELS) {
            ToggleButton tb = new ToggleButton(role);
            tb.setToggleGroup(group);
            tb.setStyle(STYLE_TOGGLE_OFF);
            tb.selectedProperty().addListener((obs, was, now) -> {
                tb.setStyle(now ? STYLE_TOGGLE_ON : STYLE_TOGGLE_OFF);
                selectedRole = now ? role : null;
                // If another button just selected, that listener will set the real value
                applyFilter();
            });
            roleFilterBox.getChildren().add(tb);
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    /**
     * Loads employees in a background thread, then refreshes the table on the FX thread.
     * Public so that parent frames can trigger a refresh after navigation.
     */
    public void loadData() {
        Task<List<Employee>> task = new Task<>() {
            @Override
            protected List<Employee> call() {
                return showAccountCol
                        ? DataManager.getInstance().getEmployeesWithAccountStatus()
                        : DataManager.getInstance().getEmployees();
            }
        };
        task.setOnSucceeded(e -> {
            allItems = task.getValue();
            applyFilter();
        });
        task.setOnFailed(e ->
                System.err.println("[EmployeeController] loadData lỗi: "
                        + task.getException().getMessage()));
        new Thread(task, "emp-load").start();
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    @FXML
    private void onSearchKeyReleased() { applyFilter(); }

    private void applyFilter() {
        String q = searchField.getText().trim().toLowerCase();
        List<Employee> filtered = allItems.stream()
                .filter(emp -> {
                    boolean nameMatch = q.isEmpty()
                            || emp.getName().toLowerCase().contains(q)
                            || emp.getId().toLowerCase().contains(q);
                    boolean roleMatch = selectedRole == null
                            || emp.getRoleDisplay().equalsIgnoreCase(selectedRole);
                    return nameMatch && roleMatch;
                })
                .collect(Collectors.toList());
        Platform.runLater(() -> displayedItems.setAll(filtered));
    }

    // ── CRUD handlers ─────────────────────────────────────────────────────────

    private void handleDelete(Employee item) {
        if (item == null) return;

        // Require Operation Token before destructive action
        long targetId = resolveTargetId(item.getId());
        boolean confirmed = ConfirmOperationDialogController.show(
                getWindow(), OperationType.DELETE_EMPLOYEE, targetId);
        if (!confirmed) return;

        runAsync(() -> DataManager.getInstance().deleteEmployee(item.getId()));
    }

    private void handleEdit(Employee item) {
        if (item == null) return;
        EmployeeDialogController.show(getWindow(), item, saved ->
                runAsync(() -> DataManager.getInstance().updateEmployee(saved)));
    }

    private void handleViewDetail(Employee item) {
        if (item == null) return;
        // Wire to EmployeeDetailDialogController when available
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.initOwner(getWindow());
        dlg.setTitle("Chi tiết nhân viên");
        dlg.setHeaderText(item.getName() + " — " + item.getRoleDisplay());
        dlg.setContentText(
                "ID          : " + item.getId()        + "\n" +
                "CCCD        : " + item.getCccd()       + "\n" +
                "SDT         : " + item.getPhone()      + "\n" +
                "Ngày vào làm: " + item.getStartDate()  + "\n" +
                "Địa chỉ     : " + item.getAddress());
        dlg.showAndWait();
    }

    @FXML
    private void openAddDialog() {
        EmployeeDialogController.show(getWindow(), null, saved ->
                runAsync(() -> DataManager.getInstance().addEmployee(saved)));
    }

    @FXML
    private void openRegisterDialog() {
        boolean ok = RegisterStaffDialogController.show(getWindow());
        if (ok) {
            loadData();
            showInfo("Tạo tài khoản thành công!", Alert.AlertType.INFORMATION);
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Run {@code work} in a background thread; reload the table when done. */
    private void runAsync(Runnable work) {
        Task<Void> task = new Task<>() {
            @Override protected Void call() { work.run(); return null; }
        };
        task.setOnSucceeded(e -> loadData());
        task.setOnFailed(e -> Platform.runLater(() ->
                showInfo("Lỗi: " + task.getException().getMessage(), Alert.AlertType.ERROR)));
        new Thread(task, "emp-write").start();
    }

    private long resolveTargetId(String employeeId) {
        try {
            return employeeDAO.findUserId(employeeId)
                    .map(Long::longValue)
                    .orElse((long) employeeId.hashCode());
        } catch (Exception ignored) {
            return (long) employeeId.hashCode();
        }
    }

    private Window getWindow() {
        return tableView.getScene().getWindow();
    }

    private void showInfo(String msg, Alert.AlertType type) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        a.initOwner(getWindow());
        a.setHeaderText(null);
        a.showAndWait();
    }

    // ── Inline style constants ─────────────────────────────────────────────────

    private static Button actionBtn(String text, String fg, String bg) {
        Button b = new Button(text);
        b.setStyle(String.format(
                "-fx-font-size:11px;-fx-text-fill:%s;-fx-background-color:%s;" +
                "-fx-border-color:%s;-fx-border-radius:4;-fx-background-radius:4;" +
                "-fx-cursor:hand;-fx-padding:3 8 3 8;", fg, bg, fg));
        // Hover feedback
        String baseStyle = b.getStyle();
        b.setOnMouseEntered(e -> b.setStyle(baseStyle + "-fx-opacity:0.85;"));
        b.setOnMouseExited(e  -> b.setStyle(baseStyle));
        return b;
    }

    private static final String STYLE_TOGGLE_OFF =
            "-fx-background-color:white;-fx-border-color:#CBD5E1;-fx-border-radius:6;" +
            "-fx-background-radius:6;-fx-text-fill:#475569;-fx-font-size:12px;" +
            "-fx-padding:4 10 4 10;-fx-cursor:hand;";

    private static final String STYLE_TOGGLE_ON =
            "-fx-background-color:#EFF6FF;-fx-border-color:#3B82F6;-fx-border-radius:6;" +
            "-fx-background-radius:6;-fx-text-fill:#3B82F6;-fx-font-weight:bold;" +
            "-fx-font-size:12px;-fx-padding:4 10 4 10;-fx-cursor:hand;";
}