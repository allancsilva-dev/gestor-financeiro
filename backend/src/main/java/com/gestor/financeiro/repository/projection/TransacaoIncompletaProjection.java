package com.gestor.financeiro.repository.projection;

public interface TransacaoIncompletaProjection {
    Long getTransacaoId();
    Long getContaId();
    Long getCarteiraId();
}
