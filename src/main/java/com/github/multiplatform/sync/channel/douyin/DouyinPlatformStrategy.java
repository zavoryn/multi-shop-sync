package com.github.multiplatform.sync.channel.douyin;

import com.alibaba.fastjson2.JSON;
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

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 抖音小店策略实现。
 *
 * API 文档：https://op.jinritemai.com/docs/api-docs/14/5980
 *
 * 核心流程：
 * 1. 将 StandardProductDTO 转换为抖店 product.addV2 格式
 * 2. 签名并调用抖店 API
 * 3. 解析回调中的 event 字段（4=审核通过, 5=审核驳回）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DouyinPlatformStrategy extends AbstractPlatformStrategy {

    private final DouyinAuthHelper authHelper;
    private final WebClient.Builder webClientBuilder;

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.DOUYIN;
    }

    @Override
    protected boolean doPushProduct(StandardProductDTO product) {
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

        log.info("抖音商品推送成功: outProductId={}", product.getOutProductId());
        return true;
    }

    @Override
    protected boolean doChangeStatus(String outProductId, ProductStatusEnum status) {
        String method = (status == ProductStatusEnum.ON_SHELF) ? "product.launchProduct" : "product.setOffline";
        JSONObject paramJson = new JSONObject();
        // 需要先将 outProductId 转换为抖音的 product_id
        // 实际项目中从渠道商品映射表查询
        paramJson.put("product_id", Long.parseLong(outProductId));

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
    protected void doSyncPlatformStatus(String outProductId) {
        JSONObject paramJson = new JSONObject();
        paramJson.put("product_id", Long.parseLong(outProductId));

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

        log.info("抖音主动查询商品状态返回: {}", response);
        // TODO: 解析状态并更新本地数据库
    }

    /**
     * 解析抖音回调数据。
     * 回调文档：https://op.jinritemai.com/docs/api-docs/14/56
     * Tag=400, event: 4=审核通过, 5=审核驳回, 10=下架, 11=上架
     */
    @Override
    protected ProductStatusEnum doParseCallback(Map<String, Object> callbackData) {
        Object eventData = callbackData.get("event");
        if (eventData == null) {
            return null;
        }

        int event = Integer.parseInt(eventData.toString());
        switch (event) {
            case 4:
                return ProductStatusEnum.ON_SHELF;
            case 5:
                return ProductStatusEnum.AUDIT_REJECT;
            case 10:
                return ProductStatusEnum.OFF_SHELF;
            case 11:
                return ProductStatusEnum.ON_SHELF;
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
     * - 类目：需要通过 CategoryMapping 表将本地类目转为抖音类目
     * - 规格：标准 attributes Map 转为抖音 spec_prices 数组
     */
    private JSONObject buildAddProductParam(StandardProductDTO product) {
        JSONObject param = new JSONObject();
        param.put("name", product.getTitle());
        param.put("outer_product_id", product.getOutProductId());
        param.put("product_type", 0); // 0=普通商品
        param.put("category_leaf_id", product.getCategoryId());
        param.put("pic", String.join("|", product.getMainImages()));
        param.put("description", product.getDescription() != null ? product.getDescription() : "");
        param.put("reduce_type", 1); // 1=下单减库存
        param.put("freight_id", product.getFreightTemplateId());
        param.put("commit", true); // true=直接提交审核
        param.put("start_sale_type", 0); // 0=审核后立即上架

        // 构建 SKU
        List<JSONObject> skuList = product.getSkus().stream().map(sku -> {
            JSONObject skuJson = new JSONObject();
            skuJson.put("price", sku.getPrice());
            skuJson.put("stock_num", sku.getStock());
            skuJson.put("outer_sku_id", sku.getSkuCode());

            // 将标准 attributes 转为抖音规格字段
            int i = 1;
            for (Map.Entry<String, String> attr : sku.getAttributes().entrySet()) {
                skuJson.put("spec_detail_name" + i, attr.getValue());
                i++;
            }
            return skuJson;
        }).collect(Collectors.toList());

        param.put("spec_prices", JSON.toJSONString(skuList));

        // 构建规格名称
        if (!product.getSkus().isEmpty() && product.getSkus().get(0).getAttributes() != null) {
            String specName = String.join("-", product.getSkus().get(0).getAttributes().keySet());
            param.put("spec_name", specName);
        }

        return param;
    }
}
