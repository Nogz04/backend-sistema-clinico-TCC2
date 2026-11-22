package com.clinicaia.backendtccclinicaia.dto.auth;

import java.time.Instant;

/**
 * Resultado da autenticação.
 *
 * @param token JWT a ser enviado no cabeçalho {@code Authorization}
 * @param tipo esquema do token (sempre {@code Bearer})
 * @param expiraEm instante em que o token deixa de ser aceito
 * @param medicoId identificador do médico autenticado
 * @param nomeCompleto nome do médico autenticado
 * @param clinicaAtuacao nome da clínica do médico
 */
public record TokenResponse(
        String token,
        String tipo,
        Instant expiraEm,
        Long medicoId,
        String nomeCompleto,
        String clinicaAtuacao
) {}
