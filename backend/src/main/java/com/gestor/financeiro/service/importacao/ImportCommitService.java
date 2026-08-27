package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.model.enums.ImportRecordReasonCode;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ImportBatchRepository;
import com.gestor.financeiro.repository.ImportRecordRepository;
import com.gestor.financeiro.service.RegraCategoriaService;
import com.gestor.financeiro.service.SugestaoCategoriaService;
import com.gestor.financeiro.service.TransacaoService;
import com.gestor.financeiro.service.job.BackgroundJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Lançamento do lote revisado no ledger.
 *
 * <p>Regras que este serviço existe para garantir:</p>
 * <ul>
 *   <li><b>Regra financeira não é duplicada aqui.</b> Cada linha vira transação por
 *       {@link TransacaoService#criar(Transacao, Long, String)} — o mesmo caminho do lançamento
 *       manual, com saldo, categoria e ledger.</li>
 *   <li><b>Idempotência por registro.</b> A chave {@code IMPORT:{batchId}:{recordId}} chega ao
 *       ledger e o índice único de idempotência do movimento é o backstop: reexecutar o commit
 *       (retentativa de job, lease vencido) não duplica saldo.</li>
 *   <li><b>Transação curta.</b> Uma transação por linha, nunca uma cobrindo o lote — o pool tem 10
 *       conexões e o lote vai a dezenas de milhares de linhas.</li>
 *   <li><b>Commit parcial é resultado válido.</b> Linha que falha vira {@code INVALID} com motivo
 *       registrado; o lote segue e o usuário vê o que ficou de fora.</li>
 * </ul>
 */
@Service
public class ImportCommitService {

    private static final Logger log = LoggerFactory.getLogger(ImportCommitService.class);
    private static final String TIPO_JOB_COMMIT = "IMPORT_COMMIT";
    private static final String MOEDA_SUPORTADA = "BRL";
    private static final List<ImportRecordStatus> LANCAVEIS =
            List.of(ImportRecordStatus.VALID, ImportRecordStatus.APPROVED);

    private final ImportBatchService batches;
    private final ImportBatchRepository batchRepository;
    private final ImportRecordRepository records;
    private final CarteiraRepository carteiras;
    private final ContaRepository cartoes;
    private final CategoriaRepository categorias;
    private final TransacaoService transacaoService;
    private final RegraCategoriaService regras;
    private final BackgroundJobService jobs;
    private final TransactionTemplate porRegistro;
    private final int tamanhoDoBloco;

    public ImportCommitService(ImportBatchService batches, ImportBatchRepository batchRepository,
                               ImportRecordRepository records, CarteiraRepository carteiras,
                               ContaRepository cartoes,
                               CategoriaRepository categorias, TransacaoService transacaoService,
                               RegraCategoriaService regras,
                               BackgroundJobService jobs, PlatformTransactionManager transactionManager,
                               @Value("${app.import.commit.chunk-size:200}") int tamanhoDoBloco) {
        this.batches = batches;
        this.batchRepository = batchRepository;
        this.records = records;
        this.carteiras = carteiras;
        this.cartoes = cartoes;
        this.categorias = categorias;
        this.transacaoService = transacaoService;
        this.regras = regras;
        this.jobs = jobs;
        this.porRegistro = new TransactionTemplate(transactionManager);
        this.porRegistro.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tamanhoDoBloco = Math.max(1, tamanhoDoBloco);
    }

    /** Define a conta de destino e libera o lote para lançamento. */
    @Transactional
    public ImportBatch preparar(Long usuarioId, Long batchId, Long contaFinanceiraId) {
        return preparar(usuarioId, batchId, contaFinanceiraId, null);
    }

    /**
     * Define o destino do lote: conta de caixa (extrato) ou cartão (fatura). Um ou outro, nunca os
     * dois — compra de cartão nasce na fatura e movimento de caixa nasce no ledger (ADR-0009), e o
     * arquivo pertence a uma dessas duas histórias.
     */
    @Transactional
    public ImportBatch preparar(Long usuarioId, Long batchId, Long contaFinanceiraId, Long cartaoId) {
        ImportBatch batch = batches.get(usuarioId, batchId);
        if ((contaFinanceiraId == null) == (cartaoId == null)) {
            throw new BusinessException("Escolha uma conta de caixa ou um cartão como destino");
        }

        if (cartaoId != null) {
            Conta cartao = cartoes.findByIdAndUsuarioId(cartaoId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));
            batch.setConta(cartao);
            batch.setCarteira(null);
        } else {
            Carteira conta = carteiras.findByIdAndUsuarioId(contaFinanceiraId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conta financeira não encontrada"));
            if (conta.getSubtipo() == SubtipoContaFinanceira.CARTAO) {
                // A conta passiva do cartão é espelho contábil; a fatura entra pelo cartão.
                throw new BusinessException("Para importar fatura, escolha o cartão, não a conta passiva");
            }
            batch.setCarteira(conta);
            batch.setConta(null);
        }
        batchRepository.save(batch);
        if (batch.getStatus() == ImportBatchStatus.PARSED
                || batch.getStatus() == ImportBatchStatus.PENDING_REVIEW) {
            return batches.transition(usuarioId, batchId, ImportBatchStatus.READY_TO_COMMIT, null);
        }
        return batch;
    }

    /** Aprova uma linha que a revisão decidiu trazer, opcionalmente com categoria. */
    @Transactional
    public ImportRecord aprovar(Long usuarioId, Long batchId, Long registroId, Long categoriaId) {
        return aprovar(usuarioId, batchId, registroId, categoriaId, false);
    }

    /**
     * Aprova a linha e, quando o titular pede, guarda a escolha como regra para as próximas
     * importações — a partir da descrição desta linha, casamento exato.
     */
    @Transactional
    public ImportRecord aprovar(Long usuarioId, Long batchId, Long registroId, Long categoriaId,
                                boolean criarRegra) {
        ImportBatch batch = batches.get(usuarioId, batchId);
        if (batch.getStatus() == ImportBatchStatus.COMMITTING
                || batch.getStatus() == ImportBatchStatus.COMMITTED
                || batch.getStatus() == ImportBatchStatus.REVERSED) {
            throw new BusinessException("Lote já lançado; revisão não é mais permitida");
        }
        ImportRecord record = records.findByIdAndBatchId(registroId, batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de importação não encontrado"));
        if (record.getStatus() == ImportRecordStatus.COMMITTED
                || record.getStatus() == ImportRecordStatus.REVERSED) {
            throw new BusinessException("Registro já lançado");
        }
        if (record.getAmount() == null || record.getOccurredOn() == null || record.getDirection() == null) {
            throw new BusinessException("Registro sem data, valor ou direção não pode ser aprovado");
        }
        if (categoriaId != null) {
            Categoria categoria = categorias.findByIdAndUsuarioId(categoriaId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
            record.setCategoria(categoria);
        }
        record.setStatus(ImportRecordStatus.APPROVED);
        ImportRecord salvo = records.save(record);

        if (criarRegra && categoriaId != null && record.getNormalizedDescription() != null) {
            regras.criar(usuarioId, SugestaoCategoriaService.normalizar(record.getNormalizedDescription()),
                    com.gestor.financeiro.model.enums.TipoCasamentoRegra.IGUAL, record.getDirection(),
                    categoriaId, 50);
        }
        return salvo;
    }

    /** Enfileira o lançamento: trabalho longo não roda na thread do request. */
    @Transactional
    public ImportBatch solicitarCommit(Long usuarioId, Long batchId) {
        ImportBatch batch = batches.get(usuarioId, batchId);
        if (batch.getCarteira() == null && batch.getConta() == null) {
            throw new BusinessException("Defina o destino antes de lançar");
        }
        if (batch.getStatus() == ImportBatchStatus.COMMITTING) {
            return batch; // Pedido repetido: o job já está na fila.
        }
        ImportBatch emCommit = batches.transition(usuarioId, batchId, ImportBatchStatus.COMMITTING, null);
        jobs.enqueue("IMPORT_COMMIT:" + batchId, TIPO_JOB_COMMIT,
                "{\"usuarioId\":" + usuarioId + ",\"batchId\":" + batchId + "}", (short) 1,
                10, Instant.now(), 3);
        return emCommit;
    }

    /**
     * Executa o lançamento. Idempotente por construção: registro já lançado é pulado e a chave de
     * idempotência do ledger protege a corrida com uma reexecução do job.
     */
    public void executar(Long usuarioId, Long batchId) {
        ImportBatch batch = batches.get(usuarioId, batchId);
        if (batch.getStatus() == ImportBatchStatus.COMMITTED) return;
        if (batch.getStatus() != ImportBatchStatus.COMMITTING) {
            throw new BusinessException("Lote não está em lançamento");
        }
        Long carteiraId = batch.getCarteira() == null ? null : batch.getCarteira().getId();
        Long cartaoId = batch.getConta() == null ? null : batch.getConta().getId();
        if (carteiraId == null && cartaoId == null) {
            batches.transition(usuarioId, batchId, ImportBatchStatus.FAILED, ImportFailureCode.COMMIT_FAILED);
            throw new BusinessException("Lote sem destino");
        }

        int cursor = 0;
        int lancados = 0;
        int falhas = 0;
        while (true) {
            List<ImportRecord> bloco = records.paginaParaLancamento(batchId, cursor, LANCAVEIS,
                    Limit.of(tamanhoDoBloco));
            if (bloco.isEmpty()) break;
            for (ImportRecord record : bloco) {
                cursor = record.getSourceLine();
                if (lancarRegistro(usuarioId, batchId, carteiraId, cartaoId, record.getId())) {
                    lancados++;
                } else {
                    falhas++;
                }
            }
        }

        batches.transition(usuarioId, batchId, ImportBatchStatus.COMMITTED, null);
        log.info("Importação {} lançada: {} registro(s), {} falha(s)", batchId, lancados, falhas);
    }

    /** Uma transação por linha: falha isolada não derruba o lote nem prende conexão. */
    private boolean lancarRegistro(Long usuarioId, Long batchId, Long carteiraId, Long cartaoId,
                                   Long registroId) {
        try {
            return Boolean.TRUE.equals(porRegistro.execute(status -> {
                ImportRecord record = records.findById(registroId).orElseThrow();
                if (record.getStatus() == ImportRecordStatus.COMMITTED) return true;
                if (!MOEDA_SUPORTADA.equals(record.getCurrency())) {
                    marcarFalha(record, ImportRecordReasonCode.CURRENCY_UNSUPPORTED);
                    return false;
                }

                if (cartaoId != null && record.getDirection() != TipoTransacao.SAIDA) {
                    // Entrada em fatura é estorno, e estorno nasce do cancelamento da compra
                    // original — não de uma linha solta do arquivo.
                    marcarFalha(record, ImportRecordReasonCode.COMMIT_FAILED);
                    return false;
                }

                Transacao transacao = new Transacao();
                transacao.setDescricao(record.getNormalizedDescription());
                transacao.setValorTotal(record.getAmount());
                transacao.setData(record.getOccurredOn());
                transacao.setTipo(record.getDirection());
                transacao.setStatus(StatusPagamento.PAGO);
                if (cartaoId != null) {
                    // Conta preenchida + SAIDA é o caminho de compra no cartão: TransacaoService
                    // registra a fatura e espelha o passivo, sem duplicar regra aqui.
                    transacao.setConta(cartoes.getReferenceById(cartaoId));
                } else {
                    transacao.setCarteira(carteiras.getReferenceById(carteiraId));
                }
                if (record.getCategoria() != null) {
                    transacao.setCategoria(record.getCategoria());
                }

                // Chave derivada do registro: reexecutar o commit não cria segundo movimento.
                Transacao criada = transacaoService.criar(transacao, usuarioId,
                        "IMPORT:" + batchId + ":" + record.getId());

                record.setTransacao(criada);
                record.setStatus(ImportRecordStatus.COMMITTED);
                record.setReasonCode(null);
                records.save(record);
                return true;
            }));
        } catch (RuntimeException falha) {
            // Falha de uma linha (categoria removida, saldo insuficiente, valor recusado) não
            // interrompe o lote: fica registrada e o usuário vê o que ficou de fora.
            log.warn("Registro {} do lote {} não pôde ser lançado: {}", registroId, batchId,
                    falha.getClass().getSimpleName());
            porRegistro.executeWithoutResult(status -> {
                ImportRecord record = records.findById(registroId).orElseThrow();
                marcarFalha(record, ImportRecordReasonCode.COMMIT_FAILED);
            });
            return false;
        }
    }

    private void marcarFalha(ImportRecord record, ImportRecordReasonCode motivo) {
        record.setStatus(ImportRecordStatus.INVALID);
        record.setReasonCode(motivo.name());
        records.save(record);
    }
}
