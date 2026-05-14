package com.github.multiplatform.sync.strategy;

import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;

import java.util.Map;

/**
 * 多渠道商品同步策略接口。
 * 每个渠道（抖音/小红书/微信/本地）实现此接口，封装各自的 API 调用逻辑。
 *
 * 设计原则：
 * - 符合策略模式：同一种行为（推送、上下架），不同平台不同实现
 * - 符合防腐层（ACL）：屏蔽各平台 API 差异，对外暴露统一方法
 */
public interface IPlatformProductStrategy {

    /** 返回当前策略对应的渠道枚举 */
    ChannelEnum getChannel();

    /**
     * 推送商品到外部平台。
     * 将内部标准模型转换为目标平台参数格式并发送。
     *
     * @return 推送成功返回 true
     */
    boolean pushProduct(StandardProductDTO product);

    /**
     * 上下架控制。
     *
     * @param outProductId 商户自定义商品ID
     * @param status       目标状态（ON_SHELF / OFF_SHELF）
     * @return 操作成功返回 true
     */
    boolean changeStatus(String outProductId, ProductStatusEnum status);

    /**
     * 主动同步外部平台商品状态到本地（轮询补偿用）。
     *
     * @param outProductId 商户自定义商品ID
     */
    void syncPlatformStatus(String outProductId);

    /**
     * 解析平台异步回调数据，返回标准化状态。
     * 各平台回调报文格式不同，由各自的 Strategy 负责解析。
     *
     * @param callbackData 原始回调数据
     * @return 解析后的商品状态
     */
    ProductStatusEnum parseCallback(Map<String, Object> callbackData);
}
