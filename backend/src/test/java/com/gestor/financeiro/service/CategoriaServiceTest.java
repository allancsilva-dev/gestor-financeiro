package com.gestor.financeiro.service;

import com.gestor.financeiro.TestDataFactory;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Atualização de categoria. O que fica travado aqui: um PUT que não fala do ícone não pode apagar
 * o emoji já gravado. O campo é opcional no CategoriaUpdateRequest, e antes disso o cliente perdia
 * o ícone sem ter pedido — o app mobile desenha esse campo como texto e voltava a mostrar fallback.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoriaServiceTest {

    @Autowired CategoriaService service;
    @Autowired CategoriaRepository categorias;
    @Autowired UsuarioRepository usuarios;

    private Usuario usuario;
    private Categoria categoria;

    @BeforeEach
    void setup() {
        usuario = usuarios.save(TestDataFactory.usuario(
                "Categoria", "categoria-" + System.nanoTime() + "@test.local", "h"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        categoria = TestDataFactory.categoria(usuario, "Mercado");
        categoria.setIcone("\uD83D\uDED2"); // 🛒
        categoria = categorias.save(categoria);
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void putSemIconePreservaOEmojiJaGravado() {
        Categoria semIcone = new Categoria();
        semIcone.setNome("Mercado do mês");
        semIcone.setCor("#10B981");
        semIcone.setValorEsperado(BigDecimal.TEN);
        // icone fica null, como chega de um PUT que omite o campo

        Categoria salva = service.atualizar(categoria.getId(), semIcone);

        assertEquals("Mercado do mês", salva.getNome());
        assertEquals("\uD83D\uDED2", salva.getIcone());
    }

    @Test
    void putComIconeTrocaOEmoji() {
        Categoria comIcone = new Categoria();
        comIcone.setNome("Mercado");
        comIcone.setCor("#10B981");
        comIcone.setIcone("\uD83C\uDFEA"); // 🏪
        comIcone.setValorEsperado(BigDecimal.ZERO);

        Categoria salva = service.atualizar(categoria.getId(), comIcone);

        assertEquals("\uD83C\uDFEA", salva.getIcone());
    }

    @Test
    void putComIconeVazioLimpaOCampoDeProposito() {
        // String vazia é escolha explícita do cliente, diferente de omitir o campo.
        Categoria vazio = new Categoria();
        vazio.setNome("Mercado");
        vazio.setCor("#10B981");
        vazio.setIcone("");
        vazio.setValorEsperado(BigDecimal.ZERO);

        Categoria salva = service.atualizar(categoria.getId(), vazio);

        assertEquals("", salva.getIcone());
    }
}
