package com.gestor.financeiro.config;

import com.gestor.financeiro.service.assistant.ProviderExtractionRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalE2eAssistantConfigurationTest {
    private final LocalE2eAssistantConfiguration config = new LocalE2eAssistantConfiguration();

    @Test
    void providersSaoDeterministicosESemRede() {
        var request = new ProviderExtractionRequest(1L, null, "mercado ontem",
                "Contas permitidas: [Conta Principal]\nCategorias permitidas: [Mercado]");

        var result = config.localE2ePrimaryProvider().extract(request, "transaction-draft-v1");

        assertThat(result.provider()).isEqualTo("LOCAL_E2E_PRIMARY");
        assertThat(result.draft().valor()).isNull();
        assertThat(result.draft().contaNome()).isEqualTo("Conta Principal");
        assertThat(result.draft().categoriaNome()).isEqualTo("Mercado");
        assertThat(result.draft().missingFields()).containsExactly("valor");
        assertThat(config.localE2eTranscriptionProvider().transcribe(Path.of("unused")))
                .isEqualTo("gasolina 85 hoje");
    }
}
