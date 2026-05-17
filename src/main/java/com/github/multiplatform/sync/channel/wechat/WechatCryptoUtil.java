package com.github.multiplatform.sync.channel.wechat;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * 微信小店 / 公众号回调消息加解密工具。
 *
 * 移植自微信官方 WXBizMsgCrypt（Java 版），适配本项目使用习惯：
 * - 静态方法，无 Spring 依赖
 * - 抛 {@link CryptoException}（受检），上层显式处理 401 vs 500
 *
 * 核心协议：
 * <ol>
 *   <li>验签：sha1(sort([token, timestamp, nonce, msg_encrypt]).join(""))</li>
 *   <li>解密：AES-256-CBC，key/iv 由 encodingAesKey base64 解码后构成（aesKey[0..16] 作为 iv）</li>
 *   <li>解密后明文布局：[16 字节随机] + [4 字节 big-endian 长度] + [真实消息] + [appId]</li>
 * </ol>
 *
 * 官方文档：
 * - https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Message_encryption_and_decryption.html
 * - 服务商授权事件接收：https://developers.weixin.qq.com/doc/oplatform/Third-party_Platforms/2.0/api/Before_Develop/Authorization_Process_Technical_Description.html
 */
@Slf4j
public final class WechatCryptoUtil {

    private WechatCryptoUtil() {}

    /**
     * 验证回调签名。
     *
     * @param token        平台后台配置的 token
     * @param timestamp    query 中的 timestamp
     * @param nonce        query 中的 nonce
     * @param msgEncrypt   body 中的 Encrypt 字段（base64 密文）
     * @param signature    query 中的 msg_signature
     * @return true=验签通过
     */
    public static boolean verifySignature(String token, String timestamp, String nonce,
                                          String msgEncrypt, String signature) {
        if (token == null || timestamp == null || nonce == null || msgEncrypt == null || signature == null) {
            return false;
        }
        String[] arr = {token, timestamp, nonce, msgEncrypt};
        Arrays.sort(arr);
        String joined = String.join("", arr);
        String expected = DigestUtils.sha1Hex(joined);
        return Objects.equals(expected, signature);
    }

    /**
     * 解密微信回调消息。
     *
     * @param encodingAesKey 平台后台配置的 EncodingAESKey（43 字符）
     * @param msgEncrypt     body 中的 Encrypt 字段（base64 密文）
     * @param expectedAppId  期望的 appId（解密末尾会带 appId，校验是否匹配；传 null 跳过校验）
     * @return 明文消息（通常是 JSON 或 XML 字符串）
     */
    public static String decrypt(String encodingAesKey, String msgEncrypt, String expectedAppId) {
        try {
            // EncodingAESKey 43 字符 + 末尾补 "=" → base64 解出 32 字节 AES-256 密钥
            byte[] aesKey = Base64.decodeBase64(encodingAesKey + "=");
            if (aesKey.length != 32) {
                throw new CryptoException("EncodingAESKey 解码后长度异常: " + aesKey.length + "（期望 32）");
            }
            byte[] iv = Arrays.copyOfRange(aesKey, 0, 16);

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
            byte[] padded = cipher.doFinal(Base64.decodeBase64(msgEncrypt));
            byte[] plain = pkcs7Unpad(padded);

            // 明文布局：[0..16] 随机串 | [16..20] big-endian 长度 | [20..20+len] 真实消息 | [尾部] appId
            if (plain.length < 20) {
                throw new CryptoException("解密后长度不足 20 字节: " + plain.length);
            }
            int msgLen = ((plain[16] & 0xff) << 24)
                       | ((plain[17] & 0xff) << 16)
                       | ((plain[18] & 0xff) << 8)
                       |  (plain[19] & 0xff);
            if (msgLen < 0 || 20 + msgLen > plain.length) {
                throw new CryptoException("解密消息长度字段非法: " + msgLen);
            }

            String msg = new String(plain, 20, msgLen, StandardCharsets.UTF_8);
            String appId = new String(plain, 20 + msgLen, plain.length - 20 - msgLen, StandardCharsets.UTF_8);

            if (expectedAppId != null && !expectedAppId.isEmpty() && !expectedAppId.equals(appId)) {
                throw new CryptoException("appId 不匹配: 解密得 " + appId + "，期望 " + expectedAppId);
            }
            return msg;
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("微信回调解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 反向操作（仅供单元测试使用，验证 decrypt 行为）。
     * 生产代码不应该用本方法 —— 微信回调是平台主动加密发给我们，不应反向构造。
     */
    static String encrypt(String encodingAesKey, String plaintext, String appId, String random16) {
        try {
            byte[] aesKey = Base64.decodeBase64(encodingAesKey + "=");
            byte[] iv = Arrays.copyOfRange(aesKey, 0, 16);
            byte[] msgBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] appIdBytes = appId.getBytes(StandardCharsets.UTF_8);
            byte[] random = random16.getBytes(StandardCharsets.UTF_8);
            byte[] lenBytes = new byte[]{
                    (byte) (msgBytes.length >> 24),
                    (byte) (msgBytes.length >> 16),
                    (byte) (msgBytes.length >> 8),
                    (byte)  msgBytes.length};

            int totalLen = random.length + lenBytes.length + msgBytes.length + appIdBytes.length;
            byte[] assembled = new byte[totalLen];
            int p = 0;
            System.arraycopy(random, 0, assembled, p, random.length);  p += random.length;
            System.arraycopy(lenBytes, 0, assembled, p, lenBytes.length); p += lenBytes.length;
            System.arraycopy(msgBytes, 0, assembled, p, msgBytes.length); p += msgBytes.length;
            System.arraycopy(appIdBytes, 0, assembled, p, appIdBytes.length);

            // PKCS7 pad
            int blockSize = 32;
            int pad = blockSize - (totalLen % blockSize);
            byte[] padded = new byte[totalLen + pad];
            System.arraycopy(assembled, 0, padded, 0, totalLen);
            for (int i = totalLen; i < padded.length; i++) padded[i] = (byte) pad;

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
            byte[] cipherBytes = cipher.doFinal(padded);
            return Base64.encodeBase64String(cipherBytes);
        } catch (Exception e) {
            throw new CryptoException("encrypt failed: " + e.getMessage(), e);
        }
    }

    /** PKCS7 反填充：明文末尾 N 字节的值都为 N，去掉即可 */
    static byte[] pkcs7Unpad(byte[] padded) {
        if (padded == null || padded.length == 0) return padded;
        int pad = padded[padded.length - 1] & 0xff;
        if (pad < 1 || pad > 32) {
            // 非法填充值，可能未填充
            return padded;
        }
        return Arrays.copyOfRange(padded, 0, padded.length - pad);
    }

    /** 验签或解密失败的统一异常 */
    public static class CryptoException extends RuntimeException {
        public CryptoException(String msg) { super(msg); }
        public CryptoException(String msg, Throwable t) { super(msg, t); }
    }
}
