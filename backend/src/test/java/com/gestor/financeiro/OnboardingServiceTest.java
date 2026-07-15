package com.gestor.financeiro;

import com.gestor.financeiro.dto.OnboardingFinalizarRequest;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.service.OnboardingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OnboardingServiceTest {
    @Autowired OnboardingService onboardingService;
    @Autowired ContaFixaRepository contaFixaRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired com.gestor.financeiro.repository.UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Onboarding", "onboarding@teste.com", passwordEncoder.encode("123456")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void finalizarCriaRendaComoEntradaComCategoriaRenda() {
        onboardingService.finalizar(request());

        ContaFixa renda = rendaDoUsuario();
        assertEquals(TipoTransacao.ENTRADA, renda.getTipo());
        assertNotNull(renda.getCategoria());
        assertEquals(OnboardingService.CATEGORIA_RENDA, renda.getCategoria().getNome());
        assertTrue(usuarioRepository.findById(usuario.getId()).orElseThrow().isOnboardingCompleto());
    }

    @Test
    void finalizarReutilizaCategoriaRendaExistenteIgnorandoCaixa() {
        Categoria existente = categoriaRepository.save(TestDataFactory.categoria(usuario, "RENDA"));

        onboardingService.finalizar(request());

        ContaFixa renda = rendaDoUsuario();
        assertEquals(existente.getId(), renda.getCategoria().getId());
        long categoriasRenda = categoriaRepository.findByUsuarioId(usuario.getId()).stream()
                .filter(c -> c.getNome().equalsIgnoreCase(OnboardingService.CATEGORIA_RENDA))
                .count();
        assertEquals(1, categoriasRenda);
    }

    @Test
    void finalizarRepetidoNaoDuplicaRendaNemCategorias() {
        onboardingService.finalizar(request());
        onboardingService.finalizar(request());

        List<ContaFixa> rendas = contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId()).stream()
                .filter(c -> c.getNome().equalsIgnoreCase("Salário"))
                .toList();
        assertEquals(1, rendas.size());
        long categoriasRenda = categoriaRepository.findByUsuarioId(usuario.getId()).stream()
                .filter(c -> c.getNome().equalsIgnoreCase(OnboardingService.CATEGORIA_RENDA))
                .count();
        assertEquals(1, categoriasRenda);
    }

    @Test
    void rendaNaoUsaPrimeiraCategoriaDeGasto() {
        onboardingService.finalizar(request());

        ContaFixa renda = rendaDoUsuario();
        assertEquals(OnboardingService.CATEGORIA_RENDA, renda.getCategoria().getNome());
        assertTrue(categoriaRepository.findByUsuarioIdAndNomeIgnoreCase(usuario.getId(), "Alimentação").isPresent());
    }

    private ContaFixa rendaDoUsuario() {
        return contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId()).stream()
                .filter(c -> c.getNome().equalsIgnoreCase("Salário"))
                .findFirst()
                .orElseThrow();
    }

    private OnboardingFinalizarRequest request() {
        return new OnboardingFinalizarRequest(
                new OnboardingFinalizarRequest.CarteiraInicial(
                        "Principal", TipoCarteira.CONTA_BANCARIA, new BigDecimal("1000.00"), "Nubank"),
                new OnboardingFinalizarRequest.ContaInicial(
                        "Cartão Roxo", TipoConta.CREDITO, new BigDecimal("2000.00"), 5, 12, "#7C3AED", "Nubank"),
                List.of(new OnboardingFinalizarRequest.CategoriaInicial("Alimentação", "#EF4444", "🍔", null)),
                new OnboardingFinalizarRequest.RendaInicial("Salário", new BigDecimal("3500.00"), 5),
                new OnboardingFinalizarRequest.MetaInicial(
                        "Reserva", new BigDecimal("5000.00"), new BigDecimal("500.00"), null, "#22C55E", "🎯", null)
        );
    }
}
