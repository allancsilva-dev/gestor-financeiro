package com.gestor.financeiro.service.importacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.model.enums.TipoTransacao;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O que o snapshot do parceiro pode e não pode fazer com o pipeline canônico. */
class OpenFinanceNdjsonConnectorTest {

    private static final String ENVELOPE = "{\"schema\":\"gf-openfinance-v1\",\"institution\":\"BANCO-X\","
            + "\"account\":\"ext-1\",\"currency\":\"BRL\",\"openingBalance\":\"100.00\",\"closingBalance\":\"54.10\"}";

    private final ImportLimits limits = new ImportLimits(10_485_760L, 65536, 50_000, 65536, 8192, 64, 32, 500_000, 8192, 250);
    private final Clock clockSaoPaulo = Clock.system(ZoneId.of("America/Sao_Paulo"));

    private OpenFinanceNdjsonConnector connector() {
        return new OpenFinanceNdjsonConnector(limits, new CanonicalNormalizer(), new ObjectMapper(), clockSaoPaulo);
    }

    private ImportSource fonte(String conteudo) {
        byte[] bytes = conteudo.getBytes(StandardCharsets.UTF_8);
        return new ImportSource() {
            @Override public InputStream openStream() { return new ByteArrayInputStream(bytes); }
            @Override public long size() { return bytes.length; }
            @Override public String displayName() { return "snapshot"; }
            @Override public String contentType() { return "application/x-ndjson"; }
            @Override public String sha256() { return null; }
        };
    }

    private List<CanonicalImportRecord> parse(String conteudo) throws IOException {
        List<CanonicalImportRecord> lidos = new ArrayList<>();
        connector().parse(fonte(conteudo), ImportMapping.automatico(), lidos::add);
        return lidos;
    }

    @Test
    void declaraFormatoProprioEReconheceOEnvelope() throws IOException {
        ConnectorDetection deteccao = connector().detect(fonte(ENVELOPE + "\n"));
        assertEquals(ImportFormat.OPEN_FINANCE, connector().format());
        assertEquals(ImportFormat.OPEN_FINANCE, deteccao.format());
        assertEquals("BANCO-X", deteccao.institutionCode());
        assertEquals(100, deteccao.confidence());
    }

    /** Confiança zero, e não exceção: recusar aqui impediria outro conector de reivindicar o arquivo. */
    @Test
    void arquivoDeOutroFormatoNaoEReivindicado() throws IOException {
        assertEquals(0, connector().detect(fonte("data;valor\n01/08/2026;10,00\n")).confidence());
    }

    @Test
    void leFatosDoSnapshot() throws IOException {
        List<CanonicalImportRecord> lidos = parse(ENVELOPE + "\n"
                + "{\"externalId\":\"tx-1\",\"date\":\"2026-08-02\",\"description\":\"Mercado\","
                + "\"amount\":\"-45.90\",\"currency\":\"BRL\"}\n"
                + "{\"externalId\":\"tx-2\",\"date\":\"2026-08-03\",\"description\":\"Salario\","
                + "\"amount\":\"1000.00\"}\n");

        assertEquals(2, lidos.size());
        assertEquals("tx-1", lidos.get(0).externalId());
        assertEquals(TipoTransacao.SAIDA, lidos.get(0).direction());
        assertEquals(0, new BigDecimal("45.90").compareTo(lidos.get(0).amount()));
        assertEquals(ImportRecordStatus.VALID, lidos.get(0).status());
        // Moeda do envelope vale para o fato que não declara a sua.
        assertEquals("BRL", lidos.get(1).currency());
        assertEquals(TipoTransacao.ENTRADA, lidos.get(1).direction());
    }

    /**
     * A regra do ADR-0021 que mais silenciosamente estragaria contas: instante perto da virada do
     * dia precisa cair no dia de negócio do titular, não no dia do offset que o parceiro mandou.
     */
    @Test
    void instanteDoParceiroViraDataDeNegocioDoTitular() throws IOException {
        // 23:30 em Nova York é 01:30 do dia seguinte em São Paulo.
        List<CanonicalImportRecord> lidos = parse(ENVELOPE + "\n"
                + "{\"externalId\":\"tx-1\",\"date\":\"2026-08-31T23:30:00-04:00\",\"description\":\"Virada\","
                + "\"amount\":\"-10.00\"}\n");

        assertEquals("2026-09-01", lidos.get(0).occurredOn().toString());
    }

    @Test
    void dataPuraSegueComoDataDeNegocioDeclarada() throws IOException {
        List<CanonicalImportRecord> lidos = parse(ENVELOPE + "\n"
                + "{\"externalId\":\"tx-1\",\"date\":\"2026-08-31\",\"description\":\"Simples\",\"amount\":\"-10.00\"}\n");
        assertEquals("2026-08-31", lidos.get(0).occurredOn().toString());
    }

    @Test
    void saldosDeclaradosAlimentamAConciliacaoDoLote() throws IOException {
        ImportStatementBalances saldos = connector().declaredBalances(fonte(ENVELOPE + "\n"), ImportMapping.automatico());
        assertEquals(0, new BigDecimal("100.00").compareTo(saldos.opening()));
        assertEquals(0, new BigDecimal("54.10").compareTo(saldos.closing()));
    }

