package com.gestor.financeiro;

import com.gestor.financeiro.dto.ReconciliacaoCarteiraResponse;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.LedgerBackfillService;
import com.gestor.financeiro.service.LedgerOrfaBackfillResult;
import com.gestor.financeiro.service.LedgerReconciliationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class LedgerBackfillOrfasTest {

    @Autowired
    private LedgerBackfillService backfillService;
    @Autowired
    private LedgerReconciliationService reconciliationService;
    @Autowired
    private MovimentoCarteiraRepository movimentoCarteiraRepository;
    @Autowired
    private TransacaoRepository transacaoRepository;
    @Autowired
    private CarteiraRepository carteiraRepository;
    @Autowired
    private ContaRepository contaRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Categoria categoria;

    @BeforeEach
    void setup() {
        cleanup();
        usuario = usuarioRepository.save(TestDataFactory.usuario("Orfas", "ledger-orfas@teste.com", passwordEncoder.encode("123456")));
        categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Geral"));
    }

    @AfterEach
    void cleanup() {
        movimentoCarteiraRepository.deleteAll();
        transacaoRepository.deleteAll();
        contaRepository.deleteAll();
        categoriaRepository.deleteAll();
        carteiraRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // Cenário A: saldo já reflete a órfã (S-L == O). Backfill movimento-only reconcilia.
    @Test
    void cenarioA_divergenciaExplicadaPelasOrfas_reconcilia() {
        Carteira carteira = carteiraRepository.save(TestDataFactory.carteira(usuario, "Principal", new BigDecimal("100.00")));
        salvarOrfa(carteira, TipoTransacao.ENTRADA, new BigDecimal("100.00")); // O = +100, S-L = 100-0

        LedgerOrfaBackfillResult diag = backfillService.reconciliarTransacoesOrfasUsuario(usuario.getId(), true);
        assertEquals(1, diag.transacoesOrfas());
        assertEquals(1, diag.carteirasReconciliaveis());
        assertEquals(0, diag.movimentosCriados()); // dry-run não persiste

        LedgerOrfaBackfillResult aplicado = backfillService.reconciliarTransacoesOrfasUsuario(usuario.getId(), false);
        assertEquals(1, aplicado.movimentosCriados());

        ReconciliacaoCarteiraResponse rec = reconciliationService.reconciliarCarteira(usuario.getId(), carteira.getId());
        assertEquals(ReconciliacaoCarteiraResponse.Status.OK, rec.status());
        assertBig(new BigDecimal("100.00"), rec.saldoLedger());
        assertBig(new BigDecimal("100.00"), carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo());
        assertEquals(OrigemMovimentoCarteira.TRANSACAO, movimentosDa(carteira).get(0).getOrigem());
    }

    // Cenário A com SAIDA: impacto negativo explica saldo menor que o ledger.
    @Test
    void cenarioA_saida_reconcilia() {
        Carteira carteira = carteiraRepository.save(TestDataFactory.carteira(usuario, "Principal", new BigDecimal("-40.00")));
        salvarOrfa(carteira, TipoTransacao.SAIDA, new BigDecimal("40.00")); // O = -40, S-L = -40-0

        backfillService.reconciliarTransacoesOrfasUsuario(usuario.getId(), false);

        ReconciliacaoCarteiraResponse rec = reconciliationService.reconciliarCarteira(usuario.getId(), carteira.getId());
        assertEquals(ReconciliacaoCarteiraResponse.Status.OK, rec.status());
        assertBig(new BigDecimal("-40.00"), rec.saldoLedger());
    }

    // Cenário B: saldo NÃO reflete a órfã (S-L == 0, O != 0). Não reconciliável → intacto.
    @Test
    void cenarioB_divergenciaNaoExplicada_ficaRevisaoManual() {
        Carteira carteira = carteiraRepository.save(TestDataFactory.carteira(usuario, "Principal", BigDecimal.ZERO));
        salvarOrfa(carteira, TipoTransacao.ENTRADA, new BigDecimal("50.00")); // O = +50, S-L = 0

        LedgerOrfaBackfillResult aplicado = backfillService.reconciliarTransacoesOrfasUsuario(usuario.getId(), false);

        assertEquals(1, aplicado.transacoesOrfas());
        assertEquals(0, aplicado.carteirasReconciliaveis());
        assertEquals(1, aplicado.carteirasRevisaoManual());
        assertEquals(0, aplicado.movimentosCriados());
        assertEquals(0, movimentosDa(carteira).size()); // carteira intacta
    }

    // Idempotência: rodar duas vezes não duplica movimentos.
    @Test
    void backfillDuasVezesNaoDuplica() {
        Carteira carteira = carteiraRepository.save(TestDataFactory.carteira(usuario, "Principal", new BigDecimal("30.00")));
        salvarOrfa(carteira, TipoTransacao.ENTRADA, new BigDecimal("30.00"));

        int primeira = backfillService.reconciliarTransacoesOrfasUsuario(usuario.getId(), false).movimentosCriados();
        LedgerOrfaBackfillResult segunda = backfillService.reconciliarTransacoesOrfasUsuario(usuario.getId(), false);

        assertEquals(1, primeira);
        assertEquals(0, segunda.transacoesOrfas()); // já têm movimento → não são mais órfãs
        assertEquals(0, segunda.movimentosCriados());
        assertEquals(1, movimentosDa(carteira).size());
    }

    // Compra de cartão (SAIDA em conta CREDITO) não é órfã de carteira.
    @Test
    void compraCartaoNaoEhOrfa() {
        Carteira carteira = carteiraRepository.save(TestDataFactory.carteira(usuario, "Principal", BigDecimal.ZERO));
        Conta credito = contaRepository.save(TestDataFactory.conta(usuario, "Cartão", TipoConta.CREDITO));
        Transacao compra = TestDataFactory.transacao(usuario, categoria, "Compra cartão", new BigDecimal("200.00"));
        compra.setTipo(TipoTransacao.SAIDA);
        compra.setCarteira(carteira);
        compra.setConta(credito);
        compra.setAtiva(true);
        transacaoRepository.save(compra);

        LedgerOrfaBackfillResult diag = backfillService.reconciliarTransacoesOrfasUsuario(usuario.getId(), true);
        assertEquals(0, diag.transacoesOrfas());
    }

    // Isolamento: backfill de um usuário não toca órfãs de outro.
    @Test
    void naoMisturaOutroUsuario() {
        Carteira carteira = carteiraRepository.save(TestDataFactory.carteira(usuario, "Principal", new BigDecimal("10.00")));
        salvarOrfa(carteira, TipoTransacao.ENTRADA, new BigDecimal("10.00"));

        Usuario outro = usuarioRepository.save(TestDataFactory.usuario("Outro", "ledger-orfas-outro@teste.com", passwordEncoder.encode("123456")));
        Categoria catOutro = categoriaRepository.save(TestDataFactory.categoria(outro, "Geral"));
        Carteira carteiraOutro = carteiraRepository.save(TestDataFactory.carteira(outro, "Principal", new BigDecimal("10.00")));
        Transacao orfaOutro = TestDataFactory.transacao(outro, catOutro, "Órfã outro", new BigDecimal("10.00"));
        orfaOutro.setTipo(TipoTransacao.ENTRADA);
        orfaOutro.setCarteira(carteiraOutro);
        orfaOutro.setAtiva(true);
        transacaoRepository.save(orfaOutro);

        backfillService.reconciliarTransacoesOrfasUsuario(usuario.getId(), false);

        assertEquals(1, movimentosDa(carteira).size());
        assertEquals(0, movimentosDa(carteiraOutro).size());
    }

    private Transacao salvarOrfa(Carteira carteira, TipoTransacao tipo, BigDecimal valor) {
        Transacao t = TestDataFactory.transacao(usuario, categoria, "Órfã", valor);
        t.setTipo(tipo);
        t.setCarteira(carteira);
        t.setData(LocalDate.now());
        t.setAtiva(true);
        return transacaoRepository.save(t);
    }

    private List<com.gestor.financeiro.model.MovimentoCarteira> movimentosDa(Carteira carteira) {
        return movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(carteira.getUsuario().getId(), carteira.getId());
    }

    private static void assertBig(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
