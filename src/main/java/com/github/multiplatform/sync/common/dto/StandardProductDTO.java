package com.github.multiplatform.sync.common.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 系统内部标准商品模型。
 * 各渠道 Strategy 负责将此模型转换为目标平台所需的参数格式。
 */
@Data
public class StandardProductDTO {

    /** 商户自定义商品ID */
    private String outProductId;

    /** 商品标题 */
    private String title;

    /** 内部类目ID（通过 CategoryMapping 转换为目标平台类目） */
    private Long categoryId;

    /** 品牌ID */
    private String brandId;

    /** 主图URL列表 */
    private List<String> mainImages;

    /** 商品描述（富文本/图片URL） */
    private String description;

    /** 运费模板ID */
    private Long freightTemplateId;

    /** SKU列表 */
    private List<StandardSkuDTO> skus;

    /** 扩展属性（各平台特有字段通过此Map透传） */
    private Map<String, Object> extraAttrs;
}
