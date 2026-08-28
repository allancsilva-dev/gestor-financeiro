package com.gestor.financeiro.service.assistant;

import java.util.List;
import java.util.Map;

public final class TransactionDraftSchema {
    public static final String VERSION = "transaction-draft-v1";
    private TransactionDraftSchema() { }
    public static Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "intent", Map.of("type", List.of("string", "null")),
                        "tipo", Map.of("type", List.of("string", "null"), "enum", java.util.Arrays.asList("ENTRADA", "SAIDA", null)),
                        "valor", Map.of("type", List.of("number", "null")),
                        "descricao", Map.of("type", List.of("string", "null")),
                        "data", Map.of("type", List.of("string", "null"), "format", "date"),
                        "contaNome", Map.of("type", List.of("string", "null")),
                        "categoriaNome", Map.of("type", List.of("string", "null")),
                        "missingFields", Map.of("type", "array", "items", Map.of("type", "string"))
                ),
                "required", List.of("intent", "tipo", "valor", "descricao", "data", "contaNome", "categoriaNome", "missingFields")
        );
    }
}
