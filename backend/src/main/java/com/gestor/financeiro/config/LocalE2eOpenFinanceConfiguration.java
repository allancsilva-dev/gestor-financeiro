package com.gestor.financeiro.config;

import com.gestor.financeiro.service.openfinance.FakeOpenFinanceProvider;
import com.gestor.financeiro.service.openfinance.OpenFinanceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Provedor determinístico exclusivo do profile {@code local-e2e}.
 *
 * <p>Mesma disciplina do assistente: o fake nunca é candidato a bean em {@code prod} ou {@code vps},
 * e a única forma de ativá-lo é o profile do runner E2E. É o que permite desenvolver e provar a
 * fase inteira sem parceiro contratado e sem dado bancário real.</p>
 */
@Configuration
@Profile("local-e2e")
public class LocalE2eOpenFinanceConfiguration {

    @Bean
    public OpenFinanceProvider localE2eOpenFinanceProvider(java.time.Clock clock) {
        return new FakeOpenFinanceProvider(clock);
    }
}
