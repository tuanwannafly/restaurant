-- ============================================================
-- SmartRestaurant – Oracle 19c/21c Full Setup Script
-- Password cho mọi tài khoản test: 123456
-- BCrypt hash dùng $2a$ (không phải $2b$)
-- ============================================================

-- ============================================================
-- PHẦN 1: XOÁ TOÀN BỘ ĐỐI TƯỢNG CŨ
-- ============================================================
BEGIN
  FOR t IN (
    SELECT table_name FROM user_tables
    WHERE table_name IN (
      'OPERATION_TOKENS','SECURITY_AUDIT_LOG','PASSWORD_RESET_TOKENS',
      'REFRESH_TOKENS','SESSION_TOKENS',
      'REPORTS','ORDER_ITEMS','ORDERS',
      'RESTAURANT_TABLES','MENU_ITEMS','MENUS',
      'EMPLOYEES','USERS','RESTAURANTS','ROLES'
    )
  ) LOOP
    EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS';
  END LOOP;
END;
/

BEGIN
  FOR s IN (
    SELECT sequence_name FROM user_sequences
    WHERE sequence_name IN (
      'SEQ_ROLE_ID','SEQ_RESTAURANT_ID','SEQ_USER_ID','SEQ_EMPLOYEE_ID',
      'SEQ_MENU_ID','SEQ_MENU_ITEM_ID','SEQ_TABLE_ID','SEQ_ORDER_ID',
      'SEQ_ORDER_ITEM_ID','SEQ_REPORT_ID','SEQ_AUDIT_LOG_ID'
    )
  ) LOOP
    EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
  END LOOP;
END;
/

-- ============================================================
-- PHẦN 2: TẠO SEQUENCES
-- ============================================================
CREATE SEQUENCE SEQ_ROLE_ID        START WITH 1  INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_RESTAURANT_ID  START WITH 1  INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_USER_ID        START WITH 1  INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_EMPLOYEE_ID    START WITH 1  INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_MENU_ID        START WITH 1  INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_MENU_ITEM_ID   START WITH 1  INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_TABLE_ID       START WITH 1  INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_ORDER_ID       START WITH 1  INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_ORDER_ITEM_ID  START WITH 1  INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_REPORT_ID      START WITH 1  INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_AUDIT_LOG_ID   START WITH 1  INCREMENT BY 1 NOCACHE NOCYCLE;

-- ============================================================
-- PHẦN 3: TẠO BẢNG
-- ============================================================

-- 3.1 ROLES
CREATE TABLE roles (
  id    NUMBER        NOT NULL,
  name  VARCHAR2(50)  NOT NULL,
  CONSTRAINT pk_roles PRIMARY KEY (id),
  CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE OR REPLACE TRIGGER trg_roles_id
  BEFORE INSERT ON roles FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := SEQ_ROLE_ID.NEXTVAL;
  END IF;
END;
/

-- 3.2 RESTAURANTS
CREATE TABLE restaurants (
  restaurant_id  NUMBER         NOT NULL,
  name           VARCHAR2(200)  NOT NULL,
  address        VARCHAR2(500),
  phone          VARCHAR2(20),
  email          VARCHAR2(200),
  status         VARCHAR2(20)   DEFAULT 'ACTIVE',
  logo_url       VARCHAR2(500),
  created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP,
  CONSTRAINT pk_restaurants PRIMARY KEY (restaurant_id),
  CONSTRAINT chk_restaurant_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE OR REPLACE TRIGGER trg_restaurants_id
  BEFORE INSERT ON restaurants FOR EACH ROW
BEGIN
  IF :NEW.restaurant_id IS NULL THEN
    :NEW.restaurant_id := SEQ_RESTAURANT_ID.NEXTVAL;
  END IF;
END;
/

-- 3.3 USERS
CREATE TABLE users (
  user_id        NUMBER        NOT NULL,
  name           VARCHAR2(200) NOT NULL,
  email          VARCHAR2(200) NOT NULL,
  password       VARCHAR2(200) NOT NULL,
  role_id        NUMBER        NOT NULL,
  restaurant_id  NUMBER,
  status         VARCHAR2(20)  DEFAULT 'ACTIVE',
  created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP,
  CONSTRAINT pk_users PRIMARY KEY (user_id),
  CONSTRAINT uq_users_email UNIQUE (email),
  CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id),
  CONSTRAINT fk_users_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id),
  CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','INACTIVE','LOCKED'))
);

CREATE OR REPLACE TRIGGER trg_users_id
  BEFORE INSERT ON users FOR EACH ROW
BEGIN
  IF :NEW.user_id IS NULL THEN
    :NEW.user_id := SEQ_USER_ID.NEXTVAL;
  END IF;
END;
/

-- 3.4 EMPLOYEES
CREATE TABLE employees (
  employee_id    NUMBER        NOT NULL,
  name           VARCHAR2(200) NOT NULL,
  cccd           VARCHAR2(20),
  phone          VARCHAR2(20),
  address        VARCHAR2(500),
  start_date     DATE,
  role           VARCHAR2(20)  DEFAULT 'PHUC_VU',
  restaurant_id  NUMBER        NOT NULL,
  user_id        NUMBER,
  CONSTRAINT pk_employees PRIMARY KEY (employee_id),
  CONSTRAINT fk_emp_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id),
  CONSTRAINT fk_emp_user FOREIGN KEY (user_id) REFERENCES users(user_id),
  CONSTRAINT chk_emp_role CHECK (role IN ('PHUC_VU','DAU_BEP','THU_NGAN','QUAN_LY'))
);

CREATE OR REPLACE TRIGGER trg_employees_id
  BEFORE INSERT ON employees FOR EACH ROW
