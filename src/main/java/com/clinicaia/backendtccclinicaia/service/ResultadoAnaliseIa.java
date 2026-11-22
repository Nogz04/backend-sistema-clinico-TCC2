package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.dto.ia.AnaliseIaResponse;

/**
 * Análise já validada junto dos metadados da chamada que a produziu.
 *
 * @param analise conteúdo estruturado e normalizado
 * @param resposta modelo, tentativas e tokens da chamada
 */
public record ResultadoAnaliseIa(AnaliseIaResponse analise, GeminiResposta resposta) {
}
