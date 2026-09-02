package com.gestor.financeiro.service.openfinance;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cifra de credencial de terceiro: o que ela promete, e o que ela recusa. */
class OpenFinanceCryptoTest {

    private static final String CHAVE_V1 = chave((byte) 1);
    private static final String CHAVE_V2 = chave((byte) 2);

    private static String chave(byte preenchimento) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, preenchimento);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private OpenFinanceCrypto crypto(String chaveCorrente, String versao, String anteriores) {
        return new OpenFinanceCrypto(chaveCorrente, versao, anteriores, "segredo-hmac");
    }

    @Test
    void cifraEDecifraNaVersaoCorrente() {
        OpenFinanceCrypto crypto = crypto(CHAVE_V1, "v1", "");
        OpenFinanceCrypto.Cifrado cifrado = crypto.encrypt("token-de-acesso");

        assertEquals("v1", cifrado.keyVersion());
        assertNotEquals("token-de-acesso", cifrado.valor());
        assertEquals("token-de-acesso", crypto.decrypt(cifrado.valor(), "v1"));
    }

    /**
     * IV sorteado por operação: cifrar o mesmo token duas vezes não pode produzir o mesmo texto,
     * senão o banco revelaria quais linhas guardam o mesmo segredo.
     */
    @Test
    void mesmoTextoCifradoDuasVezesProduzSaidasDiferentes() {
        OpenFinanceCrypto crypto = crypto(CHAVE_V1, "v1", "");
        assertNotEquals(crypto.encrypt("igual").valor(), crypto.encrypt("igual").valor());
    }

    /**
     * O ponto da rotação: durante a transição, o que foi cifrado na chave antiga continua legível
     * enquanto o novo já sai na chave nova. Sem isto, trocar a chave exigiria janela de parada.
     */
    @Test
    void duranteRotacaoDecifraVersaoAntigaECifraNaNova() {
        OpenFinanceCrypto antes = crypto(CHAVE_V1, "v1", "");
        OpenFinanceCrypto.Cifrado antigo = antes.encrypt("token-antigo");

        OpenFinanceCrypto depois = crypto(CHAVE_V2, "v2", "v1:" + CHAVE_V1);

        assertEquals("token-antigo", depois.decrypt(antigo.valor(), "v1"));
        assertEquals("v2", depois.encrypt("token-novo").keyVersion());
    }

    @Test
    void versaoRetiradaDeixaDeDecifrar() {
        OpenFinanceCrypto antes = crypto(CHAVE_V1, "v1", "");
        OpenFinanceCrypto.Cifrado antigo = antes.encrypt("token-antigo");

        OpenFinanceCrypto semAntiga = crypto(CHAVE_V2, "v2", "");

        assertThrows(IllegalStateException.class, () -> semAntiga.decrypt(antigo.valor(), "v1"));
    }

    /** Texto adulterado não decifra: a tag do GCM é autenticação, não só sigilo. */
    @Test
    void payloadAdulteradoNaoDecifra() {
        OpenFinanceCrypto crypto = crypto(CHAVE_V1, "v1", "");
        String valor = crypto.encrypt("token").valor();
        byte[] bytes = Base64.getDecoder().decode(valor);
        bytes[bytes.length - 1] ^= 0x01;
        String adulterado = Base64.getEncoder().encodeToString(bytes);

        assertThrows(IllegalStateException.class, () -> crypto.decrypt(adulterado, "v1"));
    }

    @Test
    void chaveComTamanhoErradoNaoSobe() {
        String curta = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> crypto(curta, "v1", ""));
    }

    @Test
    void semChaveNaoSeDizConfiguradaENaoCifra() {
        OpenFinanceCrypto crypto = crypto("", "v1", "");
        assertFalse(crypto.configurada());
        assertThrows(IllegalStateException.class, () -> crypto.encrypt("token"));
    }

    @Test
    void hmacEEstavelEExigeSegredo() {
        OpenFinanceCrypto crypto = crypto(CHAVE_V1, "v1", "");
        assertEquals(crypto.hmac("token"), crypto.hmac("token"));
        assertNotEquals(crypto.hmac("token"), crypto.hmac("outro"));
        assertTrue(crypto.hmac("token").matches("[a-f0-9]{64}"));

        OpenFinanceCrypto semSegredo = new OpenFinanceCrypto(CHAVE_V1, "v1", "", "");
        assertThrows(IllegalStateException.class, () -> semSegredo.hmac("token"));
    }

    @Test
    void comparacaoConstanteNaoAceitaNuloNemDivergente() {
        OpenFinanceCrypto crypto = crypto(CHAVE_V1, "v1", "");
        assertTrue(crypto.iguais("abc", "abc"));
        assertFalse(crypto.iguais("abc", "abd"));
        assertFalse(crypto.iguais(null, "abc"));
        assertFalse(crypto.iguais("abc", null));
    }
}
