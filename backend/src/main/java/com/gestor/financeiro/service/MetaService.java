package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.MovimentoMeta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.StatusMeta;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.MovimentoMetaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode; // ✅ ADICIONAR ESTE IMPORT
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MetaService {
    private final java.time.Clock clock;
    private final MetaRepository metaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimentoMetaRepository movimentoMetaRepository;
    private final LedgerService ledgerService;
    
    // Lista metas do usuário por status canônico (ausência de filtro = ATIVA, compat com clientes antigos)
    public Page<Meta> listarPorUsuario(Long usuarioId, StatusMeta status, Pageable pageable) {
        return metaRepository.findByUsuarioIdAndStatus(usuarioId, status == null ? StatusMeta.ATIVA : status, pageable);
    }
    
    // Cria nova meta
    @Transactional
    public Meta criar(Meta meta, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        meta.setUsuario(usuario);

        // Valores padrão — criação sempre nasce ATIVA (ADR-0004)
        meta.setStatus(StatusMeta.ATIVA);
        meta.setAtiva(true);
        if (meta.getValorReservado() == null) meta.setValorReservado(BigDecimal.ZERO);
        if (meta.getDataInicio() == null) meta.setDataInicio(LocalDate.now(clock));

        return metaRepository.save(meta);
    }
    
    // Adiciona valor à meta (quando o usuário guarda dinheiro)
    @Transactional
    public Meta adicionarValor(Long metaId, BigDecimal valor, Long carteiraId, Long usuarioId) {
        Meta meta = buscarPorIdDoUsuario(metaId, usuarioId);
        exigirNaoArquivada(meta);

        // Reserva sem origem gera dupla contagem: o valor "guardado" continuaria disponível na carteira
        if (carteiraId == null) {
            throw new BusinessException("Informe a carteira de origem da reserva");
        }

        registrarMovimentoCarteira(meta, carteiraId, usuarioId, valor,
                TipoMovimentoCarteira.RESERVA_META,
                RegistrarMovimentoCommand.Direcao.SAIDA,
                "Reserva para meta: " + meta.getNome());

        BigDecimal valorAnterior = meta.getValorReservado();
        meta.setValorReservado(valorAnterior.add(valor));

        meta.recalcularEstado(LocalDate.now(clock));

        Meta salva = metaRepository.save(meta);

        registroMovimento(salva, usuarioId, "ADICAO", valor, valorAnterior,
                "Adição de valor na meta: " + salva.getNome());

        return salva;
    }

    @Transactional
    public Meta removerValor(Long metaId, BigDecimal valor, Long carteiraId, Long usuarioId) {
        Meta meta = buscarPorIdDoUsuario(metaId, usuarioId);
        exigirNaoArquivada(meta);

        if (carteiraId == null) {
            throw new BusinessException("Informe a carteira de destino do resgate");
        }

        // Resgatar mais do que foi reservado criaria dinheiro do nada na carteira
        if (valor.compareTo(meta.getValorReservado()) > 0) {
            throw new BusinessException("Valor maior que o reservado na meta");
        }

        registrarMovimentoCarteira(meta, carteiraId, usuarioId, valor,
                TipoMovimentoCarteira.RESGATE_META,
                RegistrarMovimentoCommand.Direcao.ENTRADA,
                "Resgate da meta: " + meta.getNome());

        BigDecimal valorAnterior = meta.getValorReservado();
        meta.setValorReservado(valorAnterior.subtract(valor));

        meta.recalcularEstado(LocalDate.now(clock));

        Meta salva = metaRepository.save(meta);

        registroMovimento(salva, usuarioId, "REMOCAO", valor, valorAnterior,
                "Remoção de valor da meta: " + salva.getNome());

        return salva;
    }
    
    // Atualiza meta
    @Transactional
    public Meta atualizar(Long id, Meta metaAtualizada, Long usuarioId) {
        Meta meta = buscarPorIdDoUsuario(id, usuarioId);
        exigirNaoArquivada(meta);

        meta.setNome(metaAtualizada.getNome());
        meta.setValorTotal(metaAtualizada.getValorTotal());
        meta.setValorMensal(metaAtualizada.getValorMensal());
        meta.setDataPrevista(metaAtualizada.getDataPrevista());
        meta.setCor(metaAtualizada.getCor());
        meta.setIcone(metaAtualizada.getIcone());
        meta.setDescricao(metaAtualizada.getDescricao());
        meta.recalcularEstado(LocalDate.now(clock));
        
        return metaRepository.save(meta);
    }
    
    /**
     * Arquiva a meta (exclusão lógica, ADR-0004). Meta com valor reservado não pode ser
     * excluída: o dinheiro sumiria da visão do usuário — resgate primeiro. Repetir o
     * arquivamento não duplica efeitos.
     */
    @Transactional
    public void deletar(Long id, Long usuarioId) {
        Meta meta = buscarPorIdDoUsuario(id, usuarioId);

        if (meta.getValorReservado() != null && meta.getValorReservado().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(
                "Esta meta ainda tem R$ " + meta.getValorReservado().setScale(2, RoundingMode.HALF_UP)
                + " reservados. Resgate o valor para uma carteira antes de excluir.");
        }

        meta.arquivar();
        metaRepository.save(meta);
    }

    private void exigirNaoArquivada(Meta meta) {
        if (meta.getStatus() == StatusMeta.ARQUIVADA) {
            throw new BusinessException("Meta arquivada não aceita edição ou movimentação");
        }
    }
    
    // Busca por ID
    public Meta buscarPorId(Long id) {
        return metaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada"));
    }

    // Valida ownership para evitar IDOR em endpoints por ID.
    public Meta buscarPorIdDoUsuario(Long id, Long usuarioId) {
        Meta meta = metaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada"));

        if (!meta.getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedAccessException("Acesso negado a esta meta");
        }

        return meta;
    }
    
    // Calcula porcentagem de conclusão da meta
    public BigDecimal calcularProgresso(Long metaId, Long usuarioId) {
        Meta meta = buscarPorIdDoUsuario(metaId, usuarioId);

        if (meta.getValorTotal().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return meta.getValorReservado()
            .divide(meta.getValorTotal(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    // Debita/credita a carteira via ledger (valida ownership e saldo lá dentro)
    private void registrarMovimentoCarteira(Meta meta, Long carteiraId, Long usuarioId,
                                            BigDecimal valor, TipoMovimentoCarteira tipo,
                                            RegistrarMovimentoCommand.Direcao direcao, String descricao) {
        ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
                usuarioId,
                carteiraId,
                tipo,
                valor,
                direcao,
                OrigemMovimentoCarteira.META,
                "META",
                meta.getId(),
                descricao,
                null,
                LocalDateTime.now(clock),
                false
        ));
    }

    private void registroMovimento(Meta meta, Long usuarioId, String tipo,
                                   BigDecimal valor, BigDecimal valorAnterior, String descricao) {
        BigDecimal valorAssinado = "ADICAO".equals(tipo) ? valor : valor.negate();

        MovimentoMeta movimento = new MovimentoMeta();
        movimento.setUsuario(meta.getUsuario());
        movimento.setMeta(meta);
        movimento.setTipo(tipo);
        movimento.setValor(valor);
        movimento.setValorAssinado(valorAssinado);
        movimento.setValorResultante(meta.getValorReservado());
        movimento.setDescricao(descricao);

        movimentoMetaRepository.save(movimento);
    }
}
