package com.gestor.financeiro;

import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.gestor.financeiro.repository.ImportBatchRepository;
import com.gestor.financeiro.repository.ImportRecordRepository;
import com.gestor.financeiro.repository.RateLimitBucketRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato do endpoint de importação: replay de Idempotency-Key, conflito de conteúdo, erro de
 * upload com código estável, admissão por lote em voo e — o que mais importa em produção — o
 * arquivo temporário nunca sobrevive à requisição.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.servlet.multipart.location=target/multipart-test",
        "app.import.admission.max-in-flight-per-user=2"
})
class ImportacaoControllerTest {

    private static final String EMAIL = "importacao@test.local";
    private static final Path TEMP_DIR = Path.of("target/multipart-test");

    private static final String CSV = "data,descricao,valor,moeda,tipo\n"
            + "2026-08-20,Mercado,-12.34,BRL,SAIDA\n";
    private static final String CSV_ALTERNATIVO = "data,descricao,valor,moeda,tipo\n"
            + "2026-08-21,Farmacia,-40.00,BRL,SAIDA\n";

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ImportBatchRepository batchRepository;
    @Autowired ImportRecordRepository recordRepository;
    @Autowired RateLimitBucketRepository rateLimitBucketRepository;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        limparImportacoes();
        rateLimitBucketRepository.deleteAll();
        usuario = usuarioRepository.findByEmail(EMAIL)
                .orElseGet(() -> usuarioRepository.save(TestDataFactory.usuario("Importacao", EMAIL, "hash")));
    }

    @AfterEach
    void limpar() {
        limparImportacoes();
        rateLimitBucketRepository.deleteAll();
        usuarioRepository.deleteById(usuario.getId());
    }

    private void limparImportacoes() {
        recordRepository.deleteAll();
        batchRepository.deleteAll();
    }

    private MockMultipartFile arquivo(String conteudo) {
        return new MockMultipartFile("file", "extrato.csv", "text/csv",
                conteudo.getBytes(StandardCharsets.UTF_8));
    }

    private long temporariosPendentes() throws IOException {
        if (!Files.isDirectory(TEMP_DIR)) return 0;
        try (Stream<Path> arquivos = Files.list(TEMP_DIR)) {
            return arquivos.filter(p -> p.getFileName().toString().startsWith("import-")).count();
        }
    }

    @Test
    @WithMockUser(username = EMAIL)
    void enviaArquivoEDevolveLoteProcessado() throws Exception {
        mockMvc.perform(multipart("/api/v1/importacoes").file(arquivo(CSV)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(ImportBatchStatus.PARSED.name()))
                .andExpect(jsonPath("$.format").value(ImportFormat.CSV.name()))
                .andExpect(jsonPath("$.totalRecords").value(1))
                .andExpect(jsonPath("$.validRecords").value(1));

        assertEquals(0, temporariosPendentes(), "arquivo temporário não pode sobreviver à requisição");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void replayDaMesmaChaveDevolveOMesmoLote() throws Exception {
        String primeiro = mockMvc.perform(multipart("/api/v1/importacoes").file(arquivo(CSV))
                        .header("Idempotency-Key", "import-http-1"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replay = mockMvc.perform(multipart("/api/v1/importacoes").file(arquivo(CSV))
                        .header("Idempotency-Key", "import-http-1"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(idDe(primeiro), idDe(replay));
        assertEquals(1, batchRepository.count(), "replay não pode criar um segundo lote");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void mesmaChaveComOutroArquivoConflita() throws Exception {
        mockMvc.perform(multipart("/api/v1/importacoes").file(arquivo(CSV))
                        .header("Idempotency-Key", "import-http-2"))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/importacoes").file(arquivo(CSV_ALTERNATIVO))
                        .header("Idempotency-Key", "import-http-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCIAL_CONFLICT"));

        assertEquals(0, temporariosPendentes(), "conflito também precisa apagar o temporário");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void arquivoIrreconhecivelViraErroDeDominio() throws Exception {
        mockMvc.perform(multipart("/api/v1/importacoes").file(arquivo("conteudo sem forma reconhecivel\n")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMPORT_PARSING_FAILED"))
                .andExpect(jsonPath("$.details.failureCode").value("DETECTION_FAILED"));

        assertEquals(0, temporariosPendentes(), "falha de parsing também precisa apagar o temporário");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void replayDeChaveQueFalhouRepeteOMesmoErro() throws Exception {
        String lixo = "conteudo sem forma reconhecivel\n";
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            mockMvc.perform(multipart("/api/v1/importacoes").file(arquivo(lixo))
                            .header("Idempotency-Key", "import-http-falha"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details.failureCode").value("DETECTION_FAILED"));
        }
        assertEquals(1, batchRepository.count(), "replay não pode criar um segundo lote");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void parteFileAusenteViraBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/v1/importacoes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PART"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void loteAindaEmProcessamentoBloqueiaNovoEnvio() throws Exception {
        // Dois lotes presos em RECEIVED simulam parse em andamento; o teto é 2.
        List<ImportBatch> presos = List.of(loteRecebido("a".repeat(64)), loteRecebido("b".repeat(64)));
        batchRepository.saveAll(presos);

        mockMvc.perform(multipart("/api/v1/importacoes").file(arquivo(CSV)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Retry-After"));
    }

    private ImportBatch loteRecebido(String hash) {
        ImportBatch batch = new ImportBatch();
        batch.setUsuario(usuario);
        batch.setFormat(ImportFormat.UNKNOWN);
        batch.setFileSha256(hash);
        batch.setStatus(ImportBatchStatus.RECEIVED);
        return batch;
    }

    private long idDe(String json) {
        int inicio = json.indexOf("\"id\":") + 5;
        int fim = json.indexOf(',', inicio);
        return Long.parseLong(json.substring(inicio, fim).trim());
    }
}
