package com.gestor.financeiro.config;

import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.service.assistant.ProviderExtractionRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LocalE2eAssistantConfigurationTest {
    private static final String CONTEXT = "Contas permitidas: [Conta Principal]\n"
            + "Categorias permitidas: [Alimentacao, Mercado, Transporte]\n"
            + "Cartoes permitidos: [Cartao Nubank]";

    private final LocalE2eAssistantConfiguration config = new LocalE2eAssistantConfiguration();

    private com.gestor.financeiro.service.assistant.TransactionDraftV1 extract(String text) {
        return config.localE2ePrimaryProvider()
                .extract(new ProviderExtractionRequest(1L, null, text, CONTEXT), "transaction-draft-v1")
                .draft();
    }

    @Test
    void fraseSemValorViraRascunhoIncompleto() {
        var result = config.localE2ePrimaryProvider().extract(
                new ProviderExtractionRequest(1L, null, "comprei no mercado ontem", CONTEXT), "transaction-draft-v1");

        assertThat(result.provider()).isEqualTo("LOCAL_E2E_PRIMARY");
        assertThat(result.draft().valor()).isNull();
        assertThat(result.draft().contaNome()).isEqualTo("Conta Principal");
        assertThat(result.draft().categoriaNome()).isEqualTo("Mercado");
        assertThat(result.draft().data()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(result.draft().missingFields()).containsExactly("valor");
    }

    @Test
    void fraseCompletaExtraiValorComCentavosDescricaoECategoria() {
        var draft = extract("paguei 137,90 no supermercado hoje pela Conta Principal");

        assertThat(draft.tipo()).isEqualTo(TipoTransacao.SAIDA);
        assertThat(draft.valor()).isEqualByComparingTo(new BigDecimal("137.90"));
        assertThat(draft.descricao()).isEqualTo("Supermercado");
        assertThat(draft.categoriaNome()).isEqualTo("Mercado");
        assertThat(draft.contaNome()).isEqualTo("Conta Principal");
        assertThat(draft.cartaoNome()).isNull();
        assertThat(draft.parcelas()).isNull();
        assertThat(draft.missingFields()).isEmpty();
    }

    @Test
    void compraParceladaNoCartaoNaoUsaConta() {
        var draft = extract("comprei 899,90 de gasolina no posto ontem no Cartao Nubank em 3x");

        assertThat(draft.valor()).isEqualByComparingTo(new BigDecimal("899.90"));
        assertThat(draft.cartaoNome()).isEqualTo("Cartao Nubank");
        assertThat(draft.parcelas()).isEqualTo(3);
        assertThat(draft.contaNome()).isNull();
        assertThat(draft.categoriaNome()).isEqualTo("Transporte");
        assertThat(draft.data()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(draft.missingFields()).isEmpty();
    }

    @Test
    void parcelamentoSemCartaoPedeCartao() {
        var draft = extract("almoco 240,00 hoje em 4 vezes");

        // O pedido de parcelar sobrevive no rascunho: é o que permite cobrar o cartão.
        assertThat(draft.parcelas()).isEqualTo(4);
        assertThat(draft.cartaoNome()).isNull();
        assertThat(draft.missingFields()).contains("cartaoNome");
    }

    @Test
    void entradaEReconhecidaPeloVerbo() {
        var draft = extract("recebi 3500,00 hoje");

        assertThat(draft.tipo()).isEqualTo(TipoTransacao.ENTRADA);
        assertThat(draft.valor()).isEqualByComparingTo(new BigDecimal("3500.00"));
    }

    @Test
    void transcricaoEDeterministicaESemRede() {
        assertThat(config.localE2eTranscriptionProvider().transcribe(Path.of("unused")))
                .isEqualTo("paguei 137,90 de gasolina no posto hoje pelo Cartao Nubank em 3x");
    }
}
