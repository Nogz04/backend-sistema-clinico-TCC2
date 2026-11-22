package com.clinicaia.backendtccclinicaia.security;

import com.clinicaia.backendtccclinicaia.model.Medico;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Emite e valida tokens JWT assinados com HMAC.
 *
 * <p>O token identifica o médico pelo e-mail (subject) e traz o identificador e o perfil como declarações.
 * Expira após o prazo configurado em {@code app.jwt.expiration}. A assinatura garante a integridade, mas não o
 * sigilo: o conteúdo pode ser lido por quem possuir o token.</p>
 */
@Service
public class TokenService {

    private final SecretKey key;
    private final Duration expiration;

    /**
     * Prepara a chave de assinatura a partir do segredo configurado.
     *
     * @param secret segredo HMAC de {@code app.jwt.secret}; precisa ter ao menos 32 bytes
     * @param expiration validade dos tokens emitidos
     */
    public TokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") Duration expiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * Gera o token de acesso de um médico autenticado.
     *
     * @param medico médico que concluiu a autenticação
     * @return token assinado e instante de expiração
     */
    public TokenGerado gerar(Medico medico) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);
        String token = Jwts.builder()
                .subject(medico.getEmail())
                .claim("medicoId", medico.getId())
                .claim("perfil", "MEDICO")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new TokenGerado(token, expiresAt);
    }

    /**
     * Verifica a assinatura e a validade do token.
     *
     * @param token JWT recebido na requisição
     * @return e-mail do médico (subject do token)
     * @throws io.jsonwebtoken.JwtException se a assinatura for inválida ou o token estiver expirado
     */
    public String validarEObterEmail(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }

    /**
     * Token emitido junto do seu instante de expiração.
     *
     * @param token JWT assinado
     * @param expiraEm instante a partir do qual o token deixa de ser aceito
     */
    public record TokenGerado(String token, Instant expiraEm) {}
}
