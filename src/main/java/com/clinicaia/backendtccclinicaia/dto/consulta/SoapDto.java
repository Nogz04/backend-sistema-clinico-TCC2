package com.clinicaia.backendtccclinicaia.dto.consulta;

import jakarta.validation.constraints.Size;

/**
 * Estrutura SOAP editável da consulta.
 *
 * @param subjetivo relato e percepções informados pelo paciente
 * @param objetivo achados observáveis, medidas e exames
 * @param avaliacao síntese e hipóteses sob avaliação médica
 * @param plano condutas, orientações e acompanhamento
 */
public record SoapDto(
        @Size(max = 50000) String subjetivo,
        @Size(max = 50000) String objetivo,
        @Size(max = 50000) String avaliacao,
        @Size(max = 50000) String plano
) {
}
