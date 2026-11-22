package com.clinicaia.backendtccclinicaia.dto.ia;

import java.time.LocalDateTime;

/** Uma solicitação reduzida ao necessário para a série diária do painel. */
public record UsoLinha(LocalDateTime dataHora, long tokensTotal, Long medicoId) {
}
