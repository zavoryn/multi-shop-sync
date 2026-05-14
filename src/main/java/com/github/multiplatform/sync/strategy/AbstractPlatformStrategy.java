package com.github.multiplatform.sync.strategy;

import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Retryable;

import java.util.Map;

/**
 * 抽象策略基类（模板方法模式）。
 * 封装所有渠道的公共逻辑：参数校验、日志记录、重试、异常处理。
 * 子类只需实现 doXxx 方法，专注于平台差异化的 API 调用。
 */
@Slf4j
public abstract class AbstractPlatformStrategy implements IPlatformProductStrategy {

    // ==================== 模板方法 ====================

    @Override
    @Retryable(value = {ChannelException.class}, maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 1000))
    public boolean pushProduct(StandardProductDTO product) {
        String channel = getChannel().getCode();
        log.info("[{}] 开始推送商品: outProductId={}, title={}", channel, product.getOutProductId(), product.getTitle());

        try {
            validateProduct(product);
            boolean result = doPushProduct(product);
            log.info("[{}] 推送商品完成: outProductId={}, result={}", channel, product.getOutProductId(), result);
            return result;
        } catch (ChannelException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] 推送商品异常: outProductId={}", channel, product.getOutProductId(), e);
            throw new ChannelException(channel, "推送商品失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean changeStatus(String outProductId, ProductStatusEnum status) {
        String channel = getChannel().getCode();
        log.info("[{}] 变更商品状态: outProductId={}, targetStatus={}", channel, outProductId, status);

        try {
            boolean result = doChangeStatus(outProductId, status);
            log.info("[{}] 状态变更完成: outProductId={}, result={}", channel, outProductId, result);
            return result;
        } catch (Exception e) {
            log.error("[{}] 状态变更异常: outProductId={}", channel, outProductId, e);
            throw new ChannelException(channel, "状态变更失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void syncPlatformStatus(String outProductId) {
        String channel = getChannel().getCode();
        log.info("[{}] 主动同步状态: outProductId={}", channel, outProductId);

        try {
            doSyncPlatformStatus(outProductId);
        } catch (Exception e) {
            log.error("[{}] 同步状态异常: outProductId={}", channel, outProductId, e);
            throw new ChannelException(channel, "同步状态失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ProductStatusEnum parseCallback(Map<String, Object> callbackData) {
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

    // ==================== 子类实现的抽象方法 ====================

    protected abstract boolean doPushProduct(StandardProductDTO product);

    protected abstract boolean doChangeStatus(String outProductId, ProductStatusEnum status);

    protected abstract void doSyncPlatformStatus(String outProductId);

    protected abstract ProductStatusEnum doParseCallback(Map<String, Object> callbackData);
}
