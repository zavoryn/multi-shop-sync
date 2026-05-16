package com.github.multiplatform.sync.channel.wechat;

import com.github.multiplatform.sync.common.auth.AccessTokenManager;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微信小店鉴权辅助类。
 * 实际 token 获取和缓存在 {@link com.github.multiplatform.sync.common.auth.AccessTokenManager}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatAuthHelper {

    @Value("${channel.wechat.app-id:}")
    private String appId;

    @Value("${channel.wechat.app-secret:}")
    private String appSecret;

    private final AccessTokenManager tokenManager;

    public String getAccessToken() {
        return tokenManager.getToken(ChannelEnum.WECHAT);
    }

    public String getAppId() { return appId; }
    public String getAppSecret() { return appSecret; }
}
