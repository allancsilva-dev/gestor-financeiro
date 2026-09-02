package com.gestor.financeiro.model.enums;

/**
 * De onde o lote veio, independente de como o conteúdo é lido.
 *
 * <p>Separado de {@link ImportFormat} de propósito: formato responde "como parsear", origem
 * responde "quem trouxe". Hoje daria para inferir um do outro; no dia em que um conector entregar
 * CSV de verdade, a inferência passaria a mentir.</p>
 */
public enum ImportOrigin {
    /** Titular enviou o arquivo. */
    UPLOAD,
    /** Sincronização automática com instituição, sem ação do titular no momento. */
    CONNECTOR
}
