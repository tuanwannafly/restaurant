package com.restaurant.data;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingWorker;

import com.restaurant.dao.EmployeeDAO;
import com.restaurant.dao.MenuItemDAO;
import com.restaurant.dao.OrderDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.model.Employee;
import com.restaurant.model.MenuItem;
import com.restaurant.model.Order;
import com.restaurant.model.TableItem;
import com.restaurant.session.AppSession;
import com.restaurant.session.RbacGuard;

/**
 * DataManager — lớp duy nhất mà UI biết đến.
 * Phiên bản JDBC: thay thế hoàn toàn ApiClient (REST API).
 * Giao diện public giữ nguyên → tất cả Panel không cần đổi.
 *
 * <p><b>Phase 2C — Session Guard:</b><br>
 * Mọi method write (add/update/delete) đều gọi {@link #checkSession()} trước
 * khi delegate xuống DAO. restaurantId KHÔNG được truyền từ UI — DataManager
 * tự lấy qua AppSession, DAO cũng tự gắn khi cần.<br>
 * SUPER_ADMIN được miễn guard (restaurantId == 0 vẫn OK).
 */
public class DataManager {

    private static DataManager instance;

    private final MenuItemDAO menuItemDAO  = new MenuItemDAO();
    private final TableDAO    tableDAO     = new TableDAO();
    private final OrderDAO    orderDAO     = new OrderDAO();
    private final EmployeeDAO employeeDAO  = new EmployeeDAO();

    private DataManager() {}

    public static DataManager getInstance() {
        if (instance == null) instance = new DataManager();
        return instance;
    }


    // ═══════════════════════════════════════════════════════
    //  SESSION GUARD
    // ═══════════════════════════════════════════════════════

    /**
     * Kiểm tra phiên làm việc hợp lệ trước khi thực hiện thao tác write.
     *
     * <p>Điều kiện bị chặn: {@code restaurantId == 0} VÀ KHÔNG phải SUPER_ADMIN.
     * Bao gồm cả trường hợp chưa đăng nhập (logout → restaurantId=0, role=null).
     *
     * <p>SUPER_ADMIN với {@code restaurantId == 0} được miễn — có thể thao tác
     * toàn hệ thống.
     *
     * @throws IllegalStateException nếu chưa đăng nhập hoặc thiếu tenant
     */
    private void checkSession() {
        AppSession session = AppSession.getInstance();
        if (session.getRestaurantId() == 0
                && !RbacGuard.getInstance().isSuperAdmin()) {
            System.err.println("[DataManager] checkSession thất bại:"
                    + " chưa đăng nhập hoặc restaurantId == 0");
            throw new IllegalStateException("Chưa đăng nhập");
        }
    }

    // ═══════════════════════════════════════════════════════
    //  MENU ITEMS
    // ═══════════════════════════════════════════════════════

