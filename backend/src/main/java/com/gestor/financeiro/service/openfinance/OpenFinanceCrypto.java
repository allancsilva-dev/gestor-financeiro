package com.gestor.financeiro.service.openfinance;

import org.springframework.beans.factory.annotation.Value;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cifra dos segredos de terceiro guardados em {@code conexao_credenciais}.
 *
 * <p>AES-GCM com tag de 128 bits e IV de 12 bytes sorteado por operação. A chave vem só de variável
 * de ambiente; nada de chave em banco ou em arquivo versionado.</p>
 *
 * <p>Diferença deliberada em relação a {@code WhatsappCrypto}, que serviu de molde: aqui a
 * <b>rotação</b> é suportada de verdade. A classe aceita chaves por versão e cifra sempre na versão
 * corrente, mas decifra em qualquer versão ainda declarada. Sem isso, {@code key_version} seria
 * decoração — trocar a chave exigiria parar o sistema e reescrever tudo numa janela, e na prática
 * ninguém trocaria nunca.</p>
 *
 * <p>Procedimento de rotação: declarar a chave nova como corrente mantendo a antiga em
 * {@code openfinance.encryption-keys}, rodar a re-cifra em lote, e só então remover a antiga,
 * conferindo que nenhuma linha ainda aponta para a versão retirada.</p>
 */
@Component
public class OpenFinanceCrypto {

    private static final int TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final Map<String, SecretKeySpec> chavesPorVersao;
    private final String versaoCorrente;
    private final String hmacSecret;
    private final SecureRandom random = new SecureRandom();

    public OpenFinanceCrypto(
            @Value("${openfinance.encryption-key:}") String chaveCorrente,
            @Value("${openfinance.key-version:v1}") String versaoCorrente,
            @Value("${openfinance.encryption-keys:}") String chavesAnteriores,
            @Value("${openfinance.hmac-secret:}") String hmacSecret) {
        this.versaoCorrente = versaoCorrente;
        this.hmacSecret = hmacSecret;
        this.chavesPorVersao = montarChaves(chaveCorrente, versaoCorrente, chavesAnteriores);
    }

    /** Cifra na versão corrente. O retorno carrega a versão para o chamador gravar na linha. */
    public Cifrado encrypt(String texto) {
        if (texto == null) return null;
        SecretKeySpec chave = chaveDe(versaoCorrente);
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] junto = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, junto, 0, iv.length);
            System.arraycopy(cifrado, 0, junto, iv.length, cifrado.length);
            return new Cifrado(Base64.getEncoder().encodeToString(junto), versaoCorrente);
        } catch (Exception falha) {
            // Mensagem sem detalhe: erro de cifra não pode virar oráculo nem vazar material.
            throw new IllegalStateException("Falha ao cifrar credencial de conexão");
        }
    }

    /** Decifra na versão gravada na linha, que pode não ser a corrente durante uma rotação. */
    public String decrypt(String cifradoBase64, String versao) {
        if (cifradoBase64 == null) return null;
        SecretKeySpec chave = chaveDe(versao == null || versao.isBlank() ? versaoCorrente : versao);
        try {
            byte[] junto = Base64.getDecoder().decode(cifradoBase64);
            if (junto.length <= IV_BYTES) throw new IllegalArgumentException("payload curto");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave,
                    new GCMParameterSpec(TAG_BITS, java.util.Arrays.copyOfRange(junto, 0, IV_BYTES)));
            return new String(cipher.doFinal(junto, IV_BYTES, junto.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception falha) {
            throw new IllegalStateException("Falha ao decifrar credencial de conexão");
        }
    }

    /**
     * Impressão para localizar e detectar reuso de um token sem guardar o valor em claro.
     *
     * <p>Não substitui a cifra: serve para responder "já vi este token antes?" sem decifrar linha
     * por linha.</p>
     */
    public String hmac(String valor) {
        if (hmacSecret.isBlank()) throw new IllegalStateException("Segredo HMAC do Open Finance ausente");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (IllegalStateException falha) {
            throw falha;
        } catch (Exception falha) {
            throw new IllegalStateException("Falha no HMAC do Open Finance");
        }
    }

    /** Comparação em tempo constante, para validação de callback e de assinatura. */
    public boolean iguais(String esperado, String recebido) {
        if (esperado == null || recebido == null) return false;
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                recebido.getBytes(StandardCharsets.UTF_8));
    }

    public String versaoCorrente() {
        return versaoCorrente;
    }

    public boolean configurada() {
        return chavesPorVersao.containsKey(versaoCorrente);
    }

    private SecretKeySpec chaveDe(String versao) {
        SecretKeySpec chave = chavesPorVersao.get(versao);
        if (chave == null) throw new IllegalStateException("Chave de cifra ausente para a versão " + versao);
        return chave;
    }

    /**
     * Chaves adicionais no formato {@code versao:base64,versao:base64}, só para leitura durante a
     * rotação. A corrente entra por {@code openfinance.encryption-key}.
     */
    private static Map<String, SecretKeySpec> montarChaves(String chaveCorrente, String versaoCorrente,
                                                           String chavesAnteriores) {
        Map<String, SecretKeySpec> chaves = new LinkedHashMap<>();
        if (chavesAnteriores != null && !chavesAnteriores.isBlank()) {
            for (String entrada : chavesAnteriores.split(",")) {
                String[] partes = entrada.trim().split(":", 2);
                if (partes.length != 2 || partes[0].isBlank() || partes[1].isBlank()) {
                    throw new IllegalStateException("openfinance.encryption-keys mal formada");
                }
                chaves.put(partes[0].trim(), aesKey(partes[1].trim()));
            }
        }
        if (chaveCorrente != null && !chaveCorrente.isBlank()) {
            chaves.put(versaoCorrente, aesKey(chaveCorrente));
        }
        return Map.copyOf(chaves);
    }

    private static SecretKeySpec aesKey(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException invalida) {
            throw new IllegalStateException("Chave de cifra do Open Finance não é Base64");
        }
        if (bytes.length != 32) throw new IllegalStateException("Chave de cifra do Open Finance deve ter 32 bytes");
        return new SecretKeySpec(bytes, "AES");
    }

    /** Texto cifrado com a versão de chave que o produziu; as duas coisas viajam juntas. */
    public record Cifrado(String valor, String keyVersion) { }
}
