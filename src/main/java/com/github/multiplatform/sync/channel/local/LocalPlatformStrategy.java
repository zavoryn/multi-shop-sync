package com.github.multiplatform.sync.channel.local;

import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.strategy.AbstractPlatformStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 本地商城策略实现。
 * 本地渠道不需要调用外部 API，直接将标准模型入库即可。
 */
@Slf4j
@Component
public class LocalPlatformStrategy extends AbstractPlatformStrategy {

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.LOCAL;
    }

    @Override
    protected boolean doPushProduct(StandardProductDTO product) {
        // 本地商城直接入库，无需转换格式
        log.info("本地商城商品入库: outProductId={}, title={}", product.getOutProductId(), product.getTitle());
        // TODO: 调用本地商品 Service 保存到数据库
        return true;
    }

    @Override
    protected boolean doChangeStatus(String outProductId, ProductStatusEnum status) {
        log.info("本地商城状态变更: outProductId={}, status={}", outProductId, status);
        // TODO: 更新本地数据库中的商品状态
        return true;
    }

    @Override
    protected void doSyncPlatformStatus(String outProductId) {
        // 本地商城无外部状态需要同步
        log.debug("本地商城无需同步外部状态: outProductId={}", outProductId);
    }

    @Override
    protected ProductStatusEnum doParseCallback(Map<String, Object> callbackData) {
        // 本地商城无回调
        return null;
    }
}
