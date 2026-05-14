package com.github.multiplatform.sync.common.dto;

import lombok.Data;
import java.util.Map;

/**
 * 标准SKU模型。
 * 价格单位统一为**分**（与所有平台一致）。
 */
@Data
public class StandardSkuDTO {

    /** 商户自定义SKU编码 */
    private String skuCode;

    /** 售价（单位：分） */
    private Long price;

    /** 原价（单位：分） */
    private Long originalPrice;

    /** 库存数量 */
    private Integer stock;

    /** SKU图片URL */
    private String imageUrl;

    /** SKU属性，如 {"颜色":"红色","尺码":"XL"} */
    private Map<String, String> attributes;

    /** 商品条码 */
    private String barcode;
}
