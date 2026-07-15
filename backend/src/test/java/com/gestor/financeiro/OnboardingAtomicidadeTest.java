package com.gestor.financeiro;

import com.gestor.financeiro.dto.OnboardingFinalizarRequest;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.OnboardingService;
import com.gestor.financeiro.service.ProjecaoService;
import com.gestor.financeiro.service.UsuarioExclusaoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Atomicidade e idempotência do onboarding canônico (ADR-0002 / PROB-0078).
 * Sem @Transactional de teste: o rollback observado é o da transação real do service.
 */
@SpringBootTest
@ActiveProfiles("test")
class OnboardingAtomicidadeTest {
    @Autowired OnboardingService onboardingService;
    @Autowired UsuarioExclusaoService usuarioExclusaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired ContaRepository contaRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired ContaFixaRepository contaFixaRepository;
    @Autowired MetaRepository metaRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProjecaoService projecaoService;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Atomico", "onboarding-atomico@teste.com", passwordEncoder.encode("123456")));
        autenticar();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (usuarioRepository.existsById(usuario.getId())) {
            usuarioExclusaoService.excluirConta(usuario.getId());
        }
    }

    private void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    @Test
    void falhaNoMeioNaoDeixaDadosParciais() {
        // meta sem valorTotal viola NOT NULL no flush — última etapa da transação
        OnboardingFinalizarRequest comMetaInvalida = request(
                new OnboardingFinalizarRequest.MetaInicial("Quebrada", null, null, null, null, null, null));

        assertThrows(Exception.class, () -> onboardingService.finalizar(comMetaInvalida));

        Long id = usuario.getId();
        assertEquals(0, carteiraRepository.findByUsuarioId(id).size());
        assertEquals(0, contaRepository.findByUsuarioIdAndAtivoTrue(id).size());
        assertEquals(0, categoriaRepository.findByUsuarioId(id).size());
        assertEquals(0, contaFixaRepository.findByUsuarioIdAndAtivoTrue(id).size());
        assertEquals(0, metaRepository.findByUsuarioId(id).size());
        assertFalse(usuarioRepository.findById(id).orElseThrow().isOnboardingCompleto());
    }

    @Test
    void reenvioAposSucessoEhNoOpSemDuplicatas() {
        onboardingService.finalizar(request(null));
        onboardingService.finalizar(request(null));

        Long id = usuario.getId();
        assertEquals(1, carteiraRepository.findByUsuarioId(id).size());
        assertEquals(1, contaFixaRepository.findByUsuarioIdAndAtivoTrue(id).size());
        assertTrue(usuarioRepository.findById(id).orElseThrow().isOnboardingCompleto());
        assertTrue(projecaoService.projetar(id, 2).meses().stream()
                .anyMatch(mes -> mes.totalEntradas().compareTo(BigDecimal.ZERO) > 0),
                "a renda criada no onboarding deve aparecer positivamente na projeção");
    }

    @Test
    void finalizacoesConcorrentesNaoDuplicamDados() throws InterruptedException {
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch prontos = new CountDownLatch(threads);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger falhas = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                autenticar(); // SecurityContext é ThreadLocal
                prontos.countDown();
                try {
                    largada.await();
                    onboardingService.finalizar(request(null));
                    sucessos.incrementAndGet();
                } catch (Exception ignored) {
                    falhas.incrementAndGet();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
        }

        assertTrue(prontos.await(10, TimeUnit.SECONDS));
        largada.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        Long id = usuario.getId();
        assertEquals(threads, sucessos.get());
        assertEquals(0, falhas.get());
        assertEquals(1, carteiraRepository.findByUsuarioId(id).size());
        assertEquals(1, contaRepository.findByUsuarioIdAndAtivoTrue(id).size());
        assertEquals(1, contaFixaRepository.findByUsuarioIdAndAtivoTrue(id).size());
        assertEquals(1, metaRepository.findByUsuarioIdAndAtivaTrue(id).size());
        assertTrue(usuarioRepository.findById(id).orElseThrow().isOnboardingCompleto());
    }

    private OnboardingFinalizarRequest request(OnboardingFinalizarRequest.MetaInicial metaOverride) {
        return new OnboardingFinalizarRequest(
                new OnboardingFinalizarRequest.CarteiraInicial(
                        "Principal", TipoCarteira.CONTA_BANCARIA, new BigDecimal("500.00"), "Nubank"),
                new OnboardingFinalizarRequest.ContaInicial(
                        "Cartão", TipoConta.CREDITO, new BigDecimal("1000.00"), 5, 12, "#7C3AED", "Nubank"),
                List.of(new OnboardingFinalizarRequest.CategoriaInicial("Alimentação", "#EF4444", "🍔", null)),
                new OnboardingFinalizarRequest.RendaInicial("Salário", new BigDecimal("4000.00"), 5),
                metaOverride != null ? metaOverride
                        : new OnboardingFinalizarRequest.MetaInicial(
                                "Reserva", new BigDecimal("3000.00"), new BigDecimal("300.00"), null, null, null, null)
        );
    }
}
