package com.gestor.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.RefreshToken;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.AnexoRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.RefreshTokenRepository;
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
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UsuarioExclusaoTest {

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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AnexoRepository anexoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario alice;
    private Usuario bob;

    @BeforeEach
    void setup() {
        anexoRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        transacaoRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        alice = criarUsuarioComDados("Alice", "alice@teste.com");
        bob = criarUsuarioComDados("Bob", "bob@teste.com");
    }

    private Usuario criarUsuarioComDados(String nome, String email) {
        Usuario usuario = usuarioRepository.save(
            TestDataFactory.usuario(nome, email, passwordEncoder.encode("Senha1234")));
        Categoria categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Mercado"));
        transacaoRepository.save(TestDataFactory.transacao(usuario, categoria, "Compra", new BigDecimal("50.00")));
        refreshTokenRepository.save(new RefreshToken(usuario, "hash-" + email, LocalDateTime.now().plusDays(7)));
        return usuario;
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void excluirConta_senhaErrada_naoApagaNada() throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("senha", "errada"))))
            .andExpect(status().isUnprocessableEntity());

        assertThat(usuarioRepository.count()).isEqualTo(2);
        assertThat(transacaoRepository.count()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void excluirConta_apagaTudoDoTitularEPreservaOutrosUsuarios() throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("senha", "Senha1234"))))
            .andExpect(status().isNoContent());

        // Titular: zero rastro
        assertThat(usuarioRepository.findByEmail("alice@teste.com")).isEmpty();
        assertThat(categoriaRepository.findByUsuarioIdAndAtivoTrue(alice.getId())).isEmpty();
        assertThat(refreshTokenRepository.findByUsuario(alice)).isEmpty();

        // Outros usuários: intactos
        assertThat(usuarioRepository.findByEmail("bob@teste.com")).isPresent();
        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(transacaoRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }
}
