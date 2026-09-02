package com.gestor.financeiro.dto;

/**
 * Aviso que acompanha a resposta de uma operacao, sem impedi-la.
 *
 * Mesma forma do rascunho de notificacao (tipo, texto e destino de navegacao) de
 * proposito: o cliente trata o alerta sincrono e a notificacao in-app com um
 * componente so.
 *
 * Existe porque nem todo aviso pode esperar a sincronizacao de notificacoes: quem
 * acabou de estourar o limite do cartao precisa saber na hora. A cobranca automatica
 * da recorrencia nao tem request, entao la o aviso chega apenas pelos canais in-app e
 * push, derivados em NotificacaoService.
 */
public record AlertaDto(
        String codigo,
        String titulo,
        String mensagem,
        String destino,
        Long destinoId
) {
}
