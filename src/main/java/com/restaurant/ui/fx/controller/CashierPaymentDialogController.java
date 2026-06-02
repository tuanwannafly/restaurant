package com.restaurant.ui.fx.controller;

import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
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

    /** Nút in hóa đơn. */
    @FXML private Button           btnPrint;

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
            // [FIX] Bỏ qua món đã hủy — không hiển thị trên dialog thanh toán
            if (item.getItemStatus() == com.restaurant.model.Order.OrderItem.ItemStatus.CANCELLED) continue;

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
        if (req == null) return;

        // Disable nút trong lúc xử lý
        if (btnPrint != null) btnPrint.setDisable(true);

        // Lấy tiền nhận / tiền thừa tại thời điểm bấm in
        long cashReceived = 0;
        long cashChange   = 0;
        boolean isCash = getSelectedMethod() == CashierController.PaymentRequest.PaymentMethod.CASH;
        if (isCash && tfCashReceived != null) {
            try {
                cashReceived = Long.parseLong(
                    tfCashReceived.getText().trim().replace(",", "").replace(".", ""));
                cashChange = cashReceived - (long) req.getGrandTotal();
            } catch (NumberFormatException ignored) {
                cashReceived = req.getCashReceived();
                cashChange   = req.getChange();
            }
        }

        // Nhân viên đang chọn (có thể chưa chọn)
        String empName = (cbEmployee != null && cbEmployee.getValue() != null
                && !cbEmployee.getValue().equals("Chọn nhân viên")
                && !cbEmployee.getValue().equals("Đang tải..."))
                ? cbEmployee.getValue() : "";

        final long    fReceived = cashReceived;
        final long    fChange   = cashChange;
        final String  fEmp      = empName;
        final boolean fIsCash   = isCash;

        // Build receipt node và gửi printer — vẫn chạy trên FX thread vì
        // PrinterJob phải được tạo và showDialog trên FX Application Thread.
        VBox receipt = buildReceiptNode(fEmp, fIsCash, fReceived, fChange);

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showWarn("Không tìm thấy máy in. Hãy kiểm tra cài đặt máy in hệ thống.");
            if (btnPrint != null) btnPrint.setDisable(false);
            return;
        }

        // Chọn máy in + cài đặt trang (khổ A5 portrait — phù hợp hóa đơn nhà hàng)
        boolean proceed = job.showPrintDialog(getStage());
        if (!proceed) {
            job.cancelJob();
            if (btnPrint != null) btnPrint.setDisable(false);
            return;
        }

        // [FIX] Gắn receipt vào Scene tạm — applyCss()/layout() chỉ hoạt động khi node có Scene context
        new javafx.scene.Scene(new javafx.scene.Group(receipt));
        receipt.applyCss();
        receipt.layout();

        // Lấy kích thước trang để scale receipt vừa khổ
        PageLayout layout = job.getJobSettings().getPageLayout();
        double printableW = layout.getPrintableWidth();
        double printableH = layout.getPrintableHeight();

        // [FIX] Dùng getBoundsInLocal() sau khi đã layout
        //      getPrefHeight() trả -1 trước layout → scaleY âm → node bị lật ngược → in trang trắng
        double nodeW = receipt.getBoundsInLocal().getWidth();
        double nodeH = receipt.getBoundsInLocal().getHeight();
        if (nodeW <= 0) nodeW = receipt.getPrefWidth();
        if (nodeH <= 0) nodeH = printableH;

        // Scale receipt xuống nếu to hơn trang in
        double scaleX = printableW / nodeW;
        double scaleY = printableH / nodeH;
        double scale  = Math.min(1.0, Math.min(scaleX, scaleY));
        // [FIX] Guard: scale <= 0 hoặc NaN gây in trang trắng
        if (scale <= 0 || Double.isNaN(scale) || Double.isInfinite(scale)) scale = 1.0;
        receipt.setScaleX(scale);
        receipt.setScaleY(scale);


        boolean printed = job.printPage(receipt);
        if (printed) {
            job.endJob();
            Alert ok = new Alert(Alert.AlertType.INFORMATION, "Hóa đơn đã được gửi đến máy in.");
            ok.setHeaderText("In thành công");
            ok.initOwner(getStage());
            ok.showAndWait();
        } else {
            job.cancelJob();
            showWarn("Không thể in hóa đơn. Vui lòng thử lại hoặc kiểm tra máy in.");
        }

        if (btnPrint != null) btnPrint.setDisable(false);
    }

    // ─── Receipt builder ─────────────────────────────────────────────────────

    /**
     * Tạo Node VBox có dạng hóa đơn (receipt) để gửi vào {@link PrinterJob}.
     *
     * <p>Layout:
     * <pre>
     *   ══════════════════════════════
     *       HÓA ĐƠN THANH TOÁN
     *   ══════════════════════════════
     *   Bàn: xxx    Mã ĐH: xxxxxxxx
     *   Thời gian: dd/MM/yyyy HH:mm
     *   ──────────────────────────────
     *   Tên món             SL  Thành tiền
     *   ──────────────────────────────
     *   [items]
     *   ──────────────────────────────
     *   Tạm tính:           xxx đ
     *   VAT (10%):          xxx đ
     *   ══════════════════════════════
     *   TỔNG CỘNG:          xxx đ
     *   ══════════════════════════════
     *   [Nếu tiền mặt]
     *   Tiền nhận:          xxx đ
     *   Tiền thừa:          xxx đ
     *   ──────────────────────────────
     *   Phương thức: Tiền mặt / ...
     *   Nhân viên: xxx
     *   ──────────────────────────────
     *       Cảm ơn quý khách!
     *   Hẹn gặp lại!
     * </pre>
     */
    private VBox buildReceiptNode(String employeeName,
                                  boolean isCash,
                                  long cashReceived,
                                  long cashChange) {
        VBox root = new VBox(0);
        root.setPrefWidth(380);
        root.setStyle("-fx-background-color: white; -fx-padding: 24 28 24 28;");

        String LINE  = "──────────────────────────────────";
        String DLINE = "══════════════════════════════════";

        // ── Tiêu đề ──────────────────────────────────────────────────────────
        root.getChildren().add(receiptLabel(DLINE, 11, false, Pos.CENTER));
        Label title = receiptLabel("HÓA ĐƠN THANH TOÁN", 16, true, Pos.CENTER);
        title.setPadding(new Insets(4, 0, 4, 0));
        root.getChildren().add(title);
        root.getChildren().add(receiptLabel(DLINE, 11, false, Pos.CENTER));
        root.getChildren().add(receiptSpacer(6));

        // ── Thông tin đơn ─────────────────────────────────────────────────────
        String timeStr = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm  dd/MM/yyyy"));
        String orderShort = req.orderId != null && req.orderId.length() > 8
                ? req.orderId.substring(req.orderId.length() - 8).toUpperCase()
                : (req.orderId != null ? req.orderId : "--");

        root.getChildren().add(receiptRow("Bàn:", req.tableName, 13));
        root.getChildren().add(receiptRow("Mã đơn:", "#" + orderShort, 13));
        root.getChildren().add(receiptRow("Thời gian:", timeStr, 13));
        root.getChildren().add(receiptSpacer(6));
        root.getChildren().add(receiptLabel(LINE, 11, false, Pos.CENTER));
        root.getChildren().add(receiptSpacer(4));

        // ── Header cột món ────────────────────────────────────────────────────
        HBox colHeader = new HBox();
        Label colName = receiptLabel("MÓN ĂN", 11, true, Pos.CENTER_LEFT);
        HBox.setHgrow(colName, Priority.ALWAYS);
        colName.setMaxWidth(Double.MAX_VALUE);
        Label colQty  = new Label("SL");
        colQty.setFont(Font.font("Monospace", FontWeight.BOLD, 11));
        colQty.setMinWidth(28);
        colQty.setAlignment(Pos.CENTER);
        Label colAmt  = new Label("Thành tiền");
        colAmt.setFont(Font.font("Monospace", FontWeight.BOLD, 11));
        colAmt.setMinWidth(90);
        colAmt.setAlignment(Pos.CENTER_RIGHT);
        colHeader.getChildren().addAll(colName, colQty, colAmt);
        root.getChildren().add(colHeader);
        root.getChildren().add(receiptLabel(LINE, 11, false, Pos.CENTER));

        // ── Danh sách món ─────────────────────────────────────────────────────
        if (req.items != null) {
            for (OrderItem item : req.items) {
                // [FIX] Bỏ qua món đã hủy — không in vào hóa đơn
                if (item.getItemStatus() == com.restaurant.model.Order.OrderItem.ItemStatus.CANCELLED) continue;

                HBox row = new HBox(4);
                row.setPadding(new Insets(2, 0, 2, 0));

                // Tên món — wrap nếu dài
                Label lName = new Label(item.getMenuItemName());
                lName.setFont(Font.font("Monospace", 12));
                lName.setWrapText(true);
                lName.setMaxWidth(200);
                HBox.setHgrow(lName, Priority.ALWAYS);

                Label lQty = new Label(String.valueOf(item.getQuantity()));
                lQty.setFont(Font.font("Monospace", 12));
                lQty.setMinWidth(28);
                lQty.setAlignment(Pos.CENTER);

                Label lAmt = new Label(fmt(item.getSubtotal()) + "đ");
                lAmt.setFont(Font.font("Monospace", 12));
                lAmt.setMinWidth(90);
                lAmt.setAlignment(Pos.CENTER_RIGHT);

                row.getChildren().addAll(lName, lQty, lAmt);
                root.getChildren().add(row);

                // Dòng phụ: qty × đơn giá (nhỏ hơn)
                Label lUnitPrice = new Label(
                    "    " + item.getQuantity() + " × " + fmt(item.getUnitPrice()) + "đ");
                lUnitPrice.setFont(Font.font("Monospace", 10));
                lUnitPrice.setStyle("-fx-text-fill: #666666;");
                root.getChildren().add(lUnitPrice);
            }
        }

        root.getChildren().add(receiptLabel(LINE, 11, false, Pos.CENTER));
        root.getChildren().add(receiptSpacer(2));

        // ── Tổng ─────────────────────────────────────────────────────────────
        root.getChildren().add(receiptRow("Tạm tính:", fmt(req.getSubtotal()) + "đ", 12));
        root.getChildren().add(receiptRow("VAT (10%):", fmt(req.getVatAmount()) + "đ", 12));
        root.getChildren().add(receiptSpacer(4));
        root.getChildren().add(receiptLabel(DLINE, 11, false, Pos.CENTER));

        HBox grandRow = receiptRow("TỔNG CỘNG:", fmt(req.getGrandTotal()) + "đ", 15);
        grandRow.setPadding(new Insets(4, 0, 4, 0));
        // Bold the grand total label
        grandRow.getChildren().forEach(n -> {
            if (n instanceof Label l) l.setFont(Font.font("Monospace", FontWeight.BOLD, 15));
        });
        root.getChildren().add(grandRow);
        root.getChildren().add(receiptLabel(DLINE, 11, false, Pos.CENTER));
        root.getChildren().add(receiptSpacer(6));

        // ── Tiền mặt ─────────────────────────────────────────────────────────
        if (isCash) {
            root.getChildren().add(receiptRow("Tiền nhận:", fmt(cashReceived) + "đ", 12));
            root.getChildren().add(receiptRow("Tiền thừa:", fmt(cashChange)   + "đ", 12));
            root.getChildren().add(receiptSpacer(4));
        }

        // ── Phương thức & Nhân viên ──────────────────────────────────────────
        root.getChildren().add(receiptRow("Phương thức:", req.getPaymentMethodLabel(), 12));
        if (!employeeName.isBlank()) {
            root.getChildren().add(receiptRow("Nhân viên:", employeeName, 12));
        }

        root.getChildren().add(receiptSpacer(8));
        root.getChildren().add(receiptLabel(LINE, 11, false, Pos.CENTER));
        root.getChildren().add(receiptSpacer(6));

        // ── Footer ────────────────────────────────────────────────────────────
        Label thanks = receiptLabel("Cảm ơn quý khách!", 14, true, Pos.CENTER);
        thanks.setPadding(new Insets(2, 0, 2, 0));
        root.getChildren().add(thanks);
        root.getChildren().add(
            receiptLabel("Hẹn gặp lại quý khách!", 12, false, Pos.CENTER));
        root.getChildren().add(receiptSpacer(4));
        root.getChildren().add(receiptLabel(LINE, 11, false, Pos.CENTER));

        // applyCss()/layout() sẽ được gọi sau khi gắn vào Scene tạm trong onPrint()
        return root;
    }

    // ─── Receipt node helpers ────────────────────────────────────────────────

    /** Label đơn dùng font monospace (phù hợp receipt cân chỉnh cột). */
    private static Label receiptLabel(String text, double size,
                                      boolean bold, Pos align) {
        Label l = new Label(text);
        l.setFont(bold
            ? Font.font("Monospace", FontWeight.BOLD, size)
            : Font.font("Monospace", size));
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(align);
        return l;
    }

    /**
     * Hàng key–value: [key grow=ALWAYS] [value right-align].
     * Trả về HBox để caller có thể override style nếu cần.
     */
    private static HBox receiptRow(String key, String value, double size) {
        HBox row = new HBox();
        row.setPadding(new Insets(1, 0, 1, 0));

        Label lKey = new Label(key);
        lKey.setFont(Font.font("Monospace", size));
        HBox.setHgrow(lKey, Priority.ALWAYS);
        lKey.setMaxWidth(Double.MAX_VALUE);

        Label lVal = new Label(value);
        lVal.setFont(Font.font("Monospace", FontWeight.BOLD, size));
        lVal.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(lKey, lVal);
        return row;
    }

    /** Khoảng cách dọc giữa các section. */
    private static Region receiptSpacer(double height) {
        Region r = new Region();
        r.setPrefHeight(height);
        return r;
    }

    @FXML
    private void onBack() {
        closeDialog();
    }

    // ─── Async employee loading ───────────────────────────────────────────────

    private void loadEmployeesAsync() {
        com.restaurant.session.AppSession session = com.restaurant.session.AppSession.getInstance();
        String role = session.getUserRole();
        long   uid  = session.getUserId();

        // Đăng nhập bằng tài khoản CASHIER/THU_NGAN → tự động gán, không cần chọn thủ công
        boolean isCashierLogin = "CASHIER".equalsIgnoreCase(role)
                              || "THU_NGAN".equalsIgnoreCase(role);

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                if (isCashierLogin && uid > 0) {
                    // Tìm bản ghi nhân viên liên kết với tài khoản đang đăng nhập
                    Optional<Employee> empOpt = new EmployeeDAO().findByUserId(uid);
                    if (empOpt.isPresent()) {
                        return List.of(empOpt.get().getName());   // chỉ trả về chính mình
                    }
                    // Tài khoản CASHIER chưa liên kết employee → fallback load all
                }
                // Không phải CASHIER (hoặc không tìm thấy): load toàn bộ thu ngân để chọn
                return new EmployeeDAO().findAll().stream()
                    .filter(e -> e.getRole() == Employee.Role.THU_NGAN)
                    .map(Employee::getName)
                    .collect(Collectors.toList());
            }
        };

        task.setOnSucceeded(e -> {
            List<String> names = task.getValue();
            cbEmployee.getItems().clear();

            if (isCashierLogin && names.size() == 1) {
                // Tài khoản thu ngân: tự động chọn chính mình, khoá lại
                cbEmployee.getItems().addAll(names);
                cbEmployee.getSelectionModel().selectFirst();
                cbEmployee.setDisable(true);
            } else {
                // Không phải thu ngân hoặc chưa liên kết → cho phép chọn thủ công
                cbEmployee.getItems().add("Chọn nhân viên");
                if (names.isEmpty()) {
                    cbEmployee.getItems().add("(Không có thu ngân)");
                    cbEmployee.setDisable(true);
                } else {
                    cbEmployee.getItems().addAll(names);
                    cbEmployee.setDisable(false);
                }
                cbEmployee.getSelectionModel().selectFirst();
            }
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