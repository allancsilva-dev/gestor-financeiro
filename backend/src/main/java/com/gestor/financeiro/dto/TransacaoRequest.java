package com.gestor.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.gestor.financeiro.model.enums.TipoTransacao;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransacaoRequest {

    @NotBlank(message = "Campo obrigatório")
    @Size(max = 255, message = "Descrição deve ter no máximo 255 caracteres")
    private String descricao;

    // Aceita 'valor' e também 'valorTotal' para compatibilidade com clientes atuais.
    @NotNull(message = "Campo obrigatório")
    @Positive(message = "Valor deve ser positivo")
    @JsonAlias({"valor", "valorTotal"})
    private BigDecimal valor;

    @NotNull(message = "Campo obrigatório")
    private LocalDate data;

    @NotNull(message = "Campo obrigatório")
    private TipoTransacao tipo;

    private Long categoriaId;

    private IdRef categoria;

    private Long contaId;

    private IdRef conta;

    private Boolean parcelado;

    private Integer totalParcelas;

    private String observacoes;

    private Boolean recorrente;

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public TipoTransacao getTipo() {
        return tipo;
    }

    public void setTipo(TipoTransacao tipo) {
        this.tipo = tipo;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(Long categoriaId) {
        this.categoriaId = categoriaId;
    }

    public IdRef getCategoria() {
        return categoria;
    }

    public void setCategoria(IdRef categoria) {
        this.categoria = categoria;
    }

    public Long getContaId() {
        return contaId;
    }

    public void setContaId(Long contaId) {
        this.contaId = contaId;
    }

    public IdRef getConta() {
        return conta;
    }

    public void setConta(IdRef conta) {
        this.conta = conta;
    }

    public Boolean getParcelado() {
        return parcelado;
    }

    public void setParcelado(Boolean parcelado) {
        this.parcelado = parcelado;
    }

    public Integer getTotalParcelas() {
        return totalParcelas;
    }

    public void setTotalParcelas(Integer totalParcelas) {
        this.totalParcelas = totalParcelas;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public Boolean getRecorrente() {
        return recorrente;
    }

    public void setRecorrente(Boolean recorrente) {
        this.recorrente = recorrente;
    }

    public Long getCategoriaIdNormalizada() {
        if (categoriaId != null) {
            return categoriaId;
        }

        if (categoria != null) {
            return categoria.getId();
        }

        return null;
    }

    public Long getContaIdNormalizada() {
        if (contaId != null) {
            return contaId;
        }

        if (conta != null) {
            return conta.getId();
        }

        return null;
    }

    @AssertTrue(message = "Campo obrigatório")
    public boolean isCategoriaInformada() {
        return getCategoriaIdNormalizada() != null;
    }

    public static class IdRef {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }
}
