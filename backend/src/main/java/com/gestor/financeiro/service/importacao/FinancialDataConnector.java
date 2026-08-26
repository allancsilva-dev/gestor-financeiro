package com.gestor.financeiro.service.importacao;

import java.io.IOException;

/** SPI streaming. Implementações devem limitar bytes, linhas e campos. */
public interface FinancialDataConnector {
    ConnectorDetection detect(ImportSource source) throws IOException;

    void parse(ImportSource source, RecordConsumer consumer) throws IOException;

    @FunctionalInterface
    interface RecordConsumer {
        void accept(CanonicalImportRecord record) throws IOException;
    }
}
