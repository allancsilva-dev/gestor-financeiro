package com.gestor.financeiro.service.assistant;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class FinancialQuestionClassifier {
    /** Verbos que anunciam lançamento, não consulta. */
    private static final Pattern LEDGER_CUE = Pattern.compile(
            ".*\\b(comprei|paguei|gastei|gasto de|lancei|lancar|registrar|anota|anote)\\b.*");
    private static final Pattern MONEY = Pattern.compile(
            "(?<![\\p{L}\\d])(?:r\\$\\s*)?[0-9]+(?:[.,][0-9]{1,2})?(?![\\p{L}\\d])");

    private final Clock clock;
    public FinancialQuestionClassifier(Clock clock) { this.clock = clock; }

    public Optional<ValidatedFinancialQuery> classify(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String text = normalize(input);
        var intent = intent(text);
        if (intent == null) return Optional.empty();
        // "comprei 300 no Cartao Nubank" cita cartão mas é lançamento: só vira consulta
        // quando a frase pergunta algo. Sem isso o assistente nunca registraria a compra.
        boolean asks = text.contains("?")
                || has(text, "quanto", "qual", "quais", "quando", "quantos", "mostra", "resumo", "como esta");
        if (!asks && (LEDGER_CUE.matcher(text).matches() || MONEY.matcher(text).find())) return Optional.empty();
        LocalDate today = LocalDate.now(clock);
        LocalDate from = text.contains("hoje") ? today : today.withDayOfMonth(1);
        LocalDate to = text.contains("hoje") ? today : today.withDayOfMonth(today.lengthOfMonth());
        return Optional.of(new ValidatedFinancialQuery(intent, from, to));
    }

    private ValidatedFinancialQuery.Intent intent(String text) {
        if (has(text, "quanto tenho", "saldo", "disponivel", "patrimonio")) return ValidatedFinancialQuery.Intent.BALANCE;
        if (has(text, "quanto gastei", "meus gastos", "gastos do mes", "gastos por categoria")) return ValidatedFinancialQuery.Intent.SPENDING_BY_CATEGORY;
        if (has(text, "orcamento", "limite do mes")) return ValidatedFinancialQuery.Intent.BUDGET;
        if (has(text, "meta", "objetivo")) return ValidatedFinancialQuery.Intent.GOALS;
        if (has(text, "fatura", "cartao")) return ValidatedFinancialQuery.Intent.INVOICES;
        if (has(text, "compromisso", "conta a pagar", "vence", "vencimento")) return ValidatedFinancialQuery.Intent.COMMITMENTS;
        if (has(text, "investimento", "investido", "ativo")) return ValidatedFinancialQuery.Intent.INVESTMENTS;
        return null;
    }
    private boolean has(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }
    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("\\s+", " ").trim();
    }
}
