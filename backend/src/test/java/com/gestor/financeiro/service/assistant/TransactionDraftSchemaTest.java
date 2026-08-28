package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionDraftSchemaTest {
    @Test
    void confidenceECampoAdicionalSaoRecusados() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        String json = """
                {"intent":"CREATE_TRANSACTION","tipo":"SAIDA","valor":50,"descricao":"Mercado",
                 "data":"2026-08-27","contaNome":"Nubank","categoriaNome":"Mercado",
                 "missingFields":[],"confidence":0.99}
                """;
        assertThatThrownBy(() -> mapper.readValue(json, TransactionDraftV1.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class);
    }
}
