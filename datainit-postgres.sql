-- ════════════════════════════════════════════════════════════════════════════
-- SmartRestaurant — datainit-postgres.sql  (PostgreSQL 16)
--
-- Chuyển đổi Oracle datainit.sql → PostgreSQL:
--   NUMBER / VARCHAR2         → BIGINT / BIGSERIAL / VARCHAR
--   DEFAULT SYSTIMESTAMP      → DEFAULT NOW()
--   sequence + BEFORE INSERT  → BIGSERIAL (GENERATED ALWAYS AS IDENTITY)
--   ROWNUM = 1                → LIMIT 1
--   NVL(x, y)                 → COALESCE(x, y)
--   SYSDATE / SYSTIMESTAMP    → NOW() / CURRENT_DATE
--   INTERVAL 'n' UNIT         → INTERVAL 'n units'
--   ALTER SEQUENCE RESTART    → ALTER SEQUENCE RESTART WITH n
--   FROM DUAL                 → (removed — PostgreSQL không cần)
--   TRUNC(date)               → DATE_TRUNC('day', date) hoặc CURRENT_DATE
--
-- Dữ liệu seed giống datainit.sql — tài khoản test: mật khẩu 123456
-- ════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
SET timezone = 'Asia/Ho_Chi_Minh';

-- Extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;


-- ════════════════════════════════════════════════════════════════════════════
-- PHẦN 1: XOÁ BẢNG CŨ (ngược thứ tự FK)
-- ════════════════════════════════════════════════════════════════════════════
DROP TABLE IF EXISTS operation_tokens       CASCADE;
DROP TABLE IF EXISTS security_audit_log     CASCADE;
DROP TABLE IF EXISTS password_reset_tokens  CASCADE;
DROP TABLE IF EXISTS refresh_tokens         CASCADE;
DROP TABLE IF EXISTS session_tokens         CASCADE;
DROP TABLE IF EXISTS reports                CASCADE;
DROP TABLE IF EXISTS order_items            CASCADE;
DROP TABLE IF EXISTS orders                 CASCADE;
DROP TABLE IF EXISTS restaurant_tables      CASCADE;
DROP TABLE IF EXISTS menu_items             CASCADE;
DROP TABLE IF EXISTS menus                  CASCADE;
DROP TABLE IF EXISTS employees              CASCADE;
DROP TABLE IF EXISTS users                  CASCADE;
DROP TABLE IF EXISTS restaurant_requests    CASCADE;
DROP TABLE IF EXISTS restaurants            CASCADE;
DROP TABLE IF EXISTS roles                  CASCADE;


-- ════════════════════════════════════════════════════════════════════════════
-- PHẦN 2: TẠO BẢNG
-- ════════════════════════════════════════════════════════════════════════════

-- ── 2.1  ROLES ───────────────────────────────────────────────────────────────
CREATE TABLE roles (
    id    BIGSERIAL    PRIMARY KEY,
    name  VARCHAR(50)  NOT NULL UNIQUE
);

