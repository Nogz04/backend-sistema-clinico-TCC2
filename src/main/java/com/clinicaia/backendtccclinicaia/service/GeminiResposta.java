package com.clinicaia.backendtccclinicaia.service;

/**
 * Resultado bruto de uma chamada bem-sucedida à Gemini.
 *
 * @param texto JSON textual produzido pelo modelo
 * @param modelo modelo que efetivamente respondeu (pode ser o reserva)
 * @param tentativas quantidade de chamadas HTTP necessárias até a resposta
 * @param tokensEntrada tokens do prompt informados pelo provedor
 * @param tokensSaida tokens da resposta visível informados pelo provedor
 * @param tokensRaciocinio tokens de raciocínio interno informados pelo provedor
 * @param tokensTotal total contabilizado pelo provedor
 * @param duracaoMs tempo total, incluindo esperas entre tentativas
 */
public record GeminiResposta(
        String texto,
        String modelo,
        int tentativas,
        long tokensEntrada,
        long tokensSaida,
        long tokensRaciocinio,
        long tokensTotal,
        long duracaoMs
) {
}
