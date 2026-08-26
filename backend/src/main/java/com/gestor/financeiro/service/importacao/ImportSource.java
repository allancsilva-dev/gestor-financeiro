package com.gestor.financeiro.service.importacao;

import java.io.IOException;
import java.io.InputStream;

/** Fonte limitada fornecida pela camada HTTP; conectores não recebem paths nem URLs. */
public interface ImportSource {
    InputStream openStream() throws IOException;
    long size();
    String displayName();
    String contentType();
    String sha256();
}
