package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.enums.ImportFormat;

public record ConnectorDetection(ImportFormat format, String institutionCode, int confidence) {
    public ConnectorDetection {
        if (confidence < 0 || confidence > 100) {
            throw new IllegalArgumentException("confidence deve estar entre 0 e 100");
        }
    }
}
