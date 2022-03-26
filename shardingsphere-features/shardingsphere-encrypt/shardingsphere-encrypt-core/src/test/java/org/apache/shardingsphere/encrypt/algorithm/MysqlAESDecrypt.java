package org.apache.shardingsphere.encrypt.algorithm;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author Jarod.Kong
 */
@Slf4j
public class MysqlAESDecrypt {


    private final byte[] secretKey;

    {
        secretKey = createSecretKey();
    }

    public static String aes_decrypt(String passwordhex, String strKey) throws Exception {
        try {
            byte[] keyBytes = Arrays.copyOf(strKey.getBytes("ASCII"), 16);

            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Cipher decipher = Cipher.getInstance("AES");

            decipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decodeHex = DatatypeConverter.parseHexBinary(passwordhex);
//            char[] cleartext = passwordhex.toCharArray();
//            byte[] decodeHex = Hex.decodeHex(cleartext);

            byte[] ciphertextBytes = decipher.doFinal(decodeHex);

            return new String(ciphertextBytes);

        } catch (Exception e) {
            e.getMessage();
        }
        return null;
    }

    public static String aes_encrypt2(String password, String strKey) {
        try {
//            byte[] keyBytes = Arrays.copyOf(strKey.getBytes("ASCII"), 16);
            byte[] keyBytes = Arrays.copyOf(strKey.getBytes("UTF-8"), 16);

            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] cleartext = password.getBytes("UTF-8");
            byte[] ciphertextBytes = cipher.doFinal(cleartext);

//            return new String(Hex.encodeHex(ciphertextBytes));
            return DatatypeConverter.printBase64Binary(ciphertextBytes);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        System.out.println(aes_encrypt2("text", "xxx"));


        String xxx = aes_decrypt("z3m/3afcjrUzZCci5V3KSw==", "xxx");
        System.out.println(xxx);

        MysqlAESDecrypt aes = new MysqlAESDecrypt();
        String encrypt = aes.encrypt("18813975053");
        System.out.println(encrypt);
        Object decrypt = aes.decrypt(encrypt);
        System.out.println(decrypt);

    }

    @SneakyThrows
    private byte[] createSecretKey() {
//        String hex = DigestUtils.md2Hex("xxx");
//        return Arrays.copyOf(hex.getBytes(StandardCharsets.UTF_8), 16);
        return Arrays.copyOf(Hex.decodeHex("xxx".toCharArray()), 16);
    }

    public String encrypt(final Object plainValue) {
        if (null == plainValue) {
            return null;
        }
        try {
            byte[] result = getCipher(Cipher.ENCRYPT_MODE).doFinal(String.valueOf(plainValue).getBytes(StandardCharsets.UTF_8));
            return DatatypeConverter.printBase64Binary(result);
        } catch (Exception e) {
            log.error("加密异常，plainValue={}", plainValue, e);
        }
        return Objects.toString(plainValue, null);
    }

    public Object decrypt(final String cipherValue) {
        if (null == cipherValue) {
            return null;
        }
        try {
            byte[] result = getCipher(Cipher.DECRYPT_MODE).doFinal(DatatypeConverter.parseBase64Binary(cipherValue));
            return new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密异常，cipherValue={}", cipherValue, e);
        }
        return cipherValue;
    }

    private Cipher getCipher(final int decryptMode) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        Cipher result = Cipher.getInstance(getType());
        result.init(decryptMode, new SecretKeySpec(secretKey, getType()));
        return result;
    }

    private String getType() {
        return "AES";
    }

}