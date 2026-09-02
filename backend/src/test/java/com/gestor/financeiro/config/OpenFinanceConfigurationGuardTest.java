package com.gestor.financeiro.config;

import com.gestor.financeiro.service.openfinance.OpenFinanceCrypto;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O guard existe para transformar configuração faltante em falha de boot, e não em descoberta
 * tardia com credencial de terceiro já em jogo.
 */
class OpenFinanceConfigurationGuardTest {

    private static final String CHAVE = Base64.getEncoder().encodeToString(new byte[32]);

    private OpenFinanceConfigurationGuard guard(String perfil, boolean ligado, String chave,
                                                String hmac, boolean politica, String provedor,
                                                String redirect) {
        MockEnvironment environment = new MockEnvironment();
        if (perfil != null) environment.setActiveProfiles(perfil);
        OpenFinanceCrypto crypto = new OpenFinanceCrypto(chave, "v1", "", hmac);
        OpenFinanceConfigurationGuard guard = new OpenFinanceConfigurationGuard(environment, crypto);
        guard.enabled = ligado;
        guard.hmacSecret = hmac;
        guard.politicaAceita = politica;
        guard.provedor = provedor;
        guard.redirectUri = redirect;
        return guard;
    }

    private OpenFinanceConfigurationGuard completo(String perfil) {
        return guard(perfil, true, CHAVE, "segredo", true, "FAKE", "https://app.exemplo/callback");
    }

    /**
     * O profile do runner E2E liga o conector; se a chave dali estiver malformada, a falha só
     * apareceria no meio de uma execução longa, e como erro de cifra em vez de erro de configuração.
     */
    @Test
    void profileLocalE2eTemConfiguracaoQueOGuardAceita() throws Exception {
        java.util.Properties props = new java.util.Properties();
        try (var entrada = java.nio.file.Files.newInputStream(
                java.nio.file.Path.of("src/main/resources/application-local-e2e.properties"))) {
            props.load(entrada);
        }

        OpenFinanceConfigurationGuard guard = guard("prod", true,
                props.getProperty("openfinance.encryption-key"),
                props.getProperty("openfinance.hmac-secret"),
                Boolean.parseBoolean(props.getProperty("openfinance.data-policy-accepted")),
                props.getProperty("openfinance.provider"),
                props.getProperty("openfinance.oauth.redirect-uri"));

        assertDoesNotThrow(guard::validate);
        assertTrue(Boolean.parseBoolean(props.getProperty("openfinance.enabled")));
    }

    @Test
    void producaoComTudoConfiguradoSobe() {
        assertDoesNotThrow(() -> completo("prod").validate());
        assertDoesNotThrow(() -> completo("vps").validate());
    }

    @Test
    void desligadoNaoExigeNada() {
        assertDoesNotThrow(() -> guard("prod", false, "", "", false, "", "").validate());
    }

    /** Fora de produção o desenvolvimento roda contra provedor fake, sem segredo real. */
    @Test
    void foraDeProducaoNaoDerrubaOBoot() {
        assertDoesNotThrow(() -> guard("dev", true, "", "", false, "", "").validate());
    }

    @Test
    void producaoSemChaveDeCifraDerrubaOBoot() {
        var falha = assertThrows(IllegalStateException.class,
                () -> guard("prod", true, "", "segredo", true, "FAKE", "https://app.exemplo/cb").validate());
        assertTrue(falha.getMessage().contains("chave de cifra"));
    }

    @Test
    void producaoSemHmacDerrubaOBoot() {
        assertThrows(IllegalStateException.class,
                () -> guard("prod", true, CHAVE, "", true, "FAKE", "https://app.exemplo/cb").validate());
    }

    @Test
    void producaoSemPoliticaDeDadosDerrubaOBoot() {
        assertThrows(IllegalStateException.class,
                () -> guard("prod", true, CHAVE, "segredo", false, "FAKE", "https://app.exemplo/cb").validate());
    }

    @Test
    void producaoSemProvedorDerrubaOBoot() {
        assertThrows(IllegalStateException.class,
                () -> guard("prod", true, CHAVE, "segredo", true, "", "https://app.exemplo/cb").validate());
    }

    /**
     * Redirect fixa e HTTPS é o que impede o callback de aceitar destino vindo do request — a porta
     * por onde um consentimento seria desviado para outro lugar.
     */
    @Test
    void producaoSemRedirectHttpsFixaDerrubaOBoot() {
        assertThrows(IllegalStateException.class,
                () -> guard("prod", true, CHAVE, "segredo", true, "FAKE", "").validate());
        assertThrows(IllegalStateException.class,
                () -> guard("prod", true, CHAVE, "segredo", true, "FAKE", "http://app.exemplo/cb").validate());
    }
}
