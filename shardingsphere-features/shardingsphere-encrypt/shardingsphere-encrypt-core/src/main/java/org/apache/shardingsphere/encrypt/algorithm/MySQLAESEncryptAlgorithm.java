/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.encrypt.algorithm;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.encrypt.spi.EncryptAlgorithm;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;

/**
 * bluemoon AES encrypt algorithm.
 * 该算法也是基于aes，区别在于encrypt、decrypt和mysql数据库的aes_encrypt、aes_decrypt算法一致（加解密结果一样）
 * 用于加密模块中，框架不支持的可以用mysql的算法进行加解密.
 */
@Getter
@Setter
@Slf4j
public final class MySQLAESEncryptAlgorithm implements EncryptAlgorithm<Object, String> {

    private static final String AES_KEY = "aes-key-value";

    private Properties props = new Properties();

    private byte[] secretKey;

    @Override
    public void init() {
        secretKey = createSecretKey();
    }

    private byte[] createSecretKey() {
        Preconditions.checkArgument(props.containsKey(AES_KEY), "%s can not be null.", AES_KEY);
        return Arrays.copyOf(props.getProperty(AES_KEY).getBytes(StandardCharsets.UTF_8), 16);
    }

    @Override
    public String encrypt(final Object plainValue) {
        if (null == plainValue) {
            return null;
        }
        try {
            Cipher cipher = getCipher(Cipher.ENCRYPT_MODE);
            byte[] ciphertextBytes = cipher.doFinal(String.valueOf(plainValue).getBytes(StandardCharsets.UTF_8));
            return DatatypeConverter.printBase64Binary(ciphertextBytes);
        } catch (Exception e) {
            log.error("MYSQL-AES加密异常，plainValue={}", plainValue, e);
        }
        return Objects.toString(plainValue, null);
    }

    @Override
    public Object decrypt(final String cipherValue) {
        if (null == cipherValue) {
            return null;
        }
        try {
            Cipher cipher = getCipher(Cipher.DECRYPT_MODE);
            byte[] plainText = cipher.doFinal(DatatypeConverter.parseBase64Binary(cipherValue));
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密异常，cipherValue={}", cipherValue, e);
        }
        return cipherValue;
    }

    private Cipher getCipher(int mode) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(mode, new SecretKeySpec(secretKey, "AES"));
        return cipher;
    }

    @Override
    public String getType() {
        return "MYSQL-AES";
    }
}
