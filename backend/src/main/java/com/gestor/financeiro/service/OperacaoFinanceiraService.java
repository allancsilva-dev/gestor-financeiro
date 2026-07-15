package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.FinancialConflictException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.OperacaoFinanceira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.OrigemOperacaoFinanceira;
import com.gestor.financeiro.model.enums.PoliticaOperacao;
import com.gestor.financeiro.model.enums.StatusOperacaoFinanceira;
import com.gestor.financeiro.model.enums.TipoOperacaoFinanceira;
import com.gestor.financeiro.repository.OperacaoFinanceiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Nucleo do ledger operacional (ADR-0009): cria operacoes imutaveis com
 * idempotencia por (usuario, chave) e hash do request. Chave repetida com
 * payload igual retorna a operacao original; payload diferente retorna 409.
 * Correcao gera operacao de ESTORNO referenciando a original.
 */
@Service
public class OperacaoFinanceiraService {

    private final OperacaoFinanceiraRepository operacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public OperacaoFinanceiraService(OperacaoFinanceiraRepository operacaoRepository,
                                     UsuarioRepository usuarioRepository,
                                     Clock clock) {
        this.operacaoRepository = operacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @Transactional
    public OperacaoFinanceira criar(CriarOperacaoCommand command) {
        String requestHash = hashPayload(command.requestPayload());

        if (hasText(command.idempotencyKey())) {
            var existente = operacaoRepository
                    .findByUsuarioIdAndIdempotencyKey(command.usuarioId(), command.idempotencyKey());
            if (existente.isPresent()) {
                OperacaoFinanceira original = existente.get();
                if (requestHash != null && requestHash.equals(original.getRequestHash())) {
                    return original;
                }
                throw new FinancialConflictException(
                        "Chave de idempotência reutilizada com conteúdo diferente");
            }
        }

        return criarNova(command, requestHash);
    }

    /**
     * Estorna uma operacao CONFIRMADA: cria operacao ESTORNO referenciando a
     * original e marca a original como ESTORNADA. Nunca altera conteudo
     * financeiro da original (ADR-0009). Os lancamentos compensatorios sao
     * responsabilidade do fluxo de dominio que chamou o estorno.
     */
    @Transactional
    public OperacaoFinanceira estornar(Long usuarioId, Long operacaoId, String motivo, String idempotencyKey) {
        OperacaoFinanceira original = operacaoRepository.findById(operacaoId)
                .filter(op -> op.getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new ResourceNotFoundException("Operação não encontrada"));

        if (original.getStatus() == StatusOperacaoFinanceira.ESTORNADA) {
            throw new BusinessException("Operação já estornada");
        }

        OperacaoFinanceira estorno = criar(new CriarOperacaoCommand(
                usuarioId,
                TipoOperacaoFinanceira.ESTORNO,
                original.getPolitica(),
                OrigemOperacaoFinanceira.SISTEMA,
                LocalDateTime.now(clock),
                idempotencyKey,
                "estorno-de:" + original.getId(),
                motivo,
                original.getId()));

        original.setStatus(StatusOperacaoFinanceira.ESTORNADA);
        operacaoRepository.save(original);
        return estorno;
    }

    private OperacaoFinanceira criarNova(CriarOperacaoCommand command, String requestHash) {
        Usuario usuario = usuarioRepository.findById(command.usuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        OperacaoFinanceira operacao = new OperacaoFinanceira();
        operacao.setUsuario(usuario);
        operacao.setTipo(command.tipo());
        operacao.setStatus(StatusOperacaoFinanceira.CONFIRMADA);
        operacao.setPolitica(command.politica() == null ? PoliticaOperacao.CAIXA : command.politica());
        operacao.setOrigem(command.origem() == null ? OrigemOperacaoFinanceira.MANUAL : command.origem());
        operacao.setDataOperacao(command.dataOperacao() == null
                ? LocalDateTime.now(clock)
                : command.dataOperacao());
        operacao.setDataCriacao(LocalDateTime.now(clock));
        operacao.setIdempotencyKey(command.idempotencyKey());
        operacao.setRequestHash(requestHash);
        operacao.setDescricao(command.descricao());

        if (command.estornoDeId() != null) {
            OperacaoFinanceira referenciada = operacaoRepository.findById(command.estornoDeId())
                    .filter(op -> op.getUsuario().getId().equals(command.usuarioId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Operação referenciada não encontrada"));
            operacao.setEstornoDe(referenciada);
        }

        return operacaoRepository.save(operacao);
    }

    /** SHA-256 em hex do payload canonico do request (ADR-0009). */
    public static String hashPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
