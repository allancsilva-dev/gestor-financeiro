package com.gestor.financeiro;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MovimentoCarteiraRepositoryTest {

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

        usuario = usuarioRepository.save(TestDataFactory.usuario("Ledger", "ledger@teste.com", passwordEncoder.encode("123456")));
        carteira = carteiraRepository.save(carteira(usuario, "Carteira Principal"));
    }

    @Test
    void deveSalvarMovimentoValido() {
        MovimentoCarteira movimento = movimentoCarteiraRepository.saveAndFlush(
                movimento(carteira, new BigDecimal("50.00"), new BigDecimal("150.00"), "idem-001"));

        assertEquals(usuario.getId(), movimento.getUsuario().getId());
        assertEquals(carteira.getId(), movimento.getCarteira().getId());
        assertEquals(TipoMovimentoCarteira.ENTRADA, movimento.getTipo());
        assertEquals(OrigemMovimentoCarteira.CARTEIRA_AJUSTE, movimento.getOrigem());
        assertEquals("BRL", movimento.getMoeda());
        assertTrue(movimentoCarteiraRepository.findByUsuarioIdAndIdempotencyKey(usuario.getId(), "idem-001").isPresent());
    }

    @Test
    void deveListarMovimentosPorUsuarioECarteira() {
        movimentoCarteiraRepository.save(movimento(carteira, new BigDecimal("10.00"), new BigDecimal("110.00"), "idem-010"));
        movimentoCarteiraRepository.save(movimento(carteira, new BigDecimal("20.00"), new BigDecimal("130.00"), "idem-020"));

        Usuario outroUsuario = usuarioRepository.save(TestDataFactory.usuario("Outro", "outro@teste.com", passwordEncoder.encode("123456")));
        Carteira outraCarteira = carteiraRepository.save(carteira(outroUsuario, "Carteira Outro"));
        movimentoCarteiraRepository.save(movimento(outraCarteira, new BigDecimal("99.00"), new BigDecimal("99.00"), "idem-099"));

        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());

        assertEquals(2, movimentos.size());
        assertTrue(movimentos.stream().allMatch(m -> m.getUsuario().getId().equals(usuario.getId())));
        assertTrue(movimentos.stream().allMatch(m -> m.getCarteira().getId().equals(carteira.getId())));
    }

    private static Carteira carteira(Usuario usuario, String nome) {
        Carteira carteira = new Carteira();
        carteira.setUsuario(usuario);
        carteira.setNome(nome);
        carteira.setTipo(TipoCarteira.DINHEIRO);
        carteira.setSaldo(new BigDecimal("100.00"));
        return carteira;
    }

    private static MovimentoCarteira movimento(Carteira carteira, BigDecimal valor, BigDecimal saldoResultante, String idempotencyKey) {
        MovimentoCarteira movimento = new MovimentoCarteira();
        movimento.setUsuario(carteira.getUsuario());
        movimento.setCarteira(carteira);
        movimento.setTipo(TipoMovimentoCarteira.ENTRADA);
        movimento.setValor(valor);
        movimento.setValorAssinado(valor);
        movimento.setOrigem(OrigemMovimentoCarteira.CARTEIRA_AJUSTE);
        movimento.setReferenciaTipo("CARTEIRA");
        movimento.setReferenciaId(carteira.getId());
        movimento.setDescricao("Ajuste manual");
        movimento.setDataMovimento(LocalDateTime.now());
        movimento.setSaldoResultante(saldoResultante);
        movimento.setIdempotencyKey(idempotencyKey);
        return movimento;
    }
}