    @Test
    void envelopeSemSaldoDeixaConciliacaoIndisponivel() throws IOException {
        String semSaldo = "{\"schema\":\"gf-openfinance-v1\",\"institution\":\"BANCO-X\"}";
        ImportStatementBalances saldos = connector().declaredBalances(fonte(semSaldo + "\n"), ImportMapping.automatico());
        assertNull(saldos.opening());
        assertNull(saldos.closing());
    }

    @Test
    void envelopeDeOutroEsquemaERecusado() {
        String outro = "{\"schema\":\"outra-coisa\"}";
        ImportParsingException falha = assertThrows(ImportParsingException.class,
                () -> parse(outro + "\n{\"externalId\":\"tx-1\"}\n"));
        assertEquals(ImportFailureCode.FORMAT_MISMATCH, falha.code());
    }

    @Test
    void linhaQueNaoEJsonFalhaSemEcoarConteudo() {
        ImportParsingException falha = assertThrows(ImportParsingException.class,
                () -> parse(ENVELOPE + "\nisto nao e json\n"));
        assertEquals(ImportFailureCode.PARSE_FAILED, falha.code());
        assertTrue(falha.getMessage().contains("Linha 1"));
        assertTrue(!falha.getMessage().contains("isto nao e json"),
                "mensagem de erro não pode ecoar dado bancário");
    }

    /**
     * Instituição é o que a identidade forte da deduplicação compara junto com o id externo. Um
     * snapshot sem ela faria dois bancos diferentes casarem pelo id sozinho, e o titular veria como
     * duplicado o que são dois fatos reais.
     */
    @Test
    void envelopeSemInstituicaoERecusado() {
        String semInstituicao = "{\"schema\":\"gf-openfinance-v1\",\"currency\":\"BRL\"}";
        ImportParsingException falha = assertThrows(ImportParsingException.class,
                () -> parse(semInstituicao + "\n{\"externalId\":\"tx-1\"}\n"));
        assertEquals(ImportFailureCode.FORMAT_MISMATCH, falha.code());
    }

    @Test
    void arquivoVazioEFalhaExplicita() {
        ImportParsingException falha = assertThrows(ImportParsingException.class, () -> parse(""));
        assertEquals(ImportFailureCode.EMPTY_FILE, falha.code());
    }

    /**
     * Snapshot malformado de uma única linha derrubaria o processo inteiro com
     * {@code BufferedReader.readLine()}, que cresce até acabar a memória. Precisa falhar a
     * importação, não a instância.
     */
    @Test
    void linhaGiganteSemQuebraFalhaPorLimiteEstrutural() throws IOException {
        ImportLimits apertado = new ImportLimits(10_485_760L, 65536, 50_000, 64, 32, 64, 32, 500_000, 8192, 250);
        OpenFinanceNdjsonConnector connector = new OpenFinanceNdjsonConnector(
                apertado, new CanonicalNormalizer(), new ObjectMapper(), clockSaoPaulo);
        String gigante = "{\"schema\":\"gf-openfinance-v1\",\"institution\":\"" + "X".repeat(5_000) + "\"}";

        ImportParsingException falha = assertThrows(ImportParsingException.class,
                () -> connector.parse(fonte(gigante), ImportMapping.automatico(), r -> { }));
        assertEquals(ImportFailureCode.STRUCTURE_LIMIT_EXCEEDED, falha.code());

        // Na detecção o mesmo arquivo apenas não é reivindicado: julgar conteúdo alheio com
        // exceção sequestraria a mensagem de erro de quem enviou outro formato.
        assertEquals(0, connector.detect(fonte(gigante)).confidence());
    }

    @Test
    void arquivoAcimaDoLimiteNaoEParseado() {
        ImportSource grande = new ImportSource() {
            @Override public InputStream openStream() { return new ByteArrayInputStream(new byte[0]); }
            @Override public long size() { return 20_000_000L; }
            @Override public String displayName() { return "grande"; }
            @Override public String contentType() { return "application/x-ndjson"; }
            @Override public String sha256() { return null; }
        };
        ImportParsingException falha = assertThrows(ImportParsingException.class,
                () -> connector().parse(grande, ImportMapping.automatico(), r -> { }));
        assertEquals(ImportFailureCode.FILE_LIMIT_EXCEEDED, falha.code());
    }

    @Test
    void excessoDeRegistrosFalhaPorLimiteDeLinhas() {
        ImportLimits poucos = new ImportLimits(10_485_760L, 65536, 2, 65536, 8192, 64, 32, 500_000, 8192, 250);
        OpenFinanceNdjsonConnector connector = new OpenFinanceNdjsonConnector(
                poucos, new CanonicalNormalizer(), new ObjectMapper(), clockSaoPaulo);
        StringBuilder conteudo = new StringBuilder(ENVELOPE).append('\n');
        for (int i = 0; i < 5; i++) {
            conteudo.append("{\"externalId\":\"tx-").append(i)
                    .append("\",\"date\":\"2026-08-02\",\"description\":\"X\",\"amount\":\"-1.00\"}\n");
        }
        ImportParsingException falha = assertThrows(ImportParsingException.class,
                () -> connector.parse(fonte(conteudo.toString()), ImportMapping.automatico(), r -> { }));
        assertEquals(ImportFailureCode.ROW_LIMIT_EXCEEDED, falha.code());
    }
}
