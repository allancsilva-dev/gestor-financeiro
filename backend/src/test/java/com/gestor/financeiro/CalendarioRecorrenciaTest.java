package com.gestor.financeiro;

import com.gestor.financeiro.model.enums.FrequenciaRecorrencia;
import com.gestor.financeiro.util.CalendarioRecorrencia;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V72 — calendario de recorrencia. Funcoes puras: a data de referencia entra por
 * parametro, nunca do relogio.
 */
class CalendarioRecorrenciaTest {

    /**
     * O clamp reclampa a partir do diaVencimento persistido, nao do dia da data atual.
     * Sem isso a serie derivaria: jan/31 -> fev/28 -> mar/28 -> para sempre dia 28.
     */
    @Test
    void mensalNoDia31NaoDerivaDepoisDeFevereiro() {
        LocalDate janeiro = LocalDate.of(2026, 1, 31);

        LocalDate fevereiro = CalendarioRecorrencia.proxima(janeiro, FrequenciaRecorrencia.MENSAL, 31);
        LocalDate marco = CalendarioRecorrencia.proxima(fevereiro, FrequenciaRecorrencia.MENSAL, 31);

        assertEquals(LocalDate.of(2026, 2, 28), fevereiro);
        assertEquals(LocalDate.of(2026, 3, 31), marco, "a serie precisa voltar ao dia 31");
    }

    @Test
    void mensalAvancaUmMes() {
        assertEquals(LocalDate.of(2026, 10, 15),
                CalendarioRecorrencia.proxima(LocalDate.of(2026, 9, 15), FrequenciaRecorrencia.MENSAL, 15));
    }

    @Test
    void bimestralPulaUmMesETrimestralPulaDois() {
        LocalDate setembro = LocalDate.of(2026, 9, 10);
        assertEquals(LocalDate.of(2026, 11, 10),
                CalendarioRecorrencia.proxima(setembro, FrequenciaRecorrencia.BIMESTRAL, 10));
        assertEquals(LocalDate.of(2026, 12, 10),
                CalendarioRecorrencia.proxima(setembro, FrequenciaRecorrencia.TRIMESTRAL, 10));
    }

    @Test
    void semestralEAnualAvancamSeisEDozeMeses() {
        LocalDate setembro = LocalDate.of(2026, 9, 10);
        assertEquals(LocalDate.of(2027, 3, 10),
                CalendarioRecorrencia.proxima(setembro, FrequenciaRecorrencia.SEMESTRAL, 10));
        assertEquals(LocalDate.of(2027, 9, 10),
                CalendarioRecorrencia.proxima(setembro, FrequenciaRecorrencia.ANUAL, 10));
    }

    /** Sub-mensal anda em dias: o dia da semana precisa sobreviver a virada de mes. */
    @Test
    void semanalPreservaODiaDaSemana() {
        LocalDate terca = LocalDate.of(2026, 9, 1);
        assertEquals(DayOfWeek.TUESDAY, terca.getDayOfWeek());

        LocalDate ocorrencia = terca;
        for (int i = 0; i < 8; i++) {
            ocorrencia = CalendarioRecorrencia.proxima(ocorrencia, FrequenciaRecorrencia.SEMANAL, 1);
            assertEquals(DayOfWeek.TUESDAY, ocorrencia.getDayOfWeek());
        }
        assertEquals(LocalDate.of(2026, 10, 27), ocorrencia);
    }

    @Test
    void quinzenalAvancaCatorzeDias() {
        assertEquals(LocalDate.of(2026, 9, 15),
                CalendarioRecorrencia.proxima(LocalDate.of(2026, 9, 1), FrequenciaRecorrencia.QUINZENAL, 1));
    }

    /** Vencer hoje e vencer: quem cadastra hoje uma cobranca de hoje ve hoje. */
    @Test
    void primeiraOcorrenciaDeHojeEhHoje() {
        LocalDate hoje = LocalDate.of(2026, 9, 15);
        assertEquals(hoje, CalendarioRecorrencia.primeiraAPartirDe(
                hoje, FrequenciaRecorrencia.MENSAL, 15, null));
    }

    @Test
    void primeiraOcorrenciaJaPassadaVaiParaOProximoPasso() {
        LocalDate hoje = LocalDate.of(2026, 9, 20);
        assertEquals(LocalDate.of(2026, 10, 15), CalendarioRecorrencia.primeiraAPartirDe(
                hoje, FrequenciaRecorrencia.MENSAL, 15, null));
    }

