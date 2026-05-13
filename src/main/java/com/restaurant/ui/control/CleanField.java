package com.restaurant.ui.control;

import javafx.scene.control.TextField;

/**
 * CleanField — TextField tùy chỉnh với style "focus-ring" qua CSS.
 *
 * <p>Chỉ cần thêm style class {@code clean-field} (đã khai báo trong app.css).
 * Khi focused: border chuyển sang PRIMARY (#2563EB), box-shadow nhẹ.
 * Khi disabled: nền xám nhạt (#F3F4F6), chữ TEXT_SECONDARY.
 *
 * <p><b>Vị trí:</b> {@code src/main/java/com/restaurant/ui/control/CleanField.java}
 */
public class CleanField extends TextField {

    public CleanField() {
        getStyleClass().add("clean-field");
    }

    public CleanField(String text) {
        super(text);
        getStyleClass().add("clean-field");
    }
}