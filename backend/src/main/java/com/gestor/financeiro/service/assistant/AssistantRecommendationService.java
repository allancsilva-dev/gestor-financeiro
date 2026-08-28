package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.dto.AssistantDtos.RecommendationResponse;
import com.gestor.financeiro.dto.CategoriaAlerta;
import com.gestor.financeiro.dto.InsightsResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.AssistantRecommendation;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.AssistantRecommendationRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.InsightsService;
import com.gestor.financeiro.service.OperacaoFinanceiraService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recomendações nascem exclusivamente de regras e fatos calculados pelo backend. */
@Service
public class AssistantRecommendationService {
    private static final List<String> SOURCES = List.of("INSIGHTS", "/api/insights");
    private final InsightsService insights;
    private final AssistantRecommendationRepository repository;
    private final UsuarioRepository usuarios;
    private final ObjectMapper objectMapper;
    private final AssistantMutationReplay mutationReplay;
    private final Clock clock;

    public AssistantRecommendationService(InsightsService insights, AssistantRecommendationRepository repository,
                                          UsuarioRepository usuarios, ObjectMapper objectMapper,
                                          AssistantMutationReplay mutationReplay, Clock clock) {
        this.insights = insights; this.repository = repository; this.usuarios = usuarios;
        this.objectMapper = objectMapper; this.mutationReplay = mutationReplay; this.clock = clock;
    }

    @Transactional
    public List<RecommendationResponse> generate(Long usuarioId) {
        Usuario usuario = usuarios.findByIdComLock(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.withDayOfMonth(1);
        LocalDate to = today.withDayOfMonth(today.lengthOfMonth());
        InsightsResponse value = insights.gerarInsights(usuarioId);
        List<Candidate> candidates = rules(value);
        return candidates.stream().limit(5).map(candidate -> persist(usuario, candidate, from, to)).toList();
    }

    @Transactional
    public void feedback(Long usuarioId, Long recommendationId, String value) {
        feedback(usuarioId, recommendationId, value, null);
    }

    @Transactional
    public void feedback(Long usuarioId, Long recommendationId, String value, String idempotencyKey) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!normalized.equals("HELPFUL") && !normalized.equals("NOT_HELPFUL")) {
            throw new BusinessException("Feedback deve ser HELPFUL ou NOT_HELPFUL");
        }
        String requestHash = OperacaoFinanceiraService.hashPayload(
                "RECOMMENDATION_FEEDBACK\n" + recommendationId + "\n" + normalized);
        if (idempotencyKey != null) {
            usuarios.findByIdComLock(usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
            if (mutationReplay.find(usuarioId, idempotencyKey, requestHash, String.class).isPresent()) return;
        }
        AssistantRecommendation recommendation = repository.findByIdAndUsuarioId(recommendationId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Recomendação não encontrada"));
        recommendation.setFeedback(normalized);
        if (idempotencyKey != null) mutationReplay.store(usuarioId, null, "RECOMMENDATION_FEEDBACK",
                idempotencyKey, requestHash, "RECORDED");
        // Feedback é apenas produto/telemetria local; nunca é enviado a um provider ou usado como treino automático.
    }

    private List<Candidate> rules(InsightsResponse value) {
        List<Candidate> result = new ArrayList<>();
        BigDecimal variation = value.getVariacaoPercentual();
        if (value.getPrevisaoSaldoFinal().signum() < 0) {
            result.add(new Candidate("PROJECTED_NEGATIVE_BALANCE",
                    facts("projectedBalance", value.getPrevisaoSaldoFinal()),
                    "Sua projeção para o fim do mês está negativa. Revise os próximos gastos antes de recorrer a crédito.",
                    "OPEN_SCREEN", "/analises"));
        }
        if (variation.compareTo(new BigDecimal("20")) > 0) {
            result.add(new Candidate("SPENDING_ABOVE_AVERAGE",
                    facts("currentSpending", value.getGastoMesAtual(), "monthlyAverage", value.getGastoMedioMensal(), "variationPercent", variation),
                    "Os gastos do mês estão " + percent(variation) + "% acima da média recente. Veja onde há espaço para ajustar.",
                    "OPEN_SCREEN", "/analises"));
        } else if (variation.compareTo(new BigDecimal("-10")) < 0) {
            result.add(new Candidate("SAVINGS_OPPORTUNITY",
                    facts("currentSpending", value.getGastoMesAtual(), "monthlyAverage", value.getGastoMedioMensal(), "variationPercent", variation),
                    "Os gastos estão abaixo da média recente. Você pode avaliar direcionar parte da diferença para uma meta.",
                    "OPEN_SCREEN", "/metas"));
        }
        for (CategoriaAlerta category : value.getCategoriasAlerta()) {
            if (category.isAcimaMedia()) {
                result.add(new Candidate("CATEGORY_ABOVE_NORMAL",
                        facts("category", category.getCategoriaNome(), "currentSpending", category.getGastoAtual(),
                                "previousSpending", category.getGastoMedio(), "variationPercent", category.getVariacaoPercentual()),
                        "A categoria “" + category.getCategoriaNome() + "” está " + percent(category.getVariacaoPercentual())
                                + "% acima do período anterior. Revise os lançamentos dessa categoria.",
                    "OPEN_SCREEN", "/transacoes"));
            }
        }
        if (result.isEmpty()) {
            result.add(new Candidate("FINANCES_STABLE",
                    facts("currentSpending", value.getGastoMesAtual(), "projectedBalance", value.getPrevisaoSaldoFinal()),
                    "Os indicadores do mês estão estáveis. Continue acompanhando os lançamentos regularmente.",
                    "OPEN_SCREEN", "/analises"));
        }
        return result;
    }

    private RecommendationResponse persist(Usuario usuario, Candidate candidate, LocalDate from, LocalDate to) {
        AssistantRecommendation entity = repository
                .findByUsuarioIdAndRuleCodeAndPeriodStartAndPeriodEnd(
                        usuario.getId(), candidate.rule(), from, to)
                .orElseGet(() -> {
                    AssistantRecommendation created = new AssistantRecommendation();
                    created.setUsuario(usuario); created.setRuleCode(candidate.rule());
                    created.setPeriodStart(from); created.setPeriodEnd(to);
                    created.setCreatedAt(LocalDateTime.now(clock));
                    return created;
                });
        entity.setFactsJson(json(candidate.facts())); entity.setSourcesJson(json(SOURCES));
        entity.setActionType(candidate.actionType()); entity.setActionTarget(candidate.actionTarget());
        entity.setExplanation(candidate.explanation());
        entity = repository.save(entity);
        return new RecommendationResponse(entity.getId(), entity.getRuleCode(), entity.getExplanation(), from, to,
                SOURCES, entity.getActionType(), entity.getActionTarget());
    }

    private Map<String, Object> facts(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put((String) values[index], values[index + 1]);
        return result;
    }
    private String percent(BigDecimal value) { return value.abs().setScale(1, RoundingMode.HALF_UP).toPlainString(); }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Falha ao registrar proveniência", exception); }
    }
    private record Candidate(String rule, Map<String, Object> facts, String explanation,
                             String actionType, String actionTarget) { }
}
