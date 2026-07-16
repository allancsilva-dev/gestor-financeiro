package com.gestor.financeiro;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.ReconciliacaoObservabilidade;
import com.gestor.financeiro.service.ReconciliacaoSistemaResultado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReconciliacaoGlobalControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ReconciliacaoObservabilidade observabilidade;

    @Test
    void exigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliacao/global")).andExpect(status().isUnauthorized());
    }

    @Test
    void openApiPublicaSomenteOContratoDoRelatorio() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/reconciliacao/global'].get").exists())
                .andExpect(jsonPath("$.components.schemas.ReconciliacaoGlobalResponse").exists());
    }

    @Test
    void healthDegradedContinuaHttp200() throws Exception {
        observabilidade.registrar(new ReconciliacaoSistemaResultado(Instant.parse("2026-07-16T03:30:00Z"),
                1, 1, 1, 1, 0, Map.of(), List.of()));
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.components.reconciliation.status").value("DEGRADED"));
    }

    @Test
    @WithMockUser(username = "titular-reconciliation@teste.com")
    void retornaSomenteRelatorioDoTitularEDetalhesDivergentes() throws Exception {
        Usuario titular = user("titular");
        Usuario outro = user("outro");
        carteira(titular, "OK", BigDecimal.ZERO);
        Carteira alheia = carteira(outro, "Divergente", BigDecimal.TEN);

        mockMvc.perform(get("/api/v1/reconciliacao/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.divergencias").value(0))
                .andExpect(jsonPath("$.detalhes.length()").value(0))
                .andExpect(jsonPath("$.resumo.length()").value(4))
                .andExpect(jsonPath("$.detalhes[?(@.recursoId == " + alheia.getId() + ")]").isEmpty());
    }

    private Usuario user(String suffix) {
        return usuarioRepository.save(TestDataFactory.usuario(suffix,
                suffix + "-reconciliation@teste.com", passwordEncoder.encode("123456")));
    }

    private Carteira carteira(Usuario user, String name, BigDecimal saldo) {
        return carteiraRepository.save(TestDataFactory.carteira(user, name, saldo));
    }
}
