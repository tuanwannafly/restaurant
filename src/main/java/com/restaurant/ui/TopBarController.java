package com.restaurant.ui;

import com.restaurant.data.DataManager;
import com.restaurant.model.Restaurant;
import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * TopBarController
 * ────────────────
 * Backing controller for {@code TopBarView.fxml}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Populate restaurant name, role badge, and profile button from
 *       the current {@link AppSession}.</li>
 *   <li>Show the profile button only when the user has
 *       {@link Permission#EDIT_OWN_PROFILE}.</li>
 *   <li>Open {@code MyProfileDialog} on profile button click.</li>
 * </ul>
 *
 * <p>Called automatically by the FX FXML loader via
 * {@code <fx:include source="TopBarView.fxml"/>} in {@code MainView.fxml}.
 * {@link MainController} can obtain this controller via
 * {@code loader.getController()} and call {@link #refresh()} after
 * a session change if needed.
 */
public class TopBarController implements Initializable {

    // ── FXML injections ───────────────────────────────────────────────────
    @FXML private StackPane logoMark;
    @FXML private Label     lblRestaurantName;
    @FXML private Label     dotSeparator;
    @FXML private StackPane roleBadgeContainer;
    @FXML private Label     lblRoleBadge;
    @FXML private Button    btnProfile;

    // ── Initializable ─────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        refresh();
    }

    /**
     * Re-reads the current {@link AppSession} and repopulates all fields.
     * Safe to call multiple times (e.g. after profile update changes display name).
     */
    public void refresh() {
        AppSession session = AppSession.getInstance();

        // ── Restaurant name ────────────────────────────────────────────────
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

        // ── Role badge ─────────────────────────────────────────────────────
        lblRoleBadge.setText(session.getRoleLabel());

        // ── Profile button (RBAC gated) ────────────────────────────────────
        boolean canEditProfile = session.hasPermission(Permission.EDIT_OWN_PROFILE);
        String displayName = session.getUserName();
        if (canEditProfile && displayName != null && !displayName.isBlank()) {
            btnProfile.setText(displayName + "  ▾");
            btnProfile.setVisible(true);
            btnProfile.setManaged(true);
        }
    }

    // ── FXML handlers ──────────────────────────────────────────────────────

    @FXML
    private void handleProfileClick() {
        // Opens MyProfileDialog — window owner is the top-level Stage
        javafx.stage.Window owner = btnProfile.getScene().getWindow();
        // Instantiate via FXML loader or constructor, e.g.:
        // MyProfileDialog dlg = new MyProfileDialog((Stage) owner);
        // dlg.showAndWait();
        //
        // Stubbed here to keep TopBarController free of circular deps;
        // wire up in your dialog package:
        try {
            Class<?> dlgClass = Class.forName("com.restaurant.ui.dialog.MyProfileDialog");
            Object dlg = dlgClass
                    .getConstructor(javafx.stage.Stage.class)
                    .newInstance((javafx.stage.Stage) owner);
            dlgClass.getMethod("showAndWait").invoke(dlg);
        } catch (Exception ex) {
            // MyProfileDialog not yet implemented in JavaFX — ignore
            System.err.println("[TopBarController] MyProfileDialog not found: " + ex.getMessage());
        }
    }
}