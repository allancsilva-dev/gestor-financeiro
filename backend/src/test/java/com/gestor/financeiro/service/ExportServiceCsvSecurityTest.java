package com.gestor.financeiro.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportServiceCsvSecurityTest {

    @Test
    void neutralizaFormulasMesmoAposEspacos() {
        assertEquals("\"'=HYPERLINK(\"\"https://evil\"\")\"",
                ExportService.escapeCsv("=HYPERLINK(\"https://evil\")"));
        assertEquals("'  +1+1", ExportService.escapeCsv("  +1+1"));
        assertEquals("'-10", ExportService.escapeCsv("-10"));
        assertEquals("'@SUM(A1:A2)", ExportService.escapeCsv("@SUM(A1:A2)"));
    }

    @Test
    void aplicaEscapeRfc4180DepoisDaNeutralizacao() {
        assertEquals("\"'=cmd,1\"", ExportService.escapeCsv("=cmd,1"));
        assertEquals("\"texto \"\"citado\"\"\"", ExportService.escapeCsv("texto \"citado\""));
        assertEquals("normal", ExportService.escapeCsv("normal"));
        assertEquals("", ExportService.escapeCsv(null));
    }

    @Test
    void exportacaoDoAssistenteIncluiHashesEReplaysPseudonimizados() {
        for (String tabela : java.util.List.of("assistant_messages", "assistant_invocations")) {
            String sql = ExportService.ASSISTANT_EXPORT_TABLES.stream()
                    .filter(export -> export.table().equals(tabela))
                    .findFirst()
                    .orElseThrow()
                    .sql();

            assertTrue(sql.contains("request_hash"), tabela + " deve exportar o hash do request");
            assertTrue(sql.contains("response_json"), tabela + " deve exportar a resposta idempotente retida");
        }
    }
}
