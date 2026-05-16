package com.github.multiplatform.sync.config;

import com.github.multiplatform.sync.common.auth.AccessTokenManager;
import com.github.multiplatform.sync.common.auth.TokenInfo;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumSet;

/**
 * Token 定时预刷新。
 *
 * 每 60s 扫描所有外部渠道的 token 缓存：
 * - 若快过期（剩余 < 10min），主动 refresh
 * - 若未缓存或调用失败，记 WARN 但不抛错（避免拖垮其他渠道）
 *
 * 这是"被动 refresh + 主动预热"的双保险：
 * 即使没有定时刷新，getToken 时也会按需 refresh；定时只是减少业务侧首次调用的延迟尖刺。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenScheduler {

    private static final Duration REFRESH_THRESHOLD = Duration.ofMinutes(10);

    private final AccessTokenManager tokenManager;

    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void refreshExpiringTokens() {
        for (ChannelEnum channel : EnumSet.of(ChannelEnum.DOUYIN, ChannelEnum.XIAOHONGSHU, ChannelEnum.WECHAT)) {
            try {
                TokenInfo info = tokenManager.peek(channel);
                if (info == null || info.isAboutToExpire(REFRESH_THRESHOLD)) {
                    log.debug("[{}] 触发预刷新（cached={}）", channel.getCode(), info != null);
                    tokenManager.refresh(channel);
                }
            } catch (Exception e) {
                // 某个渠道刷新失败不影响其他渠道；业务侧首次调用时还会按需 refresh，可二次容错
                log.warn("[{}] 定时预刷新失败: {}", channel.getCode(), e.getMessage());
            }
        }
    }
}
