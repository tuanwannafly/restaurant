# SmartRestaurant



A Java desktop application for restaurant management, built with Java Swing (FlatLaf) as the UI layer and Oracle Database as the backend. The system supports multi-restaurant management, multi-level RBAC authorization, token-based authentication, and a full audit logging pipeline.

---

## Team Members

### Trương Quang Việt - 24522003
- Role: Project manager lead, System design co-lead, co-developer
- Contributions: managing project, designing system, develop product from plan 
- GitHub: [Truong Quang Viet]([https://github.com/username](https://github.com/vitdaden))

### Hồ Nguyễn Mai Thy - 24521759
- Role: UI designer lead, System desgin co-lead, co-developer
- Contributions: designing UI,  designing system, develop product from plan 
- GitHub: 

### Nguyễn Thành Vinh - 24522021
- Role: Database Design lead, System design co-lead, co-developer
- Contributions: Oracle schema design,  designing system, develop product from plan 
- GitHub: 

### Tô Đặng Minh Tuấn - 24521942
- Role: co-developer
- Contributions: develop product from plan 
- GitHub: tuanwannafly

## Table of Contents

- [System Requirements](#system-requirements)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Installation and Configuration](#installation-and-configuration)
- [Running the Application](#running-the-application)
- [Database](#database)
- [System Architecture](#system-architecture)
- [RBAC Permission System](#rbac-permission-system)
- [Security and Authentication](#security-and-authentication)
- [Module Descriptions](#module-descriptions)
- [Migrating to JavaFX](#migrating-to-javafx)
- [Running Tests](#running-tests)
- [Development Guidelines](#development-guidelines)

---

## System Requirements

| Component        | Minimum Version                         |
|------------------|-----------------------------------------|
| JDK              | 17                                      |
| Maven            | 3.8+                                    |
| Oracle Database  | 19c or 21c                              |
| Operating System | Windows 10+, macOS 12+, Ubuntu 20.04+  |

---

## Technology Stack

| Library / Tool               | Version | Purpose                                       |
|------------------------------|---------|-----------------------------------------------|
| Oracle JDBC (ojdbc11)        | 23.4    | Oracle Database connectivity                  |
| FlatLaf                      | 3.4     | Modern Look & Feel for Swing                  |
| MigLayout (miglayout-swing)  | 11.3    | Flexible layout manager for Swing             |
| jBCrypt                      | 0.4     | BCrypt password hashing                       |
| JUnit Jupiter                | 5.11.4  | Unit and integration testing                  |
| JavaFX Controls + FXML       | 21      | Included in pom.xml as a migration fallback   |
| maven-shade-plugin           | 3.5.2   | Fat JAR packaging                             |

---

## Project Structure

```
restaurant-main/
+-- assets/
|   +-- restaurant_logos/          # Restaurant logo images uploaded via UI
+-- src/
|   +-- main/
|       +-- java/com/restaurant/
|       |   +-- Main.java                  # Entry point: Look & Feel init, silent re-auth
|       |   +-- dao/                       # Data Access Object layer
|       |   |   +-- EmployeeDAO.java       # Employee CRUD
|       |   |   +-- KitchenDAO.java        # Kitchen order status and item updates
|       |   |   +-- MenuItemDAO.java       # Menu item CRUD
|       |   |   +-- OrderDAO.java          # Order and order item management
|       |   |   +-- ReportDAO.java         # Activity reports
|       |   |   +-- RestaurantDAO.java     # Restaurant management
|       |   |   +-- StatsDAO.java          # Revenue and performance statistics
|       |   |   +-- TableDAO.java          # Table status management
|       |   |   +-- UserDAO.java           # Login and account management
|       |   +-- data/
|       |   |   +-- DataManager.java       # Single facade between UI and the DAO layer
|       |   +-- db/
|       |   |   +-- DBConnection.java      # Oracle JDBC singleton with network timeout
|       |   +-- integration/
|       |   |   +-- FullOrderFlowIntegrationTest.java
|       |   +-- model/                     # POJO domain objects
|       |   |   +-- Employee.java
|       |   |   +-- MenuItem.java
|       |   |   +-- Order.java
|       |   |   +-- Report.java
|       |   |   +-- Restaurant.java
|       |   |   +-- TableItem.java
|       |   +-- session/                   # Authentication, authorization, audit
|       |   |   +-- AppSession.java        # Singleton storing current session state
|       |   |   +-- AuditLogger.java       # Writes audit entries to the database
|       |   |   +-- OperationTokenService.java # Short-lived token for sensitive ops (5 min)
|       |   |   +-- OperationType.java     # Enum of operation types requiring confirmation
|       |   |   +-- Permission.java        # Permission enum with role-based permission sets
|       |   |   +-- RbacGuard.java         # Permission check enforcement
|       |   |   +-- RbacTest.java          # Quick RBAC smoke test
|       |   |   +-- RefreshTokenService.java # Refresh token for silent re-auth
|       |   |   +-- SessionExpiredException.java
|       |   |   +-- TokenService.java      # Session token lifecycle (BCrypt + DB)
|       |   |   +-- TokenStorage.java      # Persists refresh token to disk
|       |   +-- ui/                        # Swing UI layer
|       |       +-- MainFrame.java         # Main window: sidebar + card layout
|       |       +-- LoginDialog.java       # Login screen
|       |       +-- HomePanel.java         # Overview dashboard
|       |       +-- AdminStatsPanel.java   # System-wide statistics (SUPER_ADMIN)
|       |       +-- StatsPanel.java        # Per-restaurant statistics (RESTAURANT_ADMIN)
|       |       +-- AuditLogPanel.java     # Audit log viewer
|       |       +-- CashierPanel.java      # Cashier interface
|       |       +-- EmployeePanel.java     # Employee management
|       |       +-- KitchenPanel.java      # Kitchen display and item status updates
|       |       +-- MenuPanel.java         # Menu management
|       |       +-- MyRestaurantInfoPanel.java # Current restaurant information
|       |       +-- OrderPanel.java        # Order management
|       |       +-- ReportPanel.java       # Report viewer and creation
|       |       +-- RestaurantPanel.java   # Restaurant management (SUPER_ADMIN)
|       |       +-- RestaurantDetailPanel.java
|       |       +-- TablePanel.java        # Table management
|       |       +-- TableOrderFrame.java   # Per-table ordering window
|       |       +-- WaiterServicePanel.java # Waiter service interface
|       |       +-- PollManager.java       # Centralized Swing Timer registry
|       |       +-- FlatLafConfig.java     # Look & Feel configuration
|       |       +-- UIConstants.java       # Color, font, and size constants
|       |       +-- AppButton.java         # Custom Button component
|       |       +-- AppTextField.java      # Custom TextField component
|       |       +-- AppComboBox.java       # Custom ComboBox component
|       |       +-- AppBadge.java          # Status badge component
|       |       +-- BadgeButton.java       # Button with a numeric badge
|       |       +-- StaffHeader.java       # Staff status bar header
|       |       +-- StyledTable.java       # Styled JTable wrapper
|       |       +-- EmptyStatePanel.java   # Empty state placeholder panel
|       |       +-- InlineErrorBar.java    # Inline error bar (replaces JOptionPane)
|       |       +-- ToastNotification.java # Non-blocking toast notifications
|       |       +-- ImageLoader.java       # Async image loader
|       |       +-- SimpleSpinner.java     # Loading spinner
|       |       +-- RoundedButton.java     # Button with rounded corners
|       |       +-- RoundedTextField.java  # TextField with rounded corners
|       |       +-- dialog/               # Feature dialogs
|       |           +-- AppDialog.java
|       |           +-- CashierPaymentDialog.java
|       |           +-- ConfirmOperationDialog.java
|       |           +-- EmployeeDetailDialog.java
|       |           +-- EmployeeDialog.java
|       |           +-- KitchenCookingDetailDialog.java
|       |           +-- KitchenPendingDetailDialog.java
|       |           +-- MenuDetailDialog.java
|       |           +-- MenuDialog.java
|       |           +-- MenuStatDialog.java
|       |           +-- MyProfileDialog.java
|       |           +-- OpenTableDialog.java
|       |           +-- OrderDetailDialog.java
|       |           +-- OrderStatDialog.java
|       |           +-- PaymentDialog.java
|       |           +-- RegisterStaffDialog.java
|       |           +-- ReportAddDialog.java
|       |           +-- ReportDetailDialog.java
|       |           +-- RestaurantDialog.java
|       |           +-- TableDialog.java
|       +-- resources/
|           +-- db.properties             # JDBC connection config (never commit to Git)
+-- datainit.sql                          # Full Oracle schema + seed data script
+-- pom.xml
```

---

## Installation and Configuration

### 1. Clone the repository

```bash
git clone <repository-url>
cd restaurant-main
```

### 2. Configure the database connection

Edit `src/main/resources/db.properties`:

```properties
db.url=jdbc:oracle:thin:@localhost:1521:ORCL
db.username=your_username
db.password=your_password
db.driver=oracle.jdbc.OracleDriver
```

Replace `localhost:1521:ORCL` with the host, port, and SID or service name that match your environment.

> **Security note:** `db.properties` contains sensitive credentials. Never commit this file to version control. Verify it is listed in `.gitignore`.

### 3. Initialize the database

Run the full `datainit.sql` script via SQL*Plus or SQL Developer:

```sql
@datainit.sql
```

The script will automatically:
- Drop all existing tables and sequences if they are present
- Recreate the complete schema
- Insert seed data including test accounts (default password: `123456`)

### 4. Build the project

```bash
mvn clean package -DskipTests
```

On success, the fat JAR is produced at:

```
target/smartrestaurant-1.0.0.jar
```

---

## Running the Application

```bash
java -jar target/smartrestaurant-1.0.0.jar
```

**Startup flow:**
1. `Main.java` initializes the Look & Feel (FlatLightLaf) and applies global fonts.
2. Expired tokens from previous sessions are cleaned up.
3. A saved refresh token on disk is checked for silent re-auth. If valid, `MainFrame` opens directly.
4. If silent login fails or no token is found, `LoginDialog` is shown for manual authentication.
5. On successful login, `MainFrame` opens.

---

## Database

### Core Schema

| Table                   | Description                                               |
|-------------------------|-----------------------------------------------------------|
| `ROLES`                 | System role definitions                                   |
| `RESTAURANTS`           | Restaurant records                                        |
| `USERS`                 | Login accounts (BCrypt-hashed passwords)                  |
| `EMPLOYEES`             | Employee profiles linked to a USER record                 |
| `MENUS`                 | Menus belonging to a restaurant                           |
| `MENU_ITEMS`            | Individual items within a menu                            |
| `RESTAURANT_TABLES`     | Tables and their status (RANH / BAN / DIRTY)              |
| `ORDERS`                | Order records                                             |
| `ORDER_ITEMS`           | Individual items per order with independent status        |
| `REPORTS`               | Activity reports                                          |
| `SESSION_TOKENS`        | Active session tokens                                     |
| `REFRESH_TOKENS`        | Refresh tokens for silent re-auth                         |
| `PASSWORD_RESET_TOKENS` | Time-limited password reset tokens                        |
| `OPERATION_TOKENS`      | Short-lived confirmation tokens for sensitive ops (5 min) |
| `SECURITY_AUDIT_LOG`    | Full audit trail of sensitive operations                  |

### Auto-increment Strategy

All tables use Oracle Sequences combined with `BEFORE INSERT` triggers to generate primary keys automatically, preventing race conditions in concurrent scenarios.

---

## System Architecture

```
+-----------+     +--------------+     +--------+     +----------------+
|  UI Layer |---->|  DataManager |---->|  DAO   |---->|  Oracle DB     |
| (Swing)   |     |  (Facade)    |     | Layer  |     |  via JDBC      |
+-----------+     +--------------+     +--------+     +----------------+
      |                  |
      |           +------+----------+
      |           |   AppSession    |
      |           |   RbacGuard     |
      |           |   AuditLogger   |
      |           +-----------------+
```

**Architectural principles:**

- The UI layer communicates exclusively through `DataManager`. DAOs are never called directly from panels.
- `DataManager` validates the session and enforces RBAC before every write operation (add / update / delete).
- `DBConnection` is a singleton. Each `getConnection()` call returns a new `Connection` with a 10-second network timeout set via `setNetworkTimeout()`. Every connection must be closed using `try-with-resources`.
- `PollManager` owns all `javax.swing.Timer` instances. When `stopAll()` is called on logout, all timers stop immediately, preventing further DB calls and memory leaks after the session ends.

---

## RBAC Permission System

Roles are defined in the `ROLES` table and stored in `AppSession` after login. All permission checks are enforced by `RbacGuard.check(permission)` or `AppSession.hasPermission(permission)`.

| Role               | Primary Permissions                                                              |
|--------------------|----------------------------------------------------------------------------------|
| `SUPER_ADMIN`      | Full access: restaurant management, role assignment, audit log, system-wide stats|
| `RESTAURANT_ADMIN` | Manage own restaurant: staff, menu, tables, orders, reports, statistics          |
| `WAITER`           | Open tables, create orders, update item status, view waiter service              |
| `CHEF`             | View and update status for items being processed in the kitchen                  |
| `CASHIER`          | View cashier panel, process payments                                             |

**Additional constraints:**
- `RESTAURANT_ADMIN` can only manage their own restaurant and cannot assign the `SUPER_ADMIN` role.
- `CASHIER` sees only the cashier view with no access to full order management (least-privilege principle).

All permissions are defined as constants in the `Permission` enum. The `forRole(String)` method returns an immutable `Set<Permission>` for a given role name.

---

## Security and Authentication

### BCrypt

User passwords are hashed with BCrypt (jBCrypt 0.4) before being stored. All hashes use the `$2a$` prefix.

### Session Token

After a successful login, `UserDAO.login()` generates a session token via `TokenService` and persists it to the `SESSION_TOKENS` table. The token is revoked when `AppSession.logout()` is called.

### Refresh Token

`RefreshTokenService` manages refresh tokens persisted to disk by `TokenStorage`. These enable silent re-auth when the application restarts. Each token is rotated on every use, preventing re-use attacks.

### Operation Token

Irreversible sensitive operations (delete employee, change role, etc.) require the user to enter an 8-character Operation Token with a 5-minute TTL. Tokens are issued by `OperationTokenService` and confirmed through `ConfirmOperationDialog`.

### Audit Log

`AuditLogger` writes every sensitive action to `SECURITY_AUDIT_LOG`, recording the action type, actor, target entity, outcome (SUCCESS / FAIL), and timestamp. The full log is accessible through `AuditLogPanel` (SUPER_ADMIN only).

---

## Module Descriptions

### DataManager (`com.restaurant.data`)

The single facade the UI interacts with. Validates the session and enforces RBAC before every write operation. The `restaurantId` is sourced from `AppSession` internally and is never passed in from the UI, preventing client-side tampering.

### DBConnection (`com.restaurant.db`)

Singleton managing Oracle JDBC connectivity. Every `getConnection()` call returns a fresh `Connection` with a 10-second network timeout. The `isConnectionValid()` method can be used to check a connection before reuse.

### PollManager (`com.restaurant.ui`)

Centralized registry of all `javax.swing.Timer` instances. Panels that require polling register their tasks here using a fixed string key (e.g., `"kitchen"`, `"waiter"`, `"tableorder_42"`). Calling `stopAll()` on logout stops every timer at once.

### AppSession (`com.restaurant.session`)

Singleton storing the active session state: `userId`, `userName`, `userEmail`, `userRole`, `restaurantId`, and `sessionToken`. Supports `SessionListener` callbacks via `WeakReference` so panels receive logout events without causing memory leaks after they are disposed.

---

## Migrating to JavaFX

The `pom.xml` already declares JavaFX dependencies (`javafx-controls`, `javafx-fxml` version 21) and the `javafx-maven-plugin` with the entry point configured as `com.restaurant.ui.AppLauncher`.

**Step-by-step migration guide:**

**Step 1 - Create AppLauncher**

Create `com.restaurant.ui.AppLauncher extends Application` as the JavaFX entry point, replacing the startup logic in `Main.java`:

```java
package com.restaurant.ui;

import javafx.application.Application;
import javafx.stage.Stage;

public class AppLauncher extends Application {
    @Override
    public void start(Stage primaryStage) {
        // Initialization logic equivalent to Main.java
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

**Step 2 - Update mainClass in pom.xml**

Change the `<mainClass>` value in `maven-shade-plugin` from `com.restaurant.Main` to `com.restaurant.ui.AppLauncher`.

**Step 3 - Keep DAO, model, and session packages unchanged**

The `dao`, `model`, `db`, `data`, and `session` packages have no dependency on Swing or JavaFX. No changes are required in these layers.

**Step 4 - Rewrite the UI layer in JavaFX**

Convert each Swing Panel and Dialog to an FXML file with a Controller class:
- `MainFrame.java` -> `BorderPane` with a `VBox` sidebar
- Each `*Panel.java` -> an FXML file and a corresponding Controller
- `PollManager` -> replace `javax.swing.Timer` with `javafx.animation.Timeline`
- `DataManager` keeps the same public interface; replace EDT dispatching with `Platform.runLater()`

**Step 5 - Verify JavaFX module configuration**

If using JDK 17 with a separately distributed JavaFX runtime, confirm the plugin configuration in `pom.xml`:

```xml
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.8</version>
    <configuration>
        <mainClass>com.restaurant.ui.AppLauncher</mainClass>
    </configuration>
</plugin>
```

Run with:

```bash
mvn javafx:run
```

> **Note:** `UIConstants.java` and `FlatLafConfig.java` are Swing-specific. When migrating to JavaFX, replace them with a JavaFX CSS file using CSS custom properties for color tokens.

---

## Running Tests

```bash
# Run all unit tests
mvn test

# Run a specific test class
mvn test -Dtest=RbacGuardTest

# Run the integration test (requires a live DB connection)
mvn test -Dtest=FullOrderFlowIntegrationTest
```

> **Note:** `FullOrderFlowIntegrationTest` requires a valid `db.properties` and a running Oracle instance. Do not run this test in CI environments without an Oracle database available.

---

## Development Guidelines

- All write operations must go through `DataManager`. Never call a DAO directly from a UI panel.
- Every JDBC `Connection` must be closed inside a `try-with-resources` block.
- Heavy or blocking operations (DB calls, data loading) must run on a `SwingWorker` or background thread. UI updates from background threads must be dispatched via `SwingUtilities.invokeLater()`.
- Register all polling tasks with `PollManager`. Do not create standalone `Timer` instances inside individual panels.
- Every sensitive state change (delete, role change, password reset) must be recorded via `AuditLogger.getInstance().log(...)`.
- Never commit `db.properties` to version control. Use environment variables or an external config file for server deployments.
