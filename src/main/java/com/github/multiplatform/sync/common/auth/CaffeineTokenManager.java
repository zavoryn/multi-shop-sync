package com.github.multiplatform.sync.common.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 Caffeine 的 Token 管理实现。
 *
 * 设计要点：
 * - Caffeine 只作为 KV 缓存，TTL 由 {@link TokenInfo#expiresAt} 自管理（黑盒 expireAfter
 *   触发时机不可控，且 cache miss → fetch 全靠 caller，逻辑分散）
 * - 单飞：每个渠道一个 ReentrantLock；并发 getToken 中只有第一个真正调 fetch，
 *   其余阻塞等待并复用结果
 * - LOCAL 渠道无 token，调用直接返回空串（不进入 cache）
 */
@Slf4j
@Component
public class CaffeineTokenManager implements AccessTokenManager {

    private final Cache<ChannelEnum, TokenInfo> cache = Caffeine.newBuilder().maximumSize(16).build();
    private final Map<ChannelEnum, ReentrantLock> locks = new EnumMap<>(ChannelEnum.class);
    private final Map<ChannelEnum, RefreshStrategy> strategies = new EnumMap<>(ChannelEnum.class);

    public CaffeineTokenManager(List<RefreshStrategy> refreshStrategies) {
        for (RefreshStrategy s : refreshStrategies) {
            strategies.put(s.getChannel(), s);
            locks.put(s.getChannel(), new ReentrantLock());
        }
        log.info("CaffeineTokenManager 初始化完成，已注册渠道: {}", strategies.keySet());
    }

    @Override
    public String getToken(ChannelEnum channel) {
        if (channel == ChannelEnum.LOCAL) {
            return "";
        }
        TokenInfo cached = cache.getIfPresent(channel);
        if (cached != null && cached.isValid()) {
            return cached.getToken();
        }
        return doRefresh(channel, "miss-or-expired").getToken();
    }

    @Override
    public String refresh(ChannelEnum channel) {
        if (channel == ChannelEnum.LOCAL) {
            return "";
        }
        // 强制刷新必须先 invalidate，否则 doRefresh 内的 double-check 会命中缓存直接返回旧 token
        cache.invalidate(channel);
        return doRefresh(channel, "forced").getToken();
    }

    @Override
    public TokenInfo peek(ChannelEnum channel) {
        return cache.getIfPresent(channel);
    }

    /**
     * 真正的刷新逻辑。
     *
     * 单飞实现：lock 内 double-check —— 多个等待线程拿锁后再读一次 cache，
     * 如果已经被前一个线程刷新好则直接复用，避免重复 fetch。
     */
    private TokenInfo doRefresh(ChannelEnum channel, String reason) {
        RefreshStrategy strategy = strategies.get(channel);
        if (strategy == null) {
            throw new ChannelException(channel.getCode(), "未注册 RefreshStrategy，无法获取 token");
        }
        ReentrantLock lock = locks.get(channel);
        lock.lock();
        try {
            TokenInfo cached = cache.getIfPresent(channel);
            if (cached != null && cached.isValid()) {
                return cached;  // double-check 命中，等待线程直接复用
            }
            log.info("[{}] 触发 token 刷新: reason={}", channel.getCode(), reason);
            TokenInfo fresh = strategy.fetch();
            if (fresh == null || fresh.getToken() == null || fresh.getToken().isEmpty()) {
                throw new ChannelException(channel.getCode(), "RefreshStrategy 返回空 token");
            }
            cache.put(channel, fresh);
            log.info("[{}] token 刷新成功，下次过期时刻: {}", channel.getCode(), fresh.getExpiresAt());
            return fresh;
        } finally {
            lock.unlock();
        }
    }
}
