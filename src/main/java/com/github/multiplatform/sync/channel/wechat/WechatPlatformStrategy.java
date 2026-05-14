package com.github.multiplatform.sync.channel.wechat;

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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 微信小店（视频号小店）策略实现。
 *
 * API 文档：https://developers.weixin.qq.com/doc/store/shop/API/channels-shop-product/shop/api_addproduct.html
 *
 * 核心流程：
 * 1. 将 StandardProductDTO 转换为微信 channels/ec/product/add 格式
 * 2. 通过 access_token 调用微信 API
 * 3. 解析商品审核回调事件
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
    private final WebClient.Builder webClientBuilder;

    private static final String BASE_URL = "https://api.weixin.qq.com";

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.WECHAT;
    }

    @Override
    protected boolean doPushProduct(StandardProductDTO product) {
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

        log.info("微信商品推送成功: outProductId={}, productId={}",
                product.getOutProductId(),
                result.getJSONObject("data") != null ? result.getJSONObject("data").getLong("product_id") : null);
        return true;
    }

    @Override
    protected boolean doChangeStatus(String outProductId, ProductStatusEnum status) {
        String path = (status == ProductStatusEnum.ON_SHELF)
                ? "/channels/ec/product/listing"
                : "/channels/ec/product/delisting";

        JSONObject param = new JSONObject();
        param.put("product_id", Long.parseLong(outProductId));

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
    protected void doSyncPlatformStatus(String outProductId) {
        JSONObject param = new JSONObject();
        param.put("product_id", Long.parseLong(outProductId));

        String url = BASE_URL + "/channels/ec/product/get?access_token=" + authHelper.getAccessToken();

        String response = webClientBuilder.build().post()
                .uri(url)
                .bodyValue(param.toJSONString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("微信主动查询商品状态返回: {}", response);
        // TODO: 解析状态并更新本地数据库
    }

    /**
     * 解析微信回调数据。
     * 回调文档：https://developers.weixin.qq.com/doc/store/shop/API/channels-shop-product/shop/callback_audit.html
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
            case "channels_ec_product_audit_approve":
                return ProductStatusEnum.ON_SHELF;
            case "channels_ec_product_audit_reject":
                return ProductStatusEnum.AUDIT_REJECT;
            case "channels_ec_product_delisting":
                return ProductStatusEnum.OFF_SHELF;
            default:
                log.warn("微信未处理的回调事件: event={}", event);
                return null;
        }
    }

    /**
     * 将标准模型转换为微信 channels/ec/product/add 参数格式。
     *
     * 关键映射：
     * - 价格单位：标准模型和微信都是「分」
     * - 类目：cats_v2 格式 [{category_id: xxx}]
     * - 图片：微信要求已上传的 mmecimage.cn/p/ URL
     * - SKU 属性：sku_attrs 格式 {key: value}
     */
    private JSONObject buildAddProductParam(StandardProductDTO product) {
        JSONObject param = new JSONObject();
        param.put("title", product.getTitle());
        param.put("out_product_id", product.getOutProductId());
        param.put("head_imgs", new JSONArray(product.getMainImages()));
        param.put("deliver_method", 0); // 0=快递

        // 类目
        JSONArray cats = new JSONArray();
        JSONObject cat = new JSONObject();
        cat.put("category_id", product.getCategoryId());
        cats.add(cat);
        param.put("cats_v2", cats);

        // SKU 列表
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

            // 属性转换
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

        // 创建后自动提交上架审核
        param.put("listing", 1);

        return param;
    }
}
