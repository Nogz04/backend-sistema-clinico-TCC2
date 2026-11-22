package com.clinicaia.backendtccclinicaia.dto.consulta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Conteúdo substituível de uma consulta em rascunho.
 *
 * @param transcricaoOriginal texto revisável do atendimento
 * @param dadosGeraisConsulta anotações clínicas complementares
 * @param soap quatro eixos SOAP parcialmente ou totalmente preenchidos
 */
public record AtualizarConsultaRequest(
        @Size(max = 100000) String transcricaoOriginal,
        @Size(max = 50000) String dadosGeraisConsulta,
        @Valid SoapDto soap
) {
}
