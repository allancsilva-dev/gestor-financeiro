package com.gestor.financeiro.service.orcamento;

import com.gestor.financeiro.TestDataFactory;
import com.gestor.financeiro.dto.OrcamentoCategoriaRequest;
import com.gestor.financeiro.dto.OrcamentoRequest;
import com.gestor.financeiro.dto.OrcamentoResponse;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.OrcamentoFechamento;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.PoliticaRollover;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.OrcamentoCategoriaRepository;
import com.gestor.financeiro.repository.OrcamentoFechamentoRepository;
import com.gestor.financeiro.repository.OrcamentoMensalRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.OrcamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rollover de orçamento: o que sobra ou falta em um mês, e o que disso atravessa para o seguinte.
 *
 * <p>O que estes testes travam: cada política carrega o que promete e nada além, fechar é
 * idempotente, e mudar a política depois não reescreve mês já fechado.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrcamentoFechamentoServiceTest {

    private static final YearMonth JULHO = YearMonth.of(2026, 7);
    private static final YearMonth AGOSTO = YearMonth.of(2026, 8);

    @Autowired OrcamentoFechamentoService fechamento;
    @Autowired OrcamentoService orcamentoService;
    @Autowired OrcamentoFechamentoRepository fechamentos;
    @Autowired OrcamentoMensalRepository orcamentos;
    @Autowired OrcamentoCategoriaRepository orcamentoCategorias;
    @Autowired CategoriaRepository categorias;
    @Autowired CarteiraRepository carteiras;
    @Autowired TransacaoRepository transacoes;
    @Autowired UsuarioRepository usuarios;

    private Usuario usuario;
    private Categoria categoria;
    private Carteira conta;

    @BeforeEach
    void setup() {
        usuario = usuarios.save(TestDataFactory.usuario("Rollover",
                "rollover-" + System.nanoTime() + "@test.local", "h"));
        categoria = categorias.save(TestDataFactory.categoria(usuario, "Mercado"));
        conta = carteiras.save(TestDataFactory.carteira(usuario, "Conta", new BigDecimal("5000.00")));
    }

    private void limite(YearMonth competencia, String valor, PoliticaRollover politica) {
        OrcamentoCategoriaRequest categoriaRequest = new OrcamentoCategoriaRequest();
        categoriaRequest.setCategoriaId(categoria.getId());
        categoriaRequest.setValorLimite(new BigDecimal(valor));
        categoriaRequest.setPoliticaRollover(politica.name());

        OrcamentoRequest request = new OrcamentoRequest();
        request.setMes(competencia.getMonthValue());
        request.setAno(competencia.getYear());
        request.setCategorias(List.of(categoriaRequest));
        orcamentoService.criarOuAtualizar(usuario.getId(), request);
    }

    /** Gasto direto na tabela: aqui o alvo é a regra de fechamento, não o caminho de lançamento. */
    private void gasto(YearMonth competencia, String valor) {
        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        transacao.setCategoria(categoria);
        transacao.setCarteira(conta);
        transacao.setDescricao("Compra");
        transacao.setValorTotal(new BigDecimal(valor));
        transacao.setTipo(TipoTransacao.SAIDA);
        transacao.setData(competencia.atDay(10));
        transacao.setAtiva(true);
        transacoes.save(transacao);
    }

    private OrcamentoFechamento fechado(YearMonth competencia) {
        return fechamentos.findByUsuarioIdAndCategoriaIdAndAnoAndMes(usuario.getId(), categoria.getId(),
                (short) competencia.getYear(), (short) competencia.getMonthValue()).orElseThrow();
    }

    @Test
    void sobraSoAtravessaQuandoAPoliticaPede() {
        limite(JULHO, "800.00", PoliticaRollover.SURPLUS_ONLY);
        gasto(JULHO, "650.00");

        fechamento.fechar(usuario.getId(), JULHO);

        OrcamentoFechamento julho = fechado(JULHO);
        assertEquals(0, new BigDecimal("150.00").compareTo(julho.getResultado()));
        assertEquals(0, new BigDecimal("150.00").compareTo(julho.getCarryOut()));
        assertEquals(0, new BigDecimal("150.00")
                .compareTo(fechamento.carryIn(usuario.getId(), categoria.getId(), AGOSTO)));
    }

    @Test
    void excessoAtravessaNegativoEReduzOMesSeguinte() {
        limite(JULHO, "800.00", PoliticaRollover.BOTH);
        gasto(JULHO, "900.00");

        fechamento.fechar(usuario.getId(), JULHO);

        assertEquals(0, new BigDecimal("-100.00").compareTo(fechado(JULHO).getCarryOut()));

        limite(AGOSTO, "800.00", PoliticaRollover.BOTH);
        OrcamentoResponse agosto = orcamentoService.buscarPorMes(usuario.getId(), 8, 2026);
        var linha = agosto.categorias().get(0);
        assertEquals(0, new BigDecimal("-100.00").compareTo(linha.carryIn()));
        assertEquals(0, new BigDecimal("700.00").compareTo(linha.valorDisponivel()),
                "quem estourou julho tem menos para gastar em agosto");
    }

    @Test
    void politicaNoneNaoCarregaNada() {
        limite(JULHO, "800.00", PoliticaRollover.NONE);
        gasto(JULHO, "650.00");

        fechamento.fechar(usuario.getId(), JULHO);

        assertEquals(0, BigDecimal.ZERO.compareTo(fechado(JULHO).getCarryOut()));
        assertEquals(0, BigDecimal.ZERO
                .compareTo(fechamento.carryIn(usuario.getId(), categoria.getId(), AGOSTO)));
    }

    @Test
    void somenteExcessoIgnoraSobra() {
        limite(JULHO, "800.00", PoliticaRollover.DEFICIT_ONLY);
        gasto(JULHO, "500.00");

        fechamento.fechar(usuario.getId(), JULHO);

        assertEquals(0, new BigDecimal("300.00").compareTo(fechado(JULHO).getResultado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fechado(JULHO).getCarryOut()),
                "quem só carrega excesso não ganha a sobra de volta");
    }

    @Test
    void fecharDuasVezesNaoRecalculaNemDuplica() {
        limite(JULHO, "800.00", PoliticaRollover.SURPLUS_ONLY);
        gasto(JULHO, "650.00");

        assertEquals(1, fechamento.fechar(usuario.getId(), JULHO));
        assertEquals(0, fechamento.fechar(usuario.getId(), JULHO), "competência fechada é no-op");
        assertEquals(1, fechamentos.findByUsuarioIdAndAnoAndMes(usuario.getId(), (short) 2026, (short) 7).size());
    }

    @Test
    void mudarAPoliticaDepoisNaoReescreveMesFechado() {
        limite(JULHO, "800.00", PoliticaRollover.SURPLUS_ONLY);
        gasto(JULHO, "650.00");
        fechamento.fechar(usuario.getId(), JULHO);

        // O dono muda de ideia em agosto; julho continua como foi fechado.
        limite(JULHO, "800.00", PoliticaRollover.NONE);
        fechamento.fechar(usuario.getId(), JULHO);

        OrcamentoFechamento julho = fechado(JULHO);
        assertEquals(PoliticaRollover.SURPLUS_ONLY, julho.getPolitica());
        assertEquals(0, new BigDecimal("150.00").compareTo(julho.getCarryOut()));
        assertEquals(OrcamentoFechamento.REGRA_VERSAO_ATUAL, julho.getRegraVersao());
    }

    @Test
    void carryEncadeiaEntreMesesConsecutivos() {
        limite(JULHO, "800.00", PoliticaRollover.BOTH);
        gasto(JULHO, "600.00");
        fechamento.fechar(usuario.getId(), JULHO);

        limite(AGOSTO, "800.00", PoliticaRollover.BOTH);
        gasto(AGOSTO, "1000.00");
        fechamento.fechar(usuario.getId(), AGOSTO);

        OrcamentoFechamento agosto = fechado(AGOSTO);
        assertEquals(0, new BigDecimal("200.00").compareTo(agosto.getCarryIn()));
        assertEquals(0, new BigDecimal("0.00").compareTo(agosto.getResultado()),
                "800 de base + 200 que sobraram - 1000 gastos fecha exatamente em zero");
        assertTrue(agosto.getFechadoEm() != null);
    }

    @Test
    void semOrcamentoNaoHaOQueFechar() {
        assertEquals(0, fechamento.fechar(usuario.getId(), JULHO));
    }
}
