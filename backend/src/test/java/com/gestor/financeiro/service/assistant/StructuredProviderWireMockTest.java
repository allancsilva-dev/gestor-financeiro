package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StructuredProviderWireMockTest {
    private static final String DRAFT = """
            {"intent":"CREATE_TRANSACTION","tipo":"SAIDA","valor":50,"descricao":"Mercado",
             "data":"2026-08-27","contaNome":"Nubank","categoriaNome":"Mercado","missingFields":[]}
            """;

    private WireMockServer server;
    private ProviderResilienceExecutor resilience;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(wireMockConfig().bindAddress("127.0.0.1").dynamicPort());
        try { server.start(); }
        catch (RuntimeException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Sandbox não permite socket local: " + unavailable.getMessage());
        }
    }

    @AfterEach
    void shutdown() {
        if (resilience != null) resilience.shutdown();
        if (server != null && server.isRunning()) server.stop();
    }

    @Test
    void geminiEnviaSchemaEstritoELeRespostaEstruturada() throws Exception {
        server.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-test:generateContent"))
                .withQueryParam("key", equalTo("secret"))
                .withRequestBody(matchingJsonPath("$.generationConfig.responseJsonSchema.additionalProperties", equalTo("false")))
                .withRequestBody(containing("UNTRUSTED_USER_TEXT"))
                .willReturn(okJson("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                        + new ObjectMapper().writeValueAsString(DRAFT) + "}]}}]}")));

        ProviderExtraction extraction = gemini().extract(request(), TransactionDraftSchema.VERSION);

        assertThat(extraction.provider()).isEqualTo("GEMINI");
        assertThat(extraction.draft().valor()).isEqualByComparingTo("50");
    }

    @Test
    void openAiDesligaStoreEFerramentasEUsaMesmoSchema() throws Exception {
        server.stubFor(post(urlEqualTo("/v1/responses"))
                .withHeader("Authorization", equalTo("Bearer secret"))
                .withRequestBody(matchingJsonPath("$.store", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.text.format.strict", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.text.format.schema.additionalProperties", equalTo("false")))
                .willReturn(okJson("{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":"
                        + new ObjectMapper().writeValueAsString(DRAFT) + "}]}]}")));

        ProviderExtraction extraction = openAi().extract(request(), TransactionDraftSchema.VERSION);

        assertThat(extraction.provider()).isEqualTo("OPENAI");
        assertThat(extraction.draft().contaNome()).isEqualTo("Nubank");
    }

    @Test
    void erro429RespeitaContratoETentaUmaVezMais() {
        server.stubFor(post(urlEqualTo("/v1/responses"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> openAi().extract(request(), TransactionDraftSchema.VERSION))
                .isInstanceOfSatisfying(ProviderFailure.class,
                        failure -> assertThat(failure.kind()).isEqualTo(ProviderFailure.Kind.RETRYABLE));
        server.verify(2, postRequestedFor(urlEqualTo("/v1/responses")));
    }

    @Test
    void metaEnviaMensagemPeloContratoCloudApiSemDadosEmQueryString() {
        server.stubFor(post(urlEqualTo("/v23.0/phone-id/messages"))
                .withHeader("Authorization", equalTo("Bearer meta-secret"))
                .withRequestBody(matchingJsonPath("$.messaging_product", equalTo("whatsapp")))
                .withRequestBody(matchingJsonPath("$.type", equalTo("text")))
                .withRequestBody(matchingJsonPath("$.text.preview_url", equalTo("false")))
                .willReturn(aResponse().withStatus(200)));

        new MetaWhatsappClient(RestClient.builder(), resilience(), server.baseUrl(), "meta-secret",
                "phone-id", "v23.0", 2).sendText("5511999999999", "Rascunho pronto");

        server.verify(1, postRequestedFor(urlEqualTo("/v23.0/phone-id/messages")));
    }

    private GeminiStructuredAiProvider gemini() {
        return new GeminiStructuredAiProvider(new ObjectMapper(), RestClient.builder(), resilience(),
                mock(AssistantUsageBudget.class), mock(AssistantInvocationAudit.class),
                server.baseUrl(), "secret", "gemini-test", 2);
    }

    private OpenAiStructuredAiProvider openAi() {
        return new OpenAiStructuredAiProvider(new ObjectMapper(), RestClient.builder(), resilience(),
                mock(AssistantUsageBudget.class), mock(AssistantInvocationAudit.class),
                server.baseUrl(), "secret", "openai-test", 2);
    }

    private ProviderResilienceExecutor resilience() {
        if (resilience == null) resilience = new ProviderResilienceExecutor(
                new SimpleMeterRegistry(), 2, 1, Duration.ofSeconds(3), Duration.ofSeconds(3));
        return resilience;
    }

    private ProviderExtractionRequest request() {
        return new ProviderExtractionRequest(7L, 11L, "mercado 50", "contas: Nubank");
    }
}
