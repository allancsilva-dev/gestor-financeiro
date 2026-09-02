package com.gestor.financeiro.service.openfinance;

import com.gestor.financeiro.config.LocalE2eOpenFinanceConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O fake é o que sustenta a decisão de não contratar agregador nesta fase: se ele não exercitar
 * paginação, determinismo e os modos de falha, a sincronização não tem como ser desenvolvida nem
 * provada sem parceiro.
 */
class FakeOpenFinanceProviderTest {

    private static final OpenFinanceProvider.ContextoProvedor CONTEXTO =
            new OpenFinanceProvider.ContextoProvedor(1L, 1L, "conn-1", "token");

    private final LocalDate inicio = LocalDate.of(2026, 8, 1);
    private final LocalDate fim = LocalDate.of(2026, 8, 5);

    private FakeOpenFinanceProvider novoProvider() {
        return new FakeOpenFinanceProvider(
                java.time.Clock.fixed(java.time.Instant.parse("2026-09-01T12:00:00Z"), java.time.ZoneOffset.UTC));
    }

    private List<OpenFinanceProvider.TransacaoRemota> todas(FakeOpenFinanceProvider provider, String conta) {
        List<OpenFinanceProvider.TransacaoRemota> acumulado = new ArrayList<>();
        String cursor = null;
        int paginas = 0;
        do {
            OpenFinanceProvider.PaginaTransacoes pagina =
                    provider.transacoes(CONTEXTO, conta, inicio, fim, cursor);
            acumulado.addAll(pagina.itens());
            cursor = pagina.proximoCursor();
            paginas++;
        } while (cursor != null && paginas < 50);
        return acumulado;
    }

    @Test
    void pagina() {
        FakeOpenFinanceProvider provider = novoProvider();
        OpenFinanceProvider.PaginaTransacoes primeira =
                provider.transacoes(CONTEXTO, "fake-conta-corrente", inicio, fim, null);

        assertEquals(2, primeira.itens().size());
        assertNotNull(primeira.proximoCursor(), "janela de 5 dias não cabe em uma página");
        assertEquals(5, todas(provider, "fake-conta-corrente").size());
    }

    /**
     * Determinismo é o que faz o snapshot ter sempre o mesmo hash, e é o que transforma replay em
     * detecção barata no pipeline canônico. Sem isto, a Fase 6 perde a prova auditável.
     */
    @Test
    void mesmaJanelaDuasVezesProduzOsMesmosFatosNaMesmaOrdem() {
        List<OpenFinanceProvider.TransacaoRemota> primeira =
                todas(novoProvider(), "fake-conta-corrente");
        List<OpenFinanceProvider.TransacaoRemota> segunda =
                todas(novoProvider(), "fake-conta-corrente");

        assertEquals(primeira, segunda);
    }

    /**
     * Espelha o risco número um da fase: parceiro cujo identificador não é estável entre chamadas.
     * Sem uma conta que reproduza isso, a defesa contra duplicação no ledger nunca seria testada.
     */
    @Test
    void contaInstavelTrocaDeIdentificadorEntreChamadas() {
        FakeOpenFinanceProvider provider = novoProvider();
        String primeiro = todas(provider, FakeOpenFinanceProvider.CONTA_ID_INSTAVEL).get(0).externalId();
        String segundo = todas(provider, FakeOpenFinanceProvider.CONTA_ID_INSTAVEL).get(0).externalId();

        assertFalse(primeiro.equals(segundo), "conta instável precisa trocar de id");
    }

    @Test
    void contaComEsperaPedeRetryAfterUmaVezESegueDepois() {
        FakeOpenFinanceProvider provider = novoProvider();
        OpenFinanceProvider.RetryAfterException espera = assertThrows(
                OpenFinanceProvider.RetryAfterException.class,
                () -> provider.transacoes(CONTEXTO, FakeOpenFinanceProvider.CONTA_COM_ESPERA, inicio, fim, null));

        assertEquals(30, espera.segundos());
        assertFalse(todas(provider, FakeOpenFinanceProvider.CONTA_COM_ESPERA).isEmpty());
    }

    @Test
    void consentimentoInvalidoEFalhaTerminal() {
        assertThrows(OpenFinanceProvider.ConsentimentoInvalidoException.class,
                () -> novoProvider().transacoes(
                        CONTEXTO, FakeOpenFinanceProvider.CONTA_SEM_CONSENTIMENTO, inicio, fim, null));
    }

    /** O fetcher precisa ter o que descartar: a regra de só ingerir fato efetivado é nossa. */
    @Test
    void janelaTrazFatoNaoEfetivadoParaOFetcherDescartar() {
        List<OpenFinanceProvider.TransacaoRemota> fatos = todas(novoProvider(),
                "fake-conta-corrente");
        List<OpenFinanceProvider.TransacaoRemota> ampla = new ArrayList<>(fatos);
        FakeOpenFinanceProvider provider = novoProvider();
        String cursor = null;
        do {
            var pagina = provider.transacoes(CONTEXTO, "fake-conta-corrente",
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), cursor);
            ampla.addAll(pagina.itens());
            cursor = pagina.proximoCursor();
        } while (cursor != null);

        assertTrue(ampla.stream().anyMatch(f -> !f.efetivada()), "janela precisa conter pendente");
        assertTrue(ampla.stream().anyMatch(OpenFinanceProvider.TransacaoRemota::efetivada));
    }

    @Test
    void saldoTrazContabilEDisponivelSeparados() {
        OpenFinanceProvider.SaldoRemoto saldo = novoProvider().saldos(CONTEXTO, "fake-conta-corrente");
        assertNotNull(saldo.saldoContabil());
        assertNotNull(saldo.saldoDisponivel());
        assertFalse(saldo.saldoContabil().compareTo(saldo.saldoDisponivel()) == 0,
                "contábil e disponível diferentes: é a diferença que diagnostica pendente");
    }

    /**
     * A garantia que sustenta tudo: o fake só existe sob {@code local-e2e}. Se alguém remover o
     * profile, um provedor de mentira poderia virar bean num ambiente com dado real.
     */
    @Test
    void fakeSoEBeanNoProfileLocalE2e() {
        Profile profile = LocalE2eOpenFinanceConfiguration.class.getAnnotation(Profile.class);
        assertNotNull(profile, "configuração do fake precisa declarar @Profile");
        assertEquals(1, profile.value().length);
        assertEquals("local-e2e", profile.value()[0]);
        assertNull(FakeOpenFinanceProvider.class.getAnnotation(org.springframework.stereotype.Component.class),
                "fake não pode ser candidato a bean por component scan");
    }
}
