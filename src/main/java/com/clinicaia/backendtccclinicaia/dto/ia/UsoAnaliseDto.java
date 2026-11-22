package com.clinicaia.backendtccclinicaia.dto.ia;

/**
 * Consumo de uma solicitação de análise, devolvido junto do resultado para a interface.
 *
 * @param modelo modelo que respondeu, ou que respondeu originalmente quando reaproveitada
 * @param tokensEntrada tokens do prompt
 * @param tokensSaida tokens da resposta visível
 * @param tokensRaciocinio tokens de raciocínio interno
 * @param tokensTotal total cobrado
 * @param tentativas chamadas HTTP necessárias (0 quando reaproveitada)
 * @param reaproveitada {@code true} quando nenhum token foi gasto porque o texto não mudou
 */
public record UsoAnaliseDto(
        String modelo,
        long tokensEntrada,
        long tokensSaida,
        long tokensRaciocinio,
        long tokensTotal,
        int tentativas,
        boolean reaproveitada
) {
}
