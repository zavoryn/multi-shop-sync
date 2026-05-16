package com.github.multiplatform.sync.service;

import com.github.multiplatform.sync.common.dao.entity.ChannelProductMapping;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;

import java.util.Optional;

/**
 * 渠道商品映射服务。
 *
 * 负责维护 (outProductId, channel) → externalProductId / 状态 的映射关系，
 * 是业务层和各渠道 Strategy 之间的桥梁。
 */
public interface ChannelProductMappingService {

    Optional<ChannelProductMapping> find(String outProductId, ChannelEnum channel);

    /** 推送成功后写入或更新映射；状态置为 {@link ProductStatusEnum#WAIT_PLATFORM_AUDIT} */
    void upsertAfterPush(String outProductId, ChannelEnum channel, String externalProductId);

    /** 回调或主动查询后更新状态 */
    void updateStatus(String outProductId, ChannelEnum channel, ProductStatusEnum status, String externalStatus, String rejectReason);

    /**
     * 本地渠道入库（external_product_id = outProductId，状态直接 ON_SHELF）
     */
    void upsertLocal(String outProductId);
}
