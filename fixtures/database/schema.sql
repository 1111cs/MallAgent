-- Huawei Mall Mock schema, DDL v1.0
-- PostgreSQL 14+; create in an explicitly selected test database only.
-- No DROP, TRUNCATE, or modification of Ragent public tables.
BEGIN;

CREATE SCHEMA huawei_mall_mock;
SET LOCAL search_path = huawei_mall_mock, pg_catalog;

CREATE TABLE t_user (
    id              varchar(64) PRIMARY KEY,
    nickname        varchar(100) NOT NULL,
    phone           varchar(20),
    region          varchar(32) NOT NULL DEFAULT 'CN_DEFAULT',
    created_at      timestamptz NOT NULL,
    CONSTRAINT ck_user_region CHECK (region IN ('CN_DEFAULT','BEIJING','SHANGHAI','GUANGZHOU')),
    CONSTRAINT ck_user_phone CHECK (phone IS NULL OR phone ~ '^1[3-9][0-9]{9}$')
);

CREATE TABLE t_product (
    id              varchar(64) PRIMARY KEY,
    product_name    varchar(150) NOT NULL,
    category        varchar(32) NOT NULL,
    series          varchar(100) NOT NULL,
    model           varchar(150) NOT NULL,
    current_price   numeric(12,2) NOT NULL,
    original_price  numeric(12,2) NOT NULL,
    status          varchar(24) NOT NULL,
    spec_json       jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT ck_product_category CHECK (category IN ('phone','tablet','laptop','wearable','headphone','router')),
    CONSTRAINT ck_product_price CHECK (current_price >= 0 AND original_price >= 0),
    CONSTRAINT ck_product_status CHECK (status IN ('ACTIVE','DISCONTINUED')),
    CONSTRAINT ck_product_spec CHECK (jsonb_typeof(spec_json) = 'object'),
    CONSTRAINT ck_product_time CHECK (updated_at >= created_at)
);
CREATE INDEX idx_product_category_price ON t_product (category, current_price);
CREATE INDEX idx_product_model ON t_product (model);

CREATE TABLE t_order (
    id                      varchar(64) PRIMARY KEY,
    order_no                varchar(64) NOT NULL UNIQUE,
    user_id                 varchar(64) NOT NULL REFERENCES t_user(id),
    status                  varchar(32) NOT NULL,
    total_amount            numeric(12,2) NOT NULL,
    receiver_region         varchar(32) NOT NULL,
    receiver_address_masked varchar(250) NOT NULL,
    logistics_carrier       varchar(100),
    logistics_tracking_no   varchar(100) UNIQUE,
    logistics_status        varchar(32),
    estimated_delivery_time timestamptz,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING_PAYMENT','PAID','PROCESSING','SHIPPED','DELIVERED','CANCELLED','COMPLETED')),
    CONSTRAINT ck_order_total CHECK (total_amount >= 0),
    CONSTRAINT ck_order_region CHECK (receiver_region IN ('CN_DEFAULT','BEIJING','SHANGHAI','GUANGZHOU')),
    CONSTRAINT ck_order_time CHECK (updated_at >= created_at),
    CONSTRAINT ck_order_logistics_status CHECK (logistics_status IS NULL OR logistics_status IN ('CREATED','IN_TRANSIT','DELIVERED','EXCEPTION')),
    CONSTRAINT ck_order_logistics_fields CHECK (
        (logistics_carrier IS NULL AND logistics_tracking_no IS NULL AND logistics_status IS NULL)
        OR (logistics_carrier IS NOT NULL AND logistics_tracking_no IS NOT NULL AND logistics_status IS NOT NULL)
    )
);
CREATE INDEX idx_order_user_created ON t_order (user_id, created_at DESC);

