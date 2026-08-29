package com.gestor.financeiro.config;

import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.service.assistant.ProviderExtraction;
import com.gestor.financeiro.service.assistant.ProviderExtractionRequest;
import com.gestor.financeiro.service.assistant.StructuredAiProvider;
import com.gestor.financeiro.service.assistant.TransactionDraftV1;
import com.gestor.financeiro.service.assistant.TranscriptionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Providers determinísticos disponíveis exclusivamente no profile local-e2e. */
@Configuration
@Profile("local-e2e")
public class LocalE2eAssistantConfiguration {
    private static final Pattern FIRST_ACCOUNT = Pattern.compile("Contas permitidas: \\[([^,\\]]+)");
    private static final Pattern FIRST_CATEGORY = Pattern.compile("Categorias permitidas: \\[([^,\\]]+)");

    @Bean("geminiStructuredAiProvider")
    StructuredAiProvider localE2ePrimaryProvider() {
        return structured("LOCAL_E2E_PRIMARY");
    }

    @Bean("openAiStructuredAiProvider")
    StructuredAiProvider localE2eSecondaryProvider() {
        return structured("LOCAL_E2E_SECONDARY");
    }

    @Bean
    TranscriptionProvider localE2eTranscriptionProvider() {
        return new TranscriptionProvider() {
            @Override public String transcribe(Path audio) { return "gasolina 85 hoje"; }
            @Override public String provider() { return "LOCAL_E2E"; }
            @Override public String model() { return "deterministic-transcript-v1"; }
        };
    }

    private StructuredAiProvider structured(String providerName) {
        return new StructuredAiProvider() {
            @Override
            public ProviderExtraction extract(ProviderExtractionRequest request, String schemaVersion) {
                String account = first(FIRST_ACCOUNT, request.trustedContext());
                String category = first(FIRST_CATEGORY, request.trustedContext());
                boolean ambiguity = request.text() != null && request.text().contains("mercado ontem");
                boolean audio = request.text() != null && request.text().contains("gasolina 85 hoje");
                TransactionDraftV1 draft = new TransactionDraftV1(
                        "CREATE_TRANSACTION", TipoTransacao.SAIDA,
                        ambiguity ? null : new BigDecimal(audio ? "85.00" : "50.00"), audio ? "Gasolina" : "Mercado",
                        ambiguity ? LocalDate.now().minusDays(1) : LocalDate.now(),
                        account, category, ambiguity ? List.of("valor") : List.of());
                return new ProviderExtraction(draft, provider(), model());
            }

            @Override public String provider() { return providerName; }
            @Override public String model() { return "deterministic-draft-v1"; }
        };
    }

    private static String first(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}
