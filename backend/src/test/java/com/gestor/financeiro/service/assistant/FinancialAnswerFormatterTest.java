package com.gestor.financeiro.service.assistant;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialAnswerFormatterTest {
    private final FinancialAnswerFormatter formatter = new FinancialAnswerFormatter();

    @Test
    void respostaTrazProvenienciaECompetencia() {
        String answer = formatter.format(new FinancialToolResult(ValidatedFinancialQuery.Intent.BALANCE,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                Instant.parse("2026-08-27T15:30:00Z"), "/api/v1/metricas", true, null,
                Map.of("disponivelAgora", new BigDecimal("100.00"),
                        "disponivelParaGastar", new BigDecimal("80.00"),
                        "patrimonioLiquido", new BigDecimal("500.00"))));
        assertThat(answer).contains("Competência: 01/08/2026 a 31/08/2026")
                .contains("Atualizado em 27/08/2026 12:30")
                .contains("Fonte: /api/v1/metricas");
    }

    @Test
    void divergenciaNuncaEhApresentadaComoOficial() {
        String answer = formatter.format(new FinancialToolResult(ValidatedFinancialQuery.Intent.INVOICES,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), Instant.EPOCH,
                "/api/v1/faturas", false, "Há dados não reconciliados; estes valores não devem ser tratados como oficiais.",
                Map.of("dividas", BigDecimal.TEN)));
        assertThat(answer).contains("Atenção:").contains("não devem ser tratados como oficiais");
    }
}
