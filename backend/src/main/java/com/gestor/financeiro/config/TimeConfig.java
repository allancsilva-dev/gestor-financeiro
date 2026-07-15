package com.gestor.financeiro.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Timezone de negócio único (ADR-0003). Serviços financeiros nunca usam
 * {@code now()} sem este Clock — o guard {@code BusinessClockGuardTest} garante.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${app.business.timezone:America/Sao_Paulo}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
