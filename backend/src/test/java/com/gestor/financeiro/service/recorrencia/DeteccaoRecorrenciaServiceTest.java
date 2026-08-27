package com.gestor.financeiro.service.recorrencia;

import com.gestor.financeiro.TestDataFactory;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.RecorrenciaCandidata;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusRecorrenciaCandidata;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.RecorrenciaCandidataRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Detecção de recorrências e assinaturas.
 *
 * <p>O que estes testes protegem: o app sugere, nunca assume compromisso sozinho; três meses
 * distintos é o piso do que se chama de padrão; valor instável não vira assinatura; e decisão do
 * titular (descartar) é definitiva.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeteccaoRecorrenciaServiceTest {

    @Autowired DeteccaoRecorrenciaService deteccao;
    @Autowired RecorrenciaCandidataService decisao;
    @Autowired RecorrenciaCandidataRepository candidatas;
    @Autowired TransacaoRepository transacoes;
    @Autowired ContaFixaRepository contasFixas;
    @Autowired CategoriaRepository categorias;
    @Autowired CarteiraRepository carteiras;
    @Autowired UsuarioRepository usuarios;
    @Autowired Clock clock;

    private Usuario usuario;
    private Categoria categoria;
    private Carteira conta;

    @BeforeEach
    void setup() {
        usuario = usuarios.save(TestDataFactory.usuario("Recorrencia",
                "recorrencia-" + System.nanoTime() + "@test.local", "h"));
        categoria = categorias.save(TestDataFactory.categoria(usuario, "Assinaturas"));
        conta = carteiras.save(TestDataFactory.carteira(usuario, "Conta", new BigDecimal("5000.00")));
    }

    private void lancamento(String descricao, String valor, LocalDate data) {
        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        transacao.setCategoria(categoria);
        transacao.setCarteira(conta);
        transacao.setDescricao(descricao);
        transacao.setValorTotal(new BigDecimal(valor));
        transacao.setTipo(TipoTransacao.SAIDA);
        transacao.setData(data);
        transacao.setAtiva(true);
        transacoes.save(transacao);
    }

    /** Três meses seguidos, mesmo dia, mesmo valor: o caso clássico de assinatura. */
    private void assinaturaDeTresMeses() {
        LocalDate hoje = LocalDate.now(clock);
        lancamento("NETFLIX.COM", "39.90", hoje.minusMonths(3).withDayOfMonth(10));
        lancamento("Netflix.com ", "39.90", hoje.minusMonths(2).withDayOfMonth(10));
        lancamento("netflix.com", "39.90", hoje.minusMonths(1).withDayOfMonth(11));
    }

    @Test
    void detectaAssinaturaMensalIgnorandoCaixaEEspaco() {
        assinaturaDeTresMeses();

        assertEquals(1, deteccao.detectar(usuario.getId()));

        RecorrenciaCandidata candidata = decisao.sugeridas(usuario.getId()).get(0);
        assertEquals(0, new BigDecimal("39.90").compareTo(candidata.getValorMedio()));
        assertEquals(10, candidata.getDiaTipico(), "dia típico é a mediana, não a média");
        assertEquals(3, candidata.getOcorrencias());
        assertEquals(categoria.getId(), candidata.getCategoria().getId());
    }

    @Test
    void duasOcorrenciasNaoSaoPadrao() {
        LocalDate hoje = LocalDate.now(clock);
        lancamento("Spotify", "21.90", hoje.minusMonths(2).withDayOfMonth(5));
        lancamento("Spotify", "21.90", hoje.minusMonths(1).withDayOfMonth(5));

        assertEquals(0, deteccao.detectar(usuario.getId()));
        assertTrue(decisao.sugeridas(usuario.getId()).isEmpty());
    }

    @Test
    void tresComprasNoMesmoMesNaoViramRecorrencia() {
        LocalDate mes = LocalDate.now(clock).minusMonths(1);
        lancamento("Padaria", "30.00", mes.withDayOfMonth(3));
        lancamento("Padaria", "30.00", mes.withDayOfMonth(12));
        lancamento("Padaria", "30.00", mes.withDayOfMonth(20));

        assertEquals(0, deteccao.detectar(usuario.getId()),
                "parcelamento e compra repetida no mesmo mês não são assinatura");
    }

    @Test
    void valorInstavelNaoViraAssinatura() {
        LocalDate hoje = LocalDate.now(clock);
        lancamento("Conta de luz", "80.00", hoje.minusMonths(3).withDayOfMonth(8));
        lancamento("Conta de luz", "260.00", hoje.minusMonths(2).withDayOfMonth(8));
        lancamento("Conta de luz", "150.00", hoje.minusMonths(1).withDayOfMonth(8));

        assertEquals(0, deteccao.detectar(usuario.getId()),
                "sugerir valor fixo para conta que triplica seria mentira");
    }

    @Test
    void detectarDuasVezesNaoDuplicaSugestao() {
        assinaturaDeTresMeses();

        deteccao.detectar(usuario.getId());
        deteccao.detectar(usuario.getId());

        assertEquals(1, decisao.sugeridas(usuario.getId()).size());
    }

    @Test
    void confirmarCriaRecorrenciaSemLigarExecucaoAutomatica() {
        assinaturaDeTresMeses();
        deteccao.detectar(usuario.getId());
        RecorrenciaCandidata candidata = decisao.sugeridas(usuario.getId()).get(0);

        ContaFixa criada = decisao.confirmar(usuario.getId(), candidata.getId());

        assertEquals(0, new BigDecimal("39.90").compareTo(criada.getValorPlanejado()));
        assertEquals(10, criada.getDiaVencimento());
        assertFalse(criada.getExecucaoAutomatica(),
                "detectar repetição não é autorização para o app lançar sozinho");
        assertTrue(decisao.sugeridas(usuario.getId()).isEmpty(), "confirmada sai da lista de sugestões");
        assertEquals(StatusRecorrenciaCandidata.CONFIRMADA,
                candidatas.findById(candidata.getId()).orElseThrow().getStatus());
    }

    @Test
    void descartadaNaoVoltaASerSugerida() {
        assinaturaDeTresMeses();
        deteccao.detectar(usuario.getId());
        RecorrenciaCandidata candidata = decisao.sugeridas(usuario.getId()).get(0);

        decisao.descartar(usuario.getId(), candidata.getId());
        deteccao.detectar(usuario.getId());

        assertTrue(decisao.sugeridas(usuario.getId()).isEmpty(), "decisão do titular é definitiva");
    }

    @Test
    void sugestaoJaDescartadaNaoPodeSerConfirmada() {
        assinaturaDeTresMeses();
        deteccao.detectar(usuario.getId());
        RecorrenciaCandidata candidata = decisao.sugeridas(usuario.getId()).get(0);
        decisao.descartar(usuario.getId(), candidata.getId());

        assertThrows(BusinessException.class, () -> decisao.confirmar(usuario.getId(), candidata.getId()));
    }

    @Test
    void lancamentoQueJaVeioDeRecorrenciaNaoViraSugestao() {
        ContaFixa contaFixa = new ContaFixa();
        contaFixa.setUsuario(usuario);
        contaFixa.setNome("Aluguel");
        contaFixa.setValorPlanejado(new BigDecimal("1500.00"));
        contaFixa.setDiaVencimento(5);
        contaFixa.setTipo(TipoTransacao.SAIDA);
        ContaFixa salva = contasFixas.save(contaFixa);

        LocalDate hoje = LocalDate.now(clock);
        for (int mes = 3; mes >= 1; mes--) {
            Transacao transacao = new Transacao();
            transacao.setUsuario(usuario);
            transacao.setCarteira(conta);
            transacao.setContaFixa(salva);
            transacao.setDescricao("Aluguel");
            transacao.setValorTotal(new BigDecimal("1500.00"));
            transacao.setTipo(TipoTransacao.SAIDA);
            transacao.setData(hoje.minusMonths(mes).withDayOfMonth(5));
            transacao.setAtiva(true);
            transacoes.save(transacao);
        }

        assertEquals(0, deteccao.detectar(usuario.getId()),
                "o que já é recorrência não precisa ser sugerido de novo");
    }

    @Test
    void sugestaoDeOutroTitularNaoEhAlcancavel() {
        assinaturaDeTresMeses();
        deteccao.detectar(usuario.getId());
        Long candidataId = decisao.sugeridas(usuario.getId()).get(0).getId();
        Usuario outro = usuarios.save(TestDataFactory.usuario("Outro", "outro-rec@test.local", "h"));

        assertThrows(RuntimeException.class, () -> decisao.confirmar(outro.getId(), candidataId));
        assertEquals(List.of(), decisao.sugeridas(outro.getId()));
    }
}
