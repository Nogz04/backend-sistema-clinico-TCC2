package com.clinicaia.backendtccclinicaia.dto.auth;

/**
 * Dados mínimos exibidos na página pública de definição de senha.
 *
 * @param valido {@code false} se o convite não existe, já foi usado ou expirou
 * @param nomeCompleto nome do médico convidado
 * @param email e-mail do médico convidado
 * @param clinica nome da clínica que o convidou
 */
public record ConviteStatusResponse(boolean valido, String nomeCompleto, String email, String clinica) {}
