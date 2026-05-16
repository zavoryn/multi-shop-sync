package com.github.multiplatform.sync.service.impl;

import com.alibaba.fastjson2.JSON;
import com.github.multiplatform.sync.common.dao.entity.ChannelProductMapping;
import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
import com.github.multiplatform.sync.common.model.PushResult;
import com.github.multiplatform.sync.service.CallbackLogService;
import com.github.multiplatform.sync.service.ChannelProductMappingService;
import com.github.multiplatform.sync.service.ProductSyncService;
import com.github.multiplatform.sync.strategy.IPlatformProductStrategy;
import com.github.multiplatform.sync.strategy.PlatformStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncServiceImpl implements ProductSyncService {

    private final PlatformStrategyFactory strategyFactory;
    private final ChannelProductMappingService mappingService;
    private final CallbackLogService callbackLogService;

    @Override
    public PushResult pushProduct(ChannelEnum channel, StandardProductDTO product) {
        IPlatformProductStrategy strategy = strategyFactory.getStrategy(channel);
        PushResult result = strategy.pushProduct(product);

        if (result != null && result.isSuccess()) {
            // 外部渠道：把平台返回的 channelProductId 落库（状态 → WAIT_AUDIT）
            // 本地渠道：LocalPlatformStrategy 已经在 doPushProduct 内部调 upsertLocal，这里跳过避免覆盖 ON_SHELF
            if (channel != ChannelEnum.LOCAL) {
                mappingService.upsertAfterPush(product.getOutProductId(), channel, result.getChannelProductId());
            }
        }
        return result;
    }

    @Override
    public void pushProductToChannels(StandardProductDTO product, List<ChannelEnum> channels) {
        for (ChannelEnum channel : channels) {
            try {
                pushProduct(channel, product);
            } catch (ChannelException e) {
                log.error("推送商品到渠道 {} 失败: {}", channel, e.getMessage());
                // 某个渠道失败不影响其他渠道
            }
        }
    }

    @Override
    public boolean changeStatus(ChannelEnum channel, String outProductId, ProductStatusEnum status) {
        String channelProductId = resolveChannelProductId(channel, outProductId);
        IPlatformProductStrategy strategy = strategyFactory.getStrategy(channel);
        boolean ok = strategy.changeStatus(channelProductId, status);
        if (ok) {
            mappingService.updateStatus(outProductId, channel, status, null, null);
        }
        return ok;
    }

    @Override
    public void syncPlatformStatus(ChannelEnum channel, String outProductId) {
        String channelProductId = resolveChannelProductId(channel, outProductId);
        IPlatformProductStrategy strategy = strategyFactory.getStrategy(channel);
        ProductStatusEnum parsed = strategy.syncPlatformStatus(channelProductId);
        if (parsed != null) {
            mappingService.updateStatus(outProductId, channel, parsed, null, null);
        }
    }

    /**
     * 处理平台回调（异步执行）。
     * 1. 写 callback_log（Phase 3 接入幂等去重）
     * 2. 调 Strategy 解析得到标准状态
     * 3. 写 mapping 表
     */
    @Async("webhookExecutor")
    @Override
    public void handleCallback(ChannelEnum channel, Map<String, Object> callbackData) {
        String outProductId = (String) callbackData.get("out_product_id");
        String eventType = String.valueOf(callbackData.getOrDefault("event",
                callbackData.getOrDefault("msgTag", "")));
        String rawData = JSON.toJSONString(callbackData);

        Long logId = callbackLogService.record(channel, eventType, rawData, outProductId);
        if (logId == null) {
            // 幂等命中，已在 service 内 log，直接返回
            return;
        }

        try {
            IPlatformProductStrategy strategy = strategyFactory.getStrategy(channel);
            ProductStatusEnum status = strategy.parseCallback(callbackData);

            if (status == null) {
                log.warn("回调解析结果为空，跳过状态更新: channel={}, outProductId={}", channel, outProductId);
                callbackLogService.markProcessed(logId, null);
                return;
            }

            String rejectReason = (String) callbackData.get("reason");
            if (outProductId != null) {
                mappingService.updateStatus(outProductId, channel, status, eventType, rejectReason);
            } else {
                log.warn("回调数据缺少 out_product_id，无法更新 mapping: channel={}, event={}", channel, eventType);
            }
            callbackLogService.markProcessed(logId, status.getCode());

        } catch (Exception e) {
            log.error("回调处理异常: channel={}, outProductId={}", channel, outProductId, e);
            callbackLogService.markFailed(logId, e.getMessage());
        }
    }

    /**
     * 把业务侧 outProductId 翻译为 channelProductId。
     * 本地渠道直接返回 outProductId（mapping 表中 external = out）。
     * 外部渠道必须存在 mapping，否则抛 ChannelException。
     */
    private String resolveChannelProductId(ChannelEnum channel, String outProductId) {
        Optional<ChannelProductMapping> mapping = mappingService.find(outProductId, channel);
        if (!mapping.isPresent() || mapping.get().getExternalProductId() == null) {
            throw new ChannelException(channel.getCode(),
                    "商品未推送或映射缺失: outProductId=" + outProductId);
        }
        return mapping.get().getExternalProductId();
    }
}
