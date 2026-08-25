package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Limpeza diária de refresh tokens expirados.
 *
 * <p>{@code limparTokensExpirados()} existia sem nenhum caller, então a tabela
 * {@code refresh_tokens} crescia sem limite — e a janela de 30 dias com rotação
 * a cada refresh gera uma linha revogada por renovação.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.refresh-token.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class RefreshTokenScheduler {

    private final RefreshTokenService refreshTokenService;
    private final AtomicBoolean executando = new AtomicBoolean();

    @Scheduled(cron = "${app.refresh-token.cleanup.cron:0 15 3 * * *}",
            zone = "${app.business.timezone:America/Sao_Paulo}")
    public void limpar() {
        if (!executando.compareAndSet(false, true)) {
            log.warn("refresh_token_cleanup_sobreposicao_ignorada");
            return;
        }
        try {
            int removidos = refreshTokenService.limparTokensExpirados();
            log.info("refresh_token_cleanup_concluido removidos={}", removidos);
        } finally {
            executando.set(false);
        }
    }
}
