package com.github.multiplatform.sync.common.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道商品映射表：记录本地商品在各平台的外部ID与状态。
 * 主键唯一索引为 (out_product_id, channel)。
 */
@Data
@TableName("channel_product_mapping")
public class ChannelProductMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户自定义商品ID（本地业务侧） */
    private String outProductId;

    /** 渠道编码：local / douyin / xiaohongshu / wechat */
    private String channel;

    /** 平台返回的商品ID，用于后续上下架/查询接口 */
    private String externalProductId;

    /** 本地状态：见 ProductStatusEnum.code（0/10/20/30/40） */
    private Integer localStatus;

    /** 平台原始状态字符串（便于排查） */
    private String externalStatus;

    private String rejectReason;
    private LocalDateTime lastSyncTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
