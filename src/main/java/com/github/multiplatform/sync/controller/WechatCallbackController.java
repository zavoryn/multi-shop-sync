package com.github.multiplatform.sync.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.multiplatform.sync.channel.wechat.WechatCryptoUtil;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.config.ChannelConfig;
import com.github.multiplatform.sync.service.ProductSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微信小店 / 视频号小店回调专用入口。
 *
 * 此控制器精确匹配 /api/webhook/wechat，优先级高于 {@link WebhookController} 的 /{channel} 通配，
 * 因为微信回调必须做 AES-CBC 解密 + sha1 验签后才能拿到业务数据。
 *
 * 协议：
 * - query: msg_signature / timestamp / nonce
 * - body: { "ToUserName": "...", "Encrypt": "<base64 密文>" }
 * - 响应 SLA: 5 秒；handleCallback 标 @Async 异步处理
 *
 * 验签失败 → 401（拒绝重试也是合理的，因为伪造请求理应被丢弃）。
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook/wechat")
@RequiredArgsConstructor
public class WechatCallbackController {

    private final ChannelConfig channelConfig;
    private final ProductSyncService productSyncService;

    @PostMapping
    public ResponseEntity<String> handleWechatCallback(
            @RequestParam("msg_signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestBody String rawBody) {

        ChannelConfig.WechatConfig cfg = channelConfig.getWechat();
        if (cfg.getCallbackToken() == null || cfg.getCallbackToken().isEmpty()
                || cfg.getEncodingAesKey() == null || cfg.getEncodingAesKey().isEmpty()) {
            log.warn("微信回调被调用但 callbackToken / encodingAesKey 未配置，直接拒绝");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("not configured");
        }

        // 1. 取出 Encrypt 字段（body 可能是 JSON 或 XML，本项目按 JSON 处理；XML 场景另接）
        JSONObject body = JSON.parseObject(rawBody);
        String encrypted = body == null ? null : body.getString("Encrypt");
        if (encrypted == null) {
            log.warn("微信回调缺少 Encrypt 字段");
            return ResponseEntity.badRequest().body("missing Encrypt");
        }

        // 2. 验签
        if (!WechatCryptoUtil.verifySignature(cfg.getCallbackToken(), timestamp, nonce, encrypted, signature)) {
            log.warn("微信回调验签失败: signature={}", signature);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }

        // 3. 解密
        String plaintext;
        try {
            plaintext = WechatCryptoUtil.decrypt(cfg.getEncodingAesKey(), encrypted, cfg.getAppId());
        } catch (WechatCryptoUtil.CryptoException e) {
            log.warn("微信回调解密失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("decrypt failed");
        }

        // 4. 解析明文为 Map → 走统一回调链
        try {
            JSONObject decoded = JSON.parseObject(plaintext);
            @SuppressWarnings("unchecked")
            Map<String, Object> callbackData = (Map<String, Object>) (Map<?, ?>) decoded;
            productSyncService.handleCallback(ChannelEnum.WECHAT, callbackData);
        } catch (Exception e) {
            log.error("微信回调明文解析失败: plaintext={}", plaintext, e);
            // 验签已通过，业务解析失败仍返回 200，避免微信无意义重试
        }

        return ResponseEntity.ok("success");
    }
}
