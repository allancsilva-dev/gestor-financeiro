package com.gestor.financeiro.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
