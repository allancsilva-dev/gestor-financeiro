package com.gestor.financeiro.service.assistant;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsappCryptoTest {
    private final WhatsappCrypto crypto = new WhatsappCrypto(
            Base64.getEncoder().encodeToString(new byte[32]), "hmac-secret", "v7");

    @Test void cifraComIvAleatorioEDecifraSemExporTexto() {
        String first = crypto.encrypt("5511999999999");
        String second = crypto.encrypt("5511999999999");
        assertThat(first).isNotEqualTo(second).doesNotContain("5511999999999");
        assertThat(crypto.decrypt(first)).isEqualTo("5511999999999");
        assertThat(crypto.keyVersion()).isEqualTo("v7");
    }

    @Test void assinaturaUsaCorpoBrutoEComparacaoCriptografica() throws Exception {
        byte[] body = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("app-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        assertThat(crypto.validSignature(body, signature, "app-secret")).isTrue();
        assertThat(crypto.validSignature("alterado".getBytes(StandardCharsets.UTF_8), signature, "app-secret")).isFalse();
    }
}
