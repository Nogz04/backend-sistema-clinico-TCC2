package com.clinicaia.backendtccclinicaia.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Credenciais de acesso.
 *
 * @param email e-mail do médico
 * @param senha senha em texto, enviada apenas neste momento
 */
public record LoginRequest(
        @NotBlank(message = "E-mail obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "Senha obrigatória")
        String senha
) {}
