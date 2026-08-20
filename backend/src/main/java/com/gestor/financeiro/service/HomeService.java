package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.CategoriaResumoDto;
import com.gestor.financeiro.dto.HomeResponse;
import com.gestor.financeiro.dto.ParcelaAgendadaDto;
import com.gestor.financeiro.dto.RelatorioResponse;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import com.gestor.financeiro.repository.FaturaLancamentoRepository;
import com.gestor.financeiro.service.CompromissosService.CompromissoItem;
import com.gestor.financeiro.service.MetricasService.Metricas;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Agregador da home. Nao calcula nada por conta propria: compoe metricas,
 * compromissos, relatorio do mes, categorias e notificacoes a partir dos
 * servicos que ja sao a fonte oficial de cada numero.
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    /** Teto de chips de categoria no filtro de operacoes. */
    private static final int MAXIMO_CATEGORIAS = 30;

    private final MetricasService metricasService;
    private final CompromissosService compromissosService;
    private final RelatorioService relatorioService;
    private final CategoriaService categoriaService;
    private final NotificacaoService notificacaoService;
    private final FaturaLancamentoRepository faturaLancamentoRepository;
    private final Clock clock;

    @Transactional
    public HomeResponse montar(Long usuarioId) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        LocalDate fimMes = hoje.withDayOfMonth(hoje.lengthOfMonth());

        Metricas metricas = metricasService.calcular(usuarioId);
        List<CompromissoItem> itens = compromissosService.listar(usuarioId, null).itens();

        BigDecimal totalFaturas = somar(itens, "FATURA");

        // Compra parcelada no cartao nao vira linha em `parcelas` desde a V36:
        // ela mora nos lancamentos das faturas seguintes.
        List<ParcelaAgendadaDto> parcelas = faturaLancamentoRepository
                .findParcelasAgendadas(usuarioId, TipoFaturaLancamento.COMPRA, FaturaStatus.PAGA, hoje)
                .stream()
                .map(fl -> ParcelaAgendadaDto.fromEntity(fl, hoje))
                .toList();

        RelatorioResponse relatorio = relatorioService.gerarRelatorio(usuarioId, inicioMes, fimMes);

        List<CategoriaResumoDto> categorias = categoriaService
                .listarMinhasCategorias(PageRequest.of(0, MAXIMO_CATEGORIAS))
                .map(CategoriaResumoDto::fromEntity)
                .getContent();

        // Sincroniza na abertura da home: idempotente por chave, nao duplica
        long naoLidas = notificacaoService.sincronizar(usuarioId);

        return new HomeResponse(
                metricas,
                metricas.disponivelAgora(),
                totalFaturas,
                nvl(relatorio.totalEntradas()),
                nvl(relatorio.totalSaidas()),
                totalFaturas,
                parcelas,
                categorias,
                naoLidas);
    }

    private static BigDecimal somar(List<CompromissoItem> itens, String tipo) {
        return itens.stream()
                .filter(i -> tipo.equals(i.tipo()))
                .map(i -> nvl(i.valor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
