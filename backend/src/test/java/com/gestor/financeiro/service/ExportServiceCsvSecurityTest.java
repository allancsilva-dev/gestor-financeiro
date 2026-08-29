package com.gestor.financeiro.service;

import com.gestor.financeiro.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
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

    @Test
    void erroSqlNaExportacaoDoAssistenteFalhaExplicitamente() {
        JdbcTemplate failingJdbc = new JdbcTemplate() {
            @Override
            public void query(String sql, RowCallbackHandler handler, Object... args) {
                throw new DataAccessResourceFailureException("falha SQL controlada");
            }
        };
        ExportService service = new ExportService(Clock.systemUTC(), failingJdbc,
                mock(TransacaoRepository.class), mock(CategoriaRepository.class), mock(ContaRepository.class),
                mock(CarteiraRepository.class), mock(MetaRepository.class), mock(ContaFixaRepository.class),
                mock(UsuarioRepository.class));

        assertThrows(DataAccessResourceFailureException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "exportarAssistente", 1L));
    }
}
