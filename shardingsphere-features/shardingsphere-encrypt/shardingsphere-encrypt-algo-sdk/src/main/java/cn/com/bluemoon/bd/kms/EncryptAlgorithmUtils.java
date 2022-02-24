package cn.com.bluemoon.bd.kms;

import cn.com.bluemoon.bd.kms.util.HttpUtils;
import cn.com.bluemoon.bd.kms.util.ResponseResult;
import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.encrypt.spi.EncryptAlgorithm;
import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithmFactory;
import org.apache.shardingsphere.infra.exception.ShardingSphereException;
import org.apache.shardingsphere.spi.ShardingSphereServiceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <pre>
 *     public void testSysAES() {
 *         Object plain = "test";
 *         String cipher = EncryptAlgorithmUtils.encrypt("ec", plain);
 *         Assert.assertNotEquals(plain, cipher);
 *         Object plain2 = EncryptAlgorithmUtils.decrypt("ec", cipher);
 *         Assert.assertNotEquals(cipher, plain2);
 *         Assert.assertEquals(plain, plain2);
 *     }
 * </pre>
 */
@Slf4j
public final class EncryptAlgorithmUtils {

    /**
     * kms center pull secure key url key.
     */
    private static final String KMS_CENTER_URL_PROP_KEY = System.getProperty("kms.center.secure.url", "kms.center.secure.url");

    private static final String KMS_CENTER_MODE_KEY = System.getProperty("kms.center.secure.mode", "kms.center.secure.mode");

    private static final String KMS_CENTER_SECURE_LOCAL_KEY = System.getProperty("kms.center.secure.localKey", "kms.center.secure.localKey");


    /**
     * 缓存系统与加密算法（一个系统只对应一个加密算法）.
     */
    private static final Map<String, EncryptAlgorithm<Object, String>> SYS_SECURE_CACHE = new ConcurrentHashMap<>(16);

    static {
        ShardingSphereServiceLoader.register(EncryptAlgorithm.class);
        initEncryptAlgorithmProps();
    }

    /**
     * 系统加密
     *
     * @param sys        系统名称如：公司电商、现代渠道、财务、供应链分别对应ec、ka、fi、sc
     * @param plainValue 明文
     * @return 密文
     */
    public static String encrypt(String sys, Object plainValue) {
        if (SYS_SECURE_CACHE.containsKey(sys)) {
            return SYS_SECURE_CACHE.get(sys).encrypt(plainValue);
        }
        return Objects.toString(plainValue);
    }

    /**
     * 系统解密
     *
     * @param sys         系统名称如：公司电商、现代渠道、财务、供应链分别对应ec、ka、fi、sc
     * @param cipherValue 密文
     * @return 明文
     */
    public static Object decrypt(String sys, String cipherValue) {
        if (SYS_SECURE_CACHE.containsKey(sys)) {
            return SYS_SECURE_CACHE.get(sys).decrypt(cipherValue);
        }
        return cipherValue;
    }

    // private
    private static void initEncryptAlgorithmProps() {
        Properties props;
        try (InputStream inputStream = EncryptAlgorithmUtils.class.getClassLoader().getResourceAsStream("config.properties")) {
            props = new Properties();
            props.load(inputStream);
        } catch (IOException e) {
            throw new ShardingSphereException("请配置KMS平台获取秘钥信息, config.properties", e);
        }
        if ("remote".equalsIgnoreCase(props.getProperty(KMS_CENTER_MODE_KEY))) {
            String kmsUrlStr = props.getProperty(KMS_CENTER_URL_PROP_KEY);
            String[] kmsUrls = kmsUrlStr.split(";");
            for (String kmsUrl : kmsUrls) {
                ResponseResult resp = getResponseResult(kmsUrl);
                log.info("init kmsUrl={},response:{}", kmsUrl, resp);
                String base64Str = getBase64Response(kmsUrl, resp);
                Algorithm algorithm = getAlgorithmByBase64Str(base64Str);
                SYS_SECURE_CACHE.put(algorithm.getSys(), createAlgorithm(algorithm));
            }
        } else {
            String base64Str = props.getProperty(KMS_CENTER_SECURE_LOCAL_KEY);
            Algorithm algorithm = getAlgorithmByBase64Str(base64Str);
            log.debug("init algorithm : {}", algorithm);
            SYS_SECURE_CACHE.put(algorithm.getSys(), createAlgorithm(algorithm));
        }
        log.info("init finished, sys:{}", SYS_SECURE_CACHE.keySet());
    }

    private static Algorithm getAlgorithmByBase64Str(String base64Str) {
        return JSON.parseObject(Base64.getDecoder().decode(base64Str), Algorithm.class);
    }

    private static ResponseResult getResponseResult(String kmsUrl) {
        ResponseResult resp;
        try {
            resp = JSON.parseObject(HttpUtils.doGet(kmsUrl), ResponseResult.class);
        } catch (IOException e) {
            throw new ShardingSphereException("kmsUrl=" + kmsUrl + "，连接KMS平台失败，请确保kmsUrl访问正常", e);
        }
        return resp;
    }

    private static String getBase64Response(String kmsUrl, ResponseResult resp) {
        String base64Str;
        if (resp.isSuccess()) {
            base64Str = resp.getModel();
        } else {
            throw new ShardingSphereException("kmsUrl=" + kmsUrl + "，获取KMS平台秘钥失败，原因是：" + resp.getErrorMsg());
        }
        return base64Str;
    }

    private static EncryptAlgorithm<Object, String> createAlgorithm(final Algorithm algorithm) {
        Properties props = new Properties();
        props.putAll(JSON.parseObject(algorithm.getKey(), Map.class));
        return ShardingSphereAlgorithmFactory.createAlgorithm(new ShardingSphereAlgorithmConfiguration(algorithm.getType().toUpperCase(), props), EncryptAlgorithm.class);
    }

    @Data
    private static class Algorithm {

        private String sys;

        private String type;

        private String key;

    }

}