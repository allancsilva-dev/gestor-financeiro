package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.CapacidadesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espelho das flags de canal para o cliente.
 *
 * Deliberadamente SEM `@ConditionalOnProperty`: o endpoint só serve para dizer que algo está
 * desligado, então ele mesmo não pode desaparecer junto. Lê exatamente as mesmas properties dos
 * controllers condicionais, para não existir uma segunda verdade sobre o que está no ar.
 */
@RestController
@RequestMapping("/api/v1/capacidades")
@Tag(name = "Capacidades", description = "Recursos ligados neste servidor")
public class CapacidadesController {

    @Value("${assistant.text.enabled:false}") boolean assistenteTexto;
    @Value("${assistant.audio.enabled:false}") boolean assistenteAudio;
    @Value("${assistant.whatsapp.enabled:false}") boolean assistenteWhatsapp;

    @GetMapping
    @Operation(summary = "Capacidades do servidor",
        description = "Diz ao cliente quais canais estão ligados, para ele não oferecer rota que responderia 404.")
    public ResponseEntity<CapacidadesResponse> capacidades() {
        return ResponseEntity.ok(new CapacidadesResponse(assistenteTexto, assistenteAudio, assistenteWhatsapp));
    }
}
