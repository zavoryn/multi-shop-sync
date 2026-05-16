package com.github.multiplatform.sync.strategy;

import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.common.model.PushResult;

import java.util.Map;

/**
 * 多渠道商品同步策略接口。
 * 每个渠道（抖音/小红书/微信/本地）实现此接口，封装各自的 API 调用逻辑。
 *
 * 设计原则：
 * - 策略模式：同一种行为（推送、上下架），不同平台不同实现
 * - 防腐层（ACL）：屏蔽各平台 API 差异，对外暴露统一方法
 *
 * 命名约定：
 * - outProductId      — 商户自定义商品 ID（业务侧）
 * - channelProductId  — 渠道侧的商品 ID（外部平台分配；本地渠道等于 outProductId）
 *   推送时由本接口返回，上下架/查询时由 service 层查 mapping 表后传入。
 */
public interface IPlatformProductStrategy {

    /** 返回当前策略对应的渠道枚举 */
    ChannelEnum getChannel();

    /**
     * 推送商品到外部平台。
     * 将内部标准模型转换为目标平台参数格式并发送。
     *
     * @return 推送结果（含平台分配的 channelProductId）
     */
    PushResult pushProduct(StandardProductDTO product);

    /**
     * 上下架控制。
     *
     * @param channelProductId 渠道侧商品 ID
     * @param status           目标状态（ON_SHELF / OFF_SHELF）
     */
    boolean changeStatus(String channelProductId, ProductStatusEnum status);

    /**
     * 主动同步外部平台商品状态到本地。
     *
     * @param channelProductId 渠道侧商品 ID
     * @return 当前平台侧解析后的标准状态（拿不到返回 null）
     */
    ProductStatusEnum syncPlatformStatus(String channelProductId);

    /**
     * 解析平台异步回调数据，返回标准化状态。
     * 各平台回调报文格式不同，由各自的 Strategy 负责解析。
     */
    ProductStatusEnum parseCallback(Map<String, Object> callbackData);
}
