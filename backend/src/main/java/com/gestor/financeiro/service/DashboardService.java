package com.gestor.financeiro.service;

import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

@Service
public class DashboardService {
    
    @Autowired
    private TransacaoRepository transacaoRepository;
    
    @Autowired
    private CategoriaRepository categoriaRepository;
    
    @Autowired
    private ContaRepository contaRepository;
    
    @Autowired
    private MetaRepository metaRepository;
    
    @Autowired
    private ContaFixaRepository contaFixaRepository;
    
    @Autowired
    private CarteiraRepository carteiraRepository;
    
    public Map<String, Object> obterResumo(Long usuarioId) {
        Map<String, Object> resumo = new HashMap<>();
        
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        
        BigDecimal totalEntradas = transacaoRepository.sumValorTotalByUsuarioIdAndTipoAndDataBetween(
                usuarioId, TipoTransacao.ENTRADA, inicioMes, fimMes);
        BigDecimal totalSaidas = transacaoRepository.sumValorEfetivoByUsuarioIdAndTipoAndDataBetween(
                usuarioId, TipoTransacao.SAIDA, inicioMes, fimMes);
        BigDecimal saldo = totalEntradas.subtract(totalSaidas);
        BigDecimal saldoCarteiras = carteiraRepository.sumSaldoByUsuarioId(usuarioId);
        
        resumo.put("totalEntradas", totalEntradas);
        resumo.put("totalSaidas", totalSaidas);
        resumo.put("saldo", saldo);
        resumo.put("totalCategorias", categoriaRepository.countByUsuarioIdAndAtivoTrue(usuarioId));
        resumo.put("totalContas", contaRepository.countByUsuarioId(usuarioId));
        resumo.put("totalMetas", metaRepository.countByUsuarioIdAndAtivaTrue(usuarioId));
        resumo.put("totalContasFixas", contaFixaRepository.countByUsuarioIdAndAtivoTrue(usuarioId));
        resumo.put("saldoCarteiras", saldoCarteiras);
        
        return resumo;
    }
    
    public List<Map<String, Object>> obterGastosPorCategoria(Long usuarioId) {
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        
        List<Object[]> rows = transacaoRepository.sumValorEfetivoAgrupadoPorCategoria(
                usuarioId, TipoTransacao.SAIDA, inicioMes, fimMes);

        BigDecimal totalGastos = rows.stream()
                .map(r -> (BigDecimal) r[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new HashMap<>();
            String nomeCategoria = (String) row[0];
            BigDecimal valor = (BigDecimal) row[1];
            String cor = (String) row[2];
            item.put("categoria", nomeCategoria);
            item.put("valor", valor);
            item.put("cor", cor);
            
            if (totalGastos.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percentual = valor
                    .divide(totalGastos, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(1, RoundingMode.HALF_UP);
                item.put("percentual", percentual);
            } else {
                item.put("percentual", 0);
            }
            
            resultado.add(item);
        }
        
        resultado.sort((a, b) -> 
            ((BigDecimal) b.get("valor")).compareTo((BigDecimal) a.get("valor"))
        );
        
        return resultado;
    }
    
    public List<Map<String, Object>> obterEvolucaoMensal(Long usuarioId) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        
        for (int i = 5; i >= 0; i--) {
            LocalDate data = LocalDate.now().minusMonths(i);
            LocalDate inicioMes = data.withDayOfMonth(1);
            LocalDate fimMes = data.withDayOfMonth(data.lengthOfMonth());
            
            BigDecimal entradas = transacaoRepository.sumValorTotalByUsuarioIdAndTipoAndDataBetween(
                    usuarioId, TipoTransacao.ENTRADA, inicioMes, fimMes);
            BigDecimal saidas = transacaoRepository.sumValorEfetivoByUsuarioIdAndTipoAndDataBetween(
                    usuarioId, TipoTransacao.SAIDA, inicioMes, fimMes);
            BigDecimal saldo = entradas.subtract(saidas);
            
            Map<String, Object> mes = new HashMap<>();
            mes.put("mes", data.getMonth().getDisplayName(TextStyle.SHORT, new Locale("pt", "BR")));
            mes.put("entradas", entradas);
            mes.put("saidas", saidas);
            mes.put("saldo", saldo);
            
            resultado.add(mes);
        }
        
        return resultado;
    }
    
    public List<Map<String, Object>> obterComparacaoMensal(Long usuarioId) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        
        LocalDate mesAnterior = LocalDate.now().minusMonths(1);
        LocalDate inicioMesAnterior = mesAnterior.withDayOfMonth(1);
        LocalDate fimMesAnterior = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth());
        
        Map<String, Object> anterior = new HashMap<>();
        anterior.put("periodo", "Mês Anterior");
        anterior.put("entradas", transacaoRepository.sumValorTotalByUsuarioIdAndTipoAndDataBetween(
                usuarioId, TipoTransacao.ENTRADA, inicioMesAnterior, fimMesAnterior));
        anterior.put("saidas", transacaoRepository.sumValorEfetivoByUsuarioIdAndTipoAndDataBetween(
                usuarioId, TipoTransacao.SAIDA, inicioMesAnterior, fimMesAnterior));
        resultado.add(anterior);
        
        LocalDate inicioMesAtual = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMesAtual = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        
        Map<String, Object> atual = new HashMap<>();
        atual.put("periodo", "Mês Atual");
        atual.put("entradas", transacaoRepository.sumValorTotalByUsuarioIdAndTipoAndDataBetween(
                usuarioId, TipoTransacao.ENTRADA, inicioMesAtual, fimMesAtual));
        atual.put("saidas", transacaoRepository.sumValorEfetivoByUsuarioIdAndTipoAndDataBetween(
                usuarioId, TipoTransacao.SAIDA, inicioMesAtual, fimMesAtual));
        resultado.add(atual);
        
        return resultado;
    }
}