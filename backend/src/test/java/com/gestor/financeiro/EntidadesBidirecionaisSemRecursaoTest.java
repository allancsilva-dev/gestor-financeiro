package com.gestor.financeiro;

import com.gestor.financeiro.model.Ativo;
import com.gestor.financeiro.model.MovimentacaoAtivo;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.Transacao;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * BACKLOG-0084: os dois unicos ciclos bidirecionais do modelo (Ativo x
 * MovimentacaoAtivo e Transacao x Parcela) tinham @Data nos dois lados, sem
 * exclusao — toString/equals/hashCode recursavam ate estourar a pilha.
 */
class EntidadesBidirecionaisSemRecursaoTest {

    @Test
    void ativoEMovimentacaoNaoRecursam() {
        Ativo ativo = new Ativo();
        MovimentacaoAtivo movimentacao = new MovimentacaoAtivo();
        movimentacao.setAtivo(ativo);
        ativo.getMovimentacoes().add(movimentacao);

        assertDoesNotThrow(ativo::toString);
        assertDoesNotThrow(movimentacao::toString);
        assertDoesNotThrow(ativo::hashCode);
        assertDoesNotThrow(movimentacao::hashCode);
        assertDoesNotThrow(() -> ativo.equals(new Ativo()));
        assertDoesNotThrow(() -> movimentacao.equals(new MovimentacaoAtivo()));
    }

    @Test
    void transacaoEParcelaNaoRecursam() {
        Transacao transacao = new Transacao();
        Parcela parcela = new Parcela();
        parcela.setTransacao(transacao);
        transacao.getParcelas().add(parcela);

        assertDoesNotThrow(transacao::toString);
        assertDoesNotThrow(parcela::toString);
        assertDoesNotThrow(transacao::hashCode);
        assertDoesNotThrow(parcela::hashCode);
        assertDoesNotThrow(() -> transacao.equals(new Transacao()));
        assertDoesNotThrow(() -> parcela.equals(new Parcela()));
    }
}
