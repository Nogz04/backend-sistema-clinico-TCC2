package com.clinicaia.backendtccclinicaia.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Solicitação pública que recebe resposta neutra, para não revelar quais e-mails têm conta (enumeração de contas).
 *
 * @param email e-mail da conta a recuperar
 */
public record SolicitarRecuperacaoSenhaRequest(
        @NotBlank(message = "Informe o e-mail")
        @Email(message = "Informe um e-mail válido")
        String email
) {}
