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

/** Com o texto ligado o servidor precisa dizer que esta ligado — e a rota tem que existir. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "assistant.text.enabled=true",
        "assistant.audio.enabled=false",
        "assistant.whatsapp.enabled=false",
})
class CapacidadesLigadasTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(username = "quem-quer-que-seja@teste.com")
    void refleteAFlagLigada() throws Exception {
        mockMvc.perform(get("/api/v1/capacidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistenteTexto").value(true))
                .andExpect(jsonPath("$.assistenteAudio").value(false));
    }
}
