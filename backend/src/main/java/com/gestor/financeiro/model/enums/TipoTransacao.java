package com.gestor.financeiro.model.enums;

/**
 * ENUM que define os tipos de transação financeira
 
 */
public enum TipoTransacao {
    
    // ENTRADA = dinheiro que ENTRA (salário, freelance, venda)
    ENTRADA("Entrada"),
    
    // SAIDA = dinheiro que SAI (compras, contas, despesas)
    SAIDA("Saída");
    
    // Atributo privado que guarda o texto amigável
    private String descricao;
    
    /**
     * CONSTRUTOR do ENUM
     * Chamado automaticamente quando criamos ENTRADA ou SAIDA
     * 
     * @param descricao - O texto que será exibido para o usuário
     */
    TipoTransacao(String descricao) {
        this.descricao = descricao;
    }
    
    /**
     * GETTER - Retorna a descrição amigável
     * 
     * Exemplo de uso:
     * TipoTransacao.ENTRADA.getDescricao() → retorna "Entrada"
     * TipoTransacao.SAIDA.getDescricao() → retorna "Saída"
     */
    public String getDescricao() {
        return descricao;
    }
}