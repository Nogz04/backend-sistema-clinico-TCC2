package com.clinicaia.backendtccclinicaia.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restrição que exige um CPF válido, com ou sem máscara.
 *
 * @see CPFValidator
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CPFValidator.class)
public @interface CPF {

    /**
     * Mensagem devolvida quando o CPF é inválido.
     *
     * @return texto da mensagem
     */
    String message() default "CPF inválido";

    /**
     * Grupos de validação, exigidos pela especificação Bean Validation.
     *
     * @return grupos aos quais a restrição pertence
     */
    Class<?>[] groups() default {};

    /**
     * Metadados da restrição, exigidos pela especificação Bean Validation.
     *
     * @return payloads associados à restrição
     */
    Class<? extends Payload>[] payload() default {};
}
