package com.gestor.financeiro.exception;

public class LegacyImportDisabledException extends RuntimeException {
    public LegacyImportDisabledException() {
        super("Importação CSV direta desativada; use o fluxo de revisão");
    }
}
