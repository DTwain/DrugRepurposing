package org.example.repository.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class CryptoUtil {

    // AES requires a 16-byte key for AES-128. For AES-256, provide a 32-byte key.
    private static final String SECRET_KEY = "abcdefghijklmnop"; // Example key (16 characters)
    // AES CBC mode needs a 16-byte IV
    private static final String INIT_VECTOR = "RandomInitVector"; // Example IV (16 characters)

    /**
     * Encrypts the given plain text using AES/CBC/PKCS5Padding.
     *
     * @param value the plain text value to encrypt.
     * @return the encrypted value encoded in Base64.
     */
    public static String encrypt(String value) {
        try {
            IvParameterSpec iv = new IvParameterSpec(INIT_VECTOR.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(SECRET_KEY.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            byte[] encrypted = cipher.doFinal(value.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Decrypts the given encrypted text (Base64-encoded) using AES/CBC/PKCS5Padding.
     *
     * @param encrypted the encrypted text (Base64-encoded) to decrypt.
     * @return the decrypted plain text.
     */
    public static String decrypt(String encrypted) {
        try {
            IvParameterSpec iv = new IvParameterSpec(INIT_VECTOR.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(SECRET_KEY.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

            byte[] original = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            return new String(original, "UTF-8");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Demonstrates encryption and decryption.
     */
    public static void main(String[] args) {
        String originalPassword = "anaaremere123";
        System.out.println("Original Password: " + originalPassword);

        String encryptedPassword = encrypt(originalPassword);
        System.out.println("Encrypted Password: " + encryptedPassword);

        String decryptedPassword = decrypt(encryptedPassword);
        System.out.println("Decrypted Password: " + decryptedPassword);
    }
}
