package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.OrcamentoCategoria;
import com.gestor.financeiro.model.OrcamentoMensal;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrcamentoService {

    @Autowired
    private OrcamentoMensalRepository orcamentoMensalRepository;

    @Autowired
    private OrcamentoCategoriaRepository orcamentoCategoriaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public OrcamentoResponse buscarOuCriarAtual(Long usuarioId) {
        YearMonth ym = YearMonth.now();
        return orcamentoMensalRepository.findByUsuarioIdAndMesAndAno(usuarioId, ym.getMonthValue(), ym.getYear())
                .map(o -> toResponse(o, usuarioId))
                .orElseGet(() -> criarVazio(usuarioId, ym.getMonthValue(), ym.getYear()));
    }

    public OrcamentoResponse buscarPorMes(Long usuarioId, Integer mes, Integer ano) {
        return orcamentoMensalRepository.findByUsuarioIdAndMesAndAno(usuarioId, mes, ano)
                .map(o -> toResponse(o, usuarioId))
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado para " + mes + "/" + ano));
    }

    @Transactional
    public OrcamentoResponse criarOuAtualizar(Long usuarioId, OrcamentoRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        OrcamentoMensal orcamento = orcamentoMensalRepository
                .findByUsuarioIdAndMesAndAno(usuarioId, request.getMes(), request.getAno())
                .orElseGet(() -> {
                    OrcamentoMensal novo = new OrcamentoMensal();
                    novo.setUsuario(usuario);
                    novo.setMes(request.getMes());
                    novo.setAno(request.getAno());
                    return novo;
                });

        List<OrcamentoCategoriaRequest> categoriasRequest = request.getCategorias() != null
                ? request.getCategorias()
                : Collections.emptyList();

        orcamentoMensalRepository.save(orcamento);
        orcamentoCategoriaRepository.deleteByOrcamentoId(orcamento.getId());
        orcamentoCategoriaRepository.flush();

        BigDecimal totalPlanejado = BigDecimal.ZERO;
        for (OrcamentoCategoriaRequest catReq : categoriasRequest) {
            Categoria categoria = categoriaRepository.findByIdAndUsuarioId(catReq.getCategoriaId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));

            OrcamentoCategoria oc = new OrcamentoCategoria();
            oc.setOrcamento(orcamento);
            oc.setCategoria(categoria);
            oc.setValorLimite(catReq.getValorLimite());
            oc.setAtivo(true);
            orcamentoCategoriaRepository.save(oc);

            totalPlanejado = totalPlanejado.add(catReq.getValorLimite());
        }

        orcamento.setValorTotalPlanejado(totalPlanejado);
        OrcamentoMensal saved = orcamentoMensalRepository.save(orcamento);
        return toResponse(saved, usuarioId);
    }

    private OrcamentoResponse criarVazio(Long usuarioId, Integer mes, Integer ano) {
        Map<Long, BigDecimal> gastosPorCategoria = carregarGastosDoMes(usuarioId, mes, ano);
        return new OrcamentoResponse(null, mes, ano, BigDecimal.ZERO, BigDecimal.ZERO, Collections.emptyList());
    }

    private OrcamentoResponse toResponse(OrcamentoMensal orcamento, Long usuarioId) {
        List<OrcamentoCategoria> categorias = orcamentoCategoriaRepository
                .findByOrcamentoIdAndAtivoTrue(orcamento.getId());

        Map<Long, BigDecimal> gastosPorCategoria = carregarGastosDoMes(usuarioId, orcamento.getMes(), orcamento.getAno());

        BigDecimal totalGasto = BigDecimal.ZERO;
        List<OrcamentoCategoriaResponse> catResponses = new ArrayList<>();

        for (OrcamentoCategoria oc : categorias) {
            Categoria cat = oc.getCategoria();
            BigDecimal gasto = gastosPorCategoria.getOrDefault(cat.getId(), BigDecimal.ZERO);
            totalGasto = totalGasto.add(gasto);

            int percentual = BigDecimal.ZERO.compareTo(oc.getValorLimite()) == 0 ? 0
                    : gasto.multiply(BigDecimal.valueOf(100)).divide(oc.getValorLimite(), 0, RoundingMode.HALF_UP).intValue();

            catResponses.add(new OrcamentoCategoriaResponse(
                    oc.getId(),
                    cat.getId(),
                    cat.getNome(),
                    cat.getCor() != null ? cat.getCor() : "#6B7280",
                    cat.getIcone() != null ? cat.getIcone() : "",
                    oc.getValorLimite(),
                    gasto,
                    percentual
            ));
        }

        return new OrcamentoResponse(
                orcamento.getId(),
                orcamento.getMes(),
                orcamento.getAno(),
                orcamento.getValorTotalPlanejado(),
                totalGasto,
                catResponses
        );
    }

    private Map<Long, BigDecimal> carregarGastosDoMes(Long usuarioId, Integer mes, Integer ano) {
        YearMonth ym = YearMonth.of(ano, mes);
        LocalDate inicio = ym.atDay(1);
        LocalDate fim = ym.atEndOfMonth();

        List<Object[]> gastos = transacaoRepository.sumValorEfetivoAgrupadoPorCategoria(
                usuarioId, TipoTransacao.SAIDA, inicio, fim);

        Map<Long, BigDecimal> mapa = new HashMap<>();
        for (Object[] row : gastos) {
            String categoriaNome = (String) row[0];
            BigDecimal valor = (BigDecimal) row[1];

            categoriaRepository.findByUsuarioIdAndNomeIgnoreCase(usuarioId, categoriaNome)
                    .ifPresent(cat -> mapa.merge(cat.getId(), valor, BigDecimal::add));
        }
        return mapa;
    }
}
