package com.restaurant.ui.dialog;

import com.restaurant.model.TableItem;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class TableDialogController {

    @FXML private Label         lblTitle;
    @FXML private TextField     tfName;
    @FXML private TextField     tfCapacity;
    @FXML private ComboBox<String> cbStatus;
    @FXML private Label         errName;
    @FXML private Label         errCapacity;

    private TableItem item;      // null → add mode
    private TableItem result;
    private boolean   saved = false;

    @FXML
    private void initialize() {
        cbStatus.getItems().addAll("RANH", "DAT_TRUOC", "DIRTY", "CLEANING");
        cbStatus.setValue("RANH");
    }

    /** Called by TableController after FXMLLoader.load(). */
    public void setItem(TableItem item) {
        this.item = item;
        if (item != null) {
            lblTitle.setText("Cập nhật bàn");
            tfName.setText(item.getName());
            tfCapacity.setText(String.valueOf(item.getCapacity()));
            if (item.getStatus() != null) cbStatus.setValue(item.getStatus().name());
        } else {
            lblTitle.setText("Thêm bàn mới");
        }
    }

    public boolean   isSaved()   { return saved; }
    public TableItem getResult() { return result; }

    @FXML
    private void onSave() {
        errName.setText("");
        errCapacity.setText("");

        String name = tfName.getText().trim();
        String capStr = tfCapacity.getText().trim();

        boolean valid = true;
        if (name.isEmpty()) {
            errName.setText("Tên bàn không được trống.");
            valid = false;
        }
        int capacity = 0;
        try {
            capacity = Integer.parseInt(capStr);
            if (capacity <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            errCapacity.setText("Sức chứa phải là số nguyên dương.");
            valid = false;
        }
        if (!valid) return;

        TableItem.Status status = TableItem.Status.valueOf(cbStatus.getValue());
        String id = (item != null) ? item.getId() : null;
        result = new TableItem(id, name, capacity, status);
        saved  = true;
        close();
    }

    @FXML
    private void onCancel() { close(); }

    private void close() {
        ((Stage) tfName.getScene().getWindow()).close();
    }
}