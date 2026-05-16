package com.github.multiplatform.sync.common.auth;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * 不可变 Token 信息。
 *
 * - 通过 {@link #ofTtl(String, Duration)} 由具体 RefreshStrategy 构造
 * - {@link #isAboutToExpire(Duration)} 用于 Scheduler 决定是否提前刷新
 */
@Data
@RequiredArgsConstructor
public class TokenInfo {

    private final String token;
    /** 平台返回的过期时刻（绝对时间，避免相对计算误差） */
    private final Instant expiresAt;

    /** 平台一般给的是 expires_in 秒数；这里转成绝对时间 */
    public static TokenInfo ofTtl(String token, Duration ttl) {
        return new TokenInfo(token, Instant.now().plus(ttl));
    }

    /** 当前 token 是否仍然有效 */
    public boolean isValid() {
        return token != null && !token.isEmpty() && Instant.now().isBefore(expiresAt);
    }

    /** 距离过期 <= threshold 时返回 true（含已过期） */
    public boolean isAboutToExpire(Duration threshold) {
        return Instant.now().plus(threshold).isAfter(expiresAt);
    }
}
