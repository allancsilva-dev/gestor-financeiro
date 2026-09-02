package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.model.enums.ImportFormat;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public final class OfxImportConnector implements FinancialDataConnector {
    private static final Set<String> FIELDS = Set.of("FITID", "DTPOSTED", "TRNAMT", "TRNTYPE", "NAME", "MEMO",
            "CURDEF", "ORG", "FID", "BANKID", "BALAMT", "OPENINGBAL", "CLOSINGBAL", "BALOPEN", "BALCLOSE");
    private final ImportLimits limits;
    private final CanonicalNormalizer normalizer;

    public OfxImportConnector(ImportLimits limits, CanonicalNormalizer normalizer) { this.limits = limits; this.normalizer = normalizer; }

    @Override public ImportFormat format() { return ImportFormat.OFX; }

    @Override public ConnectorDetection detect(ImportSource source) throws IOException {
        byte[] sample;
        try (InputStream input = new BufferedInputStream(source.openStream())) { sample = input.readNBytes(limits.detectionBytes()); }
        if (sample.length == 0) throw new ImportParsingException(ImportFailureCode.EMPTY_FILE, "Arquivo vazio");
        String value = new String(sample, StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
        rejectDangerous(value);
        boolean root = value.contains("<OFX>");
        boolean statement = value.contains("<STMTRS>") || value.contains("<CCSTMTRS>");
        boolean transaction = value.contains("<STMTTRN>");
        int score = root && statement ? (transaction ? 100 : 90) : 0;
        return new ConnectorDetection(ImportFormat.OFX, institution(value), score);
    }

    /** OFX é marcado por tag, não por coluna: mapeamento de colunas não se aplica. */
    @Override public void parse(ImportSource source, ImportMapping mapeamento, RecordConsumer consumer)
            throws IOException {
        if (source.size() > limits.fileBytes()) throw new ImportParsingException(ImportFailureCode.FILE_LIMIT_EXCEEDED, "Arquivo excede limite");
        byte[] prefix;
        try (InputStream input = new BufferedInputStream(source.openStream())) { prefix = input.readNBytes(limits.ofxHeaderBytes()); }
        String header = new String(prefix, StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
        rejectDangerous(header);
        if (header.contains("<INVSTMTRS") || header.contains("<INVTRAN"))
            throw new ImportParsingException(ImportFailureCode.UNSUPPORTED_FORMAT, "Statement de investimento não suportado");
        if (!header.contains("<OFX>")) throw new ImportParsingException(ImportFailureCode.FORMAT_MISMATCH, "Estrutura OFX inválida");
        boolean xml = header.stripLeading().startsWith("<?XML") || !header.startsWith("OFXHEADER:");
        if (xml) parseXml(source, consumer); else parseSgml(source, consumer);
    }

    @Override
    public ImportStatementBalances declaredBalances(ImportSource source, ImportMapping ignored) throws IOException {
        if (source.size() > limits.fileBytes()) throw new ImportParsingException(
                ImportFailureCode.FILE_LIMIT_EXCEEDED, "Arquivo excede limite");
        byte[] bytes;
        try (InputStream input = source.openStream()) { bytes = input.readNBytes((int) limits.fileBytes() + 1); }
        if (bytes.length > limits.fileBytes()) throw new ImportParsingException(
                ImportFailureCode.FILE_LIMIT_EXCEEDED, "Arquivo excede limite");
        String value = new String(bytes, StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
        rejectDangerous(value);
        String opening = first(leafUnchecked(value, "OPENINGBAL"), leafUnchecked(value, "BALOPEN"));
        String closing = first(leafUnchecked(value, "CLOSINGBAL"), leafUnchecked(value, "BALCLOSE"),
                leafUnchecked(value, "BALAMT"));
        return new ImportStatementBalances(DeclaredBalanceParser.parse(opening), DeclaredBalanceParser.parse(closing));
    }

    private void parseXml(ImportSource source, RecordConsumer consumer) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        XMLResolver resolver = (publicID, systemID, baseURI, namespace) -> { throw new XMLStreamException("External resolution disabled"); };
        factory.setXMLResolver(resolver);
        try (InputStream input = source.openStream()) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            Map<String, String> statement = new HashMap<>(); Map<String, String> transaction = null;
            int depth = 0, elements = 0, records = 0;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (++depth > limits.ofxDepth() || ++elements > limits.ofxElements()) limit();
                    String name = reader.getLocalName().toUpperCase(Locale.ROOT);
                    if (name.equals("INVSTMTRS")) throw new ImportParsingException(ImportFailureCode.UNSUPPORTED_FORMAT, "Statement não suportado");
                    if (name.equals("STMTTRN")) transaction = new HashMap<>();
                    else if (FIELDS.contains(name)) {
                        String text = reader.getElementText(); depth--;
                        if (text.length() > limits.fieldChars()) limit();
                        (transaction == null ? statement : transaction).put(name, text);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName().toUpperCase(Locale.ROOT);
                    if (name.equals("STMTTRN") && transaction != null) {
                        if (++records > limits.records()) throw new ImportParsingException(ImportFailureCode.ROW_LIMIT_EXCEEDED, "Limite de registros excedido");
                        emit(records, statement, transaction, consumer); transaction = null;
                    }
                    depth--;
                } else if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw new ImportParsingException(ImportFailureCode.FORMAT_MISMATCH, "Construção XML proibida");
                }
            }
        } catch (ImportParsingException e) { throw e; }
        catch (XMLStreamException e) { throw new ImportParsingException(ImportFailureCode.PARSE_FAILED, "OFX XML inválido", e); }
    }

    private void parseSgml(ImportSource source, RecordConsumer consumer) throws IOException {
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(source.openStream(), StandardCharsets.US_ASCII), 8192)) {
            Map<String, String> statement = new HashMap<>(); Map<String, String> transaction = null;
            String line; int records = 0, elements = 0;
            while ((line = reader.readLine()) != null) {
                if (line.length() > limits.recordChars()) limit();
                String upper = line.toUpperCase(Locale.ROOT); rejectDangerous(upper);
                if (upper.contains("<STMTTRN>")) transaction = new HashMap<>();
                for (String field : FIELDS) {
                    String value = leaf(line, upper, field);
                    if (value != null) (transaction == null ? statement : transaction).put(field, value);
                    if (value != null && ++elements > limits.ofxElements()) limit();
                }
                if (transaction != null && (upper.contains("</STMTTRN>") || (transaction.containsKey("TRNAMT") && transaction.containsKey("DTPOSTED") && transaction.containsKey("FITID") && upper.contains("<STMTTRN>")))) {
                    if (++records > limits.records()) throw new ImportParsingException(ImportFailureCode.ROW_LIMIT_EXCEEDED, "Limite de registros excedido");
                    emit(records, statement, transaction, consumer); transaction = upper.contains("<STMTTRN>") ? new HashMap<>() : null;
                }
            }
            if (transaction != null && !transaction.isEmpty()) emit(++records, statement, transaction, consumer);
        }
    }

    private String leaf(String line, String upper, String field) throws ImportParsingException {
        String tag = "<" + field + ">"; int start = upper.indexOf(tag);
        if (start < 0) return null;
        start += tag.length(); int end = line.indexOf('<', start); if (end < 0) end = line.length();
        String value = line.substring(start, end).trim(); if (value.length() > limits.fieldChars()) limit(); return value;
    }
    private void emit(int record, Map<String, String> statement, Map<String, String> transaction, RecordConsumer consumer) throws IOException {
        String description = transaction.get("NAME"); if (description == null || description.isBlank()) description = transaction.get("MEMO");
        String institution = first(statement.get("BANKID"), statement.get("FID"), statement.get("ORG"));
        String date = transaction.get("DTPOSTED"); if (date != null && date.matches("\\d{8}.*")) date = date.substring(0,4)+"-"+date.substring(4,6)+"-"+date.substring(6,8);
        consumer.accept(normalizer.normalize(record, institution, transaction.get("FITID"), date, description,
                transaction.get("TRNAMT"), statement.get("CURDEF"), transaction.get("TRNTYPE")));
    }
    private String first(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return null; }
    private String institution(String value) { for (String tag : Set.of("BANKID", "FID", "ORG")) { String found = leafUnchecked(value, tag); if (found != null) { String safe = found.replaceAll("[^A-Z0-9._-]", ""); if (!safe.isEmpty()) return safe.substring(0, Math.min(80, safe.length())); } } return null; }
    private String leafUnchecked(String value, String field) { int start = value.indexOf("<"+field+">"); if (start < 0) return null; start += field.length()+2; int end = value.indexOf('<', start); return value.substring(start, end < 0 ? value.length() : end).trim(); }
    private void rejectDangerous(String value) throws ImportParsingException { if (value.contains("<!DOCTYPE") || value.contains("<!ENTITY") || value.contains("XINCLUDE") || value.contains("SYSTEM \"") || value.contains("PUBLIC \"")) throw new ImportParsingException(ImportFailureCode.FORMAT_MISMATCH, "Construção externa proibida"); }
    private void limit() throws ImportParsingException { throw new ImportParsingException(ImportFailureCode.STRUCTURE_LIMIT_EXCEEDED, "Estrutura excede limite"); }
}
