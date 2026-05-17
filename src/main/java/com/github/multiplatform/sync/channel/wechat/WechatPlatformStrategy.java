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

        JSONObject result = JSON.parseObject(response);
        if (result == null || result.getIntValue("errcode") != 0) {
            log.warn("微信查询商品状态失败: response={}", response);
            return null;
        }
        // 返回结构：{ errcode, errmsg, product: { status, ... } }
        JSONObject product = result.getJSONObject("product");
        if (product == null || !product.containsKey("status")) {
            log.warn("微信查询返回缺少 product.status: {}", response);
            return null;
        }
        return mapStatus(product.getIntValue("status"));
    }

    /**
     * 微信小店 res.product.status → 标准状态。
     * 完整映射见 docs/research/platform-status-mapping.md（用户提供的官方枚举表）。
     */
    static ProductStatusEnum mapStatus(int status) {
        switch (status) {
            case 0:  // 初始值
            case 1:  // 编辑中
                return ProductStatusEnum.DRAFT;
            case 2:  // 审核中
            case 4:  // 审核成功（未上架）
            case 7:  // 异步提交上传中
            case 70: // 异步提审中
                return ProductStatusEnum.WAIT_PLATFORM_AUDIT;
            case 5:  // 上架
                return ProductStatusEnum.ON_SHELF;
            case 3:  // 审核失败
            case 8:  // 异步提交上传失败
            case 20: // 商品被封禁
            case 71: // 质检不通过
            case 72: // 异步提审失败：当日 quota 不足
            case 73: // 异步提审失败：限频触发
                return ProductStatusEnum.AUDIT_REJECT;
            case 6:  // 回收站
            case 9:  // 彻底删除
            case 10: // 冻结
            case 11: // 自主下架
            case 12: // 售罄下架
            case 13: // 违规下架
            case 14: // 保证金不足下架
            case 15: // 品牌过期下架
            case 21: // SKU 逻辑删除
                return ProductStatusEnum.OFF_SHELF;
            case 30: // 商品不存在
                log.warn("微信查询返回 status=30（商品不存在），上层应清理 mapping");
                return null;
            default:
                log.warn("微信未知 status: {}", status);
                return null;
        }
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
