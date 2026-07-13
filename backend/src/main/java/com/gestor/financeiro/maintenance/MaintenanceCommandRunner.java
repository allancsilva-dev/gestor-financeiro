package com.gestor.financeiro.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.LedgerBackfillService;
import com.gestor.financeiro.service.ParcelamentoRoundingBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runner offline. Nunca e registrado como endpoint HTTP. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.maintenance.enabled", havingValue = "true")
public class MaintenanceCommandRunner implements ApplicationRunner {
    private final UsuarioRepository usuarioRepository;
    private final LedgerBackfillService ledgerBackfillService;
    private final ParcelamentoRoundingBackfillService roundingBackfillService;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String job = required(args, "job");
        Path report = Path.of(required(args, "report")).toAbsolutePath().normalize();
        boolean apply = args.containsOption("apply");
        if (!List.of("ledger-orphans", "rounding-residue", "card-schedule").contains(job)) {
            throw new IllegalArgumentException("Job inválido: " + job);
        }

        TransactionTemplate perUser = new TransactionTemplate(transactionManager);
        perUser.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Long usuarioId : usuarioRepository.findAll().stream().map(u -> u.getId()).sorted().toList()) {
            Object result = perUser.execute(status -> execute(job, usuarioId, apply));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("usuarioId", usuarioId);
            item.put("resultado", result);
            results.add(item);
        }

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("job", job);
        artifact.put("dryRun", !apply);
        artifact.put("generatedAt", Instant.now());
        artifact.put("usuariosProcessados", results.size());
        artifact.put("resultados", results);
        if (report.getParent() != null) Files.createDirectories(report.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), artifact);
    }

    private Object execute(String job, Long usuarioId, boolean apply) {
        return switch (job) {
            case "ledger-orphans" -> ledgerBackfillService.reconciliarTransacoesOrfasUsuario(usuarioId, !apply);
            case "rounding-residue" -> apply
                    ? roundingBackfillService.corrigirUsuario(usuarioId)
                    : roundingBackfillService.diagnosticarUsuario(usuarioId);
            case "card-schedule" -> auditCardSchedule(usuarioId, apply);
            default -> throw new IllegalStateException("Job não suportado");
        };
    }

    private Map<String, Object> auditCardSchedule(Long usuarioId, boolean apply) {
        Map<String, Object> result = jdbcTemplate.queryForMap("""
                SELECT
                  COUNT(DISTINCT t.id) FILTER (WHERE p.id IS NOT NULL) AS transacoes_com_parcela_legada,
                  COUNT(DISTINCT t.id) FILTER (WHERE p.id IS NOT NULL AND fl.id IS NULL) AS sem_lancamento_canonico
                FROM transacoes t
                JOIN contas c ON c.id = t.conta_id AND c.tipo = 'CREDITO'
                LEFT JOIN parcelas p ON p.transacao_id = t.id
                LEFT JOIN fatura_lancamentos fl ON fl.transacao_id = t.id AND fl.tipo = 'COMPRA'
                WHERE t.usuario_id = ? AND t.tipo = 'SAIDA'
                """, usuarioId);
        Number missing = (Number) result.get("sem_lancamento_canonico");
        Long divergences = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM transacoes t
                JOIN contas c ON c.id = t.conta_id AND c.tipo = 'CREDITO'
                WHERE t.usuario_id = ? AND t.tipo = 'SAIDA'
                  AND EXISTS (SELECT 1 FROM parcelas p WHERE p.transacao_id = t.id)
                  AND ((SELECT COUNT(*) FROM parcelas p WHERE p.transacao_id = t.id)
                       <> (SELECT COUNT(*) FROM fatura_lancamentos fl WHERE fl.transacao_id = t.id AND fl.tipo = 'COMPRA')
                    OR (SELECT COALESCE(SUM(p.valor), 0) FROM parcelas p WHERE p.transacao_id = t.id)
                       <> (SELECT COALESCE(SUM(fl.valor), 0) FROM fatura_lancamentos fl WHERE fl.transacao_id = t.id AND fl.tipo = 'COMPRA'))
                """, Long.class, usuarioId);
        result.put("divergenciasFinanceiras", divergences);
        if (apply && (missing.longValue() > 0 || divergences != null && divergences > 0)) {
            throw new IllegalStateException("Cronograma de cartão ambíguo; reconstrução automática bloqueada");
        }
        result.put("dryRun", !apply);
        result.put("alteracoes", 0);
        return result;
    }

    private String required(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.get(0).isBlank()) {
            throw new IllegalArgumentException("Informe --" + name + "=<valor>");
        }
        return values.get(0);
    }
}
