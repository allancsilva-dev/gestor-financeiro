package com.gestor.financeiro;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class LedgerServiceTest {

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
    private Carteira carteira;

    @BeforeEach
    void setup() {
        movimentoCarteiraRepository.deleteAll();
        carteiraRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(TestDataFactory.usuario("Ledger", "ledger-service@teste.com", passwordEncoder.encode("123456")));
        carteira = carteiraRepository.save(carteira(usuario, "Principal", new BigDecimal("100.00")));
    }

    @AfterEach
    void cleanup() {
        movimentoCarteiraRepository.deleteAll();
        carteiraRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void registrarEntradaAumentaSaldoESalvaSaldoResultante() {
        MovimentoCarteira movimento = ledgerService.registrarMovimento(entrada(new BigDecimal("50.00"), "entrada-001"));

        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(atualizada.getSaldo()));
        assertEquals(0, new BigDecimal("150.00").compareTo(movimento.getSaldoResultante()));
        assertEquals(0, new BigDecimal("50.00").compareTo(movimento.getValorAssinado()));
    }

    @Test
    void registrarSaidaReduzSaldoESalvaSaldoResultante() {
        MovimentoCarteira movimento = ledgerService.registrarMovimento(saida(new BigDecimal("40.00"), "saida-001"));

        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("60.00").compareTo(atualizada.getSaldo()));
        assertEquals(0, new BigDecimal("60.00").compareTo(movimento.getSaldoResultante()));
        assertEquals(0, new BigDecimal("-40.00").compareTo(movimento.getValorAssinado()));
    }

    @Test
    void saidaAcimaDoSaldoBloqueiaPorPadrao() {
        assertThrows(BusinessException.class,
                () -> ledgerService.registrarMovimento(saida(new BigDecimal("101.00"), "saida-999")));
    }

    @Test
    void carteiraDeOutroUsuarioRetornaErro() {
        Usuario outroUsuario = usuarioRepository.save(TestDataFactory.usuario("Outro", "ledger-outro@teste.com", passwordEncoder.encode("123456")));

        RegistrarMovimentoCommand command = new RegistrarMovimentoCommand(
                outroUsuario.getId(),
                carteira.getId(),
                TipoMovimentoCarteira.ENTRADA,
                new BigDecimal("10.00"),
                RegistrarMovimentoCommand.Direcao.ENTRADA,
                OrigemMovimentoCarteira.CARTEIRA_AJUSTE,
                "CARTEIRA",
                carteira.getId(),
                "Tentativa cruzada",
                "cross-001",
                null,
                false
        );

        assertThrows(ResourceNotFoundException.class, () -> ledgerService.registrarMovimento(command));
    }

    @Test
    void duasSaidasConcorrentesNaoCorrompemSaldo() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> primeira = executor.submit(() -> registrarSaidaAposLatch(start, "conc-001"));
        Future<?> segunda = executor.submit(() -> registrarSaidaAposLatch(start, "conc-002"));

        start.countDown();
        primeira.get(5, TimeUnit.SECONDS);
        segunda.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());

        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals(0, new BigDecimal("20.00").compareTo(atualizada.getSaldo()));
        assertEquals(2, movimentos.size());
        assertTrue(movimentos.stream().allMatch(m -> new BigDecimal("-40.00").compareTo(m.getValorAssinado()) == 0));
    }

    private void registrarSaidaAposLatch(CountDownLatch start, String idempotencyKey) {
        try {
            start.await(5, TimeUnit.SECONDS);
            ledgerService.registrarMovimento(saida(new BigDecimal("40.00"), idempotencyKey));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private RegistrarMovimentoCommand entrada(BigDecimal valor, String idempotencyKey) {
        return command(valor, RegistrarMovimentoCommand.Direcao.ENTRADA, TipoMovimentoCarteira.ENTRADA, idempotencyKey);
    }

    private RegistrarMovimentoCommand saida(BigDecimal valor, String idempotencyKey) {
        return command(valor, RegistrarMovimentoCommand.Direcao.SAIDA, TipoMovimentoCarteira.SAIDA, idempotencyKey);
    }

    private RegistrarMovimentoCommand command(BigDecimal valor,
                                              RegistrarMovimentoCommand.Direcao direcao,
                                              TipoMovimentoCarteira tipo,
                                              String idempotencyKey) {
        return new RegistrarMovimentoCommand(
                usuario.getId(),
                carteira.getId(),
                tipo,
                valor,
                direcao,
                OrigemMovimentoCarteira.CARTEIRA_AJUSTE,
                "CARTEIRA",
                carteira.getId(),
                "Movimento de teste",
                idempotencyKey,
                null,
                false
        );
    }

    private static Carteira carteira(Usuario usuario, String nome, BigDecimal saldo) {
        Carteira carteira = new Carteira();
        carteira.setUsuario(usuario);
        carteira.setNome(nome);
        carteira.setTipo(TipoCarteira.DINHEIRO);
        carteira.setSaldo(saldo);
        return carteira;
    }
}
