package com.gestor.financeiro.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.LedgerBackfillService;
import com.gestor.financeiro.service.ParcelamentoRoundingBackfillService;
import com.gestor.financeiro.service.ReconciliacaoSistemaResultado;
import com.gestor.financeiro.service.ReconciliacaoSistemaService;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Clock;
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
    private final ReconciliacaoSistemaService reconciliacaoSistemaService;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String job = required(args, "job");
        Path report = Path.of(required(args, "report")).toAbsolutePath().normalize();
        boolean apply = args.containsOption("apply");
        if (!List.of("ledger-orphans", "rounding-residue", "card-schedule", "global-reconciliation").contains(job)) {
            throw new IllegalArgumentException("Job inválido: " + job);
        }
        rejectRepositoryPath(report);
        if ("global-reconciliation".equals(job)) {
            if (apply) throw new IllegalArgumentException("global-reconciliation é estritamente read-only e rejeita --apply");
            writeGlobalReport(report, reconciliacaoSistemaService.executar());
            return;
        }

        TransactionTemplate perUser = new TransactionTemplate(transactionManager);
        perUser.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<Map<String, Object>> results = new ArrayList<>();
        long cursor = 0;
        while (true) {
            List<Long> ids = usuarioRepository.findIdsAfter(cursor, org.springframework.data.domain.PageRequest.of(0, 200));
            if (ids.isEmpty()) break;
            for (Long usuarioId : ids) {
                Object result = perUser.execute(status -> execute(job, usuarioId, apply));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("usuarioId", usuarioId);
                item.put("resultado", result);
                results.add(item);
            }
            cursor = ids.get(ids.size() - 1);
            if (ids.size() < 200) break;
        }

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("job", job);
        artifact.put("dryRun", !apply);
        artifact.put("generatedAt", clock.instant());
        artifact.put("usuariosProcessados", results.size());
        artifact.put("resultados", results);
        writeRestricted(report, artifact);
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
                JOIN contas c ON c.id = t.conta_id
                LEFT JOIN parcelas p ON p.transacao_id = t.id
                LEFT JOIN fatura_lancamentos fl ON fl.transacao_id = t.id AND fl.tipo = 'COMPRA'
                WHERE t.usuario_id = ? AND t.tipo = 'SAIDA'
                """, usuarioId);
        Number missing = (Number) result.get("sem_lancamento_canonico");
        Long divergences = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM transacoes t
                JOIN contas c ON c.id = t.conta_id
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

    private void writeGlobalReport(Path report, ReconciliacaoSistemaResultado result) throws Exception {
        List<Map<String, Object>> divergenciasPorUsuario = result.resultados().stream()
                .filter(item -> item.relatorio() != null || item.erroTecnico())
                .map(item -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("usuarioId", item.usuarioId());
                    value.put("erroTecnico", item.erroTecnico());
                    value.put("divergencias", item.relatorio() == null ? List.of() : item.relatorio().detalhes());
                    return value;
                }).toList();
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("schemaVersion", 1);
        artifact.put("timestamp", result.executadoEm());
        artifact.put("totais", Map.of(
                "usuarios", result.usuarios(), "verificacoes", result.verificacoes(),
                "divergencias", result.divergencias(), "erros", result.erros()));
        artifact.put("divergenciasPorUsuario", divergenciasPorUsuario);
        writeRestricted(report, artifact);
    }

    private void rejectRepositoryPath(Path report) {
        try {
            Path repository = repositoryRoot().toRealPath();
            if (report.startsWith(repository)
                    || Files.exists(report) && report.toRealPath().startsWith(repository)) {
                throw new IllegalArgumentException("Relatórios financeiros devem ser gravados fora do repositório");
            }
            Path existing = report.getParent();
            while (existing != null && !Files.exists(existing)) existing = existing.getParent();
            if (existing != null && existing.toRealPath().startsWith(repository)) {
                throw new IllegalArgumentException("Relatórios financeiros devem ser gravados fora do repositório");
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Não foi possível validar o caminho do relatório", ex);
        }
    }

    private Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve(".git"))) return candidate;
        }
        return current;
    }

    private void writeRestricted(Path report, Object artifact) throws Exception {
        if (report.getParent() != null) Files.createDirectories(report.getParent());
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(artifact);
        Files.write(report, json);
        setRestricted(report);
        String checksum = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        Path checksumPath = report.resolveSibling(report.getFileName() + ".sha256");
        Files.writeString(checksumPath, checksum + "  " + report.getFileName() + System.lineSeparator(),
                StandardCharsets.US_ASCII);
        setRestricted(checksumPath);
    }

    private void setRestricted(Path path) throws Exception {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            if (!path.toFile().setReadable(false, false)
                    || !path.toFile().setReadable(true, true)
                    || !path.toFile().setWritable(false, false)
                    || !path.toFile().setWritable(true, true)) {
                throw new IllegalStateException("Não foi possível restringir permissões do relatório");
            }
        }
    }
}
