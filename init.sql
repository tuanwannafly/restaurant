-- ════════════════════════════════════════════════════════════════════════════
-- SmartRestaurant — init.sql (PostgreSQL 16)
--
-- Chuyển đổi Oracle DDL → PostgreSQL:
--   SYSDATE / SYSTIMESTAMP  → CURRENT_TIMESTAMP
--   NVL(x, y)               → COALESCE(x, y)
--   VARCHAR2(n)             → VARCHAR(n)
--   NUMBER                  → BIGINT (ID) / NUMERIC (tiền)
--   seq_xxx.NEXTVAL         → GENERATED ALWAYS AS IDENTITY
--   ROWNUM = 1              → LIMIT 1 (cuối SELECT, không phải WHERE)
--
-- Chạy tự động khi postgres container tạo mới (mount vào initdb.d).
-- ════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
SET timezone = 'Asia/Ho_Chi_Minh';

-- ─── EXTENSIONS ─────────────────────────────────────────────────────────────
-- pgcrypto: gen_random_uuid(), crypt() — có thể dùng sau
-- citext   : case-insensitive TEXT, thay LOWER() trong WHERE email
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

-- ════════════════════════════════════════════════════════════════════════════
-- 1. ROLES
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE roles (
    id    BIGSERIAL    PRIMARY KEY,
    name  VARCHAR(50)  NOT NULL UNIQUE   -- SUPER_ADMIN, RESTAURANT_ADMIN, WAITER, CASHIER, KITCHEN, TABLET
);

-- Seed data — đúng với RbacGuard.java
INSERT INTO roles (name) VALUES
    ('SUPER_ADMIN'),
    ('RESTAURANT_ADMIN'),
    ('WAITER'),
    ('CASHIER'),
    ('KITCHEN'),
    ('TABLET');


