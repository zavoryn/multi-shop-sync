package com.github.multiplatform.sync.channel.wechat;

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

import java.util.Map;

/**
 * 微信小店（视频号小店）策略实现。
 *
 * API 文档：https://developers.weixin.qq.com/doc/store/shop/API/channels-shop-product/shop/api_addproduct.html
 *
 * 微信特点：
 * - 图片必须使用微信上传接口返回的 URL（mmecimage.cn/p/）
 * - 草稿+线上双份数据模型，add/update 只改草稿
 * - SKU > 25 个时异步处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatPlatformStrategy extends AbstractPlatformStrategy {

    private final WechatAuthHelper authHelper;
    private final CategoryMappingService categoryMappingService;
    private final WebClient.Builder webClientBuilder;

    private static final String BASE_URL = "https://api.weixin.qq.com";

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.WECHAT;
    }

    @Override
    protected PushResult doPushProduct(StandardProductDTO product) {
        JSONObject param = buildAddProductParam(product);
        String url = BASE_URL + "/channels/ec/product/add?access_token=" + authHelper.getAccessToken();

        String response = webClientBuilder.build().post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(param.toJSONString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject result = JSON.parseObject(response);
        int errcode = result.getIntValue("errcode");
        if (errcode != 0) {
            throw new ChannelException("wechat", "商品推送失败[" + errcode + "]: " + result.getString("errmsg"));
        }

        JSONObject data = result.getJSONObject("data");
        String productId = data == null ? null : data.getString("product_id");
        log.info("微信商品推送成功: outProductId={}, productId={}", product.getOutProductId(), productId);
        return PushResult.ok(productId);
    }

    @Override
    protected boolean doChangeStatus(String channelProductId, ProductStatusEnum status) {
        String path = (status == ProductStatusEnum.ON_SHELF)
                ? "/channels/ec/product/listing"
                : "/channels/ec/product/delisting";

        JSONObject param = new JSONObject();
        param.put("product_id", Long.parseLong(channelProductId));

        String url = BASE_URL + path + "?access_token=" + authHelper.getAccessToken();

        String response = webClientBuilder.build().post()
                .uri(url)
                .bodyValue(param.toJSONString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject result = JSON.parseObject(response);
        return result.getIntValue("errcode") == 0;
    }

    @Override
    protected ProductStatusEnum doSyncPlatformStatus(String channelProductId) {
        JSONObject param = new JSONObject();
        param.put("product_id", Long.parseLong(channelProductId));

        String url = BASE_URL + "/channels/ec/product/get?access_token=" + authHelper.getAccessToken();

        String response = webClientBuilder.build().post()
                .uri(url)
                .bodyValue(param.toJSONString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("微信主动查询商品状态返回: {}", response);
        return null; // Phase 5: status → 标准枚举（具体取值待官方文档核对）
    }

    /**
     * 解析微信回调数据。
     * 事件类型：商品审核（通过/驳回）、商品上下架、商品更新
     */
    @Override
    protected ProductStatusEnum doParseCallback(Map<String, Object> callbackData) {
        Object eventType = callbackData.get("event");
        if (eventType == null) {
            return null;
        }

        String event = eventType.toString();
        switch (event) {
            case "channels_ec_product_audit_approve": return ProductStatusEnum.ON_SHELF;
            case "channels_ec_product_audit_reject":  return ProductStatusEnum.AUDIT_REJECT;
            case "channels_ec_product_delisting":     return ProductStatusEnum.OFF_SHELF;
            default:
                log.warn("微信未处理的回调事件: event={}", event);
                return null;
        }
    }

    private JSONObject buildAddProductParam(StandardProductDTO product) {
        String externalCategoryId = categoryMappingService.translateRequired(product.getCategoryId(), ChannelEnum.WECHAT);

        JSONObject param = new JSONObject();
        param.put("title", product.getTitle());
        param.put("out_product_id", product.getOutProductId());
        param.put("head_imgs", new JSONArray(product.getMainImages()));
        param.put("deliver_method", 0);

        JSONArray cats = new JSONArray();
        JSONObject cat = new JSONObject();
        cat.put("category_id", externalCategoryId);
        cats.add(cat);
        param.put("cats_v2", cats);

        JSONArray skuArray = new JSONArray();
        for (StandardSkuDTO sku : product.getSkus()) {
            JSONObject skuJson = new JSONObject();
            skuJson.put("out_sku_id", sku.getSkuCode());
            skuJson.put("sale_price", sku.getPrice());
            skuJson.put("stock_num", sku.getStock());
            skuJson.put("sku_code", sku.getSkuCode());

            if (sku.getImageUrl() != null) {
                skuJson.put("thumb_img", sku.getImageUrl());
            }

            if (sku.getAttributes() != null) {
                JSONObject attrs = new JSONObject();
                for (Map.Entry<String, String> attr : sku.getAttributes().entrySet()) {
                    attrs.put(attr.getKey(), attr.getValue());
                }
                skuJson.put("sku_attrs", attrs);
            }
            skuArray.add(skuJson);
        }
        param.put("skus", skuArray);
        param.put("listing", 1);

        return param;
    }
}
