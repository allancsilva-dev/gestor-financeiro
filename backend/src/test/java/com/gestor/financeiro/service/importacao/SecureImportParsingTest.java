package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.model.enums.ImportRecordReasonCode;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.model.enums.TipoTransacao;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecureImportParsingTest {
    private final ImportLimits limits = new ImportLimits(10_485_760, 65_536, 50_000, 65_536, 8_192, 64, 32, 500_000, 8_192, 250);
    private final CanonicalNormalizer normalizer = new CanonicalNormalizer();

    @Test void parsesQuotedMultilineCsvAndIgnoresUnknownColumns() throws Exception {
        String csv = "data,descrição,valor,moeda,direção,FITID,segredo\n"
                + "2026-08-20,\"Mercado\nCentro\",-12.34,BRL,SAIDA,AbC-1,=1+1\n";
        CsvImportConnector connector = new CsvImportConnector(limits, normalizer);
        List<CanonicalImportRecord> records = new ArrayList<>();
        connector.parse(source(csv, "text/csv"), records::add);
        assertEquals(1, records.size());
        assertEquals("Mercado Centro", records.get(0).description());
        assertEquals("AbC-1", records.get(0).externalId());
        assertEquals(TipoTransacao.SAIDA, records.get(0).direction());
        assertEquals(ImportRecordStatus.VALID, records.get(0).status());
        assertTrue(records.get(0).metadata().isEmpty());
    }

    @Test void doesNotAcceptIncoherentDelimiterAndHeader() throws Exception {
        String csv = "data,date;descrição,description;valor,amount\n2026-01-01,x;y,z;1,2\n";
        CsvImportConnector connector = new CsvImportConnector(limits, normalizer);
        assertTrue(connector.detect(source(csv, "text/csv")).confidence() < 80);
    }

    @Test void parsesHardenedXmlOfxAndPreservesFitidCase() throws Exception {
        String ofx = "<?xml version=\"1.0\"?><OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL</CURDEF>"
                + "<BANKACCTFROM><BANKID>001</BANKID></BANKACCTFROM><BANKTRANLIST><STMTTRN>"
                + "<TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260820120000[-3:BRT]</DTPOSTED><TRNAMT>-10.00</TRNAMT>"
                + "<FITID>FiT-9</FITID><NAME>Café</NAME></STMTTRN></BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";
        OfxImportConnector connector = new OfxImportConnector(limits, normalizer);
        List<CanonicalImportRecord> records = new ArrayList<>();
        assertEquals(100, connector.detect(source(ofx, "application/x-ofx")).confidence());
        connector.parse(source(ofx, "application/x-ofx"), records::add);
        assertEquals(1, records.size());
        assertEquals("FiT-9", records.get(0).externalId());
        assertEquals(ImportRecordStatus.VALID, records.get(0).status());
    }

    @Test void rejectsDoctypeBeforeXmlParserCanResolveIt() {
        String ofx = "<?xml version=\"1.0\"?><!DOCTYPE OFX [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><OFX><STMTRS/></OFX>";
        OfxImportConnector connector = new OfxImportConnector(limits, normalizer);
        ImportParsingException error = assertThrows(ImportParsingException.class, () -> connector.parse(source(ofx, "application/xml"), record -> {}));
        assertEquals(ImportFailureCode.FORMAT_MISMATCH, error.code());
    }

    @Test void flagsAmbiguousAmountAndNeverDefaultsCurrency() {
        CanonicalImportRecord record = normalizer.normalize(2, null, null, "2026-08-20", "Teste", "1,234", null, null);
        assertEquals(ImportRecordStatus.PENDING_REVIEW, record.status());
        assertEquals(ImportRecordReasonCode.MULTIPLE_ISSUES, record.reasonCode());
        assertNull(record.currency());
    }

    @Test void fingerprintIsStableAndExternalIdCaseSensitive() {
        CanonicalImportRecord first = normalizer.normalize(2, "001", "AbC", "2026-08-20", "A", "10.00", "BRL", "ENTRADA");
        CanonicalImportRecord replay = normalizer.normalize(99, "001", "AbC", "2026-08-21", "B", "20.00", "USD", "SAIDA");
        CanonicalImportRecord differentCase = normalizer.normalize(2, "001", "abc", "2026-08-20", "A", "10.00", "BRL", "ENTRADA");
        assertEquals(first.fingerprint(), replay.fingerprint());
        assertNotEquals(first.fingerprint(), differentCase.fingerprint());
    }

    private ImportSource source(String value, String contentType) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new ImportSource() {
            public InputStream openStream() { return new ByteArrayInputStream(bytes); }
            public long size() { return bytes.length; }
            public String displayName() { return "must-not-be-used.csv"; }
            public String contentType() { return contentType; }
            public String sha256() {
                try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
                catch (Exception e) { throw new IllegalStateException(e); }
            }
        };
    }
}
