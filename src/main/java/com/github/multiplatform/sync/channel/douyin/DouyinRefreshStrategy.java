package com.github.multiplatform.sync.channel.douyin;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.multiplatform.sync.common.auth.RefreshStrategy;
import com.github.multiplatform.sync.common.auth.TokenInfo;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
import com.github.multiplatform.sync.config.ChannelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * 抖音小店 Token 刷新策略。
 *
 * 官方文档：
 * - access_token 体系：https://op.jinritemai.com/docs/guide-docs/10/2261
 * - refresh 接口：POST https://oauth.snssdk.com/oauth/refresh_token/?
 *     app_id=&app_secret=&grant_type=refresh_token&refresh_token=
 *
 * 实际抖店有两种授权模式：
 * 1. SELF_BUILD（自用型）：直接 grant_type=authorization_self + shop_id 拿
 * 2. PLUGIN/TOOL（工具型）：先授权拿 authorization_code，再换 token；过期靠 refresh_token
 *
 * 当前实现假设走自用型（最常见），如果你的应用是 PLUGIN 类型，请改成对应的 grant_type。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "channel.douyin", name = "enabled", havingValue = "true")
public class DouyinRefreshStrategy implements RefreshStrategy {

    private static final String OAUTH_URL = "https://oauth.snssdk.com/oauth/refresh_token/";

    private final ChannelConfig channelConfig;
    private final WebClient.Builder webClientBuilder;

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.DOUYIN;
    }

    @Override
    public TokenInfo fetch() {
        ChannelConfig.DouyinConfig cfg = channelConfig.getDouyin();

        String url = OAUTH_URL + "?app_id=" + cfg.getAppKey()
                + "&app_secret=" + cfg.getAppSecret()
                + "&grant_type=refresh_token"
                + "&refresh_token=" + cfg.getRefreshToken();

        String response = webClientBuilder.build().get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject result = JSON.parseObject(response);
        // 抖店 oauth 接口返回 { code, message, data: { access_token, refresh_token, expires_in } }
        if (result == null || result.getIntValue("code") != 10000) {
            throw new ChannelException("douyin", "刷新 token 失败: " + (result == null ? "null response" : result.getString("message")));
        }
        JSONObject data = result.getJSONObject("data");
        String accessToken = data.getString("access_token");
        long expiresIn = data.getLongValue("expires_in");

        // 提前 5 分钟过期，避免临界点上 race
        Duration ttl = Duration.ofSeconds(Math.max(60, expiresIn - 300));
        log.info("[douyin] token 刷新返回 expires_in={}s, 有效期取 {}s", expiresIn, ttl.getSeconds());
        return TokenInfo.ofTtl(accessToken, ttl);
    }
}
