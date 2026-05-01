-- ============================================================
--  SMART RESTAURANT MANAGEMENT SYSTEM — Oracle SQL Setup
--  Tương thích: Oracle 19c / 21c  (dùng với user TEST / orclpdb)
--  Tác giả: Generated for Tô Đăng Minh Tuấn (Tuna)
-- ============================================================
--
--  TÀI KHOẢN TEST (ghi nhớ):
--  ─────────────────────────────────────────────────────────
--  SUPER_ADMIN
--    Email   : superadmin@restaurant.com
--    Password: SuperAdmin@123
--
--  RESTAURANT_ADMIN — Nhà hàng "Phở Hà Nội" (restaurant_id=1)
--    Email   : admin1@phohanoi.com
--    Password: Admin@123
--
--  RESTAURANT_ADMIN — Nhà hàng "Bún Bò Huế" (restaurant_id=2)
--    Email   : admin2@bungbo.com
--    Password: Admin@123
--
--  NHÂN VIÊN — Phở Hà Nội
--    waiter1@phohanoi.com  | Staff@123  (WAITER)
--    waiter2@phohanoi.com  | Staff@123  (WAITER)
--    chef1@phohanoi.com    | Staff@123  (CHEF)
--    chef2@phohanoi.com    | Staff@123  (CHEF)
--    cashier1@phohanoi.com | Staff@123  (CASHIER)
--
--  NHÂN VIÊN — Bún Bò Huế
--    cashier1@bungbo.com   | Staff@123  (CASHIER)
--    waiter1@bungbo.com    | Staff@123  (WAITER)
--  ─────────────────────────────────────────────────────────
--
--  CÁCH CHẠY:
--    sqlplus test/123@//localhost:1521/orclpdb @smart_restaurant_oracle.sql
--  Hoặc mở SQL Developer, kết nối user TEST, chạy toàn bộ file.
-- ============================================================

-- Xoá session hiện tại nếu cần
SET DEFINE OFF;
SET FEEDBACK OFF;
SET ECHO OFF;

-- ============================================================
-- BƯỚC 1: DROP tất cả objects cũ (an toàn, ignore error)
-- ============================================================

BEGIN
  FOR t IN (
    SELECT table_name FROM user_tables
    WHERE table_name IN (
      'OPERATION_TOKENS','SECURITY_AUDIT_LOG','PASSWORD_RESET_TOKENS',
      'REFRESH_TOKENS','SESSION_TOKENS','REPORTS',
      'ORDER_ITEMS','ORDERS','MENU_ITEMS','MENUS',
      'RESTAURANT_TABLES','EMPLOYEES','USERS','ROLES','RESTAURANTS'
    )
  ) LOOP
    EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
  END LOOP;
END;
/

BEGIN
  FOR s IN (
    SELECT sequence_name FROM user_sequences
    WHERE sequence_name IN (
      'SEQ_RESTAURANT','SEQ_ROLE','SEQ_USER','SEQ_EMPLOYEE',
      'SEQ_REST_TABLE','SEQ_MENU','SEQ_MENU_ITEM',
      'SEQ_ORDER','SEQ_ORDER_ITEM','SEQ_REPORT',
      'SEQ_SESSION_TOKEN','SEQ_REFRESH_TOKEN',
      'SEQ_PW_RESET','SEQ_AUDIT_LOG','SEQ_OP_TOKEN'
    )
  ) LOOP
    EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
  END LOOP;
END;
/

-- ============================================================
-- BƯỚC 2: SEQUENCES
-- ============================================================

CREATE SEQUENCE SEQ_RESTAURANT  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_ROLE        START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_USER        START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_EMPLOYEE    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_REST_TABLE  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_MENU        START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_MENU_ITEM   START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_ORDER       START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_ORDER_ITEM  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_REPORT      START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SESSION     START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_REFRESH     START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_PW_RESET    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_AUDIT_LOG   START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_OP_TOKEN    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ============================================================
-- BƯỚC 3: BẢNG CƠ SỞ
-- ============================================================

