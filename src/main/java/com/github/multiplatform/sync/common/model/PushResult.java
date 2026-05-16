package com.github.multiplatform.sync.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品推送结果。
 *
 * @param success           推送是否成功
 * @param channelProductId  渠道侧的商品 ID（外部平台分配，本地渠道则等于 outProductId）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PushResult {
    private boolean success;
    private String channelProductId;

    public static PushResult ok(String channelProductId) {
        return new PushResult(true, channelProductId);
    }

    public static PushResult fail() {
        return new PushResult(false, null);
    }
}
