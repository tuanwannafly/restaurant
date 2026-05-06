package com.restaurant.ui;

import java.net.URL;
import java.util.ResourceBundle;

import com.restaurant.data.DataManager;
import com.restaurant.model.Restaurant;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.StackPane;

/**
 * TopBarController — backing controller for {@code TopBarView.fxml}.
 *
 * Changes v2:
 *  - Profile button now opens a ContextMenu with profile + logout options.
 */
public class TopBarController implements Initializable {

    @FXML private StackPane logoMark;
    @FXML private Label     lblRestaurantName;
    @FXML private Label     dotSeparator;
    @FXML private StackPane roleBadgeContainer;
    @FXML private Label     lblRoleBadge;
    @FXML private Button    btnProfile;

    private ContextMenu profileMenu;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        buildProfileMenu();
        refresh();
    }

    public void refresh() {
        AppSession session = AppSession.getInstance();

        // Restaurant name
        if (session.getRestaurantId() != 0) {
            Restaurant r = DataManager.getInstance().getMyRestaurant();
            if (r != null && r.getName() != null && !r.getName().isBlank()) {
                lblRestaurantName.setText(r.getName());
                lblRestaurantName.setVisible(true);
                lblRestaurantName.setManaged(true);
                dotSeparator.setVisible(true);
                dotSeparator.setManaged(true);
            }
        }

        // Role badge
        lblRoleBadge.setText(session.getRoleLabel());

        // Profile button — chỉ hiển thị khi có quyền EDIT_OWN_PROFILE
        boolean canEditProfile = session.hasPermission(Permission.EDIT_OWN_PROFILE);
        String displayName = session.getUserName();
        boolean showBtn = canEditProfile && displayName != null && !displayName.isBlank();
        btnProfile.setVisible(showBtn);
        btnProfile.setManaged(showBtn);
        if (showBtn) {
            btnProfile.setText(displayName + "  \u25BE");
        }
    }

    /** Builds the dropdown ContextMenu shown when user clicks the profile button. */
    private void buildProfileMenu() {
        profileMenu = new ContextMenu();
        profileMenu.getStyleClass().add("profile-context-menu");

        MenuItem miProfile = new MenuItem("Thong tin ca nhan");
        miProfile.setOnAction(e -> openProfileDialog());

        SeparatorMenuItem sep = new SeparatorMenuItem();

        MenuItem miLogout = new MenuItem("Dang xuat");
        miLogout.getStyleClass().add("menu-item-danger");
        miLogout.setOnAction(e -> AppSession.getInstance().logout());

        profileMenu.getItems().addAll(miProfile, sep, miLogout);
    }

    @FXML
    private void handleProfileClick() {
        if (profileMenu != null && btnProfile != null) {
            profileMenu.show(btnProfile,
                javafx.geometry.Side.BOTTOM,
                0, 4);
        }
    }

    private void openProfileDialog() {
        if (btnProfile == null || btnProfile.getScene() == null) return;
        javafx.stage.Window owner = btnProfile.getScene().getWindow();
        try {
            Class<?> dlgClass = Class.forName("com.restaurant.ui.dialog.MyProfileDialog");
            Object dlg = dlgClass
                    .getConstructor(javafx.stage.Stage.class)
                    .newInstance((javafx.stage.Stage) owner);
            dlgClass.getMethod("showAndWait").invoke(dlg);
        } catch (Exception ex) {
            System.err.println("[TopBarController] MyProfileDialog not found: " + ex.getMessage());
        }
    }
}