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
        JSONObject businessData = new JSONObject();
        businessData.put("itemId", channelProductId);

        String requestBody = authHelper.buildRequestBody("product.getItemInfo", businessData.toJSONString());

        String response = webClientBuilder.build().post()
                .uri(authHelper.getGatewayUrl())
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("小红书主动查询商品状态返回: {}", response);
        return null; // Phase 5: itemStatus → 标准枚举（具体取值待官方文档核对）
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
