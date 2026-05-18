package com.github.multiplatform.sync.common.enums;

import com.github.multiplatform.sync.common.exception.ChannelException;
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

    /**
     * 字符串 code → 枚举。
     * 抛 {@link ChannelException}（而非 IllegalArgumentException），由
     * GlobalExceptionHandler 统一映射为 400 + 友好消息。
     */
    public static ChannelEnum fromCode(String code) {
        if (code != null) {
            for (ChannelEnum channel : values()) {
                if (channel.code.equalsIgnoreCase(code)) {
                    return channel;
                }
            }
        }
        throw new ChannelException(code == null ? "null" : code, "未知渠道编码: " + code);
    }
}
