package com.github.multiplatform.sync.channel.douyin;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.multiplatform.sync.common.dto.StandardProductDTO;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 抖音小店策略实现。
 *
 * API 文档：https://op.jinritemai.com/docs/api-docs/14/5980
 * 回调文档：https://op.jinritemai.com/docs/api-docs/14/56
 *
 * 核心流程：
 * 1. 将 StandardProductDTO 转换为抖店 product.addV2 格式
 * 2. 签名并调用抖店 API
 * 3. 解析回调中的 event 字段（4=审核通过, 5=审核驳回, 10=下架, 11=上架）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DouyinPlatformStrategy extends AbstractPlatformStrategy {

    private final DouyinAuthHelper authHelper;
    private final CategoryMappingService categoryMappingService;
    private final WebClient.Builder webClientBuilder;

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.DOUYIN;
    }

    @Override
    protected PushResult doPushProduct(StandardProductDTO product) {
        JSONObject paramJson = buildAddProductParam(product);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String sign = authHelper.calcSign("product.addV2", paramJson.toJSONString(), timestamp);
        String url = authHelper.buildUrl("product.addV2", paramJson.toJSONString(), timestamp, sign);

        String response = webClientBuilder.build().post()
                .uri(url)
                .bodyValue(paramJson.toJSONString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject result = JSON.parseObject(response);
        int code = result.getIntValue("code");
        if (code != 10000) {
            throw new ChannelException("douyin", "商品推送失败: " + result.getString("msg"));
        }

        // 抖音商品创建成功后，data.product_id 为平台分配的商品 ID
        JSONObject data = result.getJSONObject("data");
        String productId = data == null ? null : data.getString("product_id");
        log.info("抖音商品推送成功: outProductId={}, productId={}", product.getOutProductId(), productId);
        return PushResult.ok(productId);
    }

    @Override
    protected boolean doChangeStatus(String channelProductId, ProductStatusEnum status) {
        String method = (status == ProductStatusEnum.ON_SHELF) ? "product.launchProduct" : "product.setOffline";
        JSONObject paramJson = new JSONObject();
        paramJson.put("product_id", Long.parseLong(channelProductId));

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String sign = authHelper.calcSign(method, paramJson.toJSONString(), timestamp);
        String url = authHelper.buildUrl(method, paramJson.toJSONString(), timestamp, sign);

        String response = webClientBuilder.build().post()
                .uri(url)
                .bodyValue(paramJson.toJSONString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject result = JSON.parseObject(response);
        return result.getIntValue("code") == 10000;
    }

    @Override
    protected ProductStatusEnum doSyncPlatformStatus(String channelProductId) {
        JSONObject paramJson = new JSONObject();
        paramJson.put("product_id", Long.parseLong(channelProductId));

        String method = "product.detail";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String sign = authHelper.calcSign(method, paramJson.toJSONString(), timestamp);
        String url = authHelper.buildUrl(method, paramJson.toJSONString(), timestamp, sign);

        String response = webClientBuilder.build().post()
                .uri(url)
                .bodyValue(paramJson.toJSONString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject result = JSON.parseObject(response);
        if (result == null || result.getIntValue("code") != 10000) {
            log.warn("抖音查询商品状态失败: response={}", response);
            return null;
        }
        JSONObject data = result.getJSONObject("data");
        if (data == null || !data.containsKey("status")) {
            log.warn("抖音查询返回缺少 status 字段: {}", response);
            return null;
        }
        return mapStatus(data.getIntValue("status"));
    }

    /**
     * 抖音 product.detail.status → 标准状态。
     * 官方状态机：https://op.jinritemai.com/docs/question-docs/92/2070
     * 0=在线 / 1=下线 / 2=删除
     */
    static ProductStatusEnum mapStatus(int status) {
        switch (status) {
            case 0:  return ProductStatusEnum.ON_SHELF;
            case 1:  return ProductStatusEnum.OFF_SHELF;
            case 2:  return ProductStatusEnum.OFF_SHELF;
            default:
                log.warn("抖音未知 status: {}", status);
                return null;
        }
    }

    /**
     * 解析抖音回调数据。Tag=400, event:
     * 4=审核通过, 5=审核驳回, 10=下架, 11=上架
     */
    @Override
    protected ProductStatusEnum doParseCallback(Map<String, Object> callbackData) {
        Object eventData = callbackData.get("event");
        if (eventData == null) {
            return null;
        }

        int event = Integer.parseInt(eventData.toString());
        switch (event) {
            case 4:  return ProductStatusEnum.ON_SHELF;
            case 5:  return ProductStatusEnum.AUDIT_REJECT;
            case 10: return ProductStatusEnum.OFF_SHELF;
            case 11: return ProductStatusEnum.ON_SHELF;
            default:
                log.warn("抖音未处理的回调事件类型: event={}", event);
                return null;
        }
    }

    /**
     * 将标准模型转换为抖音 product.addV2 参数格式。
     *
     * 关键映射：
     * - 价格单位：标准模型和抖音都是「分」
     * - 类目：通过 CategoryMappingService 把本地类目翻译为抖音 leaf 类目
     * - 规格：标准 attributes Map 转为抖音 spec_prices 数组
     */
    private JSONObject buildAddProductParam(StandardProductDTO product) {
        String externalCategoryId = categoryMappingService.translateRequired(product.getCategoryId(), ChannelEnum.DOUYIN);

        JSONObject param = new JSONObject();
        param.put("name", product.getTitle());
        param.put("outer_product_id", product.getOutProductId());
        param.put("product_type", 0);
        param.put("category_leaf_id", externalCategoryId);
        param.put("pic", String.join("|", product.getMainImages()));
        param.put("description", product.getDescription() != null ? product.getDescription() : "");
        param.put("reduce_type", 1);
        param.put("freight_id", product.getFreightTemplateId());
        param.put("commit", true);
        param.put("start_sale_type", 0);

        List<JSONObject> skuList = product.getSkus().stream().map(sku -> {
            JSONObject skuJson = new JSONObject();
            skuJson.put("price", sku.getPrice());
            skuJson.put("stock_num", sku.getStock());
            skuJson.put("outer_sku_id", sku.getSkuCode());
            int i = 1;
            if (sku.getAttributes() != null) {
                for (Map.Entry<String, String> attr : sku.getAttributes().entrySet()) {
                    skuJson.put("spec_detail_name" + i, attr.getValue());
                    i++;
                }
            }
            return skuJson;
        }).collect(Collectors.toList());
        param.put("spec_prices", JSON.toJSONString(skuList));

        if (!product.getSkus().isEmpty() && product.getSkus().get(0).getAttributes() != null) {
            String specName = String.join("-", product.getSkus().get(0).getAttributes().keySet());
            param.put("spec_name", specName);
        }

        return param;
    }
}
