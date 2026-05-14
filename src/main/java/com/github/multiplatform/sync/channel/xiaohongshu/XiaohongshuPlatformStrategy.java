package com.github.multiplatform.sync.channel.xiaohongshu;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.multiplatform.sync.common.dto.StandardProductDTO;
import com.github.multiplatform.sync.common.dto.StandardSkuDTO;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
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
    private final WebClient.Builder webClientBuilder;

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.XIAOHONGSHU;
    }

    @Override
    protected boolean doPushProduct(StandardProductDTO product) {
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

        log.info("小红书商品推送成功: outProductId={}", product.getOutProductId());
        return true;
    }

    @Override
    protected boolean doChangeStatus(String outProductId, ProductStatusEnum status) {
        JSONObject businessData = new JSONObject();
        businessData.put("skuId", outProductId);
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
    protected void doSyncPlatformStatus(String outProductId) {
        JSONObject businessData = new JSONObject();
        businessData.put("itemId", outProductId);

        String requestBody = authHelper.buildRequestBody("product.getItemInfo", businessData.toJSONString());

        String response = webClientBuilder.build().post()
                .uri(authHelper.getGatewayUrl())
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("小红书主动查询商品状态返回: {}", response);
        // TODO: 解析状态并更新本地数据库
    }

    /**
     * 解析小红书回调数据。
     * 回调文档：https://open.xiaohongshu.com/document/api
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
                // 需要进一步解析 data 中的 available 字段判断上架还是下架
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

    /**
     * 将标准模型转换为小红书 createItemAndSku 参数格式。
     *
     * 关键映射：
     * - 价格单位：标准模型和小红书都是「分」
     * - 类目：需要通过 CategoryMapping 转换
     * - 图片：小红书格式为 [{link: "url"}]
     * - SKU 变体：attributes Map 转为 variants 数组
     */
    private JSONObject buildCreateItemParam(StandardProductDTO product) {
        JSONObject param = new JSONObject();
        param.put("name", product.getTitle());
        param.put("categoryId", String.valueOf(product.getCategoryId()));
        param.put("shippingTemplateId", String.valueOf(product.getFreightTemplateId()));
        param.put("description", product.getDescription());

        // 图片格式转换
        JSONArray images = new JSONArray();
        for (String imgUrl : product.getMainImages()) {
            JSONObject img = new JSONObject();
            img.put("link", imgUrl);
            images.add(img);
        }
        param.put("images", images);

        // SKU 列表转换
        JSONArray skuList = new JSONArray();
        for (StandardSkuDTO sku : product.getSkus()) {
            JSONObject skuJson = new JSONObject();
            skuJson.put("price", sku.getPrice());
            skuJson.put("originalPrice", sku.getOriginalPrice());
            skuJson.put("stock", sku.getStock());
            skuJson.put("erpCode", sku.getSkuCode());

            // 变体转换
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
