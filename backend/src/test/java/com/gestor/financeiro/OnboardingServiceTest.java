package com.gestor.financeiro;

import com.gestor.financeiro.dto.OnboardingFinalizarRequest;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
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
    @Autowired com.gestor.financeiro.repository.CarteiraRepository carteiraRepository;
    @Autowired com.gestor.financeiro.repository.ContaRepository contaRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired jakarta.validation.Validator validator;

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
    void payloadMinimoSoComCarteiraFinalizaSemCartaoNemCategorias() {
        OnboardingFinalizarRequest minimo = new OnboardingFinalizarRequest(
                new OnboardingFinalizarRequest.CarteiraInicial(
                        "Conta Principal", SubtipoContaFinanceira.CORRENTE, BigDecimal.ZERO, null),
                null, null, null, null);
        assertTrue(validator.validate(minimo).isEmpty());

        onboardingService.finalizar(minimo);

        Usuario atualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertTrue(atualizado.isOnboardingCompleto());
        assertEquals(1, carteiraRepository.findByUsuarioId(usuario.getId()).size());
        assertEquals(0, contaRepository.findByUsuarioId(usuario.getId()).size());
        assertEquals(0, categoriaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId()).size());
        assertEquals(0, contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId()).size());
    }

    @Test
    void listaVaziaDeCategoriasEhNoOpEPayloadSemCarteiraEhRejeitado() {
        OnboardingFinalizarRequest comListaVazia = new OnboardingFinalizarRequest(
                new OnboardingFinalizarRequest.CarteiraInicial(
                        "Conta Principal", SubtipoContaFinanceira.CORRENTE, BigDecimal.ZERO, null),
                null, List.of(), null, null);
        assertTrue(validator.validate(comListaVazia).isEmpty());

        onboardingService.finalizar(comListaVazia);
        assertEquals(0, categoriaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId()).size());

        OnboardingFinalizarRequest semCarteira = new OnboardingFinalizarRequest(
                null, null, null, null, null);
        assertEquals(1, validator.validate(semCarteira).size());
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
                        "Principal", SubtipoContaFinanceira.CORRENTE, new BigDecimal("1000.00"), "Nubank"),
                new OnboardingFinalizarRequest.CartaoInicial(
                        "Cartão Roxo", new BigDecimal("2000.00"), 5, 12, "#7C3AED", "Nubank"),
                List.of(new OnboardingFinalizarRequest.CategoriaInicial("Alimentação", "#EF4444", "🍔", null)),
                new OnboardingFinalizarRequest.RendaInicial("Salário", new BigDecimal("3500.00"), 5),
                new OnboardingFinalizarRequest.MetaInicial(
                        "Reserva", new BigDecimal("5000.00"), new BigDecimal("500.00"), null, "#22C55E", "🎯", null)
        );
    }
}
