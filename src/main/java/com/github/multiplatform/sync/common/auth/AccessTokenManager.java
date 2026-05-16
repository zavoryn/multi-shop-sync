package com.github.multiplatform.sync.common.auth;

import com.github.multiplatform.sync.common.enums.ChannelEnum;

/**
 * Access Token 统一管理入口。
 *
 * 业务侧通过 {@link #getToken(ChannelEnum)} 获取当前 token；
 * 实现负责缓存、失效检测、按需刷新与并发单飞。
 */
public interface AccessTokenManager {

    /**
     * 获取指定渠道的 access token。
     * 命中且未过期则直接返回；过期或缺失则触发同步 refresh。
     */
    String getToken(ChannelEnum channel);

    /**
     * 强制刷新指定渠道的 token，跳过缓存判断。
     * 适用于：收到平台返回的"鉴权失败"错误码时主动作废重取。
     */
    String refresh(ChannelEnum channel);

    /**
     * 查询当前缓存的 TokenInfo（不触发 refresh），主要给 Scheduler 决策用。
     */
    TokenInfo peek(ChannelEnum channel);
}
