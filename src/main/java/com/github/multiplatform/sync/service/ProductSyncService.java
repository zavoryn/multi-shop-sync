package com.github.multiplatform.sync.service;

import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.common.model.PushResult;

import java.util.List;
import java.util.Map;

public interface ProductSyncService {

    /** 推送商品到指定渠道 */
    PushResult pushProduct(ChannelEnum channel, StandardProductDTO product);

    /** 批量推送商品到多个渠道 */
    void pushProductToChannels(StandardProductDTO product, List<ChannelEnum> channels);

    /**
     * 上下架。
     *
     * @param outProductId 商户自定义商品 ID（service 内部会查 mapping 翻译为 channelProductId）
     */
    boolean changeStatus(ChannelEnum channel, String outProductId, ProductStatusEnum status);

    /** 主动同步外部平台状态到本地 */
    void syncPlatformStatus(ChannelEnum channel, String outProductId);

    /** 处理平台回调（异步） */
    void handleCallback(ChannelEnum channel, Map<String, Object> callbackData);
}
