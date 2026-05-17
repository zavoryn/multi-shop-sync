package com.github.multiplatform.sync.strategy;

import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
import com.github.multiplatform.sync.common.exception.ChannelNetworkException;
import com.github.multiplatform.sync.common.model.PushResult;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * 抽象策略基类（模板方法模式）。
 * 封装所有渠道的公共逻辑：参数校验、日志记录、限流、重试、异常包装。
 * 子类只需实现 doXxx 方法，专注于平台差异化的 API 调用。
 *
 * Phase 7 调整：
 * - @Retryable 只对 {@link ChannelNetworkException} 重试，业务错与鉴权错不重试
 * - changeStatus / syncPlatformStatus 也加上 @Retryable
 * - 入口接入 Resilience4j RateLimiter（每个渠道实例独立配置），LOCAL 跳过
 */
@Slf4j
public abstract class AbstractPlatformStrategy implements IPlatformProductStrategy {

    /**
     * RateLimiterRegistry 由 Spring 注入；如果 resilience4j 配置缺失则为 null（LOCAL 渠道场景），
     * acquirePermit 会自动跳过。
     */
    @Autowired(required = false)
    private RateLimiterRegistry rateLimiterRegistry;

    // ==================== 模板方法 ====================

    @Override
    @Retryable(value = {ChannelNetworkException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public PushResult pushProduct(StandardProductDTO product) {
        String channel = getChannel().getCode();
        log.info("[{}] 开始推送商品: outProductId={}, title={}", channel, product.getOutProductId(), product.getTitle());

        acquirePermit();
        try {
            validateProduct(product);
            PushResult result = doPushProduct(product);
            log.info("[{}] 推送商品完成: outProductId={}, channelProductId={}", channel,
                    product.getOutProductId(), result == null ? null : result.getChannelProductId());
            return result;
        } catch (ChannelException | IllegalStateException e) {
            throw e;  // 已分类或状态机异常直接冒泡
        } catch (Exception e) {
            log.error("[{}] 推送商品异常: outProductId={}", channel, product.getOutProductId(), e);
            throw classify(channel, "推送商品失败", e);
        }
    }

    @Override
    @Retryable(value = {ChannelNetworkException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public boolean changeStatus(String channelProductId, ProductStatusEnum status) {
        String channel = getChannel().getCode();
        log.info("[{}] 变更商品状态: channelProductId={}, targetStatus={}", channel, channelProductId, status);

        acquirePermit();
        try {
            boolean result = doChangeStatus(channelProductId, status);
            log.info("[{}] 状态变更完成: channelProductId={}, result={}", channel, channelProductId, result);
            return result;
        } catch (ChannelException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] 状态变更异常: channelProductId={}", channel, channelProductId, e);
            throw classify(channel, "状态变更失败", e);
        }
    }

    @Override
    @Retryable(value = {ChannelNetworkException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public ProductStatusEnum syncPlatformStatus(String channelProductId) {
        String channel = getChannel().getCode();
        log.info("[{}] 主动同步状态: channelProductId={}", channel, channelProductId);

        acquirePermit();
        try {
            return doSyncPlatformStatus(channelProductId);
        } catch (ChannelException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] 同步状态异常: channelProductId={}", channel, channelProductId, e);
            throw classify(channel, "同步状态失败", e);
        }
    }

    @Override
    public ProductStatusEnum parseCallback(Map<String, Object> callbackData) {
        // 回调解析是纯本地计算，不限流也不重试
        String channel = getChannel().getCode();
        log.info("[{}] 收到回调数据: {}", channel, callbackData);

        try {
            ProductStatusEnum status = doParseCallback(callbackData);
            log.info("[{}] 回调解析结果: status={}", channel, status);
            return status;
        } catch (Exception e) {
            log.error("[{}] 回调解析异常", channel, e);
            throw new ChannelException(channel, "回调解析失败: " + e.getMessage(), e);
        }
    }

    // ==================== 公共逻辑 ====================

    protected void validateProduct(StandardProductDTO product) {
        if (product.getTitle() == null || product.getTitle().trim().isEmpty()) {
            throw new ChannelException(getChannel().getCode(), "商品标题不能为空");
        }
        if (product.getSkus() == null || product.getSkus().isEmpty()) {
            throw new ChannelException(getChannel().getCode(), "SKU列表不能为空");
        }
    }

    /**
     * 把底层异常分类：
     * - WebClient 5xx / 连接异常 → ChannelNetworkException（可重试）
     * - 其他未知 → ChannelException（不重试，默认走 GlobalExceptionHandler 400）
     *
     * Strategy 的"业务码错误"（如 errcode=40001）应该在 Strategy 内部主动抛 ChannelBusinessException
     * 或 ChannelAuthException；此辅助方法不去解析平台业务码。
     */
    private ChannelException classify(String channel, String prefix, Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException) {
                WebClientResponseException w = (WebClientResponseException) t;
                if (w.getStatusCode().is5xxServerError()) {
                    return new ChannelNetworkException(channel, prefix + ": HTTP " + w.getStatusCode(), e);
                }
            } else if (t instanceof WebClientRequestException
                    || t instanceof java.net.SocketTimeoutException
                    || t instanceof java.net.ConnectException) {
                return new ChannelNetworkException(channel, prefix + ": " + t.getMessage(), e);
            }
        }
        return new ChannelException(channel, prefix + ": " + e.getMessage(), e);
    }

    /**
     * 限流入口：对该渠道的每次调用 acquire 一个 permit。
     * - LOCAL 渠道跳过
     * - 未在 resilience4j 配置中定义的渠道也跳过（首次调用时 registry 会拿到默认值）
     * - acquire 超时会抛 RequestNotPermitted，被外层 catch 包成 ChannelException
     */
    private void acquirePermit() {
        if (rateLimiterRegistry == null || getChannel() == ChannelEnum.LOCAL) return;
        String name = getChannel().getCode();
        RateLimiter limiter = rateLimiterRegistry.rateLimiter(name);
        RateLimiter.waitForPermission(limiter);
    }

    // ==================== 子类实现的抽象方法 ====================

    protected abstract PushResult doPushProduct(StandardProductDTO product);

    protected abstract boolean doChangeStatus(String channelProductId, ProductStatusEnum status);

    protected abstract ProductStatusEnum doSyncPlatformStatus(String channelProductId);

    protected abstract ProductStatusEnum doParseCallback(Map<String, Object> callbackData);
}
