package com.gestor.financeiro;

import com.gestor.financeiro.dto.FaturaResponse;
import com.gestor.financeiro.model.*;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FaturaCartaoWorkflowTest {

    @Autowired
    private TransacaoService transacaoService;

    @Autowired
    private FaturaService faturaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private MovimentoCarteiraRepository movimentoCarteiraRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private FaturaCartaoRepository faturaCartaoRepository;

    @Autowired
    private FaturaLancamentoRepository faturaLancamentoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Categoria categoria;
    private Conta cartao;
    private Carteira carteira;

    @BeforeEach
    void setup() {
        faturaLancamentoRepository.deleteAll();
        faturaCartaoRepository.deleteAll();
        movimentoCarteiraRepository.deleteAll();
        transacaoRepository.deleteAll();
        carteiraRepository.deleteAll();
        contaRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(
                TestDataFactory.usuario("Fatura", "fatura-workflow@teste.com", passwordEncoder.encode("123456")));
        categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Mercado"));

        cartao = TestDataFactory.conta(usuario, "Cartão Roxo", TipoConta.CREDITO);
        cartao.setDiaFechamento(28);
        cartao.setDiaVencimento(5);
        cartao = contaRepository.save(cartao);

        carteira = new Carteira();
        carteira.setUsuario(usuario);
        carteira.setNome("Conta corrente");
        carteira.setTipo(TipoCarteira.CONTA_BANCARIA);
        carteira.setSaldo(new BigDecimal("1000.00"));
        carteira = carteiraRepository.save(carteira);
    }

    @Test
    void compraParceladaNoCartaoCriaLancamentosNasFaturasCorretasSemDebitarCarteira() {
        Transacao compra = compraCartao("Notebook", new BigDecimal("120.00"), LocalDate.of(2026, 7, 29));
        compra.setCarteira(carteira);
        compra.setParcelado(true);
        compra.setTotalParcelas(3);

        transacaoService.criar(compra, usuario.getId());

        assertEquals(0, new BigDecimal("1000.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        assertEquals(0, new BigDecimal("120.00").compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));

        FaturaCartao agosto = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 8, 2026).orElseThrow();
        FaturaCartao setembro = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 9, 2026).orElseThrow();
        FaturaCartao outubro = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 10, 2026).orElseThrow();

        assertEquals(0, new BigDecimal("40.00").compareTo(agosto.getValorTotal()));
        assertEquals(0, new BigDecimal("40.00").compareTo(setembro.getValorTotal()));
        assertEquals(0, new BigDecimal("40.00").compareTo(outubro.getValorTotal()));

        FaturaResponse response = faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 8, 2026);
        assertEquals(1, response.lancamentos().size());
        assertEquals(1, response.lancamentos().get(0).parcelaAtual());
        assertEquals(3, response.lancamentos().get(0).totalParcelas());
        assertEquals(0, new BigDecimal("40.00").compareTo(response.valorTotal()));
    }

    @Test
    void pagarFaturaDebitaCarteiraLiberaLimiteEMarcaComoPaga() {
        transacaoService.criar(
                compraCartao("Mercado", new BigDecimal("100.00"), LocalDate.of(2026, 7, 10)),
                usuario.getId());

        FaturaCartao fatura = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 7, 2026).orElseThrow();

        FaturaResponse response = faturaService.pagarFatura(
                usuario.getId(), fatura.getId(), new BigDecimal("100.00"), carteira.getId());

        assertEquals(FaturaStatus.PAGA.name(), response.status());
        assertEquals(0, new BigDecimal("900.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));

        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());
        assertEquals(1, movimentos.size());
        assertEquals(OrigemMovimentoCarteira.FATURA_CARTAO, movimentos.get(0).getOrigem());
        assertEquals(0, new BigDecimal("-100.00").compareTo(movimentos.get(0).getValorAssinado()));
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
