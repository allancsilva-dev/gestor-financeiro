package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.model.enums.ImportFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@Component
public final class ImportConnectorRegistry {
    private final List<FinancialDataConnector> connectors;
    public ImportConnectorRegistry(List<FinancialDataConnector> connectors) { this.connectors = List.copyOf(connectors); }

    public DetectedConnector detect(ImportSource source) throws IOException {
        List<Candidate> candidates = connectors.stream().map(connector -> candidate(connector, source))
                .filter(candidate -> candidate.detection() != null)
                .sorted(Comparator.comparingInt((Candidate c) -> c.detection().confidence()).reversed()).toList();
        if (candidates.isEmpty()) {
            for (FinancialDataConnector connector : connectors) {
                try { connector.detect(source); }
                catch (ImportParsingException specific) { throw specific; }
                catch (IOException ignored) { }
            }
            throw new ImportParsingException(ImportFailureCode.DETECTION_FAILED, "Formato não reconhecido");
        }
        if (candidates.get(0).detection().confidence() < 80)
            throw new ImportParsingException(ImportFailureCode.DETECTION_FAILED, "Formato não reconhecido");
        if (candidates.size() > 1 && candidates.get(0).detection().confidence() - candidates.get(1).detection().confidence() < 15)
            throw new ImportParsingException(ImportFailureCode.DETECTION_FAILED, "Formato ambíguo");
        Candidate winner = candidates.get(0);
        return new DetectedConnector(winner.connector(), winner.detection());
    }
    /**
     * Conector escolhido pelo formato, sem detecção heurística.
     *
     * <p>Para quando a origem já declara o que entrega: o mapeamento de colunas do titular diz que
     * o arquivo é CSV, e o conector de rede sabe o que buscou. Submeter esse caso à detecção seria
     * só uma chance de recusar por ambiguidade um conteúdo sobre o qual não há dúvida.</p>
     */
    public DetectedConnector forFormat(ImportFormat formato) throws ImportParsingException {
        if (formato == null || formato == ImportFormat.UNKNOWN)
            throw new ImportParsingException(ImportFailureCode.UNSUPPORTED_FORMAT, "Formato não informado");
        return connectors.stream().filter(connector -> connector.format() == formato).findFirst()
                .map(connector -> new DetectedConnector(connector, new ConnectorDetection(formato, null, 100)))
                .orElseThrow(() -> new ImportParsingException(ImportFailureCode.UNSUPPORTED_FORMAT,
                        "Nenhum conector atende o formato"));
    }

    private Candidate candidate(FinancialDataConnector connector, ImportSource source) {
        try { return new Candidate(connector, connector.detect(source)); }
        catch (IOException | RuntimeException ignored) { return new Candidate(connector, null); }
    }
    public record DetectedConnector(FinancialDataConnector connector, ConnectorDetection detection) { }
    private record Candidate(FinancialDataConnector connector, ConnectorDetection detection) { }
}
