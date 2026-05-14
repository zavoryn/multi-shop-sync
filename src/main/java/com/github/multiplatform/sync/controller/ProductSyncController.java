package com.github.multiplatform.sync.controller;

import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.common.model.ApiResponse;
import com.github.multiplatform.sync.service.ProductSyncService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductSyncController {

    private final ProductSyncService productSyncService;

    /**
     * 推送商品到指定渠道。
     *
     * POST /api/product/push
     * Body: { "channelCode": "DOUYIN", "product": { ... } }
     */
    @PostMapping("/push")
    public ApiResponse<Boolean> pushProduct(@Valid @RequestBody PushRequest request) {
        ChannelEnum channel = ChannelEnum.fromCode(request.getChannelCode());
        boolean result = productSyncService.pushProduct(channel, request.getProduct());
        return ApiResponse.success(result);
    }

    /**
     * 批量推送商品到多个渠道。
     *
     * POST /api/product/list
     */
    @PostMapping("/list")
    public ApiResponse<Void> pushToChannels(@Valid @RequestBody BatchPushRequest request) {
        List<ChannelEnum> channels = request.getChannelCodes().stream()
                .map(ChannelEnum::fromCode)
                .collect(java.util.stream.Collectors.toList());
        productSyncService.pushProductToChannels(request.getProduct(), channels);
        return ApiResponse.success();
    }

    /**
     * 统一上下架。
     *
     * POST /api/product/changeStatus
     */
    @PostMapping("/changeStatus")
    public ApiResponse<Boolean> changeStatus(@Valid @RequestBody StatusChangeRequest request) {
        ChannelEnum channel = ChannelEnum.fromCode(request.getChannelCode());
        boolean result = productSyncService.changeStatus(channel, request.getOutProductId(), request.getStatus());
        return ApiResponse.success(result);
    }

    /**
     * 主动同步外部平台状态（用于补偿机制）。
     *
     * POST /api/product/syncStatus
     */
    @PostMapping("/syncStatus")
    public ApiResponse<Void> syncStatus(@Valid @RequestBody SyncStatusRequest request) {
        ChannelEnum channel = ChannelEnum.fromCode(request.getChannelCode());
        productSyncService.syncPlatformStatus(channel, request.getOutProductId());
        return ApiResponse.success();
    }

    // ========== Request DTOs ==========

    @Data
    public static class PushRequest {
        @NotBlank(message = "渠道编码不能为空")
        private String channelCode;

        @NotNull(message = "商品信息不能为空")
        @Valid
        private StandardProductDTO product;
    }

    @Data
    public static class BatchPushRequest {
        @NotNull(message = "渠道列表不能为空")
        private List<String> channelCodes;

        @NotNull(message = "商品信息不能为空")
        @Valid
        private StandardProductDTO product;
    }

    @Data
    public static class StatusChangeRequest {
        @NotBlank(message = "渠道编码不能为空")
        private String channelCode;

        @NotBlank(message = "商品ID不能为空")
        private String outProductId;

        @NotNull(message = "目标状态不能为空")
        private ProductStatusEnum status;
    }

    @Data
    public static class SyncStatusRequest {
        @NotBlank(message = "渠道编码不能为空")
        private String channelCode;

        @NotBlank(message = "商品ID不能为空")
        private String outProductId;
    }
}
