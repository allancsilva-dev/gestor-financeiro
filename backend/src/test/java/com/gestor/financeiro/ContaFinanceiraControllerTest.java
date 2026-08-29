package com.gestor.financeiro;

import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContaFinanceiraControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired MovimentoCarteiraRepository movimentoRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Usuario alice;
    private Usuario bob;
    private Carteira conta;

    @BeforeEach
    void setUp() {
        movimentoRepository.deleteAll();
        carteiraRepository.deleteAll();
        usuarioRepository.deleteAll();
        alice = usuarioRepository.save(TestDataFactory.usuario(
                "Alice", "alice-conta@teste.com", passwordEncoder.encode("123456")));
        bob = usuarioRepository.save(TestDataFactory.usuario(
                "Bob", "bob-conta@teste.com", passwordEncoder.encode("123456")));
        conta = novaConta(alice, "Principal", BigDecimal.ZERO);
    }

    @Test
    void rotasCanonicasExigemAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/contas-financeiras/minhas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice-conta@teste.com")
    void criacaoEdicaoListagemEExclusaoTemParidadeComLegado() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "nome", "Reserva", "natureza", "ATIVO", "subtipo", "POUPANCA",
                "liquidez", "IMEDIATA", "moeda", "BRL", "saldoInicial", 100.00, "banco", "Nexos"));
        String body = mockMvc.perform(post("/api/v1/contas-financeiras")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Reserva"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(put("/api/v1/contas-financeiras/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Reserva editada", "natureza", "ATIVO", "subtipo", "POUPANCA",
                                "liquidez", "IMEDIATA", "moeda", "BRL",
                                "saldoInicial", 120.00, "banco", "Nexos"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Reserva editada"))
                .andExpect(jsonPath("$.tipo").doesNotExist())
                .andExpect(jsonPath("$.saldo").value(120.00));

        mockMvc.perform(get("/api/v1/contas-financeiras/minhas?size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.size").value(1));

        Carteira vazia = novaConta(alice, "Excluir", BigDecimal.ZERO);
        mockMvc.perform(delete("/api/v1/contas-financeiras/{id}", vazia.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice-conta@teste.com")
    void rejeitaSubtiposGerenciadosPorOutrosModulos() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "nome", "Cartão manual", "natureza", "PASSIVO", "subtipo", "CARTAO",
                "liquidez", "IMEDIATA", "moeda", "BRL", "saldoInicial", 0));
        mockMvc.perform(post("/api/v1/contas-financeiras")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "alice-conta@teste.com")
    void ajusteMovimentosEReconciliacaoRespeitamOwnership() throws Exception {
        mockMvc.perform(post("/api/v1/contas-financeiras/{id}/ajustes", conta.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "ENTRADA", "valor", 50.00, "descricao", "Ajuste canônico"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(50.00));

        mockMvc.perform(get("/api/v1/contas-financeiras/{id}/movimentos", conta.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(get("/api/v1/contas-financeiras/{id}/reconciliacao", conta.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
        mockMvc.perform(get("/api/v1/contas-financeiras/minhas/reconciliacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        Carteira alheia = novaConta(bob, "Bob", BigDecimal.ZERO);
        mockMvc.perform(get("/api/v1/contas-financeiras/{id}", alheia.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/contas-financeiras/{id}/movimentos", alheia.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/contas-financeiras/{id}/reconciliacao", alheia.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice-conta@teste.com")
    void elegerPrincipalDesmarcaAAnterior() throws Exception {
        Carteira antiga = novaConta(alice, "Antiga", BigDecimal.ZERO);
        antiga.setPrincipal(true);
        carteiraRepository.saveAndFlush(antiga);

        mockMvc.perform(put("/api/v1/contas-financeiras/{id}", conta.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Principal", "natureza", "ATIVO", "subtipo", "DINHEIRO",
                                "liquidez", "IMEDIATA", "moeda", "BRL", "saldoInicial", 0,
                                "principal", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(true));

        // O indice parcial so admite uma marcada por titular: se a anterior nao fosse
        // desmarcada antes do flush, isto aqui seria uma violacao de constraint, nao um assert.
        assertThat(carteiraRepository.findById(antiga.getId()).orElseThrow().isPrincipal()).isFalse();
        assertThat(carteiraRepository.findByUsuarioIdAndPrincipalTrue(alice.getId()).orElseThrow().getId())
                .isEqualTo(conta.getId());
    }

    @Test
    @WithMockUser(username = "alice-conta@teste.com")
    void putSemCampoPrincipalNaoDesmarcaAContaPadrao() throws Exception {
        conta.setPrincipal(true);
        carteiraRepository.saveAndFlush(conta);

        // Corrigir o nome nao pode custar a conta padrao: `principal` ausente significa
        // "nao mexa", nao "desmarque".
        mockMvc.perform(put("/api/v1/contas-financeiras/{id}", conta.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Nome corrigido", "natureza", "ATIVO", "subtipo", "DINHEIRO",
                                "liquidez", "IMEDIATA", "moeda", "BRL", "saldoInicial", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(true));
    }

    @Test
    @WithMockUser(username = "alice-conta@teste.com")
    void criacaoJaNasceComoPrincipalQuandoPedido() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "nome", "Nova padrão", "natureza", "ATIVO", "subtipo", "CORRENTE",
                "liquidez", "IMEDIATA", "moeda", "BRL", "saldoInicial", 0, "principal", true));
        mockMvc.perform(post("/api/v1/contas-financeiras")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(true));
    }

    @Test
    @WithMockUser(username = "alice-conta@teste.com")
    void exclusaoDaPrincipalElegeSucessora() throws Exception {
        conta.setPrincipal(true);
        carteiraRepository.saveAndFlush(conta);
        Carteira sucessora = novaConta(alice, "Sucessora", BigDecimal.ZERO);

        mockMvc.perform(delete("/api/v1/contas-financeiras/{id}", conta.getId()))
                .andExpect(status().isNoContent());

        // Sem sucessao o titular ficaria sem conta padrao e o formulario de lancamento
        // voltaria a chutar a primeira da lista.
        assertThat(carteiraRepository.findByUsuarioIdAndPrincipalTrue(alice.getId()).orElseThrow().getId())
                .isEqualTo(sucessora.getId());
    }

    private Carteira novaConta(Usuario usuario, String nome, BigDecimal saldo) {
        Carteira c = new Carteira();
        c.setNome(nome);
        c.setSubtipo(SubtipoContaFinanceira.DINHEIRO);
        c.setSaldo(saldo);
        c.setUsuario(usuario);
        return carteiraRepository.save(c);
    }
}
