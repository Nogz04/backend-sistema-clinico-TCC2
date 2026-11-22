package com.clinicaia.backendtccclinicaia.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Corpo padrão das respostas de erro da API.
 *
 * @param timestamp instante do erro
 * @param status código HTTP
 * @param erro descrição padrão do status HTTP
 * @param mensagem explicação para o cliente
 * @param caminho rota requisitada
 * @param campos mensagens de validação por campo; vazio quando não se aplica
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String erro,
        String mensagem,
        String caminho,
        Map<String, String> campos
) {}
