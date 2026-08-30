package com.gestor.financeiro;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusExecucaoRecorrencia;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
import com.gestor.financeiro.service.CartaoService;
import com.gestor.financeiro.service.ContaFixaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V67 — assinatura cobrada no cartao. A recorrencia passa a ter dois destinos
 * mutuamente exclusivos: caixa (carteira) ou cartao (conta). No cartao nao ha
 * saldo a validar, nao ha Parcela e o caixa nunca e tocado.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContaFixaCartaoTest {

    @Autowired ContaFixaService service;
    @Autowired CartaoService cartaoService;
    @Autowired ContaFixaRepository contaFixaRepository;
    @Autowired ExecucaoRecorrenciaRepository execucaoRepository;
    @Autowired TransacaoRepository transacaoRepository;
    @Autowired ParcelaRepository parcelaRepository;
    @Autowired FaturaLancamentoRepository faturaLancamentoRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Carteira carteira;
    private Conta cartao;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Assinante", "assinatura-cartao@teste.com", passwordEncoder.encode("123456")));
        carteira = carteiraRepository.save(
                TestDataFactory.carteira(usuario, "Principal", new BigDecimal("1000.00")));

        Conta novo = new Conta();
        novo.setNome("Cartao");
        novo.setDiaFechamento(10);
        novo.setDiaVencimento(20);
        cartao = cartaoService.criar(novo, usuario.getId());
    }

    @Test
    void assinaturaDeCartaoLancaNaFaturaSemTocarCaixaESemParcela() {
        ContaFixa netflix = criar("Netflix", "60.00", false, LocalDate.now(), cartao, null);

        ContaFixa depois = service.realizar(
                netflix.getId(), null, null, usuario.getId(), false);

        var transacoes = transacaoRepository.findByUsuarioId(usuario.getId());
        assertEquals(1, transacoes.size());
        var transacao = transacoes.get(0);
        // Na fatura o nome da assinatura aparece limpo, sem prefixo "Pagamento:"
        assertEquals("Netflix", transacao.getDescricao());
        assertNotNull(transacao.getConta());
        assertNull(transacao.getCarteira());

        var lancamentos = faturaLancamentoRepository.findByTransacaoId(transacao.getId());
        assertEquals(1, lancamentos.size());
        assertEquals(0, new BigDecimal("60.00").compareTo(lancamentos.get(0).getValor()));

        // Cartao tem cronograma proprio: Parcela nunca e criada
        assertTrue(parcelaRepository.findByTransacaoId(transacao.getId()).isEmpty());
        // Caixa intacto: assinatura de cartao nao debita conta corrente
        assertEquals(0, new BigDecimal("1000.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        // Passivo do cartao absorve a divida (invariante do espelho)
        assertEquals(0, new BigDecimal("60.00").compareTo(
                carteiraRepository.findById(cartao.getContaFinanceira().getId())
                        .orElseThrow().getSaldo().abs()));

        assertTrue(depois.getDataProximoVencimento().isAfter(LocalDate.now()));
    }

    @Test
    void carteiraIdNoCorpoNaoDebitaCaixaEmRecorrenciaDeCartao() {
        ContaFixa netflix = criar("Netflix", "60.00", false, LocalDate.now(), cartao, null);

        // Cliente desatualizado manda carteiraId; a assinatura continua indo para a fatura
        service.realizar(netflix.getId(), null, carteira.getId(), usuario.getId(), false);

        assertEquals(0, new BigDecimal("1000.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        assertEquals(1, faturaLancamentoRepository
                .findByTransacaoId(transacaoRepository.findByUsuarioId(usuario.getId()).get(0).getId()).size());
    }

    @Test
    void segundaExecucaoDaMesmaOcorrenciaNaoDuplicaLancamento() {
        ContaFixa netflix = criar("Netflix", "60.00", true, LocalDate.now(), cartao, null);
        service.realizarAutomatica(netflix.getId());

        // Volta o vencimento para simular a corrida do scheduler em duas instancias
        ContaFixa recarregada = contaFixaRepository.findById(netflix.getId()).orElseThrow();
        recarregada.setDataProximoVencimento(LocalDate.now());
        contaFixaRepository.saveAndFlush(recarregada);

        assertThrows(BusinessException.class, () -> service.realizarAutomatica(netflix.getId()));
        assertEquals(1, transacaoRepository.findByUsuarioId(usuario.getId()).size());
        assertEquals(StatusExecucaoRecorrencia.REALIZADA, execucaoRepository
                .findByContaFixaIdAndDataVencimento(netflix.getId(), LocalDate.now())
                .orElseThrow().getStatus());
    }

    @Test
    void execucaoAutomaticaComVencimentoFuturoNaoLanca() {
        // Ocorrencia ja avancada por outra instancia: a segunda nao pode cobrar de novo
        ContaFixa netflix = criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), cartao, null);

        service.realizarAutomatica(netflix.getId());

        assertTrue(transacaoRepository.findByUsuarioId(usuario.getId()).isEmpty());
        assertEquals(LocalDate.now().plusDays(5),
                contaFixaRepository.findById(netflix.getId()).orElseThrow().getDataProximoVencimento());
    }

    @Test
    void desativarCartaoDesativaAsAssinaturasQueCobravamNele() {
        ContaFixa netflix = criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), cartao, null);
        ContaFixa aluguel = criar("Aluguel", "900.00", true, LocalDate.now().plusDays(5), null, carteira);

        int desativadas = cartaoService.deletarCartao(cartao.getId(), usuario.getId());

        assertEquals(1, desativadas);
        assertFalse(contaFixaRepository.findById(netflix.getId()).orElseThrow().getAtivo());
        // Recorrencia de caixa nao e afetada
        assertTrue(contaFixaRepository.findById(aluguel.getId()).orElseThrow().getAtivo());
    }

    @Test
    void primeiraOcorrenciaVencidaExecutaNaCriacao() {
        ContaFixa netflix = criar("Netflix", "60.00", true, LocalDate.now(), cartao, null);

        // criar() ja disparou a primeira cobranca: quem cadastra hoje ve hoje
        assertEquals(1, transacaoRepository.findByUsuarioId(usuario.getId()).size());
        assertTrue(contaFixaRepository.findById(netflix.getId()).orElseThrow()
                .getDataProximoVencimento().isAfter(LocalDate.now()));
    }

    @Test
    void primeiraOcorrenciaSemSaldoCadastraERegistraFalha() {
        // Caixa sem saldo não derruba o cadastro: a ocorrência fica visível como falha
        ContaFixa cara = criar("Aluguel", "5000.00", true, LocalDate.now(), null, carteira);

        assertTrue(contaFixaRepository.findById(cara.getId()).orElseThrow().getAtivo());
        assertTrue(transacaoRepository.findByUsuarioId(usuario.getId()).isEmpty());
        assertEquals(StatusExecucaoRecorrencia.FALHA_SALDO, execucaoRepository
                .findByContaFixaIdAndDataVencimento(cara.getId(), LocalDate.now())
                .orElseThrow().getStatus());
    }

    @Test
    void destinoDuplicadoEhRecusado() {
        assertThrows(BusinessException.class,
                () -> criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), cartao, carteira));
    }

    @Test
    void entradaComCartaoEhRecusada() {
        ContaFixa salario = base("Salário", "5000.00", true, LocalDate.now().plusDays(5));
        salario.setTipo(TipoTransacao.ENTRADA);
        salario.setConta(cartao);
        assertThrows(BusinessException.class, () -> service.criar(salario, usuario.getId()));
    }

    @Test
    void cartaoInativoEhRecusado() {
        cartaoService.deletarCartao(cartao.getId(), usuario.getId());
        assertThrows(BusinessException.class,
                () -> criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), cartao, null));
    }

    @Test
    void cartaoDeOutroUsuarioEhRecusado() {
        Usuario outro = usuarioRepository.save(TestDataFactory.usuario(
                "Outro", "outro-assinante@teste.com", passwordEncoder.encode("123456")));
        Conta novo = new Conta();
        novo.setNome("Cartao alheio");
        novo.setDiaFechamento(10);
        novo.setDiaVencimento(20);
        Conta alheio = cartaoService.criar(novo, outro.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), alheio, null));
    }

    private ContaFixa base(String nome, String valor, boolean automatica, LocalDate vencimento) {
        ContaFixa conta = new ContaFixa();
        conta.setUsuario(usuario);
        conta.setNome(nome);
        conta.setValorPlanejado(new BigDecimal(valor));
        conta.setDiaVencimento(vencimento.getDayOfMonth());
        conta.setStatus(StatusPagamento.PENDENTE);
        conta.setRecorrente(true);
        conta.setAtivo(true);
        conta.setTipo(TipoTransacao.SAIDA);
        conta.setExecucaoAutomatica(automatica);
        return conta;
    }

    private ContaFixa criar(String nome, String valor, boolean automatica, LocalDate vencimento,
                            Conta cartaoDestino, Carteira carteiraDestino) {
        ContaFixa conta = base(nome, valor, automatica, vencimento);
        conta.setConta(cartaoDestino);
        conta.setCarteira(carteiraDestino);
        return service.criar(conta, usuario.getId());
    }
}
