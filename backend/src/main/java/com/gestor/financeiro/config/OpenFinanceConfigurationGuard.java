package com.gestor.financeiro.config;

import com.gestor.financeiro.service.openfinance.OpenFinanceCrypto;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Fail-closed de boot para o conector regulado (ADR-0020).
 *
 * <p>Derruba o contexto em {@code prod}/{@code vps} quando a feature está ligada sem o que a torna
 * segura. Falhar no boot é deliberado: a alternativa é subir e só descobrir a configuração faltante
 * quando um titular já tiver consentido, com credencial de terceiro em jogo.</p>
 *
 * <p>Fora de produção nada é derrubado — o desenvolvimento roda contra provedor fake, sem segredo
 * real, e travar o boot ali só atrapalharia.</p>
 */
@Component
public class OpenFinanceConfigurationGuard {

    private final Environment environment;
    private final OpenFinanceCrypto crypto;

    @Value("${openfinance.enabled:false}") boolean enabled;
    @Value("${openfinance.data-policy-accepted:false}") boolean politicaAceita;
    @Value("${openfinance.provider:}") String provedor;
    @Value("${openfinance.hmac-secret:}") String hmacSecret;
    @Value("${openfinance.oauth.redirect-uri:}") String redirectUri;

    public OpenFinanceConfigurationGuard(Environment environment, OpenFinanceCrypto crypto) {
        this.environment = environment;
        this.crypto = crypto;
    }

    @PostConstruct
    void validate() {
        boolean producao = Arrays.asList(environment.getActiveProfiles()).contains("prod")
                || Arrays.asList(environment.getActiveProfiles()).contains("vps");
        if (!producao || !enabled) return;

        if (!crypto.configurada()) {
            throw new IllegalStateException("Open Finance habilitado sem chave de cifra na versão corrente");
        }
        if (hmacSecret.isBlank()) {
            throw new IllegalStateException("Open Finance habilitado sem segredo HMAC");
        }
        if (!politicaAceita) {
            throw new IllegalStateException("Open Finance habilitado sem política de dados aceita");
        }
        if (provedor.isBlank()) {
            throw new IllegalStateException("Open Finance habilitado sem provedor configurado");
        }
        // Redirect fixo em property é o que impede o callback de aceitar destino vindo do request.
        if (redirectUri.isBlank() || !redirectUri.startsWith("https://")) {
            throw new IllegalStateException("Open Finance habilitado sem redirect_uri HTTPS fixa");
        }
    }
}
