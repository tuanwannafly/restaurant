package com.restaurant;

import java.awt.Font;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.restaurant.dao.UserDAO;
import com.restaurant.data.DataManager;
import com.restaurant.session.AppSession;
import com.restaurant.session.RefreshTokenService;
import com.restaurant.session.TokenStorage;
import com.restaurant.ui.LoginDialog;
import com.restaurant.ui.MainFrame;

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

            // Dọn dẹp token hết hạn/đã dùng từ phiên trước
            try {
                DataManager.getInstance().cleanupExpiredResetTokens();
            } catch (Exception ignored) { /* Không block khởi động nếu DB chưa sẵn sàng */ }
            try {
                RefreshTokenService.getInstance().cleanExpiredTokens();
            } catch (Exception ignored) { /* Không block khởi động */ }

            // ── Silent re-auth: kiểm tra refresh token đã lưu trên disk ────────
            boolean silentLoginOk = false;
            try {
                java.util.Optional<String> savedToken = TokenStorage.getInstance().loadRefreshToken();
                if (savedToken.isPresent()) {
                    java.util.Optional<Long> userIdOpt =
                            RefreshTokenService.getInstance().validateAndRotate(savedToken.get());
                    if (userIdOpt.isPresent()) {
                        // Token hợp lệ → load thông tin user từ DB vào AppSession
                        silentLoginOk = new UserDAO().loginByUserId(userIdOpt.get());
                    } else {
                        // Token đã hết hạn hoặc bị revoke → xoá file
                        TokenStorage.getInstance().clearSavedToken();
                    }
                }
            } catch (Exception silentEx) {
                System.err.println("[Main] Silent re-auth thất bại: " + silentEx.getMessage());
                TokenStorage.getInstance().clearSavedToken();
                silentLoginOk = false;
            }

            // Nếu silent login thành công → mở MainFrame trực tiếp, không cần LoginDialog
            if (silentLoginOk && AppSession.getInstance().isLoggedIn()) {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
                return;
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
