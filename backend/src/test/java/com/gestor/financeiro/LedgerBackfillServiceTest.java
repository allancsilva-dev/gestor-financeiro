package com.gestor.financeiro;

import com.gestor.financeiro.dto.ReconciliacaoCarteiraResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.LedgerBackfillResult;
import com.gestor.financeiro.service.LedgerBackfillService;
import com.gestor.financeiro.service.LedgerReconciliationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class LedgerBackfillServiceTest {

    @Autowired
    private LedgerBackfillService backfillService;

    @Autowired
    private LedgerReconciliationService reconciliationService;

    @Autowired
    private MovimentoCarteiraRepository movimentoCarteiraRepository;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        cleanup();
        usuario = usuarioRepository.save(TestDataFactory.usuario("Backfill", "ledger-backfill@teste.com", passwordEncoder.encode("123456")));
    }

    @AfterEach
    void cleanup() {
        movimentoCarteiraRepository.deleteAll();
        carteiraRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void carteiraExistenteRecebeMovimentoDeAberturaSemAlterarSaldo() {
        Carteira carteira = carteiraRepository.save(carteira(usuario, "Principal", new BigDecimal("120.00")));

        LedgerBackfillResult result = backfillService.backfillUsuario(usuario.getId());

        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());

        assertEquals(1, result.movimentosCriados());
        assertBigDecimalEquals(new BigDecimal("120.00"), atualizada.getSaldo());
        assertEquals(1, movimentos.size());
        assertEquals(OrigemMovimentoCarteira.BACKFILL, movimentos.get(0).getOrigem());
        assertEquals(TipoMovimentoCarteira.ENTRADA, movimentos.get(0).getTipo());
        assertEquals("CARTEIRA", movimentos.get(0).getReferenciaTipo());
        assertEquals(carteira.getId(), movimentos.get(0).getReferenciaId());
        assertBigDecimalEquals(new BigDecimal("120.00"), movimentos.get(0).getValorAssinado());
        assertBigDecimalEquals(new BigDecimal("120.00"), movimentos.get(0).getSaldoResultante());
    }

    @Test
    void backfillDuasVezesNaoDuplicaMovimento() {
        Carteira carteira = carteiraRepository.save(carteira(usuario, "Principal", new BigDecimal("80.00")));

        LedgerBackfillResult primeira = backfillService.backfillUsuario(usuario.getId());
        LedgerBackfillResult segunda = backfillService.backfillUsuario(usuario.getId());

        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());

        assertEquals(1, primeira.movimentosCriados());
        assertEquals(0, segunda.movimentosCriados());
        assertEquals(1, segunda.carteirasComBackfillExistente());
        assertEquals(1, movimentos.size());
    }

    @Test
    void saldoAposBackfillReconcilia() {
        Carteira carteira = carteiraRepository.save(carteira(usuario, "Principal", new BigDecimal("55.00")));

        backfillService.backfillUsuario(usuario.getId());

        ReconciliacaoCarteiraResponse response = reconciliationService.reconciliarCarteira(usuario.getId(), carteira.getId());

        assertEquals(ReconciliacaoCarteiraResponse.Status.OK, response.status());
        assertBigDecimalEquals(new BigDecimal("55.00"), response.saldoMaterializado());
        assertBigDecimalEquals(new BigDecimal("55.00"), response.saldoLedger());
        assertBigDecimalEquals(BigDecimal.ZERO, response.diferenca());
    }

    @Test
    void backfillDoUsuarioNaoMisturaCarteiraDeOutroUsuario() {
        Carteira carteira = carteiraRepository.save(carteira(usuario, "Principal", new BigDecimal("40.00")));
        Usuario outroUsuario = usuarioRepository.save(TestDataFactory.usuario("Outro", "ledger-backfill-outro@teste.com", passwordEncoder.encode("123456")));
        Carteira outraCarteira = carteiraRepository.save(carteira(outroUsuario, "Outro", new BigDecimal("70.00")));

        LedgerBackfillResult result = backfillService.backfillUsuario(usuario.getId());

        List<MovimentoCarteira> movimentosUsuario = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());
        List<MovimentoCarteira> movimentosOutroUsuario = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(outroUsuario.getId(), outraCarteira.getId());

        assertEquals(1, result.movimentosCriados());
        assertEquals(1, movimentosUsuario.size());
        assertEquals(0, movimentosOutroUsuario.size());
    }

    @Test
    void saldoNegativoBloqueiaBackfill() {
        carteiraRepository.save(carteira(usuario, "Negativa", new BigDecimal("-10.00")));

        assertThrows(BusinessException.class, () -> backfillService.backfillUsuario(usuario.getId()));
    }

    @Test
    void backfillUsaApenasDiferencaQuandoCarteiraJaTemMovimentos() {
        Carteira carteira = carteiraRepository.save(carteira(usuario, "Parcial", new BigDecimal("130.00")));
        MovimentoCarteira movimentoExistente = movimento(carteira, new BigDecimal("30.00"), "parcial-001");
        movimentoCarteiraRepository.save(movimentoExistente);

        LedgerBackfillResult result = backfillService.backfillUsuario(usuario.getId());

        ReconciliacaoCarteiraResponse response = reconciliationService.reconciliarCarteira(usuario.getId(), carteira.getId());
        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());

        assertEquals(1, result.movimentosCriados());
        assertEquals(2, movimentos.size());
        assertBigDecimalEquals(new BigDecimal("130.00"), response.saldoLedger());
        assertEquals(ReconciliacaoCarteiraResponse.Status.OK, response.status());
    }

    private static Carteira carteira(Usuario usuario, String nome, BigDecimal saldo) {
        Carteira carteira = new Carteira();
        carteira.setUsuario(usuario);
        carteira.setNome(nome);
        carteira.setTipo(TipoCarteira.DINHEIRO);
        carteira.setSaldo(saldo);
        return carteira;
    }

    private static MovimentoCarteira movimento(Carteira carteira, BigDecimal valor, String idempotencyKey) {
        MovimentoCarteira movimento = new MovimentoCarteira();
        movimento.setUsuario(carteira.getUsuario());
        movimento.setCarteira(carteira);
        movimento.setTipo(TipoMovimentoCarteira.ENTRADA);
        movimento.setValor(valor);
        movimento.setValorAssinado(valor);
        movimento.setOrigem(OrigemMovimentoCarteira.CARTEIRA_AJUSTE);
        movimento.setReferenciaTipo("CARTEIRA");
        movimento.setReferenciaId(carteira.getId());
        movimento.setDescricao("Movimento existente");
        movimento.setSaldoResultante(valor);
        movimento.setIdempotencyKey(idempotencyKey);
        return movimento;
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
