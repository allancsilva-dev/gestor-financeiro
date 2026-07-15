package com.gestor.financeiro;

import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class MetaControllerContractTest {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;

    @BeforeEach
    void setup() {
        usuarioRepository.save(TestDataFactory.usuario("Metas API", "metas-api@teste.com", "hash"));
    }

    @Test
    @WithMockUser(username = "metas-api@teste.com")
    void statusInvalidoRetorna400InvalidParameter() throws Exception {
        mockMvc.perform(get("/api/v1/metas/minhas").param("status", "PAUSADA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details.status").value("PAUSADA"));
    }
}
