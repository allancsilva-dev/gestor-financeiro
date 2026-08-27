package com.gestor.financeiro.model.enums;

/**
 * Como o padrão da regra encosta na descrição — sempre sobre texto já normalizado.
 *
 * <p>Não existe opção de expressão regular por decisão de segurança: regex escrita pelo usuário
 * roda no request e no worker, Java não tem engine com garantia de tempo linear, e uma regra
 * infeliz (`(a+)+b`) viraria negação de serviço com um lançamento comum.</p>
 */
public enum TipoCasamentoRegra {
    IGUAL,
    COMECA_COM,
    CONTEM;

    public boolean casa(String descricaoNormalizada, String padraoNormalizado) {
        if (descricaoNormalizada == null || padraoNormalizado == null) return false;
        return switch (this) {
            case IGUAL -> descricaoNormalizada.equals(padraoNormalizado);
            case COMECA_COM -> descricaoNormalizada.startsWith(padraoNormalizado);
            case CONTEM -> descricaoNormalizada.contains(padraoNormalizado);
        };
    }
}
