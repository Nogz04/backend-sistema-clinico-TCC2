package com.clinicaia.backendtccclinicaia.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Token de convite e senha escolhida pelo próprio médico.
 *
 * @param token código do convite recebido por e-mail
 * @param novaSenha senha escolhida; o limite de 72 caracteres respeita o que o BCrypt processa
 */
public record DefinirSenhaRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72) String novaSenha
) {}
