package com.github.multiplatform.sync.common.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台回调日志。
 * 双职责：审计 + Phase 3 的幂等去重（idempotencyKey 唯一索引）。
 */
@Data
@TableName("callback_log")
public class CallbackLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String channel;
    private String eventType;
    private String rawData;
    private String outProductId;

    /** 解析后的标准状态 code，见 ProductStatusEnum */
    private Integer parsedStatus;

    /** 0=未处理 / 1=已处理 / 2=处理失败 */
    private Integer processed;

    /** MD5(channel + rawData)，用于幂等去重 */
    private String idempotencyKey;

    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
