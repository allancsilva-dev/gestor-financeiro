package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser determinístico que sempre antecede qualquer fornecedor externo. */
@Component
public class RuleBasedFinancialInputParser implements FinancialInputParser {
    private static final Pattern VALUE = Pattern.compile("(?<![\\p{L}\\d])(?:r\\$\\s*)?([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{1,2}|[0-9]+(?:[.,][0-9]{1,2})?)(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern BR_DATE = Pattern.compile("\\b(\\d{1,2}/\\d{1,2}/\\d{4})\\b");
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("d/M/uuuu");

    private final CarteiraRepository carteiras;
    private final CategoriaRepository categorias;
    private final Clock clock;

    public RuleBasedFinancialInputParser(CarteiraRepository carteiras, CategoriaRepository categorias, Clock clock) {
        this.carteiras = carteiras;
        this.categorias = categorias;
        this.clock = clock;
    }

    @Override
    public FinancialParseResult parse(Long usuarioId, String input) {
        if (input == null || input.isBlank() || input.length() > 2_000) return FinancialParseResult.notFinancial();
        String text = normalize(input);
        TipoTransacao tipo = direction(text);
        BigDecimal valor = amount(text);
        LocalDate data = date(text);
        List<Carteira> ownedWallets = carteiras.findByUsuarioId(usuarioId);
        List<Categoria> ownedCategories = categorias.findByUsuarioIdAndAtivoTrue(usuarioId);
        Match carteira = uniqueName(text, ownedWallets.stream().map(Carteira::getNome).toList());
        Match categoria = uniqueName(text, ownedCategories.stream().map(Categoria::getNome).toList());

        boolean cue = tipo != null || valor != null || text.matches(".*\\b(gastei|paguei|comprei|recebi|ganhei)\\b.*");
        if (!cue) return FinancialParseResult.notFinancial();

        List<String> missing = new ArrayList<>();
        if (tipo == null) missing.add("tipo");
        if (valor == null) missing.add("valor");
        if (data == null) missing.add("data");
        if (carteira.name() == null) missing.add("contaNome");
        if (categoria.name() == null) missing.add("categoriaNome");
        String descricao = description(text, ownedWallets, ownedCategories);
        if (descricao.isBlank()) missing.add("descricao");

        TransactionDraftV1 draft = new TransactionDraftV1("CREATE_TRANSACTION", tipo, valor,
                descricao, data, carteira.name(), categoria.name(), missing);
        if (missing.isEmpty()) return new FinancialParseResult(ParseOutcome.COMPLETE, draft, null);
        if (missing.size() == 1) return new FinancialParseResult(ParseOutcome.NEEDS_ONE_FIELD, draft, question(missing.get(0)));
        return new FinancialParseResult(ParseOutcome.NEEDS_FORM, draft, null);
    }

    private TipoTransacao direction(String text) {
        boolean entrada = text.matches(".*\\b(recebi|ganhei|entrou|entrada|salario|salário)\\b.*");
        boolean saida = text.matches(".*\\b(gastei|paguei|comprei|saida|saída|mercado|gasolina)\\b.*");
        return entrada == saida ? null : entrada ? TipoTransacao.ENTRADA : TipoTransacao.SAIDA;
    }

    private BigDecimal amount(String text) {
        Matcher matcher = VALUE.matcher(text);
        BigDecimal found = null;
        while (matcher.find()) {
            String raw = matcher.group(1);
            // Datas isoladas não são valores financeiros.
            if (matcher.end() < text.length() && text.charAt(matcher.end()) == '/') continue;
            BigDecimal candidate = new BigDecimal(raw.replace(".", "").replace(',', '.'));
            if (candidate.signum() <= 0 || found != null) return null;
            found = candidate.setScale(2);
        }
        return found;
    }

    private LocalDate date(String text) {
        LocalDate today = LocalDate.now(clock);
        if (text.matches(".*\\bhoje\\b.*")) return today;
        if (text.matches(".*\\bontem\\b.*")) return today.minusDays(1);
        try {
            Matcher iso = ISO_DATE.matcher(text);
            if (iso.find()) return LocalDate.parse(iso.group(1));
            Matcher br = BR_DATE.matcher(text);
            if (br.find()) return LocalDate.parse(br.group(1), BR);
        } catch (DateTimeParseException ignored) {
            return null;
        }
        return today;
    }

    private Match uniqueName(String text, List<String> names) {
        List<String> matches = names.stream().filter(n -> text.contains(normalize(n))).toList();
        return matches.size() == 1 ? new Match(matches.get(0), false) : new Match(null, matches.size() > 1);
    }

    private String description(String text, List<Carteira> wallets, List<Categoria> categories) {
        String result = text.replaceAll("(?i)\\b(hoje|ontem|recebi|ganhei|entrou|entrada|gastei|paguei|comprei|saida|saída|no|na|em)\\b", " ")
                .replaceAll("(?i)(?:r\\$\\s*)?[0-9.,]+", " ");
        for (String name : wallets.stream().map(Carteira::getNome).toList()) result = result.replace(normalize(name), " ");
        result = result.replaceAll("\\s+", " ").trim();
        return result.isBlank() && text.matches(".*\\b(recebi|ganhei|entrou)\\b.*") ? "Recebimento" : result;
    }

    private String question(String field) {
        return switch (field) {
            case "tipo" -> "Esse valor entrou ou saiu?";
            case "valor" -> "Qual foi o valor?";
            case "data" -> "Em que dia aconteceu?";
            case "contaNome" -> "Qual conta você usou?";
            case "categoriaNome" -> "Qual categoria devo usar?";
            default -> "Como você descreve esse lançamento?";
        };
    }

    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("\\s+", " ").trim();
    }

    private record Match(String name, boolean ambiguous) { }
}
