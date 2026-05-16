-- ============================================================
-- 多渠道商品同步 - 数据库建表语句
-- 兼容 MySQL 8 与 H2 (MODE=MySQL)
-- ============================================================

-- 渠道商品映射表：记录本地商品在各平台的外部ID和状态
DROP TABLE IF EXISTS channel_product_mapping;
CREATE TABLE channel_product_mapping (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    out_product_id      VARCHAR(128) NOT NULL,
    channel             VARCHAR(32)  NOT NULL,
    external_product_id VARCHAR(128),
    local_status        TINYINT      NOT NULL DEFAULT 0,
    external_status     VARCHAR(64),
    reject_reason       VARCHAR(512),
    last_sync_time      DATETIME,
    created_time        DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time        DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_channel UNIQUE (out_product_id, channel)
);
CREATE INDEX idx_cpm_channel ON channel_product_mapping (channel);
CREATE INDEX idx_cpm_status  ON channel_product_mapping (local_status);

-- 渠道SKU映射表
DROP TABLE IF EXISTS channel_sku_mapping;
CREATE TABLE channel_sku_mapping (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    out_product_id  VARCHAR(128) NOT NULL,
    out_sku_id      VARCHAR(128) NOT NULL,
    channel         VARCHAR(32)  NOT NULL,
    external_sku_id VARCHAR(128),
    price           BIGINT,
    stock           INT,
    created_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sku_channel UNIQUE (out_sku_id, channel)
);
CREATE INDEX idx_csm_product ON channel_sku_mapping (out_product_id);

-- 类目映射表：本地类目 ↔ 各平台类目
DROP TABLE IF EXISTS category_mapping;
CREATE TABLE category_mapping (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    local_category_id    BIGINT       NOT NULL,
    channel              VARCHAR(32)  NOT NULL,
    external_category_id VARCHAR(128) NOT NULL,
    category_name        VARCHAR(255),
    created_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_local_channel UNIQUE (local_category_id, channel)
);

-- 回调日志表：记录所有平台回调事件，便于排查问题与幂等去重
DROP TABLE IF EXISTS callback_log;
CREATE TABLE callback_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel         VARCHAR(32)  NOT NULL,
    event_type      VARCHAR(64),
    raw_data        TEXT,
    out_product_id  VARCHAR(128),
    parsed_status   TINYINT,
    processed       TINYINT DEFAULT 0,
    -- Phase 3: 用于幂等去重；MD5(channel + raw_data)
    idempotency_key VARCHAR(64),
    error_msg       VARCHAR(1024),
    created_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_cbl_channel   ON callback_log (channel);
CREATE INDEX idx_cbl_product   ON callback_log (out_product_id);
CREATE INDEX idx_cbl_processed ON callback_log (processed);
