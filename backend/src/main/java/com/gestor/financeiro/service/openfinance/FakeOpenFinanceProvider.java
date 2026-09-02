package com.gestor.financeiro.service.openfinance;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provedor determinístico para desenvolver e testar a fase inteira sem parceiro contratado.
 *
 * <p>Nunca é bean fora do profile {@code local-e2e} — a decisão de não contratar agregador
 * (PR-F6-01) só se sustenta se dá para exercitar sincronização, backfill, paginação e os modos de
 * falha aqui dentro.</p>
 *
 * <p>Determinismo é requisito, não conveniência: a mesma janela buscada duas vezes precisa produzir
 * exatamente os mesmos fatos, na mesma ordem, senão o snapshot muda de hash e a detecção de replay
 * do pipeline canônico deixa de valer.</p>
 */
public class FakeOpenFinanceProvider implements OpenFinanceProvider {

    public static final String CODIGO = "FAKE";
    /** Conta cujo id de transação muda a cada chamada, para exercitar o parceiro instável. */
    public static final String CONTA_ID_INSTAVEL = "fake-conta-instavel";
    /** Conta que pede espera na primeira chamada, para exercitar o reagendamento sem perder tentativa. */
    public static final String CONTA_COM_ESPERA = "fake-conta-429";
    /** Conta cujo consentimento o parceiro considera inválido. */
    public static final String CONTA_SEM_CONSENTIMENTO = "fake-conta-sem-consentimento";

    private static final int POR_PAGINA = 2;
    private static final String INSTITUICAO_MOEDA = "BRL";

    private final AtomicInteger chamadasComEspera = new AtomicInteger();
    private final AtomicInteger sequenciaInstavel = new AtomicInteger();
    private final Clock clock;

    /** Clock de negócio (ADR-0003), não `now()` solto: o fake existe para ser reprodutível. */
    public FakeOpenFinanceProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public PaginaContas contas(ContextoProvedor contexto, String cursor) {
        return new PaginaContas(List.of(
                new ContaRemota("fake-conta-corrente", "CORRENTE", "1234", INSTITUICAO_MOEDA),
                new ContaRemota("fake-conta-cartao", "CARTAO", "9876", INSTITUICAO_MOEDA)), null);
    }

    @Override
    public PaginaTransacoes transacoes(ContextoProvedor contexto, String contaExterna,
                                       LocalDate inicio, LocalDate fim, String cursor) {
        if (CONTA_SEM_CONSENTIMENTO.equals(contaExterna)) {
            throw new ConsentimentoInvalidoException("Consentimento recusado pelo parceiro");
        }
        if (CONTA_COM_ESPERA.equals(contaExterna) && chamadasComEspera.getAndIncrement() == 0) {
            throw new RetryAfterException(30);
        }

        List<TransacaoRemota> todas = new ArrayList<>();
        int indice = 0;
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            todas.add(fato(contaExterna, dia, indice++));
        }

        int desde = cursor == null || cursor.isBlank() ? 0 : Integer.parseInt(cursor);
        if (desde >= todas.size()) return new PaginaTransacoes(List.of(), null);
        int ate = Math.min(desde + POR_PAGINA, todas.size());
        String proximo = ate >= todas.size() ? null : String.valueOf(ate);
        return new PaginaTransacoes(List.copyOf(todas.subList(desde, ate)), proximo);
    }

    @Override
    public SaldoRemoto saldos(ContextoProvedor contexto, String contaExterna) {
        // Contábil e disponível diferentes de propósito: a conciliação usa o contábil (ADR-0021), e
        // conciliar contra o disponível produziria divergência permanente.
        return new SaldoRemoto(LocalDate.now(clock).atStartOfDay().atOffset(ZoneOffset.UTC).toString(),
                new BigDecimal("1000.00"), new BigDecimal("950.00"), new BigDecimal("5000.00"));
    }

    @Override
    public void revogar(ContextoProvedor contexto) {
        // Revogação no fake é silenciosa; o que importa é o chamador tratar a falha remota.
    }

    /**
     * Um fato por dia da janela, com valor derivado da data — reprodutível entre execuções e entre
     * máquinas, sem depender de relógio nem de sorteio.
     */
    private TransacaoRemota fato(String contaExterna, LocalDate dia, int indice) {
        String id = CONTA_ID_INSTAVEL.equals(contaExterna)
                ? "instavel-" + sequenciaInstavel.incrementAndGet()
                : "fake-" + contaExterna + "-" + dia;
        BigDecimal valor = new BigDecimal(dia.getDayOfMonth() + ".50").negate();
        // Um dos dias vem não efetivado, para o fetcher ter o que descartar (ADR-0021).
        boolean efetivada = dia.getDayOfMonth() % 7 != 0;
        return new TransacaoRemota(id, dia.atTime(10, 0).atOffset(ZoneOffset.UTC).toString(),
                "Compra " + dia, valor, INSTITUICAO_MOEDA, efetivada);
    }
}