BEGIN
  IF :NEW.employee_id IS NULL THEN
    :NEW.employee_id := SEQ_EMPLOYEE_ID.NEXTVAL;
  END IF;
END;
/

-- 3.5 MENUS
CREATE TABLE menus (
  menu_id        NUMBER        NOT NULL,
  name           VARCHAR2(200) NOT NULL,
  description    VARCHAR2(500),
  status         VARCHAR2(20)  DEFAULT 'ACTIVE',
  restaurant_id  NUMBER        NOT NULL,
  created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP,
  CONSTRAINT pk_menus PRIMARY KEY (menu_id),
  CONSTRAINT fk_menus_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id)
);

CREATE OR REPLACE TRIGGER trg_menus_id
  BEFORE INSERT ON menus FOR EACH ROW
BEGIN
  IF :NEW.menu_id IS NULL THEN
    :NEW.menu_id := SEQ_MENU_ID.NEXTVAL;
  END IF;
END;
/

-- 3.6 MENU_ITEMS
CREATE TABLE menu_items (
  item_id        NUMBER          NOT NULL,
  name           VARCHAR2(200)   NOT NULL,
  description    VARCHAR2(500),
  price          NUMBER(12,2)    DEFAULT 0,
  image_url      VARCHAR2(500),
  status         VARCHAR2(20)    DEFAULT 'AVAILABLE',
  menu_id        NUMBER          NOT NULL,
  restaurant_id  NUMBER          NOT NULL,
  CONSTRAINT pk_menu_items PRIMARY KEY (item_id),
  CONSTRAINT fk_items_menu FOREIGN KEY (menu_id) REFERENCES menus(menu_id),
  CONSTRAINT fk_items_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id),
  CONSTRAINT chk_items_status CHECK (status IN ('AVAILABLE','UNAVAILABLE','HIDDEN'))
);

CREATE OR REPLACE TRIGGER trg_menu_items_id
  BEFORE INSERT ON menu_items FOR EACH ROW
BEGIN
  IF :NEW.item_id IS NULL THEN
    :NEW.item_id := SEQ_MENU_ITEM_ID.NEXTVAL;
  END IF;
END;
/

-- 3.7 RESTAURANT_TABLES
CREATE TABLE restaurant_tables (
  table_id       NUMBER        NOT NULL,
  table_number   VARCHAR2(50)  NOT NULL,
  capacity       NUMBER        DEFAULT 4,
  status         VARCHAR2(20)  DEFAULT 'AVAILABLE',
  restaurant_id  NUMBER        NOT NULL,
  created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP,
  CONSTRAINT pk_tables PRIMARY KEY (table_id),
  CONSTRAINT fk_tables_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id),
  CONSTRAINT chk_tables_status CHECK (status IN ('AVAILABLE','OCCUPIED','RESERVED','DIRTY','CLEANING','OUT_OF_SERVICE'))
);

CREATE OR REPLACE TRIGGER trg_tables_id
  BEFORE INSERT ON restaurant_tables FOR EACH ROW
BEGIN
  IF :NEW.table_id IS NULL THEN
    :NEW.table_id := SEQ_TABLE_ID.NEXTVAL;
  END IF;
END;
/

-- 3.8 ORDERS
CREATE TABLE orders (
  order_id       NUMBER        NOT NULL,
  status         VARCHAR2(30)  DEFAULT 'PENDING',
  total_amount   NUMBER(14,2)  DEFAULT 0,
  table_id       NUMBER        NOT NULL,
  restaurant_id  NUMBER        NOT NULL,
  customer_name  VARCHAR2(200),
  customer_phone VARCHAR2(20),
  created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP,
  completed_at   TIMESTAMP,
  CONSTRAINT pk_orders PRIMARY KEY (order_id),
  CONSTRAINT fk_orders_table FOREIGN KEY (table_id) REFERENCES restaurant_tables(table_id),
  CONSTRAINT fk_orders_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id),
  CONSTRAINT chk_orders_status CHECK (status IN (
    'PENDING','ACCEPTED','COOKING','READY','DELIVERING',
    'DELIVERED','COMPLETED','CANCELLED','PAYMENT_REQUESTED','IN_PROGRESS'
  ))
);

CREATE OR REPLACE TRIGGER trg_orders_id
  BEFORE INSERT ON orders FOR EACH ROW
BEGIN
  IF :NEW.order_id IS NULL THEN
    :NEW.order_id := SEQ_ORDER_ID.NEXTVAL;
  END IF;
END;
/

-- 3.9 ORDER_ITEMS
CREATE TABLE order_items (
  order_item_id  NUMBER        NOT NULL,
  order_id       NUMBER        NOT NULL,
  menu_item_id   NUMBER        NOT NULL,
  quantity       NUMBER        DEFAULT 1,
  price          NUMBER(12,2)  DEFAULT 0,
  item_status    VARCHAR2(20)  DEFAULT 'PENDING',
  round_number   NUMBER        DEFAULT 1,
  created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP,
  assigned_to    VARCHAR2(200),
  CONSTRAINT pk_order_items PRIMARY KEY (order_item_id),
  CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
  CONSTRAINT fk_oi_menu_item FOREIGN KEY (menu_item_id) REFERENCES menu_items(item_id),
  CONSTRAINT chk_oi_status CHECK (item_status IN (
    'PENDING','ACCEPTED','COOKING','READY','DELIVERING','DELIVERED','CANCELLED'
  ))
);

CREATE OR REPLACE TRIGGER trg_order_items_id
  BEFORE INSERT ON order_items FOR EACH ROW
