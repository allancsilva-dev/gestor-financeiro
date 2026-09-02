package com.gestor.financeiro;

import com.gestor.financeiro.dto.AlertaDto;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Notificacao;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoNotificacao;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.CartaoService;
import com.gestor.financeiro.service.ContaFixaService;
import com.gestor.financeiro.service.NotificacaoService;
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
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BACKLOG-0125 — estouro de limite do cartao avisa, nunca bloqueia.
 *
 * O aviso e derivado do estado (NotificacaoService.derivar), nao disparado no momento
 * da compra: a cobranca automatica da recorrencia roda 00:05 sem request nenhum, e
 * derivar cobre compra manual, parcelada e automatica com um bloco so.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LimiteCartaoAvisoTest {

    @Autowired CartaoService cartaoService;
    @Autowired ContaFixaService contaFixaService;
    @Autowired TransacaoService transacaoService;
    @Autowired NotificacaoService notificacaoService;
    @Autowired ContaFixaRepository contaFixaRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Estourado", "limite-cartao@teste.com", passwordEncoder.encode("123456")));
        carteiraRepository.save(TestDataFactory.carteira(usuario, "Corrente", new BigDecimal("1000.00")));
    }

    @Test
    void compraQueEstouraOLimiteGeraAvisoUmaVezPorCompetencia() {
        Conta cartao = cartao(new BigDecimal("100.00"));
        comprar(cartao, "150.00");

        List<Notificacao> novas = notificacaoService.sincronizarRetornandoNovas(usuario.getId());

        List<Notificacao> avisos = doTipo(novas);
        assertEquals(1, avisos.size(), "estourar o limite precisa gerar exatamente um aviso");
        assertEquals("LIMITE_ESTOURADO:" + cartao.getId() + ":" + YearMonth.now(),
                avisos.get(0).getChave());
        assertEquals(NotificacaoService.DESTINO_CARTAO, avisos.get(0).getDestino());
        assertEquals(cartao.getId(), avisos.get(0).getDestinoId());

        // segunda compra no mesmo mes nao repete o aviso: repetir treina o usuario a ignorar
        comprar(cartao, "50.00");
        assertTrue(doTipo(notificacaoService.sincronizarRetornandoNovas(usuario.getId())).isEmpty(),
                "o mesmo estouro na mesma competencia nao pode virar aviso novo");
    }

    /**
     * Conta.limiteTotal tem default ZERO e significa "nao informado". Sem esta guarda
     * todo usuario que nunca preencheu o limite receberia o aviso na primeira compra.
     */
    @Test
    void limiteNaoInformadoNuncaGeraAviso() {
        Conta cartao = cartao(BigDecimal.ZERO);
        comprar(cartao, "5000.00");

        assertTrue(doTipo(notificacaoService.sincronizarRetornandoNovas(usuario.getId())).isEmpty());
    }

    @Test
    void gastoDentroDoLimiteNaoGeraAviso() {
        Conta cartao = cartao(new BigDecimal("100.00"));
        comprar(cartao, "99.99");

        assertTrue(doTipo(notificacaoService.sincronizarRetornandoNovas(usuario.getId())).isEmpty());
        assertFalse(cartaoService.usoDoLimite(usuario.getId(), cartao.getId()).orElseThrow().estourado());
    }

    /** Canal sincrono: quem acabou de estourar ve na resposta, sem esperar o sync. */
    @Test
    void respostaDaOperacaoCarregaOAlertaDeLimite() {
        Conta cartao = cartao(new BigDecimal("100.00"));
        comprar(cartao, "150.00");

        List<AlertaDto> alertas = cartaoService.alertasDeLimite(usuario.getId(), cartao.getId());

        assertEquals(1, alertas.size());
        assertEquals("LIMITE_ESTOURADO", alertas.get(0).codigo());
        assertEquals(cartao.getId(), alertas.get(0).destinoId());
    }

    /** Cartao de outro titular nunca vaza alerta. */
    @Test
    void alertaNaoVazaEntreTitulares() {
        Conta cartao = cartao(new BigDecimal("100.00"));
        comprar(cartao, "150.00");

        Usuario outro = usuarioRepository.save(TestDataFactory.usuario(
                "Outro", "limite-outro@teste.com", passwordEncoder.encode("123456")));

        assertTrue(cartaoService.alertasDeLimite(outro.getId(), cartao.getId()).isEmpty());
        assertTrue(doTipo(notificacaoService.sincronizarRetornandoNovas(outro.getId())).isEmpty());
    }

    /**
     * O caso que motivou derivar em vez de disparar: a cobranca automatica da
     * assinatura acontece sem request e mesmo assim precisa avisar.
     */
    @Test
    void cobrancaAutomaticaDeAssinaturaQueEstouraTambemAvisa() {
        Conta cartao = cartao(new BigDecimal("100.00"));

        ContaFixa netflix = new ContaFixa();
        netflix.setUsuario(usuario);
        netflix.setNome("Netflix");
        netflix.setValorPlanejado(new BigDecimal("150.00"));
        netflix.setDiaVencimento(LocalDate.now().getDayOfMonth());
        netflix.setDataProximoVencimento(LocalDate.now());
        netflix.setTipo(TipoTransacao.SAIDA);
        netflix.setAtivo(true);
        netflix.setConta(cartao);
        netflix = contaFixaRepository.save(netflix);

        contaFixaService.realizar(netflix.getId(), null, null, usuario.getId(), false);

        assertEquals(1, doTipo(notificacaoService.sincronizarRetornandoNovas(usuario.getId())).size());
    }

    private Conta cartao(BigDecimal limite) {
        Conta novo = new Conta();
        novo.setNome("Cartao");
        novo.setDiaFechamento(10);
        novo.setDiaVencimento(20);
        novo.setLimiteTotal(limite);
        return cartaoService.criar(novo, usuario.getId());
    }

    private void comprar(Conta cartao, String valor) {
        Transacao compra = new Transacao();
        compra.setDescricao("Compra");
        compra.setValorTotal(new BigDecimal(valor));
        compra.setTipo(TipoTransacao.SAIDA);
        compra.setData(LocalDate.now());
        compra.setConta(cartao);
        compra.setParcelado(false);
        transacaoService.criar(compra, usuario.getId());
    }

    private List<Notificacao> doTipo(List<Notificacao> novas) {
        return novas.stream()
                .filter(n -> n.getTipo() == TipoNotificacao.LIMITE_ESTOURADO)
                .toList();
    }
}
