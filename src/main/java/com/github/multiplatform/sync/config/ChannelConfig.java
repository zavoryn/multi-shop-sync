package com.github.multiplatform.sync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 多渠道配置。
 * 从 application.yml 中读取各平台的凭证信息。
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
        private String accessToken;
    }

    @Data
    public static class XiaohongshuConfig {
        private boolean enabled = false;
        private String appId;
        private String appSecret;
        private String accessToken;
    }

    @Data
    public static class WechatConfig {
        private boolean enabled = false;
        private String appId;
        private String appSecret;
        private String accessToken;
    }
}
