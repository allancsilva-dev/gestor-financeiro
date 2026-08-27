package com.gestor.financeiro.model.enums;

import java.math.BigDecimal;

/**
 * O que a categoria faz com o que sobrou — ou faltou — no fim do mês.
 *
 * <p>Sobra é dinheiro que o usuário deixou de gastar; excesso é dinheiro que ele gastou além do
 * limite. Quem escolhe o que fazer com cada um é o dono do orçamento, porque as duas leituras são
 * legítimas: "economizei, posso gastar mês que vem" e "estourei, tenho que compensar".</p>
 */
public enum PoliticaRollover {
    /** Cada mês recomeça do limite. */
    NONE,
    /** Só a sobra passa adiante. */
    SURPLUS_ONLY,
    /** Só o excesso passa adiante, reduzindo o mês seguinte. */
    DEFICIT_ONLY,
    /** Sobra e excesso passam adiante. */
    BOTH;

    /** Quanto deste resultado atravessa para o mês seguinte. */
    public BigDecimal carregar(BigDecimal resultado) {
        BigDecimal valor = resultado == null ? BigDecimal.ZERO : resultado;
        return switch (this) {
            case NONE -> BigDecimal.ZERO.setScale(valor.scale());
            case BOTH -> valor;
            case SURPLUS_ONLY -> valor.signum() > 0 ? valor : BigDecimal.ZERO.setScale(valor.scale());
            case DEFICIT_ONLY -> valor.signum() < 0 ? valor : BigDecimal.ZERO.setScale(valor.scale());
        };
    }
}
