package com.github.multiplatform.sync.channel.wechat;

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
 * 微信小店 / 视频号小店 Token 刷新策略。
 *
 * 官方文档：
 * - 普通公众号/小程序 grant_type=client_credential：
 *   GET https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=&secret=
 * - 视频号小店 token 通常走第三方平台授权得到 authorizer_access_token，
 *   有效期 2 小时，提前 5-10 分钟刷新
 *
 * 当前实现按 client_credential 路径（适用于小店原生应用）；
 * 如果是第三方平台/服务商场景，需替换为 authorizer_token 刷新接口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "channel.wechat", name = "enabled", havingValue = "true")
public class WechatRefreshStrategy implements RefreshStrategy {

    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";

    private final ChannelConfig channelConfig;
    private final WebClient.Builder webClientBuilder;

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.WECHAT;
    }

    @Override
    public TokenInfo fetch() {
        ChannelConfig.WechatConfig cfg = channelConfig.getWechat();

        String url = TOKEN_URL + "?grant_type=client_credential"
                + "&appid=" + cfg.getAppId()
                + "&secret=" + cfg.getAppSecret();

        String response = webClientBuilder.build().get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject result = JSON.parseObject(response);
        if (result == null || !result.containsKey("access_token")) {
            throw new ChannelException("wechat", "刷新 token 失败: " +
                    (result == null ? "null" : result.getString("errmsg")));
        }
        String token = result.getString("access_token");
        long expiresIn = result.getLongValue("expires_in"); // 7200

        Duration ttl = Duration.ofSeconds(Math.max(60, expiresIn - 300));
        log.info("[wechat] token 刷新返回 expires_in={}s, 有效期取 {}s", expiresIn, ttl.getSeconds());
        return TokenInfo.ofTtl(token, ttl);
    }
}
