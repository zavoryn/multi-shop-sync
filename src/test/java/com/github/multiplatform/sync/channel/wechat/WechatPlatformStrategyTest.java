package com.github.multiplatform.sync.channel.wechat;

import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers all 22 wechat status values in docs/research/platform-status-mapping.md.
 */
class WechatPlatformStrategyTest {

    @ParameterizedTest(name = "wechat status={0} -> {1}")
    @CsvSource({
            "0, DRAFT", "1, DRAFT",
            "2, WAIT_PLATFORM_AUDIT",
            "4, WAIT_PLATFORM_AUDIT",
            "7, WAIT_PLATFORM_AUDIT",
            "70, WAIT_PLATFORM_AUDIT",
            "5, ON_SHELF",
            "3, AUDIT_REJECT",
            "8, AUDIT_REJECT",
            "20, AUDIT_REJECT",
            "71, AUDIT_REJECT",
            "72, AUDIT_REJECT",
            "73, AUDIT_REJECT",
            "6, OFF_SHELF", "9, OFF_SHELF", "10, OFF_SHELF",
            "11, OFF_SHELF", "12, OFF_SHELF", "13, OFF_SHELF",
            "14, OFF_SHELF", "15, OFF_SHELF", "21, OFF_SHELF",
    })
    void mapStatusAll22Values(int code, ProductStatusEnum expected) {
        assertEquals(expected, WechatPlatformStrategy.mapStatus(code));
    }

    @Test
    void status30NotExistReturnsNull() {
        assertNull(WechatPlatformStrategy.mapStatus(30));
    }

    @Test
    void unknownStatusReturnsNull() {
        assertNull(WechatPlatformStrategy.mapStatus(999));
        assertNull(WechatPlatformStrategy.mapStatus(-1));
    }
}
