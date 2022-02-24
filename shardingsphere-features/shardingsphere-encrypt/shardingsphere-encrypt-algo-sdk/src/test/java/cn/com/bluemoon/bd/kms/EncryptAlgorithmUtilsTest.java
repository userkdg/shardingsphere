package cn.com.bluemoon.bd.kms;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Jarod.Kong
 */
public class EncryptAlgorithmUtilsTest {
    @Test
    public void testSysAES() {
        Object plain = "test";
        String cipher = EncryptAlgorithmUtils.encrypt("ec", plain);
        Assert.assertNotEquals(plain, cipher);
        Object plain2 = EncryptAlgorithmUtils.decrypt("ec", cipher);
        Assert.assertNotEquals(cipher, plain2);
        Assert.assertEquals(plain, plain2);
    }
//
//    @Test
//    public void testSysRC4() {
//        Object plain = "test";
//        String cipher = EncryptAlgorithmUtils.encrypt("test-rc4", plain);
//        Assert.assertNotEquals(plain, cipher);
//        Object plain2 = EncryptAlgorithmUtils.decrypt("test-rc4", cipher);
//        Assert.assertNotEquals(cipher, plain2);
//        Assert.assertEquals(plain, plain2);
//    }
//
//    @Test
//    public void testSysSM4() {
//        Object plain = "test";
//        String cipher = EncryptAlgorithmUtils.encrypt("test-sm4", plain);
//        Assert.assertNotEquals(plain, cipher);
//        Object plain2 = EncryptAlgorithmUtils.decrypt("test-sm4", cipher);
//        Assert.assertEquals(plain, plain2);
//    }
//
//    @Test
//    public void testSysSM3() {
//        Object plain = "test";
//        String cipher = EncryptAlgorithmUtils.encrypt("test-sm3", plain);
//        Assert.assertNotEquals(plain, cipher);
//        Object plain2 = EncryptAlgorithmUtils.decrypt("test-sm3", cipher);
//        Assert.assertNotNull(String.valueOf(plain), plain2);
//        Assert.assertEquals(cipher, plain2);
//    }
//    @Test
//    public void testSysMD5() {
//        Object plain = "test";
//        String cipher = EncryptAlgorithmUtils.encrypt("test-md5", plain);
//        Assert.assertNotEquals(plain, cipher);
//        Object plain2 = EncryptAlgorithmUtils.decrypt("test-md5", cipher);
//        Assert.assertNotNull(String.valueOf(plain), plain2);
//        Assert.assertEquals(cipher, plain2);
//    }

    @Test
    public void testUnknown() {
        Object plain = "test";
        String cipher = EncryptAlgorithmUtils.encrypt("unknown", plain);
        Object plain2 = EncryptAlgorithmUtils.decrypt("unknown", cipher);
        Assert.assertEquals(plain, plain2);
    }
}
