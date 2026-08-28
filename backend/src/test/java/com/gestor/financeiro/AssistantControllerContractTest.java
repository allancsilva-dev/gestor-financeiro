package com.gestor.financeiro;

import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "assistant.text.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssistantControllerContractTest {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired CarteiraRepository carteiras;
    @Autowired CategoriaRepository categorias;

    @BeforeEach
    void setup() {
        Usuario usuario = usuarios.save(TestDataFactory.usuario(
                "Assistente API", "assistant-api@test.local", "hash"));
        carteiras.save(TestDataFactory.carteira(usuario, "Nubank", BigDecimal.ZERO));
        categorias.save(TestDataFactory.categoria(usuario, "Gasolina"));
    }

    @Test
    @WithMockUser(username = "assistant-api@test.local")
    void mensagemExigeIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"gasolina 85 hoje\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "assistant-api@test.local")
    void parserDeterministicoRetornaSomenteDtoVersionado() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/messages")
                        .header("Idempotency-Key", "assistant-contract-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"gasolina 85 no Nubank hoje\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("COMPLETE"))
                .andExpect(jsonPath("$.draft.tipo").value("SAIDA"))
                .andExpect(jsonPath("$.draft.valor").value(85))
                .andExpect(jsonPath("$.draft.version").isNumber())
                .andExpect(jsonPath("$.draft.usuario").doesNotExist())
                .andExpect(jsonPath("$.draft.provider").doesNotExist());
    }

    @Test
    @WithMockUser(username = "assistant-api@test.local")
    void mensagemAcimaDeDoisMilCaracteresFalhaAntesDoPipeline() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/messages")
                        .header("Idempotency-Key", "assistant-contract-long")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + "x".repeat(2_001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
