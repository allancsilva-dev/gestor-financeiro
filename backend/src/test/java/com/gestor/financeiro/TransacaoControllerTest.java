package com.gestor.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransacaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuarioA;
    private Usuario usuarioB;
    private Categoria categoriaA;
    private Categoria categoriaB;

    @BeforeEach
    void setup() {
        transacaoRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuarioA = usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));
        usuarioB = usuarioRepository.save(TestDataFactory.usuario("Bob", "bob@teste.com", passwordEncoder.encode("123456")));

        categoriaA = categoriaRepository.save(TestDataFactory.categoria(usuarioA, "Mercado"));
        categoriaB = categoriaRepository.save(TestDataFactory.categoria(usuarioB, "Lazer"));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void listar_deveRetornarApenasTransacoesDoUsuarioAutenticado() throws Exception {
        transacaoRepository.save(TestDataFactory.transacao(usuarioA, categoriaA, "Compra A", new BigDecimal("50.00")));
        transacaoRepository.save(TestDataFactory.transacao(usuarioB, categoriaB, "Compra B", new BigDecimal("70.00")));

        mockMvc.perform(get("/api/v1/transacoes/minhas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].descricao").value("Compra A"));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void listar_deveNaoVazarDadosDeOutroUsuario() throws Exception {
        transacaoRepository.save(TestDataFactory.transacao(usuarioB, categoriaB, "Somente Bob", new BigDecimal("99.00")));

        mockMvc.perform(get("/api/v1/transacoes/minhas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void criar_deveSalvarTransacaoComDadosValidos() throws Exception {
        mockMvc.perform(post("/api/v1/transacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "descricao", "Supermercado",
                        "valor", 120.50,
                        "data", "2026-03-10",
                        "tipo", "SAIDA",
                        "categoriaId", categoriaA.getId()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.descricao").value("Supermercado"));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void criar_deveFalharQuandoDadosInvalidos() throws Exception {
        mockMvc.perform(post("/api/v1/transacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "descricao", "",
                        "valor", -10,
                        "tipo", "SAIDA"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void atualizar_deveFuncionarParaDonoDoRecurso() throws Exception {
        Transacao transacao = transacaoRepository.save(
            TestDataFactory.transacao(usuarioA, categoriaA, "Compra Inicial", new BigDecimal("25.00"))
        );

        mockMvc.perform(put("/api/v1/transacoes/{id}", transacao.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "descricao", "Compra Atualizada",
                        "valor", 30.00,
                        "data", "2026-03-11",
                        "tipo", "SAIDA",
                        "categoriaId", categoriaA.getId()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.descricao").value("Compra Atualizada"));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void atualizar_deveFalharQuandoNaoEhODono() throws Exception {
        Transacao transacao = transacaoRepository.save(
            TestDataFactory.transacao(usuarioB, categoriaB, "Compra Bob", new BigDecimal("60.00"))
        );

        mockMvc.perform(put("/api/v1/transacoes/{id}", transacao.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "descricao", "Tentativa Invasao",
                        "valor", 61.00,
                        "data", "2026-03-12",
                        "tipo", "SAIDA",
                        "categoriaId", categoriaA.getId()
                ))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void delete_deveSucessoParaDonoEFalharParaNaoDono() throws Exception {
        Transacao propria = transacaoRepository.save(
            TestDataFactory.transacao(usuarioA, categoriaA, "Minha Compra", new BigDecimal("40.00"))
        );

        mockMvc.perform(delete("/api/v1/transacoes/{id}", propria.getId()))
            .andExpect(status().isNoContent());

        Transacao alheia = transacaoRepository.save(
            TestDataFactory.transacao(usuarioB, categoriaB, "Compra Bob", new BigDecimal("80.00"))
        );

        mockMvc.perform(delete("/api/v1/transacoes/{id}", alheia.getId()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
