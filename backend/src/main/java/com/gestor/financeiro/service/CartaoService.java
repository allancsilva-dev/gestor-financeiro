package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.LiquidezContaFinanceira;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.EstadoConciliacaoConta;
import com.gestor.financeiro.model.enums.OrigemDadosConta;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.dto.AlertaDto;
import com.gestor.financeiro.dto.CarteiraCartaoResponse;
import com.gestor.financeiro.dto.FaturaResumoDto;
import com.gestor.financeiro.model.FaturaCartao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.FaturaLancamentoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.util.FaturaDatas;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servico canonico de cartao (PR-F2-19): substitui o antigo ContaService
 * generico. Conta e somente a configuracao interna do cartao; a divida vive no
 * ledger da conta financeira PASSIVO pareada. Sem pareamento e corrupcao:
 * nenhum fallback cria a conta financeira sob demanda.
 */
@Service
@RequiredArgsConstructor
public class CartaoService {
    private final ContaRepository contaRepository;
    private final ContaFixaRepository contaFixaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CarteiraRepository carteiraRepository;
    private final FaturaCartaoRepository faturaCartaoRepository;
    private final FaturaLancamentoRepository faturaLancamentoRepository;
    private final Clock clock;

    /** Faturas por cartao devolvidas por padrao: anterior, atual e proxima. */
    private static final int MESES_PADRAO = 3;
    private static final int MESES_MAX = 12;

    public Page<Conta> listarCartoesPorUsuario(Long usuarioId, Pageable pageable) {
        return contaRepository.findByUsuarioIdAndAtivoTrue(usuarioId, pageable);
    }

