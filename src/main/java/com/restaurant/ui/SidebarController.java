package com.restaurant.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.restaurant.session.AppSession;
import com.restaurant.session.Permission;
import com.restaurant.session.RbacGuard;
import com.restaurant.ui.control.BadgeLabel;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * SidebarController
 * ─────────────────
 * Builds the left navigation sidebar programmatically, mirroring the
 * logic in {@code MainFrame#buildSidebar()} and {@code #applyRoleFilter()}.
 *
 * <p><b>RBAC:</b> Each nav item is only added if {@link Permission#forRole}
 * grants the required permission for the current user's role.
 * Items for kitchen / waiter / cashier also get a {@link BadgeLabel}
 * instead of a plain label, so badge counts can be updated at runtime.
 *
 * <p><b>Active state:</b> Managed via the CSS pseudo-class {@code :active-nav}
 * applied to the nav item container ({@code VBox} child). The active item
 * also shows a 3 px accent stripe on the left edge — painted with CSS.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   SidebarController sidebar = new SidebarController(page -> mainCtrl.navigateTo(page));
 *   sidebar.build();
 *   borderPane.setLeft(sidebar.getRoot());
 *   sidebar.setActivePage("home");
 * }</pre>
 */
public class SidebarController {

    // ── CSS pseudo-class ──────────────────────────────────────────────────
    private static final PseudoClass ACTIVE_NAV =
            PseudoClass.getPseudoClass("active-nav");

    // ── Nav item descriptor ───────────────────────────────────────────────

    /**
     * Lightweight DTO that drives nav item creation.
     *
     * @param key        card/page key (e.g. "bep")
     * @param label      display label in Vietnamese (e.g. "Bếp")
     * @param permission required permission, or {@code null} = always show
     *                   (conditional on role checks elsewhere)
     * @param badged     whether this item needs a {@link BadgeLabel}
     * @param visibleFor special visibility rule (overrides permission check
     *                   when non-null): "super_admin_only", "restaurant_admin_only",
     *                   "not_super_admin"
     */
    private record NavItem(
            String key,
            String label,
            Permission permission,
            boolean badged,
            String visibleFor
    ) {}

    // ── Nav catalog ───────────────────────────────────────────────────────
    //   Mirrors NAV_PAGES / applyRoleFilter() from MainFrame.java

    private static final List<NavItem> NAV_CATALOG = List.of(
        // FIX 1: removed stray Permission.VIEW_ORDER arg from 4th position;
        //        badged=false is correct — home never shows a badge counter
        new NavItem("home",           "Trang chủ",        null,
                false, "not_super_admin_but_home"),

        new NavItem("menu",           "Thực đơn",         null,
                false, "not_super_admin"),

        new NavItem("ban",            "Quản bàn",         null,
                false, "not_super_admin"),

        new NavItem("nhanvien",       "Nhân viên",        Permission.VIEW_EMPLOYEE,
                false, "not_super_admin"),

        new NavItem("donhang",        "Đơn hàng",         null,
                false, "not_super_admin"),

        new NavItem("chedomlamviec",  "Ca làm việc",      null,
                false, "not_super_admin"),

        new NavItem("baocao",         "Báo cáo",          null,
                false, "not_super_admin"),

        new NavItem("thongke",        "Thống kê",         Permission.VIEW_STATS,
                false, "not_super_admin"),

        new NavItem("nhahangs",       "Nhà hàng",         null,
                false, "super_admin_only"),

        new NavItem("bep",            "Bếp",              Permission.VIEW_KITCHEN,
                true,  "not_super_admin"),

        new NavItem("phucvu",         "Phục vụ",          Permission.VIEW_WAITER_SERVICE,
                true,  "not_super_admin"),

        new NavItem("thungan",        "Thu ngân",         Permission.VIEW_CASHIER,
                true,  "not_super_admin"),

        new NavItem("myrestaurant",   "Nhà hàng của tôi", null,
                false, "restaurant_admin_only"),

        new NavItem("baomat",         "Bảo mật",          null,
                false, "super_admin_only"),

        new NavItem("adminstats",     "Thống kê Admin",   null,
                false, "super_admin_only")
    );

    // ── Instance fields ───────────────────────────────────────────────────

    private final Consumer<String>   onNavigate;      // callback → MainController
    private final VBox               root;             // the sidebar VBox
    private final Map<String, Node>  itemNodes;        // pageKey → nav node
    private       String             activePage = "";

    // Badge references (so MainController can update counts)
    private BadgeLabel kitchenBadge;
    private BadgeLabel waiterBadge;
    private BadgeLabel cashierBadge;

    // ── Constructor ───────────────────────────────────────────────────────

    /**
     * @param onNavigate callback invoked with the page key when a nav item
     *                   is clicked (runs on FX thread)
     */
    public SidebarController(Consumer<String> onNavigate) {
        this.onNavigate = Objects.requireNonNull(onNavigate);
        this.root       = new VBox();
        this.itemNodes  = new LinkedHashMap<>();
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Returns the sidebar VBox to be set as {@code BorderPane#left}. */
    public VBox getRoot() { return root; }

    public BadgeLabel getKitchenBadge() { return kitchenBadge; }
    public BadgeLabel getWaiterBadge()  { return waiterBadge;  }
    public BadgeLabel getCashierBadge() { return cashierBadge; }

    /**
     * Builds the sidebar.  Must be called on the FX Application Thread
     * after {@link AppSession} has been populated via {@code login()}.
     */
    public void build() {
        root.getStyleClass().add("sidebar");
        root.setPrefWidth(220);
        root.setSpacing(2);
        root.setPadding(new Insets(20, 12, 16, 12));

        AppSession         session = AppSession.getInstance();
        RbacGuard          guard   = RbacGuard.getInstance();
        Set<Permission>    perms   = Permission.forRole(session.getUserRole());
        boolean            isSuper = guard.isSuperAdmin();
        boolean            isAdmin = guard.isRestaurantAdmin();

        // ── Nav items ──────────────────────────────────────────────────────
        for (NavItem ni : NAV_CATALOG) {
            if (!shouldShow(ni, perms, isSuper, isAdmin)) continue;

            Node itemNode;
            if (ni.badged()) {
                BadgeLabel bl = new BadgeLabel(ni.label());
                bl.getStyleClass().add("nav-item");
                bl.setCursor(Cursor.HAND);
                bl.setOnMouseClicked(e -> handleNavClick(ni.key()));
                setupHover(bl);
                itemNode = bl;

                // Capture badge references
                switch (ni.key()) {
                    case "bep"     -> kitchenBadge = bl;
                    case "phucvu"  -> waiterBadge  = bl;
                    case "thungan" -> cashierBadge = bl;
                }
            } else {
                itemNode = buildPlainNavItem(ni.label(), ni.key());
            }

            itemNodes.put(ni.key(), itemNode);
            root.getChildren().add(itemNode);
        }

        // ── Spacer ─────────────────────────────────────────────────────────
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        root.getChildren().add(spacer);

        // ── Separator ──────────────────────────────────────────────────────
        Separator sep = new Separator();
        sep.getStyleClass().add("sidebar-separator");
        root.getChildren().add(sep);
        root.getChildren().add(new Region() {{ setMinHeight(12); setMaxHeight(12); }});

        // ── User info panel ────────────────────────────────────────────────
        root.getChildren().add(buildUserInfoPanel(session));
    }

    /**
     * Updates the active state of all nav items.
     * Calls {@link BadgeLabel#setActive} for badged items and applies
     * {@code :active-nav} pseudo-class for plain items.
     *
     * @param pageKey the key of the currently visible page
     */
    public void setActivePage(String pageKey) {
        if (Objects.equals(activePage, pageKey)) return;
        activePage = pageKey;

        itemNodes.forEach((key, node) -> {
            // FIX 3: null-guard — itemNodes values should never be null,
            //         but guard defensively to avoid NPE if map was populated
            //         before the node was fully initialised
            if (node == null) return;

            boolean active = key.equals(pageKey);
            if (node instanceof BadgeLabel bl) {
                bl.setActive(active);
            } else {
                node.pseudoClassStateChanged(ACTIVE_NAV, active);
                // Also propagate to the inner label for text colour
                if (node instanceof HBox hb) {
                    hb.getChildren().stream()
                      .filter(c -> c instanceof Label)
                      .forEach(c -> c.pseudoClassStateChanged(ACTIVE_NAV, active));
                }
            }
        });
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Determines whether a nav item should be visible for the current session.
     */
    private boolean shouldShow(NavItem ni,
                                Set<Permission> perms,
                                boolean isSuper,
                                boolean isAdmin) {
        return switch (ni.visibleFor() == null ? "" : ni.visibleFor()) {
            case "super_admin_only"      -> isSuper;
            case "restaurant_admin_only" -> isAdmin;
            case "not_super_admin"       -> !isSuper
                                            && (ni.permission() == null
                                                || perms.contains(ni.permission()));
            // Home is visible to all non-super users AND super admins
            default                      -> ni.permission() == null
                                            || perms.contains(ni.permission());
        };
    }

    /**
     * Builds a plain (non-badged) nav item as an {@link HBox} with a
     * label inside.
     */
    private HBox buildPlainNavItem(String label, String key) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("nav-item-text");
        lbl.setMaxWidth(Double.MAX_VALUE);

        HBox box = new HBox(lbl);
        box.getStyleClass().add("nav-item");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 12, 0, 12));
        box.setPrefHeight(40);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setCursor(Cursor.HAND);
        box.setOnMouseClicked(e -> handleNavClick(key));
        setupHover(box);
        return box;
    }

    /** Adds hover pseudo-class for mouse enter/exit. */
    private void setupHover(Node node) {
        final PseudoClass HOVER_NAV = PseudoClass.getPseudoClass("hover-nav");
        node.setOnMouseEntered(e -> node.pseudoClassStateChanged(HOVER_NAV, true));
        node.setOnMouseExited (e -> node.pseudoClassStateChanged(HOVER_NAV, false));
    }

    private void handleNavClick(String key) {
        setActivePage(key);
        onNavigate.accept(key);
    }

    // ── User info panel ────────────────────────────────────────────────────

    private VBox buildUserInfoPanel(AppSession session) {
        VBox panel = new VBox(8);
        panel.getStyleClass().add("user-info-panel");

        // Avatar row
        HBox avatarRow = new HBox(10);
        avatarRow.setAlignment(Pos.CENTER_LEFT);

        // Avatar circle with initials
        StackPane avatar = buildAvatarCircle(initials(session.getUserName()), 34);

        VBox nameBlock = new VBox(2);
        Label lblName = new Label(session.getUserName());
        lblName.getStyleClass().add("user-name-label");

        // FIX 3 (continued): null-safe getRoleLabel — method may return null
        //         when the session role hasn't been set (e.g. early build call)
        String roleLabel = session.getRoleLabel();
        Label lblRole = new Label(roleLabel != null ? roleLabel : "");
        lblRole.getStyleClass().add("user-role-label");

        nameBlock.getChildren().addAll(lblName, lblRole);
        avatarRow.getChildren().addAll(avatar, nameBlock);

        // Logout button
        Button btnLogout = new Button("Đăng xuất");
        btnLogout.getStyleClass().add("logout-btn");
        btnLogout.setMaxWidth(Double.MAX_VALUE);
        btnLogout.setCursor(Cursor.HAND);
        btnLogout.setOnAction(e -> AppSession.getInstance().logout());

        panel.getChildren().addAll(avatarRow, btnLogout);
        return panel;
    }

    /** Builds the circular avatar with initials painted via CSS. */
    private StackPane buildAvatarCircle(String text, int size) {
        Label initLbl = new Label(text);
        initLbl.getStyleClass().add("avatar-initials");

        StackPane sp = new StackPane(initLbl);
        sp.getStyleClass().add("avatar-circle");
        sp.setPrefSize(size, size);
        sp.setMinSize(size, size);
        sp.setMaxSize(size, size);
        return sp;
    }

    /** Returns up-to-two-letter initials from a display name. */
    private static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1)
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return String.valueOf(Character.toUpperCase(parts[0].charAt(0)))
             + Character.toUpperCase(parts[parts.length - 1].charAt(0));
    }
}