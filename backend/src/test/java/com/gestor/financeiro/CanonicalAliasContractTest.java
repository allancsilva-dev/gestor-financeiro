package com.gestor.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gestor.financeiro.dto.OnboardingFinalizarRequest;
import com.gestor.financeiro.dto.TransacaoRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalAliasContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void transacaoAceitaCartaoIdComoNomeCanonico() throws Exception {
        TransacaoRequest request = objectMapper.readValue("""
                {"descricao":"Mercado","valor":10,"data":"2026-07-16","tipo":"SAIDA",
                 "categoriaId":1,"cartaoId":42}
                """, TransacaoRequest.class);
        assertEquals(42L, request.getCartaoId());
    }

    @Test
    void onboardingAceitaObjetoCartaoSemTipoLegado() throws Exception {
        OnboardingFinalizarRequest request = objectMapper.readValue("""
                {"carteira":{"nome":"Principal","subtipo":"DINHEIRO","saldo":0},
                 "cartao":{"nome":"Nexos","limiteTotal":1000,"diaFechamento":5,"diaVencimento":12},
                 "categorias":[{"nome":"Mercado"}]}
                """, OnboardingFinalizarRequest.class);
        assertNotNull(request.cartao());
        assertEquals("Nexos", request.cartao().nome());
    }

    @Test
    void transacaoRejeitaContaIdLegado() {
        assertThrows(UnrecognizedPropertyException.class, () -> objectMapper.readValue("""
                {"descricao":"Mercado","valor":10,"data":"2026-07-16","tipo":"SAIDA",
                 "categoriaId":1,"contaId":42}
                """, TransacaoRequest.class));
    }

    @Test
    void onboardingRejeitaObjetoContaLegado() {
        assertThrows(UnrecognizedPropertyException.class, () -> objectMapper.readValue("""
                {"carteira":{"nome":"Principal","subtipo":"CORRENTE","saldo":0},
                 "conta":{"nome":"Nexos","limiteTotal":1000,"diaFechamento":5,"diaVencimento":12},
                 "categorias":[{"nome":"Mercado"}]}
                """, OnboardingFinalizarRequest.class));
    }
}
