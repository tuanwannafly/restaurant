// com/restaurant/ui/dialog/MenuDetailDialog.java
package com.restaurant.ui.dialog;

import com.restaurant.model.MenuItem;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class MenuDetailDialog {
    private final Stage owner;
    private final MenuItem item;

    public MenuDetailDialog(Stage owner, MenuItem item) {
        this.owner = owner;
        this.item  = item;
    }

    public void show() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Chi tiết: " + (item != null ? item.getName() : "—"),
                javafx.scene.control.ButtonType.OK);
        alert.setTitle("Chi tiết món ăn");
        alert.setHeaderText(null);
        alert.initOwner(owner);
        alert.showAndWait();
    }
}