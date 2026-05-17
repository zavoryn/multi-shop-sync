package com.github.multiplatform.sync.channel.xiaohongshu;

import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XiaohongshuPlatformStrategyTest {

    @ParameterizedTest(name = "buyable={0}, freeze={1} -> {2}")
    @CsvSource({
            "true,  false, ON_SHELF",
            "true,  true,  OFF_SHELF",
            "false, false, OFF_SHELF",
            "false, true,  OFF_SHELF",
    })
    void mapStatusTruthTable(boolean buyable, boolean freeze, ProductStatusEnum expected) {
        assertEquals(expected, XiaohongshuPlatformStrategy.mapStatus(buyable, freeze));
    }
}
