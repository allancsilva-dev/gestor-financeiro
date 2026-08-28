package com.gestor.financeiro.service.assistant;

public interface StructuredAiProvider {
    ProviderExtraction extract(ProviderExtractionRequest request, String schemaVersion);
    String provider();
    String model();
}
