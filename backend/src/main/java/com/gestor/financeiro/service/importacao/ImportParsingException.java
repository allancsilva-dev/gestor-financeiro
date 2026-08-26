package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.enums.ImportFailureCode;
import java.io.IOException;

public final class ImportParsingException extends IOException {
    private final ImportFailureCode code;
    public ImportParsingException(ImportFailureCode code, String safeMessage) { super(safeMessage); this.code = code; }
    public ImportParsingException(ImportFailureCode code, String safeMessage, Throwable cause) { super(safeMessage, cause); this.code = code; }
    public ImportFailureCode code() { return code; }
}
