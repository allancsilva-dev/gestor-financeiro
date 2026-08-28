package com.gestor.financeiro.service.importacao;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Mapeamento de colunas escolhido pelo titular.
 *
 * <p>Existe porque o extrato de cada banco chega com um cabeçalho diferente, e a lista fixa de
 * apelidos do connector nunca vai cobrir todos. Quando o mapeamento está presente, ele manda: é o
 * usuário dizendo qual coluna é a data, qual é o valor e assim por diante.</p>
 *
 * @param colunaPorCampo campo canônico ({@code date}, {@code description}, {@code amount},
 *                       {@code currency}, {@code direction}, {@code externalId}) para o nome da
 *                       coluna no arquivo.
 * @param delimitador    delimitador declarado; nulo deixa a detecção decidir.
 */
public record ImportMapping(Map<String, String> colunaPorCampo, Character delimitador) {

    public static final Set<String> CAMPOS =
            Set.of("date", "description", "amount", "currency", "direction", "externalId",
                    "openingBalance", "closingBalance");

    /** Sem mapeamento: valem os apelidos conhecidos do connector. */
    public static ImportMapping automatico() {
        return new ImportMapping(Map.of(), null);
    }

    public ImportMapping {
        colunaPorCampo = colunaPorCampo == null ? Map.of() : Map.copyOf(colunaPorCampo);
    }

    public boolean vazio() {
        return colunaPorCampo.isEmpty();
    }

    /** Campo canônico da coluna, comparando por texto normalizado do cabeçalho. */
    public String campoDaColuna(String cabecalhoNormalizado) {
        for (Map.Entry<String, String> entrada : colunaPorCampo.entrySet()) {
            if (normalizar(entrada.getValue()).equals(cabecalhoNormalizado)) {
                return entrada.getKey();
            }
        }
        return null;
    }

    private static String normalizar(String valor) {
        return java.text.Normalizer.normalize(valor == null ? "" : valor, java.text.Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
    }
}
