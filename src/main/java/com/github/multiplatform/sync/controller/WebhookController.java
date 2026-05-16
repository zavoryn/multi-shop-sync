package com.github.multiplatform.sync.controller;

import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
import com.github.multiplatform.sync.common.model.ApiResponse;
import com.github.multiplatform.sync.service.ProductSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 统一 Webhook 回调入口。
 * 各平台将回调通知发送到此接口，系统根据路径中的渠道标识路由到对应的策略。
 *
 * 路由规则：
 * - POST /api/webhook/douyin       → 抖音 doudian_product_change 回调
 * - POST /api/webhook/xiaohongshu  → 小红书消息推送
 * - POST /api/webhook/wechat       → 微信小店事件通知（Phase 6 加上 AES-CBC 验签）
 *
 * 设计原则：
 * - 各平台对回调响应时间有强约束（微信 5s、小红书 2s），所以本入口必须尽快返回
 * - {@link ProductSyncService#handleCallback} 标注 {@code @Async("webhookExecutor")} 真正异步处理
 * - 微信回调的验签 / 解密在 Phase 6 单独走 WechatCallbackController，本控制器仅处理 JSON 类回调
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final ProductSyncService productSyncService;

    @PostMapping("/{channel}")
    public ApiResponse<Void> handleWebhook(
            @PathVariable String channel,
            @RequestBody Map<String, Object> body) {

        log.info("收到 [{}] 渠道回调", channel);

        try {
            ChannelEnum channelEnum = ChannelEnum.fromCode(channel);
            // 异步处理，主线程立刻返回
            productSyncService.handleCallback(channelEnum, body);
        } catch (ChannelException | IllegalArgumentException e) {
            // 未知渠道直接拒绝，但仍返回 200 避免平台无意义重试
            log.warn("回调渠道无效或被拒绝: channel={}, err={}", channel, e.getMessage());
        }

        return ApiResponse.success();
    }
}
