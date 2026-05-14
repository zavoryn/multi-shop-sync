package com.github.multiplatform.sync.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChannelEnum {

    LOCAL("local", "本地商城"),
    DOUYIN("douyin", "抖音小店"),
    XIAOHONGSHU("xiaohongshu", "小红书"),
    WECHAT("wechat", "微信小店");

    private final String code;
    private final String name;

    public static ChannelEnum fromCode(String code) {
        for (ChannelEnum channel : values()) {
            if (channel.code.equalsIgnoreCase(code)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("未知渠道编码: " + code);
    }
}
