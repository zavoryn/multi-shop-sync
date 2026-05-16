package com.github.multiplatform.sync.service;

import com.github.multiplatform.sync.common.enums.ChannelEnum;

import java.util.Optional;

/**
 * 本地类目 ↔ 平台类目翻译。
 * Strategy 在 build 上传参数时调用 translate 拿到平台真实类目 ID。
 */
public interface CategoryMappingService {

    /**
     * 把本地类目翻译为目标渠道的外部类目 ID。
     * 找不到返回 Optional.empty()，调用方决定 fallback（抛异常或用默认值）。
     */
    Optional<String> translate(Long localCategoryId, ChannelEnum channel);

    /** 找不到时抛 ChannelException，简化业务侧调用 */
    String translateRequired(Long localCategoryId, ChannelEnum channel);
}
