package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFailureCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ImportObservability {
    private final MeterRegistry registry;

    public ImportObservability(MeterRegistry registry) {
        this.registry = registry;
    }

    public void transition(ImportBatchStatus target) {
        registry.counter("app.import.batch.transitions", "status", target.name()).increment();
    }

    public void failure(ImportFailureCode code) {
        registry.counter("app.import.batch.failures", "code",
                code == null ? ImportFailureCode.UNKNOWN.name() : code.name()).increment();
    }
}
