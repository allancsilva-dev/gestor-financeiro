package com.gestor.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import com.gestor.financeiro.model.enums.FrequenciaRecorrencia;
import com.gestor.financeiro.model.enums.TipoTransacao;
import java.time.LocalDate;

public class ContaFixaRequest {

    @NotBlank(message = "Campo obrigatório")
    @JsonAlias({"descricao", "nome"})
    private String descricao;

    @NotNull(message = "Campo obrigatório")
    @Positive(message = "Valor deve ser positivo")
    @JsonAlias({"valor", "valorPlanejado"})
    private BigDecimal valor;

    @NotNull(message = "Campo obrigatório")
    @Min(value = 1, message = "Dia de vencimento deve estar entre 1 e 31")
    @Max(value = 31, message = "Dia de vencimento deve estar entre 1 e 31")
    private Integer diaVencimento;

    private Long categoriaId;

    private IdRef categoria;

    private Boolean recorrente;

    private String observacoes;

    private TipoTransacao tipo = TipoTransacao.SAIDA;

    private Boolean execucaoAutomatica = false;

    private Long carteiraId;

    /** Destino alternativo a carteiraId: assinatura cobrada no cartao (V67). */
    private Long cartaoId;

    /** Periodicidade (V72). Ausente vale MENSAL: cliente antigo continua funcionando. */
    private FrequenciaRecorrencia frequencia;

    /** Primeira cobranca de recorrencia sub-mensal (SEMANAL/QUINZENAL). */
    private LocalDate dataAncora;

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

    public Integer getDiaVencimento() {
        return diaVencimento;
    }

    public void setDiaVencimento(Integer diaVencimento) {
        this.diaVencimento = diaVencimento;
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

    public Boolean getRecorrente() {
        return recorrente;
    }

    public void setRecorrente(Boolean recorrente) {
        this.recorrente = recorrente;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public TipoTransacao getTipo() { return tipo == null ? TipoTransacao.SAIDA : tipo; }
    public void setTipo(TipoTransacao tipo) { this.tipo = tipo; }
    public Boolean getExecucaoAutomatica() { return Boolean.TRUE.equals(execucaoAutomatica); }
    public void setExecucaoAutomatica(Boolean execucaoAutomatica) { this.execucaoAutomatica = execucaoAutomatica; }
    public Long getCarteiraId() { return carteiraId; }
    public void setCarteiraId(Long carteiraId) { this.carteiraId = carteiraId; }
    public Long getCartaoId() { return cartaoId; }
    public void setCartaoId(Long cartaoId) { this.cartaoId = cartaoId; }
    public FrequenciaRecorrencia getFrequencia() { return frequencia; }
    public void setFrequencia(FrequenciaRecorrencia frequencia) { this.frequencia = frequencia; }
    public LocalDate getDataAncora() { return dataAncora; }
    public void setDataAncora(LocalDate dataAncora) { this.dataAncora = dataAncora; }

    // Um destino, nunca dois: a cobranca sai do caixa ou do cartao (V67).
    @AssertTrue(message = "Informe apenas um destino: conta ou cartão")
    public boolean isDestinoUnico() {
        return carteiraId == null || cartaoId == null;
    }

    @AssertTrue(message = "Destino é obrigatório para execução automática")
    public boolean isDestinoAutomaticoInformado() {
        return !Boolean.TRUE.equals(execucaoAutomatica) || carteiraId != null || cartaoId != null;
    }

    // Cartao cobra; nao se recebe salario no cartao.
    @AssertTrue(message = "Informe a data da primeira cobrança para recorrência semanal ou quinzenal")
    public boolean isAncoraInformadaQuandoSubMensal() {
        return frequencia == null || !frequencia.isSubMensal() || dataAncora != null;
    }

    @AssertTrue(message = "Cartão só aceita recorrência de saída")
    public boolean isCartaoSomenteSaida() {
        return cartaoId == null || getTipo() == TipoTransacao.SAIDA;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
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
