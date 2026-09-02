package com.gestor.financeiro.service.importacao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.model.enums.ImportFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Lê o snapshot NDJSON produzido a partir de um conector regulado (ADR-0019).
 *
 * <p>Não fala com a rede. Quem busca páginas, renova token e carrega cursor vive em
 * {@code service.openfinance} e entrega aqui um arquivo já materializado — o mesmo contrato de
 * bytes que CSV e OFX usam. É isso que faz o {@code file_sha256} continuar sendo prova auditável do
 * que o parceiro respondeu, e a deduplicação, a prévia e o commit valerem sem código novo.</p>
 *
 * <p>Formato: primeira linha é o envelope, demais são fatos, um por linha.</p>
 * <pre>
 * {"schema":"gf-openfinance-v1","institution":"BANCO-X","account":"ext-1",
 *  "currency":"BRL","openingBalance":"100.00","closingBalance":"54.10"}
 * {"externalId":"tx-1","date":"2026-08-02T10:00:00-03:00","description":"Mercado",
 *  "amount":"-45.90","currency":"BRL","direction":"SAIDA"}
 * </pre>
 */
@Component
public final class OpenFinanceNdjsonConnector implements FinancialDataConnector {

    static final String SCHEMA = "gf-openfinance-v1";

    private final ImportLimits limits;
    private final CanonicalNormalizer normalizer;
    private final ObjectMapper mapper;
    private final Clock clock;

