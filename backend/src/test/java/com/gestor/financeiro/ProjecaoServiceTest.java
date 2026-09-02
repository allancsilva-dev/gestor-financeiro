package com.gestor.financeiro;

import com.gestor.financeiro.dto.ProjecaoMensalDto;
import com.gestor.financeiro.dto.ProjecaoResponse;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.CartaoService;
import com.gestor.financeiro.service.ContaFixaService;
import com.gestor.financeiro.service.ProjecaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjecaoServiceTest {

    @Autowired
    private ProjecaoService projecaoService;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private ContaFixaRepository contaFixaRepository;

    @Autowired
    private CartaoService cartaoService;

    @Autowired
    private ContaFixaService contaFixaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Projetor", "projecao-service@teste.com", passwordEncoder.encode("123456")));
        carteiraRepository.save(TestDataFactory.carteira(usuario, "Conta", new BigDecimal("1000.00")));
    }

    @Test
    void projecaoSomaContaFixaPendenteDoMesEIgnoraPaga() {
        LocalDate venceEsteMes = LocalDate.now().withDayOfMonth(15);
        // pendente vencendo este mes -> entra na projecao do mes 0
        contaFixaRepository.save(contaFixa("Aluguel", "800.00", venceEsteMes, StatusPagamento.PENDENTE));
        // paga -> excluida
        contaFixaRepository.save(contaFixa("Internet", "100.00", venceEsteMes, StatusPagamento.PAGO));

        ProjecaoResponse r = projecaoService.projetar(usuario.getId(), 3);

        assertEquals(0, new BigDecimal("1000.00").compareTo(r.saldoAtual()));
        ProjecaoMensalDto mes0 = r.meses().get(0);
        assertEquals(0, new BigDecimal("800.00").compareTo(mes0.totalContasFixas()),
                "so a conta fixa pendente do mes deve somar");
        assertEquals(0, BigDecimal.ZERO.compareTo(mes0.totalParcelas()));
        assertEquals(0, BigDecimal.ZERO.compareTo(mes0.totalFaturas()));
        // saldo final = 1000 - 800
        assertEquals(0, new BigDecimal("200.00").compareTo(mes0.saldoFinal()));
    }

    @Test
    void projecaoSemLancamentosMantemSaldo() {
        ProjecaoResponse r = projecaoService.projetar(usuario.getId(), 2);
        ProjecaoMensalDto mes0 = r.meses().get(0);
        assertEquals(0, BigDecimal.ZERO.compareTo(mes0.totalSaidas()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(mes0.saldoFinal()));
    }

    @Test
    void salarioRecorrenteSomaEmTodosOsMeses() {
        ContaFixa salario = contaFixa("Salário", "2500.00", LocalDate.now().withDayOfMonth(15), StatusPagamento.PENDENTE);
        salario.setTipo(TipoTransacao.ENTRADA);
        salario.setRecorrente(true);
        contaFixaRepository.save(salario);

        ProjecaoResponse r = projecaoService.projetar(usuario.getId(), 3);

        assertEquals(0, new BigDecimal("2500.00").compareTo(r.meses().get(0).totalEntradas()));
        assertEquals(0, new BigDecimal("3500.00").compareTo(r.meses().get(0).saldoFinal()));
        assertEquals(0, new BigDecimal("6000.00").compareTo(r.meses().get(1).saldoFinal()));
        assertEquals(0, new BigDecimal("8500.00").compareTo(r.meses().get(2).saldoFinal()));
    }

    /**
     * Assinatura de cartao (destino conta_id, V67) nao e saida de caixa: ela vira
     * FaturaLancamento e so sai do caixa quando a fatura vence. Somar em
     * totalContasFixas contava o mesmo dinheiro duas vezes — uma como recorrencia
     * prevista, outra dentro da fatura — e deixava o saldo projetado pessimista.
     */
    @Test
    void assinaturaDeCartaoNaoSomaComoSaidaDeCaixaNaProjecao() {
        LocalDate venceEsteMes = LocalDate.now().withDayOfMonth(15);

        Conta cartao = cartaoService.criar(cartaoNovo(), usuario.getId());
        ContaFixa netflix = contaFixa("Netflix", "60.00", venceEsteMes, StatusPagamento.PENDENTE);
        netflix.setConta(cartao);
        contaFixaRepository.save(netflix);

        // recorrencia de caixa continua somando: e o contraste que prova o filtro
        contaFixaRepository.save(contaFixa("Aluguel", "800.00", venceEsteMes, StatusPagamento.PENDENTE));

        ProjecaoResponse r = projecaoService.projetar(usuario.getId(), 3);

        ProjecaoMensalDto mes0 = r.meses().get(0);
        assertEquals(0, new BigDecimal("800.00").compareTo(mes0.totalContasFixas()),
                "so a recorrencia de caixa soma em contas fixas; cartao vira fatura");
    }

    /**
     * Filtrar a assinatura de contas fixas sem mais nada a faria sumir da projecao: a
     * fatura so e materializada quando a cobranca acontece, entao nos meses adiante nao
     * havia fatura nenhuma para somar e o saldo virava otimista. Ela precisa aparecer no
     * mes em que a fatura que a contem vence.
     */
    @Test
    void assinaturaDeCartaoApareceNoMesEmQueAFaturaVence() {
        // cartao fecha dia 10 e vence dia 20; assinatura cobra dia 15
        LocalDate cobraDia15 = LocalDate.now().withDayOfMonth(15);

        Conta cartao = cartaoService.criar(cartaoNovo(), usuario.getId());
        ContaFixa netflix = contaFixa("Netflix", "60.00", cobraDia15, StatusPagamento.PENDENTE);
        netflix.setConta(cartao);
        contaFixaRepository.save(netflix);

        ProjecaoResponse r = projecaoService.projetar(usuario.getId(), 4);

        // dia 15 > fechamento 10 -> competencia do mes seguinte -> vence dia 20 dele
        assertEquals(0, BigDecimal.ZERO.compareTo(r.meses().get(0).totalFaturas()),
                "a cobranca deste mes so vira dinheiro na fatura do mes seguinte");
        for (int i = 1; i < 4; i++) {
            assertEquals(0, new BigDecimal("60.00").compareTo(r.meses().get(i).totalFaturas()),
                    "assinatura precisa aparecer no mes de vencimento da fatura");
            assertEquals(0, BigDecimal.ZERO.compareTo(r.meses().get(i).totalContasFixas()));
        }
    }

    /**
     * O caso que originou a correcao: cobranca ja realizada esta numa fatura
     * materializada. Ela nao pode ser contada de novo como cobranca futura.
     */
    @Test
    void assinaturaJaCobradaNaoEhContadaDeNovoComoCobrancaFutura() {
        LocalDate cobraDia15 = LocalDate.now().withDayOfMonth(15);

        Conta cartao = cartaoService.criar(cartaoNovo(), usuario.getId());
        ContaFixa netflix = contaFixa("Netflix", "60.00", cobraDia15, StatusPagamento.PENDENTE);
        netflix.setConta(cartao);
        netflix = contaFixaRepository.save(netflix);

        contaFixaService.realizar(netflix.getId(), null, null, usuario.getId(), false);

        ProjecaoResponse r = projecaoService.projetar(usuario.getId(), 4);

        // a ocorrencia cobrada aparece uma vez so, pela fatura materializada
        BigDecimal somaFaturas = r.meses().stream()
                .map(ProjecaoMensalDto::totalFaturas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 4 meses de projecao = no maximo 4 cobrancas de 60; nunca 5
        assertTrue(somaFaturas.compareTo(new BigDecimal("240.00")) <= 0,
                "cobranca ja realizada nao pode contar como futura tambem; somou " + somaFaturas);
    }

    private Conta cartaoNovo() {
        Conta cartao = new Conta();
        cartao.setNome("Cartao");
        cartao.setDiaFechamento(10);
        cartao.setDiaVencimento(20);
        return cartao;
    }

    private ContaFixa contaFixa(String nome, String valor, LocalDate vencimento, StatusPagamento status) {
        ContaFixa cf = new ContaFixa();
        cf.setUsuario(usuario);
        cf.setNome(nome);
        cf.setValorPlanejado(new BigDecimal(valor));
        cf.setDiaVencimento(vencimento.getDayOfMonth());
        cf.setDataProximoVencimento(vencimento);
        cf.setStatus(status);
        cf.setAtivo(true);
        return cf;
    }
}
