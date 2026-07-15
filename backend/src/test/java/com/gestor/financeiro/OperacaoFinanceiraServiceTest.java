package com.gestor.financeiro;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.FinancialConflictException;
import com.gestor.financeiro.model.OperacaoFinanceira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.OrigemOperacaoFinanceira;
import com.gestor.financeiro.model.enums.PoliticaOperacao;
import com.gestor.financeiro.model.enums.StatusOperacaoFinanceira;
import com.gestor.financeiro.model.enums.TipoOperacaoFinanceira;
import com.gestor.financeiro.repository.OperacaoFinanceiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.CriarOperacaoCommand;
import com.gestor.financeiro.service.OperacaoFinanceiraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PR-F2-03 — Operacao financeira (ADR-0009): idempotencia por chave+hash
 * (payload igual retorna original; diferente conflita 409) e estorno que
 * preserva a original imutavel.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OperacaoFinanceiraServiceTest {

    @Autowired OperacaoFinanceiraService operacaoService;
    @Autowired OperacaoFinanceiraRepository operacaoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNome("Operacao F2");
        usuario.setEmail("operacao-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);
    }

    private CriarOperacaoCommand comando(String chave, String payload) {
        return new CriarOperacaoCommand(
                usuario.getId(),
                TipoOperacaoFinanceira.TRANSFERENCIA,
                PoliticaOperacao.CAIXA,
                OrigemOperacaoFinanceira.MANUAL,
                null,
                chave,
                payload,
                "Transferencia teste",
                null);
    }

    @Test
    void chaveRepetidaComPayloadIgualRetornaOperacaoOriginal() {
        OperacaoFinanceira primeira = operacaoService.criar(comando("op-idem-1", "{valor:100,de:1,para:2}"));
        OperacaoFinanceira segunda = operacaoService.criar(comando("op-idem-1", "{valor:100,de:1,para:2}"));

        assertEquals(primeira.getId(), segunda.getId());
        assertEquals(1, operacaoRepository.count());
    }

    @Test
    void chaveRepetidaComPayloadDiferenteConflita() {
        operacaoService.criar(comando("op-idem-2", "{valor:100,de:1,para:2}"));

        assertThrows(FinancialConflictException.class,
                () -> operacaoService.criar(comando("op-idem-2", "{valor:999,de:1,para:2}")));
        assertEquals(1, operacaoRepository.count());
    }

    @Test
    void semChaveCriaOperacoesIndependentes() {
        OperacaoFinanceira a = operacaoService.criar(comando(null, "{valor:10}"));
        OperacaoFinanceira b = operacaoService.criar(comando(null, "{valor:10}"));

        assertNotEquals(a.getId(), b.getId());
    }

    @Test
    void estornoCriaOperacaoReferenciandoOriginalEMarcaEstornada() {
        OperacaoFinanceira original = operacaoService.criar(comando("op-est-1", "{valor:50}"));

        OperacaoFinanceira estorno = operacaoService.estornar(
                usuario.getId(), original.getId(), "Correcao de valor", "op-est-1-estorno");

        assertEquals(TipoOperacaoFinanceira.ESTORNO, estorno.getTipo());
        assertNotNull(estorno.getEstornoDe());
        assertEquals(original.getId(), estorno.getEstornoDe().getId());
        assertEquals(StatusOperacaoFinanceira.ESTORNADA,
                operacaoRepository.findById(original.getId()).orElseThrow().getStatus());
        // conteudo financeiro da original permanece intacto
        assertEquals("Transferencia teste",
                operacaoRepository.findById(original.getId()).orElseThrow().getDescricao());
    }

    @Test
    void estornoDuploEBloqueado() {
        OperacaoFinanceira original = operacaoService.criar(comando("op-est-2", "{valor:50}"));
        operacaoService.estornar(usuario.getId(), original.getId(), "Correcao", "op-est-2-estorno");

        assertThrows(BusinessException.class,
                () -> operacaoService.estornar(usuario.getId(), original.getId(), "De novo", "op-est-2-estorno-b"));
    }
}
