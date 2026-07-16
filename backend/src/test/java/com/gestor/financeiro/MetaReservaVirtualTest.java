package com.gestor.financeiro;

import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.ModalidadeMeta;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.MetaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PR-F2-12 — Reserva virtual (ADR-0012): alocacao explicita sem lancamento no
 * ledger; saldo da conta intacto; alocacao limitada ao saldo; troca de
 * modalidade bloqueada com reserva.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MetaReservaVirtualTest {

    @Autowired MetaService metaService;
    @Autowired MetaRepository metaRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired MovimentoCarteiraRepository movimentoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private Carteira carteira;
    private Meta metaVirtual;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNome("Virtual F2");
        usuario.setEmail("virtual-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);

        carteira = new Carteira();
        carteira.setNome("Corrente");
        carteira.setSubtipo(SubtipoContaFinanceira.CORRENTE);
        carteira.setSaldo(new BigDecimal("500.00"));
        carteira.setUsuario(usuario);
        carteira = carteiraRepository.save(carteira);

        Meta nova = new Meta();
        nova.setNome("Emergencia");
        nova.setValorTotal(new BigDecimal("1000.00"));
        nova.setModalidade(ModalidadeMeta.RESERVA_VIRTUAL);
        metaVirtual = metaService.criar(nova, usuario.getId());
    }

    @Test
    void alocacaoVirtualNaoMoveDinheiroNemCriaCofre() {
        Meta alocada = metaService.adicionarValor(
                metaVirtual.getId(), new BigDecimal("300.00"), carteira.getId(), usuario.getId());

        assertEquals(0, new BigDecimal("300.00").compareTo(alocada.getValorReservado()));
        assertNull(metaRepository.findById(alocada.getId()).orElseThrow().getCofre());
        // saldo da conta intacto (o dinheiro continua la)
        assertEquals(0, new BigDecimal("500.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        // nenhum movimento de ledger gerado
        assertEquals(0, movimentoRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(
                        usuario.getId(), carteira.getId()).size());
    }

    @Test
    void alocacaoAcimaDoSaldoDaContaEBloqueada() {
        assertThrows(BusinessException.class, () -> metaService.adicionarValor(
                metaVirtual.getId(), new BigDecimal("600.00"), carteira.getId(), usuario.getId()));
    }

    @Test
    void desalocacaoReduzReservaSemMovimento() {
        metaService.adicionarValor(metaVirtual.getId(), new BigDecimal("300.00"),
                carteira.getId(), usuario.getId());
        Meta aposDesalocar = metaService.removerValor(
                metaVirtual.getId(), new BigDecimal("100.00"), carteira.getId(), usuario.getId());

        assertEquals(0, new BigDecimal("200.00").compareTo(aposDesalocar.getValorReservado()));
        assertEquals(0, new BigDecimal("500.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
    }

    @Test
    void trocaDeModalidadeComReservaEBloqueada() {
        metaService.adicionarValor(metaVirtual.getId(), new BigDecimal("100.00"),
                carteira.getId(), usuario.getId());

        Meta alteracao = new Meta();
        alteracao.setNome("Emergencia");
        alteracao.setValorTotal(new BigDecimal("1000.00"));
        alteracao.setModalidade(ModalidadeMeta.COFRE_REAL);

        assertThrows(BusinessException.class,
                () -> metaService.atualizar(metaVirtual.getId(), alteracao, usuario.getId()));
    }
}
