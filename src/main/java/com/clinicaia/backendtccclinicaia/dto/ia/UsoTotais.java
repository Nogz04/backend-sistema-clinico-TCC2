package com.clinicaia.backendtccclinicaia.dto.ia;

/**
 * Totais de tokens e de solicitações em uma janela de tempo.
 *
 * @param solicitacoes inclui falhas e análises reaproveitadas
 * @param reaproveitadas solicitações atendidas sem gasto de tokens
 */
public record UsoTotais(
        long tokensEntrada,
        long tokensSaida,
        long tokensRaciocinio,
        long tokensTotal,
        long solicitacoes,
        long falhas,
        long reaproveitadas
) {
}
