package com.gestor.financeiro;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regressao do contract: a rota generica de carteiras foi removida na V41. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CarteiraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "usuario@teste.com")
    void rotasLegadasDeCarteirasRetornam404() throws Exception {
        mockMvc.perform(get("/api/v1/carteiras")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/carteiras/1")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/carteiras/1/ajustes")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "usuario@teste.com")
    void rotasLegadasDeContasRetornam404() throws Exception {
        mockMvc.perform(get("/api/v1/contas")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/contas/1")).andExpect(status().isNotFound());
    }
}
