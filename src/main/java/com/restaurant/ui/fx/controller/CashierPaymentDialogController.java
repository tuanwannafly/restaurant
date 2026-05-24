package com.restaurant.ui.fx.controller;

import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.restaurant.dao.EmployeeDAO;
import com.restaurant.model.Employee;
import com.restaurant.model.Order.OrderItem;
import com.restaurant.ui.fx.controller.CashierController.PaymentRequest;
import com.restaurant.ui.fx.controller.CashierController.PaymentRequest.PaymentMethod;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * CashierPaymentDialogController — Phase 10 · Thu ngân – Dialog thanh toán
 * ─────────────────────────────────────────────────────────────────────────────
 * Dialog thực tế của nhà hàng: hiện đủ danh sách món, tạm tính, VAT 10%,
 * tổng cộng, phương thức thanh toán, tiền thừa (nếu tiền mặt), nhân viên.
 *
 * <h3>Callback</h3>
 * {@code BiConsumer<String, String>} — (employeeName, paymentMethodName)
 *
 * <h3>FXML</h3>
 * {@code src/main/resources/fxml/dialog/CashierPaymentDialog.fxml}
 */
public class CashierPaymentDialogController implements Initializable {

    // ─── FXML Injections ─────────────────────────────────────────────────────

    /** Header: tên bàn. */
    @FXML private Label            lblTableName;

    /** Header: giờ mở bàn. */
    @FXML private Label            lblCreatedTime;

    /** Container chứa các hàng món ăn — được build động bởi buildItemRows(). */
    @FXML private VBox             itemsContainer;

    /** Tạm tính (trước VAT). */
    @FXML private Label            lblSubtotal;

    /** VAT 10%. */
    @FXML private Label            lblVat;

    /** Tổng cộng (đã VAT) — text lớn, bold. */
    @FXML private Label            lblGrandTotal;

    /** ComboBox chọn phương thức thanh toán. */
    @FXML private ComboBox<String> cbMethod;

    /** Panel tiền mặt — chỉ hiện khi cbMethod = Tiền mặt. */
    @FXML private VBox             cashPanel;

    /** Tiền khách đưa — TextField, pre-fill bằng getCashReceived(). */
    @FXML private TextField        tfCashReceived;

    /** Tiền thừa — tự cập nhật khi tfCashReceived thay đổi. */
    @FXML private Label            lblChange;

    /** ComboBox chọn nhân viên thu ngân. */
    @FXML private ComboBox<String> cbEmployee;

    // ─── State ───────────────────────────────────────────────────────────────

    private PaymentRequest req;

    /**
     * Callback nhận (employeeName, paymentMethodName) khi xác nhận.
     */
    private BiConsumer<String, String> onConfirmCallback;

    // ─── initialize ──────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cbEmployee.setPromptText("Đang tải...");
        cbEmployee.setDisable(true);

        // Populate payment method ComboBox
        cbMethod.getItems().setAll(
            PaymentMethod.CASH.name()          + "|" + PaymentMethod.CASH.getLabel(),
            PaymentMethod.BANK_TRANSFER.name() + "|" + PaymentMethod.BANK_TRANSFER.getLabel(),
            PaymentMethod.CARD.name()          + "|" + PaymentMethod.CARD.getLabel(),
            PaymentMethod.MOMO.name()          + "|" + PaymentMethod.MOMO.getLabel(),
            PaymentMethod.VNPAY.name()         + "|" + PaymentMethod.VNPAY.getLabel()
        );
        // Custom cell factory để hiển thị chỉ label
        javafx.util.StringConverter<String> methodConverter = new javafx.util.StringConverter<>() {
            @Override public String toString(String item) {
                if (item == null) return "";
                int idx = item.indexOf('|');
                return idx >= 0 ? item.substring(idx + 1) : item;
            }
            @Override public String fromString(String s) { return s; }
        };
        cbMethod.setConverter(methodConverter);
        cbMethod.getSelectionModel().selectFirst(); // default CASH