CREATE TABLE t_order_item (
    id                      varchar(64) PRIMARY KEY,
    order_id                varchar(64) NOT NULL REFERENCES t_order(id),
    product_id              varchar(64) NOT NULL REFERENCES t_product(id),
    product_name_snapshot   varchar(150) NOT NULL,
    model_snapshot          varchar(150) NOT NULL,
    price                   numeric(12,2) NOT NULL,
    quantity                integer NOT NULL,
    device_sn               varchar(64),
    CONSTRAINT ck_order_item_price CHECK (price >= 0),
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_sn_quantity CHECK (device_sn IS NULL OR quantity = 1),
    CONSTRAINT uq_order_item_sn UNIQUE (device_sn)
);
CREATE INDEX idx_order_item_order ON t_order_item (order_id);
CREATE INDEX idx_order_item_product ON t_order_item (product_id);

CREATE TABLE t_device (
    id                  varchar(64) PRIMARY KEY,
    sn                  varchar(64) NOT NULL UNIQUE,
    user_id             varchar(64) NOT NULL REFERENCES t_user(id),
    product_id          varchar(64) NOT NULL REFERENCES t_product(id),
    order_item_id       varchar(64) UNIQUE REFERENCES t_order_item(id),
    purchase_date       date NOT NULL,
    activation_date     date,
    warranty_start      date,
    warranty_end        date,
    warranty_type       varchar(50),
    device_status       varchar(24) NOT NULL,
    CONSTRAINT ck_device_warranty CHECK (warranty_start IS NULL OR warranty_end IS NULL OR warranty_end >= warranty_start),
    CONSTRAINT ck_device_activation CHECK (activation_date IS NULL OR activation_date >= purchase_date),
    CONSTRAINT ck_device_status CHECK (device_status IN ('ACTIVE','REPAIRING','RETIRED'))
);
CREATE INDEX idx_device_user ON t_device (user_id);

CREATE TABLE t_inventory (
    id                  varchar(64) PRIMARY KEY,
    product_id          varchar(64) NOT NULL REFERENCES t_product(id),
    region              varchar(32) NOT NULL,
    stock               integer NOT NULL,
    sale_price          numeric(12,2) NOT NULL,
    promotion_text      varchar(250),
    updated_at          timestamptz NOT NULL,
    CONSTRAINT uq_inventory_product_region UNIQUE (product_id, region),
    CONSTRAINT ck_inventory_region CHECK (region IN ('CN_DEFAULT','BEIJING','SHANGHAI','GUANGZHOU')),
    CONSTRAINT ck_inventory_stock CHECK (stock >= 0),
    CONSTRAINT ck_inventory_price CHECK (sale_price >= 0)
);

CREATE TABLE t_coupon (
    id                  varchar(64) PRIMARY KEY,
    coupon_name         varchar(150) NOT NULL,
    coupon_type         varchar(32) NOT NULL,
    min_amount          numeric(12,2) NOT NULL,
    discount_amount     numeric(12,2) NOT NULL,
    start_time          timestamptz NOT NULL,
    end_time            timestamptz NOT NULL,
    applicable_category varchar(32),
    status              varchar(24) NOT NULL,
    CONSTRAINT ck_coupon_amount CHECK (min_amount >= 0 AND discount_amount >= 0),
    CONSTRAINT ck_coupon_period CHECK (end_time > start_time),
    CONSTRAINT ck_coupon_category CHECK (applicable_category IS NULL OR applicable_category IN ('phone','tablet','laptop','wearable','headphone','router')),
    CONSTRAINT ck_coupon_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_coupon_type CHECK (coupon_type IN ('AMOUNT_OFF'))
);

CREATE TABLE t_user_coupon (
    id                  varchar(64) PRIMARY KEY,
    user_id             varchar(64) NOT NULL REFERENCES t_user(id),
    coupon_id           varchar(64) NOT NULL REFERENCES t_coupon(id),
    status              varchar(24) NOT NULL,
    claimed_at          timestamptz NOT NULL,
    used_at             timestamptz,
    CONSTRAINT uq_user_coupon UNIQUE (user_id, coupon_id),
    CONSTRAINT ck_user_coupon_status CHECK (status IN ('UNUSED','USED')),
    CONSTRAINT ck_user_coupon_used CHECK ((status = 'UNUSED' AND used_at IS NULL) OR (status = 'USED' AND used_at IS NOT NULL AND used_at >= claimed_at))
);

