package com.gestor.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.CarteiraService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CarteiraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private MovimentoCarteiraRepository movimentoCarteiraRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CarteiraService carteiraService;

    private Usuario usuarioA;
    private Usuario usuarioB;
    private Carteira carteiraA;

    @BeforeEach
    void setup() {
        movimentoCarteiraRepository.deleteAll();
        carteiraRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuarioA = usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));
        usuarioB = usuarioRepository.save(TestDataFactory.usuario("Bob", "bob@teste.com", passwordEncoder.encode("123456")));

        carteiraA = criarCarteira(usuarioA, "Principal", new BigDecimal("200.00"));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void ajustarSaldoEntradaAumentaSaldoECriaMovimento() throws Exception {
        mockMvc.perform(post("/api/v1/carteiras/{id}/ajustes", carteiraA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "ENTRADA",
                                "valor", 50.00,
                                "descricao", "Ajuste de teste"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(carteiraA.getId()))
                .andExpect(jsonPath("$.saldo").value(250.00));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void ajustarSaldoSaidaReduzSaldoECriaMovimento() throws Exception {
        mockMvc.perform(post("/api/v1/carteiras/{id}/ajustes", carteiraA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "SAIDA",
                                "valor", 40.00,
                                "descricao", "Retirada de teste"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(carteiraA.getId()))
                .andExpect(jsonPath("$.saldo").value(160.00));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void ajustarSaldoTipoInvalidoRetornaErro() throws Exception {
        mockMvc.perform(post("/api/v1/carteiras/{id}/ajustes", carteiraA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "INVALIDO",
                                "valor", 10.00
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void ajustarSaldoCarteiraDeOutroUsuarioRetornaErro() throws Exception {
        Carteira carteiraB = criarCarteira(usuarioB, "Bob Carteira", new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/carteiras/{id}/ajustes", carteiraB.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "ENTRADA",
                                "valor", 10.00
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void listarMovimentosRetornaMovimentosDaCarteira() throws Exception {
        mockMvc.perform(post("/api/v1/carteiras/{id}/ajustes", carteiraA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "tipo", "ENTRADA",
                        "valor", 30.00,
                        "descricao", "Primeiro ajuste"
                ))));

        mockMvc.perform(post("/api/v1/carteiras/{id}/ajustes", carteiraA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "tipo", "SAIDA",
                        "valor", 10.00,
                        "descricao", "Segundo ajuste"
                ))));

        mockMvc.perform(get("/api/v1/carteiras/{id}/movimentos", carteiraA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].descricao").value("Segundo ajuste"))
                .andExpect(jsonPath("$.content[1].descricao").value("Primeiro ajuste"));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void listarMovimentosCarteiraDeOutroUsuarioRetornaErro() throws Exception {
        Carteira carteiraB = criarCarteira(usuarioB, "Bob Carteira", new BigDecimal("100.00"));

        mockMvc.perform(get("/api/v1/carteiras/{id}/movimentos", carteiraB.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void reconciliacaoAposAjusteRetornaOK() throws Exception {
        Carteira carteiraZero = criarCarteiraService(usuarioA, "Zerada", BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/carteiras/{id}/ajustes", carteiraZero.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "tipo", "ENTRADA",
                        "valor", 50.00
                ))));

        mockMvc.perform(get("/api/v1/carteiras/{id}/reconciliacao", carteiraZero.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carteiraId").value(carteiraZero.getId()))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.diferenca").value(0.00));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void adicionarDinheiroDeprecatedAindaFuncionaViaLedger() throws Exception {
        mockMvc.perform(post("/api/v1/carteiras/{id}/adicionar", carteiraA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "valor", 75.00
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(275.00));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void removerDinheiroDeprecatedAindaFuncionaViaLedger() throws Exception {
        mockMvc.perform(post("/api/v1/carteiras/{id}/remover", carteiraA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "valor", 30.00
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(170.00));
    }

    @Test
    @WithMockUser(username = "alice@teste.com")
    void movimentosCarteiraVaziaRetornaListaVazia() throws Exception {
        mockMvc.perform(get("/api/v1/carteiras/{id}/movimentos", carteiraA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    private Carteira criarCarteira(Usuario usuario, String nome, BigDecimal saldo) {
        Carteira carteira = new Carteira();
        carteira.setUsuario(usuario);
        carteira.setNome(nome);
        carteira.setTipo(TipoCarteira.DINHEIRO);
        carteira.setSaldo(saldo);
        return carteiraRepository.save(carteira);
    }

    private Carteira criarCarteiraService(Usuario usuario, String nome, BigDecimal saldo) {
        Carteira carteira = new Carteira();
        carteira.setNome(nome);
        carteira.setTipo(TipoCarteira.DINHEIRO);
        carteira.setSaldo(saldo);
        return carteiraService.criar(carteira, usuario.getId());
    }
}
