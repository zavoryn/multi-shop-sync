package com.github.multiplatform.sync.channel.xiaohongshu;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.dto.StandardSkuDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
import com.github.multiplatform.sync.common.model.PushResult;
import com.github.multiplatform.sync.service.CategoryMappingService;
import com.github.multiplatform.sync.strategy.AbstractPlatformStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小红书策略实现。
 *
 * API 文档：https://open.xiaohongshu.com/document/api
 *
 * 核心流程：
 * 1. 将 StandardProductDTO 转换为小红书 createItemAndSku 格式
 * 2. MD5 签名并调用小红书 API
 * 3. 解析回调中的 msgTag（msg_item_buyable=上架, msg_item_audit_reject=驳回）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XiaohongshuPlatformStrategy extends AbstractPlatformStrategy {

    private final XiaohongshuAuthHelper authHelper;
    private final CategoryMappingService categoryMappingService;
    private final WebClient.Builder webClientBuilder;

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.XIAOHONGSHU;
    }

    @Override
    protected PushResult doPushProduct(StandardProductDTO product) {
        JSONObject businessData = buildCreateItemParam(product);
        String requestBody = authHelper.buildRequestBody("product.createItemAndSku", businessData.toJSONString());

        String response = webClientBuilder.build().post()
                .uri(authHelper.getGatewayUrl())
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject result = JSON.parseObject(response);
        if (!result.getBooleanValue("success")) {
            throw new ChannelException("xiaohongshu", "商品推送失败: " + result.getString("error_msg"));
        }

        // 小红书 createItemAndSku 返回 data.itemId
        JSONObject data = result.getJSONObject("data");
        String itemId = data == null ? null : data.getString("itemId");
        log.info("小红书商品推送成功: outProductId={}, itemId={}", product.getOutProductId(), itemId);
        return PushResult.ok(itemId);
    }

    @Override
    protected boolean doChangeStatus(String channelProductId, ProductStatusEnum status) {
        JSONObject businessData = new JSONObject();
        businessData.put("itemId", channelProductId);
        businessData.put("available", status == ProductStatusEnum.ON_SHELF);

        String requestBody = authHelper.buildRequestBody("product.updateSkuAvailable", businessData.toJSONString());

        String response = webClientBuilder.build().post()
                .uri(authHelper.getGatewayUrl())
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject result = JSON.parseObject(response);
        return result.getBooleanValue("success");
    }

    @Override
    protected ProductStatusEnum doSyncPlatformStatus(String channelProductId) {
        // 用 product.getDetailItemList 按 id 单查（原 product.getItemInfo 在官方 API 索引中不存在）
        JSONObject businessData = new JSONObject();
        businessData.put("id", channelProductId);
        businessData.put("pageNo", 1);
        businessData.put("pageSize", 1);

        String requestBody = authHelper.buildRequestBody("product.getDetailItemList", businessData.toJSONString());

        String response = webClientBuilder.build().post()
                .uri(authHelper.getGatewayUrl())
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject result = JSON.parseObject(response);
        if (result == null || !result.getBooleanValue("success")) {
            log.warn("小红书查询商品状态失败: response={}", response);
            return null;
        }
        JSONArray dataList = result.getJSONArray("data");
        if (dataList == null || dataList.isEmpty()) {
            log.warn("小红书查询返回空 data: id={}", channelProductId);
            return null;
        }
        JSONObject itemData = dataList.getJSONObject(0).getJSONObject("itemData");
        if (itemData == null) {
            log.warn("小红书查询返回缺少 itemData: id={}", channelProductId);
            return null;
        }
        // freeze 不在标准 getDetailItemList 响应中暴露，但可能在某些版本扩展返回；保守按 false 处理
        boolean buyable = itemData.getBooleanValue("buyable");
        boolean freeze = itemData.getBooleanValue("freeze");
        return mapStatus(buyable, freeze);
    }

    /**
     * 小红书 buyable + freeze → 标准状态。
     * 详见 docs/research/platform-status-mapping.md
     *
     * 注：审核中 / 驳回 通过回调 msgTag 异步感知，主动查询接口拿不到。
     */
    static ProductStatusEnum mapStatus(boolean buyable, boolean freeze) {
        if (freeze) return ProductStatusEnum.OFF_SHELF;
        return buyable ? ProductStatusEnum.ON_SHELF : ProductStatusEnum.OFF_SHELF;
    }

    /**
     * 解析小红书回调数据。
     * msgTag: msg_item_buyable=上架/下架, msg_item_audit_reject=审核驳回, msg_item_create=创建
     */
    @Override
    protected ProductStatusEnum doParseCallback(Map<String, Object> callbackData) {
        Object msgTag = callbackData.get("msgTag");
        if (msgTag == null) {
            return null;
        }

        switch (msgTag.toString()) {
            case "msg_item_buyable":
                return ProductStatusEnum.ON_SHELF;
            case "msg_item_audit_reject":
                return ProductStatusEnum.AUDIT_REJECT;
            case "msg_item_create":
                return ProductStatusEnum.WAIT_PLATFORM_AUDIT;
            default:
                log.warn("小红书未处理的回调事件: msgTag={}", msgTag);
                return null;
        }
    }

    private JSONObject buildCreateItemParam(StandardProductDTO product) {
        String externalCategoryId = categoryMappingService.translateRequired(product.getCategoryId(), ChannelEnum.XIAOHONGSHU);

        JSONObject param = new JSONObject();
        param.put("name", product.getTitle());
        param.put("categoryId", externalCategoryId);
        param.put("shippingTemplateId", String.valueOf(product.getFreightTemplateId()));
        param.put("description", product.getDescription());

        JSONArray images = new JSONArray();
        for (String imgUrl : product.getMainImages()) {
            JSONObject img = new JSONObject();
            img.put("link", imgUrl);
            images.add(img);
        }
        param.put("images", images);

        JSONArray skuList = new JSONArray();
        for (StandardSkuDTO sku : product.getSkus()) {
            JSONObject skuJson = new JSONObject();
            skuJson.put("price", sku.getPrice());
            skuJson.put("originalPrice", sku.getOriginalPrice());
            skuJson.put("stock", sku.getStock());
            skuJson.put("erpCode", sku.getSkuCode());

            if (sku.getAttributes() != null) {
                List<Map<String, String>> variants = sku.getAttributes().entrySet().stream()
                        .map(e -> {
                            Map<String, String> v = new HashMap<>();
                            v.put("name", e.getKey());
                            v.put("value", e.getValue());
                            return v;
                        })
                        .collect(Collectors.toList());
                skuJson.put("variants", variants);
            }
            skuList.add(skuJson);
        }
        param.put("createSkuList", skuList);

        return param;
    }
}
