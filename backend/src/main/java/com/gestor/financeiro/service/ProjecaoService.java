package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.ProjecaoMensalDto;
import com.gestor.financeiro.dto.ProjecaoResponse;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.repository.*;
import com.gestor.financeiro.util.FaturaDatas;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjecaoService {
    private final java.time.Clock clock;
    private final CarteiraRepository carteiraRepository;
    private final ContaFixaRepository contaFixaRepository;
    private final ParcelaRepository parcelaRepository;
    private final FaturaCartaoRepository faturaCartaoRepository;

    public ProjecaoResponse projetar(Long usuarioId, int mesesProjecao) {
        BigDecimal saldoAtual = carteiraRepository.sumSaldoByUsuarioId(usuarioId);
        if (saldoAtual == null) saldoAtual = BigDecimal.ZERO;

        YearMonth ymAtual = YearMonth.now(clock);
        List<ProjecaoMensalDto> meses = new ArrayList<>();
        BigDecimal saldoAnterior = saldoAtual;

        LocalDate hoje = LocalDate.now(clock);

        for (int i = 0; i < mesesProjecao; i++) {
            YearMonth ym = ymAtual.plusMonths(i);
            LocalDate inicioMes = ym.atDay(1);
            LocalDate fimMes = ym.atEndOfMonth();

            BigDecimal totalEntradas = somarRecorrenciasNoMes(usuarioId, ym, TipoTransacao.ENTRADA);
            BigDecimal totalContasFixas = somarRecorrenciasNoMes(usuarioId, ym, TipoTransacao.SAIDA);
            BigDecimal totalParcelas = somarParcelasNoMes(usuarioId, inicioMes, fimMes);
            // Faturas ja materializadas + cobrancas de assinatura que ainda vao entrar
            // numa fatura que vence neste mes. As duas parcelas sao a mesma linha para o
            // usuario: dinheiro de cartao que sai do caixa neste mes.
            BigDecimal totalFaturas = somarFaturasEmAberto(usuarioId, inicioMes, fimMes)
                    .add(somarAssinaturasDeCartaoNoMes(usuarioId, ym));
            BigDecimal totalSaidas = totalContasFixas.add(totalParcelas).add(totalFaturas);
            BigDecimal saldoFinal = saldoAnterior.add(totalEntradas).subtract(totalSaidas);

            boolean realizado = i == 0
                    || fimMes.isBefore(hoje)
                    || fimMes.isEqual(hoje);

            String periodo = ym.getMonth().getDisplayName(
                    java.time.format.TextStyle.SHORT, new Locale("pt", "BR")) + " " + ym.getYear();

            meses.add(new ProjecaoMensalDto(
                    periodo,
                    ym.getMonthValue(),
                    ym.getYear(),
                    saldoAnterior,
                    totalContasFixas,
                    totalParcelas,
                    totalFaturas,
                    totalEntradas,
                    totalSaidas,
                    saldoFinal,
                    realizado
            ));

            saldoAnterior = saldoFinal;
        }

        return new ProjecaoResponse(saldoAtual, meses);
    }

    private BigDecimal somarRecorrenciasNoMes(Long usuarioId, YearMonth mes, TipoTransacao tipo) {
        return contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId).stream()
                // Assinatura de cartao (destino conta_id, V67) nao e saida de caixa: ela vira
                // FaturaLancamento e so sai do caixa no pagamento da fatura, ja somado por
                // somarFaturasEmAberto. Contar aqui duplicaria o mesmo dinheiro no mesmo mes.
                .filter(c -> c.getConta() == null)
                .filter(c -> (c.getTipo() == null ? TipoTransacao.SAIDA : c.getTipo()) == tipo)
                .filter(c -> c.getStatus() != StatusPagamento.PAGO && c.getStatus() != StatusPagamento.CANCELADO)
                .filter(c -> ocorreNoMes(c, mes))
                .map(ContaFixa::getValorPlanejado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean ocorreNoMes(ContaFixa conta, YearMonth mes) {
        return ocorrenciaNoMes(conta, mes) != null;
    }

    /**
     * Data da ocorrencia da recorrencia dentro do mes, ou null se nao ocorre. Mesma
     * regra de sempre — o ramo de caixa so precisa do sim/nao —, mas o ramo de cartao
     * precisa da data para descobrir em que fatura a cobranca cai.
     */
    private LocalDate ocorrenciaNoMes(ContaFixa conta, YearMonth mes) {
        if (conta.getDataProximoVencimento() == null) return null;
        YearMonth primeiro = YearMonth.from(conta.getDataProximoVencimento());
        if (Boolean.TRUE.equals(conta.getRecorrente())) {
            if (mes.isBefore(primeiro)) return null;
        } else if (!mes.equals(primeiro)) {
            return null;
        }
        int dia = conta.getDiaVencimento() != null
                ? conta.getDiaVencimento()
                : conta.getDataProximoVencimento().getDayOfMonth();
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    /**
     * Cobrancas futuras de assinatura de cartao, projetadas no mes em que a fatura que
     * as contem vence — que e quando o dinheiro sai do caixa (ADR-0010).
     *
     * Sem isto a assinatura sumiria da projecao: a fatura so e materializada quando a
     * cobranca acontece, entao nos meses adiante nao ha fatura nenhuma para somar.
     *
     * Ocorrencia anterior a dataProximoVencimento ja foi cobrada e portanto ja esta numa
     * fatura materializada, contada por somarFaturasEmAberto — pular aqui e o que impede
     * a dobra. A janela de 2 meses para tras cobre o empurrao maximo de fechamento
     * (+1 mes) e vencimento (+1 mes).
     */
    private BigDecimal somarAssinaturasDeCartaoNoMes(Long usuarioId, YearMonth mes) {
        BigDecimal total = BigDecimal.ZERO;
        for (ContaFixa conta : contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId)) {
            if (conta.getConta() == null) continue;
            if ((conta.getTipo() == null ? TipoTransacao.SAIDA : conta.getTipo()) != TipoTransacao.SAIDA) continue;
            if (conta.getStatus() == StatusPagamento.PAGO || conta.getStatus() == StatusPagamento.CANCELADO) continue;

            for (int mesesAtras = 2; mesesAtras >= 0; mesesAtras--) {
                LocalDate ocorrencia = ocorrenciaNoMes(conta, mes.minusMonths(mesesAtras));
                if (ocorrencia == null) continue;
                if (ocorrencia.isBefore(conta.getDataProximoVencimento())) continue;

                YearMonth competencia = FaturaDatas.competencia(conta.getConta(), ocorrencia);
                LocalDate vencimentoFatura = FaturaDatas.vencimento(conta.getConta(), competencia);
                if (YearMonth.from(vencimentoFatura).equals(mes)) {
                    total = total.add(conta.getValorPlanejado());
                }
            }
        }
        return total;
    }

    private BigDecimal somarParcelasNoMes(Long usuarioId, LocalDate inicio, LocalDate fim) {
        BigDecimal total = parcelaRepository.somarValorNoPeriodo(
                usuarioId, inicio, fim, StatusPagamento.PAGO, TipoTransacao.SAIDA);
        return total != null ? total : BigDecimal.ZERO;
    }

    private BigDecimal somarFaturasEmAberto(Long usuarioId, LocalDate inicio, LocalDate fim) {
        BigDecimal total = faturaCartaoRepository.somarSaldoRestanteNoPeriodo(
                usuarioId, FaturaStatus.PAGA, inicio, fim);
        return total != null ? total : BigDecimal.ZERO;
    }
}
