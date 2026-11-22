package com.clinicaia.backendtccclinicaia.dto.prontuario;

import java.time.LocalDateTime;

/**
 * Item da trilha de alterações do documento longitudinal.
 *
 * @param campo nome legível do campo alterado
 * @param valorAnterior valor antes da alteração; {@code null} na primeira vez em que o campo foi preenchido
 * @param valorNovo valor confirmado pelo médico
 * @param consultaId consulta cuja finalização originou a alteração
 * @param medicoId médico que confirmou a alteração
 * @param nomeMedico nome do médico que confirmou a alteração
 * @param dataAlteracao instante da alteração
 */
public record AtualizacaoProntuarioResponse(
        Long id,
        String campo,
        String valorAnterior,
        String valorNovo,
        Long consultaId,
        Long medicoId,
        String nomeMedico,
        LocalDateTime dataAlteracao
) {}
