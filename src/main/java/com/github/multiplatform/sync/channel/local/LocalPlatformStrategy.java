package com.github.multiplatform.sync.channel.local;

import com.github.multiplatform.sync.common.dao.entity.ChannelProductMapping;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.model.PushResult;
import com.github.multiplatform.sync.service.ChannelProductMappingService;
import com.github.multiplatform.sync.strategy.AbstractPlatformStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 本地商城策略实现。
 * 本地渠道不需要调用外部 API，直接将商品入库到 mapping 表即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalPlatformStrategy extends AbstractPlatformStrategy {

    private final ChannelProductMappingService mappingService;

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.LOCAL;
    }

    @Override
    protected PushResult doPushProduct(StandardProductDTO product) {
        log.info("本地商城商品入库: outProductId={}, title={}", product.getOutProductId(), product.getTitle());
        mappingService.upsertLocal(product.getOutProductId());
        // 本地渠道的 channelProductId 直接等于 outProductId
        return PushResult.ok(product.getOutProductId());
    }

    @Override
    protected boolean doChangeStatus(String channelProductId, ProductStatusEnum status) {
        log.info("本地商城状态变更: channelProductId={}, status={}", channelProductId, status);
        // 直接更新 mapping 表的本地状态。本地渠道无外部状态字符串。
        Optional<ChannelProductMapping> existing = mappingService.find(channelProductId, ChannelEnum.LOCAL);
        if (!existing.isPresent()) {
            log.warn("本地商品不存在: outProductId={}", channelProductId);
            return false;
        }
        mappingService.updateStatus(channelProductId, ChannelEnum.LOCAL, status, null, null);
        return true;
    }

    @Override
    protected ProductStatusEnum doSyncPlatformStatus(String channelProductId) {
        // 本地商城无外部状态需要同步；直接从 mapping 读出当前状态返回
        return mappingService.find(channelProductId, ChannelEnum.LOCAL)
                .map(m -> {
                    for (ProductStatusEnum e : ProductStatusEnum.values()) {
                        if (e.getCode() == m.getLocalStatus()) return e;
                    }
                    return null;
                })
                .orElse(null);
    }

    @Override
    protected ProductStatusEnum doParseCallback(Map<String, Object> callbackData) {
        // 本地商城无回调
        return null;
    }
}
