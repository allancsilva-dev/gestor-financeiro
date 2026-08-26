package com.gestor.financeiro.service.importacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.service.job.BackgroundJob;
import com.gestor.financeiro.service.job.JobHandler;
import org.springframework.stereotype.Component;

/**
 * Lança o lote revisado. É idempotente porque o serviço pula registro já lançado e a chave de
 * idempotência do ledger cobre a corrida com uma reexecução por lease vencido.
 */
@Component
public class ImportCommitJobHandler implements JobHandler {

    static final String TIPO = "IMPORT_COMMIT";

    private final ImportCommitService commitService;
    private final ObjectMapper objectMapper;

    public ImportCommitJobHandler(ImportCommitService commitService, ObjectMapper objectMapper) {
        this.commitService = commitService;
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
        long batchId = payload.path("batchId").asLong();
        if (usuarioId <= 0 || batchId <= 0) {
            throw new IllegalArgumentException("Payload de commit sem titular ou lote");
        }
        commitService.executar(usuarioId, batchId);
    }
}
