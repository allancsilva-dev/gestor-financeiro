package com.gestor.financeiro;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PR-F2-11 — Cofre real por meta (ADR-0012): reserva/resgate sao operacoes com
 * par carteira <-> COFRE; invariante valorReservado == saldo do cofre; cofre
 * fora do saldo legado e da listagem legada.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MetaCofreTest {

    @Autowired MetaService metaService;
    @Autowired MetaRepository metaRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired MovimentoCarteiraRepository movimentoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private Carteira carteira;
    private Meta meta;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNome("Cofre F2");
        usuario.setEmail("cofre-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);

        carteira = new Carteira();
        carteira.setNome("Corrente");
        carteira.setSubtipo(SubtipoContaFinanceira.CORRENTE);
        carteira.setSaldo(new BigDecimal("1000.00"));
        carteira.setUsuario(usuario);
        carteira = carteiraRepository.save(carteira);

        Meta nova = new Meta();
        nova.setNome("Viagem");
        nova.setValorTotal(new BigDecimal("500.00"));
        meta = metaService.criar(nova, usuario.getId());
    }

    @Test
    void reservaCriaCofreComParDeLancamentosEInvarianteVale() {
        Meta atualizada = metaService.adicionarValor(
                meta.getId(), new BigDecimal("200.00"), carteira.getId(), usuario.getId());

        Carteira cofre = metaRepository.findById(atualizada.getId()).orElseThrow().getCofre();
        assertNotNull(cofre);
        assertEquals(SubtipoContaFinanceira.COFRE, cofre.getSubtipo());
        assertEquals(NaturezaContaFinanceira.ATIVO, cofre.getNatureza());

        // invariante: valorReservado == saldo do cofre; carteira debitada
        assertEquals(0, new BigDecimal("200.00").compareTo(atualizada.getValorReservado()));
        assertEquals(0, atualizada.getValorReservado().compareTo(
                carteiraRepository.findById(cofre.getId()).orElseThrow().getSaldo()));
        assertEquals(0, new BigDecimal("800.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));

        // par de lancamentos vinculados a mesma operacao
        Long operacaoId = movimentoRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), cofre.getId())
                .get(0).getOperacao().getId();
        assertEquals(2, movimentoRepository.findByOperacaoIdOrderByValorAssinadoAsc(operacaoId).size());

        // cofre fora do saldo legado (dinheiro reservado nao e disponivel)
        assertEquals(0, new BigDecimal("800.00").compareTo(
                carteiraRepository.sumSaldoByUsuarioId(usuario.getId())));
    }

    @Test
    void resgateDevolveDoCofreParaCarteira() {
        metaService.adicionarValor(meta.getId(), new BigDecimal("200.00"), carteira.getId(), usuario.getId());
        Meta aposResgate = metaService.removerValor(
                meta.getId(), new BigDecimal("150.00"), carteira.getId(), usuario.getId());

        assertEquals(0, new BigDecimal("50.00").compareTo(aposResgate.getValorReservado()));
        Carteira cofre = metaRepository.findById(meta.getId()).orElseThrow().getCofre();
        assertEquals(0, new BigDecimal("50.00").compareTo(
                carteiraRepository.findById(cofre.getId()).orElseThrow().getSaldo()));
        assertEquals(0, new BigDecimal("950.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
    }

    @Test
    void resgateAcimaDoReservadoEBloqueado() {
        metaService.adicionarValor(meta.getId(), new BigDecimal("100.00"), carteira.getId(), usuario.getId());

        assertThrows(BusinessException.class, () -> metaService.removerValor(
                meta.getId(), new BigDecimal("300.00"), carteira.getId(), usuario.getId()));
    }
}
