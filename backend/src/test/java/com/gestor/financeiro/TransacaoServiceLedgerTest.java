package com.gestor.financeiro;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransacaoServiceLedgerTest {

    @Autowired
    private TransacaoService transacaoService;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MovimentoCarteiraRepository movimentoCarteiraRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Carteira carteira;
    private Categoria categoria;

    @BeforeEach
    void setup() {
        movimentoCarteiraRepository.deleteAll();
        transacaoRepository.deleteAll();
        carteiraRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(
                TestDataFactory.usuario("Trans", "trans-ledger@teste.com", passwordEncoder.encode("123456")));
        categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Alimentação"));

        carteira = new Carteira();
        carteira.setUsuario(usuario);
        carteira.setNome("Carteira Ledger");
        carteira.setTipo(TipoCarteira.DINHEIRO);
        carteira.setSaldo(new BigDecimal("500.00"));
        carteira = carteiraRepository.save(carteira);
    }

    @Test
    void criarTransacaoEntradaComCarteiraCriaMovimentoPositivo() {
        Transacao transacao = transacaoService.criar(
                transacaoEntrada(usuario, categoria, carteira, "Salário", new BigDecimal("200.00")),
                usuario.getId());

        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("700.00").compareTo(atualizada.getSaldo()));

        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());
        assertEquals(1, movimentos.size());
        assertEquals("TRANSACAO", movimentos.get(0).getReferenciaTipo());
        assertEquals(transacao.getId(), movimentos.get(0).getReferenciaId());
        assertTrue(movimentos.get(0).getValorAssinado().signum() > 0);
    }

    @Test
    void criarTransacaoSaidaComCarteiraCriaMovimentoNegativo() {
        Transacao transacao = transacaoService.criar(
                transacaoSaida(usuario, categoria, carteira, "Mercado", new BigDecimal("100.00")),
                usuario.getId());

        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("400.00").compareTo(atualizada.getSaldo()));

        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());
        assertEquals(1, movimentos.size());
        assertTrue(movimentos.get(0).getValorAssinado().signum() < 0);
    }

    @Test
    void criarTransacaoSemCarteiraNaoGeraMovimento() {
        transacaoService.criar(
                transacaoSaida(usuario, categoria, null, "Sem carteira", new BigDecimal("50.00")),
                usuario.getId());

        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());
        assertEquals(0, movimentos.size());
    }

    @Test
    void criarTransacaoComCarteiraStubSubstituiPorEntidadeGerenciada() {
        Carteira carteiraStub = new Carteira();
        carteiraStub.setId(carteira.getId());

        Transacao transacao = transacaoSaida(usuario, categoria, carteiraStub, "Stub carteira", new BigDecimal("75.00"));

        Transacao criada = transacaoService.criar(transacao, usuario.getId());

        assertEquals(carteira.getId(), criada.getCarteira().getId());
        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("425.00").compareTo(atualizada.getSaldo()));
    }

    @Test
    void atualizarValorGeraMovimentoDeDiferenca() {
        Transacao transacao = transacaoService.criar(
                transacaoSaida(usuario, categoria, carteira, "Original", new BigDecimal("100.00")),
                usuario.getId());

        Transacao atualizada = new Transacao();
        atualizada.setDescricao("Original");
        atualizada.setValorTotal(new BigDecimal("150.00"));
        atualizada.setData(LocalDate.now());

        transacaoService.atualizar(transacao.getId(), atualizada, usuario.getId());

        Carteira carteiraAtualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("350.00").compareTo(carteiraAtualizada.getSaldo()));

        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());
        assertEquals(2, movimentos.size());
    }

    @Test
    void cancelarGeraEstornoERestauraSaldo() {
        Transacao transacao = transacaoService.criar(
                transacaoSaida(usuario, categoria, carteira, "Para cancelar", new BigDecimal("200.00")),
                usuario.getId());

        transacaoService.cancelar(transacao.getId(), usuario.getId());

        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("500.00").compareTo(atualizada.getSaldo()));

        Transacao cancelada = transacaoRepository.findById(transacao.getId()).orElseThrow();
        assertTrue(!cancelada.getAtiva());

        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());
        assertEquals(2, movimentos.size());
        assertTrue(movimentos.get(0).getValorAssinado().signum() > 0);
    }

    @Test
    void cancelarTransacaoEntradaGeraEstornoNegativo() {
        Transacao transacao = transacaoService.criar(
                transacaoEntrada(usuario, categoria, carteira, "Entrada", new BigDecimal("300.00")),
                usuario.getId());

        transacaoService.cancelar(transacao.getId(), usuario.getId());

        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("500.00").compareTo(atualizada.getSaldo()));
    }

    private Transacao transacaoEntrada(Usuario u, Categoria cat, Carteira cart, String desc, BigDecimal valor) {
        return criarTransacao(u, cat, cart, desc, valor, TipoTransacao.ENTRADA);
    }

    private Transacao transacaoSaida(Usuario u, Categoria cat, Carteira cart, String desc, BigDecimal valor) {
        return criarTransacao(u, cat, cart, desc, valor, TipoTransacao.SAIDA);
    }

    private Transacao criarTransacao(Usuario u, Categoria cat, Carteira cart, String desc, BigDecimal valor,
                                     TipoTransacao tipo) {
        Transacao transacao = new Transacao();
        transacao.setUsuario(u);
        transacao.setCategoria(cat);
        transacao.setCarteira(cart);
        transacao.setDescricao(desc);
        transacao.setValorTotal(valor);
        transacao.setTipo(tipo);
        transacao.setData(LocalDate.now());
        transacao.setParcelado(false);
        transacao.setRecorrente(false);
        return transacao;
    }
}
