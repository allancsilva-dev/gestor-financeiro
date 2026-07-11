package com.gestor.financeiro;

import com.gestor.financeiro.dto.AnexoResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Anexo;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.AnexoRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.AnexoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "app.upload.dir=target/test-uploads")
@ActiveProfiles("test")
@Transactional
class AnexoServiceTest {

    private static final byte[] PNG_VALIDO = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

    @Autowired
    private AnexoService anexoService;

    @Autowired
    private AnexoRepository anexoRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private Transacao transacao;

    @BeforeEach
    void setup() {
        anexoRepository.deleteAll();
        transacaoRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", "hash"));
        Categoria categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Mercado"));
        transacao = transacaoRepository.save(
            TestDataFactory.transacao(usuario, categoria, "Compra", new BigDecimal("10.00")));
    }

    @Test
    void upload_pngValido_gravaTipoCanonicoENomeDeExibicao() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "C:\\fakepath\\nota fiscal.png", "application/x-evil", PNG_VALIDO);

        AnexoResponse response = anexoService.upload(usuario.getId(), transacao.getId(), file);

        // MIME vem da whitelist, não do Content-Type do cliente
        assertEquals("image/png", response.getTipo());
        // Nome de exibição sem componentes de caminho
        assertEquals("nota fiscal.png", response.getNome());
    }

    @Test
    void upload_htmlDisfarcadoDePdf_rejeitaPorMagicBytes() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "comprovante.pdf", "application/pdf",
            "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        assertThrows(BusinessException.class,
            () -> anexoService.upload(usuario.getId(), transacao.getId(), file));
    }

    @Test
    void upload_extensaoNaoPermitida_rejeita() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "malware.exe", "application/octet-stream", PNG_VALIDO);

        assertThrows(BusinessException.class,
            () -> anexoService.upload(usuario.getId(), transacao.getId(), file));
    }

    @Test
    void upload_arquivoVazio_rejeita() {
        MockMultipartFile file = new MockMultipartFile("file", "vazio.png", "image/png", new byte[0]);

        assertThrows(BusinessException.class,
            () -> anexoService.upload(usuario.getId(), transacao.getId(), file));
    }

    @Test
    void upload_nomeComTraversal_nuncaUsaFilenameDoClienteNoDisco() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "../../../etc/evil.png", "image/png", PNG_VALIDO);

        AnexoResponse response = anexoService.upload(usuario.getId(), transacao.getId(), file);

        Anexo salvo = anexoRepository.findById(response.getId()).orElseThrow();
        // Caminho em disco = uploadDir/usuarioId/UUID.ext, sem o filename do cliente
        assertTrue(salvo.getCaminho().matches(".*[0-9a-f\\-]{36}\\.png$"),
            "caminho inesperado: " + salvo.getCaminho());
        assertEquals("evil.png", salvo.getNome());
    }

    @Test
    void contentTypeSeguro_neutralizaMimeLegadoForaDaWhitelist() {
        Anexo legado = new Anexo();
        legado.setTipo("text/html");
        assertEquals("application/octet-stream", anexoService.contentTypeSeguro(legado));

        Anexo valido = new Anexo();
        valido.setTipo("application/pdf");
        assertEquals("application/pdf", anexoService.contentTypeSeguro(valido));
    }
}
