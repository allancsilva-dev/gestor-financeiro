package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.FinancialConflictException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.OperacaoFinanceira;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.OrigemOperacaoFinanceira;
import com.gestor.financeiro.model.enums.PoliticaOperacao;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoOperacaoFinanceira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.OperacaoFinanceiraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Transferencia interna atomica (PR-F2-04, ADR-0009): operacao TRANSFERENCIA
 * com dois lancamentos vinculados. Locks adquiridos em ordem deterministica de
 * id para evitar deadlock; contas do mesmo usuario; origem != destino;
 * excluida de receita, despesa e resultado mensal (nao cria Transacao).
 */
@Service
public class TransferenciaService {

    private static final List<SubtipoContaFinanceira> SUBTIPOS_TRANSFERIVEIS = List.of(
            SubtipoContaFinanceira.DINHEIRO,
            SubtipoContaFinanceira.CORRENTE,
            SubtipoContaFinanceira.POUPANCA,
            SubtipoContaFinanceira.PAGAMENTO,
            SubtipoContaFinanceira.COFRE);

    private final CarteiraRepository carteiraRepository;
    private final MovimentoCarteiraRepository movimentoCarteiraRepository;
    private final OperacaoFinanceiraRepository operacaoRepository;
    private final OperacaoFinanceiraService operacaoService;
    private final LedgerService ledgerService;
    private final Clock clock;

    public TransferenciaService(CarteiraRepository carteiraRepository,
                                MovimentoCarteiraRepository movimentoCarteiraRepository,
                                OperacaoFinanceiraRepository operacaoRepository,
                                OperacaoFinanceiraService operacaoService,
                                LedgerService ledgerService,
                                Clock clock) {
        this.carteiraRepository = carteiraRepository;
        this.movimentoCarteiraRepository = movimentoCarteiraRepository;
        this.operacaoRepository = operacaoRepository;
        this.operacaoService = operacaoService;
        this.ledgerService = ledgerService;
        this.clock = clock;
    }

    public record Resultado(OperacaoFinanceira operacao,
                            MovimentoCarteira saida,
                            MovimentoCarteira entrada) {
    }

    @Transactional
    public Resultado transferir(TransferirCommand command) {
        if (command.contaOrigemId().equals(command.contaDestinoId())) {
            throw new BusinessException("Origem e destino devem ser contas diferentes");
        }

        String payload = payloadCanonico(command);

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            var existente = operacaoRepository.findByUsuarioIdAndIdempotencyKey(
                    command.usuarioId(), command.idempotencyKey());
            if (existente.isPresent()) {
                OperacaoFinanceira original = existente.get();
                String hash = OperacaoFinanceiraService.hashPayload(payload);
                if (hash != null && hash.equals(original.getRequestHash())) {
                    return montarResultadoExistente(original);
                }
                throw new FinancialConflictException(
                        "Chave de idempotência reutilizada com conteúdo diferente");
            }
        }

        // Locks em ordem deterministica de id (ADR-0009)
        long primeiroId = Math.min(command.contaOrigemId(), command.contaDestinoId());
        long segundoId = Math.max(command.contaOrigemId(), command.contaDestinoId());
        Carteira primeira = lockConta(primeiroId, command.usuarioId());
        Carteira segunda = lockConta(segundoId, command.usuarioId());

        Carteira origem = primeira.getId().equals(command.contaOrigemId()) ? primeira : segunda;
        Carteira destino = primeira.getId().equals(command.contaDestinoId()) ? primeira : segunda;

        validarTransferivel(origem, "origem");
        validarTransferivel(destino, "destino");

        LocalDateTime dataOperacao = command.dataOperacao() == null
                ? LocalDateTime.now(clock)
                : command.dataOperacao();

        OperacaoFinanceira operacao = operacaoService.criar(new CriarOperacaoCommand(
                command.usuarioId(),
                TipoOperacaoFinanceira.TRANSFERENCIA,
                PoliticaOperacao.CAIXA,
                OrigemOperacaoFinanceira.MANUAL,
                dataOperacao,
                command.idempotencyKey(),
                payload,
                command.descricao(),
                null));

        // Saldo insuficiente aplica a contas ATIVO sem credito permitido (ADR-0009)
        boolean permitirNegativoOrigem = origem.getNatureza() == NaturezaContaFinanceira.PASSIVO;

        MovimentoCarteira saida = ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
                command.usuarioId(),
                origem.getId(),
                TipoMovimentoCarteira.TRANSFERENCIA_SAIDA,
                command.valor(),
                RegistrarMovimentoCommand.Direcao.SAIDA,
                OrigemMovimentoCarteira.TRANSFERENCIA,
                "OPERACAO",
                operacao.getId(),
                descricaoMovimento(command, destino, "para"),
                chaveMovimento(command.idempotencyKey(), "saida"),
                dataOperacao,
                permitirNegativoOrigem), operacao);

        MovimentoCarteira entrada = ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
                command.usuarioId(),
                destino.getId(),
                TipoMovimentoCarteira.TRANSFERENCIA_ENTRADA,
                command.valor(),
                RegistrarMovimentoCommand.Direcao.ENTRADA,
                OrigemMovimentoCarteira.TRANSFERENCIA,
                "OPERACAO",
                operacao.getId(),
                descricaoMovimento(command, origem, "de"),
                chaveMovimento(command.idempotencyKey(), "entrada"),
                dataOperacao,
                true), operacao);

        return new Resultado(operacao, saida, entrada);
    }

    private Resultado montarResultadoExistente(OperacaoFinanceira operacao) {
        List<MovimentoCarteira> movimentos =
                movimentoCarteiraRepository.findByOperacaoIdOrderByValorAssinadoAsc(operacao.getId());
        if (movimentos.size() != 2) {
            throw new FinancialConflictException(
                    "Transferência em andamento ou inconsistente. Tente novamente.");
        }
        // ordenado por valorAssinado: saida (negativo) primeiro, entrada depois
        return new Resultado(operacao, movimentos.get(0), movimentos.get(1));
    }

    private Carteira lockConta(Long contaId, Long usuarioId) {
        return carteiraRepository.findByIdAndUsuarioIdForUpdate(contaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta financeira não encontrada"));
    }

    private static void validarTransferivel(Carteira conta, String papel) {
        if (conta.getSubtipo() == null || !SUBTIPOS_TRANSFERIVEIS.contains(conta.getSubtipo())) {
            throw new BusinessException(
                    "Conta de " + papel + " não aceita transferência direta (subtipo "
                            + conta.getSubtipo() + ")");
        }
    }

    private static String payloadCanonico(TransferirCommand command) {
        return "transferencia|origem=" + command.contaOrigemId()
                + "|destino=" + command.contaDestinoId()
                + "|valor=" + command.valor().stripTrailingZeros().toPlainString();
    }

    private static String chaveMovimento(String chaveOperacao, String sufixo) {
        return chaveOperacao == null || chaveOperacao.isBlank()
                ? null
                : chaveOperacao + "-" + sufixo;
    }

    private static String descricaoMovimento(TransferirCommand command, Carteira contraparte, String preposicao) {
        String base = command.descricao() == null || command.descricao().isBlank()
                ? "Transferência"
                : command.descricao();
        return base + " (" + preposicao + " " + contraparte.getNome() + ")";
    }
}
