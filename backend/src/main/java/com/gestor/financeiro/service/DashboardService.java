package com.gestor.financeiro.service;

import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

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
    
    // Retorna resumo completo do dashboard
    public Map<String, Object> obterResumo(Long usuarioId) {
        Map<String, Object> resumo = new HashMap<>();
        
        // Período: mês atual
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = LocalDate.now().withDayOfMonth(
            LocalDate.now().lengthOfMonth()
        );
        
        // Total de entradas do mês
        BigDecimal totalEntradas = calcularTotalPorTipo(
            usuarioId, TipoTransacao.ENTRADA, inicioMes, fimMes
        );
        
        // Total de saídas do mês
        BigDecimal totalSaidas = calcularTotalPorTipo(
            usuarioId, TipoTransacao.SAIDA, inicioMes, fimMes
        );
        
        // Saldo do mês
        BigDecimal saldo = totalEntradas.subtract(totalSaidas);
        
        // Monta o resumo
        resumo.put("totalEntradas", totalEntradas);
        resumo.put("totalSaidas", totalSaidas);
        resumo.put("saldo", saldo);
        resumo.put("totalCategorias", categoriaRepository.findByUsuarioId(usuarioId).size());
        resumo.put("totalContas", contaRepository.findByUsuarioId(usuarioId).size());
        resumo.put("totalMetas", metaRepository.findByUsuarioIdAndAtivaTrue(usuarioId).size());
        resumo.put("totalContasFixas", contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId).size());
        
        return resumo;
    }
    
    // Calcula total por tipo de transação
    private BigDecimal calcularTotalPorTipo(Long usuarioId, TipoTransacao tipo, 
                                           LocalDate inicio, LocalDate fim) {
        return transacaoRepository
            .findByUsuarioIdAndDataBetween(usuarioId, inicio, fim)
            .stream()
            .filter(t -> t.getTipo() == tipo)
            .map(t -> t.getValorTotal())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}