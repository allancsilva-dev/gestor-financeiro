package com.gestor.financeiro.model.enums;

/**
 * Periodicidade de uma ContaFixa (V72). Ate aqui o motor so sabia plusMonths(1):
 * assinatura anual (Amazon Prime) ou semanal nao tinha como ser cadastrada.
 *
 * <p>Duas familias, e a diferenca importa:</p>
 * <ul>
 *   <li><b>Multiplo de mes</b> (MENSAL..ANUAL): a serie sai de {@code diaVencimento} e
 *       reclampa a cada passo, entao dia 31 vira 28 em fevereiro e volta a 31 em marco.</li>
 *   <li><b>Sub-mensal</b> (SEMANAL, QUINZENAL): "dia do mes" nao existe. A serie sai de
 *       {@code dataAncora} e anda em dias, preservando o dia da semana e a paridade da
 *       quinzena.</li>
 * </ul>
 */
public enum FrequenciaRecorrencia {

    SEMANAL("Semanal", 7, Unidade.DIAS),
    QUINZENAL("Quinzenal", 14, Unidade.DIAS),
    MENSAL("Mensal", 1, Unidade.MESES),
    BIMESTRAL("Bimestral", 2, Unidade.MESES),
    TRIMESTRAL("Trimestral", 3, Unidade.MESES),
    SEMESTRAL("Semestral", 6, Unidade.MESES),
    ANUAL("Anual", 12, Unidade.MESES);

    public enum Unidade { DIAS, MESES }

    private final String descricao;
    private final int passo;
    private final Unidade unidade;

    FrequenciaRecorrencia(String descricao, int passo, Unidade unidade) {
        this.descricao = descricao;
        this.passo = passo;
        this.unidade = unidade;
    }

    public String getDescricao() {
        return descricao;
    }

    public int getPasso() {
        return passo;
    }

    public Unidade getUnidade() {
        return unidade;
    }

    /** Sub-mensal exige data de ancora; multiplo de mes usa diaVencimento. */
    public boolean isSubMensal() {
        return unidade == Unidade.DIAS;
    }
}
