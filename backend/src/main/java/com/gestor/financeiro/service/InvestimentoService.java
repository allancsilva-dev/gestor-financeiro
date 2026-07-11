package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Ativo;
import com.gestor.financeiro.model.MovimentacaoAtivo;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoAtivo;
import com.gestor.financeiro.model.enums.TipoMovimentacao;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.repository.AtivoRepository;
import com.gestor.financeiro.repository.MovimentacaoAtivoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InvestimentoService {

    private final AtivoRepository ativoRepository;
    private final MovimentacaoAtivoRepository movimentacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final LedgerService ledgerService;

    public InvestimentoService(AtivoRepository ativoRepository,
                               MovimentacaoAtivoRepository movimentacaoRepository,
                               UsuarioRepository usuarioRepository,
                               LedgerService ledgerService) {
        this.ativoRepository = ativoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public AtivoResponse criarAtivo(Long usuarioId, AtivoRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        Ativo ativo = new Ativo();
        ativo.setUsuario(usuario);
        ativo.setTicker(request.getTicker().toUpperCase());
        ativo.setNome(request.getNome());
        ativo.setTipo(TipoAtivo.valueOf(request.getTipo()));
        ativo.setQuantidade(BigDecimal.ZERO);
        ativo.setCustoTotal(BigDecimal.ZERO);
        ativo.setValorAtual(request.getValorAtual());
        ativo = ativoRepository.save(ativo);
        return toResponse(ativo);
    }

    public List<AtivoResponse> listarAtivos(Long usuarioId) {
        return ativoRepository.findByUsuarioId(usuarioId).stream()
            .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public AtivoResponse atualizarAtivo(Long usuarioId, Long ativoId, AtivoRequest request) {
        Ativo ativo = ativoRepository.findByIdAndUsuarioId(ativoId, usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Ativo nao encontrado"));
        ativo.setTicker(request.getTicker().toUpperCase());
        ativo.setNome(request.getNome());
        ativo.setTipo(TipoAtivo.valueOf(request.getTipo()));
        ativo.setValorAtual(request.getValorAtual());
        return toResponse(ativoRepository.save(ativo));
    }

    @Transactional
    public void deletarAtivo(Long usuarioId, Long ativoId) {
        Ativo ativo = ativoRepository.findByIdAndUsuarioId(ativoId, usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Ativo nao encontrado"));
        ativoRepository.delete(ativo);
    }

    @Transactional
    public MovimentacaoResponse adicionarMovimentacao(Long usuarioId, Long ativoId, MovimentacaoRequest request) {
        Ativo ativo = ativoRepository.findByIdAndUsuarioId(ativoId, usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Ativo nao encontrado"));
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();

        TipoMovimentacao tipo = parseTipo(request.getTipo());
        BigDecimal quantidade = request.getQuantidade();
        BigDecimal preco = request.getPrecoUnitario();
        validarValores(tipo, quantidade, preco);

        BigDecimal valorTotal = quantidade.multiply(preco);

        // Bloqueia venda acima da posicao antes de qualquer efeito colateral (PROB-0054).
        if (tipo == TipoMovimentacao.VENDA && quantidade.compareTo(ativo.getQuantidade()) > 0) {
            throw new BusinessException("Quantidade insuficiente para venda: posicao atual "
                + ativo.getQuantidade() + ", venda solicitada " + quantidade);
        }

        MovimentacaoAtivo mov = new MovimentacaoAtivo();
        mov.setAtivo(ativo);
        mov.setUsuario(usuario);
        mov.setTipo(tipo);
        mov.setData(request.getData());
        mov.setQuantidade(quantidade);
        mov.setPrecoUnitario(preco);
        mov.setValorTotal(valorTotal);
        mov = movimentacaoRepository.save(mov);

        updateAtivoPosicao(ativo, tipo, quantidade, valorTotal);
        integrarCaixa(usuario, ativo, mov, tipo, valorTotal, request);

        return MovimentacaoResponse.builder()
            .id(mov.getId())
            .tipo(mov.getTipo().name())
            .data(mov.getData())
            .quantidade(mov.getQuantidade())
            .precoUnitario(mov.getPrecoUnitario())
            .valorTotal(mov.getValorTotal())
            .build();
    }

    private void updateAtivoPosicao(Ativo ativo, TipoMovimentacao tipo, BigDecimal qtd, BigDecimal valor) {
        switch (tipo) {
            case COMPRA -> {
                ativo.setQuantidade(ativo.getQuantidade().add(qtd));
                ativo.setCustoTotal(ativo.getCustoTotal().add(valor));
            }
            case BONIFICACAO -> {
                // Acoes gratuitas: aumentam a quantidade sem custo, reduzindo o preco medio.
                ativo.setQuantidade(ativo.getQuantidade().add(qtd));
            }
            case VENDA -> {
                // qtd <= quantidade (validado antes), entao quantidade > 0: sem divisao por zero.
                BigDecimal precoMedio = ativo.getCustoTotal()
                    .divide(ativo.getQuantidade(), 8, RoundingMode.HALF_UP);
                BigDecimal custoVendido = precoMedio.multiply(qtd);
                ativo.setQuantidade(ativo.getQuantidade().subtract(qtd));
                ativo.setCustoTotal(ativo.getCustoTotal().subtract(custoVendido).max(BigDecimal.ZERO));
            }
            case DIVIDENDO -> {
                // Provento em caixa: nao altera quantidade nem custo da posicao.
            }
        }
        ativoRepository.save(ativo);
    }

    private static TipoMovimentacao parseTipo(String tipo) {
        try {
            return TipoMovimentacao.valueOf(tipo);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Tipo de movimentacao invalido: " + tipo);
        }
    }

    private static void validarValores(TipoMovimentacao tipo, BigDecimal qtd, BigDecimal preco) {
        if (qtd == null || qtd.signum() <= 0) {
            throw new BusinessException("Quantidade deve ser positiva");
        }
        if (preco == null || preco.signum() < 0) {
            throw new BusinessException("Preco unitario nao pode ser negativo");
        }
        // Bonificacao pode ter preco zero (acoes gratuitas); os demais exigem preco positivo.
        if (tipo != TipoMovimentacao.BONIFICACAO && preco.signum() == 0) {
            throw new BusinessException("Preco unitario deve ser positivo");
        }
    }

    // Integra a movimentacao ao caixa quando uma carteira e informada (PROB-0054).
    private void integrarCaixa(Usuario usuario, Ativo ativo, MovimentacaoAtivo mov,
                               TipoMovimentacao tipo, BigDecimal valorTotal, MovimentacaoRequest request) {
        if (request.getCarteiraId() == null) {
            return; // Movimentacao apenas de posicao, sem efeito de caixa.
        }

        RegistrarMovimentoCommand.Direcao direcao;
        switch (tipo) {
            case COMPRA -> direcao = RegistrarMovimentoCommand.Direcao.SAIDA;
            case VENDA, DIVIDENDO -> direcao = RegistrarMovimentoCommand.Direcao.ENTRADA;
            default -> {
                return; // BONIFICACAO nao movimenta caixa.
            }
        }

        if (valorTotal.signum() <= 0) {
            return; // Guarda extra: nada a movimentar.
        }

        TipoMovimentoCarteira tipoMov = direcao == RegistrarMovimentoCommand.Direcao.ENTRADA
            ? TipoMovimentoCarteira.ENTRADA
            : TipoMovimentoCarteira.SAIDA;

        // A carteira precisa ser do usuario; LedgerService valida ownership com lock e
        // bloqueia saldo insuficiente na COMPRA (permitirSaldoNegativo = false).
        ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
            usuario.getId(),
            request.getCarteiraId(),
            tipoMov,
            valorTotal,
            direcao,
            OrigemMovimentoCarteira.INVESTIMENTO,
            "ATIVO",
            ativo.getId(),
            tipo.getDescricao() + " de " + ativo.getTicker(),
            "MOV_ATIVO_" + mov.getId(),
            request.getData() != null ? request.getData().atStartOfDay() : null,
            false
        ));
    }

    public List<MovimentacaoResponse> listarMovimentacoes(Long usuarioId, Long ativoId) {
        return movimentacaoRepository.findByAtivoIdAndUsuarioIdOrderByDataDesc(ativoId, usuarioId)
            .stream().map(m -> MovimentacaoResponse.builder()
                .id(m.getId())
                .tipo(m.getTipo().name())
                .data(m.getData())
                .quantidade(m.getQuantidade())
                .precoUnitario(m.getPrecoUnitario())
                .valorTotal(m.getValorTotal())
                .build())
            .collect(Collectors.toList());
    }

    private AtivoResponse toResponse(Ativo a) {
        BigDecimal precoMedio = BigDecimal.ZERO;
        if (a.getQuantidade().compareTo(BigDecimal.ZERO) > 0) {
            precoMedio = a.getCustoTotal().divide(a.getQuantidade(), 2, RoundingMode.HALF_UP);
        }

        BigDecimal lucroPrejuizo = BigDecimal.ZERO;
        BigDecimal rentabilidade = BigDecimal.ZERO;
        if (a.getValorAtual() != null && a.getQuantidade().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal valorMercado = a.getValorAtual().multiply(a.getQuantidade());
            lucroPrejuizo = valorMercado.subtract(a.getCustoTotal());
            if (a.getCustoTotal().compareTo(BigDecimal.ZERO) > 0) {
                rentabilidade = lucroPrejuizo.divide(a.getCustoTotal(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            }
        }

        return AtivoResponse.builder()
            .id(a.getId())
            .ticker(a.getTicker())
            .nome(a.getNome())
            .tipo(a.getTipo().name())
            .quantidade(a.getQuantidade())
            .custoTotal(a.getCustoTotal())
            .valorAtual(a.getValorAtual())
            .precoMedio(precoMedio)
            .lucroPrejuizo(lucroPrejuizo)
            .rentabilidade(rentabilidade)
            .build();
    }
}