    public List<MenuItem> getMenuItems() {
        try { return menuItemDAO.getAll(); }
        catch (Exception e) {
            System.err.println("[DataManager] getMenuItems lỗi: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public MenuItem addMenuItem(MenuItem item) {
        checkSession();
        try { return menuItemDAO.create(item); }
        catch (Exception e) {
            System.err.println("[DataManager] addMenuItem lỗi: " + e.getMessage());
            return item;
        }
    }

    public MenuItem updateMenuItem(MenuItem updated) {
        checkSession();
        try { return menuItemDAO.update(updated); }
        catch (Exception e) {
            System.err.println("[DataManager] updateMenuItem lỗi: " + e.getMessage());
            return updated;
        }
    }

    public void deleteMenuItem(String id) {
        checkSession();
        try { menuItemDAO.delete(id); }
        catch (Exception e) { System.err.println("[DataManager] deleteMenuItem lỗi: " + e.getMessage()); }
    }

    // ═══════════════════════════════════════════════════════
    //  TABLES
    // ═══════════════════════════════════════════════════════

    public List<TableItem> getTables() {
        try { return tableDAO.getAll(); }
        catch (Exception e) {
            System.err.println("[DataManager] getTables lỗi: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public TableItem addTable(TableItem table) {
        checkSession();
        try { return tableDAO.create(table); }
        catch (Exception e) {
            System.err.println("[DataManager] addTable lỗi: " + e.getMessage());
            return table;
        }
    }

    public TableItem updateTable(TableItem updated) {
        checkSession();
        try { return tableDAO.update(updated); }
        catch (Exception e) {
            System.err.println("[DataManager] updateTable lỗi: " + e.getMessage());
            return updated;
        }
    }

    public void deleteTable(String id) {
        checkSession();
        try { tableDAO.delete(id); }
        catch (Exception e) { System.err.println("[DataManager] deleteTable lỗi: " + e.getMessage()); }
    }

    // ═══════════════════════════════════════════════════════
    //  EMPLOYEES
    // ═══════════════════════════════════════════════════════

    public List<Employee> getEmployees() {
        try { return employeeDAO.findAll(); }
        catch (Exception e) {
            System.err.println("[DataManager] getEmployees lỗi: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public Employee addEmployee(Employee emp) {
        checkSession();
        try { return employeeDAO.add(emp); }
        catch (Exception e) {
            System.err.println("[DataManager] addEmployee lỗi: " + e.getMessage());
            return emp;
        }
    }

    public Employee updateEmployee(Employee updated) {
        checkSession();
        try { return employeeDAO.update(updated); }
        catch (Exception e) {
            System.err.println("[DataManager] updateEmployee lỗi: " + e.getMessage());
            return updated;
        }
    }

    public void deleteEmployee(String id) {
        checkSession();
        try { employeeDAO.delete(id); }
        catch (Exception e) { System.err.println("[DataManager] deleteEmployee lỗi: " + e.getMessage()); }
    }

    /** ID được sinh tự động bởi DB sequence, trả về placeholder */
    public String generateEmployeeId() { return "AUTO"; }

    // ═══════════════════════════════════════════════════════
    //  ORDERS
    // ═══════════════════════════════════════════════════════

    public List<Order> getOrders() {
        try { return orderDAO.getAll(); }
        catch (Exception e) {
            System.err.println("[DataManager] getOrders lỗi: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public Order addOrder(Order order) {
        checkSession();
        try { return orderDAO.create(order); }
        catch (Exception e) {
            System.err.println("[DataManager] addOrder lỗi: " + e.getMessage());
            return order;
        }
    }

    public Order updateOrder(Order updated) {
        checkSession();
        try { return orderDAO.update(updated); }
        catch (Exception e) {
            System.err.println("[DataManager] updateOrder lỗi: " + e.getMessage());
            return updated;
        }
    }

    public void deleteOrder(String id) {
        checkSession();
        try { orderDAO.delete(id); }
        catch (Exception e) { System.err.println("[DataManager] deleteOrder lỗi: " + e.getMessage()); }
    }

    // ═══════════════════════════════════════════════════════
    //  Async helper: chạy task trong background, sau đó gọi callback trên EDT
    // ═══════════════════════════════════════════════════════

    public static void runAsync(Runnable task, Runnable onDone) {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() { task.run(); return null; }
            @Override protected void done() { if (onDone != null) onDone.run(); }
        }.execute();
    }

    // ═══════════════════════════════════════════════════════
    //  DASHBOARD STATS
    // ═══════════════════════════════════════════════════════

    public double getTodayRevenue() {
        return getOrders().stream()
                .filter(o -> o.getStatus() == Order.Status.HOAN_THANH)
                .mapToDouble(Order::getTotalAmount).sum();
    }

    public long getTodayOrderCount() {
        return getOrders().stream()
                .filter(o -> o.getStatus() != Order.Status.DA_HUY).count();
    }

    public long getServingTableCount() {
        return getTables().stream()
                .filter(t -> t.getStatus() == TableItem.Status.BAN).count();
    }

    public long getTotalMenuItemsSold() {
        return getOrders().stream()
                .filter(o -> o.getStatus() == Order.Status.HOAN_THANH)
                .flatMap(o -> o.getItems().stream())
                .mapToLong(Order.OrderItem::getQuantity).sum();
    }
}