package com.gestor.financeiro;

import com.gestor.financeiro.config.TimeConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O Clock de negócio precisa nascer na mesma precisão das colunas {@code timestamp(6)}:
 * em Linux o relógio do JDK tem nanossegundos e o valor gravado deixa de bater com o
 * valor relido do banco — foi o que quebrou o replay do assistente no build Docker.
 */
class TimeConfigTest {

    private final Clock clock = new TimeConfig().clock("America/Sao_Paulo");

    @Test
    void clockDeNegocioNaoProduzSubMicrossegundo() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(clock.instant().getNano() % 1_000)
                    .as("Clock de negócio deve truncar em microssegundos")
                    .isZero();
        }
    }

    @Test
    void clockDeNegocioPreservaTimezoneDoAdr0003() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }
}
