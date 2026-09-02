package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.enums.ImportFormat;

import java.io.IOException;

/** SPI streaming. Implementações devem limitar bytes, linhas e campos. */
public interface FinancialDataConnector {
    /**
     * Formato que esta implementação atende, um por conector.
     *
     * <p>Existe para o caminho em que a origem já sabe o que está entregando e a detecção
     * heurística seria só uma chance de errar — mapeamento de coluna do titular hoje, conector de
     * rede depois. O retorno é enum de propósito: a guarda de arquitetura proíbe {@code List},
     * {@code Path} e {@code URL} nas assinaturas desta SPI, e é assim que ela continua valendo.</p>
     */
    ImportFormat format();

    ConnectorDetection detect(ImportSource source) throws IOException;

    /** Sem mapeamento explícito valem os apelidos conhecidos de cada formato. */
    default void parse(ImportSource source, RecordConsumer consumer) throws IOException {
        parse(source, ImportMapping.automatico(), consumer);
    }

    void parse(ImportSource source, ImportMapping mapeamento, RecordConsumer consumer) throws IOException;

    default ImportStatementBalances declaredBalances(ImportSource source, ImportMapping mapeamento) throws IOException {
        return ImportStatementBalances.unavailable();
    }

    @FunctionalInterface
    interface RecordConsumer {
        void accept(CanonicalImportRecord record) throws IOException;
    }
}
