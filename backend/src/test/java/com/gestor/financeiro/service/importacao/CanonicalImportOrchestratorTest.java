package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.TestDataFactory;
import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.repository.ImportBatchRepository;
import com.gestor.financeiro.repository.ImportRecordRepository;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.enums.TipoCasamentoRegra;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.RegraCategoriaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cobre o orquestrador com limites reduzidos: contagem por status, flush em lote,
 * hash declarado divergente, teto de arquivo, teto de registros e o caminho de falha,
 * que precisa deixar o lote em FAILED com código.
 */
@SpringBootTest
@ActiveProfiles("test")
class CanonicalImportOrchestratorTest {

    @Autowired ImportBatchService batches;
    @Autowired CanonicalNormalizer normalizer;
    @Autowired ImportDeduplicationService deduplicacao;
    @Autowired ImportCategorizacaoService categorizacao;
    @Autowired RegraCategoriaService regras;
    @Autowired CategoriaRepository categorias;
    @Autowired ImportRecordRepository records;
    @Autowired ImportBatchRepository batchRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        records.deleteAll();
        batchRepository.deleteAll();
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Orquestrador", "orquestrador-" + System.nanoTime() + "@test.local", "hash"));
    }

    @org.junit.jupiter.api.AfterEach
    void limpar() {
        // Suite compartilha o H2 do JVM: lote e titular precisam sair para não
        // travar o delete de usuários de outros testes por integridade referencial.
        records.deleteAll();
        batchRepository.deleteAll();
        // Regra e categoria referenciam o titular: saem antes dele, na ordem das FKs.
        regras.listar(usuario.getId()).forEach(regra -> regras.remover(usuario.getId(), regra.getId()));
        categorias.deleteAll(categorias.findByUsuarioId(usuario.getId()));
        usuarioRepository.deleteById(usuario.getId());
    }

    private CanonicalImportOrchestrator orchestrator(int maxRecords, int stagingFlush, long fileBytes) {
        ImportLimits limits = new ImportLimits(fileBytes, 65536, maxRecords, 65536, 8192, 64, 32,
                500000, 8192, stagingFlush);
        // Registry proprio: os conectores precisam enxergar os mesmos limites do orquestrador.
        ImportConnectorRegistry registry = new ImportConnectorRegistry(List.of(
                new CsvImportConnector(limits, normalizer), new OfxImportConnector(limits, normalizer)));
        return new CanonicalImportOrchestrator(batches, registry, deduplicacao, categorizacao, records, batchRepository, limits,
                entityManager, transactionManager);
    }

    private List<com.gestor.financeiro.model.ImportRecord> recordsOf(Long batchId) {
        return records.findAll().stream().filter(r -> r.getBatch().getId().equals(batchId)).toList();
    }

    private ImportBatch batchDoUsuario() {
        return batchRepository.findAll().stream()
                .filter(b -> b.getUsuario().getId().equals(usuario.getId()))
                .findFirst().orElseThrow();
    }

    @Test
    void contaRegistrosPorStatusEEncerraEmParsed() throws Exception {
        String csv = String.join("\n",
                "data,descricao,valor,moeda,tipo",
                "2026-08-20,Mercado,-12.34,BRL,SAIDA",
                "2026-08-21,Salario,1000.00,,ENTRADA",
                "2026-08-22,Quebrado,abc,BRL,SAIDA") + "\n";

        ImportBatch batch = orchestrator(1000, 250, 10_485_760L)
                .stage(usuario.getId(), MemorySource.of(csv), "orq:contagem");

        assertEquals(ImportBatchStatus.PARSED, batch.getStatus());
        assertEquals(ImportFormat.CSV, batch.getFormat());
        assertEquals(3, batch.getTotalRecords());
        assertEquals(1, batch.getValidRecords());
        assertEquals(1, batch.getInvalidRecords());
        assertEquals(1, batch.getPendingReviewRecords());
        assertEquals(3, recordsOf(batch.getId()).size());
        assertEquals(1, recordsOf(batch.getId()).stream()
                .filter(r -> r.getStatus() == ImportRecordStatus.VALID).count());
    }

    @Test
    void naoPerdeRegistrosQuandoOFlushEmLoteLimpaOContexto() throws Exception {
        StringBuilder csv = new StringBuilder("data,descricao,valor,moeda,tipo\n");
        for (int i = 1; i <= 5; i++) {
            csv.append("2026-08-0").append(i).append(",Compra ").append(i).append(",-10.00,BRL,SAIDA\n");
        }

        ImportBatch batch = orchestrator(1000, 2, 10_485_760L)
                .stage(usuario.getId(), MemorySource.of(csv.toString()), "orq:flush");

        assertEquals(ImportBatchStatus.PARSED, batch.getStatus());
        assertEquals(5, batch.getTotalRecords());
        assertEquals(5, recordsOf(batch.getId()).size());
    }

    @Test
    void recusaQuandoHashDeclaradoDiverge() {
        String csv = "data,descricao,valor,moeda,tipo\n2026-08-20,Mercado,-12.34,BRL,SAIDA\n";
        MemorySource source = MemorySource.withDeclaredHash(csv, "0".repeat(64));

        ImportParsingException erro = assertThrows(ImportParsingException.class,
                () -> orchestrator(1000, 250, 10_485_760L).stage(usuario.getId(), source, "orq:hash"));

        assertEquals(ImportFailureCode.HASH_MISMATCH, erro.code());
        ImportBatch batch = batchDoUsuario();
        assertEquals(ImportBatchStatus.FAILED, batch.getStatus());
        assertEquals(ImportFailureCode.HASH_MISMATCH.name(), batch.getFailureCode());
    }

    @Test
    void recusaArquivoAcimaDoLimiteAntesDeCriarLote() {
        String csv = "data,descricao,valor,moeda,tipo\n2026-08-20,Mercado,-12.34,BRL,SAIDA\n";

        ImportParsingException erro = assertThrows(ImportParsingException.class,
                () -> orchestrator(1000, 250, 16L).stage(usuario.getId(), MemorySource.of(csv), "orq:tamanho"));

        assertEquals(ImportFailureCode.FILE_LIMIT_EXCEEDED, erro.code());
        assertTrue(batchRepository.findAll().stream()
                        .noneMatch(b -> b.getUsuario().getId().equals(usuario.getId())),
                "arquivo recusado nao pode deixar lote persistido");
    }

    @Test
    void marcaFalhaQuandoOArquivoExcedeOTetoDeRegistros() {
        String csv = String.join("\n",
                "data,descricao,valor,moeda,tipo",
                "2026-08-20,Um,-1.00,BRL,SAIDA",
                "2026-08-21,Dois,-2.00,BRL,SAIDA",
                "2026-08-22,Tres,-3.00,BRL,SAIDA") + "\n";

        ImportParsingException erro = assertThrows(ImportParsingException.class,
                () -> orchestrator(2, 250, 10_485_760L).stage(usuario.getId(), MemorySource.of(csv), "orq:linhas"));

        assertEquals(ImportFailureCode.ROW_LIMIT_EXCEEDED, erro.code());
        ImportBatch batch = batchDoUsuario();
        assertEquals(ImportBatchStatus.FAILED, batch.getStatus());
        assertEquals(ImportFailureCode.ROW_LIMIT_EXCEEDED.name(), batch.getFailureCode());
    }

    @Test
    void marcaFalhaQuandoNenhumConectorReconheceOArquivo() {
        String lixo = "conteudo sem forma reconhecivel\n";

        ImportParsingException erro = assertThrows(ImportParsingException.class,
                () -> orchestrator(1000, 250, 10_485_760L)
                        .stage(usuario.getId(), MemorySource.of(lixo), "orq:deteccao"));

        assertEquals(ImportFailureCode.DETECTION_FAILED, erro.code());
        ImportBatch batch = batchDoUsuario();
        assertEquals(ImportBatchStatus.FAILED, batch.getStatus());
        assertEquals(ImportFailureCode.DETECTION_FAILED.name(), batch.getFailureCode());
    }

    @Test
    void regraDoTitularJaCategorizaAsLinhasNaPrevia() throws Exception {
        Categoria mercado = categorias.save(TestDataFactory.categoria(usuario, "Mercado"));
        regras.criar(usuario.getId(), "mercado", TipoCasamentoRegra.CONTEM, null, mercado.getId(), null);

        String csv = String.join("\n",
                "data,descricao,valor,moeda,tipo",
                "2026-08-20,Mercado Centro,-12.34,BRL,SAIDA",
                "2026-08-21,Farmacia,-40.00,BRL,SAIDA") + "\n";

        ImportBatch batch = orchestrator(1000, 250, 10_485_760L)
                .stage(usuario.getId(), MemorySource.of(csv), "orq:regra");

        var registros = recordsOf(batch.getId());
        var comCategoria = registros.stream().filter(r -> r.getCategoria() != null).toList();
        assertEquals(1, comCategoria.size(), "só a linha que casa com a regra é categorizada");
        assertEquals(mercado.getId(), comCategoria.get(0).getCategoria().getId());
    }

    /** Fonte reabrivel em memoria: o orquestrador le o conteudo mais de uma vez. */
    private record MemorySource(byte[] content, String declaredHash) implements ImportSource {
        static MemorySource of(String content) {
            return new MemorySource(content.getBytes(StandardCharsets.UTF_8), null);
        }

        static MemorySource withDeclaredHash(String content, String declaredHash) {
            return new MemorySource(content.getBytes(StandardCharsets.UTF_8), declaredHash);
        }

        @Override public InputStream openStream() { return new ByteArrayInputStream(content); }
        @Override public long size() { return content.length; }
        @Override public String displayName() { return "extrato.csv"; }
        @Override public String contentType() { return "text/csv"; }
        @Override public String sha256() { return declaredHash; }
    }
}
