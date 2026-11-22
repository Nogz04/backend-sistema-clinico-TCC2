package com.clinicaia.backendtccclinicaia.dto.consulta;

import com.clinicaia.backendtccclinicaia.dto.prontuario.ConsolidacaoProntuarioRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Confirmação médica que transforma um rascunho em registro clínico definitivo.
 *
 * @param soap estrutura SOAP completa e revisada pelo médico
 * @param dadosGeraisConsulta anotações finais do atendimento
 * @param consolidacaoProntuario dados longitudinais confirmados que podem atualizar o prontuário
 */
public record FinalizarConsultaRequest(
        @NotNull @Valid SoapDto soap,
        @Size(max = 50000) String dadosGeraisConsulta,
        @Valid ConsolidacaoProntuarioRequest consolidacaoProntuario
) {
}
