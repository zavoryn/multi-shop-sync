-- ============================================================
-- 多渠道商品同步 - 数据库建表语句
-- ============================================================

-- 渠道商品映射表：记录本地商品在各平台的外部ID和状态
CREATE TABLE IF NOT EXISTS channel_product_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    out_product_id VARCHAR(128) NOT NULL COMMENT '商户自定义商品ID',
    channel VARCHAR(32) NOT NULL COMMENT '渠道标识(douyin/xiaohongshu/wechat/local)',
    external_product_id VARCHAR(128) COMMENT '外部平台商品ID',
    local_status TINYINT NOT NULL DEFAULT 0 COMMENT '本地状态(0=草稿,10=审核中,20=已上架,30=驳回,40=已下架)',
    external_status VARCHAR(64) COMMENT '外部平台原始状态',
    reject_reason VARCHAR(512) COMMENT '审核驳回原因',
    last_sync_time DATETIME COMMENT '最后同步时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_product_channel (out_product_id, channel),
    KEY idx_channel (channel),
    KEY idx_status (local_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道商品映射表';

-- 渠道SKU映射表
CREATE TABLE IF NOT EXISTS channel_sku_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    out_product_id VARCHAR(128) NOT NULL COMMENT '商户自定义商品ID',
    out_sku_id VARCHAR(128) NOT NULL COMMENT '商户自定义SKU ID',
    channel VARCHAR(32) NOT NULL COMMENT '渠道标识',
    external_sku_id VARCHAR(128) COMMENT '外部平台SKU ID',
    price BIGINT COMMENT '价格(分)',
    stock INT COMMENT '库存',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_sku_channel (out_sku_id, channel),
    KEY idx_product (out_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道SKU映射表';

-- 类目映射表：本地类目 ↔ 各平台类目的对应关系
CREATE TABLE IF NOT EXISTS category_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    local_category_id BIGINT NOT NULL COMMENT '本地类目ID',
    channel VARCHAR(32) NOT NULL COMMENT '渠道标识',
    external_category_id VARCHAR(128) NOT NULL COMMENT '外部平台类目ID',
    category_name VARCHAR(255) COMMENT '外部类目名称',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_local_channel (local_category_id, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='类目映射表';

-- 回调日志表：记录所有平台回调事件，便于排查问题
CREATE TABLE IF NOT EXISTS callback_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    channel VARCHAR(32) NOT NULL COMMENT '渠道标识',
    event_type VARCHAR(64) COMMENT '事件类型',
    raw_data TEXT COMMENT '原始回调数据(JSON)',
    out_product_id VARCHAR(128) COMMENT '关联的商品ID',
    parsed_status TINYINT COMMENT '解析后的状态',
    processed TINYINT DEFAULT 0 COMMENT '是否已处理(0=未处理,1=已处理)',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_channel (channel),
    KEY idx_product (out_product_id),
    KEY idx_processed (processed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回调日志表';
