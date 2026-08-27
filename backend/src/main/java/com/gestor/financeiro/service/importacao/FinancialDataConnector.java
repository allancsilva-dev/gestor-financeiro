package com.gestor.financeiro.service.importacao;

import java.io.IOException;

/** SPI streaming. Implementações devem limitar bytes, linhas e campos. */
public interface FinancialDataConnector {
    ConnectorDetection detect(ImportSource source) throws IOException;

    /** Sem mapeamento explícito valem os apelidos conhecidos de cada formato. */
    default void parse(ImportSource source, RecordConsumer consumer) throws IOException {
        parse(source, ImportMapping.automatico(), consumer);
    }

    void parse(ImportSource source, ImportMapping mapeamento, RecordConsumer consumer) throws IOException;

    @FunctionalInterface
    interface RecordConsumer {
        void accept(CanonicalImportRecord record) throws IOException;
    }
}
