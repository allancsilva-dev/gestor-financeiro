package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ReconciliacaoSistemaResultado(
        Instant executadoEm,
        long duracaoMs,
        long usuarios,
        long verificacoes,
        long divergencias,
        long erros,
        Map<ReconciliacaoGlobalResponse.Invariante, TotaisInvariante> porInvariante,
        List<ResultadoUsuario> resultados
) {
    public boolean degradado() {
        return divergencias > 0 || erros > 0;
    }

    public record TotaisInvariante(long verificacoes, long divergencias) {}

    public record ResultadoUsuario(Long usuarioId, ReconciliacaoGlobalResponse relatorio, boolean erroTecnico) {}
}
