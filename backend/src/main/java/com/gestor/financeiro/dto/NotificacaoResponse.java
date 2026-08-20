package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Notificacao;

import java.time.LocalDateTime;

public record NotificacaoResponse(
        Long id,
        String tipo,
        String titulo,
        String mensagem,
        String destino,
        Long destinoId,
        Boolean lida,
        LocalDateTime criadaEm
) {
    public static NotificacaoResponse fromEntity(Notificacao n) {
        return new NotificacaoResponse(
                n.getId(),
                n.getTipo().name(),
                n.getTitulo(),
                n.getMensagem(),
                n.getDestino(),
                n.getDestinoId(),
                n.getLida(),
                n.getCriadaEm());
    }
}
