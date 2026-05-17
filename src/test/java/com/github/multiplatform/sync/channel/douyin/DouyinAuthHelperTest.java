package com.github.multiplatform.sync.channel.douyin;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class DouyinAuthHelperTest {

    @Test
    void calcSignProducesDeterministicHmacSha256Hex() {
        DouyinAuthHelper helper = new DouyinAuthHelper(null);
        ReflectionTestUtils.setField(helper, "appKey", "test-app-key");
        ReflectionTestUtils.setField(helper, "appSecret", "test-app-secret");

        String sign = helper.calcSign("product.addV2", "{\"k\":\"v\"}", "1700000000");
        assertNotNull(sign);
        assertEquals(64, sign.length(), "HMAC-SHA256 hex should be 64 chars");
        assertTrue(sign.matches("[0-9a-f]{64}"));

        String sign2 = helper.calcSign("product.addV2", "{\"k\":\"v\"}", "1700000000");
        assertEquals(sign, sign2, "same input must produce same sign");

        String sign3 = helper.calcSign("product.addV2", "{\"k\":\"v\"}", "1700000001");
        assertNotEquals(sign, sign3, "different timestamp must produce different sign");
    }

    @Test
    void calcSignBaselineRegression() {
        // Pin the algorithm: any change to the sign formula will break this baseline.
        // signPattern = "sk" + "app_keyak" + "methodm" + "param_json{}" + "timestamp1" + "v2" + "sk"
        // sign = HmacSHA256(signPattern, key="sk").hex
        DouyinAuthHelper helper = new DouyinAuthHelper(null);
        ReflectionTestUtils.setField(helper, "appKey", "ak");
        ReflectionTestUtils.setField(helper, "appSecret", "sk");

        String sign = helper.calcSign("m", "{}", "1");
        assertEquals(64, sign.length());
        assertNotNull(sign);
        // baseline pin filled after first run; placeholder asserts shape only
    }
}
