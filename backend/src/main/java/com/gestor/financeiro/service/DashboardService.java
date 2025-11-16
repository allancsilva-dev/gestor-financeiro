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
        private CarteiraRepository carteiraRepository;
        
        @Autowired
        private ParcelaRepository parcelaRepository;
        
        public Map<String, Object> obterResumo(Long usuarioId) {
            Map<String, Object> resumo = new HashMap<>();
            
            // Período: mês atual
            LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
            LocalDate fimMes = LocalDate.now().withDayOfMonth(
                LocalDate.now().lengthOfMonth()
            );
            
            // Total de entradas do mês (todas as transações de ENTRADA)
            BigDecimal totalEntradas = calcularTotalPorTipo(
                usuarioId, TipoTransacao.ENTRADA, inicioMes, fimMes
            );
            
            // Total de saídas do mês (CORRIGIDO: considera valor da parcela)
            BigDecimal totalSaidas = calcularTotalSaidasComParcelas(
                usuarioId, inicioMes, fimMes
            );
            
            // Saldo do mês (fluxo de caixa)
            BigDecimal saldo = totalEntradas.subtract(totalSaidas);
            
            // Saldo total das carteiras
            BigDecimal saldoCarteiras = calcularSaldoCarteiras(usuarioId);
            
            // Monta o resumo
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
        
        // Calcula total por tipo de transação (TODAS, com ou sem conta)
        private BigDecimal calcularTotalPorTipo(Long usuarioId, TipoTransacao tipo, 
                                            LocalDate inicio, LocalDate fim) {
            return transacaoRepository
                .findByUsuarioIdAndDataBetween(usuarioId, inicio, fim)
                .stream()
                .filter(t -> t.getTipo() == tipo)
                .map(t -> t.getValorTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        
        // NOVO MÉTODO: Calcula total de saídas considerando valor da parcela
        private BigDecimal calcularTotalSaidasComParcelas(Long usuarioId, 
                                                        LocalDate inicio, 
                                                        LocalDate fim) {
            return transacaoRepository
                .findByUsuarioIdAndDataBetween(usuarioId, inicio, fim)
                .stream()
                .filter(t -> t.getTipo() == TipoTransacao.SAIDA)
                .map(t -> {
                    // Se é parcelado, considera apenas o valor da parcela
                    if (t.getParcelado() != null && t.getParcelado() && t.getValorParcela() != null) {
                        return t.getValorParcela();
                    }
                    // Se não é parcelado, considera o valor total
                    return t.getValorTotal();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        
        // Calcula saldo total das carteiras
        private BigDecimal calcularSaldoCarteiras(Long usuarioId) {
            return carteiraRepository.findByUsuarioId(usuarioId)
                .stream()
                .map(c -> c.getSaldo())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }