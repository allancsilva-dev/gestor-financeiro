package com.gestor.financeiro;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard do timezone de negócio (ADR-0003): serviços financeiros não podem usar
 * {@code LocalDate.now()}, {@code LocalDateTime.now()} ou {@code YearMonth.now()}
 * sem o {@code Clock} injetado — em servidor UTC o dia/mês viram adiantados.
 */
class BusinessClockGuardTest {

    private static final Path SERVICES_DIR = Path.of("src/main/java/com/gestor/financeiro/service");

    private static final Pattern NOW_SEM_CLOCK =
            Pattern.compile("(LocalDate|LocalDateTime|YearMonth)\\.now\\(\\)");

    /**
     * Exceções documentadas — adicionar aqui exige justificativa (ADR-0003):
     * - RefreshTokenService: expiração/limpeza de tokens são instantes técnicos com
     *   duração absoluta (+N dias), não datas de negócio; o fuso não altera o resultado.
     * O pacote model/ e a infraestrutura de auth ficam fora da varredura por decisão do ADR.
     */
    private static final Set<String> ARQUIVOS_EXCECAO = Set.of("RefreshTokenService.java");

    @Test
    void servicosFinanceirosNaoUsamNowSemClock() throws IOException {
        List<String> violacoes = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(SERVICES_DIR)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> !ARQUIVOS_EXCECAO.contains(p.getFileName().toString()))
                 .forEach(p -> {
                     try {
                         List<String> linhas = Files.readAllLines(p);
                         for (int i = 0; i < linhas.size(); i++) {
                             if (NOW_SEM_CLOCK.matcher(linhas.get(i)).find()) {
                                 violacoes.add(p.getFileName() + ":" + (i + 1) + " -> " + linhas.get(i).trim());
                             }
                         }
                     } catch (IOException e) {
                         throw new RuntimeException(e);
                     }
                 });
        }

        assertTrue(violacoes.isEmpty(),
                "now() sem Clock em serviço financeiro (use o bean Clock de TimeConfig, ADR-0003):\n"
                        + String.join("\n", violacoes));
    }
}
