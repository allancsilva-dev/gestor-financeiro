package com.gestor.financeiro;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaModifier;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerContractArchitectureTest {
    @Test
    void controllersNaoExpoemEntidadesJpaNemDentroDeWrappers() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.gestor.financeiro.controller");
        List<String> violations = new ArrayList<>();
        classes.stream()
                .filter(c -> c.isAnnotatedWith(RestController.class))
                .flatMap(c -> c.getMethods().stream())
                .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
                .forEach(method -> collectEntities(method.reflect().getGenericReturnType(), violations,
                        method.getOwner().getSimpleName() + "." + method.getName()));
        assertTrue(violations.isEmpty(), "Controller expõe @Entity: " + violations);
    }

    private void collectEntities(Type type, List<String> violations, String method) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isAnnotationPresent(Entity.class)) violations.add(method + " -> " + clazz.getName());
        } else if (type instanceof ParameterizedType parameterized) {
            collectEntities(parameterized.getRawType(), violations, method);
            for (Type argument : parameterized.getActualTypeArguments()) collectEntities(argument, violations, method);
        } else if (type instanceof GenericArrayType array) {
            collectEntities(array.getGenericComponentType(), violations, method);
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) collectEntities(bound, violations, method);
            for (Type bound : wildcard.getLowerBounds()) collectEntities(bound, violations, method);
        }
    }
}
