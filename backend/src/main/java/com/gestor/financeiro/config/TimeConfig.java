package com.gestor.financeiro.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Timezone de negócio único (ADR-0003). Serviços financeiros nunca usam
 * {@code now()} sem este Clock — o guard {@code BusinessClockGuardTest} garante.
 */
@Configuration
public class TimeConfig {

    /** Precisão das colunas {@code timestamp(6)} do Postgres e do H2. */
    private static final Duration PRECISAO_BANCO = Duration.ofNanos(1_000);

    /**
     * O clock trunca em microssegundos porque é essa a precisão que o banco guarda.
     * Em Linux {@code Clock.system} entrega nanossegundos: sem o truncamento, o valor
     * devolvido na primeira resposta difere do valor relido do banco em um replay
     * idempotente. Em macOS o relógio já é de microssegundos, então a divergência só
     * aparecia no build Docker.
     */
    @Bean
    public Clock clock(@Value("${app.business.timezone:America/Sao_Paulo}") String timezone) {
        return Clock.tick(Clock.system(ZoneId.of(timezone)), PRECISAO_BANCO);
    }
}
