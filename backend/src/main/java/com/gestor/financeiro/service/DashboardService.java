package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.dto.DashboardDtos;
import com.gestor.financeiro.repository.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final java.time.Clock clock;
    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaRepository contaRepository;
    private final MetaRepository metaRepository;
    private final ContaFixaRepository contaFixaRepository;
    private final CarteiraRepository carteiraRepository;
    
    public DashboardDtos.Resumo obterResumo(Long usuarioId) {
        LocalDate inicioMes = LocalDate.now(clock).withDayOfMonth(1);
        LocalDate fimMes = LocalDate.now(clock).withDayOfMonth(LocalDate.now(clock).lengthOfMonth());
        
        BigDecimal totalEntradas = transacaoRepository.sumValorTotalByUsuarioIdAndTipoAndDataBetween(
                usuarioId, TipoTransacao.ENTRADA, inicioMes, fimMes);
        BigDecimal totalSaidas = transacaoRepository.sumValorEfetivoByUsuarioIdAndTipoAndDataBetween(
                usuarioId, TipoTransacao.SAIDA, inicioMes, fimMes);
        BigDecimal saldo = totalEntradas.subtract(totalSaidas);
        BigDecimal saldoCarteiras = carteiraRepository.sumSaldoByUsuarioId(usuarioId);
        
        return new DashboardDtos.Resumo(totalEntradas, totalSaidas, saldo,
                categoriaRepository.countByUsuarioIdAndAtivoTrue(usuarioId), contaRepository.countByUsuarioId(usuarioId),
                metaRepository.countByUsuarioIdAndAtivaTrue(usuarioId), contaFixaRepository.countByUsuarioIdAndAtivoTrue(usuarioId),
                saldoCarteiras);
    }
    
    public List<DashboardDtos.Categoria> obterGastosPorCategoria(Long usuarioId) {
        LocalDate inicioMes = LocalDate.now(clock).withDayOfMonth(1);
        LocalDate fimMes = LocalDate.now(clock).withDayOfMonth(LocalDate.now(clock).lengthOfMonth());
        
        List<Object[]> rows = transacaoRepository.sumValorEfetivoAgrupadoPorCategoria(
                usuarioId, TipoTransacao.SAIDA, inicioMes, fimMes);

        BigDecimal totalGastos = rows.stream()
                .map(r -> (BigDecimal) r[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        List<DashboardDtos.Categoria> resultado = new ArrayList<>();
        for (Object[] row : rows) {
            String nomeCategoria = (String) row[0];
            BigDecimal valor = (BigDecimal) row[1];
            String cor = (String) row[2];
            BigDecimal percentual;
            if (totalGastos.compareTo(BigDecimal.ZERO) > 0) {
                percentual = valor
                    .divide(totalGastos, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(1, RoundingMode.HALF_UP);
            } else {
                percentual = BigDecimal.ZERO;
            }
            resultado.add(new DashboardDtos.Categoria(nomeCategoria, valor, cor, percentual));
        }
        
        resultado.sort((a, b) -> b.valor().compareTo(a.valor()));
        
        return resultado;
    }
    
    public List<DashboardDtos.Evolucao> obterEvolucaoMensal(Long usuarioId) {
        List<DashboardDtos.Evolucao> resultado = new ArrayList<>();
        
        for (int i = 5; i >= 0; i--) {
            LocalDate data = LocalDate.now(clock).minusMonths(i);
            LocalDate inicioMes = data.withDayOfMonth(1);
            LocalDate fimMes = data.withDayOfMonth(data.lengthOfMonth());
            
            BigDecimal entradas = transacaoRepository.sumValorTotalByUsuarioIdAndTipoAndDataBetween(
                    usuarioId, TipoTransacao.ENTRADA, inicioMes, fimMes);
            BigDecimal saidas = transacaoRepository.sumValorEfetivoByUsuarioIdAndTipoAndDataBetween(
                    usuarioId, TipoTransacao.SAIDA, inicioMes, fimMes);
            BigDecimal saldo = entradas.subtract(saidas);
            
            resultado.add(new DashboardDtos.Evolucao(
                    data.getMonth().getDisplayName(TextStyle.SHORT, new Locale("pt", "BR")),
                    entradas, saidas, saldo));
        }
        
        return resultado;
    }
    
    public List<DashboardDtos.Comparacao> obterComparacaoMensal(Long usuarioId) {
        List<DashboardDtos.Comparacao> resultado = new ArrayList<>();
        
        LocalDate mesAnterior = LocalDate.now(clock).minusMonths(1);
        LocalDate inicioMesAnterior = mesAnterior.withDayOfMonth(1);
        LocalDate fimMesAnterior = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth());
        
        resultado.add(new DashboardDtos.Comparacao("Mês Anterior",
                transacaoRepository.sumValorTotalByUsuarioIdAndTipoAndDataBetween(
                        usuarioId, TipoTransacao.ENTRADA, inicioMesAnterior, fimMesAnterior),
                transacaoRepository.sumValorEfetivoByUsuarioIdAndTipoAndDataBetween(
                        usuarioId, TipoTransacao.SAIDA, inicioMesAnterior, fimMesAnterior)));
        
        LocalDate inicioMesAtual = LocalDate.now(clock).withDayOfMonth(1);
        LocalDate fimMesAtual = LocalDate.now(clock).withDayOfMonth(LocalDate.now(clock).lengthOfMonth());
        
        resultado.add(new DashboardDtos.Comparacao("Mês Atual",
                transacaoRepository.sumValorTotalByUsuarioIdAndTipoAndDataBetween(
                        usuarioId, TipoTransacao.ENTRADA, inicioMesAtual, fimMesAtual),
                transacaoRepository.sumValorEfetivoByUsuarioIdAndTipoAndDataBetween(
                        usuarioId, TipoTransacao.SAIDA, inicioMesAtual, fimMesAtual)));
        
        return resultado;
    }
}