    // Valida ownership para evitar IDOR em endpoints por ID.
    public Conta buscarCartaoDoUsuario(Long id, Long usuarioId) {
        return contaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));
    }

    // Cartao nasce pareado com sua conta financeira passiva (PR-F2-06)
    @Transactional
    public Conta criar(Conta cartao, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        cartao.setUsuario(usuario);
        if (cartao.getAtivo() == null) cartao.setAtivo(true);
        if (cartao.getLimiteTotal() == null) cartao.setLimiteTotal(BigDecimal.ZERO);

        Carteira passivo = new Carteira();
        passivo.setNome(cartao.getNome());
        passivo.setSubtipo(SubtipoContaFinanceira.CARTAO);
        passivo.setNatureza(NaturezaContaFinanceira.PASSIVO);
        passivo.setLiquidez(LiquidezContaFinanceira.IMEDIATA);
        passivo.setSaldo(BigDecimal.ZERO);
        passivo.setMoeda("BRL");
        passivo.setOrigemDados(OrigemDadosConta.MANUAL);
        passivo.setEstadoConciliacao(EstadoConciliacaoConta.CONCILIADA);
        passivo.setBanco(cartao.getBanco());
        passivo.setUsuario(usuario);
        cartao.setContaFinanceira(carteiraRepository.save(passivo));

        return contaRepository.save(cartao);
    }

    @Transactional
    public Conta atualizarCartao(Long id, Conta cartaoAtualizado, Long usuarioId) {
        Conta cartao = buscarCartaoDoUsuario(id, usuarioId);
        cartao.setNome(cartaoAtualizado.getNome());
        cartao.setLimiteTotal(cartaoAtualizado.getLimiteTotal());
        cartao.setDiaFechamento(cartaoAtualizado.getDiaFechamento());
        cartao.setDiaVencimento(cartaoAtualizado.getDiaVencimento());
        cartao.setCor(cartaoAtualizado.getCor());
        cartao.setBanco(cartaoAtualizado.getBanco());
        cartao.setUltimosDigitos(cartaoAtualizado.getUltimosDigitos());
        cartao.setBandeira(cartaoAtualizado.getBandeira());
        cartao.getContaFinanceira().setNome(cartaoAtualizado.getNome());
        cartao.getContaFinanceira().setBanco(cartaoAtualizado.getBanco());
        return contaRepository.save(cartao);
    }


    /**
     * Visao consolidada dos cartoes para a tela Carteira.
     *
     * Leitura pura de proposito: NAO reusa FaturaService, cujos GETs sao
     * transacionais e disparam liquidarFaturaAnterior (rollover lazy). Um
     * endpoint chamado a cada render nao pode escrever no ledger.
     */
    @Transactional(readOnly = true)
    public List<CarteiraCartaoResponse> montarCarteira(Long usuarioId, Integer meses) {
        int janela = meses == null ? MESES_PADRAO : Math.min(Math.max(meses, 1), MESES_MAX);
        List<Conta> cartoes = contaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        if (cartoes.isEmpty()) {
            return List.of();
        }

        List<Long> contaIds = cartoes.stream().map(Conta::getId).toList();
        // contaIds nunca vazio aqui; `IN ()` quebra em parte dos dialetos.
        Map<Long, List<FaturaCartao>> porCartao = new LinkedHashMap<>();
        for (FaturaCartao fatura : faturaCartaoRepository.findByContaIdsEUsuario(usuarioId, contaIds)) {
            porCartao.computeIfAbsent(fatura.getConta().getId(), k -> new ArrayList<>()).add(fatura);
        }

        // Total da fatura = soma dos lancamentos (mesma regra do detalhe). Uma
        // query agregada para todas as faturas de uma vez, sem N+1.
        List<Long> faturaIds = porCartao.values().stream()
                .flatMap(List::stream).map(FaturaCartao::getId).filter(java.util.Objects::nonNull).toList();
        Map<Long, BigDecimal> somaPorFatura = new LinkedHashMap<>();
        if (!faturaIds.isEmpty()) {
            for (Object[] linha : faturaLancamentoRepository.somarPorFatura(faturaIds)) {
                somaPorFatura.put((Long) linha[0], (BigDecimal) linha[1]);
            }
        }

        LocalDate hoje = LocalDate.now(clock);
        List<CarteiraCartaoResponse> resposta = new ArrayList<>(cartoes.size());
        for (Conta cartao : cartoes) {
            resposta.add(montarCartao(cartao, porCartao.getOrDefault(cartao.getId(), List.of()),
                    somaPorFatura, hoje, janela));
        }
        return resposta;
    }

    /**
     * Uso do limite de um cartao. Fonte unica: a tela Carteira e o aviso de estouro
     * (BACKLOG-0125) precisam do mesmo numero, ou o usuario recebe um alerta que a tela
     * nao confirma.
     */
    public record UsoLimite(Long cartaoId, String nome, BigDecimal limite,
                            BigDecimal emAberto, BigDecimal creditoAFavor, int percentualUso) {

        /**
         * Limite zero ou nulo e o default de Conta.limiteTotal e significa "nao
         * informado": avisar ali seria alarme falso para todo mundo que nunca preencheu
         * o limite do cartao.
         */
        public boolean estourado() {
            return limite != null && limite.signum() > 0 && emAberto.compareTo(limite) > 0;
        }
    }

    /** Uso do limite de todos os cartoes ativos do titular. Sem efeito colateral. */
    @Transactional(readOnly = true)
    public List<UsoLimite> usoDoLimite(Long usuarioId) {
        return contaRepository.findByUsuarioIdAndAtivoTrue(usuarioId).stream()
                .map(CartaoService::usoDoLimite)
                .toList();
    }

    /** Uso do limite de um cartao do titular; vazio se o cartao nao e dele ou nao existe. */
    @Transactional(readOnly = true)
    public Optional<UsoLimite> usoDoLimite(Long usuarioId, Long cartaoId) {
        if (cartaoId == null) return Optional.empty();
        return contaRepository.findByIdAndUsuarioId(cartaoId, usuarioId)
                .map(CartaoService::usoDoLimite);
    }

    /**
     * Alerta sincrono de limite estourado para a resposta da operacao que acabou de
     * acontecer (BACKLOG-0125). Vazio quando nao ha cartao, quando o cartao nao e do
     * titular ou quando o limite nao foi informado.
     *
     * Nunca bloqueia nada: a decisao do dono do produto e "lancar e avisar". A leitura
     * acontece depois do commit da operacao, entao ja enxerga o passivo atualizado por
     * FaturaService.espelharPassivo.
     */
    @Transactional(readOnly = true)
    public List<AlertaDto> alertasDeLimite(Long usuarioId, Long cartaoId) {
        return usoDoLimite(usuarioId, cartaoId)
                .filter(UsoLimite::estourado)
                .map(u -> List.of(new AlertaDto(
                        "LIMITE_ESTOURADO",
                        "Limite do cartão estourado",
                        "O cartão " + u.nome() + " passou do limite. A cobrança foi lançada"
                                + " normalmente e entra na fatura.",
                        NotificacaoService.DESTINO_CARTAO,
                        u.cartaoId())))
                .orElseGet(List::of);
    }

    private static UsoLimite usoDoLimite(Conta cartao) {
        BigDecimal limite = cartao.getLimiteTotal() == null ? BigDecimal.ZERO : cartao.getLimiteTotal();
        BigDecimal saldo = cartao.getContaFinanceira() == null || cartao.getContaFinanceira().getSaldo() == null
                ? BigDecimal.ZERO : cartao.getContaFinanceira().getSaldo();

        // Saldo do passivo pode ficar credor (pagamento a maior, estorno). A UI
        // precisa dos dois lados separados: nada de "Em aberto" negativo.
        BigDecimal emAberto = saldo.max(BigDecimal.ZERO);
        BigDecimal creditoAFavor = saldo.negate().max(BigDecimal.ZERO);

        int percentualUso = limite.signum() <= 0 ? 0
                : emAberto.multiply(BigDecimal.valueOf(100))
                        .divide(limite, 0, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100))
                        .intValue();

        return new UsoLimite(cartao.getId(), cartao.getNome(), limite, emAberto,
                creditoAFavor, percentualUso);
    }

    private CarteiraCartaoResponse montarCartao(Conta cartao, List<FaturaCartao> faturas,
                                                Map<Long, BigDecimal> somaPorFatura,
                                                LocalDate hoje, int janela) {
        UsoLimite uso = usoDoLimite(cartao);
        BigDecimal limite = uso.limite();
        BigDecimal emAberto = uso.emAberto();
        BigDecimal creditoAFavor = uso.creditoAFavor();
        int percentualUso = uso.percentualUso();
        // Limite disponivel usa o saldo COM sinal, nao o emAberto: credito a favor
        // aumenta o disponivel acima do limite. Comportamento preservado da versao
        // anterior a extracao de UsoLimite.
        BigDecimal saldo = emAberto.subtract(creditoAFavor);

        YearMonth competenciaAtual = YearMonth.from(hoje);
        LocalDate vencimentoAtual = FaturaDatas.vencimento(cartao, competenciaAtual);
        LocalDate melhorDia = FaturaDatas.melhorDiaCompra(cartao, hoje);

        return new CarteiraCartaoResponse(
                cartao.getId(),
                cartao.getNome(),
                cartao.getBanco(),
                cartao.getCor(),
                cartao.getUltimosDigitos(),
                cartao.getBandeira(),
                cartao.getDiaFechamento(),
                cartao.getDiaVencimento(),
                limite,
                limite.subtract(saldo),
                emAberto,
                creditoAFavor,
                percentualUso,
                vencimentoAtual,
                (int) ChronoUnit.DAYS.between(hoje, vencimentoAtual),
                melhorDia,
                (int) ChronoUnit.DAYS.between(hoje, melhorDia),
                montarFaturas(cartao, faturas, somaPorFatura, hoje, competenciaAtual, janela));
    }

    /**
     * Janela centrada na competencia atual: uma anterior e o resto para frente.
     * Competencia sem fatura materializada entra zerada, com as datas
     * calculadas — mesmo contrato do toResponseVazia do FaturaService.
     */
    private List<FaturaResumoDto> montarFaturas(Conta cartao, List<FaturaCartao> faturas,
                                                 Map<Long, BigDecimal> somaPorFatura,
                                                 LocalDate hoje, YearMonth atual, int janela) {
        Map<YearMonth, FaturaCartao> materializadas = new LinkedHashMap<>();
        for (FaturaCartao f : faturas) {
            materializadas.put(YearMonth.of(f.getAno(), f.getMes()), f);
        }

        YearMonth inicio = janela > 1 ? atual.minusMonths(1) : atual;
        List<FaturaResumoDto> resultado = new ArrayList<>(janela);
        for (int i = 0; i < janela; i++) {
            YearMonth competencia = inicio.plusMonths(i);
            FaturaCartao fatura = materializadas.get(competencia);
            resultado.add(fatura == null
                    ? vazia(cartao, competencia)
                    : resumo(fatura, competencia, somaPorFatura, hoje));
        }
        // A atual primeiro: antes a lista vinha do futuro para o passado e a
        // primeira linha era a proxima fatura, que quase nunca esta materializada
        // — o item mais visivel era justamente o que abre vazio.
        resultado.sort(Comparator
                .comparingInt((FaturaResumoDto f) -> distanciaDaAtual(f, atual))
                .thenComparing(f -> YearMonth.of(f.ano(), f.mes())));
        return resultado;
    }

    /** 0 para a competencia corrente, depois futuras, depois passadas. */
    private int distanciaDaAtual(FaturaResumoDto f, YearMonth atual) {
        int delta = (f.ano() - atual.getYear()) * 12 + (f.mes() - atual.getMonthValue());
        if (delta == 0) return 0;
        return delta > 0 ? delta : 100 - delta;
    }

    private FaturaResumoDto resumo(FaturaCartao fatura, YearMonth competencia,
                                   Map<Long, BigDecimal> somaPorFatura, LocalDate hoje) {
        BigDecimal soma = somaPorFatura.get(fatura.getId());
        // Persistido so vale quando a fatura nao tem lancamento nenhum (pre-V17).
        BigDecimal total = soma != null ? soma
                : (fatura.getValorTotal() == null ? BigDecimal.ZERO : fatura.getValorTotal());
        BigDecimal pago = fatura.getValorPago() == null ? BigDecimal.ZERO : fatura.getValorPago();
        return new FaturaResumoDto(
                fatura.getId(),
                competencia.getMonthValue(),
                competencia.getYear(),
                fatura.getDataFechamento(),
                fatura.getDataVencimento(),
                total,
                pago,
                total.subtract(pago).max(BigDecimal.ZERO),
                FaturaDatas.statusAtual(fatura, hoje).name());
    }

    private FaturaResumoDto vazia(Conta cartao, YearMonth competencia) {
        return new FaturaResumoDto(
                null,
                competencia.getMonthValue(),
                competencia.getYear(),
                FaturaDatas.fechamento(cartao, competencia),
                FaturaDatas.vencimento(cartao, competencia),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                com.gestor.financeiro.model.enums.FaturaStatus.ABERTA.name());
    }

    /**
     * Desativa o cartao e, junto, as recorrencias que cobravam nele: continuar lancando
     * assinatura na fatura de um cartao que o titular removeu seria cobranca invisivel.
     *
     * @return quantas recorrencias foram desativadas junto
     */
    @Transactional
    public int deletarCartao(Long id, Long usuarioId) {
        Conta cartao = buscarCartaoDoUsuario(id, usuarioId);
        cartao.setAtivo(false);
        contaRepository.save(cartao);
        return contaFixaRepository.desativarPorConta(cartao.getId());
    }
}