-- 3.1 RESTAURANTS
CREATE TABLE restaurants (
    restaurant_id  NUMBER        PRIMARY KEY,
    name           VARCHAR2(200) NOT NULL,
    address        VARCHAR2(500),
    phone          VARCHAR2(20),
    email          VARCHAR2(150),
    status         VARCHAR2(20)  DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_restaurant_id
BEFORE INSERT ON restaurants FOR EACH ROW
BEGIN
    IF :NEW.restaurant_id IS NULL THEN
        :NEW.restaurant_id := SEQ_RESTAURANT.NEXTVAL;
    END IF;
END;
/

-- 3.2 ROLES
CREATE TABLE roles (
    id    NUMBER        PRIMARY KEY,
    name  VARCHAR2(50)  NOT NULL UNIQUE
);

CREATE OR REPLACE TRIGGER trg_role_id
BEFORE INSERT ON roles FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        :NEW.id := SEQ_ROLE.NEXTVAL;
    END IF;
END;
/

-- 3.3 USERS
CREATE TABLE users (
    user_id        NUMBER        PRIMARY KEY,
    name           VARCHAR2(200) NOT NULL,
    email          VARCHAR2(150) NOT NULL UNIQUE,
    password       VARCHAR2(255) NOT NULL,
    role_id        NUMBER        REFERENCES roles(id),
    restaurant_id  NUMBER        REFERENCES restaurants(restaurant_id),
    status         VARCHAR2(20)  DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE','INACTIVE','LOCKED')),
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_user_id
BEFORE INSERT ON users FOR EACH ROW
BEGIN
    IF :NEW.user_id IS NULL THEN
        :NEW.user_id := SEQ_USER.NEXTVAL;
    END IF;
END;
/

-- 3.4 EMPLOYEES
CREATE TABLE employees (
    employee_id    NUMBER        PRIMARY KEY,
    name           VARCHAR2(200) NOT NULL,
    cccd           VARCHAR2(20),
    phone          VARCHAR2(20),
    address        VARCHAR2(500),
    start_date     DATE,
    role           VARCHAR2(30)  DEFAULT 'PHUC_VU'
                   CHECK (role IN ('PHUC_VU','DAU_BEP','THU_NGAN','QUAN_LY')),
    restaurant_id  NUMBER        NOT NULL REFERENCES restaurants(restaurant_id),
    user_id        NUMBER        REFERENCES users(user_id),
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_employee_id
BEFORE INSERT ON employees FOR EACH ROW
BEGIN
    IF :NEW.employee_id IS NULL THEN
        :NEW.employee_id := SEQ_EMPLOYEE.NEXTVAL;
    END IF;
END;
/

-- 3.5 RESTAURANT_TABLES
CREATE TABLE restaurant_tables (
    table_id       NUMBER        PRIMARY KEY,
    table_number   VARCHAR2(50)  NOT NULL,
    capacity       NUMBER(3)     DEFAULT 4,
    status         VARCHAR2(30)  DEFAULT 'AVAILABLE'
                   CHECK (status IN ('AVAILABLE','OCCUPIED','RESERVED','DIRTY','CLEANING','OUT_OF_SERVICE')),
    restaurant_id  NUMBER        NOT NULL REFERENCES restaurants(restaurant_id),
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_rest_table_id
BEFORE INSERT ON restaurant_tables FOR EACH ROW
BEGIN
    IF :NEW.table_id IS NULL THEN
        :NEW.table_id := SEQ_REST_TABLE.NEXTVAL;
    END IF;
END;
/

-- 3.6 MENUS (danh mục)
CREATE TABLE menus (
    menu_id        NUMBER        PRIMARY KEY,
    name           VARCHAR2(200) NOT NULL,
    description    VARCHAR2(500),
    status         VARCHAR2(20)  DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE','INACTIVE')),
    restaurant_id  NUMBER        NOT NULL REFERENCES restaurants(restaurant_id),
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_menu_id
BEFORE INSERT ON menus FOR EACH ROW
BEGIN
    IF :NEW.menu_id IS NULL THEN
        :NEW.menu_id := SEQ_MENU.NEXTVAL;
    END IF;
END;
/

-- 3.7 MENU_ITEMS
CREATE TABLE menu_items (
    item_id        NUMBER          PRIMARY KEY,
    name           VARCHAR2(200)   NOT NULL,
    description    VARCHAR2(1000),
    price          NUMBER(12,0)    NOT NULL CHECK (price >= 0),
    image_url      VARCHAR2(500),
    status         VARCHAR2(20)    DEFAULT 'AVAILABLE'
                   CHECK (status IN ('AVAILABLE','UNAVAILABLE','DELETED')),
    menu_id        NUMBER          NOT NULL REFERENCES menus(menu_id),
    restaurant_id  NUMBER          NOT NULL REFERENCES restaurants(restaurant_id),
    created_at     TIMESTAMP       DEFAULT SYSTIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_menu_item_id
BEFORE INSERT ON menu_items FOR EACH ROW
BEGIN
    IF :NEW.item_id IS NULL THEN
        :NEW.item_id := SEQ_MENU_ITEM.NEXTVAL;
    END IF;
END;
/

-- 3.8 ORDERS
CREATE TABLE orders (
    order_id        NUMBER          PRIMARY KEY,
    status          VARCHAR2(30)    DEFAULT 'PENDING'
                    CHECK (status IN (
                        'PENDING','ACCEPTED','COOKING','READY',
                        'DELIVERING','DELIVERED','COMPLETED','CANCELLED'
                    )),
    total_amount    NUMBER(15,0)    DEFAULT 0,
    table_id        NUMBER          REFERENCES restaurant_tables(table_id),
    restaurant_id   NUMBER          NOT NULL REFERENCES restaurants(restaurant_id),
    customer_name   VARCHAR2(200),
    customer_phone  VARCHAR2(20),
    created_at      TIMESTAMP       DEFAULT SYSTIMESTAMP,
    completed_at    TIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_order_id
BEFORE INSERT ON orders FOR EACH ROW
BEGIN
    IF :NEW.order_id IS NULL THEN
        :NEW.order_id := SEQ_ORDER.NEXTVAL;
    END IF;
END;
/

-- 3.9 ORDER_ITEMS
CREATE TABLE order_items (
    order_item_id  NUMBER        PRIMARY KEY,
    order_id       NUMBER        NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    menu_item_id   NUMBER        NOT NULL REFERENCES menu_items(item_id),
    quantity       NUMBER(5)     DEFAULT 1 CHECK (quantity > 0),
    price          NUMBER(12,0)  NOT NULL,
    item_status    VARCHAR2(20)  DEFAULT 'PENDING'
                   CHECK (item_status IN (
                       'PENDING','ACCEPTED','COOKING','READY','DELIVERING','DELIVERED'
                   )),
    round_number   NUMBER(5)     DEFAULT 1,
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_order_item_id
BEFORE INSERT ON order_items FOR EACH ROW
BEGIN
    IF :NEW.order_item_id IS NULL THEN
        :NEW.order_item_id := SEQ_ORDER_ITEM.NEXTVAL;
    END IF;
END;
/

-- 3.10 REPORTS
CREATE TABLE reports (
    report_id      NUMBER        PRIMARY KEY,
    title          VARCHAR2(500) NOT NULL,
    description    CLOB,
    report_type    VARCHAR2(20)  DEFAULT 'INCIDENT'
                   CHECK (report_type IN ('INCIDENT','MAINTENANCE','FEEDBACK')),
    severity       VARCHAR2(20)  DEFAULT 'LOW'
                   CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status         VARCHAR2(20)  DEFAULT 'OPEN'
                   CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
    created_by     NUMBER        REFERENCES users(user_id),
    restaurant_id  NUMBER        REFERENCES restaurants(restaurant_id),
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP,
    resolved_at    TIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_report_id
BEFORE INSERT ON reports FOR EACH ROW
BEGIN
    IF :NEW.report_id IS NULL THEN
        :NEW.report_id := SEQ_REPORT.NEXTVAL;
    END IF;
END;
/

-- ============================================================
-- BƯỚC 4: BẢNG SECURITY / SESSION
-- ============================================================

-- 4.1 SESSION_TOKENS
CREATE TABLE session_tokens (
    id          NUMBER         PRIMARY KEY,
    token_id    VARCHAR2(36)   NOT NULL UNIQUE,   -- UUID
    user_id     NUMBER         NOT NULL REFERENCES users(user_id),
    created_at  TIMESTAMP      DEFAULT SYSTIMESTAMP,
    expires_at  TIMESTAMP      NOT NULL,
    is_active   NUMBER(1)      DEFAULT 1 CHECK (is_active IN (0,1)),
    ip_address  VARCHAR2(50)
);

CREATE OR REPLACE TRIGGER trg_session_id
BEFORE INSERT ON session_tokens FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        :NEW.id := SEQ_SESSION.NEXTVAL;
    END IF;
END;
/

-- 4.2 REFRESH_TOKENS
CREATE TABLE refresh_tokens (
    id           NUMBER         PRIMARY KEY,
    token        VARCHAR2(128)  NOT NULL UNIQUE,
    user_id      NUMBER         NOT NULL REFERENCES users(user_id),
    device_name  VARCHAR2(200),
    created_at   TIMESTAMP      DEFAULT SYSTIMESTAMP,
    expires_at   TIMESTAMP      NOT NULL,
    revoked      NUMBER(1)      DEFAULT 0 CHECK (revoked IN (0,1))
);

CREATE OR REPLACE TRIGGER trg_refresh_id
BEFORE INSERT ON refresh_tokens FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        :NEW.id := SEQ_REFRESH.NEXTVAL;
    END IF;
END;
/

-- 4.3 PASSWORD_RESET_TOKENS
CREATE TABLE password_reset_tokens (
    id          NUMBER        PRIMARY KEY,
    token       VARCHAR2(64)  NOT NULL UNIQUE,
    user_id     NUMBER        NOT NULL REFERENCES users(user_id),
    expires_at  TIMESTAMP     NOT NULL,
    used        NUMBER(1)     DEFAULT 0 CHECK (used IN (0,1)),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_pw_reset_id
BEFORE INSERT ON password_reset_tokens FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        :NEW.id := SEQ_PW_RESET.NEXTVAL;
    END IF;
END;
/

-- 4.4 SECURITY_AUDIT_LOG
CREATE TABLE security_audit_log (
    log_id         NUMBER         PRIMARY KEY,
    action         VARCHAR2(50)   NOT NULL,
    actor_user_id  NUMBER         REFERENCES users(user_id),
    target_id      NUMBER,
    session_token  VARCHAR2(36),
    op_token       VARCHAR2(16),
    result         VARCHAR2(10)   CHECK (result IN ('SUCCESS','FAIL','LOCKED')),
    detail         VARCHAR2(500),
    logged_at      TIMESTAMP      DEFAULT SYSTIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_audit_id
BEFORE INSERT ON security_audit_log FOR EACH ROW
BEGIN
    IF :NEW.log_id IS NULL THEN
        :NEW.log_id := SEQ_AUDIT_LOG.NEXTVAL;
    END IF;
END;
/

-- 4.5 OPERATION_TOKENS
CREATE TABLE operation_tokens (
    id              NUMBER        PRIMARY KEY,
    token           VARCHAR2(8)   NOT NULL,
    operation_type  VARCHAR2(50)  NOT NULL,
    actor_user_id   NUMBER        REFERENCES users(user_id),
    target_id       NUMBER,
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP,
    expires_at      TIMESTAMP     NOT NULL,
    used            NUMBER(1)     DEFAULT 0 CHECK (used IN (0,1))
);

CREATE OR REPLACE TRIGGER trg_op_token_id
BEFORE INSERT ON operation_tokens FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        :NEW.id := SEQ_OP_TOKEN.NEXTVAL;
    END IF;
END;
/

-- ============================================================
-- BƯỚC 5: INDEXES (tối ưu query thường dùng)
-- ============================================================

CREATE INDEX idx_users_email          ON users(LOWER(email));
CREATE INDEX idx_users_restaurant     ON users(restaurant_id);
CREATE INDEX idx_employees_restaurant ON employees(restaurant_id);
CREATE INDEX idx_employees_user       ON employees(user_id);
CREATE INDEX idx_tables_restaurant    ON restaurant_tables(restaurant_id);
CREATE INDEX idx_menu_items_rest      ON menu_items(restaurant_id);
CREATE INDEX idx_menu_items_menu      ON menu_items(menu_id);
CREATE INDEX idx_orders_restaurant    ON orders(restaurant_id);
CREATE INDEX idx_orders_table         ON orders(table_id);
CREATE INDEX idx_orders_status        ON orders(status);
CREATE INDEX idx_order_items_order    ON order_items(order_id);
CREATE INDEX idx_order_items_status   ON order_items(item_status);
CREATE INDEX idx_reports_restaurant   ON reports(restaurant_id);
CREATE INDEX idx_session_token        ON session_tokens(token_id);
CREATE INDEX idx_refresh_token        ON refresh_tokens(token);
CREATE INDEX idx_audit_logged_at      ON security_audit_log(logged_at);
CREATE INDEX idx_audit_action         ON security_audit_log(action);

-- ============================================================
-- BƯỚC 6: DỮ LIỆU MẪU (SEED DATA)
-- ============================================================

-- 6.1 ROLES
INSERT INTO roles (id, name) VALUES (1, 'SUPER_ADMIN');
INSERT INTO roles (id, name) VALUES (2, 'RESTAURANT_ADMIN');
INSERT INTO roles (id, name) VALUES (3, 'WAITER');
INSERT INTO roles (id, name) VALUES (4, 'CHEF');
INSERT INTO roles (id, name) VALUES (5, 'CASHIER');

-- 6.2 RESTAURANTS
INSERT INTO restaurants (name, address, phone, email, status)
VALUES ('Phở Hà Nội', '12 Đinh Tiên Hoàng, Hoàn Kiếm, Hà Nội',
        '024-3826-1234', 'contact@phohanoi.com', 'ACTIVE');

INSERT INTO restaurants (name, address, phone, email, status)
VALUES ('Bún Bò Huế Ngon', '45 Lê Lợi, Phường Phú Hội, TP Huế',
        '0234-3820-888', 'contact@bungbo.com', 'ACTIVE');

INSERT INTO restaurants (name, address, phone, email, status)
VALUES ('Cơm Tấm Sài Gòn', '88 Nguyễn Trãi, Quận 1, TP HCM',
        '028-3920-5678', 'info@comtam.com', 'INACTIVE');

-- 6.3 USERS
-- ─── SUPER_ADMIN ───────────────────────────────────────────
-- Email: superadmin@restaurant.com  |  Password: SuperAdmin@123
INSERT INTO users (name, email, password, role_id, restaurant_id, status)
VALUES ('Super Admin', 'superadmin@restaurant.com',
        '$2b$10$gcolIJeaotOX3e3bHPc96ekpKjHYW3j7KeKYjKbf4MCZZ8ojBFaPW',
        1, NULL, 'ACTIVE');

-- ─── RESTAURANT_ADMIN — Phở Hà Nội (restaurant_id=1) ───────
-- Email: admin1@phohanoi.com  |  Password: Admin@123
INSERT INTO users (name, email, password, role_id, restaurant_id, status)
VALUES ('Nguyễn Văn Quản', 'admin1@phohanoi.com',
        '$2b$10$0wBTXUMKGx.jw0tgGp1y5OXz1.Cudh/M4Lh1UQ8z.ExVzstQHGdXq',
        2, 1, 'ACTIVE');

-- ─── RESTAURANT_ADMIN — Bún Bò Huế (restaurant_id=2) ────────
-- Email: admin2@bungbo.com  |  Password: Admin@123
INSERT INTO users (name, email, password, role_id, restaurant_id, status)
VALUES ('Trần Thị Lý', 'admin2@bungbo.com',
        '$2b$10$WvrCOAqbbFI/bEcg7VEHo.IsgxjcBJL5F4mEV9KG5xqka7RKgAuFe',
        2, 2, 'ACTIVE');

-- ─── WAITER 1 — Phở Hà Nội ─────────────────────────────────
-- Email: waiter1@phohanoi.com  |  Password: Staff@123
INSERT INTO users (name, email, password, role_id, restaurant_id, status)
VALUES ('Lê Văn Phục', 'waiter1@phohanoi.com',
        '$2b$10$E0f.YQ/qV/vDaRAlrt3wwe1kiks5tFtXNT.CDEX8seBoilfOBT5JK',
        3, 1, 'ACTIVE');

-- ─── WAITER 2 — Phở Hà Nội ─────────────────────────────────
-- Email: waiter2@phohanoi.com  |  Password: Staff@123
INSERT INTO users (name, email, password, role_id, restaurant_id, status)
VALUES ('Phạm Thị Hoa', 'waiter2@phohanoi.com',
        '$2b$10$WFM1Q4ovsoQz2RG2uyJG4ewK7dAfSshDb2HoHtl.NjXpUa.fjPLhW',
        3, 1, 'ACTIVE');

-- ─── CHEF 1 — Phở Hà Nội ───────────────────────────────────
-- Email: chef1@phohanoi.com  |  Password: Staff@123
INSERT INTO users (name, email, password, role_id, restaurant_id, status)
VALUES ('Vũ Đình Bếp', 'chef1@phohanoi.com',
        '$2b$10$4J.2gegTjcN/yUtuqhGIzOJQUojWDKr17NRU2DM3zLCVWvfS4PdNa',
        4, 1, 'ACTIVE');

-- ─── CHEF 2 — Phở Hà Nội ───────────────────────────────────
-- Email: chef2@phohanoi.com  |  Password: Staff@123
INSERT INTO users (name, email, password, role_id, restaurant_id, status)
VALUES ('Hoàng Văn Nấu', 'chef2@phohanoi.com',
        '$2b$10$ku8UAWP4M4XiFwoKXgEJved1z7OIiZ1Bu3qnlgKYEtte0LDzHurLW',
        4, 1, 'ACTIVE');

-- ─── CASHIER 1 — Phở Hà Nội ────────────────────────────────
-- Email: cashier1@phohanoi.com  |  Password: Staff@123
INSERT INTO users (name, email, password, role_id, restaurant_id, status)
VALUES ('Đỗ Thị Thu', 'cashier1@phohanoi.com',
        '$2b$10$RxnLdk6P3mGRFjsqxvWXSukKyfJRU1d1AIQ2zP6V3s9iPv1sG9M5K',
        5, 1, 'ACTIVE');

-- ─── CASHIER 1 — Bún Bò Huế ────────────────────────────────
-- Email: cashier1@bungbo.com  |  Password: Staff@123
INSERT INTO users (name, email, password, role_id, restaurant_id, status)
VALUES ('Ngô Thị Tiền', 'cashier1@bungbo.com',
        '$2b$10$xwTZbk0Pqwz01UZkxbE2k.ELGxKChkENUXcCpvLNZgtDFkoblN0/6',
        5, 2, 'ACTIVE');

-- ─── WAITER 1 — Bún Bò Huế ─────────────────────────────────
-- Email: waiter1@bungbo.com  |  Password: Staff@123
INSERT INTO users (name, email, password, role_id, restaurant_id, status)
VALUES ('Lý Văn Bàn', 'waiter1@bungbo.com',
        '$2b$10$tcuwyNRvgW1MK52D5h7U2Ob.1sX31c.vuRUXtutvJx.SOBHlDNEq.',
        3, 2, 'ACTIVE');

-- 6.4 EMPLOYEES (gán user_id để liên kết tài khoản)
-- Nhà hàng 1 — Phở Hà Nội
INSERT INTO employees (name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES ('Nguyễn Văn Quản', '001234567890', '0912000001',
        'Số 5 Hàng Bài, Hà Nội', DATE '2022-01-10', 'QUAN_LY', 1, 2);

INSERT INTO employees (name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES ('Lê Văn Phục', '001234567891', '0912000002',
        'Số 7 Đinh Lễ, Hà Nội', DATE '2022-03-15', 'PHUC_VU', 1, 4);

INSERT INTO employees (name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES ('Phạm Thị Hoa', '001234567892', '0912000003',
        'Số 9 Hàng Bạc, Hà Nội', DATE '2023-06-01', 'PHUC_VU', 1, 5);

INSERT INTO employees (name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES ('Vũ Đình Bếp', '001234567893', '0912000004',
        'Số 11 Hàng Buồm, Hà Nội', DATE '2021-09-20', 'DAU_BEP', 1, 6);

INSERT INTO employees (name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES ('Hoàng Văn Nấu', '001234567894', '0912000005',
        'Số 13 Mã Mây, Hà Nội', DATE '2022-11-01', 'DAU_BEP', 1, 7);

INSERT INTO employees (name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES ('Đỗ Thị Thu', '001234567895', '0912000006',
        'Số 15 Hàng Ngang, Hà Nội', DATE '2022-07-15', 'THU_NGAN', 1, 8);

-- Nhà hàng 2 — Bún Bò Huế
INSERT INTO employees (name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES ('Trần Thị Lý', '001234567896', '0912000007',
        'Số 20 Trần Hưng Đạo, Huế', DATE '2021-05-10', 'QUAN_LY', 2, 3);

INSERT INTO employees (name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES ('Ngô Thị Tiền', '001234567897', '0912000008',
        'Số 22 Hùng Vương, Huế', DATE '2022-08-20', 'THU_NGAN', 2, 9);

INSERT INTO employees (name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES ('Lý Văn Bàn', '001234567898', '0912000009',
        'Số 24 Lê Duẩn, Huế', DATE '2023-01-10', 'PHUC_VU', 2, 10);

-- Nhân viên không có tài khoản (để test tính năng "Chưa có tài khoản")
INSERT INTO employees (name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES ('Tống Văn Mới', '001234567899', '0912000010',
        'Số 30 Tràng Thi, Hà Nội', DATE '2024-01-01', 'PHUC_VU', 1, NULL);

-- 6.5 RESTAURANT_TABLES — Phở Hà Nội (restaurant_id=1)
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn 01', 2, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn 02', 4, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn 03', 4, 'OCCUPIED',  1);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn 04', 6, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn 05', 6, 'DIRTY',     1);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn 06', 8, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn 07', 2, 'RESERVED',  1);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn 08', 4, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn VIP 01', 10, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn VIP 02', 12, 'CLEANING',  1);

-- RESTAURANT_TABLES — Bún Bò Huế (restaurant_id=2)
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn A1', 4, 'AVAILABLE', 2);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn A2', 4, 'OCCUPIED',  2);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn A3', 6, 'DIRTY',     2);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn A4', 2, 'AVAILABLE', 2);
INSERT INTO restaurant_tables (table_number, capacity, status, restaurant_id)
VALUES ('Bàn A5', 8, 'AVAILABLE', 2);

-- 6.6 MENUS — Phở Hà Nội
INSERT INTO menus (name, description, status, restaurant_id)
VALUES ('Phở', 'Các loại phở truyền thống', 'ACTIVE', 1);
INSERT INTO menus (name, description, status, restaurant_id)
VALUES ('Bún', 'Các loại bún', 'ACTIVE', 1);
INSERT INTO menus (name, description, status, restaurant_id)
VALUES ('Đồ uống', 'Trà, nước ngọt, sinh tố', 'ACTIVE', 1);
INSERT INTO menus (name, description, status, restaurant_id)
VALUES ('Thêm', 'Món ăn kèm và phụ', 'ACTIVE', 1);

-- MENUS — Bún Bò Huế
INSERT INTO menus (name, description, status, restaurant_id)
VALUES ('Bún Bò', 'Bún bò Huế đặc trưng', 'ACTIVE', 2);
INSERT INTO menus (name, description, status, restaurant_id)
VALUES ('Cơm', 'Các món cơm', 'ACTIVE', 2);
INSERT INTO menus (name, description, status, restaurant_id)
VALUES ('Đồ uống', 'Nước giải khát', 'ACTIVE', 2);

-- 6.7 MENU_ITEMS — Phở Hà Nội
-- menu_id=1: Phở
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Phở Bò Tái', 'Phở bò tái mềm, nước dùng trong', 65000, 'AVAILABLE', 1, 1);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Phở Bò Chín', 'Phở bò chín thơm, nước dùng đậm đà', 65000, 'AVAILABLE', 1, 1);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Phở Bò Tái Chín', 'Kết hợp tái và chín', 70000, 'AVAILABLE', 1, 1);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Phở Gà', 'Phở gà ta truyền thống', 60000, 'AVAILABLE', 1, 1);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Phở Đặc Biệt', 'Tái + Chín + Gầu + Gân', 85000, 'AVAILABLE', 1, 1);

-- menu_id=2: Bún
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Bún Chả', 'Bún chả Hà Nội chuẩn vị', 55000, 'AVAILABLE', 2, 1);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Bún Riêu Cua', 'Riêu cua đồng thơm ngon', 60000, 'AVAILABLE', 2, 1);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Bún Thang', 'Bún thang truyền thống Hà Nội', 65000, 'AVAILABLE', 2, 1);

-- menu_id=3: Đồ uống
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Trà Đá', 'Trà xanh pha đá mát lạnh', 10000, 'AVAILABLE', 3, 1);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Nước Ngọt Lon', 'Coca, Pepsi, 7Up', 20000, 'AVAILABLE', 3, 1);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Cà Phê Sữa Đá', 'Cà phê phin sữa đặc', 30000, 'AVAILABLE', 3, 1);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Sinh Tố Bơ', 'Sinh tố bơ kem sữa', 45000, 'AVAILABLE', 3, 1);

-- menu_id=4: Thêm
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Quẩy', 'Quẩy giòn ăn kèm phở', 10000, 'AVAILABLE', 4, 1);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Trứng Gà Luộc', 'Trứng gà luộc chín', 15000, 'AVAILABLE', 4, 1);

-- MENU_ITEMS — Bún Bò Huế
-- menu_id=5: Bún Bò
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Bún Bò Thường', 'Bún bò Huế chuẩn vị miền Trung', 60000, 'AVAILABLE', 5, 2);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Bún Bò Đặc Biệt', 'Thêm chả cua, giò heo', 80000, 'AVAILABLE', 5, 2);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Bún Bò Giò Heo', 'Giò heo hầm mềm', 75000, 'AVAILABLE', 5, 2);

-- menu_id=6: Cơm
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Cơm Sườn Nướng', 'Cơm trắng + sườn nướng mắm', 70000, 'AVAILABLE', 6, 2);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Cơm Gà Hội An', 'Cơm gà theo phong cách Hội An', 65000, 'AVAILABLE', 6, 2);

-- menu_id=7: Đồ uống
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Nước Mía', 'Nước mía tươi ép tại chỗ', 20000, 'AVAILABLE', 7, 2);
INSERT INTO menu_items (name, description, price, status, menu_id, restaurant_id)
VALUES ('Trà Gừng', 'Trà gừng mật ong ấm nóng', 25000, 'AVAILABLE', 7, 2);

-- 6.8 ORDERS — dữ liệu mẫu (một vài đơn)
-- Đơn đang phục vụ — Bàn 03 của Phở Hà Nội (table_id=3, restaurant_id=1)
INSERT INTO orders (status, total_amount, table_id, restaurant_id,
                    customer_name, customer_phone)
VALUES ('PENDING', 0, 3, 1, 'Khách lẻ', NULL);

-- Đơn đã hoàn thành hôm nay
INSERT INTO orders (status, total_amount, table_id, restaurant_id,
                    customer_name, customer_phone, completed_at)
VALUES ('COMPLETED', 195000, 2, 1, 'Nguyễn Hữu Thắng', '0901234567',
        SYSTIMESTAMP);

INSERT INTO orders (status, total_amount, table_id, restaurant_id,
                    customer_name, customer_phone, completed_at)
VALUES ('COMPLETED', 125000, 4, 1, NULL, NULL, SYSTIMESTAMP);

-- Đơn đã hoàn thành của Bún Bò Huế
INSERT INTO orders (status, total_amount, table_id, restaurant_id,
                    customer_name, customer_phone, completed_at)
VALUES ('COMPLETED', 160000, 12, 2, 'Trần Minh Khoa', '0909876543',
        SYSTIMESTAMP);

-- 6.9 ORDER_ITEMS cho đơn PENDING (order_id=1)
INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status, round_number)
VALUES (1, 1, 2, 65000, 'COOKING', 1);  -- 2 Phở Bò Tái
INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status, round_number)
VALUES (1, 9, 2, 10000, 'READY',   1);  -- 2 Trà Đá

-- ORDER_ITEMS cho đơn COMPLETED (order_id=2)
INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status, round_number)
VALUES (2, 5, 2, 85000, 'DELIVERED', 1);  -- 2 Phở Đặc Biệt
INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status, round_number)
VALUES (2, 10, 2, 20000, 'DELIVERED', 1);  -- 2 Nước Ngọt Lon
INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status, round_number)
VALUES (2, 13, 1, 10000, 'DELIVERED', 1);  -- 1 Quẩy

-- ORDER_ITEMS cho đơn COMPLETED (order_id=3)
INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status, round_number)
VALUES (3, 1, 1, 65000, 'DELIVERED', 1);
INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status, round_number)
VALUES (3, 6, 1, 55000, 'DELIVERED', 1);

-- ORDER_ITEMS cho đơn Bún Bò (order_id=4)
INSERT INTO order_items (order_id, menu_item_id, quantity, price, item_status, round_number)
VALUES (4, 16, 2, 80000, 'DELIVERED', 1);  -- 2 Bún Bò Đặc Biệt

-- Cập nhật tổng tiền cho đơn đang mở
UPDATE orders SET total_amount = (
    SELECT NVL(SUM(quantity * price), 0)
    FROM order_items WHERE order_id = 1
) WHERE order_id = 1;

-- 6.10 REPORTS — dữ liệu mẫu
INSERT INTO reports (title, description, report_type, severity, status,
                     created_by, restaurant_id)
VALUES ('Máy lạnh bàn 5 bị hỏng', 'Máy lạnh khu bàn 5 không hoạt động, khách phàn nàn nóng',
        'MAINTENANCE', 'HIGH', 'OPEN', 4, 1);

INSERT INTO reports (title, description, report_type, severity, status,
                     created_by, restaurant_id)
VALUES ('Khách hàng phản hồi tích cực', 'Khách Nguyễn Hữu Thắng khen phở ngon và phục vụ tốt',
        'FEEDBACK', 'LOW', 'CLOSED', 4, 1);

INSERT INTO reports (title, description, report_type, severity, status,
                     created_by, restaurant_id)
VALUES ('Sự cố rò rỉ gas bếp số 2',
        'Phát hiện mùi gas nhẹ gần bếp số 2, đã tắt van tạm thời, cần kiểm tra khẩn',
        'INCIDENT', 'CRITICAL', 'IN_PROGRESS', 6, 1);

INSERT INTO reports (title, description, report_type, severity, status,
                     created_by, restaurant_id)
VALUES ('Vệ sinh nhà vệ sinh không đạt', 'Nhà vệ sinh tầng 1 cần được vệ sinh thêm buổi chiều',
        'MAINTENANCE', 'MEDIUM', 'RESOLVED', 8, 1);

-- ============================================================
-- BƯỚC 7: COMMIT
-- ============================================================
COMMIT;

-- ============================================================
-- BƯỚC 8: KIỂM TRA NHANH
-- ============================================================
SELECT 'restaurants'   AS tbl, COUNT(*) AS cnt FROM restaurants  UNION ALL
SELECT 'roles',              COUNT(*) FROM roles               UNION ALL
SELECT 'users',              COUNT(*) FROM users               UNION ALL
SELECT 'employees',          COUNT(*) FROM employees           UNION ALL
SELECT 'restaurant_tables',  COUNT(*) FROM restaurant_tables   UNION ALL
SELECT 'menus',              COUNT(*) FROM menus               UNION ALL
SELECT 'menu_items',         COUNT(*) FROM menu_items          UNION ALL
SELECT 'orders',             COUNT(*) FROM orders              UNION ALL
SELECT 'order_items',        COUNT(*) FROM order_items         UNION ALL
SELECT 'reports',            COUNT(*) FROM reports
ORDER BY 1;

-- ============================================================
-- CHÚ THÍCH CUỐI
-- ============================================================
-- Để reset toàn bộ và chạy lại: chạy lại file này từ đầu.
-- Bảng session_tokens, refresh_tokens, operation_tokens,
-- security_audit_log, password_reset_tokens sẽ tự động
-- được tạo rỗng và được ứng dụng tự điền khi hoạt động.
-- ============================================================

-- ============================================================
-- FIX: Hash $2a$ — chạy lại toàn bộ
-- ============================================================

-- SuperAdmin@123
UPDATE users
SET password = '$2a$10$T7k4DqpPbbNCl.Q5Gxsqku9qxtxnmQPCYdb1DfvIYs9F2sVedDlZS'
WHERE email = 'superadmin@restaurant.com';

-- Admin@123
UPDATE users
SET password = '$2a$10$NYGnQXX22IpjN3dKkZw4guzCzjdGzRwnQg0skn6FUIVy4NBGAbVw6'
WHERE email IN ('admin1@phohanoi.com', 'admin2@bungbo.com');

-- Staff@123
UPDATE users
SET password = '$2a$10$NddX9pApRgrIMix/3Ml5huQlUUimvweBCE2KJhUapv1quJlC1iTIu'
WHERE email IN (
    'waiter1@phohanoi.com',
    'waiter2@phohanoi.com',
    'chef1@phohanoi.com',
    'chef2@phohanoi.com',
    'cashier1@phohanoi.com',
    'cashier1@bungbo.com',
    'waiter1@bungbo.com'
);

COMMIT;