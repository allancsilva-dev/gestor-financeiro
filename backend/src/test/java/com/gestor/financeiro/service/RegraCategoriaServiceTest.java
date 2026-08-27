package com.gestor.financeiro.service;

import com.gestor.financeiro.TestDataFactory;
import com.gestor.financeiro.dto.SugestaoCategoriaResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoCasamentoRegra;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regras de categorização do titular. O que fica travado aqui: quem escreveu a regra manda mais que
 * a heurística, a ordem de decisão é estável, e a fronteira do titular não vaza.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegraCategoriaServiceTest {

    @Autowired RegraCategoriaService service;
    @Autowired SugestaoCategoriaService sugestao;
    @Autowired CategoriaRepository categorias;
    @Autowired UsuarioRepository usuarios;

    private Usuario usuario;
    private Categoria alimentacao;
    private Categoria transporte;

    @BeforeEach
    void setup() {
        usuario = usuarios.save(TestDataFactory.usuario("Regras", "regras-" + System.nanoTime() + "@test.local", "h"));
        alimentacao = categorias.save(TestDataFactory.categoria(usuario, "Alimentação"));
        transporte = categorias.save(TestDataFactory.categoria(usuario, "Transporte"));
    }

    @Test
    void regraCasaIgnorandoAcentoECaixa() {
        service.criar(usuario.getId(), "  MERCADO da Esquina ", TipoCasamentoRegra.CONTEM, null,
                alimentacao.getId(), null);

        assertEquals(alimentacao.getId(),
                service.aplicar(usuario.getId(), "Compra no Mercado dá Esquina 12/08", TipoTransacao.SAIDA)
                        .orElseThrow().getId());
    }

    @Test
    void prioridadeMenorDecidePrimeiro() {
        service.criar(usuario.getId(), "posto", TipoCasamentoRegra.CONTEM, null, alimentacao.getId(), 200);
        service.criar(usuario.getId(), "posto ipiranga", TipoCasamentoRegra.CONTEM, null, transporte.getId(), 10);

        assertEquals(transporte.getId(),
                service.aplicar(usuario.getId(), "POSTO IPIRANGA 22", TipoTransacao.SAIDA).orElseThrow().getId());
    }

    @Test
    void regraDeEntradaNaoCategorizaSaida() {
        service.criar(usuario.getId(), "salario", TipoCasamentoRegra.CONTEM, TipoTransacao.ENTRADA,
                alimentacao.getId(), null);

        assertTrue(service.aplicar(usuario.getId(), "Salario", TipoTransacao.SAIDA).isEmpty());
        assertTrue(service.aplicar(usuario.getId(), "Salario", TipoTransacao.ENTRADA).isPresent());
    }

    @Test
    void regraDoTitularGanhaDaHeuristica() {
        service.criar(usuario.getId(), "uber", TipoCasamentoRegra.CONTEM, null, transporte.getId(), null);

        SugestaoCategoriaResponse resposta = sugestao.sugerir(usuario.getId(), "Uber viagem", TipoTransacao.SAIDA);

        assertEquals(SugestaoCategoriaService.CRITERIO_REGRA_DO_TITULAR, resposta.criterio());
        assertEquals(transporte.getId(), resposta.categoria().id());
    }

    @Test
    void mesmoPadraoNoMesmoEscopoAtualizaEmVezDeDuplicar() {
        service.criar(usuario.getId(), "mercado", TipoCasamentoRegra.CONTEM, null, alimentacao.getId(), null);
        service.criar(usuario.getId(), "MERCADO", TipoCasamentoRegra.CONTEM, null, transporte.getId(), null);

        assertEquals(1, service.listar(usuario.getId()).size());
        assertEquals(transporte.getId(),
                service.aplicar(usuario.getId(), "mercado", TipoTransacao.SAIDA).orElseThrow().getId());
    }

    @Test
    void padraoCurtoDemaisEhRecusado() {
        assertThrows(BusinessException.class,
                () -> service.criar(usuario.getId(), "a", TipoCasamentoRegra.CONTEM, null, alimentacao.getId(), null));
    }

    @Test
    void naoAceitaCategoriaDeOutroTitular() {
        Usuario outro = usuarios.save(TestDataFactory.usuario("Outro", "outro-regra@test.local", "h"));
        Categoria alheia = categorias.save(TestDataFactory.categoria(outro, "Alheia"));

        assertThrows(ResourceNotFoundException.class,
                () -> service.criar(usuario.getId(), "qualquer", TipoCasamentoRegra.CONTEM, null,
                        alheia.getId(), null));
    }

    @Test
    void removerRegraDeOutroTitularNaoEhPermitido() {
        var regra = service.criar(usuario.getId(), "mercado", TipoCasamentoRegra.CONTEM, null,
                alimentacao.getId(), null);
        Usuario outro = usuarios.save(TestDataFactory.usuario("Outro", "outro-remove@test.local", "h"));

        assertThrows(ResourceNotFoundException.class, () -> service.remover(outro.getId(), regra.getId()));
        assertEquals(1, service.listar(usuario.getId()).size());
    }

    @Test
    void semRegraQueCaseNaoInventaCategoria() {
        service.criar(usuario.getId(), "farmacia", TipoCasamentoRegra.COMECA_COM, null, alimentacao.getId(), null);

        assertTrue(service.aplicar(usuario.getId(), "Compra farmacia central", TipoTransacao.SAIDA).isEmpty(),
                "COMECA_COM não pode casar no meio da frase");
    }
}