BEGIN
  IF :NEW.order_item_id IS NULL THEN
    :NEW.order_item_id := SEQ_ORDER_ITEM_ID.NEXTVAL;
  END IF;
END;
/

-- 3.10 REPORTS
CREATE TABLE reports (
  report_id      NUMBER        NOT NULL,
  title          VARCHAR2(300) NOT NULL,
  description    VARCHAR2(2000),
  report_type    VARCHAR2(20)  DEFAULT 'INCIDENT',
  severity       VARCHAR2(20)  DEFAULT 'LOW',
  status         VARCHAR2(20)  DEFAULT 'OPEN',
  created_by     NUMBER        NOT NULL,
  restaurant_id  NUMBER        NOT NULL,
  created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP,
  resolved_at    TIMESTAMP,
  CONSTRAINT pk_reports PRIMARY KEY (report_id),
  CONSTRAINT fk_reports_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id),
  CONSTRAINT chk_report_type CHECK (report_type IN ('INCIDENT','MAINTENANCE','FEEDBACK')),
  CONSTRAINT chk_report_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  CONSTRAINT chk_report_status CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED'))
);

CREATE OR REPLACE TRIGGER trg_reports_id
  BEFORE INSERT ON reports FOR EACH ROW
BEGIN
  IF :NEW.report_id IS NULL THEN
    :NEW.report_id := SEQ_REPORT_ID.NEXTVAL;
  END IF;
END;
/

