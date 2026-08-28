package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public final class CsvImportConnector implements FinancialDataConnector {
    private static final char[] DELIMITERS = {',', ';', '\t', '|'};
    private static final Map<String, String> ALIASES = aliases();
    private final ImportLimits limits;
    private final CanonicalNormalizer normalizer;

    public CsvImportConnector(ImportLimits limits, CanonicalNormalizer normalizer) {
        this.limits = limits; this.normalizer = normalizer;
    }

    @Override public ConnectorDetection detect(ImportSource source) throws IOException {
        CsvShape shape = inspect(source);
        return new ConnectorDetection(ImportFormat.CSV, null, shape.score());
    }

    @Override public void parse(ImportSource source, ImportMapping mapeamento, RecordConsumer consumer)
            throws IOException {
        CsvShape shape = inspect(source);
        // Mapeamento do titular manda sobre a detecção: ele viu o arquivo e disse qual coluna é qual.
        char delimitador = mapeamento != null && mapeamento.delimitador() != null
                ? mapeamento.delimitador() : shape.delimiter();
        boolean comMapeamento = mapeamento != null && !mapeamento.vazio();
        if (!comMapeamento && shape.score() < 80) {
            throw new ImportParsingException(ImportFailureCode.FORMAT_MISMATCH, "Estrutura CSV inválida");
        }
        CsvParserSettings settings = new CsvParserSettings();
        settings.getFormat().setDelimiter(delimitador);
        settings.setHeaderExtractionEnabled(true);
        settings.setMaxColumns(limits.csvColumns());
        settings.setMaxCharsPerColumn(limits.recordChars());
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setSkipEmptyLines(true);
        settings.setNullValue(null);
        settings.setEmptyValue("");
        CsvParser parser = new CsvParser(settings);
        int records = 0;
        try (InputStream input = source.openStream(); InputStreamReader reader = new InputStreamReader(input, shape.charset())) {
            parser.beginParsing(reader);
            String[] row;
            while ((row = parser.parseNext()) != null) {
                if (++records > limits.records()) throw new ImportParsingException(ImportFailureCode.ROW_LIMIT_EXCEEDED, "Limite de registros excedido");
                String[] headers = parser.getContext().headers();
                Map<String, String> fields = new HashMap<>();
                int logicalChars = 0;
                for (int i = 0; i < row.length; i++) {
                    String value = row[i]; logicalChars += value == null ? 0 : value.length();
                    if (logicalChars > limits.recordChars()) throw new ImportParsingException(ImportFailureCode.STRUCTURE_LIMIT_EXCEEDED, "Registro excede limite estrutural");
                    String canonical = null;
                    if (i < headers.length) {
                        canonical = comMapeamento
                                ? mapeamento.campoDaColuna(key(headers[i]))
                                : ALIASES.get(key(headers[i]));
                    }
                    if (canonical != null) fields.put(canonical, value);
                }
                consumer.accept(normalizer.normalize(records + 1, null, fields.get("externalId"), fields.get("date"),
                        fields.get("description"), fields.get("amount"), fields.get("currency"), fields.get("direction")));
            }
        } catch (ImportParsingException e) { throw e; }
        catch (RuntimeException e) { throw new ImportParsingException(ImportFailureCode.PARSE_FAILED, "CSV inválido", e); }
        finally { parser.stopParsing(); }
    }

    @Override
    public ImportStatementBalances declaredBalances(ImportSource source, ImportMapping mapeamento) throws IOException {
        CsvShape shape = inspect(source);
        char delimiter = mapeamento != null && mapeamento.delimitador() != null
                ? mapeamento.delimitador() : shape.delimiter();
        boolean mapped = mapeamento != null && !mapeamento.vazio();
        CsvParserSettings settings = new CsvParserSettings();
        settings.getFormat().setDelimiter(delimiter);
        settings.setHeaderExtractionEnabled(true);
        settings.setMaxColumns(limits.csvColumns());
        settings.setMaxCharsPerColumn(limits.recordChars());
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setSkipEmptyLines(true);
        CsvParser parser = new CsvParser(settings);
        java.math.BigDecimal opening = null, closing = null;
        try (InputStream input = source.openStream(); InputStreamReader reader = new InputStreamReader(input, shape.charset())) {
            parser.beginParsing(reader); String[] row; int records = 0;
            while ((row = parser.parseNext()) != null) {
                if (++records > limits.records()) throw new ImportParsingException(
                        ImportFailureCode.ROW_LIMIT_EXCEEDED, "Limite de registros excedido");
                String[] headers = parser.getContext().headers();
                for (int i = 0; i < row.length && i < headers.length; i++) {
                    String canonical = mapped ? mapeamento.campoDaColuna(key(headers[i])) : ALIASES.get(key(headers[i]));
                    if ("openingBalance".equals(canonical) && opening == null)
                        opening = DeclaredBalanceParser.parse(row[i]);
                    if ("closingBalance".equals(canonical)) {
                        java.math.BigDecimal candidate = DeclaredBalanceParser.parse(row[i]);
                        if (candidate != null) closing = candidate;
                    }
                }
            }
            return new ImportStatementBalances(opening, closing);
        } catch (ImportParsingException failure) { throw failure; }
        catch (RuntimeException failure) {
            throw new ImportParsingException(ImportFailureCode.PARSE_FAILED, "Saldos CSV inválidos", failure);
        } finally { parser.stopParsing(); }
    }

    /**
     * Cabeçalhos e delimitador do arquivo, para o titular montar um mapeamento.
     *
     * <p>Devolve só os nomes das colunas — nenhuma linha de dado. O cabeçalho é estrutura; as
     * linhas são a vida financeira da pessoa, e elas não precisam trafegar para isto.</p>
     */
    public CsvInspecao inspecionarCabecalhos(ImportSource source) throws IOException {
        CsvShape shape = inspect(source);
        CsvParserSettings settings = new CsvParserSettings();
        settings.getFormat().setDelimiter(shape.delimiter());
        settings.setHeaderExtractionEnabled(true);
        settings.setMaxColumns(limits.csvColumns());
        settings.setMaxCharsPerColumn(limits.recordChars());
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setSkipEmptyLines(true);

        CsvParser parser = new CsvParser(settings);
        try (InputStream input = source.openStream();
             InputStreamReader reader = new InputStreamReader(input, shape.charset())) {
            parser.beginParsing(reader);
            parser.parseNext();
            String[] headers = parser.getContext() == null ? null : parser.getContext().headers();
            java.util.List<String> cabecalhos = headers == null ? java.util.List.of()
                    : java.util.Arrays.stream(headers).filter(java.util.Objects::nonNull).toList();
            return new CsvInspecao(String.valueOf(shape.delimiter()), cabecalhos);
        } finally {
            parser.stopParsing();
        }
    }

    /** Estrutura do arquivo, sem conteúdo. */
    public record CsvInspecao(String delimitador, java.util.List<String> cabecalhos) { }

    private CsvShape inspect(ImportSource source) throws IOException {
        if (source.size() == 0) throw new ImportParsingException(ImportFailureCode.EMPTY_FILE, "Arquivo vazio");
        if (source.size() > limits.fileBytes()) throw new ImportParsingException(ImportFailureCode.FILE_LIMIT_EXCEEDED, "Arquivo excede limite");
        byte[] sample;
        try (InputStream input = new BufferedInputStream(source.openStream())) { sample = input.readNBytes(limits.detectionBytes()); }
        Charset charset = charset(sample, source.contentType());
        String text = decode(sample, charset).replaceFirst("^\\uFEFF", "");
        String first = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        if (first.isEmpty()) throw new ImportParsingException(ImportFailureCode.EMPTY_FILE, "Arquivo vazio");
        char best = 0; int bestScore = 0; int second = 0;
        for (char delimiter : DELIMITERS) {
            String[] headers = first.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)), -1);
            if (headers.length < 3 || headers.length > limits.csvColumns()) continue;
            Set<String> canonical = new java.util.HashSet<>();
            for (String header : headers) { String mapped = ALIASES.get(key(header)); if (mapped != null) canonical.add(mapped); }
            int score = canonical.containsAll(Set.of("date", "description", "amount")) ? 90 : canonical.size() * 20;
            if (score > bestScore) { second = bestScore; bestScore = score; best = delimiter; }
            else second = Math.max(second, score);
        }
        if (bestScore < 80) return new CsvShape(charset, best, bestScore);
        if (bestScore - second < 15) throw new ImportParsingException(ImportFailureCode.DETECTION_FAILED, "Delimitador ambíguo");
        return new CsvShape(charset, best, bestScore);
    }

    private Charset charset(byte[] bytes, String hint) throws ImportParsingException {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) return StandardCharsets.UTF_8;
        if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) return StandardCharsets.UTF_16LE;
        if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) return StandardCharsets.UTF_16BE;
        try { decode(bytes, StandardCharsets.UTF_8); return StandardCharsets.UTF_8; }
        catch (ImportParsingException ignored) { }
        String safeHint = hint == null ? "" : hint.toLowerCase(Locale.ROOT);
        if (safeHint.contains("windows-1252")) return Charset.forName("windows-1252");
        if (safeHint.contains("iso-8859-1")) return StandardCharsets.ISO_8859_1;
        throw new ImportParsingException(ImportFailureCode.CHARSET_UNSUPPORTED, "Charset não suportado");
    }

    private String decode(byte[] bytes, Charset charset) throws ImportParsingException {
        try { return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw new ImportParsingException(ImportFailureCode.CHARSET_UNSUPPORTED, "Charset inválido", e); }
    }
    private String key(String value) { return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT); }
    private static Map<String, String> aliases() {
        Map<String, String> map = new HashMap<>();
        for (String value : Set.of("data", "date")) map.put(value, "date");
        for (String value : Set.of("descricao", "description", "memo", "historico")) map.put(value, "description");
        for (String value : Set.of("valor", "amount")) map.put(value, "amount");
        for (String value : Set.of("moeda", "currency")) map.put(value, "currency");
        for (String value : Set.of("direcao", "tipo", "type", "trntype")) map.put(value, "direction");
        for (String value : Set.of("external id", "external_id", "fitid")) map.put(value, "externalId");
        for (String value : Set.of("saldo inicial", "opening balance", "opening_balance")) map.put(value, "openingBalance");
        for (String value : Set.of("saldo final", "closing balance", "closing_balance")) map.put(value, "closingBalance");
        return Map.copyOf(map);
    }
    private record CsvShape(Charset charset, char delimiter, int score) { }
}
