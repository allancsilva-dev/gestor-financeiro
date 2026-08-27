package com.gestor.financeiro.service.orcamento;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.service.job.BackgroundJob;
import com.gestor.financeiro.service.job.JobHandler;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/** Fecha a competência de orçamento do titular. Idempotente: competência já fechada é no-op. */
@Component
public class OrcamentoFechamentoJobHandler implements JobHandler {

    public static final String TIPO = "BUDGET_CLOSE";

    private final OrcamentoFechamentoService fechamento;
    private final ObjectMapper objectMapper;

    public OrcamentoFechamentoJobHandler(OrcamentoFechamentoService fechamento, ObjectMapper objectMapper) {
        this.fechamento = fechamento;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return TIPO;
    }

    @Override
    public void handle(BackgroundJob job) throws Exception {
        JsonNode payload = objectMapper.readTree(job.payload());
        long usuarioId = payload.path("usuarioId").asLong();
        String competencia = payload.path("competencia").asText();
        if (usuarioId <= 0 || competencia.isBlank()) {
            throw new IllegalArgumentException("Payload de fechamento sem titular ou competência");
        }
        fechamento.fechar(usuarioId, YearMonth.parse(competencia));
    }
}
