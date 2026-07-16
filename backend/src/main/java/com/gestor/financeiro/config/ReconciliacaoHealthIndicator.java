package com.gestor.financeiro.config;

import com.gestor.financeiro.service.ReconciliacaoObservabilidade;
import com.gestor.financeiro.service.ReconciliacaoSistemaResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component("reconciliation")
@RequiredArgsConstructor
public class ReconciliacaoHealthIndicator implements HealthIndicator {
    public static final Status DEGRADED = new Status("DEGRADED");
    private final ReconciliacaoObservabilidade observabilidade;

    @Override
    public Health health() {
        ReconciliacaoSistemaResultado result = observabilidade.ultima();
        if (result == null) return Health.unknown().build();
        Health.Builder builder = result.degradado() ? Health.status(DEGRADED) : Health.up();
        return builder.withDetail("executadoEm", result.executadoEm())
                .withDetail("usuarios", result.usuarios())
                .withDetail("verificacoes", result.verificacoes())
                .withDetail("divergencias", result.divergencias())
                .withDetail("erros", result.erros())
                .build();
    }
}
