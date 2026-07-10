package com.gestor.financeiro;

import com.gestor.financeiro.dto.InsightsResponse;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.InsightsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InsightsServiceTest {

    @Autowired
    private InsightsService insightsService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    private Usuario usuario;
    private Categoria categoria;

    @BeforeEach
    void setup() {
        transacaoRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", "hash"));
        categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Mercado"));
    }

    private void saidaEm(LocalDate data, String descricao, String valor) {
        Transacao t = TestDataFactory.transacao(usuario, categoria, descricao, new BigDecimal(valor));
        t.setData(data);
        transacaoRepository.save(t);
    }

    @Test
    void gerarInsights_naoQuebraComGastoNoMesAnterior() {
        // Regressão: mapAnterior lia row[1] (nome, String) como BigDecimal → ClassCastException/500
        LocalDate mesPassado = LocalDate.now().minusMonths(1).withDayOfMonth(10);
        saidaEm(mesPassado, "Mercado passado", "600.00");
        saidaEm(LocalDate.now(), "Mercado atual", "900.00");

        InsightsResponse resposta = assertDoesNotThrow(() -> insightsService.gerarInsights(usuario.getId()));

        assertEquals(0, resposta.getGastoMesAtual().compareTo(new BigDecimal("900.00")));
        assertFalse(resposta.getCategoriasAlerta().isEmpty());
        // 900 vs 600 no mês anterior → +50% na categoria
        assertEquals(0, resposta.getCategoriasAlerta().get(0).getVariacaoPercentual()
            .compareTo(new BigDecimal("50.0000")));
    }

    @Test
    void gerarInsights_naoDivideForZeroQuandoMesAtualZerado() {
        // Regressão: variação dividia por gastoMesAtual (zero aqui) → ArithmeticException
        LocalDate mesPassado = LocalDate.now().minusMonths(1).withDayOfMonth(10);
        saidaEm(mesPassado, "Só no passado", "300.00");

        InsightsResponse resposta = assertDoesNotThrow(() -> insightsService.gerarInsights(usuario.getId()));

        assertEquals(0, resposta.getGastoMesAtual().compareTo(BigDecimal.ZERO));
        // média 3 meses = 100; variação = (0-100)/100 = -100%
        assertEquals(0, resposta.getVariacaoPercentual().compareTo(new BigDecimal("-100.0000")));
        assertTrue(resposta.getResumo() != null && !resposta.getResumo().isBlank());
    }
}
