package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.CategoriaAlerta;
import com.gestor.financeiro.dto.InsightsResponse;
import com.gestor.financeiro.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InsightsService {
    private final java.time.Clock clock;
    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final CarteiraRepository carteiraRepository;

    public InsightsResponse gerarInsights(Long usuarioId) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
        LocalDate fimMesAtual = hoje.withDayOfMonth(hoje.lengthOfMonth());
        LocalDate inicioMesAnterior = inicioMesAtual.minusMonths(1);
        LocalDate inicio3MesesAtras = inicioMesAtual.minusMonths(3);

        // Gastos mês atual
        BigDecimal gastoMesAtual = transacaoRepository.sumSaidasByUsuarioIdAndPeriodo(
            usuarioId, inicioMesAtual, hoje);
        if (gastoMesAtual == null) gastoMesAtual = BigDecimal.ZERO;

        // Média mensal (últimos 3 meses)
        BigDecimal gasto3Meses = transacaoRepository.sumSaidasByUsuarioIdAndPeriodo(
            usuarioId, inicio3MesesAtras, inicioMesAtual.minusDays(1));
        if (gasto3Meses == null) gasto3Meses = BigDecimal.ZERO;
        BigDecimal gastoMedioMensal = gasto3Meses.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);

        // Variação em relação à média (denominador é a média, nunca o mês atual — que pode ser zero)
        BigDecimal variacao = BigDecimal.ZERO;
        if (gastoMedioMensal.compareTo(BigDecimal.ZERO) > 0) {
            variacao = gastoMesAtual.subtract(gastoMedioMensal)
                .divide(gastoMedioMensal, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        }

        // Previsão de saldo fim do mês
        BigDecimal saldoAtual = carteiraRepository.sumSaldoByUsuarioId(usuarioId);
        if (saldoAtual == null) saldoAtual = BigDecimal.ZERO;
        BigDecimal gastoRestante = BigDecimal.ZERO;
        if (gastoMesAtual.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diasPassados = new BigDecimal(hoje.getDayOfMonth());
            BigDecimal diasRestantes = new BigDecimal(fimMesAtual.lengthOfMonth() - hoje.getDayOfMonth());
            BigDecimal gastoDiario = gastoMesAtual.divide(diasPassados, 4, RoundingMode.HALF_UP);
            gastoRestante = gastoDiario.multiply(diasRestantes).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal previsaoSaldo = saldoAtual.subtract(gastoRestante);

        // Alertas por categoria
        List<CategoriaAlerta> alertas = gerarAlertasCategoria(usuarioId, inicioMesAtual, hoje, inicioMesAnterior);

        // Recomendações
        List<String> recomendacoes = gerarRecomendacoes(variacao, alertas, previsaoSaldo);

        // Resumo
        String resumo = gerarResumo(gastoMesAtual, gastoMedioMensal, variacao, previsaoSaldo);

        return InsightsResponse.builder()
            .gastoMesAtual(gastoMesAtual)
            .gastoMedioMensal(gastoMedioMensal)
            .variacaoPercentual(variacao)
            .previsaoSaldoFinal(previsaoSaldo)
            .categoriasAlerta(alertas)
            .recomendacoes(recomendacoes)
            .resumo(resumo)
            .build();
    }

    private List<CategoriaAlerta> gerarAlertasCategoria(Long usuarioId, LocalDate inicio, LocalDate fim, LocalDate inicioAnterior) {
        List<Object[]> gastosAtuais = transacaoRepository.sumSaidasByCategoria(usuarioId, inicio, fim);
        List<Object[]> gastosAnteriores = transacaoRepository.sumSaidasByCategoria(
            usuarioId, inicioAnterior, inicio.minusDays(1));

        // Colunas de sumSaidasByCategoria: [0]=categoria.id, [1]=categoria.nome, [2]=soma
        Map<Long, BigDecimal> mapAnterior = new HashMap<>();
        for (Object[] row : gastosAnteriores) {
            mapAnterior.put(asLong(row[0]), asBigDecimal(row[2]));
        }

        List<CategoriaAlerta> alertas = new ArrayList<>();
        for (Object[] row : gastosAtuais) {
            Long catId = asLong(row[0]);
            String catNome = String.valueOf(row[1]);
            BigDecimal gastoAtual = asBigDecimal(row[2]);
            BigDecimal gastoAnterior = mapAnterior.getOrDefault(catId, BigDecimal.ZERO);

            BigDecimal var = BigDecimal.ZERO;
            if (gastoAnterior.compareTo(BigDecimal.ZERO) > 0) {
                var = gastoAtual.subtract(gastoAnterior)
                    .divide(gastoAnterior, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            }

            boolean acima = var.compareTo(new BigDecimal("20")) > 0;
            if (acima || gastoAtual.compareTo(new BigDecimal("500")) > 0) {
                alertas.add(CategoriaAlerta.builder()
                    .categoriaNome(catNome)
                    .gastoAtual(gastoAtual)
                    .gastoMedio(gastoAnterior)
                    .variacaoPercentual(var)
                    .acimaMedia(acima)
                    .build());
            }
        }
        return alertas.stream()
            .sorted((a, b) -> b.getGastoAtual().compareTo(a.getGastoAtual()))
            .limit(5)
            .collect(Collectors.toList());
    }

    private static Long asLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimalValue) {
            return bigDecimalValue;
        }
        if (value instanceof Number numberValue) {
            return new BigDecimal(numberValue.toString());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private List<String> gerarRecomendacoes(BigDecimal variacao, List<CategoriaAlerta> alertas, BigDecimal previsao) {
        List<String> recs = new ArrayList<>();

        if (variacao.compareTo(new BigDecimal("20")) > 0) {
            recs.add("Seus gastos estão " + variacao.setScale(1, RoundingMode.HALF_UP) + "% acima da média. Tente reduzir despesas não essenciais.");
        } else if (variacao.compareTo(new BigDecimal("-10")) < 0) {
            recs.add("Seus gastos estão abaixo da média. Considere investir o valor economizado.");
        }

        for (CategoriaAlerta a : alertas) {
            if (a.isAcimaMedia()) {
                recs.add("A categoria '" + a.getCategoriaNome() + "' está " + a.getVariacaoPercentual().setScale(0, RoundingMode.HALF_UP) + "% acima do normal. Revise esses gastos.");
            }
        }

        if (previsao.compareTo(BigDecimal.ZERO) < 0) {
            recs.add("Projeção de saldo negativo no fim do mês. Reduza gastos para evitar usar crédito rotativo.");
        }

        if (recs.isEmpty()) {
            recs.add("Suas finanças estão estáveis. Continue acompanhando seus gastos regularmente.");
        }

        return recs;
    }

    private String gerarResumo(BigDecimal gasto, BigDecimal media, BigDecimal var, BigDecimal previsao) {
        if (media.compareTo(BigDecimal.ZERO) == 0) {
            return "Ainda não há dados históricos suficientes para análise. Registre transações por pelo menos 2 meses.";
        }

        String dir = var.compareTo(BigDecimal.ZERO) >= 0 ? "acima" : "abaixo";
        String resumo = String.format("Este mês você gastou R$ %s, %s%% %s da sua média mensal de R$ %s.",
            gasto.setScale(0, RoundingMode.HALF_UP),
            var.abs().setScale(1, RoundingMode.HALF_UP),
            dir,
            media.setScale(0, RoundingMode.HALF_UP));

        if (previsao.compareTo(BigDecimal.ZERO) >= 0) {
            resumo += String.format(" Projeção de saldo no fim do mês: R$ %s.",
                previsao.setScale(0, RoundingMode.HALF_UP));
        } else {
            resumo += String.format(" Atenção: projeção de saldo negativo de R$ %s.",
                previsao.abs().setScale(0, RoundingMode.HALF_UP));
        }

        return resumo;
    }
}
