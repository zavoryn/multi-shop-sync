package com.github.multiplatform.sync.channel.wechat;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class WechatCryptoUtilTest {

    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ"; // 43 char
    private static final String APP_ID = "wx1234567890abcdef";
    private static final String TOKEN = "myToken";

    @Test
    void verifySignatureCorrectPasses() {
        String ts = "1700000000";
        String nonce = "noncexxx";
        String encrypt = "encrypted_blob_base64";
        String[] arr = {TOKEN, ts, nonce, encrypt};
        Arrays.sort(arr);
        String expected = DigestUtils.sha1Hex(String.join("", arr));

        assertTrue(WechatCryptoUtil.verifySignature(TOKEN, ts, nonce, encrypt, expected));
    }

    @Test
    void verifySignatureWrongRejects() {
        assertFalse(WechatCryptoUtil.verifySignature(TOKEN, "1", "n", "e", "wrong-sig"));
    }

    @Test
    void verifySignatureNullArgsReturnFalse() {
        assertFalse(WechatCryptoUtil.verifySignature(null, "1", "n", "e", "s"));
        assertFalse(WechatCryptoUtil.verifySignature(TOKEN, null, "n", "e", "s"));
        assertFalse(WechatCryptoUtil.verifySignature(TOKEN, "1", null, "e", "s"));
        assertFalse(WechatCryptoUtil.verifySignature(TOKEN, "1", "n", null, "s"));
        assertFalse(WechatCryptoUtil.verifySignature(TOKEN, "1", "n", "e", null));
    }

    @Test
    void encryptDecryptRoundTrip() {
        String plaintext = "{\"event\":\"channels_ec_product_audit_approve\",\"out_product_id\":\"P-CRYPTO-1\"}";
        String random16 = "0123456789abcdef";
        String cipher = WechatCryptoUtil.encrypt(AES_KEY, plaintext, APP_ID, random16);

        String decoded = WechatCryptoUtil.decrypt(AES_KEY, cipher, APP_ID);
        assertEquals(plaintext, decoded);
    }

    @Test
    void decryptAppIdMismatchThrows() {
        String cipher = WechatCryptoUtil.encrypt(AES_KEY, "{\"k\":\"v\"}", APP_ID, "0123456789abcdef");
        WechatCryptoUtil.CryptoException ex = assertThrows(WechatCryptoUtil.CryptoException.class,
                () -> WechatCryptoUtil.decrypt(AES_KEY, cipher, "wxDIFFERENT"));
        assertTrue(ex.getMessage().contains("appId"));
    }

    @Test
    void decryptExpectedAppIdNullSkipsCheck() {
        String cipher = WechatCryptoUtil.encrypt(AES_KEY, "{\"k\":\"v\"}", APP_ID, "0123456789abcdef");
        assertDoesNotThrow(() -> WechatCryptoUtil.decrypt(AES_KEY, cipher, null));
    }

    @Test
    void decryptWrongKeyThrows() {
        String cipher = WechatCryptoUtil.encrypt(AES_KEY, "{\"k\":\"v\"}", APP_ID, "0123456789abcdef");
        String wrongKey = "wrongwrongwrongwrongwrongwrongwrongwrongwro";  // 43 char
        assertThrows(WechatCryptoUtil.CryptoException.class,
                () -> WechatCryptoUtil.decrypt(wrongKey, cipher, APP_ID));
    }

    @Test
    void pkcs7UnpadNormal() {
        byte[] in = {1, 2, 3, 4, 4, 4, 4, 4};
        byte[] out = WechatCryptoUtil.pkcs7Unpad(in);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, out);
    }

    @Test
    void pkcs7UnpadIllegalPadValueReturnsAsIs() {
        byte[] in = {1, 2, 99};
        byte[] out = WechatCryptoUtil.pkcs7Unpad(in);
        assertArrayEquals(in, out);
    }
}