-- ── 2.2  RESTAURANTS ─────────────────────────────────────────────────────────
CREATE TABLE restaurants (
    restaurant_id  BIGSERIAL     PRIMARY KEY,
    name           VARCHAR(200)  NOT NULL,
    address        VARCHAR(500),
    phone          VARCHAR(20),
    email          CITEXT,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE','INACTIVE')),
    logo_url       VARCHAR(500),
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- ── 2.3  USERS ───────────────────────────────────────────────────────────────
CREATE TABLE users (
    user_id        BIGSERIAL    PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    email          CITEXT       NOT NULL UNIQUE,
    password       VARCHAR(255) NOT NULL,   -- BCrypt hash
    role_id        BIGINT       NOT NULL REFERENCES roles(id),
    restaurant_id  BIGINT       REFERENCES restaurants(restaurant_id),
    table_id       BIGINT,                  -- chỉ dùng với role TABLET
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE','INACTIVE','LOCKED')),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email          ON users(email);
CREATE INDEX idx_users_restaurant_id  ON users(restaurant_id);

-- ── 2.4  EMPLOYEES ───────────────────────────────────────────────────────────
CREATE TABLE employees (
    employee_id    BIGSERIAL    PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    cccd           VARCHAR(20),
    phone          VARCHAR(20),
    address        VARCHAR(500),
    start_date     DATE,
    role           VARCHAR(50)  DEFAULT 'PHUC_VU'
                       CHECK (role IN ('PHUC_VU','DAU_BEP','THU_NGAN','QUAN_LY')),
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(restaurant_id),
    user_id        BIGINT       REFERENCES users(user_id)
);
CREATE INDEX idx_employees_restaurant ON employees(restaurant_id);
CREATE INDEX idx_employees_user_id    ON employees(user_id);

-- ── 2.5  MENUS ───────────────────────────────────────────────────────────────
CREATE TABLE menus (
    menu_id        BIGSERIAL    PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    description    TEXT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(restaurant_id),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── 2.6  MENU_ITEMS ──────────────────────────────────────────────────────────
CREATE TABLE menu_items (
    item_id        BIGSERIAL      PRIMARY KEY,
    name           VARCHAR(200)   NOT NULL,
    description    TEXT,
    price          NUMERIC(15,2)  NOT NULL DEFAULT 0,
    image_url      VARCHAR(500),
    status         VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE'
                       CHECK (status IN ('AVAILABLE','UNAVAILABLE','HIDDEN')),
    menu_id        BIGINT         NOT NULL REFERENCES menus(menu_id),
    restaurant_id  BIGINT         NOT NULL REFERENCES restaurants(restaurant_id)
);
CREATE INDEX idx_menu_items_restaurant ON menu_items(restaurant_id);
CREATE INDEX idx_menu_items_menu       ON menu_items(menu_id);

-- ── 2.7  RESTAURANT_TABLES ───────────────────────────────────────────────────
CREATE TABLE restaurant_tables (
    table_id       BIGSERIAL    PRIMARY KEY,
    table_number   VARCHAR(50)  NOT NULL,
    capacity       INT          NOT NULL DEFAULT 4,
    status         VARCHAR(30)  NOT NULL DEFAULT 'AVAILABLE'
                       CHECK (status IN ('AVAILABLE','OCCUPIED','RESERVED','DIRTY','CLEANING','OUT_OF_SERVICE')),
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(restaurant_id),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tables_restaurant ON restaurant_tables(restaurant_id);

-- ── 2.8  ORDERS ──────────────────────────────────────────────────────────────
CREATE TABLE orders (
    order_id          BIGSERIAL      PRIMARY KEY,
    status            VARCHAR(30)    NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN (
                              'PENDING','ACCEPTED','COOKING','READY','DELIVERING',
                              'DELIVERED','COMPLETED','CANCELLED','PAYMENT_REQUESTED','IN_PROGRESS'
                          )),
    total_amount      NUMERIC(15,2)  NOT NULL DEFAULT 0,
    table_id          BIGINT         REFERENCES restaurant_tables(table_id),
    restaurant_id     BIGINT         NOT NULL REFERENCES restaurants(restaurant_id),
    customer_name     VARCHAR(200),
    customer_phone    VARCHAR(20),
    payment_method    VARCHAR(20),   -- thêm sẵn — OrderDAO tự ALTER nếu thiếu
    cancelled_reason  TEXT,
    cancelled_at      TIMESTAMP,
    completed_at      TIMESTAMP,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_orders_restaurant  ON orders(restaurant_id);
CREATE INDEX idx_orders_table       ON orders(table_id);
CREATE INDEX idx_orders_status      ON orders(status);
CREATE INDEX idx_orders_created_at  ON orders(created_at);
CREATE INDEX idx_orders_completed   ON orders(completed_at);

-- ── 2.9  ORDER_ITEMS ─────────────────────────────────────────────────────────
CREATE TABLE order_items (
    order_item_id  BIGSERIAL      PRIMARY KEY,
    order_id       BIGINT         NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    menu_item_id   BIGINT         NOT NULL REFERENCES menu_items(item_id),
    quantity       INT            NOT NULL DEFAULT 1,
    price          NUMERIC(15,2)  NOT NULL DEFAULT 0,
    item_status    VARCHAR(30)    NOT NULL DEFAULT 'PENDING'
                       CHECK (item_status IN (
                           'PENDING','ACCEPTED','COOKING','READY','DELIVERING','DELIVERED','CANCELLED'
                       )),
    round_number   INT            NOT NULL DEFAULT 1,
    note           TEXT,
    assigned_to    VARCHAR(200),  -- tên hoặc ID nhân viên phụ trách
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_order_items_order   ON order_items(order_id);
CREATE INDEX idx_order_items_item    ON order_items(menu_item_id);
CREATE INDEX idx_order_items_status  ON order_items(item_status);

-- ── 2.10  REPORTS ────────────────────────────────────────────────────────────
CREATE TABLE reports (
    report_id      BIGSERIAL    PRIMARY KEY,
    title          VARCHAR(300) NOT NULL,
    description    TEXT,
    report_type    VARCHAR(20)  DEFAULT 'INCIDENT'
                       CHECK (report_type IN ('INCIDENT','MAINTENANCE','FEEDBACK')),
    severity       VARCHAR(20)  DEFAULT 'LOW'
                       CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status         VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
                       CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
    created_by     BIGINT       NOT NULL REFERENCES users(user_id),
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(restaurant_id),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    resolved_at    TIMESTAMP
);
CREATE INDEX idx_reports_restaurant ON reports(restaurant_id);

-- ── 2.11  SESSION_TOKENS ─────────────────────────────────────────────────────
-- token_id là UUID string (VARCHAR 36) — nhất quán với TokenService.java
CREATE TABLE session_tokens (
    token_id    VARCHAR(36)   PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(user_id),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMP     NOT NULL,
    is_active   SMALLINT      NOT NULL DEFAULT 1,
    ip_address  VARCHAR(50)
);
CREATE INDEX idx_st_user_active ON session_tokens(user_id, is_active);

-- ── 2.12  REFRESH_TOKENS ─────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    token        VARCHAR(200)  PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users(user_id),
    device_name  VARCHAR(200),
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMP     NOT NULL,
    revoked      SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_rt_user ON refresh_tokens(user_id, revoked);

-- ── 2.13  PASSWORD_RESET_TOKENS ──────────────────────────────────────────────
CREATE TABLE password_reset_tokens (
    token       VARCHAR(200)  PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    expires_at  TIMESTAMP     NOT NULL,
    used        SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_prt_token ON password_reset_tokens(token);

-- ── 2.14  SECURITY_AUDIT_LOG ─────────────────────────────────────────────────
CREATE TABLE security_audit_log (
    log_id         BIGSERIAL    PRIMARY KEY,
    action         VARCHAR(50)  NOT NULL,
    actor_user_id  BIGINT       REFERENCES users(user_id),
    target_id      BIGINT,
    session_token  VARCHAR(36),
    op_token       VARCHAR(16),
    result         VARCHAR(10),
    detail         TEXT,
    logged_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_actor      ON security_audit_log(actor_user_id);
CREATE INDEX idx_audit_action     ON security_audit_log(action);
CREATE INDEX idx_audit_logged_at  ON security_audit_log(logged_at);

-- ── 2.15  OPERATION_TOKENS ───────────────────────────────────────────────────
CREATE TABLE operation_tokens (
    token           VARCHAR(16)  PRIMARY KEY,
    operation_type  VARCHAR(50)  NOT NULL,
    actor_user_id   BIGINT       NOT NULL,
    target_id       BIGINT       NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP    NOT NULL,
    used            SMALLINT     NOT NULL DEFAULT 0
);

-- ── 2.16  RESTAURANT_REQUESTS ────────────────────────────────────────────────
CREATE TABLE restaurant_requests (
    request_id          BIGSERIAL     PRIMARY KEY,
    owner_name          VARCHAR(200)  NOT NULL,
    owner_email         CITEXT        NOT NULL,
    owner_phone         VARCHAR(20),
    owner_password_hash VARCHAR(255)  NOT NULL,
    restaurant_name     VARCHAR(200)  NOT NULL,
    restaurant_address  VARCHAR(500),
    restaurant_phone    VARCHAR(20),
    restaurant_email    CITEXT,
    logo_path           VARCHAR(500),
    document_path       VARCHAR(500),
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    submitted_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    reviewed_by         BIGINT        REFERENCES users(user_id),
    reviewed_at         TIMESTAMP,
    reject_reason       TEXT
);


-- ════════════════════════════════════════════════════════════════════════════
-- PHẦN 3: DỮ LIỆU SEED
-- ════════════════════════════════════════════════════════════════════════════

-- ── 3.1  ROLES ───────────────────────────────────────────────────────────────
INSERT INTO roles (id, name) VALUES
    (1, 'SUPER_ADMIN'),
    (2, 'RESTAURANT_ADMIN'),
    (3, 'WAITER'),
    (4, 'CHEF'),
    (5, 'CASHIER'),
    (6, 'KITCHEN'),
    (7, 'TABLET');
-- Reset sequence to continue after manual IDs
SELECT setval('roles_id_seq', 10);

-- ── 3.2  RESTAURANTS ─────────────────────────────────────────────────────────
INSERT INTO restaurants (restaurant_id, name, address, phone, email, status, created_at)
VALUES
    (1, 'SmartRestaurant Trung Tâm',
        '123 Nguyễn Huệ, Quận 1, TP.HCM', '028-3823-9999',
        'contact@smartrestaurant.vn', 'ACTIVE', NOW()),

    (2, 'SmartRestaurant Phú Mỹ Hưng',
        '456 Nguyễn Lương Bằng, Quận 7, TP.HCM', '028-5413-8888',
        'pmh@smartrestaurant.vn', 'ACTIVE', NOW()),

    (3, 'SmartRestaurant Thủ Đức',
        '789 Võ Văn Ngân, TP.Thủ Đức, TP.HCM', '028-7301-7777',
        'thuduc@smartrestaurant.vn', 'INACTIVE', NOW());
SELECT setval('restaurants_restaurant_id_seq', 10);

-- ── 3.3  USERS ───────────────────────────────────────────────────────────────
-- Mật khẩu tất cả: 123456  (BCrypt $2a$10$...)
INSERT INTO users (user_id, name, email, password, role_id, restaurant_id, status)
VALUES
    (1, 'Super Admin',
        'superadmin@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        1, NULL, 'ACTIVE'),

    (2, 'Nguyễn Văn Admin',
        'admin1@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        2, 1, 'ACTIVE'),

    (3, 'Trần Thị Admin',
        'admin2@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        2, 2, 'ACTIVE'),

    (4, 'Lê Thị Phục Vụ',
        'waiter1@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        3, 1, 'ACTIVE'),

    (5, 'Phạm Văn Bếp',
        'chef1@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        4, 1, 'ACTIVE'),

    (6, 'Hoàng Thị Thu Ngân',
        'cashier1@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        5, 1, 'ACTIVE'),

    (7, 'Đỗ Văn Phục Vụ Hai',
        'waiter2@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        3, 1, 'ACTIVE'),

    (8, 'Vũ Thị Bếp Hai',
        'chef2@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        4, 2, 'ACTIVE'),

    (9, 'Bùi Văn Thu Ngân Hai',
        'cashier2@smartrestaurant.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        5, 2, 'ACTIVE');
SELECT setval('users_user_id_seq', 20);

-- ── 3.4  EMPLOYEES ───────────────────────────────────────────────────────────
INSERT INTO employees (employee_id, name, cccd, phone, address, start_date, role, restaurant_id, user_id)
VALUES
    (1, 'Nguyễn Văn Admin',     '001234567890', '0901234567', 'Q.1, TP.HCM',          '2022-01-15', 'QUAN_LY',  1, 2),
    (2, 'Lê Thị Phục Vụ',      '001234567891', '0912345678', 'Q.3, TP.HCM',          '2023-03-01', 'PHUC_VU',  1, 4),
    (3, 'Phạm Văn Bếp',        '001234567892', '0923456789', 'Q.5, TP.HCM',          '2022-06-15', 'DAU_BEP',  1, 5),
    (4, 'Hoàng Thị Thu Ngân',  '001234567893', '0934567890', 'Q.7, TP.HCM',          '2023-01-10', 'THU_NGAN', 1, 6),
    (5, 'Đỗ Văn Phục Vụ Hai',  '001234567894', '0945678901', 'Q.8, TP.HCM',          '2023-07-20', 'PHUC_VU',  1, 7),
    -- Nhân viên chưa có tài khoản — để test tạo tài khoản
    (6, 'Nguyễn Thị Mới',      '001234567895', '0956789012', 'Q.Bình Thạnh, TP.HCM', '2024-01-05', 'PHUC_VU',  1, NULL),
    -- Nhà hàng 2
    (7, 'Trần Thị Admin',       '002345678901', '0967890123', 'Q.7, TP.HCM',          '2022-02-01', 'QUAN_LY',  2, 3),
    (8, 'Vũ Thị Bếp Hai',      '002345678902', '0978901234', 'Q.7, TP.HCM',          '2023-05-15', 'DAU_BEP',  2, 8),
    (9, 'Bùi Văn Thu Ngân Hai','002345678903', '0989012345', 'Q.7, TP.HCM',          '2023-09-01', 'THU_NGAN', 2, 9);
SELECT setval('employees_employee_id_seq', 20);

-- ── 3.5  MENUS ───────────────────────────────────────────────────────────────
INSERT INTO menus (menu_id, name, description, status, restaurant_id) VALUES
    (1, 'Hải sản',    'Các món từ hải sản tươi',       'ACTIVE', 1),
    (2, 'Thịt',       'Các món thịt nướng & xào',      'ACTIVE', 1),
    (3, 'Cơm',        'Cơm trắng & cơm chiên',         'ACTIVE', 1),
    (4, 'Phở',        'Phở bò & phở gà',               'ACTIVE', 1),
    (5, 'Đồ uống',    'Nước ép, sinh tố, trà',         'ACTIVE', 1),
    (6, 'Tráng miệng','Chè, bánh, kem',                'ACTIVE', 1),
    -- Nhà hàng 2
    (7, 'Hải sản',    'Hải sản tươi sống',             'ACTIVE', 2),
    (8, 'Đồ uống',    'Thức uống các loại',            'ACTIVE', 2);
SELECT setval('menus_menu_id_seq', 20);

-- ── 3.6  MENU_ITEMS ──────────────────────────────────────────────────────────
-- Nhà hàng 1 — Hải sản
INSERT INTO menu_items (item_id, name, description, price, status, menu_id, restaurant_id) VALUES
    (1,  'Tôm sú nướng muối ớt',  'Tôm sú tươi nướng với muối ớt đặc biệt',      285000, 'AVAILABLE', 1, 1),
    (2,  'Cua rang me',            'Cua biển rang me chua ngọt',                   320000, 'AVAILABLE', 1, 1),
    (3,  'Mực nướng sa tế',       'Mực ống nướng sa tế cay nồng',                 195000, 'AVAILABLE', 1, 1),
    (4,  'Nghêu hấp sả',          'Nghêu tươi hấp với sả và gừng',                125000, 'AVAILABLE', 1, 1),
    (5,  'Cá lóc nướng trui',     'Cá lóc đồng nướng trui trọn vẹn',             220000, 'AVAILABLE', 1, 1),
-- Nhà hàng 1 — Thịt
    (6,  'Bò lúc lắc',            'Thịt bò Mỹ xào lúc lắc kiểu Pháp',            265000, 'AVAILABLE', 2, 1),
    (7,  'Sườn nướng BBQ',        'Sườn heo nướng sốt BBQ Mỹ',                    185000, 'AVAILABLE', 2, 1),
    (8,  'Gà nướng mật ong',      'Đùi gà nướng mật ong vàng ươm',               155000, 'AVAILABLE', 2, 1),
    (9,  'Heo quay da giòn',      'Heo quay da giòn tan, thịt mềm',              175000, 'AVAILABLE', 2, 1),
-- Nhà hàng 1 — Cơm
    (10, 'Cơm chiên dương châu',  'Cơm chiên kiểu Dương Châu truyền thống',        75000, 'AVAILABLE', 3, 1),
    (11, 'Cơm tấm sườn bì',      'Cơm tấm đặc sản Sài Gòn',                       95000, 'AVAILABLE', 3, 1),
    (12, 'Cơm gà Hải Nam',       'Cơm gà hấp kiểu Hải Nam với nước sốt gừng',    115000, 'AVAILABLE', 3, 1),
-- Nhà hàng 1 — Phở
    (13, 'Phở bò đặc biệt',      'Phở bò tái chín gân gầu, nước dùng đậm đà',    85000, 'AVAILABLE', 4, 1),
    (14, 'Phở gà',               'Phở gà ta hầm nguyên con, thịt mềm ngọt',       75000, 'AVAILABLE', 4, 1),
    (15, 'Hủ tiếu Nam Vang',     'Hủ tiếu Nam Vang truyền thống',                  80000, 'AVAILABLE', 4, 1),
-- Nhà hàng 1 — Đồ uống
    (16, 'Nước ép cam tươi',     'Cam vắt tươi 100%, thêm đá',                    45000, 'AVAILABLE', 5, 1),
    (17, 'Sinh tố bơ',           'Sinh tố bơ sáp mịn với sữa đặc',               55000, 'AVAILABLE', 5, 1),
    (18, 'Trà đào cam sả',       'Trà đào thơm mát với cam và sả',                45000, 'AVAILABLE', 5, 1),
    (19, 'Coca Cola',            'Coca Cola lon lạnh',                             25000, 'AVAILABLE', 5, 1),
    (20, 'Bia Tiger chai',       'Bia Tiger chai 330ml lạnh',                      35000, 'AVAILABLE', 5, 1),
-- Nhà hàng 1 — Tráng miệng
    (21, 'Chè khúc bạch',        'Chè khúc bạch thạch mát lạnh',                  55000, 'AVAILABLE', 6, 1),
    (22, 'Kem dừa',              'Kem dừa Thái Lan béo ngậy',                      45000, 'AVAILABLE', 6, 1),
-- Nhà hàng 2
    (23, 'Tôm hùm nướng',        'Tôm hùm nướng phô mai béo ngậy',               650000, 'AVAILABLE', 7, 2),
    (24, 'Ghẹ hấp bia',          'Ghẹ biển tươi hấp bia',                         380000, 'AVAILABLE', 7, 2),
    (25, 'Nước dừa tươi',        'Dừa xiêm nguyên trái',                           45000, 'AVAILABLE', 8, 2);
SELECT setval('menu_items_item_id_seq', 50);

-- ── 3.7  RESTAURANT_TABLES ───────────────────────────────────────────────────
-- Nhà hàng 1 (15 bàn)
INSERT INTO restaurant_tables (table_id, table_number, capacity, status, restaurant_id) VALUES
    (1,  'Bàn 01',   2, 'AVAILABLE', 1),
    (2,  'Bàn 02',   2, 'AVAILABLE', 1),
    (3,  'Bàn 03',   4, 'AVAILABLE', 1),
    (4,  'Bàn 04',   4, 'AVAILABLE', 1),
    (5,  'Bàn 05',   4, 'OCCUPIED',  1),
    (6,  'Bàn 06',   4, 'OCCUPIED',  1),
    (7,  'Bàn 07',   6, 'AVAILABLE', 1),
    (8,  'Bàn 08',   6, 'OCCUPIED',  1),
    (9,  'Bàn 09',   6, 'DIRTY',     1),
    (10, 'Bàn 10',   8, 'AVAILABLE', 1),
    (11, 'Bàn 11',   8, 'RESERVED',  1),
    (12, 'Bàn 12',   8, 'AVAILABLE', 1),
    (13, 'VIP 01',  10, 'AVAILABLE', 1),
    (14, 'VIP 02',  12, 'RESERVED',  1),
    (15, 'Sân vườn',20, 'AVAILABLE', 1),
-- Nhà hàng 2 (8 bàn)
    (16, 'Bàn A1',   4, 'AVAILABLE', 2),
    (17, 'Bàn A2',   4, 'OCCUPIED',  2),
    (18, 'Bàn A3',   6, 'AVAILABLE', 2),
    (19, 'Bàn A4',   6, 'DIRTY',     2),
    (20, 'Bàn B1',   8, 'AVAILABLE', 2),
    (21, 'Bàn B2',   8, 'AVAILABLE', 2),
    (22, 'VIP A',   12, 'AVAILABLE', 2),
    (23, 'VIP B',   12, 'RESERVED',  2);
SELECT setval('restaurant_tables_table_id_seq', 50);

-- ── 3.8  ORDERS ──────────────────────────────────────────────────────────────
-- PostgreSQL: interval syntax đúng → INTERVAL '45 minutes'  (không phải '45' MINUTE)
INSERT INTO orders (order_id, status, total_amount, table_id, restaurant_id,
                    customer_name, customer_phone, created_at) VALUES
    (1, 'PENDING', 455000, 5, 1, NULL, NULL, NOW() - INTERVAL '45 minutes'),
    (2, 'PENDING', 320000, 6, 1, 'Nguyễn Văn A', '0901234999', NOW() - INTERVAL '30 minutes'),
    (3, 'PENDING', 780000, 8, 1, NULL, NULL, NOW() - INTERVAL '60 minutes'),
    (7, 'PENDING', 1030000, 17, 2, NULL, NULL, NOW() - INTERVAL '20 minutes');

INSERT INTO orders (order_id, status, total_amount, table_id, restaurant_id,
                    customer_name, customer_phone, created_at, completed_at) VALUES
    (4, 'COMPLETED', 550000, 3, 1, NULL, NULL,
        NOW() - INTERVAL '3 hours', NOW() - INTERVAL '2 hours'),
    (5, 'COMPLETED', 890000, 4, 1, NULL, NULL,
        NOW() - INTERVAL '4 hours', NOW() - INTERVAL '3 hours');

INSERT INTO orders (order_id, status, total_amount, table_id, restaurant_id,
                    customer_name, customer_phone, created_at) VALUES
    (6, 'CANCELLED', 185000, 2, 1, NULL, NULL, NOW() - INTERVAL '2 hours');
SELECT setval('orders_order_id_seq', 20);

-- ── 3.9  ORDER_ITEMS ─────────────────────────────────────────────────────────
-- Order 1 — Bàn 05 (mix COOKING / PENDING)
INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, item_status, round_number, created_at) VALUES
    (1,  1, 1,  1, 285000, 'COOKING', 1, NOW() - INTERVAL '44 minutes'),
    (2,  1, 18, 2, 45000,  'PENDING', 1, NOW() - INTERVAL '44 minutes'),
    (3,  1, 22, 1, 45000,  'PENDING', 1, NOW() - INTERVAL '44 minutes'),
    (4,  1, 16, 2, 45000,  'PENDING', 2, NOW() - INTERVAL '15 minutes'),
-- Order 2 — Bàn 06 (READY — nấu xong, chờ phục vụ)
    (5,  2, 6,  1, 265000, 'READY',     1, NOW() - INTERVAL '29 minutes'),
    (6,  2, 17, 1, 55000,  'READY',     1, NOW() - INTERVAL '29 minutes'),
-- Order 3 — Bàn 08 (mix trạng thái, lượt 2 đang nấu)
    (7,  3, 2,  1, 320000, 'DELIVERED', 1, NOW() - INTERVAL '58 minutes'),
    (8,  3, 10, 2, 75000,  'DELIVERED', 1, NOW() - INTERVAL '58 minutes'),
    (9,  3, 19, 3, 25000,  'DELIVERED', 1, NOW() - INTERVAL '58 minutes'),
    (10, 3, 7,  2, 185000, 'PENDING',   2, NOW() - INTERVAL '10 minutes'),
    (11, 3, 21, 2, 55000,  'PENDING',   2, NOW() - INTERVAL '10 minutes'),
-- Order 4 — Completed hôm nay
    (12, 4, 13, 2, 85000,  'DELIVERED', 1, NOW() - INTERVAL '3 hours'),
    (13, 4, 14, 1, 75000,  'DELIVERED', 1, NOW() - INTERVAL '3 hours'),
    (14, 4, 18, 3, 45000,  'DELIVERED', 1, NOW() - INTERVAL '3 hours'),
    (15, 4, 20, 4, 35000,  'DELIVERED', 1, NOW() - INTERVAL '3 hours'),
-- Order 5 — Completed hôm nay
    (16, 5, 1,  2, 285000, 'DELIVERED', 1, NOW() - INTERVAL '4 hours'),
    (17, 5, 6,  1, 265000, 'DELIVERED', 1, NOW() - INTERVAL '4 hours'),
    (18, 5, 20, 6, 35000,  'DELIVERED', 1, NOW() - INTERVAL '4 hours'),
    (19, 5, 16, 2, 45000,  'DELIVERED', 1, NOW() - INTERVAL '4 hours'),
-- Order 6 — Cancelled
    (20, 6, 8,  1, 155000, 'CANCELLED', 1, NOW() - INTERVAL '2 hours'),
    (21, 6, 19, 2, 25000,  'CANCELLED', 1, NOW() - INTERVAL '2 hours'),
-- Order 7 — Nhà hàng 2 đang nấu
    (22, 7, 23, 2, 650000, 'COOKING',   1, NOW() - INTERVAL '18 minutes'),
    (23, 7, 25, 3, 45000,  'PENDING',   1, NOW() - INTERVAL '18 minutes');
SELECT setval('order_items_order_item_id_seq', 50);

-- ── 3.10  REPORTS ────────────────────────────────────────────────────────────
INSERT INTO reports (report_id, title, description, report_type, severity, status,
                     created_by, restaurant_id, created_at)
VALUES
    (1, 'Điều hoà phòng VIP hư',
        'Điều hoà phòng VIP 02 không lạnh, cần sửa gấp',
        'MAINTENANCE', 'HIGH', 'OPEN', 4, 1, NOW() - INTERVAL '2 hours'),

    (2, 'Khách phàn nàn món ăn chậm',
        'Bàn 08 chờ hơn 45 phút chưa có món',
        'INCIDENT', 'MEDIUM', 'IN_PROGRESS', 4, 1, NOW() - INTERVAL '1 hour'),

    (3, 'Thiếu tương ớt',
        'Hết tương ớt Chin-su, cần nhập thêm',
        'FEEDBACK', 'LOW', 'RESOLVED', 5, 1, NOW() - INTERVAL '1 day'),

    (4, 'Máy tính tiền lỗi',
        'Máy POS bàn thu ngân báo lỗi kết nối mạng',
        'INCIDENT', 'CRITICAL', 'OPEN', 6, 1, NOW() - INTERVAL '30 minutes');
SELECT setval('reports_report_id_seq', 10);


-- ════════════════════════════════════════════════════════════════════════════
-- PHẦN 4: FUNCTION tiện lợi (tương đương TRUNC của Oracle)
-- ════════════════════════════════════════════════════════════════════════════
CREATE OR REPLACE FUNCTION trunc_day(ts TIMESTAMP)
RETURNS DATE AS $$
    SELECT CAST(DATE_TRUNC('day', ts) AS DATE);
$$ LANGUAGE SQL IMMUTABLE;


-- ════════════════════════════════════════════════════════════════════════════
-- PHẦN 5: KIỂM TRA DỮ LIỆU
-- ════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
    r RECORD;
BEGIN
    RAISE NOTICE '=== KIỂM TRA DỮ LIỆU ===';
    FOR r IN
        SELECT 'roles'            AS tbl, COUNT(*) AS cnt FROM roles
        UNION ALL SELECT 'restaurants',  COUNT(*) FROM restaurants
        UNION ALL SELECT 'users',        COUNT(*) FROM users
        UNION ALL SELECT 'employees',    COUNT(*) FROM employees
        UNION ALL SELECT 'menus',        COUNT(*) FROM menus
        UNION ALL SELECT 'menu_items',   COUNT(*) FROM menu_items
        UNION ALL SELECT 'rest_tables',  COUNT(*) FROM restaurant_tables
        UNION ALL SELECT 'orders',       COUNT(*) FROM orders
        UNION ALL SELECT 'order_items',  COUNT(*) FROM order_items
        UNION ALL SELECT 'reports',      COUNT(*) FROM reports
    LOOP
        RAISE NOTICE '%-20s %s rows', r.tbl, r.cnt;
    END LOOP;
END $$;


-- ════════════════════════════════════════════════════════════════════════════
-- GHI CHÚ TÀI KHOẢN TEST
-- ════════════════════════════════════════════════════════════════════════════
/*
  ┌─────────────────────────────────────────────────────────────────────────┐
  │                       TÀI KHOẢN TEST                                   │
  │  Mật khẩu: 123456 (BCrypt $2a$10$...)                                  │
  ├──────────────────────────────────┬──────────────────┬─────────────────┤
  │ Email                            │ Role             │ Nhà hàng        │
  ├──────────────────────────────────┼──────────────────┼─────────────────┤
  │ superadmin@smartrestaurant.vn    │ SUPER_ADMIN      │ (tất cả)        │
  │ admin1@smartrestaurant.vn        │ RESTAURANT_ADMIN │ Trung Tâm       │
  │ admin2@smartrestaurant.vn        │ RESTAURANT_ADMIN │ Phú Mỹ Hưng     │
  │ waiter1@smartrestaurant.vn       │ WAITER           │ Trung Tâm       │
  │ chef1@smartrestaurant.vn         │ CHEF             │ Trung Tâm       │
  │ cashier1@smartrestaurant.vn      │ CASHIER          │ Trung Tâm       │
  │ waiter2@smartrestaurant.vn       │ WAITER           │ Trung Tâm       │
  │ chef2@smartrestaurant.vn         │ CHEF             │ Phú Mỹ Hưng     │
  │ cashier2@smartrestaurant.vn      │ CASHIER          │ Phú Mỹ Hưng     │
  └──────────────────────────────────┴──────────────────┴─────────────────┘

  TRẠNG THÁI MÔ PHỎNG:
  • Bàn 05, 06, 08 đang có khách (OCCUPIED + order PENDING)
  • Bàn 09 vừa xong, cần dọn (DIRTY)
  • Bàn 11, 14 đã đặt trước (RESERVED)
  • Order 1 (Bàn 05): 1 món COOKING + 3 PENDING → KitchenPanel thấy
  • Order 2 (Bàn 06): 2 món READY → WaiterServicePanel thấy
  • Order 3 (Bàn 08): lượt 2 có 2 món PENDING → KitchenPanel thấy
  • Order 4, 5: COMPLETED hôm nay → StatsPanel thấy doanh thu
  • Order 6: CANCELLED → tab Đã hủy thấy
  • 4 reports đang chờ xử lý
*/

\echo '✅ SmartRestaurant PostgreSQL schema + seed data initialized'