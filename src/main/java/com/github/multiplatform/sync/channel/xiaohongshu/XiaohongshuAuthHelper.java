package com.github.multiplatform.sync.channel.xiaohongshu;

import com.github.multiplatform.sync.common.auth.AccessTokenManager;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 小红书鉴权辅助类。
 *
 * 签名算法：MD5
 * 公式：MD5(method + "?" + sortedSystemParams + appSecret)
 * 文档：https://open.xiaohongshu.com/document/api
 *
 * 注意：accessToken 不参与签名计算
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XiaohongshuAuthHelper {

    @Value("${channel.xiaohongshu.app-id:}")
    private String appId;

    @Value("${channel.xiaohongshu.app-secret:}")
    private String appSecret;

    private static final String GATEWAY_URL = "https://ark.xiaohongshu.com/ark/open_api/v3/common_controller";

    private final AccessTokenManager tokenManager;

    public String calcSign(String method, String timestamp) {
        String[] params = {
                "appId=" + appId,
                "timestamp=" + timestamp,
                "version=2.0"
        };
        Arrays.sort(params);
        String queryString = String.join("&", params);
        String signSource = method + "?" + queryString + appSecret;
        return DigestUtils.md5Hex(signSource.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构建完整的请求 JSON 体（小红书所有参数都在 body 中）。
     *
     * 重要：oauth.refreshToken 接口本身不应携带 accessToken（此时 token 还没拿到），
     * 而其他接口必须带。两种场景使用同一方法是因为 RefreshStrategy 在 token 缺失时调用，
     * 此时 tokenManager.getToken() 会触发循环刷新 → 死锁。
     * 解决：oauth.* 类接口直接传空 accessToken。
     */
    public String buildRequestBody(String method, String businessParams) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String sign = calcSign(method, timestamp);
        String accessToken = method.startsWith("oauth.") ? "" : tokenManager.getToken(ChannelEnum.XIAOHONGSHU);

        return "{\"method\":\"" + method + "\""
                + ",\"appId\":\"" + appId + "\""
                + ",\"sign\":\"" + sign + "\""
                + ",\"timestamp\":\"" + timestamp + "\""
                + ",\"version\":\"2.0\""
                + ",\"accessToken\":\"" + accessToken + "\""
                + ",\"data\":" + businessParams
                + "}";
    }

    public String getGatewayUrl() { return GATEWAY_URL; }
    public String getAppId() { return appId; }
    public String getAppSecret() { return appSecret; }

    public String getAccessToken() {
        return tokenManager.getToken(ChannelEnum.XIAOHONGSHU);
    }
}
