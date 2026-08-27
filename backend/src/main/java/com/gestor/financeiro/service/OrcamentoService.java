package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.OrcamentoCategoria;
import com.gestor.financeiro.model.OrcamentoMensal;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.PoliticaRollover;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrcamentoService {
    private final java.time.Clock clock;
    private final OrcamentoMensalRepository orcamentoMensalRepository;
    private final OrcamentoCategoriaRepository orcamentoCategoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrcamentoGastoService orcamentoGastoService;
    private final com.gestor.financeiro.service.orcamento.OrcamentoFechamentoService fechamentoService;

    public OrcamentoResponse buscarOuCriarAtual(Long usuarioId) {
        YearMonth ym = YearMonth.now(clock);
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
            oc.setPoliticaRollover(politicaDe(catReq.getPoliticaRollover()));
            orcamentoCategoriaRepository.save(oc);

            totalPlanejado = totalPlanejado.add(catReq.getValorLimite());
        }

        orcamento.setValorTotalPlanejado(totalPlanejado);
        OrcamentoMensal saved = orcamentoMensalRepository.save(orcamento);
        return toResponse(saved, usuarioId);
    }

    /** Política ausente ou desconhecida vale NONE: nunca inventar carregamento que o dono não pediu. */
    private PoliticaRollover politicaDe(String valor) {
        if (valor == null || valor.isBlank()) return PoliticaRollover.NONE;
        try {
            return PoliticaRollover.valueOf(valor.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException desconhecida) {
            throw new com.gestor.financeiro.exception.BusinessException("Política de rollover inválida");
        }
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

        YearMonth competencia = YearMonth.of(orcamento.getAno(), orcamento.getMes());
        for (OrcamentoCategoria oc : categorias) {
            Categoria cat = oc.getCategoria();
            BigDecimal gasto = gastosPorCategoria.getOrDefault(cat.getId(), BigDecimal.ZERO);
            totalGasto = totalGasto.add(gasto);

            // O limite do mês é o planejado mais o que veio de trás: é contra o disponível que o
            // percentual é medido, senão a barra mentiria justamente no mês em que houve carry.
            BigDecimal carryIn = fechamentoService.carryIn(usuarioId, cat.getId(), competencia);
            BigDecimal disponivel = oc.getValorLimite().add(carryIn);

            int percentual = disponivel.signum() <= 0 ? 0
                    : gasto.multiply(BigDecimal.valueOf(100)).divide(disponivel, 0, RoundingMode.HALF_UP).intValue();

            catResponses.add(new OrcamentoCategoriaResponse(
                    oc.getId(),
                    cat.getId(),
                    cat.getNome(),
                    cat.getCor() != null ? cat.getCor() : "#6B7280",
                    cat.getIcone() != null ? cat.getIcone() : "",
                    oc.getValorLimite(),
                    gasto,
                    percentual,
                    carryIn,
                    disponivel,
                    (oc.getPoliticaRollover() == null ? PoliticaRollover.NONE : oc.getPoliticaRollover()).name()
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

    /** Delegado ao serviço canônico: a regra de competência tem um dono só (OrcamentoGastoService). */
    private Map<Long, BigDecimal> carregarGastosDoMes(Long usuarioId, Integer mes, Integer ano) {
        return orcamentoGastoService.porCategoria(usuarioId, YearMonth.of(ano, mes));
    }
}
