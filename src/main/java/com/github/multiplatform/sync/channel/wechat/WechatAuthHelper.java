package com.github.multiplatform.sync.channel.wechat;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微信小店鉴权辅助类。
 * 负责获取和缓存 Access Token。
 *
 * 文档：https://developers.weixin.qq.com/doc/store/shop/API/channels-shop-product/shop/api_token.html
 */
@Slf4j
@Component
public class WechatAuthHelper {

    @Value("${channel.wechat.app-id:}")
    private String appId;

    @Value("${channel.wechat.app-secret:}")
    private String appSecret;

    @Value("${channel.wechat.access-token:}")
    private String accessToken;

    private static final String BASE_URL = "https://api.weixin.qq.com";

    /**
     * 获取 Access Token。
     * 优先使用配置中的 token，如需刷新可调用 refreshToken()。
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * 从微信服务器刷新 Access Token。
     * 有效期 7200 秒。
     */
    public String refreshToken() {
        String url = BASE_URL + "/cgi-bin/token?grant_type=client_credential&appid=" + appId + "&secret=" + appSecret;
        String response = HttpUtil.get(url);
        JSONObject result = JSON.parseObject(response);

        if (result.containsKey("access_token")) {
            accessToken = result.getString("access_token");
            log.info("微信 Access Token 刷新成功，有效期: {}秒", result.getIntValue("expires_in"));
            return accessToken;
        }

        throw new RuntimeException("微信 Token 刷新失败: " + result.getString("errmsg"));
    }

    public String getAppId() { return appId; }
    public String getAppSecret() { return appSecret; }
}
