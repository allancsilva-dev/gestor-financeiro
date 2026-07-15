package com.gestor.financeiro;

import com.gestor.financeiro.dto.DashboardDtos;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.ContaFixaService;
import com.gestor.financeiro.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Virada de dia/mês/ano entre UTC e São Paulo (ADR-0003 / PROB-0079).
 * Instante fixo: 2027-01-01 01:30 UTC == 2026-12-31 22:30 em São Paulo.
 * A data de negócio ainda é 31/12/2026 — um servidor UTC sem Clock adiantaria mês e ano.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TimezoneViradaTest {

    static final Instant VIRADA_UTC = Instant.parse("2027-01-01T01:30:00Z");
    static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @TestConfiguration
    static class ClockFixoNaVirada {
        @Bean
        @Primary
        Clock clockFixo() {
            return Clock.fixed(VIRADA_UTC, SAO_PAULO);
        }
    }

    @Autowired DashboardService dashboardService;
    @Autowired ContaFixaService contaFixaService;
    @Autowired TransacaoRepository transacaoRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Categoria categoria;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Virada", "virada-tz@teste.com", passwordEncoder.encode("123456")));
        categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Geral"));
    }

    @Test
    void dashboardUsaMesDeSaoPauloENaoOMesUtcAdiantado() {
        salvarTransacao("Gasto de dezembro", "100.00", LocalDate.of(2026, 12, 31));
        salvarTransacao("Gasto de novembro", "50.00", LocalDate.of(2026, 11, 30));
        salvarTransacao("Gasto de janeiro", "999.00", LocalDate.of(2027, 1, 1));

        DashboardDtos.Resumo resumo = dashboardService.obterResumo(usuario.getId());

        // mês de negócio é dezembro/2026 (SP); servidor UTC sem Clock somaria os 999 de janeiro
        assertEquals(0, new BigDecimal("100.00").compareTo(resumo.totalSaidas()));
    }

    @Test
    void proximoVencimentoDeContaFixaCalculaNoDiaDeSaoPaulo() {
        ContaFixa conta = new ContaFixa();
        conta.setNome("Aluguel");
        conta.setValorPlanejado(new BigDecimal("900.00"));
        conta.setDiaVencimento(31);

        ContaFixa criada = contaFixaService.criar(conta, usuario.getId());

        // hoje em SP ainda é 31/12/2026 — vencimento não pode pular para 31/01/2027
        assertEquals(LocalDate.of(2026, 12, 31), criada.getDataProximoVencimento());
    }

    private void salvarTransacao(String descricao, String valor, LocalDate data) {
        Transacao t = TestDataFactory.transacao(usuario, categoria, descricao, new BigDecimal(valor));
        t.setData(data);
        transacaoRepository.save(t);
    }
}