-- 3.11 SESSION_TOKENS
CREATE TABLE session_tokens (
  token_id    VARCHAR2(36)  NOT NULL,
  user_id     NUMBER        NOT NULL,
  created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP,
  expires_at  TIMESTAMP     NOT NULL,
  is_active   NUMBER(1)     DEFAULT 1,
  ip_address  VARCHAR2(50),
  CONSTRAINT pk_session_tokens PRIMARY KEY (token_id),
  CONSTRAINT fk_st_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 3.12 REFRESH_TOKENS
CREATE TABLE refresh_tokens (
  token        VARCHAR2(200)  NOT NULL,
  user_id      NUMBER         NOT NULL,
  device_name  VARCHAR2(200),
  created_at   TIMESTAMP      DEFAULT SYSTIMESTAMP,
  expires_at   TIMESTAMP      NOT NULL,
  revoked      NUMBER(1)      DEFAULT 0,
  CONSTRAINT pk_refresh_tokens PRIMARY KEY (token),
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 3.13 PASSWORD_RESET_TOKENS
CREATE TABLE password_reset_tokens (
  token       VARCHAR2(200)  NOT NULL,
  user_id     NUMBER         NOT NULL,
  expires_at  TIMESTAMP      NOT NULL,
  used        NUMBER(1)      DEFAULT 0,
  CONSTRAINT pk_prt PRIMARY KEY (token),
  CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 3.14 SECURITY_AUDIT_LOG
CREATE TABLE security_audit_log (
  log_id         NUMBER        NOT NULL,
  action         VARCHAR2(50)  NOT NULL,
  actor_user_id  NUMBER,
  target_id      NUMBER,
  session_token  VARCHAR2(36),
  op_token       VARCHAR2(16),
  result         VARCHAR2(10),
  detail         VARCHAR2(500),
  logged_at      TIMESTAMP     DEFAULT SYSTIMESTAMP,
  CONSTRAINT pk_audit_log PRIMARY KEY (log_id)
);

CREATE OR REPLACE TRIGGER trg_audit_log_id
  BEFORE INSERT ON security_audit_log FOR EACH ROW
BEGIN
  IF :NEW.log_id IS NULL THEN
    :NEW.log_id := SEQ_AUDIT_LOG_ID.NEXTVAL;
  END IF;
END;
/

-- 3.15 OPERATION_TOKENS
CREATE TABLE operation_tokens (
  token           VARCHAR2(16)  NOT NULL,
  operation_type  VARCHAR2(50)  NOT NULL,
  actor_user_id   NUMBER        NOT NULL,
  target_id       NUMBER        NOT NULL,
  created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP,
  expires_at      TIMESTAMP     NOT NULL,
  used            NUMBER(1)     DEFAULT 0,
  CONSTRAINT pk_op_tokens PRIMARY KEY (token)
);

-- ============================================================
-- PHẦN 4: INDEXES
-- ============================================================
CREATE INDEX idx_users_email         ON users(email);
CREATE INDEX idx_users_restaurant    ON users(restaurant_id);
CREATE INDEX idx_employees_rest      ON employees(restaurant_id);
CREATE INDEX idx_employees_user      ON employees(user_id);
CREATE INDEX idx_menu_items_rest     ON menu_items(restaurant_id);
CREATE INDEX idx_menu_items_menu     ON menu_items(menu_id);
CREATE INDEX idx_tables_rest         ON restaurant_tables(restaurant_id);
CREATE INDEX idx_orders_table        ON orders(table_id);
CREATE INDEX idx_orders_rest         ON orders(restaurant_id);
CREATE INDEX idx_orders_status       ON orders(status);
CREATE INDEX idx_oi_order            ON order_items(order_id);
CREATE INDEX idx_oi_status           ON order_items(item_status);
CREATE INDEX idx_reports_rest        ON reports(restaurant_id);
CREATE INDEX idx_audit_action        ON security_audit_log(action);
CREATE INDEX idx_audit_logged_at     ON security_audit_log(logged_at);
CREATE INDEX idx_st_user_active      ON session_tokens(user_id, is_active);
CREATE INDEX idx_rt_user             ON refresh_tokens(user_id, revoked);

-- ============================================================
-- PHẦN 5: DỮ LIỆU SEED
-- ============================================================

-- 5.1 ROLES
INSERT INTO roles (id, name) VALUES (1, 'SUPER_ADMIN');
INSERT INTO roles (id, name) VALUES (2, 'RESTAURANT_ADMIN');
INSERT INTO roles (id, name) VALUES (3, 'WAITER');
INSERT INTO roles (id, name) VALUES (4, 'CHEF');
INSERT INTO roles (id, name) VALUES (5, 'CASHIER');

-- 5.2 RESTAURANTS
INSERT INTO restaurants (restaurant_id, name, address, phone, email, status, created_at)
VALUES (1, 'SmartRestaurant Trung Tâm',
        '123 Nguyễn Huệ, Quận 1, TP.HCM',
        '028-3823-9999',
        'contact@smartrestaurant.vn',
        'ACTIVE',
        SYSTIMESTAMP);

INSERT INTO restaurants (restaurant_id, name, address, phone, email, status, created_at)
VALUES (2, 'SmartRestaurant Phú Mỹ Hưng',
        '456 Nguyễn Lương Bằng, Quận 7, TP.HCM',
        '028-5413-8888',
        'pmh@smartrestaurant.vn',
        'ACTIVE',
        SYSTIMESTAMP);

INSERT INTO restaurants (restaurant_id, name, address, phone, email, status, created_at)
VALUES (3, 'SmartRestaurant Thủ Đức',
        '789 Võ Văn Ngân, TP.Thủ Đức, TP.HCM',
        '028-7301-7777',
        'thuduc@smartrestaurant.vn',
        'INACTIVE',
        SYSTIMESTAMP);

-- 5.3 USERS
-- Password cho tất cả: 123456
-- BCrypt $2a$10$ hash của "123456"
-- $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

-- SUPER_ADMIN (restaurant_id = NULL)
INSERT INTO users (user_id, name, email, password, role_id, restaurant_id, status)
VALUES (1, 'Super Admin',
        'superadmin@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        1, NULL, 'ACTIVE');

-- RESTAURANT_ADMIN - Nhà hàng 1
INSERT INTO users (user_id, name, email, password, role_id, restaurant_id, status)
VALUES (2, 'Nguyễn Văn Admin',
        'admin1@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        2, 1, 'ACTIVE');

-- RESTAURANT_ADMIN - Nhà hàng 2
INSERT INTO users (user_id, name, email, password, role_id, restaurant_id, status)
VALUES (3, 'Trần Thị Admin',
        'admin2@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        2, 2, 'ACTIVE');

-- WAITER - Nhà hàng 1
INSERT INTO users (user_id, name, email, password, role_id, restaurant_id, status)
VALUES (4, 'Lê Thị Phục Vụ',
        'waiter1@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        3, 1, 'ACTIVE');

-- CHEF - Nhà hàng 1
INSERT INTO users (user_id, name, email, password, role_id, restaurant_id, status)
VALUES (5, 'Phạm Văn Bếp',
        'chef1@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        4, 1, 'ACTIVE');

-- CASHIER - Nhà hàng 1
INSERT INTO users (user_id, name, email, password, role_id, restaurant_id, status)
VALUES (6, 'Hoàng Thị Thu Ngân',
        'cashier1@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        5, 1, 'ACTIVE');

-- WAITER thứ 2 - Nhà hàng 1
INSERT INTO users (user_id, name, email, password, role_id, restaurant_id, status)
VALUES (7, 'Đỗ Văn Phục Vụ Hai',
        'waiter2@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        3, 1, 'ACTIVE');

-- CHEF - Nhà hàng 2
INSERT INTO users (user_id, name, email, password, role_id, restaurant_id, status)
VALUES (8, 'Vũ Thị Bếp Hai',
        'chef2@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        4, 2, 'ACTIVE');

-- CASHIER - Nhà hàng 2
INSERT INTO users (user_id, name, email, password, role_id, restaurant_id, status)
VALUES (9, 'Bùi Văn Thu Ngân Hai',
        'cashier2@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        5, 2, 'ACTIVE');

-- 5.4 EMPLOYEES
-- Nhà hàng 1
INSERT INTO employees (employee_id, name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES (1, 'Nguyễn Văn Admin',  '001234567890', '0901234567', 'Q.1, TP.HCM', DATE '2022-01-15', 'QUAN_LY',  1, 2);

INSERT INTO employees (employee_id, name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES (2, 'Lê Thị Phục Vụ',    '001234567891', '0912345678', 'Q.3, TP.HCM', DATE '2023-03-01', 'PHUC_VU',  1, 4);

INSERT INTO employees (employee_id, name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES (3, 'Phạm Văn Bếp',      '001234567892', '0923456789', 'Q.5, TP.HCM', DATE '2022-06-15', 'DAU_BEP',  1, 5);

INSERT INTO employees (employee_id, name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES (4, 'Hoàng Thị Thu Ngân','001234567893', '0934567890', 'Q.7, TP.HCM', DATE '2023-01-10', 'THU_NGAN', 1, 6);

INSERT INTO employees (employee_id, name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES (5, 'Đỗ Văn Phục Vụ Hai','001234567894', '0945678901', 'Q.8, TP.HCM', DATE '2023-07-20', 'PHUC_VU',  1, 7);

-- Nhân viên chưa có tài khoản (để test tạo tài khoản)
INSERT INTO employees (employee_id, name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES (6, 'Nguyễn Thị Mới',    '001234567895', '0956789012', 'Q.Bình Thạnh, TP.HCM', DATE '2024-01-05', 'PHUC_VU', 1, NULL);

-- Nhà hàng 2
INSERT INTO employees (employee_id, name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES (7, 'Trần Thị Admin',     '002345678901', '0967890123', 'Q.7, TP.HCM', DATE '2022-02-01', 'QUAN_LY',  2, 3);

INSERT INTO employees (employee_id, name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES (8, 'Vũ Thị Bếp Hai',    '002345678902', '0978901234', 'Q.7, TP.HCM', DATE '2023-05-15', 'DAU_BEP',  2, 8);

INSERT INTO employees (employee_id, name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES (9, 'Bùi Văn Thu Ngân Hai','002345678903','0989012345', 'Q.7, TP.HCM', DATE '2023-09-01', 'THU_NGAN', 2, 9);

-- 5.5 MENUS
-- Nhà hàng 1
INSERT INTO menus (menu_id, name, description, status, restaurant_id)
VALUES (1, 'Hải sản',   'Các món từ hải sản tươi', 'ACTIVE', 1);
INSERT INTO menus (menu_id, name, description, status, restaurant_id)
VALUES (2, 'Thịt',      'Các món thịt nướng & xào', 'ACTIVE', 1);
INSERT INTO menus (menu_id, name, description, status, restaurant_id)
VALUES (3, 'Cơm',       'Cơm trắng & cơm chiên',    'ACTIVE', 1);
INSERT INTO menus (menu_id, name, description, status, restaurant_id)
VALUES (4, 'Phở',       'Phở bò & phở gà',          'ACTIVE', 1);
INSERT INTO menus (menu_id, name, description, status, restaurant_id)
VALUES (5, 'Đồ uống',   'Nước ép, sinh tố, trà',    'ACTIVE', 1);
INSERT INTO menus (menu_id, name, description, status, restaurant_id)
VALUES (6, 'Tráng miệng','Chè, bánh, kem',           'ACTIVE', 1);

-- Nhà hàng 2
INSERT INTO menus (menu_id, name, description, status, restaurant_id)
VALUES (7, 'Hải sản',   'Hải sản tươi sống',        'ACTIVE', 2);
INSERT INTO menus (menu_id, name, description, status, restaurant_id)
VALUES (8, 'Đồ uống',   'Thức uống các loại',       'ACTIVE', 2);

-- 5.6 MENU_ITEMS
-- Nhà hàng 1 - Hải sản
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (1, 'Tôm sú nướng muối ớt',  'Tôm sú tươi nướng với muối ớt đặc biệt', 285000, 'AVAILABLE', 1, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (2, 'Cua rang me',            'Cua biển rang me chua ngọt',              320000, 'AVAILABLE', 1, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (3, 'Mực nướng sa tế',       'Mực ống nướng sa tế cay nồng',           195000, 'AVAILABLE', 1, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (4, 'Nghêu hấp sả',          'Nghêu tươi hấp với sả và gừng',          125000, 'AVAILABLE', 1, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (5, 'Cá lóc nướng trui',     'Cá lóc đồng nướng trui trọn vẹn',       220000, 'AVAILABLE', 1, 1);

-- Nhà hàng 1 - Thịt
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (6, 'Bò lúc lắc',            'Thịt bò Mỹ xào lúc lắc kiểu Pháp',      265000, 'AVAILABLE', 2, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (7, 'Sườn nướng BBQ',        'Sườn heo nướng sốt BBQ Mỹ',              185000, 'AVAILABLE', 2, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (8, 'Gà nướng mật ong',      'Đùi gà nướng mật ong vàng ươm',         155000, 'AVAILABLE', 2, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (9, 'Heo quay da giòn',      'Heo quay da giòn tan, thịt mềm',         175000, 'AVAILABLE', 2, 1);

-- Nhà hàng 1 - Cơm
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (10, 'Cơm chiên dương châu', 'Cơm chiên kiểu Dương Châu truyền thống', 75000, 'AVAILABLE', 3, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (11, 'Cơm tấm sườn bì',     'Cơm tấm đặc sản Sài Gòn',                95000, 'AVAILABLE', 3, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (12, 'Cơm gà Hải Nam',      'Cơm gà hấp kiểu Hải Nam với nước sốt gừng', 115000, 'AVAILABLE', 3, 1);

-- Nhà hàng 1 - Phở
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (13, 'Phở bò đặc biệt',     'Phở bò tái chín gân gầu, nước dùng đậm đà', 85000, 'AVAILABLE', 4, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (14, 'Phở gà',              'Phở gà ta hầm nguyên con, thịt mềm ngọt',   75000, 'AVAILABLE', 4, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (15, 'Hủ tiếu Nam Vang',    'Hủ tiếu Nam Vang truyền thống',              80000, 'AVAILABLE', 4, 1);

-- Nhà hàng 1 - Đồ uống
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (16, 'Nước ép cam tươi',    'Cam vắt tươi 100%, thêm đá',               45000, 'AVAILABLE', 5, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (17, 'Sinh tố bơ',          'Sinh tố bơ sáp mịn với sữa đặc',           55000, 'AVAILABLE', 5, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (18, 'Trà đào cam sả',      'Trà đào thơm mát với cam và sả',            45000, 'AVAILABLE', 5, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (19, 'Coca Cola',           'Coca Cola lon lạnh',                         25000, 'AVAILABLE', 5, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (20, 'Bia Tiger chai',      'Bia Tiger chai 330ml lạnh',                  35000, 'AVAILABLE', 5, 1);

-- Nhà hàng 1 - Tráng miệng
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (21, 'Chè khúc bạch',       'Chè khúc bạch thạch mát lạnh',              55000, 'AVAILABLE', 6, 1);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (22, 'Kem dừa',             'Kem dừa Thái Lan béo ngậy',                  45000, 'AVAILABLE', 6, 1);

-- Nhà hàng 2
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (23, 'Tôm hùm nướng',       'Tôm hùm nướng phô mai béo ngậy',           650000, 'AVAILABLE', 7, 2);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (24, 'Ghẹ hấp bia',         'Ghẹ biển tươi hấp bia',                    380000, 'AVAILABLE', 7, 2);
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id)
VALUES (25, 'Nước dừa tươi',       'Dừa xiêm nguyên trái',                      45000, 'AVAILABLE', 8, 2);

-- 5.7 RESTAURANT_TABLES
-- Nhà hàng 1 (15 bàn)
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (1,  'Bàn 01', 2, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (2,  'Bàn 02', 2, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (3,  'Bàn 03', 4, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (4,  'Bàn 04', 4, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (5,  'Bàn 05', 4, 'OCCUPIED',  1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (6,  'Bàn 06', 4, 'OCCUPIED',  1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (7,  'Bàn 07', 6, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (8,  'Bàn 08', 6, 'OCCUPIED',  1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (9,  'Bàn 09', 6, 'DIRTY',     1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (10, 'Bàn 10', 8, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (11, 'Bàn 11', 8, 'RESERVED',  1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (12, 'Bàn 12', 8, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (13, 'VIP 01', 10, 'AVAILABLE', 1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (14, 'VIP 02', 12, 'RESERVED',  1);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (15, 'Sân vườn', 20, 'AVAILABLE', 1);

-- Nhà hàng 2 (8 bàn)
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (16, 'Bàn A1', 4, 'AVAILABLE', 2);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (17, 'Bàn A2', 4, 'OCCUPIED',  2);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (18, 'Bàn A3', 6, 'AVAILABLE', 2);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (19, 'Bàn A4', 6, 'DIRTY',     2);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (20, 'Bàn B1', 8, 'AVAILABLE', 2);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (21, 'Bàn B2', 8, 'AVAILABLE', 2);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (22, 'VIP A', 12, 'AVAILABLE', 2);
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id)
VALUES (23, 'VIP B', 12, 'RESERVED',  2);

-- 5.8 ORDERS (Nhà hàng 1 – các đơn đang hoạt động)

-- Đơn 1: Bàn 05 đang ăn
INSERT INTO orders (order_id, status, total_amount, table_id, restaurant_id, customer_name, customer_phone, created_at)
VALUES (1, 'PENDING', 455000, 5, 1, NULL, NULL,
        SYSTIMESTAMP - INTERVAL '45' MINUTE);

-- Đơn 2: Bàn 06 đang ăn
INSERT INTO orders (order_id, status, total_amount, table_id, restaurant_id, customer_name, customer_phone, created_at)
VALUES (2, 'PENDING', 320000, 6, 1, 'Nguyễn Văn A', '0901234999',
        SYSTIMESTAMP - INTERVAL '30' MINUTE);

-- Đơn 3: Bàn 08 đang ăn
INSERT INTO orders (order_id, status, total_amount, table_id, restaurant_id, customer_name, customer_phone, created_at)
VALUES (3, 'PENDING', 780000, 8, 1, NULL, NULL,
        SYSTIMESTAMP - INTERVAL '60' MINUTE);

-- Đơn 4: Hoàn thành hôm nay
INSERT INTO orders (order_id, status, total_amount, table_id, restaurant_id, customer_name, customer_phone, created_at, completed_at)
VALUES (4, 'COMPLETED', 550000, 3, 1, NULL, NULL,
        SYSTIMESTAMP - INTERVAL '3' HOUR,
        SYSTIMESTAMP - INTERVAL '2' HOUR);

-- Đơn 5: Hoàn thành hôm nay
INSERT INTO orders (order_id, status, total_amount, table_id, restaurant_id, customer_name, customer_phone, created_at, completed_at)
VALUES (5, 'COMPLETED', 890000, 4, 1, NULL, NULL,
        SYSTIMESTAMP - INTERVAL '4' HOUR,
        SYSTIMESTAMP - INTERVAL '3' HOUR);

-- Đơn 6: Đã hủy hôm nay
INSERT INTO orders (order_id, status, total_amount, table_id, restaurant_id, customer_name, customer_phone, created_at)
VALUES (6, 'CANCELLED', 185000, 2, 1, NULL, NULL,
        SYSTIMESTAMP - INTERVAL '2' HOUR);

-- Đơn 7: Nhà hàng 2 đang hoạt động
INSERT INTO orders (order_id, status, total_amount, table_id, restaurant_id, customer_name, customer_phone, created_at)
VALUES (7, 'PENDING', 1030000, 17, 2, NULL, NULL,
        SYSTIMESTAMP - INTERVAL '20' MINUTE);

-- 5.9 ORDER_ITEMS

-- Order 1 - Bàn 05 (Mix: PENDING & COOKING)
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (1, 1, 1, 1, 285000, 'COOKING',  1, SYSTIMESTAMP - INTERVAL '44' MINUTE);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (2, 1, 18, 2, 45000, 'PENDING',  1, SYSTIMESTAMP - INTERVAL '44' MINUTE);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (3, 1, 22, 1, 45000, 'PENDING',  1, SYSTIMESTAMP - INTERVAL '44' MINUTE);
-- Lượt 2 gọi thêm
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (4, 1, 16, 2, 45000, 'PENDING',  2, SYSTIMESTAMP - INTERVAL '15' MINUTE);

-- Order 2 - Bàn 06 (READY - đã nấu xong, chờ phục vụ)
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (5, 2, 6, 1, 265000, 'READY',  1, SYSTIMESTAMP - INTERVAL '29' MINUTE);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (6, 2, 17, 1, 55000,  'READY',  1, SYSTIMESTAMP - INTERVAL '29' MINUTE);

-- Order 3 - Bàn 08 (Mix trạng thái)
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (7,  3, 2,  1, 320000, 'DELIVERED', 1, SYSTIMESTAMP - INTERVAL '58' MINUTE);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (8,  3, 10, 2, 75000,  'DELIVERED', 1, SYSTIMESTAMP - INTERVAL '58' MINUTE);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (9,  3, 19, 3, 25000,  'DELIVERED', 1, SYSTIMESTAMP - INTERVAL '58' MINUTE);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (10, 3, 7,  2, 185000, 'PENDING',   2, SYSTIMESTAMP - INTERVAL '10' MINUTE);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (11, 3, 21, 2, 55000,  'PENDING',   2, SYSTIMESTAMP - INTERVAL '10' MINUTE);

-- Order 4 - Completed
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (12, 4, 13, 2, 85000,  'DELIVERED', 1, SYSTIMESTAMP - INTERVAL '3' HOUR);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (13, 4, 14, 1, 75000,  'DELIVERED', 1, SYSTIMESTAMP - INTERVAL '3' HOUR);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (14, 4, 18, 3, 45000,  'DELIVERED', 1, SYSTIMESTAMP - INTERVAL '3' HOUR);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (15, 4, 20, 4, 35000,  'DELIVERED', 1, SYSTIMESTAMP - INTERVAL '3' HOUR);

-- Order 5 - Completed
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (16, 5, 1,  2, 285000, 'DELIVERED', 1, SYSTIMESTAMP - INTERVAL '4' HOUR);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (17, 5, 6,  1, 265000, 'DELIVERED', 1, SYSTIMESTAMP - INTERVAL '4' HOUR);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (18, 5, 20, 6, 35000,  'DELIVERED', 1, SYSTIMESTAMP - INTERVAL '4' HOUR);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (19, 5, 16, 2, 45000,  'DELIVERED', 1, SYSTIMESTAMP - INTERVAL '4' HOUR);

-- Order 6 - Cancelled (để hiển thị trong tab Đã hủy)
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (20, 6, 8, 1, 155000, 'CANCELLED', 1, SYSTIMESTAMP - INTERVAL '2' HOUR);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (21, 6, 19, 2, 25000, 'CANCELLED', 1, SYSTIMESTAMP - INTERVAL '2' HOUR);

-- Order 7 - Nhà hàng 2
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (22, 7, 23, 2, 650000, 'COOKING',  1, SYSTIMESTAMP - INTERVAL '18' MINUTE);
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at)
VALUES (23, 7, 25, 3, 45000,  'PENDING',  1, SYSTIMESTAMP - INTERVAL '18' MINUTE);

-- 5.10 REPORTS
INSERT INTO reports (report_id, title, description, report_type, severity, status, created_by, restaurant_id, created_at)
VALUES (1, 'Điều hoà phòng VIP hư', 'Điều hoà phòng VIP 02 không lạnh, cần sửa gấp',
        'MAINTENANCE', 'HIGH', 'OPEN', 4, 1, SYSTIMESTAMP - INTERVAL '2' HOUR);

INSERT INTO reports (report_id, title, description, report_type, severity, status, created_by, restaurant_id, created_at)
VALUES (2, 'Khách phàn nàn món ăn chậm', 'Bàn 08 chờ hơn 45 phút chưa có món',
        'INCIDENT', 'MEDIUM', 'IN_PROGRESS', 4, 1, SYSTIMESTAMP - INTERVAL '1' HOUR);

INSERT INTO reports (report_id, title, description, report_type, severity, status, created_by, restaurant_id, created_at)
VALUES (3, 'Thiếu tương ớt', 'Hết tương ớt Chin-su, cần nhập thêm',
        'FEEDBACK', 'LOW', 'RESOLVED', 5, 1,
        SYSTIMESTAMP - INTERVAL '1' DAY);

INSERT INTO reports (report_id, title, description, report_type, severity, status, created_by, restaurant_id, created_at)
VALUES (4, 'Máy tính tiền lỗi', 'Máy POS bàn thu ngân báo lỗi kết nối mạng',
        'INCIDENT', 'CRITICAL', 'OPEN', 6, 1, SYSTIMESTAMP - INTERVAL '30' MINUTE);

-- ============================================================
-- PHẦN 6: CẬP NHẬT SEQUENCES CHO ĐÚNG GIÁ TRỊ TIẾP THEO
-- ============================================================
-- Sau khi INSERT manual với giá trị cố định, cần reset sequence
ALTER SEQUENCE SEQ_ROLE_ID        RESTART START WITH 10;
ALTER SEQUENCE SEQ_RESTAURANT_ID  RESTART START WITH 10;
ALTER SEQUENCE SEQ_USER_ID        RESTART START WITH 20;
ALTER SEQUENCE SEQ_EMPLOYEE_ID    RESTART START WITH 20;
ALTER SEQUENCE SEQ_MENU_ID        RESTART START WITH 20;
ALTER SEQUENCE SEQ_MENU_ITEM_ID   RESTART START WITH 50;
ALTER SEQUENCE SEQ_TABLE_ID       RESTART START WITH 50;
ALTER SEQUENCE SEQ_ORDER_ID       RESTART START WITH 20;
ALTER SEQUENCE SEQ_ORDER_ITEM_ID  RESTART START WITH 50;
ALTER SEQUENCE SEQ_REPORT_ID      RESTART START WITH 10;

COMMIT;

-- ============================================================
-- PHẦN 7: KIỂM TRA DỮ LIỆU
-- ============================================================
SELECT '=== KIỂM TRA DỮ LIỆU ===' AS info FROM DUAL;

SELECT 'ROLES' AS tbl, COUNT(*) AS cnt FROM roles
UNION ALL
SELECT 'RESTAURANTS',  COUNT(*) FROM restaurants
UNION ALL
SELECT 'USERS',        COUNT(*) FROM users
UNION ALL
SELECT 'EMPLOYEES',    COUNT(*) FROM employees
UNION ALL
SELECT 'MENUS',        COUNT(*) FROM menus
UNION ALL
SELECT 'MENU_ITEMS',   COUNT(*) FROM menu_items
UNION ALL
SELECT 'REST_TABLES',  COUNT(*) FROM restaurant_tables
UNION ALL
SELECT 'ORDERS',       COUNT(*) FROM orders
UNION ALL
SELECT 'ORDER_ITEMS',  COUNT(*) FROM order_items
UNION ALL
SELECT 'REPORTS',      COUNT(*) FROM reports;

-- Kiểm tra tài khoản đăng nhập
SELECT '--- TÀI KHOẢN ĐĂNG NHẬP ---' AS info FROM DUAL;
SELECT u.user_id, u.name, u.email, r.name AS role,
       u.restaurant_id, u.status
FROM   users u
JOIN   roles r ON u.role_id = r.id
ORDER  BY u.user_id;

-- Kiểm tra KitchenPanel (món PENDING/COOKING)
SELECT '--- KitchenPanel: Đang chờ chế biến ---' AS info FROM DUAL;
SELECT oi.order_item_id, mi.name AS mon, oi.quantity,
       oi.item_status, rt.table_number, oi.round_number
FROM   order_items oi
JOIN   orders o            ON oi.order_id     = o.order_id
JOIN   menu_items mi       ON oi.menu_item_id = mi.item_id
JOIN   restaurant_tables rt ON o.table_id     = rt.table_id
WHERE  o.restaurant_id = 1
  AND  oi.item_status IN ('PENDING','ACCEPTED','COOKING')
ORDER  BY rt.table_number, oi.round_number;

-- Kiểm tra WaiterServicePanel (món READY)
SELECT '--- WaiterServicePanel: Cần phục vụ ---' AS info FROM DUAL;
SELECT oi.order_item_id, mi.name AS mon, oi.quantity,
       oi.item_status, rt.table_number, oi.round_number
FROM   order_items oi
JOIN   orders o            ON oi.order_id     = o.order_id
JOIN   menu_items mi       ON oi.menu_item_id = mi.item_id
JOIN   restaurant_tables rt ON o.table_id     = rt.table_id
WHERE  o.restaurant_id = 1
  AND  oi.item_status = 'READY'
ORDER  BY rt.table_number;

-- Kiểm tra bàn DIRTY
SELECT '--- Bàn cần dọn ---' AS info FROM DUAL;
SELECT table_id, table_number, status
FROM   restaurant_tables
WHERE  status IN ('DIRTY','CLEANING')
ORDER  BY table_number;

-- ============================================================
-- PHẦN 8: GHI CHÚ TÀI KHOẢN TEST
-- ============================================================
/*
  ┌─────────────────────────────────────────────────────────────────────┐
  │                  TÀI KHOẢN TEST                                     │
  │  Mật khẩu: 123456 (BCrypt $2a$10$...)                              │
  ├──────────────────────────────┬───────────┬────────────────────────  │
  │ Email                        │ Role      │ Nhà hàng               │
  ├──────────────────────────────┼───────────┼────────────────────────  │
  │ superadmin@smartrestaurant.vn│ SUPER_ADMIN│ (tất cả)              │
  │ admin1@smartrestaurant.vn    │ REST_ADMIN│ SmartRest Trung Tâm    │
  │ admin2@smartrestaurant.vn    │ REST_ADMIN│ SmartRest Phú Mỹ Hưng  │
  │ waiter1@smartrestaurant.vn   │ WAITER    │ SmartRest Trung Tâm    │
  │ chef1@smartrestaurant.vn     │ CHEF      │ SmartRest Trung Tâm    │
  │ cashier1@smartrestaurant.vn  │ CASHIER   │ SmartRest Trung Tâm    │
  │ waiter2@smartrestaurant.vn   │ WAITER    │ SmartRest Trung Tâm    │
  │ chef2@smartrestaurant.vn     │ CHEF      │ SmartRest Phú Mỹ Hưng  │
  │ cashier2@smartrestaurant.vn  │ CASHIER   │ SmartRest Phú Mỹ Hưng  │
  └──────────────────────────────┴───────────┴────────────────────────  │
  └─────────────────────────────────────────────────────────────────────┘

  TRẠNG THÁI MÔ PHỎNG:
  - Bàn 05, 06, 08 đang có khách (OCCUPIED + order PENDING)
  - Bàn 09 vừa xong, cần dọn (DIRTY)
  - Bàn 11, 14 đã đặt trước (RESERVED)
  - Order 1 (Bàn 05): 1 món COOKING, 3 món PENDING → KitchenPanel thấy
  - Order 2 (Bàn 06): 2 món READY → WaiterServicePanel thấy
  - Order 3 (Bàn 08): lượt 2 có 2 món PENDING → KitchenPanel thấy
  - Order 4, 5: COMPLETED hôm nay → StatsPanel thấy doanh thu
  - Order 6: CANCELLED → WaiterServicePanel tab "Đã hủy" thấy
  - 4 reports đang chờ xử lý

  ORACLE VERSION NOTE:
  Nếu Oracle < 18c không hỗ trợ ALTER SEQUENCE ... RESTART,
  thay bằng: DROP + CREATE lại sequence với START WITH đúng.
*/