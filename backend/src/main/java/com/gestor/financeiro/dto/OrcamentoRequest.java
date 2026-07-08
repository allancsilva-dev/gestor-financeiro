package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public class OrcamentoRequest {

    @NotNull(message = "Mês é obrigatório")
    private Integer mes;

    @NotNull(message = "Ano é obrigatório")
    private Integer ano;

    private List<OrcamentoCategoriaRequest> categorias;

    public Integer getMes() { return mes; }
    public void setMes(Integer mes) { this.mes = mes; }

    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }

    public List<OrcamentoCategoriaRequest> getCategorias() { return categorias; }
    public void setCategorias(List<OrcamentoCategoriaRequest> categorias) { this.categorias = categorias; }
}
