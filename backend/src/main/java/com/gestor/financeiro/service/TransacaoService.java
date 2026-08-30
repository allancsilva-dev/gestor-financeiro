package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.OperacaoFinanceira;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.EstadoConciliacaoTransacao;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.ParcelaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransacaoService {

    private final TransacaoRepository transacaoRepository;
    private final ParcelaRepository parcelaRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaRepository contaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LedgerService ledgerService;
    private final CarteiraRepository carteiraRepository;
    private final FaturaService faturaService;
    private final java.time.Clock clock;

    public TransacaoService(TransacaoRepository transacaoRepository,
                            ParcelaRepository parcelaRepository,
                            CategoriaRepository categoriaRepository,
                            ContaRepository contaRepository,
                            UsuarioRepository usuarioRepository,
                            LedgerService ledgerService,
                            CarteiraRepository carteiraRepository,
                            FaturaService faturaService,
                            java.time.Clock clock) {
        this.transacaoRepository = transacaoRepository;
        this.parcelaRepository = parcelaRepository;
        this.categoriaRepository = categoriaRepository;
        this.contaRepository = contaRepository;
        this.usuarioRepository = usuarioRepository;
        this.ledgerService = ledgerService;
        this.carteiraRepository = carteiraRepository;
        this.faturaService = faturaService;
        this.clock = clock;
    }

    public Page<Transacao> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return transacaoRepository.findByUsuarioIdAndAtivaTrue(usuarioId, pageable);
    }

    public Page<Transacao> listarPorPeriodo(Long usuarioId, LocalDate inicio, LocalDate fim, Pageable pageable) {
        return listarPorPeriodo(usuarioId, inicio, fim, null, null, pageable);
    }

    public Page<Transacao> listarPorPeriodo(Long usuarioId, LocalDate inicio, LocalDate fim,
                                            TipoTransacao tipo, String busca, Pageable pageable) {
        return listarPorPeriodo(usuarioId, inicio, fim, tipo, busca, null, null, null, pageable);
    }

    /**
     * Drill-down (PR-F3-04): filtros opcionais de categoria, conta financeira
     * e cartao, combinaveis com periodo, tipo e busca. Recurso alheio segue o
     * contrato seguro existente (404 sem vazar existencia).
     */
    public Page<Transacao> listarPorPeriodo(Long usuarioId, LocalDate inicio, LocalDate fim,
                                            TipoTransacao tipo, String busca,
                                            Long categoriaId, Long carteiraId, Long cartaoId,
                                            Pageable pageable) {
        String q = (busca == null || busca.isBlank()) ? null : busca.trim();

        if (categoriaId != null || carteiraId != null || cartaoId != null) {
            validarOwnershipFiltros(usuarioId, categoriaId, carteiraId, cartaoId);
            if (tipo != null) {
                return transacaoRepository.buscarPorPeriodoTipoComFiltros(
                        usuarioId, tipo, inicio, fim, q, categoriaId, carteiraId, cartaoId, pageable);
            }
            return transacaoRepository.buscarPorPeriodoComFiltros(
                    usuarioId, inicio, fim, q, categoriaId, carteiraId, cartaoId, pageable);
        }

        if (tipo != null && q != null) {
            return transacaoRepository.buscarPorPeriodoTipoEDescricao(usuarioId, tipo, inicio, fim, q, pageable);
        }
        if (tipo != null) {
            return transacaoRepository.findByUsuarioIdAndTipoAndDataBetweenAndAtivaTrue(usuarioId, tipo, inicio, fim, pageable);
        }
        if (q != null) {
            return transacaoRepository.buscarPorPeriodoEDescricao(usuarioId, inicio, fim, q, pageable);
        }
        return transacaoRepository.findByUsuarioIdAndDataBetweenAndAtivaTrue(usuarioId, inicio, fim, pageable);
    }

    private void validarOwnershipFiltros(Long usuarioId, Long categoriaId, Long carteiraId, Long cartaoId) {
        if (categoriaId != null) {
            categoriaRepository.findByIdAndUsuarioId(categoriaId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
        }
        if (carteiraId != null) {
            carteiraRepository.findByIdAndUsuarioId(carteiraId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conta financeira não encontrada"));
        }
        if (cartaoId != null) {
            contaRepository.findByIdAndUsuarioId(cartaoId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));
        }
    }

    @Transactional
    public Transacao criar(Transacao transacao, Long usuarioId) {
        return criar(transacao, usuarioId, null);
    }

    @Transactional
    public Transacao criar(Transacao transacao, Long usuarioId, String ledgerIdempotencyKey) {
        return criar(transacao, usuarioId, ledgerIdempotencyKey, false, null);
    }

    /** Fluxos auditáveis novos vinculam o movimento à operação agrupadora (ADR-0009). */
    @Transactional
    public Transacao criar(Transacao transacao, Long usuarioId, String ledgerIdempotencyKey,
                           OperacaoFinanceira operacao) {
        return criar(transacao, usuarioId, ledgerIdempotencyKey, false, operacao);
    }

    /**
     * Caminho de importacao (PR-F2-05): transacao sem conta financeira e aceita
     * como PENDENTE_CONCILIACAO (importacao incompleta) em vez de rejeitada.
     */
    @Transactional
    public Transacao criarImportada(Transacao transacao, Long usuarioId) {
        return criar(transacao, usuarioId, null, true, null);
    }

    @Transactional
    protected Transacao criar(Transacao transacao, Long usuarioId, String ledgerIdempotencyKey,
                              boolean importacao, OperacaoFinanceira operacao) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        transacao.setUsuario(usuario);
        transacao.setAtiva(true);
        if (transacao.getStatus() == null) {
            transacao.setStatus(StatusPagamento.PENDENTE);
        }

        if (transacao.getCategoria() != null && transacao.getCategoria().getId() != null) {
            Categoria categoria = categoriaRepository.findByIdAndUsuarioId(
                    transacao.getCategoria().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));

            // valorGasto acumula apenas dinheiro que sai; entradas não são gasto
            if (transacao.getTipo() == TipoTransacao.SAIDA) {
                categoria.setValorGasto(
                    categoria.getValorGasto().add(transacao.getValorTotal())
                );
                categoriaRepository.save(categoria);
            }

            transacao.setCategoria(categoria);
        }

        boolean compraCartao = false;
        if (transacao.getConta() != null && transacao.getConta().getId() != null) {
            Conta conta = contaRepository.findByIdAndUsuarioId(
                    transacao.getConta().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));

            transacao.setConta(conta);
            compraCartao = isCompraCartao(transacao);
        }

        if (compraCartao) {
            transacao.setCarteira(null);
            transacao.setEstadoConciliacao(EstadoConciliacaoTransacao.CONCILIADA);
        } else if (transacao.getCarteira() != null && transacao.getCarteira().getId() != null) {
            // Substitui o stub detached vindo do controller por entidade gerenciada (valida ownership)
            Carteira carteira = carteiraRepository.findByIdAndUsuarioId(
                    transacao.getCarteira().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));

            transacao.setCarteira(carteira);
            transacao.setEstadoConciliacao(EstadoConciliacaoTransacao.CONCILIADA);
        } else if (importacao) {
            // Importacao incompleta: dado entra pendente, fora de saldos/metricas
            // conciliadas, ate ganhar conta financeira (ADR-0009/0013)
            transacao.setEstadoConciliacao(EstadoConciliacaoTransacao.PENDENTE_CONCILIACAO);
        } else {
            // Operacao manual de caixa exige conta financeira (PR-F2-05; fecha P1-2)
            throw new BusinessException(
                    "Informe a conta financeira (carteiraId) da transação");
        }

        // Cartao tem cronograma canonico em FaturaLancamento. Parcela existe somente
        // para parcelamentos fora do cartao.
        if (!compraCartao && transacao.getParcelado() && transacao.getTotalParcelas() > 1) {
            criarParcelas(transacao);
        }

        Transacao salva = transacaoRepository.save(transacao);

        registrarMovimentoCriacao(salva, usuarioId, ledgerIdempotencyKey, operacao);
        if (compraCartao) {
            // Compra de cartao nao passa pelo ledger de caixa; a chave viaja pela
            // operacao agrupadora para nao se perder (ADR-0009).
            faturaService.registrarCompraCartao(salva, usuarioId, ledgerIdempotencyKey);
        }

        return salva;
    }

    private void criarParcelas(Transacao transacao) {
        List<Parcela> parcelas = new ArrayList<>();

        BigDecimal valorParcela = transacao.getValorTotal()
            .divide(BigDecimal.valueOf(transacao.getTotalParcelas()), 2, RoundingMode.HALF_UP);

        transacao.setValorParcela(valorParcela);

        for (int i = 1; i <= transacao.getTotalParcelas(); i++) {
            Parcela parcela = new Parcela();
            parcela.setTransacao(transacao);
            parcela.setNumeroParcela(i);
            parcela.setTotalParcelas(transacao.getTotalParcelas());
            parcela.setValor(valorParcelaOuResto(transacao.getValorTotal(), valorParcela,
                    i, transacao.getTotalParcelas()));
            parcela.setDataVencimento(transacao.getData().plusMonths(i));
            parcela.setStatus(StatusPagamento.PENDENTE);

            parcelas.add(parcela);
        }

        transacao.setParcelas(parcelas);
    }

    // Última parcela absorve a diferença de arredondamento para a soma fechar o valor total
    private BigDecimal valorParcelaOuResto(BigDecimal valorTotal, BigDecimal valorParcela,
                                           int numeroParcela, int totalParcelas) {
        if (numeroParcela < totalParcelas) {
            return valorParcela;
        }
        return valorTotal.subtract(valorParcela.multiply(BigDecimal.valueOf(totalParcelas - 1L)));
    }

    @Transactional
    public Transacao atualizar(Long id, Transacao transacaoAtualizada, Long usuarioId) {
        Transacao transacao = buscarPorIdDoUsuario(id, usuarioId);

        if (!transacao.getAtiva()) {
            throw new BusinessException("Transação cancelada não pode ser editada");
        }

        BigDecimal valorAnterior = transacao.getValorTotal();
        BigDecimal novoValor = transacaoAtualizada.getValorTotal();
        boolean valorAlterado = novoValor != null && valorAnterior != null
                && novoValor.compareTo(valorAnterior) != 0;
        boolean dataAlterada = transacaoAtualizada.getData() != null
                && !transacaoAtualizada.getData().equals(transacao.getData());
        boolean compraCartao = isCompraCartao(transacao);
        Categoria categoriaAnterior = transacao.getCategoria();
        Long novaCategoriaId = transacaoAtualizada.getCategoria() != null
                ? transacaoAtualizada.getCategoria().getId()
                : null;
        boolean categoriaAlterada = novaCategoriaId != null
                && (categoriaAnterior == null || !novaCategoriaId.equals(categoriaAnterior.getId()));
        Categoria novaCategoria = categoriaAnterior;
        if (categoriaAlterada) {
            novaCategoria = categoriaRepository.findByIdAndUsuarioId(novaCategoriaId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
        }

        transacao.setDescricao(transacaoAtualizada.getDescricao());
        transacao.setValorTotal(novoValor);
        transacao.setData(transacaoAtualizada.getData());
        transacao.setObservacoes(transacaoAtualizada.getObservacoes());
        transacao.setCategoria(novaCategoria);

        if (valorAlterado) {
            atualizarValorParcelas(transacao);
        }

        if (transacao.getTipo() == TipoTransacao.SAIDA) {
            if (categoriaAlterada) {
                if (categoriaAnterior != null) {
                    categoriaAnterior.setValorGasto(categoriaAnterior.getValorGasto().subtract(valorAnterior));
                    categoriaRepository.save(categoriaAnterior);
                }
                novaCategoria.setValorGasto(novaCategoria.getValorGasto().add(novoValor));
                categoriaRepository.save(novaCategoria);
            } else if (valorAlterado && novaCategoria != null) {
                novaCategoria.setValorGasto(novaCategoria.getValorGasto().add(novoValor.subtract(valorAnterior)));
                categoriaRepository.save(novaCategoria);
            }
        }

        Transacao salva = transacaoRepository.save(transacao);

        if (compraCartao && (valorAlterado || dataAlterada)) {
            faturaService.ressincronizarCompraCartao(salva, usuarioId);
        }

        if (valorAlterado) {
            registrarMovimentoDiferenca(salva, usuarioId, novoValor.subtract(valorAnterior));
        }

        return salva;
    }

    private void atualizarValorParcelas(Transacao transacao) {
        List<Parcela> parcelas = transacao.getParcelas();
        if (parcelas == null || parcelas.isEmpty()) {
            return;
        }

        int total = parcelas.size();
        BigDecimal valorParcela = transacao.getValorTotal()
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        transacao.setValorParcela(valorParcela);

        for (Parcela parcela : parcelas) {
            parcela.setValor(valorParcelaOuResto(
                    transacao.getValorTotal(), valorParcela, parcela.getNumeroParcela(), total));
        }
    }

    @Transactional
    public void deletar(Long id, Long usuarioId) {
        deletar(id, usuarioId, null);
    }

    /**
     * Cancelamento com chave de idempotencia no estorno. Reversao em lote (importacao) precisa
     * poder ser reexecutada apos falha sem estornar duas vezes o mesmo movimento.
     */
    @Transactional
    public void deletar(Long id, Long usuarioId, String ledgerIdempotencyKey) {
        Transacao transacao = buscarPorIdDoUsuario(id, usuarioId);
        if (Boolean.FALSE.equals(transacao.getAtiva())) {
            return;
        }

        boolean compraCartao = isCompraCartao(transacao);
        if (compraCartao) {
            // Libera o limite via remoção dos lançamentos abertos e estorna a parte já paga
            faturaService.cancelarCompraCartao(transacao, usuarioId);
        }

        if (transacao.getCategoria() != null && transacao.getTipo() == TipoTransacao.SAIDA) {
            Categoria categoria = categoriaRepository.findByIdAndUsuarioId(
                    transacao.getCategoria().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));

            categoria.setValorGasto(
                categoria.getValorGasto().subtract(transacao.getValorTotal())
            );
            categoriaRepository.save(categoria);
        }

        registrarEstornoCancelamento(transacao, usuarioId, ledgerIdempotencyKey);

        transacao.setAtiva(false);
        transacaoRepository.save(transacao);
    }

    @Transactional
    public void cancelar(Long id, Long usuarioId) {
        deletar(id, usuarioId);
    }

    public Transacao buscarPorId(Long id) {
        return transacaoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));
    }

    public Transacao buscarPorIdDoUsuario(Long id, Long usuarioId) {
        return transacaoRepository.findByIdAndUsuarioId(id, usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));
    }

    private void registrarMovimentoCriacao(Transacao transacao, Long usuarioId, String idempotencyKey,
                                           OperacaoFinanceira operacao) {
        if (transacao.getCarteira() == null || transacao.getCarteira().getId() == null) {
            return;
        }

        Carteira carteira = carteiraRepository.findByIdAndUsuarioIdForUpdate(
                        transacao.getCarteira().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));

        RegistrarMovimentoCommand.Direcao direcao = transacao.getTipo() == TipoTransacao.ENTRADA
                ? RegistrarMovimentoCommand.Direcao.ENTRADA
                : RegistrarMovimentoCommand.Direcao.SAIDA;

        ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
                usuarioId,
                carteira.getId(),
                transacao.getTipo() == TipoTransacao.ENTRADA
                        ? TipoMovimentoCarteira.ENTRADA
                        : TipoMovimentoCarteira.SAIDA,
                transacao.getValorTotal(),
                direcao,
                OrigemMovimentoCarteira.TRANSACAO,
                "TRANSACAO",
                transacao.getId(),
                transacao.getDescricao(),
                idempotencyKey,
                LocalDateTime.now(clock),
                false
        ), operacao);
    }

    // Contract V41: toda conta referenciada e cartao
    private boolean isCompraCartao(Transacao transacao) {
        return transacao != null
                && transacao.getConta() != null
                && transacao.getTipo() == TipoTransacao.SAIDA;
    }

    private void registrarMovimentoDiferenca(Transacao transacao, Long usuarioId, BigDecimal diferença) {
        if (transacao.getCarteira() == null || transacao.getCarteira().getId() == null) {
            return;
        }

        Carteira carteira = carteiraRepository.findByIdAndUsuarioIdForUpdate(
                        transacao.getCarteira().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));

        boolean deltaPositivo = diferença.signum() > 0;
        boolean isSaida = transacao.getTipo() == TipoTransacao.SAIDA;
        RegistrarMovimentoCommand.Direcao direcao;

        if (isSaida) {
            direcao = deltaPositivo
                    ? RegistrarMovimentoCommand.Direcao.SAIDA
                    : RegistrarMovimentoCommand.Direcao.ENTRADA;
        } else {
            direcao = deltaPositivo
                    ? RegistrarMovimentoCommand.Direcao.ENTRADA
                    : RegistrarMovimentoCommand.Direcao.SAIDA;
        }

        ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
                usuarioId,
                carteira.getId(),
                TipoMovimentoCarteira.AJUSTE_MANUAL,
                diferença.abs(),
                direcao,
                OrigemMovimentoCarteira.TRANSACAO,
                "TRANSACAO",
                transacao.getId(),
                "Ajuste de valor da transação: " + transacao.getDescricao(),
                null,
                LocalDateTime.now(clock),
                false
        ));
    }

    private void registrarEstornoCancelamento(Transacao transacao, Long usuarioId,
                                              String ledgerIdempotencyKey) {
        if (transacao.getCarteira() == null || transacao.getCarteira().getId() == null) {
            return;
        }

        Carteira carteira = carteiraRepository.findByIdAndUsuarioIdForUpdate(
                        transacao.getCarteira().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));

        RegistrarMovimentoCommand.Direcao direcao = transacao.getTipo() == TipoTransacao.ENTRADA
                ? RegistrarMovimentoCommand.Direcao.SAIDA
                : RegistrarMovimentoCommand.Direcao.ENTRADA;

        ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
                usuarioId,
                carteira.getId(),
                TipoMovimentoCarteira.ESTORNO,
                transacao.getValorTotal(),
                direcao,
                OrigemMovimentoCarteira.TRANSACAO,
                "TRANSACAO",
                transacao.getId(),
                "Estorno por cancelamento: " + transacao.getDescricao(),
                ledgerIdempotencyKey,
                LocalDateTime.now(clock),
                false
        ));
    }
}
