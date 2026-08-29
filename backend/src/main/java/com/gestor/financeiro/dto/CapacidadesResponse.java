package com.gestor.financeiro.dto;

/**
 * O que este servidor tem ligado agora.
 *
 * Existe porque os canais do assistente nascem desligados (ADR-0017) e os controllers
 * correspondentes são condicionais: sem a property o bean nem é criado e a rota devolve 404.
 * O app precisava saber disso antes de tentar, e a resposta anterior era uma flag de build
 * (`EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED`) — que exigia publicar APK/IPA novo a cada toggle
 * do servidor. Aqui quem responde é o servidor, em runtime.
 */
public record CapacidadesResponse(
        boolean assistenteTexto,
        boolean assistenteAudio,
        boolean assistenteWhatsapp
) {}
