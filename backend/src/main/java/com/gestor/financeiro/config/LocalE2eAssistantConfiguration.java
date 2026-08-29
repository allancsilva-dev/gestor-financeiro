package com.gestor.financeiro.config;

import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.service.assistant.ProviderExtraction;
import com.gestor.financeiro.service.assistant.ProviderExtractionRequest;
import com.gestor.financeiro.service.assistant.StructuredAiProvider;
import com.gestor.financeiro.service.assistant.TransactionDraftV1;
import com.gestor.financeiro.service.assistant.TranscriptionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Providers determinísticos disponíveis exclusivamente no profile local-e2e.
 *
 * <p>O extrator é determinístico de propósito: o E2E precisa provar o caminho completo com frases
 * de gente — valor com centavos, data, categoria, conta ou cartão e parcelamento — sem depender de
 * nenhuma chamada paga. Frase sem valor vira rascunho incompleto, que é o gatilho da pergunta única.</p>
 */
@Configuration
@Profile("local-e2e")
public class LocalE2eAssistantConfiguration {
    private static final Pattern ALLOWED_ACCOUNTS = Pattern.compile("Contas permitidas: \\[([^\\]]*)\\]");
    private static final Pattern ALLOWED_CATEGORIES = Pattern.compile("Categorias permitidas: \\[([^\\]]*)\\]");
    private static final Pattern ALLOWED_CARDS = Pattern.compile("Cartoes permitidos: \\[([^\\]]*)\\]");
    private static final Pattern VALUE = Pattern.compile(
            "(?<![\\p{L}\\d])(?:r\\$\\s*)?([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{2}|[0-9]+(?:[.,][0-9]{1,2})?)(?![\\p{L}\\d])");
    private static final Pattern INSTALLMENTS = Pattern.compile("\\b(?:em\\s+)?([0-9]{1,2})\\s*(?:x\\b|vezes\\b|parcelas?\\b)");
    private static final Pattern BR_DATE = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");

    /** Palavra dita → categoria do fixture. Mantém a frase natural sem citar a categoria. */
    private static final Map<String, String> CATEGORY_HINTS = categoryHints();

    /** Ordem importa: "supermercado" precisa ser testado antes de "mercado". */
    private static Map<String, String> categoryHints() {
        Map<String, String> hints = new LinkedHashMap<>();
        hints.put("supermercado", "Mercado");
        hints.put("mercado", "Mercado");
        hints.put("feira", "Mercado");
        hints.put("gasolina", "Transporte");
        hints.put("posto", "Transporte");
        hints.put("uber", "Transporte");
        hints.put("restaurante", "Alimentacao");
        hints.put("almoco", "Alimentacao");
        hints.put("lanche", "Alimentacao");
        return hints;
    }

    @Bean("geminiStructuredAiProvider")
    StructuredAiProvider localE2ePrimaryProvider() {
        return structured("LOCAL_E2E_PRIMARY");
    }

    @Bean("openAiStructuredAiProvider")
    StructuredAiProvider localE2eSecondaryProvider() {
        return structured("LOCAL_E2E_SECONDARY");
    }

    @Bean
    TranscriptionProvider localE2eTranscriptionProvider() {
        return new TranscriptionProvider() {
            @Override public String transcribe(Path audio) {
                return "paguei 137,90 de gasolina no posto hoje pelo Cartao Nubank em 3x";
            }
            @Override public String provider() { return "LOCAL_E2E"; }
            @Override public String model() { return "deterministic-transcript-v1"; }
        };
    }

