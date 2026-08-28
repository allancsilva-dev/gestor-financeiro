package com.gestor.financeiro.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;

@Component
public class AssistantExternalConfigurationGuard {
    private final Environment environment;
    @Value("${assistant.external.enabled:false}") boolean enabled;
    @Value("${assistant.external.billing-confirmed:false}") boolean billing;
    @Value("${assistant.external.data-policy-accepted:false}") boolean dataPolicy;
    @Value("${assistant.limits.global-cost-usd-per-day:0}") BigDecimal costLimit;
    @Value("${assistant.gemini.api-key:}") String geminiKey;
    @Value("${assistant.openai.api-key:}") String openAiKey;
    @Value("${assistant.whatsapp.enabled:false}") boolean whatsappEnabled;
    @Value("${assistant.whatsapp.verify-token:}") String whatsappVerifyToken;
    @Value("${assistant.whatsapp.app-secret:}") String metaAppSecret;
    @Value("${assistant.whatsapp.access-token:}") String metaAccessToken;
    @Value("${assistant.whatsapp.phone-number-id:}") String metaPhoneNumberId;
    @Value("${assistant.whatsapp.graph-version:}") String metaGraphVersion;
    @Value("${assistant.whatsapp.encryption-key:}") String whatsappEncryptionKey;
    @Value("${assistant.whatsapp.hmac-secret:}") String whatsappHmacSecret;

    public AssistantExternalConfigurationGuard(Environment environment) { this.environment = environment; }

    @PostConstruct
    void validate() {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod")
                || Arrays.asList(environment.getActiveProfiles()).contains("vps");
        if (production && enabled && (!billing || !dataPolicy || costLimit.signum() <= 0
                || geminiKey.isBlank() || openAiKey.isBlank())) {
            throw new IllegalStateException("Assistente externo habilitado sem billing, política de dados, chaves e teto de custo");
        }
        if (production && whatsappEnabled && (whatsappVerifyToken.isBlank() || metaAppSecret.isBlank()
                || metaAccessToken.isBlank() || metaPhoneNumberId.isBlank() || metaGraphVersion.isBlank()
                || whatsappEncryptionKey.isBlank() || whatsappHmacSecret.isBlank())) {
            throw new IllegalStateException("WhatsApp habilitado sem verificação, Meta Cloud API e chaves de proteção");
        }
    }
}
