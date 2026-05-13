package com.restaurant.ui.fx.controller;

import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.restaurant.dao.EmployeeDAO;
import com.restaurant.model.Employee;
import com.restaurant.ui.fx.controller.CashierController.PaymentRequest;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * CashierPaymentDialogController — Phase 10 · Thu ngân – Dialog thanh toán
 * ─────────────────────────────────────────────────────────────────────────────
 * Controller cho {@code fxml/dialog/CashierPaymentDialog.fxml}.
 * Tương đương {@code com.restaurant.ui.dialog.CashierPaymentDialog} (Swing Phase 5D).
 *
 * <h3>Luồng hoạt động:</h3>
 * <ol>
 *   <li>{@link CashierController#openPaymentDialog(PaymentRequest)} load FXML</li>
 *   <li>Gọi {@link #initData(PaymentRequest, Consumer)} để inject dữ liệu + callback</li>
 *   <li>{@link #initialize(URL, ResourceBundle)} gọi {@link #loadEmployeesAsync()}</li>
 *   <li>Thu ngân chọn nhân viên, nhấn "Thực hiện" → {@link #onConfirm()}</li>
 *   <li>{@code onConfirmCallback} được gọi với tên nhân viên đã chọn</li>
 * </ol>
 *
 * <h3>FXML tương ứng:</h3>
 * {@code src/main/resources/fxml/dialog/CashierPaymentDialog.fxml}
 */
public class CashierPaymentDialogController implements Initializable {

    // ─── FXML Injections ─────────────────────────────────────────────────────

    /** Tên bàn in đậm trong header PRIMARY (ví dụ: "Bàn 01"). */
    @FXML private Label             lblTableName;

    /**
     * Phương thức thanh toán + ước tính tiền khách đưa.
     * Ví dụ: "Phương thức thanh toán: Tiền mặt (Tiền khách đưa: 300.000đ)"
     */
    @FXML private Label             lblMethodInfo;

    /** Tổng cộng. Ví dụ: "Tổng cộng: 250.000đ". */
    @FXML private Label             lblTotal;

    /**
     * ComboBox chọn nhân viên thu ngân.
     * Được populate bởi {@link #loadEmployeesAsync()} với role {@code THU_NGAN}.
     * Disabled khi đang load, enabled sau khi load xong.
     */
    @FXML private ComboBox<String>  cbEmployee;

    // ─── State ───────────────────────────────────────────────────────────────

    /** Đơn hàng cần xác nhận thanh toán. Inject qua {@link #initData}. */
    private PaymentRequest req;

    /**
     * Callback được gọi khi nhấn "Thực hiện" thành công.
     * Nhận tên nhân viên được chọn làm tham số.
     * Tương đương {@code Consumer<String> onConfirm} trong Swing dialog.
     */
    private Consumer<String> onConfirmCallback;

    // ─── initialize ──────────────────────────────────────────────────────────

    /**
     * Gọi tự động bởi FXMLLoader.
     * Lưu ý: {@link #req} chưa có ở đây — dữ liệu thực sự được inject qua
     * {@link #initData(PaymentRequest, Consumer)} sau khi load.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // ComboBox placeholder trước khi load xong
        cbEmployee.setPromptText("Đang tải...");
        cbEmployee.setDisable(true);
    }

    // ─── initData ────────────────────────────────────────────────────────────

    /**
     * Inject dữ liệu đơn hàng và callback vào controller.
     * <b>Phải gọi TRƯỚC khi stage.show() / showAndWait().</b>
     *
     * @param req              đơn hàng cần thanh toán
     * @param onConfirmCallback callback nhận tên nhân viên khi xác nhận thành công
     */
    public void initData(PaymentRequest req, Consumer<String> onConfirmCallback) {
        this.req              = req;
        this.onConfirmCallback = onConfirmCallback;

        // Bind dữ liệu tĩnh ngay lập tức
        lblTableName.setText(req.tableName);
        lblMethodInfo.setText(buildPaymentMethodText());
        lblTotal.setText("Tổng cộng: " + formatAmount(req.totalAmount) + "đ");

        // Load danh sách nhân viên bất đồng bộ
        loadEmployeesAsync();
    }

    // ─── FXML Actions ────────────────────────────────────────────────────────

    /**
     * Xử lý click "Thực hiện".
     * Validate nhân viên → đóng dialog → gọi callback.
     * Tương đương {@code CashierPaymentDialog#handleConfirm()} (Swing Phase 5D).
     */
    @FXML
    private void onConfirm() {
        String selected = cbEmployee.getValue();

        // Validate: phải chọn nhân viên hợp lệ
        if (selected == null
                || selected.isBlank()
                || selected.equals("Chọn nhân viên")
                || selected.equals("Đang tải...")
                || selected.equals("(Không có thu ngân)")) {

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Thiếu thông tin");
            alert.setHeaderText(null);
            alert.setContentText("Vui lòng chọn nhân viên phụ trách trước khi thực hiện.");
            alert.initOwner(getStage());
            alert.showAndWait();
            return;
        }

        // Đóng dialog trước, sau đó gọi callback
        closeDialog();
        if (onConfirmCallback != null) {
            onConfirmCallback.accept(selected);
        }
    }

    /**
     * Xử lý click "In hóa đơn" — stub, Phase 10F sẽ implement.
     */
    @FXML
    private void onPrint() {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Thông báo");
        info.setHeaderText(null);
        info.setContentText("Chức năng in đang phát triển.");
        info.initOwner(getStage());
        info.showAndWait();
    }

    /**
     * Xử lý click "←" — đóng dialog không làm gì.
     */
    @FXML
    private void onBack() {
        closeDialog();
    }

    // ─── Async Employee Loading ───────────────────────────────────────────────

    /**
     * Load danh sách nhân viên {@code THU_NGAN} bất đồng bộ từ {@link EmployeeDAO}.
     * Tương đương {@code CashierPaymentDialog#loadEmployeesAsync()} (Swing Phase 5D).
     *
     * <p>Kết quả được set vào {@link #cbEmployee} trên JavaFX Application Thread.
     */
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

            // Placeholder đầu tiên
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

    /**
     * Xây dựng text phương thức thanh toán.
     * Với CASH: thêm ước tính "tiền khách đưa" = làm tròn lên bội 50.000.
     * Tương đương {@code CashierPaymentDialog#buildPaymentMethodText()} (Swing).
     */
    private String buildPaymentMethodText() {
        if (req == null) return "Phương thức thanh toán: --";
        String method = req.getPaymentMethodLabel();
        if (req.paymentMethod == PaymentRequest.PaymentMethod.CASH) {
            long rounded = (long) (Math.ceil(req.totalAmount / 50_000.0) * 50_000);
            return "Phương thức thanh toán: " + method
                + " (Tiền khách đưa: " + formatAmount(rounded) + "đ)";
        }
        return "Phương thức thanh toán: " + method;
    }

    /** Định dạng số tiền VND. */
    private String formatAmount(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format((long) amount);
    }

    /** Lấy Stage của dialog để truyền initOwner cho Alert. */
    private Stage getStage() {
        try {
            return (Stage) cbEmployee.getScene().getWindow();
        } catch (Exception e) {
            return null;
        }
    }

    /** Đóng dialog (Stage). */
    private void closeDialog() {
        Stage stage = getStage();
        if (stage != null) stage.close();
    }
}