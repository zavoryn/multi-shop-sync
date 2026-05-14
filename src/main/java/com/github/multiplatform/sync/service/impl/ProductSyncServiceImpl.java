package com.github.multiplatform.sync.service.impl;

import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
import com.github.multiplatform.sync.service.ProductSyncService;
import com.github.multiplatform.sync.strategy.IPlatformProductStrategy;
import com.github.multiplatform.sync.strategy.PlatformStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncServiceImpl implements ProductSyncService {

    private final PlatformStrategyFactory strategyFactory;

    @Override
    public boolean pushProduct(ChannelEnum channel, StandardProductDTO product) {
        IPlatformProductStrategy strategy = strategyFactory.getStrategy(channel);
        boolean success = strategy.pushProduct(product);

        if (success) {
            log.info("商品推送成功，更新本地状态为【平台审核中】: channel={}, outProductId={}",
                    channel, product.getOutProductId());
            // TODO: 更新数据库状态 → WAIT_PLATFORM_AUDIT
        }

        return success;
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
        IPlatformProductStrategy strategy = strategyFactory.getStrategy(channel);
        return strategy.changeStatus(outProductId, status);
    }

    @Override
    public void syncPlatformStatus(ChannelEnum channel, String outProductId) {
        IPlatformProductStrategy strategy = strategyFactory.getStrategy(channel);
        strategy.syncPlatformStatus(outProductId);
    }

    /**
     * 处理平台回调。
     * 1. 通过工厂获取对应渠道的策略
     * 2. 调用策略解析回调数据，得到标准化的状态
     * 3. 更新本地数据库状态
     */
    @Override
    public void handleCallback(ChannelEnum channel, Map<String, Object> callbackData) {
        IPlatformProductStrategy strategy = strategyFactory.getStrategy(channel);
        ProductStatusEnum status = strategy.parseCallback(callbackData);

        if (status == null) {
            log.warn("回调解析结果为空，跳过处理: channel={}", channel);
            return;
        }

        String outProductId = (String) callbackData.get("out_product_id");
        log.info("回调处理完成: channel={}, outProductId={}, status={}", channel, outProductId, status);

        // TODO: 根据 status 更新数据库中的商品状态
        switch (status) {
            case ON_SHELF:
                log.info("商品审核通过，已上架: outProductId={}", outProductId);
                break;
            case AUDIT_REJECT:
                String reason = (String) callbackData.get("reason");
                log.info("商品审核驳回: outProductId={}, reason={}", outProductId, reason);
                break;
            case OFF_SHELF:
                log.info("商品已下架: outProductId={}", outProductId);
                break;
            default:
                break;
        }
    }
}
