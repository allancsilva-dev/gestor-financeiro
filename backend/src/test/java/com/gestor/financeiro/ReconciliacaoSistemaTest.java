package com.gestor.financeiro;

import com.gestor.financeiro.config.ReconciliacaoHealthIndicator;
import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.ReconciliacaoGlobalService;
import com.gestor.financeiro.service.ReconciliacaoObservabilidade;
import com.gestor.financeiro.service.ReconciliacaoScheduler;
import com.gestor.financeiro.service.ReconciliacaoSistemaResultado;
import com.gestor.financeiro.service.ReconciliacaoSistemaService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliacaoSistemaTest {
    private static final Instant NOW = Instant.parse("2026-07-16T03:30:00Z");

    @Test
    void relatorioPorUsuarioDeclaraSnapshotReadOnlyRepeatableRead() throws Exception {
        Transactional transactional = ReconciliacaoGlobalService.class
                .getMethod("reconciliarUsuario", Long.class).getAnnotation(Transactional.class);
        assertTrue(transactional.readOnly());
        assertEquals(Isolation.REPEATABLE_READ, transactional.isolation());
    }

    @Test
    void paginaPorKeysetEContinuaDepoisDeFalhaIsolada() {
        UsuarioRepository users = mock(UsuarioRepository.class);
        ReconciliacaoGlobalService perUser = mock(ReconciliacaoGlobalService.class);
        ReconciliacaoObservabilidade metrics = new ReconciliacaoObservabilidade(new SimpleMeterRegistry());
        when(users.findIdsAfter(anyLong(), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L), List.of(3L), List.of());
        when(perUser.reconciliarUsuario(1L)).thenThrow(new IllegalStateException("falha isolada"));
        when(perUser.reconciliarUsuario(2L)).thenReturn(ok(2));
        when(perUser.reconciliarUsuario(3L)).thenReturn(ok(1));
        ReconciliacaoSistemaService service = new ReconciliacaoSistemaService(users, perUser, metrics,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "batchSize", 2);

        ReconciliacaoSistemaResultado result = service.executar();
        assertEquals(3, result.usuarios());
        assertEquals(1, result.erros());
        assertEquals(3, result.verificacoes());
        verify(perUser).reconciliarUsuario(3L);
        assertEquals(result, metrics.ultima());
    }

    @Test
    void healthEMetricasUsamUnknownUpEDegradedSemTagsDeUsuario() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReconciliacaoObservabilidade metrics = new ReconciliacaoObservabilidade(registry);
        ReconciliacaoHealthIndicator health = new ReconciliacaoHealthIndicator(metrics);
        assertEquals(Status.UNKNOWN, health.health().getStatus());

        metrics.registrar(system(0, 0));
        assertEquals(Status.UP, health.health().getStatus());
        metrics.registrar(system(1, 0));
        assertEquals(ReconciliacaoHealthIndicator.DEGRADED, health.health().getStatus());
        // Duas métricas por invariante; são cinco invariantes.
        assertEquals(10, registry.find("app.reconciliation.last.invariant.checks").meters().size()
                + registry.find("app.reconciliation.last.invariant.divergences").meters().size());
        assertTrue(registry.getMeters().stream().flatMap(m -> m.getId().getTags().stream())
                .allMatch(tag -> tag.getKey().equals("invariant")));
    }

    @Test
    void schedulerIgnoraExecucaoSobreposta() throws Exception {
        ReconciliacaoSistemaService service = mock(ReconciliacaoSistemaService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.executar()).thenAnswer(invocation -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return system(0, 0);
        });
        ReconciliacaoScheduler scheduler = new ReconciliacaoScheduler(service);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(scheduler::executar);
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            scheduler.executar();
            release.countDown();
        } finally {
            release.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
        verify(service, times(1)).executar();
    }

    private ReconciliacaoGlobalResponse ok(long checks) {
        return new ReconciliacaoGlobalResponse(ReconciliacaoGlobalResponse.Status.OK, NOW, checks, 0,
                List.of(new ReconciliacaoGlobalResponse.ResumoInvariante(
                        ReconciliacaoGlobalResponse.Invariante.SALDO_LEDGER, checks, checks, 0)), List.of());
    }

    private ReconciliacaoSistemaResultado system(long divergences, long errors) {
        Map<ReconciliacaoGlobalResponse.Invariante, ReconciliacaoSistemaResultado.TotaisInvariante> totals =
                new EnumMap<>(ReconciliacaoGlobalResponse.Invariante.class);
        for (var invariant : ReconciliacaoGlobalResponse.Invariante.values()) {
            totals.put(invariant, new ReconciliacaoSistemaResultado.TotaisInvariante(1,
                    invariant == ReconciliacaoGlobalResponse.Invariante.SALDO_LEDGER ? divergences : 0));
        }
        return new ReconciliacaoSistemaResultado(NOW, 10, 1, 4, divergences, errors,
                Map.copyOf(totals), List.of());
    }
}
