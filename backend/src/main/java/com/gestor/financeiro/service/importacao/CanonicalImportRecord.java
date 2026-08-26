package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.model.enums.ImportRecordReasonCode;
import com.gestor.financeiro.model.enums.ImportRecordStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Registro normalizado, independente de CSV, OFX ou futuro conector. */
public record CanonicalImportRecord(
        int sourceLine,
        String externalId,
        String fingerprint,
        LocalDate occurredOn,
        String description,
        BigDecimal amount,
        String currency,
        TipoTransacao direction,
        ImportRecordStatus status,
        ImportRecordReasonCode reasonCode
) {
}