    @Test
    void primeiraOcorrenciaSubMensalCaminhaAPartirDaAncora() {
        LocalDate hoje = LocalDate.of(2026, 9, 20);
        LocalDate ancora = LocalDate.of(2026, 9, 1);

        LocalDate primeira = CalendarioRecorrencia.primeiraAPartirDe(
                hoje, FrequenciaRecorrencia.SEMANAL, 1, ancora);

        assertEquals(LocalDate.of(2026, 9, 22), primeira);
        assertEquals(DayOfWeek.TUESDAY, primeira.getDayOfWeek(), "paridade da ancora preservada");
    }

    /** Ancora no futuro e a propria primeira cobranca: agendar nao antecipa. */
    @Test
    void ancoraNoFuturoEhAPropriaPrimeiraOcorrencia() {
        assertEquals(LocalDate.of(2026, 10, 5), CalendarioRecorrencia.primeiraAPartirDe(
                LocalDate.of(2026, 9, 20), FrequenciaRecorrencia.QUINZENAL, 5, LocalDate.of(2026, 10, 5)));
    }

    /** Regressao do comportamento anterior a V72: mensal cai zero ou uma vez no mes. */
    @Test
    void mensalCaiUmaVezPorMes() {
        List<LocalDate> outubro = CalendarioRecorrencia.ocorrenciasNoMes(
                LocalDate.of(2026, 9, 10), FrequenciaRecorrencia.MENSAL, 10, true, YearMonth.of(2026, 10));
        assertEquals(1, outubro.size());
        assertEquals(LocalDate.of(2026, 10, 10), outubro.get(0));
    }

    @Test
    void semanalCaiQuatroOuCincoVezesNoMes() {
        List<LocalDate> outubro = CalendarioRecorrencia.ocorrenciasNoMes(
                LocalDate.of(2026, 9, 1), FrequenciaRecorrencia.SEMANAL, 1, true, YearMonth.of(2026, 10));
        assertTrue(outubro.size() == 4 || outubro.size() == 5,
                "semanal precisa cair 4 ou 5 vezes; caiu " + outubro.size());
        assertTrue(outubro.stream().allMatch(d -> d.getDayOfWeek() == DayOfWeek.TUESDAY));
    }

    /** O erro que a projecao cometia antes da V72: contar a anual todo mes. */
    @Test
    void anualSoCaiNoMesDoAniversario() {
        LocalDate primeira = LocalDate.of(2026, 9, 10);

        assertEquals(1, CalendarioRecorrencia.ocorrenciasNoMes(
                primeira, FrequenciaRecorrencia.ANUAL, 10, true, YearMonth.of(2027, 9)).size());
        assertEquals(0, CalendarioRecorrencia.ocorrenciasNoMes(
                primeira, FrequenciaRecorrencia.ANUAL, 10, true, YearMonth.of(2027, 3)).size());
    }

    @Test
    void bimestralAlternaOsMeses() {
        LocalDate primeira = LocalDate.of(2026, 9, 10);
        assertEquals(0, CalendarioRecorrencia.ocorrenciasNoMes(
                primeira, FrequenciaRecorrencia.BIMESTRAL, 10, true, YearMonth.of(2026, 10)).size());
        assertEquals(1, CalendarioRecorrencia.ocorrenciasNoMes(
                primeira, FrequenciaRecorrencia.BIMESTRAL, 10, true, YearMonth.of(2026, 11)).size());
    }

    @Test
    void naoRecorrenteApareceSoNoMesDaPropriaData() {
        LocalDate unica = LocalDate.of(2026, 9, 10);
        assertEquals(1, CalendarioRecorrencia.ocorrenciasNoMes(
                unica, FrequenciaRecorrencia.MENSAL, 10, false, YearMonth.of(2026, 9)).size());
        assertEquals(0, CalendarioRecorrencia.ocorrenciasNoMes(
                unica, FrequenciaRecorrencia.MENSAL, 10, false, YearMonth.of(2026, 10)).size());
    }

    @Test
    void mesAnteriorAPrimeiraNaoTemOcorrencia() {
        assertEquals(0, CalendarioRecorrencia.ocorrenciasNoMes(
                LocalDate.of(2026, 9, 10), FrequenciaRecorrencia.MENSAL, 10, true, YearMonth.of(2026, 8)).size());
    }

    /** Frequencia nula e linha anterior a V72: precisa se comportar como MENSAL. */
    @Test
    void frequenciaNulaSeComportaComoMensal() {
        assertEquals(LocalDate.of(2026, 10, 10),
                CalendarioRecorrencia.proxima(LocalDate.of(2026, 9, 10), null, 10));
    }
}
