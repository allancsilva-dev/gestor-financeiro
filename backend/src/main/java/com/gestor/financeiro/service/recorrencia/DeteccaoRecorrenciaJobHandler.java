package com.gestor.financeiro.service.recorrencia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.service.job.BackgroundJob;
import com.gestor.financeiro.service.job.JobHandler;
import org.springframework.stereotype.Component;

/** Varre o histórico do titular atrás de repetição. Reexecutar só atualiza sugestão existente. */
@Component
public class DeteccaoRecorrenciaJobHandler implements JobHandler {

    public static final String TIPO = "RECURRENCE_SCAN";

    private final DeteccaoRecorrenciaService deteccao;
    private final ObjectMapper objectMapper;

    public DeteccaoRecorrenciaJobHandler(DeteccaoRecorrenciaService deteccao, ObjectMapper objectMapper) {
        this.deteccao = deteccao;
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
            throw new IllegalArgumentException("Payload de detecção sem titular");
        }
        deteccao.detectar(usuarioId);
    }
}
