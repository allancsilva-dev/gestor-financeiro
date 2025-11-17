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
import java.util.stream.Collectors;

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
        
        BigDecimal totalEntradas = calcularTotalPorTipo(usuarioId, TipoTransacao.ENTRADA, inicioMes, fimMes);
        BigDecimal totalSaidas = calcularTotalSaidasComParcelas(usuarioId, inicioMes, fimMes);
        BigDecimal saldo = totalEntradas.subtract(totalSaidas);
        BigDecimal saldoCarteiras = calcularSaldoCarteiras(usuarioId);
        
        resumo.put("totalEntradas", totalEntradas);
        resumo.put("totalSaidas", totalSaidas);
        resumo.put("saldo", saldo);
        resumo.put("totalCategorias", categoriaRepository.findByUsuarioId(usuarioId).size());
        resumo.put("totalContas", contaRepository.findByUsuarioId(usuarioId).size());
        resumo.put("totalMetas", metaRepository.findByUsuarioIdAndAtivaTrue(usuarioId).size());
        resumo.put("totalContasFixas", contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId).size());
        resumo.put("saldoCarteiras", saldoCarteiras);
        
        return resumo;
    }
    
    public List<Map<String, Object>> obterGastosPorCategoria(Long usuarioId) {
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        
        // --- CORREÇÃO APLICADA ---
        var transacoes = transacaoRepository.findByUsuarioIdAndDataBetweenWithCategoria(usuarioId, inicioMes, fimMes) 
            .stream()
            .filter(t -> t.getTipo() == TipoTransacao.SAIDA)
            .collect(Collectors.toList());
        
        // --- LINHA DE DEBUG 1 ---
        System.out.println(">>> DEBUG: Total de transações de SAÍDA encontradas: " + transacoes.size());

        Map<String, BigDecimal> gastosPorCategoria = new HashMap<>();
        Map<String, String> coresCategorias = new HashMap<>();
        
        for (var transacao : transacoes) {
            if (transacao.getCategoria() != null) { 
                
                // --- LINHA DE DEBUG 2 ---
                System.out.println(">>> DEBUG: Processando Categoria: " + transacao.getCategoria().getNome());
                
                String nomeCategoria = transacao.getCategoria().getNome();
                BigDecimal valor = transacao.getParcelado() != null && transacao.getParcelado() 
                    ? transacao.getValorParcela() 
                    : transacao.getValorTotal();
                
                gastosPorCategoria.merge(nomeCategoria, valor, BigDecimal::add);
                coresCategorias.put(nomeCategoria, transacao.getCategoria().getCor());
            } else {
                
                // --- LINHA DE DEBUG 3 ---
                System.out.println(">>> DEBUG: ERRO! Categoria NULA na transação: " + transacao.getDescricao());
            }
        }
        
        // ... (resto do método) ...
        BigDecimal totalGastos = gastosPorCategoria.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : gastosPorCategoria.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("categoria", entry.getKey());
            item.put("valor", entry.getValue());
            item.put("cor", coresCategorias.get(entry.getKey()));
            
            if (totalGastos.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percentual = entry.getValue()
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
    
    // ... (outros métodos) ...

    public List<Map<String, Object>> obterEvolucaoMensal(Long usuarioId) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        
        for (int i = 5; i >= 0; i--) {
            LocalDate data = LocalDate.now().minusMonths(i);
            LocalDate inicioMes = data.withDayOfMonth(1);
            LocalDate fimMes = data.withDayOfMonth(data.lengthOfMonth());
            
            BigDecimal entradas = calcularTotalPorTipo(usuarioId, TipoTransacao.ENTRADA, inicioMes, fimMes);
            BigDecimal saidas = calcularTotalSaidasComParcelas(usuarioId, inicioMes, fimMes);
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
        anterior.put("entradas", calcularTotalPorTipo(usuarioId, TipoTransacao.ENTRADA, inicioMesAnterior, fimMesAnterior));
        anterior.put("saidas", calcularTotalSaidasComParcelas(usuarioId, inicioMesAnterior, fimMesAnterior));
        resultado.add(anterior);
        
        LocalDate inicioMesAtual = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMesAtual = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        
        Map<String, Object> atual = new HashMap<>();
        atual.put("periodo", "Mês Atual");
        atual.put("entradas", calcularTotalPorTipo(usuarioId, TipoTransacao.ENTRADA, inicioMesAtual, fimMesAtual));
        atual.put("saidas", calcularTotalSaidasComParcelas(usuarioId, inicioMesAtual, fimMesAtual));
        resultado.add(atual);
        
        return resultado;
    }
    
    private BigDecimal calcularTotalPorTipo(Long usuarioId, TipoTransacao tipo, LocalDate inicio, LocalDate fim) {
        return transacaoRepository
            .findByUsuarioIdAndDataBetween(usuarioId, inicio, fim) 
            .stream()
            .filter(t -> t.getTipo() == tipo)
            .map(t -> t.getValorTotal())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calcularTotalSaidasComParcelas(Long usuarioId, LocalDate inicio, LocalDate fim) {
        return transacaoRepository
            .findByUsuarioIdAndDataBetween(usuarioId, inicio, fim) 
            .stream()
            .filter(t -> t.getTipo() == TipoTransacao.SAIDA)
            .map(t -> {
                if (t.getParcelado() != null && t.getParcelado() && t.getValorParcela() != null) {
                    return t.getValorParcela();
                }
                return t.getValorTotal();
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calcularSaldoCarteiras(Long usuarioId) {
        return carteiraRepository.findByUsuarioId(usuarioId)
            .stream()
            .map(c -> c.getSaldo())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}