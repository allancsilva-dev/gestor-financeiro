package com.gestor.financeiro;

import com.gestor.financeiro.dto.RelatorioContaDto;
import com.gestor.financeiro.dto.RelatorioResponse;
import com.gestor.financeiro.dto.RelatorioTransacaoDto;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.RelatorioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RelatorioServiceTest {

    @Autowired
    private RelatorioService relatorioService;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Categoria catA;
    private Categoria catB;
    private Conta contaX;
    private Conta contaY;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Relator", "relatorio-service@teste.com", passwordEncoder.encode("123456")));
        catA = categoriaRepository.save(TestDataFactory.categoria(usuario, "Mercado"));
        catB = categoriaRepository.save(TestDataFactory.categoria(usuario, "Transporte"));
        contaX = contaRepository.save(TestDataFactory.conta(usuario, "Conta X", TipoConta.DEBITO));
        contaY = contaRepository.save(TestDataFactory.conta(usuario, "Conta Y", TipoConta.CREDITO));

        // catA em contaX: 100 + 200 + 50 = 350
        saida("Feira", "100.00", catA, contaX);
        saida("Padaria", "200.00", catA, contaX);
        saida("Açougue", "50.00", catA, contaX);
        // catB em contaX: 300
        saida("Uber", "300.00", catB, contaX);
        // sem categoria, em contaY: 400 (maior despesa)
        saida("Diversos", "400.00", null, contaY);
        // ENTRADA nao entra em gastos/despesas
        entrada("Salario", "1000.00");
        // cancelada (ativa=false) nao pode somar nem contar (PROB-0035)
        Transacao cancelada = TestDataFactory.transacao(usuario, catA, "Estorno", new BigDecimal("9999.00"));
        cancelada.setConta(contaX);
        cancelada.setAtiva(false);
        transacaoRepository.save(cancelada);
    }

    @Test
    void relatorioAgregaTotaisIgnorandoEntradaECancelada() {
        RelatorioResponse r = gerar();

        assertEquals(0, new BigDecimal("1000.00").compareTo(r.totalEntradas()));
        // 100+200+50+300+400 = 1050 (cancelada de 9999 excluida)
        assertEquals(0, new BigDecimal("1050.00").compareTo(r.totalSaidas()));
        assertEquals(0, new BigDecimal("-50.00").compareTo(r.saldo()));
        // 5 saidas ativas; ENTRADA e cancelada fora
        assertEquals(5, r.totalTransacoes());
    }

    @Test
    void maioresDespesasOrdenaDescLimitaEUsaCorPadraoSemCategoria() {
        RelatorioResponse r = gerar();
        var despesas = r.maioresDespesas();

        assertEquals(5, despesas.size());
        // ordem desc por valor: 400, 300, 200, 100, 50
        assertEquals(0, new BigDecimal("400.00").compareTo(despesas.get(0).valor()));
        assertEquals(0, new BigDecimal("300.00").compareTo(despesas.get(1).valor()));
        assertEquals(0, new BigDecimal("50.00").compareTo(despesas.get(4).valor()));

        // maior despesa nao tem categoria -> nome null, cor padrao
        RelatorioTransacaoDto semCategoria = despesas.get(0);
        assertNull(semCategoria.categoriaNome());
        assertEquals("#6B7280", semCategoria.categoriaCor());
    }

    @Test
    void gastosPorContaAgrupaOrdenaEResolveTipoDaConta() {
        RelatorioResponse r = gerar();
        var contas = r.gastosPorConta();

        assertEquals(2, contas.size());
        RelatorioContaDto primeira = contas.get(0);
        // contaX = 350+300 = 650 (maior); contaY = 400
        assertEquals(0, new BigDecimal("650.00").compareTo(primeira.valorTotal()));
        assertEquals("Conta X", primeira.nome());
        assertEquals(TipoConta.DEBITO.getDescricao(), primeira.tipo());
        assertEquals(0, new BigDecimal("400.00").compareTo(contas.get(1).valorTotal()));
    }

    private RelatorioResponse gerar() {
        LocalDate hoje = LocalDate.now();
        return relatorioService.gerarRelatorio(usuario.getId(), hoje.withDayOfMonth(1), hoje);
    }

    private void saida(String descricao, String valor, Categoria categoria, Conta conta) {
        Transacao t = TestDataFactory.transacao(usuario, categoria, descricao, new BigDecimal(valor));
        t.setConta(conta);
        transacaoRepository.save(t);
    }

    private void entrada(String descricao, String valor) {
        Transacao t = TestDataFactory.transacao(usuario, null, descricao, new BigDecimal(valor));
        t.setTipo(TipoTransacao.ENTRADA);
        transacaoRepository.save(t);
    }
}
