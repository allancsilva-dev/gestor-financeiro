package com.gestor.financeiro;

import com.gestor.financeiro.dto.ReconciliacaoCarteiraResponse;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.LedgerReconciliationService;
import com.gestor.financeiro.service.LedgerService;
import com.gestor.financeiro.service.RegistrarMovimentoCommand;
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

@SpringBootTest
@ActiveProfiles("test")
class LedgerReconciliationServiceTest {

    @Autowired
    private LedgerReconciliationService reconciliationService;

    @Autowired
    private LedgerService ledgerService;

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

        usuario = usuarioRepository.save(TestDataFactory.usuario("Recon", "ledger-recon@teste.com", passwordEncoder.encode("123456")));
    }

    @AfterEach
    void cleanup() {
        movimentoCarteiraRepository.deleteAll();
        carteiraRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void carteiraSemMovimentosESaldoZeroRetornaOk() {
        Carteira carteira = carteiraRepository.save(carteira("Vazia", BigDecimal.ZERO));

        ReconciliacaoCarteiraResponse response = reconciliationService.reconciliarCarteira(usuario.getId(), carteira.getId());

        assertEquals(ReconciliacaoCarteiraResponse.Status.OK, response.status());
        assertBigDecimalEquals(BigDecimal.ZERO, response.saldoMaterializado());
        assertBigDecimalEquals(BigDecimal.ZERO, response.saldoLedger());
        assertBigDecimalEquals(BigDecimal.ZERO, response.diferenca());
    }

    @Test
    void carteiraComMovimentosConsistentesRetornaOk() {
        Carteira carteira = carteiraRepository.save(carteira("Principal", BigDecimal.ZERO));
        registrarEntrada(carteira, new BigDecimal("50.00"), "recon-ok-001");

        ReconciliacaoCarteiraResponse response = reconciliationService.reconciliarCarteira(usuario.getId(), carteira.getId());

        assertEquals(ReconciliacaoCarteiraResponse.Status.OK, response.status());
        assertBigDecimalEquals(new BigDecimal("50.00"), response.saldoMaterializado());
        assertBigDecimalEquals(new BigDecimal("50.00"), response.saldoLedger());
        assertBigDecimalEquals(BigDecimal.ZERO, response.diferenca());
    }

    @Test
    void carteiraComSaldoAdulteradoRetornaDivergente() {
        Carteira carteira = carteiraRepository.save(carteira("Adulterada", BigDecimal.ZERO));
        registrarEntrada(carteira, new BigDecimal("50.00"), "recon-div-001");

        Carteira adulterada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        adulterada.setSaldo(new BigDecimal("10.00"));
        carteiraRepository.saveAndFlush(adulterada);

        ReconciliacaoCarteiraResponse response = reconciliationService.reconciliarCarteira(usuario.getId(), carteira.getId());

        assertEquals(ReconciliacaoCarteiraResponse.Status.DIVERGENTE, response.status());
        assertBigDecimalEquals(new BigDecimal("10.00"), response.saldoMaterializado());
        assertBigDecimalEquals(new BigDecimal("50.00"), response.saldoLedger());
        assertBigDecimalEquals(new BigDecimal("-40.00"), response.diferenca());
    }

    @Test
    void listaReconciliacaoDoUsuarioSemMisturarCarteirasDeOutroUsuario() {
        Carteira carteira = carteiraRepository.save(carteira("Principal", BigDecimal.ZERO));
        registrarEntrada(carteira, new BigDecimal("20.00"), "recon-list-001");

        Usuario outroUsuario = usuarioRepository.save(TestDataFactory.usuario("Outro", "ledger-recon-outro@teste.com", passwordEncoder.encode("123456")));
        carteiraRepository.save(carteira(outroUsuario, "Outro", BigDecimal.ZERO));

        List<ReconciliacaoCarteiraResponse> responses = reconciliationService.reconciliarUsuario(usuario.getId());

        assertEquals(1, responses.size());
        assertEquals(carteira.getId(), responses.get(0).carteiraId());
        assertEquals(usuario.getId(), responses.get(0).usuarioId());
    }

    private void registrarEntrada(Carteira carteira, BigDecimal valor, String idempotencyKey) {
        ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
                usuario.getId(),
                carteira.getId(),
                TipoMovimentoCarteira.ENTRADA,
                valor,
                RegistrarMovimentoCommand.Direcao.ENTRADA,
                OrigemMovimentoCarteira.CARTEIRA_AJUSTE,
                "CARTEIRA",
                carteira.getId(),
                "Movimento para reconciliação",
                idempotencyKey,
                null,
                false
        ));
    }

    private Carteira carteira(String nome, BigDecimal saldo) {
        return carteira(usuario, nome, saldo);
    }

    private static Carteira carteira(Usuario usuario, String nome, BigDecimal saldo) {
        Carteira carteira = new Carteira();
        carteira.setUsuario(usuario);
        carteira.setNome(nome);
        carteira.setTipo(TipoCarteira.DINHEIRO);
        carteira.setSaldo(saldo);
        return carteira;
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
