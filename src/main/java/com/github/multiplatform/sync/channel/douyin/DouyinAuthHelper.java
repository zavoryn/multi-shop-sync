package com.github.multiplatform.sync.channel.douyin;

import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.github.multiplatform.sync.common.auth.AccessTokenManager;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 抖音小店鉴权辅助类。
 * 负责签名计算和 Token 拉取（实际刷新由 {@link com.github.multiplatform.sync.common.auth.AccessTokenManager}）。
 *
 * 签名算法：HMAC-SHA256
 * 文档：https://op.jinritemai.com/docs/api-docs/14/56
 *
 * signPattern = appSecret + "app_key" + appKey + "method" + method
 *             + "param_json" + paramJson + "timestamp" + timestamp + "v" + v + appSecret
 * sign = HMAC-SHA256(signPattern, appSecret)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DouyinAuthHelper {

    @Value("${channel.douyin.app-key:}")
    private String appKey;

    @Value("${channel.douyin.app-secret:}")
    private String appSecret;

    private static final String BASE_URL = "https://openapi-fxg.jinritemai.com";

    private final AccessTokenManager tokenManager;

    /** 计算请求签名（HMAC-SHA256） */
    public String calcSign(String method, String paramJson, String timestamp) {
        String paramPattern = "app_key" + appKey
                + "method" + method
                + "param_json" + paramJson
                + "timestamp" + timestamp
                + "v" + "2";

        String signPattern = appSecret + paramPattern + appSecret;
        return new HMac(HmacAlgorithm.HmacSHA256, appSecret.getBytes(StandardCharsets.UTF_8))
                .digestHex(signPattern);
    }

    /** 构建完整的请求 URL */
    public String buildUrl(String method, String paramJson, String timestamp, String sign) {
        return BASE_URL + "/" + method.replace(".", "/")
                + "?method=" + method
                + "&app_key=" + appKey
                + "&access_token=" + getAccessToken()
                + "&timestamp=" + timestamp
                + "&v=2"
                + "&sign=" + sign
                + "&sign_method=hmac-sha256";
    }

    public String getAppKey() { return appKey; }
    public String getAppSecret() { return appSecret; }

    /** 取当前 access_token，未命中或过期自动 refresh */
    public String getAccessToken() {
        return tokenManager.getToken(ChannelEnum.DOUYIN);
    }
}
