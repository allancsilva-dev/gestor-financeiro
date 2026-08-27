package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.NotificacaoResponse;
import com.gestor.financeiro.dto.RegistrarDispositivoRequest;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.NotificacaoService;
import com.gestor.financeiro.service.notificacao.PushDispositivoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Notificacoes in-app (V42): o sino da home, mais o registro de aparelho para push (V50).
 * O aviso nasce da sincronizacao diaria na fila; aqui o titular le a caixa e diz em qual
 * aparelho quer ser avisado.
 */
@RestController
@RequestMapping("/api/v1/notificacoes")
@Tag(name = "Notificações", description = "Avisos in-app derivados de faturas, parcelas, orçamentos e metas")
@RequiredArgsConstructor
public class NotificacaoController {

    /** Teto de page size: a tela pagina, ninguem precisa puxar a caixa inteira. */
    private static final int TAMANHO_MAXIMO = 50;

    private final NotificacaoService notificacaoService;
    private final PushDispositivoService pushDispositivoService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    @Operation(summary = "Lista notificações — não lidas primeiro, mais recentes no topo")
    public ResponseEntity<Page<NotificacaoResponse>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Page<NotificacaoResponse> pagina = notificacaoService
                .listar(usuarioId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAMANHO_MAXIMO)))
                .map(NotificacaoResponse::fromEntity);
        return ResponseEntity.ok(pagina);
    }

    @PostMapping("/dispositivos")
    @Operation(summary = "Registrar aparelho para receber aviso por push")
    public ResponseEntity<Void> registrarDispositivo(@Valid @RequestBody RegistrarDispositivoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        pushDispositivoService.registrar(usuarioId, request.pushToken(), request.plataforma());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/dispositivos")
    @Operation(summary = "Parar de receber aviso neste aparelho")
    public ResponseEntity<Void> revogarDispositivo(@RequestParam("pushToken") String pushToken) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        pushDispositivoService.revogar(usuarioId, pushToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/nao-lidas/contagem")
    @Operation(summary = "Contagem de não lidas — alimenta o badge do sino")
    public ResponseEntity<Map<String, Long>> contarNaoLidas() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(Map.of("naoLidas", notificacaoService.contarNaoLidas(usuarioId)));
    }

    @PatchMapping("/{id}/lida")
    @Operation(summary = "Marca uma notificação como lida")
    public ResponseEntity<NotificacaoResponse> marcarComoLida(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(
                NotificacaoResponse.fromEntity(notificacaoService.marcarComoLida(id, usuarioId)));
    }

    @PostMapping("/marcar-todas-lidas")
    @Operation(summary = "Marca todas as notificações do usuário como lidas")
    public ResponseEntity<Map<String, Integer>> marcarTodasComoLidas() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(Map.of("atualizadas", notificacaoService.marcarTodasComoLidas(usuarioId)));
    }
}