    private StructuredAiProvider structured(String providerName) {
        return new StructuredAiProvider() {
            @Override
            public ProviderExtraction extract(ProviderExtractionRequest request, String schemaVersion) {
                String text = normalize(request.text());
                String context = request.trustedContext();
                Integer parcelas = installments(text);
                BigDecimal valor = amount(parcelas == null ? text : INSTALLMENTS.matcher(text).replaceAll(" "));
                // Cartão só entra quando dito por nome: inferir parcelaria a compra sem pedido.
                String card = firstAllowed(text, list(ALLOWED_CARDS, context));
                String account = card != null ? null : account(text, list(ALLOWED_ACCOUNTS, context));
                String category = category(text, list(ALLOWED_CATEGORIES, context));
                String description = description(text);

                List<String> missing = new ArrayList<>();
                if (valor == null) missing.add("valor");
                if (card == null && account == null) missing.add("contaNome");
                if (category == null) missing.add("categoriaNome");
                if (parcelas != null && card == null) missing.add("cartaoNome");

                TransactionDraftV1 draft = new TransactionDraftV1("CREATE_TRANSACTION",
                        direction(text), valor, description, date(text),
                        account, category, card, parcelas, missing);
                return new ProviderExtraction(draft, provider(), model());
            }

            @Override public String provider() { return providerName; }
            @Override public String model() { return "deterministic-draft-v1"; }
        };
    }

    private static TipoTransacao direction(String text) {
        return text.matches(".*\\b(recebi|ganhei|entrou|salario)\\b.*") ? TipoTransacao.ENTRADA : TipoTransacao.SAIDA;
    }

    private static BigDecimal amount(String text) {
        Matcher matcher = VALUE.matcher(text);
        BigDecimal found = null;
        while (matcher.find()) {
            if (matcher.end() < text.length() && text.charAt(matcher.end()) == '/') continue;
            BigDecimal candidate = new BigDecimal(matcher.group(1).replace(".", "").replace(',', '.'));
            if (candidate.signum() <= 0) continue;
            if (found != null) return null;
            found = candidate.setScale(2);
        }
        return found;
    }

    private static Integer installments(String text) {
        Matcher matcher = INSTALLMENTS.matcher(text);
        Integer found = null;
        while (matcher.find()) {
            int candidate = Integer.parseInt(matcher.group(1));
            if (candidate < 2 || candidate > 48 || found != null) return null;
            found = candidate;
        }
        return found;
    }

    private static LocalDate date(String text) {
        LocalDate today = LocalDate.now();
        Matcher br = BR_DATE.matcher(text);
        if (br.find()) {
            return LocalDate.of(Integer.parseInt(br.group(3)), Integer.parseInt(br.group(2)), Integer.parseInt(br.group(1)));
        }
        if (text.matches(".*\\banteontem\\b.*")) return today.minusDays(2);
        if (text.matches(".*\\bontem\\b.*")) return today.minusDays(1);
        return today;
    }

    /** Conta única não tem ambiguidade: quem só tem uma não precisa dizer o nome dela. */
    private static String account(String text, List<String> allowed) {
        String named = firstAllowed(text, allowed);
        if (named != null) return named;
        return allowed.size() == 1 ? allowed.get(0) : null;
    }

    private static String category(String text, List<String> allowed) {
        String named = firstAllowed(text, allowed);
        if (named != null) return named;
        for (Map.Entry<String, String> hint : CATEGORY_HINTS.entrySet()) {
            if (!text.contains(hint.getKey())) continue;
            String match = firstAllowed(normalize(hint.getValue()), allowed);
            if (match != null) return match;
        }
        return null;
    }

    /** Descrição é a palavra do gasto, não a frase inteira: é o que a pessoa reconhece na lista. */
    private static String description(String text) {
        for (Map.Entry<String, String> hint : CATEGORY_HINTS.entrySet()) {
            if (text.contains(hint.getKey())) {
                return hint.getKey().substring(0, 1).toUpperCase(Locale.ROOT) + hint.getKey().substring(1);
            }
        }
        return "Lançamento pelo assistente";
    }

    private static List<String> list(Pattern pattern, String context) {
        Matcher matcher = pattern.matcher(context == null ? "" : context);
        if (!matcher.find()) return List.of();
        String group = matcher.group(1).trim();
        if (group.isEmpty()) return List.of();
        return List.of(group.split("\\s*,\\s*"));
    }

    private static String firstAllowed(String text, List<String> allowed) {
        for (String name : allowed) {
            if (!name.isBlank() && text.contains(normalize(name))) return name;
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String stripped = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT);
    }
}
