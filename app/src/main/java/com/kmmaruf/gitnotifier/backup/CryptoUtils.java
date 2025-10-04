package com.kmmaruf.gitnotifier.backup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

    private static final String AES = "AES";

    /**
     * Generates a 256-bit AES key from any password using SHA-256 digest.
     */
    public static SecretKeySpec generateKey(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] key = digest.digest(bytes);
        return new SecretKeySpec(key, AES);
    }

    /**
     * Encrypts plain text using the given password.
     */
    public static byte[] encrypt(String plainText, String password) throws Exception {
        SecretKeySpec keySpec = generateKey(password);
        Cipher cipher = Cipher.getInstance(AES);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decrypts encrypted data using the given password.
     */
    public static String decrypt(byte[] encryptedData, String password) throws Exception {
        SecretKeySpec keySpec = generateKey(password);
        Cipher cipher = Cipher.getInstance(AES);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] result = cipher.doFinal(encryptedData);
        return new String(result, StandardCharsets.UTF_8);
    }
}