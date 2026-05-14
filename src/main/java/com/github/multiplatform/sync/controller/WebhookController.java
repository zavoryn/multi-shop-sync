package com.github.multiplatform.sync.controller;

import com.github.multiplatform.sync.common.enums.ChannelEnum;
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
 * - POST /api/webhook/douyin     → 抖音 doudian_product_change 回调
 * - POST /api/webhook/xiaohongshu → 小红书消息推送
 * - POST /api/webhook/wechat     → 微信小店事件通知
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final ProductSyncService productSyncService;

    /**
     * 统一回调入口。
     * 路径变量 channel 为渠道标识（douyin/xiaohongshu/wechat）。
     *
     * 重要：各平台要求快速响应（微信5秒，小红书2秒），
     * 所以这里先返回成功，再异步处理业务逻辑。
     */
    @PostMapping("/{channel}")
    public ApiResponse<Void> handleWebhook(
            @PathVariable String channel,
            @RequestBody Map<String, Object> body) {

        log.info("收到 [{}] 渠道回调: body={}", channel, body);

        try {
            ChannelEnum channelEnum = ChannelEnum.fromCode(channel);
            // 异步处理回调，确保快速响应
            productSyncService.handleCallback(channelEnum, body);
        } catch (Exception e) {
            log.error("回调处理异常: channel={}", channel, e);
            // 仍然返回成功，避免平台重试
        }

        return ApiResponse.success();
    }

    /**
     * 微信回调需要特殊的验签逻辑（可选）。
     * 微信消息体可能是 XML 格式，这里用 Map 接收 JSON 示例。
     */
    @PostMapping("/wechat")
    public Map<String, String> handleWechatWebhook(@RequestBody String body) {
        log.info("收到微信回调: body={}", body);

        // TODO: 微信回调验签（AES-CBC 解密）
        // TODO: 解析 XML/JSON 格式
        // TODO: 调用 productSyncService.handleCallback(ChannelEnum.WECHAT, parsedData)

        return Map.of("return_code", "SUCCESS", "return_msg", "OK");
    }
}
