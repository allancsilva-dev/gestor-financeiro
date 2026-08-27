package com.gestor.financeiro.service.importacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.service.job.BackgroundJob;
import com.gestor.financeiro.service.job.JobHandler;
import org.springframework.stereotype.Component;

/** Estorna um lote já lançado. Idempotente: registro já revertido é pulado. */
@Component
public class ImportReversalJobHandler implements JobHandler {

    private final ImportReversalService reversalService;
    private final ObjectMapper objectMapper;

    public ImportReversalJobHandler(ImportReversalService reversalService, ObjectMapper objectMapper) {
        this.reversalService = reversalService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return ImportReversalService.TIPO_JOB_REVERSAO;
    }

    @Override
    public void handle(BackgroundJob job) throws Exception {
        JsonNode payload = objectMapper.readTree(job.payload());
        long usuarioId = payload.path("usuarioId").asLong();
        long batchId = payload.path("batchId").asLong();
        if (usuarioId <= 0 || batchId <= 0) {
            throw new IllegalArgumentException("Payload de reversão sem titular ou lote");
        }
        reversalService.executar(usuarioId, batchId);
    }
}
