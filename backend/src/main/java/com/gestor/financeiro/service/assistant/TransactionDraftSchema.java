package com.gestor.financeiro.service.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TransactionDraftSchema {
    public static final String VERSION = "transaction-draft-v1";
    private TransactionDraftSchema() { }
    public static Map<String, Object> jsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("intent", Map.of("type", List.of("string", "null")));
        properties.put("tipo", Map.of("type", List.of("string", "null"), "enum", java.util.Arrays.asList("ENTRADA", "SAIDA", null)));
        properties.put("valor", Map.of("type", List.of("number", "null")));
        properties.put("descricao", Map.of("type", List.of("string", "null")));
        properties.put("data", Map.of("type", List.of("string", "null"), "format", "date"));
        properties.put("contaNome", Map.of("type", List.of("string", "null")));
        properties.put("categoriaNome", Map.of("type", List.of("string", "null")));
        // Cartão e parcelas andam juntos: parcelar sem cartão é rascunho incompleto.
        properties.put("cartaoNome", Map.of("type", List.of("string", "null")));
        properties.put("parcelas", Map.of("type", List.of("integer", "null"), "minimum", 2, "maximum", 48));
        properties.put("missingFields", Map.of("type", "array", "items", Map.of("type", "string")));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", List.of("intent", "tipo", "valor", "descricao", "data", "contaNome", "categoriaNome",
                        "cartaoNome", "parcelas", "missingFields")
        );
    }
}
