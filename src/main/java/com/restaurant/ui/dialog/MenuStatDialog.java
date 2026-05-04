// com/restaurant/ui/dialog/MenuStatDialog.java
package com.restaurant.ui.dialog;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class MenuStatDialog {
    private final Stage owner;

    public MenuStatDialog(Stage owner) {
        this.owner = owner;
    }

    public void show() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Thống kê món ăn đang được phát triển.", 
                javafx.scene.control.ButtonType.OK);
        alert.setTitle("Thống kê");
        alert.setHeaderText(null);
        alert.initOwner(owner);
        alert.showAndWait();
    }
}