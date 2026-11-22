package com.clinicaia.backendtccclinicaia.dto.medico;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

/**
 * Dados que o médico pode alterar no próprio perfil.
 *
 * <p>CRM, CPF e clínica não são alteráveis. O campo {@code clinicaAtuacao} é aceito por compatibilidade com o
 * cliente, mas não é utilizado.</p>
 *
 * @param nomeCompleto nome completo
 * @param email e-mail de login; deve continuar único
 * @param dataNascimento data de nascimento, anterior a hoje
 * @param clinicaAtuacao ignorado
 * @param especialidades especialidades do médico
 */
public record MedicoUpdateRequest(
        @NotBlank @Size(max = 150) String nomeCompleto,
        @NotBlank @Email @Size(max = 180) String email,
        @NotNull @Past LocalDate dataNascimento,
        @Size(max = 150) String clinicaAtuacao,
        Set<@NotBlank @Size(max = 100) String> especialidades
) {}
