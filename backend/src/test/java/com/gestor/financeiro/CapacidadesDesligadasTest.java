package com.gestor.financeiro;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O endpoint de capacidades existe justamente para dizer que um canal esta desligado, entao ele
 * nao pode carregar `@ConditionalOnProperty` como os controllers do assistente: sumir junto com a
 * feature devolveria 404 e o app nao saberia distinguir "desligado" de "servidor antigo".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "assistant.text.enabled=false",
        "assistant.audio.enabled=false",
        "assistant.whatsapp.enabled=false",
})
class CapacidadesDesligadasTest {

    @Autowired MockMvc mockMvc;

    @Test
    void exigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/capacidades"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "quem-quer-que-seja@teste.com")
    void respondeMesmoComTudoDesligado() throws Exception {
        mockMvc.perform(get("/api/v1/capacidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistenteTexto").value(false))
                .andExpect(jsonPath("$.assistenteAudio").value(false))
                .andExpect(jsonPath("$.assistenteWhatsapp").value(false));
    }

    @Test
    @WithMockUser(username = "quem-quer-que-seja@teste.com")
    void assistenteDesligadoDevolve404NaRotaDele() throws Exception {
        // A contraparte do endpoint: e este 404 que o app precisa evitar oferecer. Foi
        // exatamente ele que apareceu em producao como "nao foi possivel enviar".
        mockMvc.perform(get("/api/v1/assistant/recommendations"))
                .andExpect(status().isNotFound());
    }
}
