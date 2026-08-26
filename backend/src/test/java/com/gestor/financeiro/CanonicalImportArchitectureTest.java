package com.gestor.financeiro;

import com.gestor.financeiro.service.importacao.FinancialDataConnector;
import com.gestor.financeiro.service.importacao.ImportSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
