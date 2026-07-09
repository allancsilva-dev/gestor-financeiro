package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
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
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private ParcelaRepository parcelaRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private ContaService contaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private CarteiraRepository carteiraRepository;

    public Page<Transacao> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return transacaoRepository.findByUsuarioIdAndAtivaTrue(usuarioId, pageable);
    }

    public Page<Transacao> listarPorPeriodo(Long usuarioId, LocalDate inicio, LocalDate fim, Pageable pageable) {
        return transacaoRepository.findByUsuarioIdAndDataBetweenAndAtivaTrue(usuarioId, inicio, fim, pageable);
    }

    @Transactional
    public Transacao criar(Transacao transacao, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        transacao.setUsuario(usuario);
        transacao.setAtiva(true);
        transacao.setStatus(StatusPagamento.PENDENTE);

        if (transacao.getCategoria() != null && transacao.getCategoria().getId() != null) {
            Categoria categoria = categoriaRepository.findByIdAndUsuarioId(
                    transacao.getCategoria().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));

            categoria.setValorGasto(
                categoria.getValorGasto().add(transacao.getValorTotal())
            );
            categoriaRepository.save(categoria);

            transacao.setCategoria(categoria);
        }

        if (transacao.getConta() != null && transacao.getConta().getId() != null) {
            Conta conta = contaRepository.findByIdAndUsuarioId(
                    transacao.getConta().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));

            contaService.adicionarGasto(conta.getId(), transacao.getValorTotal(), usuarioId);

            transacao.setConta(conta);
        }

        if (transacao.getParcelado() && transacao.getTotalParcelas() > 1) {
            criarParcelas(transacao);
        }

        Transacao salva = transacaoRepository.save(transacao);

        registrarMovimentoCriacao(salva, usuarioId);

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
            parcela.setValor(valorParcela);
            parcela.setDataVencimento(transacao.getData().plusMonths(i));
            parcela.setStatus(StatusPagamento.PENDENTE);

            parcelas.add(parcela);
        }

        transacao.setParcelas(parcelas);
    }

    @Transactional
    public Transacao atualizar(Long id, Transacao transacaoAtualizada, Long usuarioId) {
        Transacao transacao = buscarPorIdDoUsuario(id, usuarioId);

        if (!transacao.getAtiva()) {
            throw new BusinessException("Transação cancelada não pode ser editada");
        }

        BigDecimal valorAnterior = transacao.getValorTotal();
        BigDecimal novoValor = transacaoAtualizada.getValorTotal();

        transacao.setDescricao(transacaoAtualizada.getDescricao());
        transacao.setValorTotal(novoValor);
        transacao.setData(transacaoAtualizada.getData());
        transacao.setObservacoes(transacaoAtualizada.getObservacoes());

        Transacao salva = transacaoRepository.save(transacao);

        if (novoValor != null && valorAnterior != null) {
            BigDecimal diferença = novoValor.subtract(valorAnterior);
            if (diferença.signum() != 0) {
                registrarMovimentoDiferenca(salva, usuarioId, diferença);
            }
        }

        return salva;
    }

    @Transactional
    public void deletar(Long id, Long usuarioId) {
        Transacao transacao = buscarPorIdDoUsuario(id, usuarioId);

        if (transacao.getConta() != null) {
            contaService.removerGasto(
                transacao.getConta().getId(),
                transacao.getValorTotal(),
                usuarioId
            );
        }

        if (transacao.getCategoria() != null) {
            Categoria categoria = categoriaRepository.findByIdAndUsuarioId(
                    transacao.getCategoria().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));

            categoria.setValorGasto(
                categoria.getValorGasto().subtract(transacao.getValorTotal())
            );
            categoriaRepository.save(categoria);
        }

        registrarEstornoCancelamento(transacao, usuarioId);

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

    private void registrarMovimentoCriacao(Transacao transacao, Long usuarioId) {
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
                null,
                LocalDateTime.now(),
                false
        ));
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
                LocalDateTime.now(),
                false
        ));
    }

    private void registrarEstornoCancelamento(Transacao transacao, Long usuarioId) {
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
                null,
                LocalDateTime.now(),
                false
        ));
    }
}
