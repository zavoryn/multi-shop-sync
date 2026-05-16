package com.github.multiplatform.sync.common.auth;

import com.github.multiplatform.sync.common.enums.ChannelEnum;

/**
 * 各渠道 token 刷新策略。
 * 实现负责调用平台 token 接口，把结果包成 {@link TokenInfo} 返回。
 *
 * 失败时抛 RuntimeException（具体由 caller 处理：log + 降级）。
 */
public interface RefreshStrategy {

    ChannelEnum getChannel();

    /**
     * 调用平台 token 接口拉取新 token。
     * 返回的 TokenInfo 必须包含一个保守的 expiresAt（比平台真实有效期略短）。
     */
    TokenInfo fetch();
}
