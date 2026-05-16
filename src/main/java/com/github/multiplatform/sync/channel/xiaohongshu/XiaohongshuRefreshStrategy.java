package com.github.multiplatform.sync.channel.xiaohongshu;

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
 * 小红书 Token 刷新策略。
 *
 * 官方文档：
 * - 授权与 access_token 体系：https://open.xiaohongshu.com/document/api/oauth
 * - 调用 method=oauth.getAccessToken / oauth.refreshToken
 *
 * 小红书所有 API（包括 token 接口）走同一网关，参数都在请求 body：
 *   POST https://ark.xiaohongshu.com/ark/open_api/v3/common_controller
 *   body: { method, appId, sign, timestamp, version, data: { refreshToken / code } }
 *
 * accessToken 默认 7 天，refreshToken 30 天（具体以官方为准）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "channel.xiaohongshu", name = "enabled", havingValue = "true")
public class XiaohongshuRefreshStrategy implements RefreshStrategy {

    private static final String GATEWAY_URL = "https://ark.xiaohongshu.com/ark/open_api/v3/common_controller";

    private final ChannelConfig channelConfig;
    private final XiaohongshuAuthHelper authHelper;
    private final WebClient.Builder webClientBuilder;

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.XIAOHONGSHU;
    }

    @Override
    public TokenInfo fetch() {
        ChannelConfig.XiaohongshuConfig cfg = channelConfig.getXiaohongshu();

        JSONObject data = new JSONObject();
        data.put("refreshToken", cfg.getRefreshToken());

        // XiaohongshuAuthHelper.buildRequestBody 已经实现了 MD5 签名 + body 包装
        String requestBody = authHelper.buildRequestBody("oauth.refreshToken", data.toJSONString());

        String response = webClientBuilder.build().post()
                .uri(GATEWAY_URL)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject result = JSON.parseObject(response);
        if (result == null || !result.getBooleanValue("success")) {
            throw new ChannelException("xiaohongshu", "刷新 token 失败: " + (result == null ? "null" : result.getString("error_msg")));
        }
        JSONObject payload = result.getJSONObject("data");
        String accessToken = payload.getString("accessToken");
        // 小红书返回 expires_in 单位为秒；具体字段名按官方文档校正
        long expiresIn = payload.getLongValue("accessTokenExpiresIn");
        if (expiresIn <= 0) expiresIn = 7L * 24 * 3600; // fallback 7 天

        Duration ttl = Duration.ofSeconds(Math.max(60, expiresIn - 600));  // 提前 10 分钟过期
        log.info("[xiaohongshu] token 刷新返回 expires_in={}s, 有效期取 {}s", expiresIn, ttl.getSeconds());
        return TokenInfo.ofTtl(accessToken, ttl);
    }
}
