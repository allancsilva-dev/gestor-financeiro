package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.model.Ativo;
import com.gestor.financeiro.model.MovimentacaoAtivo;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoAtivo;
import com.gestor.financeiro.model.enums.TipoMovimentacao;
import com.gestor.financeiro.repository.AtivoRepository;
import com.gestor.financeiro.repository.MovimentacaoAtivoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InvestimentoService {

    @Autowired
    private AtivoRepository ativoRepository;

    @Autowired
    private MovimentacaoAtivoRepository movimentacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

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
            .orElseThrow(() -> new RuntimeException("Ativo nao encontrado"));
        ativo.setTicker(request.getTicker().toUpperCase());
        ativo.setNome(request.getNome());
        ativo.setTipo(TipoAtivo.valueOf(request.getTipo()));
        ativo.setValorAtual(request.getValorAtual());
        return toResponse(ativoRepository.save(ativo));
    }

    @Transactional
    public void deletarAtivo(Long usuarioId, Long ativoId) {
        Ativo ativo = ativoRepository.findByIdAndUsuarioId(ativoId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Ativo nao encontrado"));
        ativoRepository.delete(ativo);
    }

    @Transactional
    public MovimentacaoResponse adicionarMovimentacao(Long usuarioId, Long ativoId, MovimentacaoRequest request) {
        Ativo ativo = ativoRepository.findByIdAndUsuarioId(ativoId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Ativo nao encontrado"));
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();

        TipoMovimentacao tipo = TipoMovimentacao.valueOf(request.getTipo());
        BigDecimal valorTotal = request.getQuantidade().multiply(request.getPrecoUnitario());

        MovimentacaoAtivo mov = new MovimentacaoAtivo();
        mov.setAtivo(ativo);
        mov.setUsuario(usuario);
        mov.setTipo(tipo);
        mov.setData(request.getData());
        mov.setQuantidade(request.getQuantidade());
        mov.setPrecoUnitario(request.getPrecoUnitario());
        mov.setValorTotal(valorTotal);
        mov = movimentacaoRepository.save(mov);

        updateAtivoPosicao(ativo, tipo, request.getQuantidade(), valorTotal);

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
        if (tipo == TipoMovimentacao.COMPRA || tipo == TipoMovimentacao.BONIFICACAO) {
            ativo.setQuantidade(ativo.getQuantidade().add(qtd));
            ativo.setCustoTotal(ativo.getCustoTotal().add(valor));
        } else if (tipo == TipoMovimentacao.VENDA) {
            BigDecimal precoMedio = ativo.getCustoTotal().divide(ativo.getQuantidade(), 8, RoundingMode.HALF_UP);
            BigDecimal custoVendido = precoMedio.multiply(qtd);
            ativo.setQuantidade(ativo.getQuantidade().subtract(qtd));
            ativo.setCustoTotal(ativo.getCustoTotal().subtract(custoVendido).max(BigDecimal.ZERO));
        }
        ativoRepository.save(ativo);
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
