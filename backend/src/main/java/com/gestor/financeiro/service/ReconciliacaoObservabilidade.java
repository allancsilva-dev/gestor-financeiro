package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse.Invariante;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class ReconciliacaoObservabilidade {
    private final AtomicReference<ReconciliacaoSistemaResultado> ultima = new AtomicReference<>();

    public ReconciliacaoObservabilidade(MeterRegistry registry) {
        registry.gauge("app.reconciliation.last.execution.epoch.seconds", ultima,
                ref -> value(ref, r -> r.executadoEm().getEpochSecond()));
        registry.gauge("app.reconciliation.last.duration.milliseconds", ultima,
                ref -> value(ref, ReconciliacaoSistemaResultado::duracaoMs));
        registry.gauge("app.reconciliation.last.users", ultima,
                ref -> value(ref, ReconciliacaoSistemaResultado::usuarios));
        registry.gauge("app.reconciliation.last.checks", ultima,
                ref -> value(ref, ReconciliacaoSistemaResultado::verificacoes));
        registry.gauge("app.reconciliation.last.divergences", ultima,
                ref -> value(ref, ReconciliacaoSistemaResultado::divergencias));
        registry.gauge("app.reconciliation.last.errors", ultima,
                ref -> value(ref, ReconciliacaoSistemaResultado::erros));
        for (Invariante invariante : Invariante.values()) {
            registry.gauge("app.reconciliation.last.invariant.checks", Tags.of("invariant", invariante.name()),
                    ultima, ref -> invariant(ref, invariante, true));
            registry.gauge("app.reconciliation.last.invariant.divergences", Tags.of("invariant", invariante.name()),
                    ultima, ref -> invariant(ref, invariante, false));
        }
    }

    public ReconciliacaoSistemaResultado ultima() {
        return ultima.get();
    }

    public void registrar(ReconciliacaoSistemaResultado resultado) {
        ultima.set(resultado);
    }

    private double value(AtomicReference<ReconciliacaoSistemaResultado> ref,
                         java.util.function.ToLongFunction<ReconciliacaoSistemaResultado> extractor) {
        ReconciliacaoSistemaResultado result = ref.get();
        return result == null ? 0 : extractor.applyAsLong(result);
    }

    private double invariant(AtomicReference<ReconciliacaoSistemaResultado> ref,
                             Invariante invariante, boolean checks) {
        ReconciliacaoSistemaResultado result = ref.get();
        if (result == null) return 0;
        ReconciliacaoSistemaResultado.TotaisInvariante total = result.porInvariante().get(invariante);
        if (total == null) return 0;
        return checks ? total.verificacoes() : total.divergencias();
    }
}
