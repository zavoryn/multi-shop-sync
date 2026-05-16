package com.github.multiplatform.sync.common.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地类目 ↔ 平台类目映射。
 * 推送商品时通过本表把本地 localCategoryId 翻译成平台需要的 externalCategoryId。
 */
@Data
@TableName("category_mapping")
public class CategoryMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long localCategoryId;
    private String channel;
    private String externalCategoryId;
    private String categoryName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
