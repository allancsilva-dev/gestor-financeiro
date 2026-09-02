package com.gestor.financeiro.util;

import com.gestor.financeiro.model.enums.FrequenciaRecorrencia;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Calendario de uma recorrencia (V72). Funcoes puras, no mesmo espirito de
 * {@link FaturaDatas}: a data de referencia entra sempre por parametro, nunca de
 * {@code LocalDate.now()} — quem tem o Clock injetado e o service (ADR-0003).
 *
 * <p>Esta classe fica fora de {@code service/}, que e o diretorio varrido pelo
 * BusinessClockGuardTest. A disciplina aqui e manual: <b>nenhuma funcao deste arquivo
 * pode ler o relogio.</b></p>
 */
public final class CalendarioRecorrencia {

    private CalendarioRecorrencia() {
    }

    /**
     * Proxima ocorrencia depois de {@code atual}.
     *
     * <p>Em multiplo de mes o dia e reclampado a partir do {@code diaVencimento}
     * persistido, e nao do dia de {@code atual}: e o que faz jan/31 virar fev/28 e
     * voltar para mar/31, em vez de derivar para mar/28. Comportamento preservado do
     * antigo {@code ContaFixaService.avancarOcorrencia}.</p>
     */
    public static LocalDate proxima(LocalDate atual, FrequenciaRecorrencia frequencia, int diaVencimento) {
        FrequenciaRecorrencia f = frequencia == null ? FrequenciaRecorrencia.MENSAL : frequencia;
        if (f.isSubMensal()) {
            return atual.plusDays(f.getPasso());
        }
        LocalDate proxima = atual.plusMonths(f.getPasso());
        return proxima.withDayOfMonth(Math.min(diaVencimento, proxima.lengthOfMonth()));
    }

    /**
     * Primeira ocorrencia que nao esta no passado, a partir de {@code hoje}.
     *
     * <p>Multiplo de mes: mesma regra de sempre — a ocorrencia deste mes, ou a seguinte
     * se ela ja passou. Note que "ja passou" e estritamente antes de hoje: uma
     * recorrencia que vence hoje vence hoje, e o cadastro executa na hora.</p>
     *
     * <p>Sub-mensal: caminha em passos inteiros a partir da ancora. Ancora no futuro e
     * a propria primeira ocorrencia — quem agenda para semana que vem nao e cobrado
     * hoje.</p>
     */
    public static LocalDate primeiraAPartirDe(LocalDate hoje, FrequenciaRecorrencia frequencia,
                                              int diaVencimento, LocalDate ancora) {
        FrequenciaRecorrencia f = frequencia == null ? FrequenciaRecorrencia.MENSAL : frequencia;

        if (f.isSubMensal()) {
            LocalDate base = ancora == null ? hoje : ancora;
            LocalDate ocorrencia = base;
            while (ocorrencia.isBefore(hoje)) {
                ocorrencia = ocorrencia.plusDays(f.getPasso());
            }
            return ocorrencia;
        }

        YearMonth mes = YearMonth.from(hoje);
        LocalDate ocorrencia = mes.atDay(Math.min(diaVencimento, mes.lengthOfMonth()));
        if (ocorrencia.isBefore(hoje)) {
            YearMonth seguinte = mes.plusMonths(f.getPasso());
            ocorrencia = seguinte.atDay(Math.min(diaVencimento, seguinte.lengthOfMonth()));
        }
        return ocorrencia;
    }

    /**
     * Ocorrencias que caem dentro de {@code mes}, a partir de {@code primeira}.
     *
     * <p>Existe para a projecao de caixa: antes da V72 ela assumia "todo mes a partir do
     * primeiro", o que contaria uma assinatura anual doze vezes por ano e uma semanal
     * uma vez so. MENSAL devolve 0 ou 1 ocorrencia, exatamente como antes.</p>
     *
     * <p>{@code recorrente == false} e cobranca de um mes so: aparece apenas no mes da
     * propria data.</p>
     */
    public static List<LocalDate> ocorrenciasNoMes(LocalDate primeira, FrequenciaRecorrencia frequencia,
                                                   int diaVencimento, boolean recorrente, YearMonth mes) {
        List<LocalDate> ocorrencias = new ArrayList<>();
        if (primeira == null || mes.isBefore(YearMonth.from(primeira))) {
            return ocorrencias;
        }
        if (!recorrente) {
            if (YearMonth.from(primeira).equals(mes)) ocorrencias.add(primeira);
            return ocorrencias;
        }

        FrequenciaRecorrencia f = frequencia == null ? FrequenciaRecorrencia.MENSAL : frequencia;
        LocalDate ocorrencia = primeira;
        // Teto defensivo: sem ele um passo mal formado viraria laco infinito.
        int limite = 400;
        while (YearMonth.from(ocorrencia).isBefore(mes) && limite-- > 0) {
            ocorrencia = proxima(ocorrencia, f, diaVencimento);
        }
        while (YearMonth.from(ocorrencia).equals(mes) && limite-- > 0) {
            ocorrencias.add(ocorrencia);
            ocorrencia = proxima(ocorrencia, f, diaVencimento);
        }
        return ocorrencias;
    }
}
