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
    
    @Autowired
    private CarteiraRepository carteiraRepository; // ✅ LINHA 1: ADICIONAR
    
    public Map<String, Object> obterResumo(Long usuarioId) {
        Map<String, Object> resumo = new HashMap<>();
        
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = LocalDate.now().withDayOfMonth(
            LocalDate.now().lengthOfMonth()
        );
        
        BigDecimal totalEntradas = calcularTotalPorTipo(
            usuarioId, TipoTransacao.ENTRADA, inicioMes, fimMes
        );
        
        BigDecimal totalSaidas = calcularTotalPorTipo(
            usuarioId, TipoTransacao.SAIDA, inicioMes, fimMes
        );
        
        BigDecimal saldo = totalEntradas.subtract(totalSaidas);
        
        BigDecimal saldoCarteiras = calcularSaldoCarteiras(usuarioId); // ✅ LINHA 2: ADICIONAR
        
        resumo.put("totalEntradas", totalEntradas);
        resumo.put("totalSaidas", totalSaidas);
        resumo.put("saldo", saldo);
        resumo.put("totalCategorias", categoriaRepository.findByUsuarioId(usuarioId).size());
        resumo.put("totalContas", contaRepository.findByUsuarioId(usuarioId).size());
        resumo.put("totalMetas", metaRepository.findByUsuarioIdAndAtivaTrue(usuarioId).size());
        resumo.put("totalContasFixas", contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId).size());
        resumo.put("saldoCarteiras", saldoCarteiras); // ✅ LINHA 3: ADICIONAR
        
        return resumo;
    }
    
    private BigDecimal calcularTotalPorTipo(Long usuarioId, TipoTransacao tipo, 
                                           LocalDate inicio, LocalDate fim) {
        return transacaoRepository
            .findByUsuarioIdAndDataBetween(usuarioId, inicio, fim)
            .stream()
            .filter(t -> t.getTipo() == tipo)
            .map(t -> t.getValorTotal())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    // ✅ MÉTODO NOVO: ADICIONAR
    private BigDecimal calcularSaldoCarteiras(Long usuarioId) {
        return carteiraRepository.findByUsuarioId(usuarioId)
            .stream()
            .map(c -> c.getSaldo())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}