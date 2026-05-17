package com.github.multiplatform.sync.common.exception;

/**
 * 渠道业务异常：4xx / 业务错误码（如类目不存在、字段非法、SKU 冲突等）。
 * 不应重试，重试只会浪费请求；由调用方修正参数后重新提交。
 *
 * GlobalExceptionHandler 默认返回 422 Unprocessable Entity。
 */
public class ChannelBusinessException extends ChannelException {

    private final String platformErrorCode;

    public ChannelBusinessException(String channelCode, String platformErrorCode, String message) {
        super(channelCode, message);
        this.platformErrorCode = platformErrorCode;
    }

    public String getPlatformErrorCode() {
        return platformErrorCode;
    }
}
