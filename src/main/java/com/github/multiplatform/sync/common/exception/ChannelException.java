package com.github.multiplatform.sync.common.exception;

import lombok.Getter;

@Getter
public class ChannelException extends RuntimeException {

    private final String channelCode;

    public ChannelException(String channelCode, String message) {
        super("[" + channelCode + "] " + message);
        this.channelCode = channelCode;
    }

    public ChannelException(String channelCode, String message, Throwable cause) {
        super("[" + channelCode + "] " + message, cause);
        this.channelCode = channelCode;
    }
}
