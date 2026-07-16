package com.gestor.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CartaoControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ContaRepository contaRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Usuario alice;

    @BeforeEach
    void setUp() {
        alice = usuarioRepository.save(TestDataFactory.usuario(
                "Alice Cartão", "alice-cartao-canonico@teste.com", passwordEncoder.encode("123456")));
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/cartoes")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice-cartao-canonico@teste.com")
    void criaCartaoPareadoECalculaSaldoPeloPassivo() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "nome", "Nexos Black", "limiteTotal", 2000,
                "diaFechamento", 5, "diaVencimento", 12,
                "cor", "#7C5CFC", "banco", "Nexos"));

        String body = mockMvc.perform(post("/api/v1/cartoes")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contaFinanceiraId").isNumber())
                .andExpect(jsonPath("$.saldoDevedor").value(0))
                .andExpect(jsonPath("$.limiteDisponivel").value(2000))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(body).get("id").asLong();
        Conta cartao = contaRepository.findById(id).orElseThrow();
        Carteira passivo = cartao.getContaFinanceira();
        passivo.setSaldo(new BigDecimal("-50.00"));
        carteiraRepository.saveAndFlush(passivo);

        mockMvc.perform(get("/api/v1/cartoes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoDevedor").value(-50.00))
                .andExpect(jsonPath("$.limiteDisponivel").value(2050.00));

        mockMvc.perform(get("/api/v1/cartoes?size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id));
    }

}
