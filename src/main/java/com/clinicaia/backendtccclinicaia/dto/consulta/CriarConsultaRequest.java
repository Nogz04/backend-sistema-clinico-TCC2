package com.clinicaia.backendtccclinicaia.dto.consulta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados necessários para abrir um atendimento em rascunho.
 *
 * @param pacienteId paciente pertencente à clínica do médico autenticado
 * @param transcricaoOriginal texto digitado ou transcrito no navegador
 * @param dadosGeraisConsulta anotações clínicas complementares
 * @param soap estrutura SOAP opcional e ainda revisável
 */
public record CriarConsultaRequest(
        @NotNull Long pacienteId,
        @Size(max = 100000) String transcricaoOriginal,
        @Size(max = 50000) String dadosGeraisConsulta,
        @Valid SoapDto soap
) {
}
