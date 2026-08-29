package com.gestor.financeiro.service.assistant;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiExtractionPipelineTest {
    private static final ProviderExtractionRequest REQUEST = new ProviderExtractionRequest(1L, null, "mercado 50", "");
    private static final TransactionDraftV1 DRAFT = new TransactionDraftV1("CREATE_TRANSACTION",
            com.gestor.financeiro.model.enums.TipoTransacao.SAIDA, new BigDecimal("50"), "Mercado",
            LocalDate.of(2026, 8, 27), "Nubank", "Mercado", null, null, List.of());

    @Test
    void falhaTemporariaDoPrimarioUsaSecundario() {
        AiExtractionPipeline pipeline = new AiExtractionPipeline(failing(ProviderFailure.Kind.RETRYABLE), ok("OPENAI"), true);
        assertThat(pipeline.extract(REQUEST).orElseThrow().provider()).isEqualTo("OPENAI");
    }

    @Test
    void falhaDosDoisVoltaVazioParaParserFormulario() {
        AiExtractionPipeline pipeline = new AiExtractionPipeline(failing(ProviderFailure.Kind.SCHEMA),
                failing(ProviderFailure.Kind.RETRYABLE), true);
        assertThat(pipeline.extract(REQUEST)).isEmpty();
    }

    @Test
    void recusaDeSegurancaNaoFazFailover() {
        AiExtractionPipeline pipeline = new AiExtractionPipeline(failing(ProviderFailure.Kind.SAFETY_REFUSAL), ok("OPENAI"), true);
        assertThatThrownBy(() -> pipeline.extract(REQUEST)).isInstanceOf(ProviderFailure.class)
                .extracting(e -> ((ProviderFailure) e).kind()).isEqualTo(ProviderFailure.Kind.SAFETY_REFUSAL);
    }

    private StructuredAiProvider ok(String name) { return provider(name, null); }
    private StructuredAiProvider failing(ProviderFailure.Kind kind) { return provider("FAIL", kind); }
    private StructuredAiProvider provider(String name, ProviderFailure.Kind failure) {
        return new StructuredAiProvider() {
            public ProviderExtraction extract(ProviderExtractionRequest request, String schema) {
                if (failure != null) throw new ProviderFailure(failure, "falha");
                return new ProviderExtraction(DRAFT, name, "model");
            }
            public String provider() { return name; }
            public String model() { return "model"; }
        };
    }
}