        // Khi đổi method → show/hide cashPanel + recalculate change
        cbMethod.setOnAction(e -> onMethodChanged());
    }

    // ─── initData ────────────────────────────────────────────────────────────

    /**
     * Inject dữ liệu và callback. <b>Gọi trước stage.show().</b>
     *
     * @param req              đơn hàng cần thanh toán
     * @param onConfirmCallback (employeeName, paymentMethodName) khi xác nhận
     */
    public void initData(PaymentRequest req, BiConsumer<String, String> onConfirmCallback) {
        this.req               = req;
        this.onConfirmCallback = onConfirmCallback;

        // Header
        lblTableName.setText(req.tableName);
        lblCreatedTime.setText(req.createdTime != null && !req.createdTime.isBlank()
            ? "Mở lúc " + req.createdTime : "");

        // Pre-select method từ tablet
        preSelectMethod(req.paymentMethod);

        // Build item rows
        buildItemRows();

        // Totals
        refreshTotals();

        // Cash panel
        onMethodChanged();

        // Load employees async
        loadEmployeesAsync();
    }

    // ─── Build item rows ─────────────────────────────────────────────────────

    /**
     * Tạo các hàng danh sách món: [Tên món] [qty × giá] [thành tiền]
     */
    private void buildItemRows() {
        itemsContainer.getChildren().clear();

        if (req.items == null || req.items.isEmpty()) {
            Label empty = new Label("(Không có thông tin món)");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic;");
            itemsContainer.getChildren().add(empty);
            return;
        }

        for (OrderItem item : req.items) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 4 0 4 0;");

            // Tên món (grow)
            Label lblName = new Label(item.getMenuItemName());
            lblName.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
            HBox.setHgrow(lblName, Priority.ALWAYS);
            lblName.setMaxWidth(Double.MAX_VALUE);

            // qty × đơn giá
            Label lblQtyPrice = new Label(item.getQuantity() + " × " + fmt(item.getUnitPrice()) + "đ");
            lblQtyPrice.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-min-width: 120;");
            lblQtyPrice.setAlignment(Pos.CENTER_RIGHT);

            // Thành tiền
            Label lblSub = new Label(fmt(item.getSubtotal()) + "đ");
            lblSub.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e40af; -fx-min-width: 90;");
            lblSub.setAlignment(Pos.CENTER_RIGHT);

            row.getChildren().addAll(lblName, lblQtyPrice, lblSub);
            itemsContainer.getChildren().add(row);
        }
    }

    // ─── Totals ──────────────────────────────────────────────────────────────

    private void refreshTotals() {
        double sub   = req.getSubtotal();
        double vat   = req.getVatAmount();
        double grand = req.getGrandTotal();

        lblSubtotal.setText(fmt(sub) + "đ");
        lblVat.setText(fmt(vat) + "đ");
        lblGrandTotal.setText(fmt(grand) + "đ");
    }

    // ─── Payment method ──────────────────────────────────────────────────────

    /** Pre-select ComboBox theo method từ tablet. */
    private void preSelectMethod(PaymentMethod method) {
        String key = (method != null ? method : PaymentMethod.CASH).name();
        cbMethod.getItems().stream()
            .filter(s -> s.startsWith(key + "|"))
            .findFirst()
            .ifPresent(s -> cbMethod.getSelectionModel().select(s));
    }

    /** Lấy PaymentMethod đang chọn. */
    private PaymentMethod getSelectedMethod() {
        String selected = cbMethod.getValue();
        if (selected == null) return PaymentMethod.CASH;
        try {
            return PaymentMethod.valueOf(selected.split("\\|")[0]);
        } catch (Exception e) {
            return PaymentMethod.CASH;
        }
    }

    /** Show/hide cashPanel + update tiền thừa khi method thay đổi. */
    private void onMethodChanged() {
        boolean isCash = getSelectedMethod() == PaymentMethod.CASH;
        if (cashPanel != null) {
            cashPanel.setVisible(isCash);
            cashPanel.setManaged(isCash);
        }
        if (isCash) {
            // Pre-fill tiền khách đưa (làm tròn lên bội 50k)
            if (tfCashReceived != null) {
                tfCashReceived.setText(String.valueOf(req.getCashReceived()));
                updateChange();
                // Lắng nghe thay đổi để tính tiền thừa real-time
                tfCashReceived.textProperty().addListener((obs, o, n) -> updateChange());
            }
        }
    }

    /** Tính và hiển thị tiền thừa. */
    private void updateChange() {
        if (lblChange == null || tfCashReceived == null) return;
        try {
            long received = Long.parseLong(tfCashReceived.getText().trim().replace(",", "").replace(".", ""));
            long grand    = (long) req.getGrandTotal();
            long change   = received - grand;
            if (change < 0) {
                lblChange.setText("Thiếu " + fmt(-change) + "đ");
                lblChange.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
            } else {
                lblChange.setText(fmt(change) + "đ");
                lblChange.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
            }
        } catch (NumberFormatException e) {
            lblChange.setText("--");
            lblChange.setStyle("-fx-text-fill: #94a3b8;");
        }
    }

    // ─── FXML Actions ────────────────────────────────────────────────────────

    @FXML
    private void onConfirm() {
        // Validate: tiền đủ nếu CASH
        if (getSelectedMethod() == PaymentMethod.CASH && tfCashReceived != null) {
            try {
                long received = Long.parseLong(
                    tfCashReceived.getText().trim().replace(",", "").replace(".", ""));
                if (received < (long) req.getGrandTotal()) {
                    showWarn("Tiền khách đưa (" + fmt(received) + "đ) ít hơn tổng cộng ("
                        + fmt((long) req.getGrandTotal()) + "đ). Vui lòng kiểm tra lại.");
                    return;
                }
            } catch (NumberFormatException e) {
                showWarn("Số tiền khách đưa không hợp lệ.");
                return;
            }
        }

        // Validate: nhân viên
        String selected = cbEmployee.getValue();
        if (selected == null || selected.isBlank()
                || selected.equals("Chọn nhân viên")
                || selected.equals("Đang tải...")
                || selected.equals("(Không có thu ngân)")) {
            showWarn("Vui lòng chọn nhân viên phụ trách trước khi thực hiện.");
            return;
        }

        String methodName = getSelectedMethod().name();
        closeDialog();
        if (onConfirmCallback != null) {
            onConfirmCallback.accept(selected, methodName);
        }
    }

    @FXML
    private void onPrint() {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Thông báo");
        info.setHeaderText(null);
        info.setContentText("Chức năng in hóa đơn đang phát triển.");
        info.initOwner(getStage());
        info.showAndWait();
    }

    @FXML
    private void onBack() {
        closeDialog();
    }

    // ─── Async employee loading ───────────────────────────────────────────────

    private void loadEmployeesAsync() {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return new EmployeeDAO().findAll().stream()
                    .filter(e -> e.getRole() == Employee.Role.THU_NGAN)
                    .map(Employee::getName)
                    .collect(Collectors.toList());
            }
        };

        task.setOnSucceeded(e -> {
            List<String> names = task.getValue();
            cbEmployee.getItems().clear();
            cbEmployee.getItems().add("Chọn nhân viên");
            if (names.isEmpty()) {
                cbEmployee.getItems().add("(Không có thu ngân)");
                cbEmployee.setDisable(true);
            } else {
                cbEmployee.getItems().addAll(names);
                cbEmployee.setDisable(false);
            }
            cbEmployee.getSelectionModel().selectFirst();
        });

        task.setOnFailed(e ->
            System.err.println("[CashierPaymentDialogController] loadEmployees lỗi: "
                + task.getException().getMessage()));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void showWarn(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Kiểm tra lại");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.initOwner(getStage());
        alert.showAndWait();
    }

    private String fmt(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format((long) amount);
    }

    private Stage getStage() {
        try { return (Stage) cbEmployee.getScene().getWindow(); } catch (Exception e) { return null; }
    }

    private void closeDialog() {
        Stage s = getStage();
        if (s != null) s.close();
    }
}