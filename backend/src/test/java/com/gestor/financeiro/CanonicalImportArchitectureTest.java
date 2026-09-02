package com.gestor.financeiro;

import com.gestor.financeiro.service.importacao.FinancialDataConnector;
import com.gestor.financeiro.service.importacao.ImportSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalImportArchitectureTest {
    @Test
    void connectorApiDoesNotExposeBulkCollectionsPathsOrUrls() {
        for (Method method : FinancialDataConnector.class.getDeclaredMethods()) {
            assertFalse(List.class.isAssignableFrom(method.getReturnType()));
            assertFalse(Path.class.isAssignableFrom(method.getReturnType()));
            assertFalse(URL.class.isAssignableFrom(method.getReturnType()));
            assertFalse(Arrays.stream(method.getParameterTypes()).anyMatch(
                    type -> List.class.isAssignableFrom(type)
                            || Path.class.isAssignableFrom(type)
                            || URL.class.isAssignableFrom(type)));
        }
        for (Method method : ImportSource.class.getDeclaredMethods()) {
            assertFalse(Path.class.isAssignableFrom(method.getReturnType()));
            assertFalse(URL.class.isAssignableFrom(method.getReturnType()));
        }
    }

    @Test
    void connectorsDoNotUseBulkReadsDomPathsUrlsOrLedger() throws Exception {
        var root = Paths.get("src/main/java/com/gestor/financeiro/service/importacao");
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("Connector.java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("readAllBytes"), file.toString());
                assertFalse(source.contains("readAllLines"), file.toString());
                assertFalse(source.contains("org.w3c.dom"), file.toString());
                assertFalse(source.contains("TransacaoService"), file.toString());
                assertFalse(source.contains("displayName()"), file.toString());
            }
        }
    }

    /**
     * Conector não fala com a rede.
     *
     * <p>A decisão da Fase 6 (ADR-0019) é que a fonte remota vira {@code ImportSource} antes de
     * chegar aqui: quem busca páginas, renova token e carrega cursor mora em outro pacote. Sem esta
     * guarda, o caminho de menor esforço no próximo conector é abrir a conexão dentro do parse, e a
     * decisão evapora sem ninguém notar.</p>
     */
    @Test
    void connectorsDoNotOpenNetworkConnections() throws Exception {
        List<String> proibidos = List.of("java.net.http", "HttpClient", "RestTemplate", "WebClient",
                "okhttp", "URI.create", "OAuth", "HttpURLConnection", "java.net.Socket");
        for (Path file : connectorSources()) {
            String source = Files.readString(file);
            for (String proibido : proibidos) {
                assertFalse(source.contains(proibido), file + " referencia " + proibido);
            }
        }
    }

    /**
     * Registro canônico não vai para log.
     *
     * <p>Ele carrega descrição, valor e identificador externo da transação — dado bancário do
     * titular. Interpolar o record inteiro numa mensagem é o jeito mais fácil de vazar isso para o
     * arquivo de log e, de lá, para qualquer coletor.</p>
     */
    @Test
    void connectorsDoNotLogTheCanonicalRecord() throws Exception {
        Pattern logDoRecord = Pattern.compile(
                "(log|logger|LOG|LOGGER)\\.(trace|debug|info|warn|error)\\([^)]*\\b(record|canonical)\\b");
        for (Path file : connectorSources()) {
            String source = Files.readString(file);
            assertFalse(logDoRecord.matcher(source).find(), file + " loga o registro canônico");
        }
    }

    /**
     * Um formato, um conector.
     *
     * <p>{@code ImportConnectorRegistry.forFormat} devolve o primeiro que casa. Dois conectores
     * reivindicando o mesmo formato tornariam a escolha dependente da ordem de injeção do Spring —
     * ou seja, silenciosamente instável entre execuções.</p>
     */
    @Test
    void eachConnectorDeclaresItsOwnDistinctFormat() throws Exception {
        Pattern declaracao = Pattern.compile("ImportFormat\\s+format\\(\\)\\s*\\{\\s*return\\s+ImportFormat\\.(\\w+)\\s*;");
        Set<String> vistos = new HashSet<>();
        List<Path> arquivos = connectorSources();
        assertFalse(arquivos.isEmpty(), "nenhum conector encontrado");
        for (Path file : arquivos) {
            Matcher matcher = declaracao.matcher(Files.readString(file));
            assertTrue(matcher.find(), file + " não declara format()");
            String formato = matcher.group(1);
            assertFalse("UNKNOWN".equals(formato), file + " declara formato UNKNOWN");
            assertTrue(vistos.add(formato), "formato " + formato + " reivindicado por mais de um conector");
            assertFalse(matcher.find(), file + " declara format() mais de uma vez");
        }
        assertEquals(arquivos.size(), vistos.size());
    }

    /** Só implementações: a própria SPI também termina em {@code Connector.java} e não tem corpo. */
    private static List<Path> connectorSources() throws Exception {
        var root = Paths.get("src/main/java/com/gestor/financeiro/service/importacao");
        try (var files = Files.list(root)) {
            List<Path> encontrados = new ArrayList<>();
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("Connector.java")).toList()) {
                if (Files.readString(file).contains("implements FinancialDataConnector")) encontrados.add(file);
            }
            return encontrados;
        }
    }
}
