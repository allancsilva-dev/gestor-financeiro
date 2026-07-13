package com.gestor.financeiro.exception;

public class CardParcelDeprecatedException extends RuntimeException {
    private final Long transacaoId;
    public CardParcelDeprecatedException(Long transacaoId) {
        super("Parcelas de cartão usam o cronograma canônico da transação");
        this.transacaoId = transacaoId;
    }
    public Long getTransacaoId() { return transacaoId; }
}
