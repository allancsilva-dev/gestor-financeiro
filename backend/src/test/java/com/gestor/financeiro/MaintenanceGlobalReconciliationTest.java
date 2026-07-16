package com.gestor.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.maintenance.MaintenanceCommandRunner;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.LedgerBackfillService;
import com.gestor.financeiro.service.ParcelamentoRoundingBackfillService;
import com.gestor.financeiro.service.ReconciliacaoSistemaResultado;
import com.gestor.financeiro.service.ReconciliacaoSistemaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MaintenanceGlobalReconciliationTest {
    @TempDir Path temp;

    @Test
    void geraJsonRestritoComChecksumSemAlterarBanco() throws Exception {
        Fixtures fixtures = fixtures();
        Path report = temp.resolve("global.json");
        fixtures.runner.run(args("--job=global-reconciliation", "--report=" + report));

        JsonNode json = fixtures.mapper.readTree(report.toFile());
        assertEquals(1, json.get("schemaVersion").asInt());
        assertEquals(2, json.get("totais").get("usuarios").asLong());
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(report)));
        assertTrue(Files.readString(report.resolveSibling("global.json.sha256")).startsWith(expected));
        assertTrue(Files.getPosixFilePermissions(report).stream().allMatch(permission ->
                permission.name().startsWith("OWNER_")));
        verifyNoInteractions(fixtures.users, fixtures.ledger, fixtures.rounding, fixtures.jdbc);
    }

    @Test
    void rejeitaApplyECaminhoDentroDoRepositorio() {
        Fixtures fixtures = fixtures();
        assertThrows(IllegalArgumentException.class, () -> fixtures.runner.run(args(
                "--job=global-reconciliation", "--report=" + temp.resolve("apply.json"), "--apply")));
        Path inside = Path.of(System.getProperty("user.dir"), "financial-report.json");
        assertThrows(IllegalArgumentException.class, () -> fixtures.runner.run(args(
                "--job=global-reconciliation", "--report=" + inside)));
    }

    private Fixtures fixtures() {
        UsuarioRepository users = mock(UsuarioRepository.class);
        LedgerBackfillService ledger = mock(LedgerBackfillService.class);
        ParcelamentoRoundingBackfillService rounding = mock(ParcelamentoRoundingBackfillService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReconciliacaoSistemaService reconciliation = mock(ReconciliacaoSistemaService.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Instant now = Instant.parse("2026-07-16T03:30:00Z");
        when(reconciliation.executar()).thenReturn(new ReconciliacaoSistemaResultado(now, 12, 2,
                8, 0, 0, Map.of(), List.of()));
        MaintenanceCommandRunner runner = new MaintenanceCommandRunner(users, ledger, rounding,
                mock(PlatformTransactionManager.class), mapper, jdbc, reconciliation,
                Clock.fixed(now, ZoneOffset.UTC));
        return new Fixtures(runner, mapper, users, ledger, rounding, jdbc);
    }

    private DefaultApplicationArguments args(String... args) {
        return new DefaultApplicationArguments(args);
    }

    private record Fixtures(MaintenanceCommandRunner runner, ObjectMapper mapper,
                            UsuarioRepository users, LedgerBackfillService ledger,
                            ParcelamentoRoundingBackfillService rounding, JdbcTemplate jdbc) {}
}
