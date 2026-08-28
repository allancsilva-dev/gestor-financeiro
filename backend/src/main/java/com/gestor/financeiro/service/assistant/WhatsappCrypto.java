package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.exception.AssistantException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class WhatsappCrypto {
    private final String encryptionKey;
    private final String hmacSecret;
    private final String keyVersion;
    private final SecureRandom random = new SecureRandom();

    public WhatsappCrypto(@Value("${assistant.whatsapp.encryption-key:}") String encryptionKey,
                          @Value("${assistant.whatsapp.hmac-secret:}") String hmacSecret,
                          @Value("${assistant.whatsapp.key-version:v1}") String keyVersion) {
        this.encryptionKey = encryptionKey; this.hmacSecret = hmacSecret; this.keyVersion = keyVersion;
    }
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] joined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, joined, 0, iv.length); System.arraycopy(encrypted, 0, joined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(joined);
        } catch (Exception failure) { throw configuration("Falha ao cifrar dados do WhatsApp"); }
    }
    public String decrypt(String ciphertext) {
        try {
            byte[] joined = Base64.getDecoder().decode(ciphertext); byte[] iv = java.util.Arrays.copyOfRange(joined, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(joined, 12, joined.length - 12), StandardCharsets.UTF_8);
        } catch (Exception failure) { throw configuration("Falha ao decifrar dados do WhatsApp"); }
    }
    public String hmac(String value) {
        try {
            if (hmacSecret.isBlank()) throw configuration("Segredo HMAC do WhatsApp ausente");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (AssistantException failure) { throw failure; }
        catch (Exception failure) { throw configuration("Falha no HMAC do WhatsApp"); }
    }
    public boolean validSignature(byte[] body, String signature, String appSecret) {
        if (appSecret.isBlank() || signature == null || !signature.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().parseHex(signature.substring(7));
            return MessageDigest.isEqual(mac.doFinal(body), expected);
        } catch (Exception invalid) { return false; }
    }
    public String keyVersion() { return keyVersion; }
    private SecretKeySpec aesKey() {
        if (encryptionKey.isBlank()) throw configuration("Chave de cifra do WhatsApp ausente");
        byte[] decoded = Base64.getDecoder().decode(encryptionKey);
        if (decoded.length != 32) throw configuration("Chave de cifra do WhatsApp deve ter 32 bytes");
        return new SecretKeySpec(decoded, "AES");
    }
    private AssistantException configuration(String message) {
        return new AssistantException("PROVIDER_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