-- ════════════════════════════════════════════════════════════════════════════
-- 2. RESTAURANTS
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE restaurants (
    restaurant_id  BIGSERIAL     PRIMARY KEY,
    name           VARCHAR(200)  NOT NULL,
    address        VARCHAR(500),
    phone          VARCHAR(20),
    email          CITEXT,                           -- CITEXT: so sánh không phân biệt hoa thường
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / INACTIVE
    logo_url       VARCHAR(500),
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ════════════════════════════════════════════════════════════════════════════
-- 3. USERS
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE users (
    user_id        BIGSERIAL    PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    email          CITEXT       NOT NULL UNIQUE,      -- UNIQUE → UserDAO.emailExists()
    password       VARCHAR(255) NOT NULL,             -- BCrypt hash (~60 chars)
    role_id        BIGINT       NOT NULL REFERENCES roles(id),
    restaurant_id  BIGINT       REFERENCES restaurants(restaurant_id),
    table_id       BIGINT,                            -- chỉ dùng với role TABLET
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_users_email          ON users(email);
CREATE INDEX idx_users_restaurant_id  ON users(restaurant_id);


-- ════════════════════════════════════════════════════════════════════════════
-- 4. EMPLOYEES
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE employees (
    employee_id    BIGSERIAL    PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    cccd           VARCHAR(20),                       -- căn cước công dân
    phone          VARCHAR(20),
    address        VARCHAR(500),
    start_date     DATE,
    role           VARCHAR(50),                       -- WAITER, CASHIER, KITCHEN, QUAN_LY
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(restaurant_id),
    user_id        BIGINT       REFERENCES users(user_id)    -- NULL nếu chưa có tài khoản
);

CREATE INDEX idx_employees_restaurant ON employees(restaurant_id);
CREATE INDEX idx_employees_user_id    ON employees(user_id);


-- ════════════════════════════════════════════════════════════════════════════
-- 5. RESTAURANT_TABLES
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE restaurant_tables (
    table_id       BIGSERIAL    PRIMARY KEY,
    table_number   VARCHAR(20)  NOT NULL,
    capacity       INT          NOT NULL DEFAULT 4,
    status         VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE / OCCUPIED / RESERVED
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(restaurant_id),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tables_restaurant ON restaurant_tables(restaurant_id);


-- ════════════════════════════════════════════════════════════════════════════
-- 6. MENUS
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE menus (
    menu_id        BIGSERIAL    PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    description    TEXT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(restaurant_id)
);


-- ════════════════════════════════════════════════════════════════════════════
-- 7. MENU_ITEMS
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE menu_items (
    item_id        BIGSERIAL      PRIMARY KEY,
    name           VARCHAR(200)   NOT NULL,
    description    TEXT,
    price          NUMERIC(15,2)  NOT NULL DEFAULT 0,
    image_url      VARCHAR(500),
    status         VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE / OUT_OF_STOCK
    menu_id        BIGINT         NOT NULL REFERENCES menus(menu_id),
    restaurant_id  BIGINT         NOT NULL REFERENCES restaurants(restaurant_id)
);

CREATE INDEX idx_menu_items_restaurant ON menu_items(restaurant_id);
CREATE INDEX idx_menu_items_menu       ON menu_items(menu_id);


-- ════════════════════════════════════════════════════════════════════════════
-- 8. ORDERS
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE orders (
    order_id          BIGSERIAL      PRIMARY KEY,
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
        -- PENDING / CONFIRMED / PREPARING / COMPLETED / CANCELLED
    total_amount      NUMERIC(15,2)  NOT NULL DEFAULT 0,
    table_id          BIGINT         REFERENCES restaurant_tables(table_id),
    restaurant_id     BIGINT         NOT NULL REFERENCES restaurants(restaurant_id),
    customer_name     VARCHAR(200),
    customer_phone    VARCHAR(20),
    cancelled_reason  TEXT,
    cancelled_at      TIMESTAMP,
    completed_at      TIMESTAMP,
    created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_restaurant  ON orders(restaurant_id);
CREATE INDEX idx_orders_table       ON orders(table_id);
CREATE INDEX idx_orders_status      ON orders(status);
CREATE INDEX idx_orders_created_at  ON orders(created_at);
CREATE INDEX idx_orders_completed   ON orders(completed_at);


-- ════════════════════════════════════════════════════════════════════════════
-- 9. ORDER_ITEMS
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE order_items (
    order_item_id  BIGSERIAL      PRIMARY KEY,
    order_id       BIGINT         NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    menu_item_id   BIGINT         NOT NULL REFERENCES menu_items(item_id),
    quantity       INT            NOT NULL DEFAULT 1,
    price          NUMERIC(15,2)  NOT NULL DEFAULT 0,  -- snapshot giá tại thời điểm đặt
    item_status    VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
        -- PENDING / COOKING / DONE / SERVED
    note           TEXT,
    assigned_to    BIGINT         REFERENCES employees(employee_id),
    round_number   INT            NOT NULL DEFAULT 1    -- phục vụ theo đợt
);

CREATE INDEX idx_order_items_order    ON order_items(order_id);
CREATE INDEX idx_order_items_item     ON order_items(menu_item_id);
CREATE INDEX idx_order_items_status   ON order_items(item_status);


-- ════════════════════════════════════════════════════════════════════════════
-- 10. REPORTS
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE reports (
    report_id    BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(300) NOT NULL,
    description  TEXT,
    report_type  VARCHAR(50),   -- COMPLAINT / INCIDENT / FEEDBACK
    severity     VARCHAR(20),   -- LOW / MEDIUM / HIGH
    status       VARCHAR(20)    NOT NULL DEFAULT 'OPEN',  -- OPEN / RESOLVED
    created_by   BIGINT         REFERENCES users(user_id),
    restaurant_id BIGINT        REFERENCES restaurants(restaurant_id),
    created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ════════════════════════════════════════════════════════════════════════════
-- 11. RESTAURANT_REQUESTS  —  đơn xin mở nhà hàng
-- Oracle dùng seq_restaurant_request_id.NEXTVAL → PostgreSQL: BIGSERIAL
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE restaurant_requests (
    request_id          BIGSERIAL     PRIMARY KEY,
    -- Thông tin chủ nhà hàng
    owner_name          VARCHAR(200)  NOT NULL,
    owner_email         CITEXT        NOT NULL,
    owner_phone         VARCHAR(20),
    owner_password_hash VARCHAR(255)  NOT NULL,
    -- Thông tin nhà hàng
    restaurant_name     VARCHAR(200)  NOT NULL,
    restaurant_address  VARCHAR(500),
    restaurant_phone    VARCHAR(20),
    restaurant_email    CITEXT,
    -- Tài liệu đính kèm
    logo_path           VARCHAR(500),
    document_path       VARCHAR(500),
    -- Trạng thái xét duyệt
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
    submitted_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by         BIGINT        REFERENCES users(user_id),
    reviewed_at         TIMESTAMP,
    reject_reason       TEXT
);


-- ════════════════════════════════════════════════════════════════════════════
-- 12. PASSWORD_RESET_TOKENS
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE password_reset_tokens (
    token_id    BIGSERIAL    PRIMARY KEY,
    token       VARCHAR(100) NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    expires_at  TIMESTAMP    NOT NULL,
    used        SMALLINT     NOT NULL DEFAULT 0    -- 0=false, 1=true (Oracle NVL(used,0)=0)
);

CREATE INDEX idx_prt_token ON password_reset_tokens(token);


-- ════════════════════════════════════════════════════════════════════════════
-- 13. SECURITY_AUDIT_LOG
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE security_audit_log (
    log_id         BIGSERIAL    PRIMARY KEY,
    action         VARCHAR(50)  NOT NULL,    -- OPEN_TABLE, LOGIN, LOGOUT, etc.
    actor_user_id  BIGINT       REFERENCES users(user_id),
    target_id      BIGINT,
    session_token  VARCHAR(200),
    op_token       VARCHAR(200),
    result         VARCHAR(20),              -- SUCCESS / FAILURE
    detail         TEXT,
    logged_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_actor    ON security_audit_log(actor_user_id);
CREATE INDEX idx_audit_logged   ON security_audit_log(logged_at);


-- ════════════════════════════════════════════════════════════════════════════
-- 14. SEED DATA — tài khoản SUPER_ADMIN mặc định
--
-- Password: "admin123" đã BCrypt (cost=10)
-- Đổi mật khẩu ngay sau khi chạy production!
-- ════════════════════════════════════════════════════════════════════════════
INSERT INTO users (name, email, password, role_id, status)
VALUES (
    'Super Admin',
    'admin@smartrestaurant.vn',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  -- "admin123"
    (SELECT id FROM roles WHERE name = 'SUPER_ADMIN'),
    'ACTIVE'
);


-- ════════════════════════════════════════════════════════════════════════════
-- 15. FUNCTION — tương đương TRUNC(date) của Oracle
--
-- Oracle: TRUNC(SYSDATE)          = cắt giờ, chỉ lấy ngày
-- PostgreSQL: DATE_TRUNC('day', NOW())
--
-- Tạo hàm tiện lợi để sau này nếu cần dùng trong SQL:
-- ════════════════════════════════════════════════════════════════════════════
CREATE OR REPLACE FUNCTION trunc_day(ts TIMESTAMP)
RETURNS DATE AS $$
    SELECT CAST(DATE_TRUNC('day', ts) AS DATE);
$$ LANGUAGE SQL IMMUTABLE;


-- Hoàn thành
\echo '✅ SmartRestaurant schema initialized successfully'