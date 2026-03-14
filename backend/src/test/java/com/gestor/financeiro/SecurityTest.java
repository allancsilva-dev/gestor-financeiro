package com.gestor.financeiro;

import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        usuarioRepository.deleteAll();
    }

    @Test
    void endpointProtegido_deveRetornar401SemToken() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/resumo"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void endpointProtegido_deveRetornar200ComUsuarioAutenticado() throws Exception {
        Usuario usuario = TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456"));
        usuarioRepository.save(usuario);

        mockMvc.perform(get("/api/v1/usuarios/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("alice@teste.com"));
    }

    @Test
    void endpointProtegido_deveRetornar401ComTokenInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/resumo")
                .header("Authorization", "Bearer token-invalido"))
            .andExpect(status().isUnauthorized());
    }
}
