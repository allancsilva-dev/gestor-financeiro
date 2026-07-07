package com.gestor.financeiro;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinancialIntegrityTest {

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private MetaRepository metaRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        carteiraRepository.deleteAll();
        metaRepository.deleteAll();
        contaRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(TestDataFactory.usuario("Diana", "diana@teste.com", passwordEncoder.encode("123456")));
    }

    @Test
    void carteira_deveTerCampoVersion() {
        Carteira c = new Carteira();
        c.setUsuario(usuario);
        c.setNome("Teste");
        c.setTipo(TipoCarteira.DINHEIRO);
        c.setSaldo(BigDecimal.valueOf(100));
        Carteira saved = carteiraRepository.saveAndFlush(c);
        assertNotNull(saved.getVersion());
        assertEquals(0L, saved.getVersion());
        saved.setSaldo(BigDecimal.valueOf(200));
        Carteira updated = carteiraRepository.saveAndFlush(saved);
        assertEquals(1L, updated.getVersion());
    }

    @Test
    void conta_deveTerCampoVersion() {
        Conta c = new Conta();
        c.setUsuario(usuario);
        c.setNome("Teste");
        c.setTipo(TipoConta.CREDITO);
        Conta saved = contaRepository.saveAndFlush(c);
        assertNotNull(saved.getVersion());
    }

    @Test
    void meta_deveTerCampoVersion() {
        Meta m = new Meta();
        m.setUsuario(usuario);
        m.setNome("Teste");
        m.setValorTotal(BigDecimal.valueOf(1000));
        Meta saved = metaRepository.saveAndFlush(m);
        assertNotNull(saved.getVersion());
    }

    @Test
    void categoria_deveTerCampoVersion() {
        Categoria c = new Categoria();
        c.setUsuario(usuario);
        c.setNome("Teste");
        Categoria saved = categoriaRepository.saveAndFlush(c);
        assertNotNull(saved.getVersion());
    }
}
