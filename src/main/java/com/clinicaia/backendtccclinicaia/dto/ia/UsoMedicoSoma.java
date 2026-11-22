package com.clinicaia.backendtccclinicaia.dto.ia;

import java.time.LocalDateTime;

/** Consumo agregado de um médico, usado para montar o ranking da clínica. */
public record UsoMedicoSoma(Long medicoId, long tokensTotal, long solicitacoes, LocalDateTime ultimoUso) {
}
