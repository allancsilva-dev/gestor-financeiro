package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
class RuleBasedFinancialInputParserTest {
    private RuleBasedFinancialInputParser parser;

    @BeforeEach
    void setup() {
        Carteira nubank = new Carteira();
        nubank.setNome("Nubank");
        Categoria gasolina = new Categoria();
        gasolina.setNome("Gasolina");
        Categoria mercado = new Categoria();
        mercado.setNome("Mercado");
        CarteiraRepository carteiras = proxy(CarteiraRepository.class, "findByUsuarioId", List.of(nubank));
        CategoriaRepository categorias = proxy(CategoriaRepository.class, "findByUsuarioIdAndAtivoTrue", List.of(gasolina, mercado));
        parser = new RuleBasedFinancialInputParser(carteiras, categorias,
                Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, String method, Object result) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, called, args) -> called.getName().equals(method) ? result
                        : called.getReturnType().equals(boolean.class) ? false
                        : called.getReturnType().isPrimitive() ? 0 : null);
    }

    @Test
    void fraseCompletaNaoPrecisaDeFornecedor() {
        FinancialParseResult result = parser.parse(7L, "gasolina 85 no Nubank hoje");

        assertThat(result.outcome()).isEqualTo(ParseOutcome.COMPLETE);
        assertThat(result.draft().tipo()).isEqualTo(TipoTransacao.SAIDA);
        assertThat(result.draft().valor()).isEqualByComparingTo("85.00");
        assertThat(result.draft().data()).isEqualTo("2026-08-27");
        assertThat(result.draft().contaNome()).isEqualTo("Nubank");
        assertThat(result.draft().categoriaNome()).isEqualTo("Gasolina");
    }

    @Test
    void naoInventaEntidadeForaDoTitular() {
        FinancialParseResult result = parser.parse(7L, "mercado 50 ontem no Itau");

        assertThat(result.outcome()).isEqualTo(ParseOutcome.NEEDS_ONE_FIELD);
        assertThat(result.draft().missingFields()).containsExactly("contaNome");
        assertThat(result.question()).isEqualTo("Qual conta você usou?");
    }

    @Test
    void doisValoresContraditoriosAbremFormulario() {
        FinancialParseResult result = parser.parse(7L, "gasolina 85 ou 90 no Nubank");

        assertThat(result.outcome()).isEqualTo(ParseOutcome.NEEDS_ONE_FIELD);
        assertThat(result.draft().missingFields()).containsExactly("valor");
    }

    @Test
    void textoNaoFinanceiroNaoCriaRascunho() {
        assertThat(parser.parse(7L, "como vai o tempo?").outcome()).isEqualTo(ParseOutcome.NOT_FINANCIAL);
    }
}