CREATE TABLE t_logistics_event (
    id                  varchar(64) PRIMARY KEY,
    order_id            varchar(64) NOT NULL REFERENCES t_order(id),
    event_time          timestamptz NOT NULL,
    location            varchar(150) NOT NULL,
    description         varchar(500) NOT NULL,
    CONSTRAINT uq_logistics_event UNIQUE (order_id, event_time, description)
);
CREATE INDEX idx_logistics_event_time ON t_logistics_event (order_id, event_time);

CREATE TABLE t_repair_order (
    id                      varchar(64) PRIMARY KEY,
    service_no              varchar(64) NOT NULL UNIQUE,
    user_id                 varchar(64) NOT NULL REFERENCES t_user(id),
    device_sn               varchar(64) NOT NULL REFERENCES t_device(sn),
    issue_description       varchar(500) NOT NULL,
    status                  varchar(32) NOT NULL,
    current_step            varchar(150) NOT NULL,
    contact_phone           varchar(20) NOT NULL,
    created_at              timestamptz NOT NULL,
    estimated_complete_time timestamptz,
    completed_at            timestamptz,
    CONSTRAINT ck_repair_status CHECK (status IN ('RECEIVED','INSPECTING','WAITING_FOR_CONFIRMATION','REPAIRING','COMPLETED','SHIPPING_BACK','CLOSED')),
    CONSTRAINT ck_repair_phone CHECK (contact_phone ~ '^1[3-9][0-9]{9}$'),
    CONSTRAINT ck_repair_time CHECK (completed_at IS NULL OR completed_at >= created_at)
);
CREATE INDEX idx_repair_user_phone ON t_repair_order (user_id, contact_phone);

CREATE TABLE t_repair_progress_event (
    id                  varchar(64) PRIMARY KEY,
    repair_order_id     varchar(64) NOT NULL REFERENCES t_repair_order(id),
    event_time          timestamptz NOT NULL,
    status              varchar(32) NOT NULL,
    description         varchar(500) NOT NULL,
    CONSTRAINT uq_repair_progress_event UNIQUE (repair_order_id, event_time, status),
    CONSTRAINT ck_repair_progress_status CHECK (status IN ('RECEIVED','INSPECTING','WAITING_FOR_CONFIRMATION','REPAIRING','COMPLETED','SHIPPING_BACK','CLOSED'))
);
CREATE INDEX idx_repair_progress_time ON t_repair_progress_event (repair_order_id, event_time);

CREATE TABLE t_spare_part_price (
    id              varchar(64) PRIMARY KEY,
    product_id      varchar(64) NOT NULL REFERENCES t_product(id),
    part_code       varchar(32) NOT NULL,
    part_name       varchar(100) NOT NULL,
    part_price      numeric(12,2) NOT NULL,
    labor_fee       numeric(12,2) NOT NULL,
    region          varchar(32) NOT NULL DEFAULT 'CN_DEFAULT',
    price_note      varchar(500),
    effective_at    timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT uq_spare_price_version UNIQUE (product_id, part_code, region, effective_at),
    CONSTRAINT ck_spare_part CHECK (part_code IN ('screen','battery','mainboard','back_cover','camera','other')),
    CONSTRAINT ck_spare_region CHECK (region IN ('CN_DEFAULT','BEIJING','SHANGHAI','GUANGZHOU')),
    CONSTRAINT ck_spare_amount CHECK (part_price >= 0 AND labor_fee >= 0),
    CONSTRAINT ck_spare_time CHECK (updated_at >= effective_at)
);
CREATE INDEX idx_spare_lookup ON t_spare_part_price (product_id, part_code, region, effective_at DESC);

COMMIT;
