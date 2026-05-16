package com.github.multiplatform.sync.common.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("channel_sku_mapping")
public class ChannelSkuMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String outProductId;
    private String outSkuId;
    private String channel;
    private String externalSkuId;

    /** 单位：分 */
    private Long price;
    private Integer stock;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
