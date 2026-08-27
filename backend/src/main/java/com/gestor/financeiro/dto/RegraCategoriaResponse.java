package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.RegraCategoria;

/** Regra como o titular a vê: o texto que ele escreveu e para onde ele manda. */
public record RegraCategoriaResponse(
        Long id,
        String padrao,
        String tipoCasamento,
        String tipoTransacao,
        Long categoriaId,
        String categoriaNome,
        String categoriaIcone,
        int prioridade
) {
    public static RegraCategoriaResponse de(RegraCategoria regra) {
        return new RegraCategoriaResponse(
                regra.getId(),
                regra.getPadrao(),
                regra.getTipoCasamento().name(),
                regra.getTipoTransacao() == null ? null : regra.getTipoTransacao().name(),
                regra.getCategoria().getId(),
                regra.getCategoria().getNome(),
                regra.getCategoria().getIcone(),
                regra.getPrioridade());
    }
}
