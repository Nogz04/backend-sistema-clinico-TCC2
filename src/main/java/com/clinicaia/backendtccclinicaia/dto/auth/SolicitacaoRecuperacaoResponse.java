package com.clinicaia.backendtccclinicaia.dto.auth;

/**
 * Resposta neutra, igual exista ou não a conta.
 *
 * @param mensagem texto genérico exibido ao usuário
 * @param urlDesenvolvimento link de redefinição, preenchido apenas no ambiente de desenvolvimento
 */
public record SolicitacaoRecuperacaoResponse(String mensagem, String urlDesenvolvimento) {}
