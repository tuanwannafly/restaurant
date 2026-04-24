package com.restaurant;

import com.restaurant.ui.LoginDialog;
import com.restaurant.ui.MainFrame;

import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        System.setProperty("file.encoding",               "UTF-8");
        System.setProperty("sun.java2d.uiScale",          "1.0");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext",                "true");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

                Font globalFont = new Font("Segoe UI", Font.PLAIN, 13);
                UIManager.put("Button.font",            globalFont);
                UIManager.put("Label.font",             globalFont);
                UIManager.put("Table.font",             globalFont);
                UIManager.put("TableHeader.font",       new Font("Segoe UI", Font.BOLD, 13));
                UIManager.put("TextField.font",         globalFont);
                UIManager.put("ComboBox.font",          globalFont);
                UIManager.put("TextArea.font",          globalFont);
                UIManager.put("PasswordField.font",     globalFont);
                UIManager.put("OptionPane.messageFont", globalFont);
                UIManager.put("OptionPane.buttonFont",  globalFont);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Bước 1: Hiện dialog đăng nhập
            LoginDialog loginDialog = new LoginDialog(null);
            loginDialog.setVisible(true);  // block đến khi dispose()

            // Bước 2: Nếu đăng nhập thành công → mở MainFrame
            if (loginDialog.isLoginSuccess()) {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
            } else {
                // Người dùng đóng cửa sổ login → thoát app
                System.exit(0);
            }
        });
    }
}
