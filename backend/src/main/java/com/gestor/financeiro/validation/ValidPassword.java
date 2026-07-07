package com.gestor.financeiro.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {
    String message() default "Senha deve ter no minimo 8 caracteres, com ao menos 1 letra e 1 numero";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
