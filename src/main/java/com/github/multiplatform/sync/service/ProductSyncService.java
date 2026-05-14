package com.github.multiplatform.sync.service;

import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;

import java.util.List;

/**
 * 多渠道商品同步服务接口。
 */
public interface ProductSyncService {

    /** 推送商品到指定渠道 */
    boolean pushProduct(ChannelEnum channel, StandardProductDTO product);

    /** 批量推送商品到多个渠道 */
    void pushProductToChannels(StandardProductDTO product, List<ChannelEnum> channels);

    /** 统一上下架 */
    boolean changeStatus(ChannelEnum channel, String outProductId, ProductStatusEnum status);

    /** 主动同步外部平台状态 */
    void syncPlatformStatus(ChannelEnum channel, String outProductId);

    /** 处理平台回调 */
    void handleCallback(ChannelEnum channel, java.util.Map<String, Object> callbackData);
}
