package com.gestor.financeiro;

import com.gestor.financeiro.dto.OrcamentoCategoriaRequest;
import com.gestor.financeiro.dto.OrcamentoRequest;
import com.gestor.financeiro.dto.OrcamentoResponse;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.OrcamentoCategoriaRepository;
import com.gestor.financeiro.repository.OrcamentoMensalRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.OrcamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrcamentoServiceTest {

    @Autowired
    private OrcamentoService orcamentoService;

    @Autowired
    private OrcamentoCategoriaRepository orcamentoCategoriaRepository;

    @Autowired
    private OrcamentoMensalRepository orcamentoMensalRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Categoria alimentacao;
    private Categoria transporte;

    @BeforeEach
    void setup() {
        orcamentoCategoriaRepository.deleteAll();
        orcamentoMensalRepository.deleteAll();
        transacaoRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(
                TestDataFactory.usuario("Budget", "budget@teste.com", passwordEncoder.encode("123456")));
        alimentacao = categoriaRepository.save(TestDataFactory.categoria(usuario, "Alimentação"));
        transporte = categoriaRepository.save(TestDataFactory.categoria(usuario, "Transporte"));
    }

    @Test
    void editarOrcamentoSalvoSubstituiLimitesSemViolacaoDeUnicidade() {
        orcamentoService.criarOuAtualizar(usuario.getId(), request(
                item(alimentacao.getId(), "500.00"),
                item(transporte.getId(), "200.00")));

        OrcamentoResponse atualizado = orcamentoService.criarOuAtualizar(usuario.getId(), request(
                item(alimentacao.getId(), "650.00"),
                item(transporte.getId(), "180.00")));

        assertEquals(2, atualizado.categorias().size());
        assertEquals(0, new BigDecimal("830.00").compareTo(atualizado.valorTotalPlanejado()));
        assertEquals(0, new BigDecimal("650.00").compareTo(atualizado.categorias().stream()
                .filter(c -> c.categoriaId().equals(alimentacao.getId()))
                .findFirst()
                .orElseThrow()
                .valorLimite()));
    }

    private static OrcamentoRequest request(OrcamentoCategoriaRequest... categorias) {
        OrcamentoRequest request = new OrcamentoRequest();
        request.setMes(7);
        request.setAno(2026);
        request.setCategorias(List.of(categorias));
        return request;
    }

    private static OrcamentoCategoriaRequest item(Long categoriaId, String valorLimite) {
        OrcamentoCategoriaRequest item = new OrcamentoCategoriaRequest();
        item.setCategoriaId(categoriaId);
        item.setValorLimite(new BigDecimal(valorLimite));
        return item;
    }
}
