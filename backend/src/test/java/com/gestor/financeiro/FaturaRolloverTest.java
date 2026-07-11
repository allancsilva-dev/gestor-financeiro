package com.gestor.financeiro;

import com.gestor.financeiro.dto.FaturaResponse;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.FaturaCartao;
import com.gestor.financeiro.model.FaturaLancamento;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.FaturaLancamentoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.FaturaService;
import com.gestor.financeiro.service.TransacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do rollover de credito/saldo devedor de fatura de cartao (R1/R2).
 * Ver docs/SYSTEM_OVERVIEW.md, secao "Regra de produto: credito de fatura e saldo devedor
 * rolado", e FaturaService.liquidarFaturaAnterior.
 *
 * As faturas de origem sao construidas diretamente via repositorio (mesmo padrao usado em
 * LedgerBackfillOrfasTest) com competencia/dataFechamento em 2020, garantindo que
 * `dataFechamento` sempre fique no passado em relacao ao relogio real da maquina que roda o
 * teste, sem depender da data do dia em que os testes sao executados.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FaturaRolloverTest {

    @Autowired
    private FaturaService faturaService;

    @Autowired
    private TransacaoService transacaoService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private FaturaCartaoRepository faturaCartaoRepository;

    @Autowired
    private FaturaLancamentoRepository faturaLancamentoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Categoria categoria;
    private Conta cartao;

    @BeforeEach
    void setup() {
        faturaLancamentoRepository.deleteAll();
        faturaCartaoRepository.deleteAll();
        contaRepository.deleteAll();
        categoriaRepository.deleteAll();
        carteiraRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(
                TestDataFactory.usuario("Rollover", "fatura-rollover@teste.com", passwordEncoder.encode("123456")));
        categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Compras"));

        cartao = TestDataFactory.conta(usuario, "Cartão Rollover", TipoConta.CREDITO);
        cartao.setDiaFechamento(10);
        cartao.setDiaVencimento(20);
        cartao = contaRepository.save(cartao);

        Carteira carteira = new Carteira();
        carteira.setUsuario(usuario);
        carteira.setNome("Conta corrente");
        carteira.setTipo(TipoCarteira.CONTA_BANCARIA);
        carteira.setSaldo(new BigDecimal("1000.00"));
        carteiraRepository.save(carteira);
    }

    @Test
    void r1FaturaComTotalNegativoGeraCreditoNaProximaEFechaOrigemComoPaga() {
        FaturaCartao janeiro = criarFaturaOrigem(1, 2020, FaturaStatus.ABERTA, BigDecimal.ZERO);
        adicionarLancamento(janeiro, TipoFaturaLancamento.COMPRA, new BigDecimal("100.00"));
        adicionarLancamento(janeiro, TipoFaturaLancamento.ESTORNO, new BigDecimal("-150.00"));
        assertEquals(0, new BigDecimal("-50.00").compareTo(recarregarFatura(janeiro).getValorTotal()));

        // Invariante (decisão 16): valorGasto acumula os lançamentos criados no setup (100 - 150).
        assertEquals(0, new BigDecimal("-50.00").compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));

        FaturaResponse fevereiro = faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 2, 2020);

        // Fatura de origem fecha como PAGA, sem exigir ação do usuário, com dataPagamento = dataFechamento.
        FaturaCartao janeiroDepois = recarregarFatura(janeiro);
        assertEquals(FaturaStatus.PAGA, janeiroDepois.getStatus());
        assertEquals(LocalDate.of(2020, 1, 10), janeiroDepois.getDataPagamento());

        // Crédito de -50 lançado na próxima fatura em aberto (fevereiro), abatendo o total dela.
        assertEquals(0, new BigDecimal("-50.00").compareTo(fevereiro.valorTotal()));

        FaturaLancamento credito = faturaLancamentoRepository.findAll().stream()
                .filter(l -> l.getTipo() == TipoFaturaLancamento.CREDITO_ANTERIOR)
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("-50.00").compareTo(credito.getValor()));
        assertEquals(janeiro.getId(), credito.getFaturaOrigem().getId());
        assertNull(credito.getTransacao());

        // Invariante de valorGasto: o crédito rolado entra como qualquer lançamento (decisão 16).
        assertEquals(0, new BigDecimal("-100.00").compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));
    }

    @Test
    void r1CreditoAbateComprasDaProximaFaturaSemZerarPorCompleto() {
        FaturaCartao janeiro = criarFaturaOrigem(1, 2020, FaturaStatus.ABERTA, BigDecimal.ZERO);
        adicionarLancamento(janeiro, TipoFaturaLancamento.COMPRA, new BigDecimal("100.00"));
        adicionarLancamento(janeiro, TipoFaturaLancamento.ESTORNO, new BigDecimal("-150.00"));

        // Materializa fevereiro e rola o crédito de -50 para lá.
        faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 2, 2020);

        // Compra real de R$200 datada dentro da competência de fevereiro (antes do fechamento dia 10).
        transacaoService.criar(compraCartao("Mercado", new BigDecimal("200.00"), LocalDate.of(2020, 2, 5)),
                usuario.getId());

        FaturaResponse fevereiro = faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 2, 2020);

        // -50 (crédito) + 200 (compra) = 150: o crédito abate mas não some por completo.
        assertEquals(0, new BigDecimal("150.00").compareTo(fevereiro.valorTotal()));
    }

    @Test
    void r1CreditoRolaDeNovoQuandoProximaFaturaNaoTemCompras() {
        FaturaCartao janeiro = criarFaturaOrigem(1, 2020, FaturaStatus.ABERTA, BigDecimal.ZERO);
        adicionarLancamento(janeiro, TipoFaturaLancamento.COMPRA, new BigDecimal("100.00"));
        adicionarLancamento(janeiro, TipoFaturaLancamento.ESTORNO, new BigDecimal("-150.00"));

        // Materializa fevereiro sem nenhuma compra: recebe apenas o crédito de -50.
        FaturaResponse fevereiro = faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 2, 2020);
        assertEquals(0, new BigDecimal("-50.00").compareTo(fevereiro.valorTotal()));

        // Ao ler março, fevereiro (total <= 0, sem compras) também é liquidado como R1 e o
        // crédito rola de novo para a fatura seguinte.
        FaturaResponse marco = faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 3, 2020);
        assertEquals(0, new BigDecimal("-50.00").compareTo(marco.valorTotal()));

        FaturaCartao fevereiroDepois = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 2, 2020).orElseThrow();
        assertEquals(FaturaStatus.PAGA, fevereiroDepois.getStatus());
    }

    @Test
    void r2FaturaComPagamentoParcialGeraSaldoDevedorNaProximaSemAlterarStatusDaOrigem() {
        FaturaCartao janeiro = criarFaturaOrigem(1, 2020, FaturaStatus.ABERTA, new BigDecimal("120.00"));
        adicionarLancamento(janeiro, TipoFaturaLancamento.COMPRA, new BigDecimal("200.00"));

        FaturaResponse fevereiro = faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 2, 2020);

        // Saldo devedor = 200 - 120 = 80, lançado na próxima fatura em aberto.
        assertEquals(0, new BigDecimal("80.00").compareTo(fevereiro.valorTotal()));

        FaturaLancamento saldoDevedor = faturaLancamentoRepository.findAll().stream()
                .filter(l -> l.getTipo() == TipoFaturaLancamento.SALDO_DEVEDOR_ANTERIOR)
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("80.00").compareTo(saldoDevedor.getValor()));
        assertEquals(janeiro.getId(), saldoDevedor.getFaturaOrigem().getId());

        // R2 não bloqueia nem força o fechamento da origem: status persistido continua o mesmo,
        // valorPago/dataPagamento não são tocados pelo rollover.
        FaturaCartao janeiroDepois = recarregarFatura(janeiro);
        assertEquals(FaturaStatus.ABERTA, janeiroDepois.getStatus());
        assertEquals(0, new BigDecimal("120.00").compareTo(janeiroDepois.getValorPago()));
        assertNull(janeiroDepois.getDataPagamento());

        // Invariante de valorGasto: saldo devedor rolado (positivo) soma ao limite utilizado.
        // 200 (compra original) + 80 (saldo devedor rolado) = 280.
        assertEquals(0, new BigDecimal("280.00").compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));

        // Idempotência: ler a mesma fatura de novo não duplica o rollover.
        faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 2, 2020);
        long quantidadeSaldoDevedor = faturaLancamentoRepository.findAll().stream()
                .filter(l -> l.getTipo() == TipoFaturaLancamento.SALDO_DEVEDOR_ANTERIOR)
                .count();
        assertEquals(1, quantidadeSaldoDevedor);
    }

    @Test
    void pagamentoTotalNaoGeraRolloverAlgum() {
        FaturaCartao janeiro = criarFaturaOrigem(1, 2020, FaturaStatus.ABERTA, new BigDecimal("150.00"));
        adicionarLancamento(janeiro, TipoFaturaLancamento.COMPRA, new BigDecimal("150.00"));

        FaturaResponse fevereiro = faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 2, 2020);

        // Fatura quitada integralmente: nenhuma fatura de fevereiro é materializada, nenhum
        // lançamento de rollover é criado.
        assertNull(fevereiro.id());
        assertEquals(0, BigDecimal.ZERO.compareTo(fevereiro.valorTotal()));
        assertTrue(fevereiro.lancamentos().isEmpty());
        assertTrue(faturaCartaoRepository.findByContaIdAndMesAndAno(cartao.getId(), 2, 2020).isEmpty());

        assertFalse(faturaLancamentoRepository.existsByFaturaOrigemId(janeiro.getId()));

        FaturaCartao janeiroDepois = recarregarFatura(janeiro);
        assertEquals(FaturaStatus.ABERTA, janeiroDepois.getStatus());
    }

    @Test
    void rolloverR1EhIdempotenteAoMaterializarFaturaDestinoDuasVezes() {
        FaturaCartao janeiro = criarFaturaOrigem(1, 2020, FaturaStatus.ABERTA, BigDecimal.ZERO);
        adicionarLancamento(janeiro, TipoFaturaLancamento.COMPRA, new BigDecimal("100.00"));
        adicionarLancamento(janeiro, TipoFaturaLancamento.ESTORNO, new BigDecimal("-150.00"));

        FaturaResponse primeiraLeitura = faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 2, 2020);
        FaturaResponse segundaLeitura = faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 2, 2020);

        assertEquals(0, new BigDecimal("-50.00").compareTo(primeiraLeitura.valorTotal()));
        assertEquals(0, new BigDecimal("-50.00").compareTo(segundaLeitura.valorTotal()));

        long quantidadeCreditos = faturaLancamentoRepository.findAll().stream()
                .filter(l -> l.getTipo() == TipoFaturaLancamento.CREDITO_ANTERIOR)
                .count();
        assertEquals(1, quantidadeCreditos);

        // valorGasto não deve dobrar por ter lido a fatura destino duas vezes.
        assertEquals(0, new BigDecimal("-100.00").compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));
    }

    @Test
    void cadeiaComMesPuladoEncadeiaCorretamenteAoLerDiretoOMesMaisRecente() {
        // Janeiro: R1 (total <= 0) -> gera crédito de -50 para fevereiro.
        FaturaCartao janeiro = criarFaturaOrigem(1, 2020, FaturaStatus.ABERTA, BigDecimal.ZERO);
        adicionarLancamento(janeiro, TipoFaturaLancamento.COMPRA, new BigDecimal("100.00"));
        adicionarLancamento(janeiro, TipoFaturaLancamento.ESTORNO, new BigDecimal("-150.00"));

        // Fevereiro: compra de 200, paga apenas 50 -> R2 (saldo devedor) depois de herdar o
        // crédito de janeiro. Fevereiro já existe no banco (fechada), mas nunca foi lida.
        FaturaCartao fevereiro = criarFaturaOrigem(2, 2020, FaturaStatus.ABERTA, new BigDecimal("50.00"));
        adicionarLancamento(fevereiro, TipoFaturaLancamento.COMPRA, new BigDecimal("200.00"));

        // Lê março diretamente (sem nunca ter lido fevereiro antes). A cadeia deve estar
        // completa: janeiro -> fevereiro (R1) e fevereiro -> março (R2), na mesma leitura.
        FaturaResponse marco = faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 3, 2020);

        FaturaCartao janeiroDepois = recarregarFatura(janeiro);
        assertEquals(FaturaStatus.PAGA, janeiroDepois.getStatus());

        FaturaCartao fevereiroDepois = recarregarFatura(fevereiro);
        // 200 (compra) - 50 (crédito herdado de janeiro) = 150 de total em fevereiro.
        assertEquals(0, new BigDecimal("150.00").compareTo(fevereiroDepois.getValorTotal()));

        FaturaLancamento creditoEmFevereiro = faturaLancamentoRepository.findAll().stream()
                .filter(l -> l.getTipo() == TipoFaturaLancamento.CREDITO_ANTERIOR)
                .findFirst().orElseThrow();
        assertEquals(janeiro.getId(), creditoEmFevereiro.getFaturaOrigem().getId());
        assertEquals(fevereiro.getId(), creditoEmFevereiro.getFatura().getId());

        // Fevereiro: total 150, pago 50 -> saldo devedor de 100 rolado para março.
        assertEquals(0, new BigDecimal("100.00").compareTo(marco.valorTotal()));

        FaturaLancamento saldoDevedorEmMarco = faturaLancamentoRepository.findAll().stream()
                .filter(l -> l.getTipo() == TipoFaturaLancamento.SALDO_DEVEDOR_ANTERIOR)
                .findFirst().orElseThrow();
        assertEquals(fevereiro.getId(), saldoDevedorEmMarco.getFaturaOrigem().getId());
    }

    private FaturaCartao criarFaturaOrigem(int mes, int ano, FaturaStatus status, BigDecimal valorPago) {
        FaturaCartao fatura = new FaturaCartao();
        fatura.setUsuario(usuario);
        fatura.setConta(cartao);
        fatura.setMes(mes);
        fatura.setAno(ano);
        LocalDate fechamento = LocalDate.of(ano, mes, 10);
        fatura.setDataFechamento(fechamento);
        fatura.setDataVencimento(fechamento.plusDays(10));
        fatura.setStatus(status);
        fatura.setValorTotal(BigDecimal.ZERO);
        fatura.setValorPago(valorPago);
        return faturaCartaoRepository.save(fatura);
    }

    // Simula um lançamento já existente na fatura (equivalente ao que FaturaService.criarLancamento
    // faria em produção), mantendo o total da fatura e o valorGasto da conta consistentes, sem
    // depender de TransacaoService para montar o estado inicial de "fatura já fechada".
    private void adicionarLancamento(FaturaCartao fatura, TipoFaturaLancamento tipo, BigDecimal valor) {
        FaturaLancamento lancamento = new FaturaLancamento();
        lancamento.setFatura(fatura);
        lancamento.setTransacao(null);
        lancamento.setDescricao(tipo.name());
        lancamento.setValor(valor);
        lancamento.setDataCompra(fatura.getDataFechamento());
        lancamento.setTipo(tipo);
        faturaLancamentoRepository.save(lancamento);

        fatura.setValorTotal(fatura.getValorTotal().add(valor));
        faturaCartaoRepository.save(fatura);

        Conta conta = contaRepository.findById(cartao.getId()).orElseThrow();
        conta.setValorGasto(conta.getValorGasto().add(valor));
        contaRepository.save(conta);
    }

    private FaturaCartao recarregarFatura(FaturaCartao fatura) {
        return faturaCartaoRepository.findById(fatura.getId()).orElseThrow();
    }

    private Transacao compraCartao(String descricao, BigDecimal valor, LocalDate data) {
        Transacao transacao = new Transacao();
        transacao.setCategoria(categoria);
        transacao.setConta(cartao);
        transacao.setDescricao(descricao);
        transacao.setValorTotal(valor);
        transacao.setTipo(TipoTransacao.SAIDA);
        transacao.setData(data);
        transacao.setParcelado(false);
        transacao.setRecorrente(false);
        return transacao;
    }
}
