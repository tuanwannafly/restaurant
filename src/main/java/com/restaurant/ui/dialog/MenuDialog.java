// com/restaurant/ui/dialog/MenuDialog.java
package com.restaurant.ui.dialog;

import java.util.function.Consumer;

import com.restaurant.model.MenuItem;

import javafx.stage.Stage;

public class MenuDialog {
    private final Stage stage;

    public MenuDialog(Stage owner, MenuItem item, Consumer<MenuItem> onSave) {
        this.stage = MenuDialogController.create(owner, item, onSave);
    }

    public void show() { stage.show(); }
}