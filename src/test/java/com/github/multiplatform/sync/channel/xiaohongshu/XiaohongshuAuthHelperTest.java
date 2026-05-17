package com.github.multiplatform.sync.channel.xiaohongshu;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class XiaohongshuAuthHelperTest {

    @Test
    void calcSignMd5Hex32Chars() {
        XiaohongshuAuthHelper helper = new XiaohongshuAuthHelper(null);
        ReflectionTestUtils.setField(helper, "appId", "test-app-id");
        ReflectionTestUtils.setField(helper, "appSecret", "test-secret");

        String sign = helper.calcSign("product.createItemAndSku", "1700000000");
        assertNotNull(sign);
        assertEquals(32, sign.length(), "MD5 hex should be 32 chars");
        assertTrue(sign.matches("[0-9a-f]{32}"));
    }

    @Test
    void calcSignBaselineRegression() {
        // Pin algorithm: MD5(method + "?" + sorted(appId,timestamp,version=2.0).join("&") + appSecret)
        // Input: appId=ak, appSecret=sk, method=m, ts=1
        // signSource = "m?appId=ak&timestamp=1&version=2.0sk"
        XiaohongshuAuthHelper helper = new XiaohongshuAuthHelper(null);
        ReflectionTestUtils.setField(helper, "appId", "ak");
        ReflectionTestUtils.setField(helper, "appSecret", "sk");

        String sign = helper.calcSign("m", "1");
        // baseline pinned below; computed from MD5("m?appId=ak&timestamp=1&version=2.0sk")
        // pinned from actual run: MD5("m?appId=ak&timestamp=1&version=2.0sk")
        String expected = "8e07b82dc6a9153a58162421aecc5ede";
        assertEquals(expected, sign, "if this fails the sign formula has been changed");
    }

    @Test
    void calcSignDifferentInputDifferentOutput() {
        XiaohongshuAuthHelper helper = new XiaohongshuAuthHelper(null);
        ReflectionTestUtils.setField(helper, "appId", "ak");
        ReflectionTestUtils.setField(helper, "appSecret", "sk");

        String s1 = helper.calcSign("m1", "1");
        String s2 = helper.calcSign("m2", "1");
        assertNotEquals(s1, s2);
    }
}
