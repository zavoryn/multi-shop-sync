package com.github.multiplatform.sync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 多渠道配置。
 * 从 application.yml 中读取各平台的凭证信息。
 *
 * 注意：accessToken 不再在此配置，由 AccessTokenManager 通过 refreshToken
 * 动态获取并缓存（Phase 4）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "channel")
public class ChannelConfig {

    private DouyinConfig douyin = new DouyinConfig();
    private XiaohongshuConfig xiaohongshu = new XiaohongshuConfig();
    private WechatConfig wechat = new WechatConfig();

    @Data
    public static class DouyinConfig {
        private boolean enabled = false;
        private String appKey;
        private String appSecret;
        private String refreshToken;
    }

    @Data
    public static class XiaohongshuConfig {
        private boolean enabled = false;
        private String appId;
        private String appSecret;
        private String refreshToken;
    }

    @Data
    public static class WechatConfig {
        private boolean enabled = false;
        private String appId;
        private String appSecret;
        /** 微信回调验签 token */
        private String callbackToken;
        /** 微信回调消息体 AES-CBC 解密密钥（43 字符） */
        private String encodingAesKey;
    }
}
