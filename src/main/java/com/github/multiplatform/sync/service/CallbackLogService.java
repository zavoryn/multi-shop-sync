package com.github.multiplatform.sync.service;

import com.github.multiplatform.sync.common.dao.entity.CallbackLog;
import com.github.multiplatform.sync.common.enums.ChannelEnum;

/**
 * 回调日志服务。
 * Phase 2 仅做插入审计；Phase 3 接入幂等去重 + 处理状态更新。
 */
public interface CallbackLogService {

    /**
     * 记录一次回调到达。Phase 3 后基于 idempotencyKey 唯一约束自动去重。
     *
     * @return 新建的日志 ID；如已存在（幂等命中）返回 null
     */
    Long record(ChannelEnum channel, String eventType, String rawData, String outProductId);

    void markProcessed(Long logId, Integer parsedStatus);

    void markFailed(Long logId, String errorMsg);
}
