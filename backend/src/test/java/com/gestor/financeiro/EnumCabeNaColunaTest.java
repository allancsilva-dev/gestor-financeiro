package com.gestor.financeiro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda contra a classe de bug da V43: um enum ganha um valor novo e mais longo
 * do que o `length` declarado na coluna. O insert só falha em Postgres — os
 * testes usam H2 com ddl-auto create-drop, que gera a coluna a partir do
 * mapeamento e nunca reproduz o limite.
 *
 * Foi assim que SALDO_DEVEDOR_ANTERIOR (22 caracteres) entrou na V25 numa coluna
 * VARCHAR(20) criada na V18 e derrubou os GETs de fatura com 500.
 */
class EnumCabeNaColunaTest {

    @Test
    void todoEnumPersistidoComoStringCabeNoLengthDaColuna() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<String> violacoes = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.gestor.financeiro.model")) {
            Class<?> entidade = Class.forName(bd.getBeanClassName());
            for (Field campo : entidade.getDeclaredFields()) {
                Enumerated enumerado = campo.getAnnotation(Enumerated.class);
                Column coluna = campo.getAnnotation(Column.class);
                if (enumerado == null || enumerado.value() != EnumType.STRING) continue;
                if (coluna == null || !campo.getType().isEnum()) continue;

                int limite = coluna.length();
                for (Object valor : campo.getType().getEnumConstants()) {
                    String nome = ((Enum<?>) valor).name();
                    if (nome.length() > limite) {
                        violacoes.add(entidade.getSimpleName() + "." + campo.getName()
                                + ": '" + nome + "' tem " + nome.length()
                                + " caracteres e a coluna aceita " + limite);
                    }
                }
            }
        }

        assertTrue(violacoes.isEmpty(),
                "Enum não cabe na coluna (o insert falha em Postgres, não em H2):\n"
                        + String.join("\n", violacoes));
    }
}
