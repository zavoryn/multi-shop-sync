package com.github.multiplatform.sync.common.exception;

/**
 * 渠道网络层异常：连接失败 / 超时 / 5xx / DNS 等。
 * 是 {@link AbstractPlatformStrategy} 唯一会触发 @Retryable 重试的异常类型。
 *
 * @see com.github.multiplatform.sync.strategy.AbstractPlatformStrategy
 */
public class ChannelNetworkException extends ChannelException {

    public ChannelNetworkException(String channelCode, String message) {
        super(channelCode, message);
    }

    public ChannelNetworkException(String channelCode, String message, Throwable cause) {
        super(channelCode, message, cause);
    }
}
