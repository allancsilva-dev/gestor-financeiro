package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.TestDataFactory;
import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.ImportBatchRepository;
import com.gestor.financeiro.repository.ImportRecordRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reenviar o mesmo arquivo não pode duplicar o ledger — e dois lançamentos legitimamente iguais
 * não podem ser bloqueados. Este teste fixa essa fronteira.
 */
@SpringBootTest
@ActiveProfiles("test")
class ImportDeduplicationServiceTest {

    private static final String IMPRESSAO_A = "f".repeat(64);
    private static final String IMPRESSAO_B = "e".repeat(64);

    @Autowired ImportDeduplicationService deduplicacao;
    @Autowired ImportBatchRepository batches;
    @Autowired ImportRecordRepository records;
    @Autowired UsuarioRepository usuarios;

    private Usuario titular;
    private Usuario outroTitular;

    @BeforeEach
    void setup() {
        records.deleteAll();
        batches.deleteAll();
        titular = usuarios.save(TestDataFactory.usuario("Dedupe", "dedupe-" + System.nanoTime() + "@test.local", "h"));
        outroTitular = usuarios.save(TestDataFactory.usuario("Outro", "dedupe-outro-" + System.nanoTime() + "@test.local", "h"));
    }

    @AfterEach
    void limpar() {
        records.deleteAll();
        batches.deleteAll();
        usuarios.deleteById(titular.getId());
        usuarios.deleteById(outroTitular.getId());
    }

    private ImportBatch lote(Usuario dono, String hash, String instituicao) {
        ImportBatch batch = new ImportBatch();
        batch.setUsuario(dono);
        batch.setFormat(ImportFormat.CSV);
        batch.setInstitutionCode(instituicao);
        batch.setFileSha256(hash);
        batch.setStatus(ImportBatchStatus.PARSED);
        return batches.save(batch);
    }

    private ImportRecord registro(ImportBatch batch, int linha, String impressao, String externalId,
                                  ImportRecordStatus status, String valor) {
        ImportRecord record = new ImportRecord();
        record.setBatch(batch);
        record.setSourceLine(linha);
        record.setRecordFingerprint(impressao);
        record.setExternalId(externalId);
        record.setStatus(status);
        record.setOccurredOn(LocalDate.of(2026, 8, 20));
        record.setNormalizedDescription("Mercado");
        record.setAmount(new BigDecimal(valor));
        record.setCurrency("BRL");
        record.setDirection(TipoTransacao.SAIDA);
        return records.save(record);
    }

    private ImportRecordStatus statusDe(Long id) {
        return records.findById(id).orElseThrow().getStatus();
    }

    @Test
    void reenvioDoMesmoArquivoMarcaDuplicadoEAjustaContadores() {
        ImportBatch anterior = lote(titular, "a".repeat(64), "NUBANK");
        registro(anterior, 2, IMPRESSAO_A, null, ImportRecordStatus.COMMITTED, "12.34");

        ImportBatch reenvio = lote(titular, "b".repeat(64), "NUBANK");
        ImportRecord repetido = registro(reenvio, 2, IMPRESSAO_A, null, ImportRecordStatus.VALID, "12.34");
        ImportRecord novo = registro(reenvio, 3, IMPRESSAO_B, null, ImportRecordStatus.VALID, "40.00");
        reenvio.setTotalRecords(2);
        reenvio.setValidRecords(2);
        batches.save(reenvio);

        assertEquals(1, deduplicacao.marcarDuplicados(titular.getId(), reenvio.getId()));

        assertEquals(ImportRecordStatus.DUPLICATE, statusDe(repetido.getId()));
        assertEquals(ImportRecordStatus.VALID, statusDe(novo.getId()));
        ImportBatch atualizado = batches.findById(reenvio.getId()).orElseThrow();
        assertEquals(1, atualizado.getDuplicateRecords());
        assertEquals(1, atualizado.getValidRecords());
    }

    @Test
    void identidadeExternaValeMesmoComValorDiferente() {
        ImportBatch anterior = lote(titular, "a".repeat(64), "ITAU");
        registro(anterior, 2, IMPRESSAO_A, "FITID-1", ImportRecordStatus.COMMITTED, "12.34");

        ImportBatch reenvio = lote(titular, "b".repeat(64), "ITAU");
        ImportRecord mesmoFato = registro(reenvio, 2, IMPRESSAO_B, "FITID-1", ImportRecordStatus.VALID, "99.99");

        assertEquals(1, deduplicacao.marcarDuplicados(titular.getId(), reenvio.getId()));
        assertEquals(ImportRecordStatus.DUPLICATE, statusDe(mesmoFato.getId()));
    }

    @Test
    void idExternoDeOutraInstituicaoNaoEhOMesmoFato() {
        ImportBatch anterior = lote(titular, "a".repeat(64), "ITAU");
        registro(anterior, 2, IMPRESSAO_A, "FITID-1", ImportRecordStatus.COMMITTED, "12.34");

        ImportBatch outroBanco = lote(titular, "b".repeat(64), "NUBANK");
        ImportRecord registro = registro(outroBanco, 2, IMPRESSAO_B, "FITID-1", ImportRecordStatus.VALID, "12.34");

        assertEquals(0, deduplicacao.marcarDuplicados(titular.getId(), outroBanco.getId()));
        assertEquals(ImportRecordStatus.VALID, statusDe(registro.getId()));
    }

    @Test
    void registroEmRevisaoNaoEhMarcadoPelaHeuristica() {
        ImportBatch anterior = lote(titular, "a".repeat(64), "NUBANK");
        registro(anterior, 2, IMPRESSAO_A, null, ImportRecordStatus.COMMITTED, "12.34");

        ImportBatch reenvio = lote(titular, "b".repeat(64), "NUBANK");
        ImportRecord emRevisao = registro(reenvio, 2, IMPRESSAO_A, null, ImportRecordStatus.PENDING_REVIEW, "12.34");

        assertEquals(0, deduplicacao.marcarDuplicados(titular.getId(), reenvio.getId()));
        assertEquals(ImportRecordStatus.PENDING_REVIEW, statusDe(emRevisao.getId()));
    }

    @Test
    void lancamentoDeOutroTitularNaoContamina() {
        ImportBatch alheio = lote(outroTitular, "a".repeat(64), "NUBANK");
        registro(alheio, 2, IMPRESSAO_A, "FITID-1", ImportRecordStatus.COMMITTED, "12.34");

        ImportBatch meu = lote(titular, "b".repeat(64), "NUBANK");
        ImportRecord meuRegistro = registro(meu, 2, IMPRESSAO_A, "FITID-1", ImportRecordStatus.VALID, "12.34");

        assertEquals(0, deduplicacao.marcarDuplicados(titular.getId(), meu.getId()));
        assertEquals(ImportRecordStatus.VALID, statusDe(meuRegistro.getId()));
    }

    @Test
    void registroNaoLancadoAindaNaoBloqueiaNovoEnvio() {
        ImportBatch emRevisao = lote(titular, "a".repeat(64), "NUBANK");
        registro(emRevisao, 2, IMPRESSAO_A, "FITID-1", ImportRecordStatus.VALID, "12.34");

        ImportBatch novo = lote(titular, "b".repeat(64), "NUBANK");
        ImportRecord registro = registro(novo, 2, IMPRESSAO_A, "FITID-1", ImportRecordStatus.VALID, "12.34");

        assertEquals(0, deduplicacao.marcarDuplicados(titular.getId(), novo.getId()));
        assertEquals(ImportRecordStatus.VALID, statusDe(registro.getId()));
    }
}
