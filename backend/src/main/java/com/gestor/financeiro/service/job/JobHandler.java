package com.gestor.financeiro.service.job;

/**
 * Executor de um tipo de job da fila durável.
 *
 * <p>Contrato obrigatório: o handler roda <b>fora</b> da transação do claim, sem
 * {@code SecurityContext} (é {@code ThreadLocal} e não cruza thread), e precisa ser idempotente —
 * um job pode ser reexecutado quando o lease vence ou o processo morre no meio.</p>
 */
public interface JobHandler {

    /** Tipo tratado, igual ao {@code job_type} enfileirado. */
    String type();

    /**
     * Executa o trabalho. Lançar exceção sinaliza falha: a fila decide entre nova tentativa
     * (com backoff) e dead letter, conforme {@code max_attempts}.
     */
    void handle(BackgroundJob job) throws Exception;

    /**
     * Código estável de erro para o registro de falha. Fica em coluna de texto, então precisa ser
     * curto e sem PII.
     */
    default String errorCode() {
        return type() + "_FAILED";
    }
}
