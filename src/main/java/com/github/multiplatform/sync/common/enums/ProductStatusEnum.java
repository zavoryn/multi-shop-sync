package com.github.multiplatform.sync.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductStatusEnum {

    DRAFT(0, "草稿"),
    WAIT_PLATFORM_AUDIT(10, "平台审核中"),
    ON_SHELF(20, "已上架"),
    AUDIT_REJECT(30, "审核驳回"),
    OFF_SHELF(40, "已下架");

    private final int code;
    private final String desc;
}
