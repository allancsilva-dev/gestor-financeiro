package com.gestor.financeiro.service.openfinance;

import java.time.LocalDate;

/**
 * SPI do parceiro de dados financeiros.
 *
 * <p>É aqui, e só aqui, que a rede entra na Fase 6 (ADR-0019). O que sai desta interface é entregue
 * ao pipeline canônico como um snapshot de bytes; nenhum conector de importação conhece esta
 * classe.</p>
 *
 * <p>A paginação é explícita em vez de devolver tudo de uma vez porque o volume é do parceiro, não
 * nosso: uma conta com anos de histórico não cabe em memória, e o teto de bytes do snapshot precisa
 * poder fechar a janela no meio de uma página.</p>
 */
public interface OpenFinanceProvider {

    /** Código do provedor, igual ao de {@code open_finance_provedores.codigo}. */
    String codigo();

    /** Contas que o consentimento alcança. */
    PaginaContas contas(ContextoProvedor contexto, String cursor);

    /**
     * Uma página de transações da janela.
     *
     * <p>Traz também as não efetivadas, com {@link TransacaoRemota#efetivada()} falso — quem
     * descarta é o fetcher, porque a regra de só ingerir fato consumado é nossa (ADR-0021), não do
     * parceiro. Esconder o pendente aqui impediria diagnosticar diferença de saldo.</p>
     */
    PaginaTransacoes transacoes(ContextoProvedor contexto, String contaExterna,
                               LocalDate inicio, LocalDate fim, String cursor);

    /** Saldos publicados pela instituição, contábil e disponível. */
    SaldoRemoto saldos(ContextoProvedor contexto, String contaExterna);

    /** Revoga o consentimento no parceiro. Revogar só localmente deixaria o acesso vivo lá. */
    void revogar(ContextoProvedor contexto);

    /**
     * Credencial e identificação da conexão, resolvidas por quem chama.
     *
     * <p>O provedor nunca lê o banco: recebe o token já decifrado e devolve dado. Isso mantém a
     * decifra num lugar só e impede que uma implementação de parceiro precise de acesso a
     * {@code conexao_credenciais}.</p>
     */
    record ContextoProvedor(Long usuarioId, Long conexaoId, String externalConnectionId,
                            String accessToken) { }

    record PaginaContas(java.util.List<ContaRemota> itens, String proximoCursor) { }

    record PaginaTransacoes(java.util.List<TransacaoRemota> itens, String proximoCursor) { }

    record ContaRemota(String externalAccountId, String tipo, String mascara, String moeda) { }

    /**
     * Fato como o parceiro conta.
     *
     * <p>{@code instante} vem com offset do parceiro e é convertido para data de negócio só na
     * escrita do snapshot; converter aqui esconderia a origem do erro de um dia.</p>
     */
    record TransacaoRemota(String externalId, String instante, String descricao,
                           java.math.BigDecimal valor, String moeda, boolean efetivada) { }

    record SaldoRemoto(String referenciaEm, java.math.BigDecimal saldoContabil,
                       java.math.BigDecimal saldoDisponivel, java.math.BigDecimal limiteCartao) { }

    /** Parceiro pediu para esperar. Reagendar não pode queimar tentativa da fila (ADR-0021). */
    class RetryAfterException extends RuntimeException {
        private final int segundos;
        public RetryAfterException(int segundos) { super("Provedor pediu espera"); this.segundos = segundos; }
        public int segundos() { return segundos; }
    }

    /** Consentimento inválido, expirado ou credencial recusada: falha terminal, sem retry. */
    class ConsentimentoInvalidoException extends RuntimeException {
        public ConsentimentoInvalidoException(String mensagem) { super(mensagem); }
    }
}
