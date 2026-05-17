package com.github.multiplatform.sync.common.exception;

/**
 * 渠道鉴权异常：access_token 过期 / 签名失败 / 应用未授权 等。
 *
 * 上层捕获后应触发 {@link com.github.multiplatform.sync.common.auth.AccessTokenManager#refresh}
 * 强制刷新 token，然后重新发起请求。GlobalExceptionHandler 默认返回 401。
 */
public class ChannelAuthException extends ChannelException {

    public ChannelAuthException(String channelCode, String message) {
        super(channelCode, message);
    }

    public ChannelAuthException(String channelCode, String message, Throwable cause) {
        super(channelCode, message, cause);
    }
}
