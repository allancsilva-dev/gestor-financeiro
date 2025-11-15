package com.gestor.financeiro.service;

import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.repository.MetaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode; // ✅ ADICIONAR ESTE IMPORT
import java.time.LocalDate;
import java.util.List;

@Service
public class MetaService {
    
    @Autowired
    private MetaRepository metaRepository;
    
    // Lista metas ativas do usuário
    public List<Meta> listarPorUsuario(Long usuarioId) {
        return metaRepository.findByUsuarioIdAndAtivaTrue(usuarioId);
    }
    
    // Cria nova meta
    public Meta criar(Meta meta) {
        // Valores padrão
        if (meta.getAtiva() == null) meta.setAtiva(true);
        if (meta.getValorReservado() == null) meta.setValorReservado(BigDecimal.ZERO);
        if (meta.getDataInicio() == null) meta.setDataInicio(LocalDate.now());
        
        return metaRepository.save(meta);
    }
    
    // Adiciona valor à meta (quando o usuário guarda dinheiro)
    public Meta adicionarValor(Long metaId, BigDecimal valor) {
        Meta meta = metaRepository.findById(metaId)
            .orElseThrow(() -> new RuntimeException("Meta não encontrada"));
        
        // Soma ao valor reservado
        meta.setValorReservado(meta.getValorReservado().add(valor));
        
        // Se alcançou a meta, marca data de conclusão
        if (meta.getValorReservado().compareTo(meta.getValorTotal()) >= 0) {
            meta.setDataConclusao(LocalDate.now());
            meta.setAtiva(false); // Meta concluída
        }
        
        return metaRepository.save(meta);
    }
    
    // Remove valor da meta (caso retire dinheiro)
    public Meta removerValor(Long metaId, BigDecimal valor) {
        Meta meta = metaRepository.findById(metaId)
            .orElseThrow(() -> new RuntimeException("Meta não encontrada"));
        
        meta.setValorReservado(meta.getValorReservado().subtract(valor));
        
        // Se estava concluída, reativa
        if (meta.getValorReservado().compareTo(meta.getValorTotal()) < 0) {
            meta.setDataConclusao(null);
            meta.setAtiva(true);
        }
        
        return metaRepository.save(meta);
    }
    
    // Atualiza meta
    public Meta atualizar(Long id, Meta metaAtualizada) {
        Meta meta = metaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Meta não encontrada"));
        
        meta.setNome(metaAtualizada.getNome());
        meta.setValorTotal(metaAtualizada.getValorTotal());
        meta.setValorMensal(metaAtualizada.getValorMensal());
        meta.setDataPrevista(metaAtualizada.getDataPrevista());
        meta.setCor(metaAtualizada.getCor());
        meta.setIcone(metaAtualizada.getIcone());
        meta.setDescricao(metaAtualizada.getDescricao());
        
        return metaRepository.save(meta);
    }
    
    // Desativa meta
    public void deletar(Long id) {
        Meta meta = metaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Meta não encontrada"));
        
        meta.setAtiva(false);
        metaRepository.save(meta);
    }
    
    // Busca por ID
    public Meta buscarPorId(Long id) {
        return metaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Meta não encontrada"));
    }
    
    // Calcula porcentagem de conclusão da meta
    public BigDecimal calcularProgresso(Long metaId) {
        Meta meta = buscarPorId(metaId);
        
        if (meta.getValorTotal().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // (valorReservado / valorTotal) * 100
        return meta.getValorReservado()
            .divide(meta.getValorTotal(), 4, RoundingMode.HALF_UP) // ✅ CORRIGIDO
            .multiply(BigDecimal.valueOf(100));
    }
}