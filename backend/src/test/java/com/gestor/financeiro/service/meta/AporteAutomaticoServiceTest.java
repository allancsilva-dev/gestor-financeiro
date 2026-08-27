package com.gestor.financeiro.service.meta;

import com.gestor.financeiro.TestDataFactory;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.ModalidadeMeta;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.MovimentoMetaRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.NotificacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.MetaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aporte automático de meta.
 *
 * <p>O que estes testes protegem: dinheiro só se move com autorização explícita, o mesmo mês nunca
 * é aportado duas vezes, e falta de saldo vira aviso — nunca conta no vermelho.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AporteAutomaticoServiceTest {

    @Autowired AporteAutomaticoService aportes;
    @Autowired MetaService metaService;
    @Autowired MetaRepository metas;
    @Autowired CarteiraRepository carteiras;
    @Autowired NotificacaoRepository notificacoes;
    @Autowired MovimentoMetaRepository movimentosMeta;
    @Autowired MovimentoCarteiraRepository movimentosCarteira;
    @Autowired UsuarioRepository usuarios;
    @Autowired Clock clock;

    private Usuario usuario;
    private Carteira conta;
    private Meta meta;

    @BeforeEach
    void setup() {
        usuario = usuarios.save(TestDataFactory.usuario("Aporte",
                "aporte-" + System.nanoTime() + "@test.local", "h"));
        conta = carteiras.save(TestDataFactory.carteira(usuario, "Conta", new BigDecimal("1000.00")));

        Meta nova = new Meta();
        nova.setUsuario(usuario);
        nova.setNome("Viagem");
        nova.setValorTotal(new BigDecimal("5000.00"));
        nova.setValorMensal(new BigDecimal("200.00"));
        nova.setModalidade(ModalidadeMeta.RESERVA_VIRTUAL);
        nova.setDataInicio(LocalDate.now(clock));
        meta = metaService.criar(nova, usuario.getId());
    }

    @org.junit.jupiter.api.AfterEach
    void limpar() {
        // Teste não transacional: o que ele cria precisa sair na ordem das FKs, senão trava a
        // limpeza de outros testes da suíte (metas apontam para carteiras).
        movimentosMeta.deleteAll();
        movimentosCarteira.deleteAll();
        notificacoes.deleteAll(notificacoes.findAll().stream()
                .filter(n -> n.getUsuario().getId().equals(usuario.getId())).toList());
        metas.deleteAll(metas.findByUsuarioId(usuario.getId()));
        carteiras.deleteAll(carteiras.findAll().stream()
                .filter(c -> c.getUsuario().getId().equals(usuario.getId())).toList());
        usuarios.deleteById(usuario.getId());
    }

    private void ligarAporte() {
        aportes.configurar(usuario.getId(), meta.getId(), true,
                (short) LocalDate.now(clock).getDayOfMonth(), conta.getId(), null);
    }

    private BigDecimal reservado() {
        return metas.findById(meta.getId()).orElseThrow().getValorReservado();
    }

    @Test
    void semAutorizacaoNadaAcontece() {
        assertEquals(0, aportes.executar(usuario.getId()));
        assertEquals(0, BigDecimal.ZERO.compareTo(reservado()),
                "valor mensal preenchido é planejamento, não autorização para mover dinheiro");
    }

    @Test
    void aporteGuardaOValorDoMes() {
        ligarAporte();

        assertEquals(1, aportes.executar(usuario.getId()));
        assertEquals(0, new BigDecimal("200.00").compareTo(reservado()));
    }

    @Test
    void reexecutarNoMesmoMesNaoGuardaDuasVezes() {
        ligarAporte();
        aportes.executar(usuario.getId());

        // É o que acontece quando o job é reexecutado por lease vencido ou o cron roda de novo.
        aportes.executar(usuario.getId());

        assertEquals(0, new BigDecimal("200.00").compareTo(reservado()));
    }

    @Test
    void antesDoDiaEscolhidoNaoAporta() {
        LocalDate hoje = LocalDate.now(clock);
        short amanha = (short) Math.min(28, hoje.getDayOfMonth() + 1);
        if (amanha <= hoje.getDayOfMonth()) return; // dia 28 ou fim de mês: nada a verificar

        aportes.configurar(usuario.getId(), meta.getId(), true, amanha, conta.getId(), null);

        assertEquals(0, aportes.executar(usuario.getId()));
        assertEquals(0, BigDecimal.ZERO.compareTo(reservado()));
    }

    @Test
    void saldoInsuficienteViraAvisoENaoSaldoNegativo() {
        Carteira vazia = carteiras.save(TestDataFactory.carteira(usuario, "Sem saldo", BigDecimal.ZERO));
        aportes.configurar(usuario.getId(), meta.getId(), true,
                (short) LocalDate.now(clock).getDayOfMonth(), vazia.getId(), null);

        assertEquals(0, aportes.executar(usuario.getId()));

        assertEquals(0, BigDecimal.ZERO.compareTo(reservado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                carteiras.findById(vazia.getId()).orElseThrow().getSaldo()),
                "o app não empurra a conta para o vermelho para cumprir meta");
        assertTrue(notificacoes.findChavesDoUsuario(usuario.getId()).stream()
                .anyMatch(chave -> chave.startsWith("META_APORTE_FALHOU:")), "o titular precisa saber");
    }

    @Test
    void desligarLimpaAConfiguracao() {
        ligarAporte();

        Meta desligada = aportes.configurar(usuario.getId(), meta.getId(), false, null, null, null);

        assertFalse(desligada.getAporteAutomatico());
        assertEquals(null, desligada.getAporteDia());
        assertEquals(null, desligada.getAporteCarteira());
        assertEquals(0, aportes.executar(usuario.getId()));
    }

    @Test
    void ligarSemValorDiaOuContaEhRecusado() {
        Meta semValor = new Meta();
        semValor.setUsuario(usuario);
        semValor.setNome("Sem plano");
        semValor.setValorTotal(new BigDecimal("100.00"));
        semValor.setModalidade(ModalidadeMeta.RESERVA_VIRTUAL);
        Meta criada = metaService.criar(semValor, usuario.getId());

        assertThrows(BusinessException.class,
                () -> aportes.configurar(usuario.getId(), criada.getId(), true, (short) 5, conta.getId(), null));
        assertThrows(BusinessException.class,
                () -> aportes.configurar(usuario.getId(), meta.getId(), true, (short) 31, conta.getId(), null));
    }

    @Test
    void cofreNaoServeComoOrigemDoAporte() {
        Carteira cofre = carteiras.save(TestDataFactory.carteira(usuario, "Cofre", BigDecimal.ZERO));
        cofre.setSubtipo(com.gestor.financeiro.model.enums.SubtipoContaFinanceira.COFRE);
        carteiras.save(cofre);

        assertThrows(BusinessException.class, () -> aportes.configurar(usuario.getId(), meta.getId(), true,
                (short) 5, cofre.getId(), null));
    }
}