    public OpenFinanceNdjsonConnector(ImportLimits limits, CanonicalNormalizer normalizer,
                                      ObjectMapper mapper, Clock clock) {
        this.limits = limits;
        this.normalizer = normalizer;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public ImportFormat format() {
        return ImportFormat.OPEN_FINANCE;
    }

    /**
     * Detecção nunca julga conteúdo alheio com exceção.
     *
     * <p>Quando nenhum conector reivindica o arquivo, o registro reexecuta {@code detect} e propaga
     * a {@link ImportParsingException} que encontrar. Se este conector estourasse ao ver um CSV, o
     * titular receberia "não é JSON válido" no lugar de "formato não reconhecido" — mensagem errada
     * para um arquivo que nunca foi um snapshot de conector. Só arquivo vazio continua sendo falha,
     * porque aí não há formato nenhum a discutir.</p>
     */
    @Override
    public ConnectorDetection detect(ImportSource source) throws IOException {
        try (Reader reader = leitor(source)) {
            String primeira = lerLinha(reader);
            if (primeira == null) throw new ImportParsingException(ImportFailureCode.EMPTY_FILE, "Arquivo vazio");
            JsonNode envelope;
            try {
                envelope = json(primeira, 0);
            } catch (ImportParsingException naoEJson) {
                return naoReivindicado();
            }
            if (!SCHEMA.equals(texto(envelope, "schema"))) return naoReivindicado();
            return new ConnectorDetection(ImportFormat.OPEN_FINANCE, texto(envelope, "institution"), 100);
        } catch (ImportParsingException limite) {
            if (limite.code() == ImportFailureCode.EMPTY_FILE) throw limite;
            return naoReivindicado();
        }
    }

    private ConnectorDetection naoReivindicado() {
        return new ConnectorDetection(ImportFormat.OPEN_FINANCE, null, 0);
    }

    /** Campos são nomeados no envelope; mapeamento de colunas é conceito de CSV e não se aplica. */
    @Override
    public void parse(ImportSource source, ImportMapping mapeamento, RecordConsumer consumer) throws IOException {
        if (source.size() > limits.fileBytes()) {
            throw new ImportParsingException(ImportFailureCode.FILE_LIMIT_EXCEEDED, "Arquivo excede limite");
        }
        try (Reader reader = leitor(source)) {
            JsonNode envelope = envelope(lerLinha(reader));
            String instituicao = texto(envelope, "institution");
            String moedaPadrao = texto(envelope, "currency");

            int linha = 1;
            String bruta;
            while ((bruta = lerLinha(reader)) != null) {
                if (bruta.isBlank()) { linha++; continue; }
                if (linha > limits.records()) {
                    throw new ImportParsingException(ImportFailureCode.ROW_LIMIT_EXCEEDED, "Excede limite de registros");
                }
                JsonNode fato = json(bruta, linha);
                consumer.accept(normalizer.normalize(
                        linha,
                        instituicao,
                        texto(fato, "externalId"),
                        dataDeNegocio(texto(fato, "date")),
                        texto(fato, "description"),
                        texto(fato, "amount"),
                        moeda(fato, moedaPadrao),
                        texto(fato, "direction")));
                linha++;
            }
        }
    }

    @Override
    public ImportStatementBalances declaredBalances(ImportSource source, ImportMapping mapeamento) throws IOException {
        try (Reader reader = leitor(source)) {
            JsonNode envelope = envelope(lerLinha(reader));
            BigDecimal abertura = decimal(envelope, "openingBalance");
            BigDecimal fechamento = decimal(envelope, "closingBalance");
            return abertura == null || fechamento == null
                    ? ImportStatementBalances.unavailable()
                    : new ImportStatementBalances(abertura, fechamento);
        }
    }

    /**
     * Converte o instante do parceiro para a data de negócio (ADR-0003 e ADR-0021).
     *
     * <p>Deixar a conversão para o normalizador usaria o offset que o parceiro mandou, não o fuso
     * do titular. Perto da virada do dia isso move a transação de mês e desalinha fatura, orçamento
     * e conciliação de saldo de uma vez só.</p>
     */
    private String dataDeNegocio(String valor) {
        if (valor == null || valor.isBlank()) return valor;
        try {
            return OffsetDateTime.parse(valor.trim()).atZoneSameInstant(clock.getZone())
                    .toLocalDate().toString();
        } catch (DateTimeParseException naoTemOffset) {
            // Data pura já é data de negócio declarada; segue para o normalizador como está.
            return valor;
        }
    }

    private JsonNode envelope(String primeiraLinha) throws IOException {
        if (primeiraLinha == null) throw new ImportParsingException(ImportFailureCode.EMPTY_FILE, "Arquivo vazio");
        JsonNode envelope = json(primeiraLinha, 0);
        if (!SCHEMA.equals(texto(envelope, "schema"))) {
            throw new ImportParsingException(ImportFailureCode.FORMAT_MISMATCH, "Envelope de conector inválido");
        }
        // Instituição é obrigatória, não decorativa: a identidade forte da deduplicação compara
        // instituição junto com o id externo. Sem ela, dois lotes de bancos diferentes casariam
        // pelo id externo sozinho, e o titular veria como duplicado o que são dois fatos reais.
        String instituicao = texto(envelope, "institution");
        if (instituicao == null || instituicao.isBlank()) {
            throw new ImportParsingException(ImportFailureCode.FORMAT_MISMATCH,
                    "Envelope de conector sem instituição");
        }
        return envelope;
    }

    private JsonNode json(String linha, int numero) throws IOException {
        try {
            JsonNode node = mapper.readTree(linha);
            if (node == null || !node.isObject()) {
                throw new ImportParsingException(ImportFailureCode.PARSE_FAILED, "Linha " + numero + " não é objeto JSON");
            }
            return node;
        } catch (JsonProcessingException invalido) {
            // Sem eco do conteúdo: a linha carrega dado bancário do titular.
            throw new ImportParsingException(ImportFailureCode.PARSE_FAILED, "Linha " + numero + " não é JSON válido");
        }
    }

    private Reader leitor(ImportSource source) throws IOException {
        InputStream input = source.openStream();
        return new InputStreamReader(new java.io.BufferedInputStream(input), StandardCharsets.UTF_8);
    }

    /**
     * Lê uma linha com teto de caracteres.
     *
     * <p>{@code BufferedReader.readLine()} cresce até acabar a memória se o conteúdo não tiver
     * quebra de linha. Como o arquivo vem de fora, um snapshot malformado de uma única linha
     * derrubaria o processo inteiro em vez de falhar a importação.</p>
     */
    private String lerLinha(Reader reader) throws IOException {
        StringBuilder linha = new StringBuilder();
        int lido;
        while ((lido = reader.read()) != -1) {
            char caractere = (char) lido;
            if (caractere == '\n') return linha.toString();
            if (caractere == '\r') continue;
            if (linha.length() >= limits.recordChars()) {
                throw new ImportParsingException(ImportFailureCode.STRUCTURE_LIMIT_EXCEEDED,
                        "Registro excede limite estrutural");
            }
            linha.append(caractere);
        }
        return linha.length() == 0 ? null : linha.toString();
    }

    private String texto(JsonNode node, String campo) {
        JsonNode valor = node.get(campo);
        if (valor == null || valor.isNull()) return null;
        String texto = valor.isTextual() ? valor.textValue() : valor.asText();
        if (texto.length() > limits.fieldChars()) return texto.substring(0, limits.fieldChars());
        return texto;
    }

    private String moeda(JsonNode fato, String padrao) {
        String propria = texto(fato, "currency");
        return propria == null || propria.isBlank() ? padrao : propria;
    }

    private BigDecimal decimal(JsonNode node, String campo) {
        String valor = texto(node, campo);
        if (valor == null || valor.isBlank()) return null;
        try {
            return new BigDecimal(valor.trim());
        } catch (NumberFormatException invalido) {
            return null;
        }
    }
}
