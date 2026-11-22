package com.clinicaia.backendtccclinicaia.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida o CPF pelo cálculo dos dígitos verificadores, ignorando a máscara.
 *
 * <p>Rejeita sequências de dígitos repetidos. Valor nulo ou em branco é considerado válido: a obrigatoriedade
 * do campo é verificada por outra restrição.</p>
 */
public class CPFValidator implements ConstraintValidator<CPF, String> {

    /**
     * Verifica o tamanho, descarta sequências repetidas (como 111.111.111-11) e confere os dois dígitos verificadores.
     *
     * @param value CPF com ou sem pontuação
     * @param context contexto da validação
     * @return {@code true} se o valor for vazio ou um CPF válido
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String cpf = value.replaceAll("\\D", "");
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }
        return digito(cpf, 9) == cpf.charAt(9) - '0' && digito(cpf, 10) == cpf.charAt(10) - '0';
    }

    /**
     * Calcula um dígito verificador do CPF.
     *
     * @param cpf somente dígitos
     * @param tamanho quantidade de dígitos que entram no cálculo (9 para o primeiro verificador, 10 para o segundo)
     * @return dígito verificador esperado
     */
    private int digito(String cpf, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i);
        }
        int resto = 11 - soma % 11;
        return resto >= 10 ? 0 : resto;
    }
}
