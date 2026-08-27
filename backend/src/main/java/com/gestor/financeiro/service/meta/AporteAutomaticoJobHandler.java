package com.gestor.financeiro.service.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.service.job.BackgroundJob;
import com.gestor.financeiro.service.job.JobHandler;
import org.springframework.stereotype.Component;

/** Executa os aportes devidos do titular. Idempotente por competência: repetir não reserva 2x. */
@Component
public class AporteAutomaticoJobHandler implements JobHandler {

    public static final String TIPO = "META_APORTE";

    private final AporteAutomaticoService aportes;
    private final ObjectMapper objectMapper;

    public AporteAutomaticoJobHandler(AporteAutomaticoService aportes, ObjectMapper objectMapper) {
        this.aportes = aportes;
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
        if (usuarioId <= 0) {
            throw new IllegalArgumentException("Payload de aporte sem titular");
        }
        aportes.executar(usuarioId);
    }
}
