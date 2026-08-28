package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.dto.CategoriaAlerta;
import com.gestor.financeiro.dto.InsightsResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.AssistantRecommendation;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.AssistantRecommendationRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.InsightsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssistantRecommendationServiceTest {
    private InsightsService insights;
    private AssistantRecommendationRepository repository;
    private UsuarioRepository usuarios;
    private AssistantRecommendationService service;
    private AssistantMutationReplay mutationReplay;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        insights = mock(InsightsService.class);
        repository = mock(AssistantRecommendationRepository.class);
        usuarios = mock(UsuarioRepository.class);
        mutationReplay = mock(AssistantMutationReplay.class);
        service = new AssistantRecommendationService(insights, repository, usuarios, new ObjectMapper(), mutationReplay,
                Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC));
        usuario = new Usuario(); usuario.setId(7L);
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));
        when(usuarios.findByIdComLock(7L)).thenReturn(Optional.of(usuario));
        when(repository.findByUsuarioIdAndRuleCodeAndPeriodStartAndPeriodEnd(
                eq(7L), anyString(), any(), any())).thenReturn(Optional.empty());
        AtomicLong ids = new AtomicLong(1);
        when(repository.save(any())).thenAnswer(call -> {
            AssistantRecommendation value = call.getArgument(0);
            if (value.getId() == null) value.setId(ids.getAndIncrement());
            return value;
        });
    }

    @Test
    void regraDeterministicaPersisteFatosPeriodoFonteEAcaoSemExecutarNada() {
        when(insights.gerarInsights(7L)).thenReturn(InsightsResponse.builder()
                .gastoMesAtual(new BigDecimal("1300")).gastoMedioMensal(new BigDecimal("1000"))
                .variacaoPercentual(new BigDecimal("30")).previsaoSaldoFinal(new BigDecimal("-200"))
                .categoriasAlerta(List.of(CategoriaAlerta.builder().categoriaNome("Mercado")
                        .gastoAtual(new BigDecimal("700")).gastoMedio(new BigDecimal("500"))
                        .variacaoPercentual(new BigDecimal("40")).acimaMedia(true).build())).build());

        var result = service.generate(7L);

        assertThat(result).extracting(r -> r.rule()).containsExactly(
                "PROJECTED_NEGATIVE_BALANCE", "SPENDING_ABOVE_AVERAGE", "CATEGORY_ABOVE_NORMAL");
        assertThat(result).allSatisfy(r -> {
            assertThat(r.periodStart()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(r.sources()).contains("INSIGHTS", "/api/insights");
            assertThat(r.actionType()).isEqualTo("OPEN_SCREEN");
        });
        verify(repository, times(3)).save(any());
    }

    @Test
    void feedbackAceitaSomenteEnumFechadoEAtualizaRegistroLocal() {
        AssistantRecommendation recommendation = new AssistantRecommendation();
        when(repository.findByIdAndUsuarioId(3L, 7L)).thenReturn(Optional.of(recommendation));

        service.feedback(7L, 3L, "helpful");
        assertThat(recommendation.getFeedback()).isEqualTo("HELPFUL");
        assertThatThrownBy(() -> service.feedback(7L, 3L, "treinar-modelo"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void leiturasRepetidasAtualizamMesmaRecomendacaoEPreservamFeedback() {
        when(insights.gerarInsights(7L)).thenReturn(InsightsResponse.builder()
                .gastoMesAtual(new BigDecimal("800")).gastoMedioMensal(new BigDecimal("1000"))
                .variacaoPercentual(new BigDecimal("-20")).previsaoSaldoFinal(new BigDecimal("200"))
                .categoriasAlerta(List.of()).build());
        AssistantRecommendation existing = new AssistantRecommendation();
        existing.setId(44L); existing.setUsuario(usuario); existing.setRuleCode("SAVINGS_OPPORTUNITY");
        existing.setPeriodStart(LocalDate.of(2026, 8, 1)); existing.setPeriodEnd(LocalDate.of(2026, 8, 31));
        existing.setFeedback("HELPFUL"); existing.setCreatedAt(java.time.LocalDateTime.of(2026, 8, 1, 0, 0));
        when(repository.findByUsuarioIdAndRuleCodeAndPeriodStartAndPeriodEnd(
                7L, "SAVINGS_OPPORTUNITY", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .thenReturn(Optional.of(existing));

        var result = service.generate(7L);

        assertThat(result).singleElement().satisfies(value -> assertThat(value.id()).isEqualTo(44L));
        assertThat(existing.getFeedback()).isEqualTo("HELPFUL");
        verify(repository).save(existing);
    }

    @Test
    void feedbackComChavePersisteReplayDepoisDeValidarOwnership() {
        AssistantRecommendation recommendation = new AssistantRecommendation();
        when(usuarios.findByIdComLock(7L)).thenReturn(Optional.of(usuario));
        when(repository.findByIdAndUsuarioId(3L, 7L)).thenReturn(Optional.of(recommendation));
        when(mutationReplay.find(eq(7L), eq("assistant:feedback:key"), anyString(), eq(String.class)))
                .thenReturn(Optional.empty());

        service.feedback(7L, 3L, "helpful", "assistant:feedback:key");

        assertThat(recommendation.getFeedback()).isEqualTo("HELPFUL");
        verify(mutationReplay).store(eq(7L), isNull(), eq("RECOMMENDATION_FEEDBACK"),
                eq("assistant:feedback:key"), anyString(), eq("RECORDED"));
    }
}
