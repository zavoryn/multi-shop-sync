package com.github.multiplatform.sync.channel.xiaohongshu;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 小红书鉴权辅助类。
 * 负责签名计算和 Token 管理。
 *
 * 签名算法：MD5
 * 公式：MD5(method + "?" + sortedSystemParams + appSecret)
 * 文档：https://open.xiaohongshu.com/document/api
 *
 * 注意：accessToken 不参与签名计算
 */
@Slf4j
@Component
public class XiaohongshuAuthHelper {

    @Value("${channel.xiaohongshu.app-id:}")
    private String appId;

    @Value("${channel.xiaohongshu.app-secret:}")
    private String appSecret;

    @Value("${channel.xiaohongshu.access-token:}")
    private String accessToken;

    private static final String GATEWAY_URL = "https://ark.xiaohongshu.com/ark/open_api/v3/common_controller";

    /**
     * 计算请求签名（MD5）
     *
     * 步骤：
     * 1. 收集系统参数：appId, timestamp, version
     * 2. 按字母序排序
     * 3. 用 & 连接
     * 4. 前缀 method + "?"，后缀 appSecret
     * 5. MD5 哈希
     */
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

    /** 构建完整的请求 JSON 体（小红书所有参数都在 body 中） */
    public String buildRequestBody(String method, String businessParams) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String sign = calcSign(method, timestamp);

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
    public String getAccessToken() { return accessToken; }
}
