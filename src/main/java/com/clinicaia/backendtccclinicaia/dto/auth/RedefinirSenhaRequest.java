package com.clinicaia.backendtccclinicaia.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Token recebido por e-mail e nova senha escolhida pelo usuário.
 *
 * @param token código de uso único enviado por e-mail
 * @param novaSenha senha escolhida; o limite de 72 caracteres respeita o que o BCrypt processa
 */
public record RedefinirSenhaRequest(
        @NotBlank(message = "Token obrigatório")
        String token,

        @NotBlank(message = "Informe a nova senha")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        String novaSenha
) {}
