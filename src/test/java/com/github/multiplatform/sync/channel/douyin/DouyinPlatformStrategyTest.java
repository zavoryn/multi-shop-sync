package com.github.multiplatform.sync.channel.douyin;

import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class DouyinPlatformStrategyTest {

    @ParameterizedTest(name = "douyin status={0} -> {1}")
    @CsvSource({
            "0, ON_SHELF",
            "1, OFF_SHELF",
            "2, OFF_SHELF",
    })
    void mapStatusKnownValues(int code, ProductStatusEnum expected) {
        assertEquals(expected, DouyinPlatformStrategy.mapStatus(code));
    }

    @Test
    void mapStatusUnknownReturnsNull() {
        assertNull(DouyinPlatformStrategy.mapStatus(99));
        assertNull(DouyinPlatformStrategy.mapStatus(-1));
    }
}
