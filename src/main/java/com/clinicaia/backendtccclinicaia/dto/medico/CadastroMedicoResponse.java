package com.clinicaia.backendtccclinicaia.dto.medico;

import java.time.LocalDateTime;

/**
 * Resultado do cadastro e da emissão do convite seguro do novo profissional.
 *
 * @param medico dados do médico cadastrado
 * @param conviteEnviadoPorEmail {@code false} quando o envio de e-mail está desabilitado ou falhou
 * @param conviteExpiraEm limite de validade do convite
 * @param urlConviteDesenvolvimento link do convite, preenchido apenas no ambiente de desenvolvimento
 */
public record CadastroMedicoResponse(
        MedicoResponse medico,
        boolean conviteEnviadoPorEmail,
        LocalDateTime conviteExpiraEm,
        String urlConviteDesenvolvimento
) {}
