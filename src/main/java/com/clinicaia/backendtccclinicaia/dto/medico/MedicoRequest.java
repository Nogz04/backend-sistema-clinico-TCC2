package com.clinicaia.backendtccclinicaia.dto.medico;

import com.clinicaia.backendtccclinicaia.validation.CPF;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

/**
 * Dados para cadastrar um médico na clínica do médico autenticado.
 *
 * @param nomeCompleto nome completo
 * @param email e-mail, que será o login; deve ser único
 * @param crm registro no Conselho Regional de Medicina; deve ser único
 * @param cpf CPF com ou sem máscara; deve ser válido e único
 * @param dataNascimento data de nascimento, anterior a hoje
 * @param especialidades especialidades do médico (opcional)
 */
public record MedicoRequest(
        @NotBlank @Size(max = 150) String nomeCompleto,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 30) String crm,
        @NotBlank @CPF String cpf,
        @NotNull @Past LocalDate dataNascimento,
        Set<@NotBlank @Size(max = 100) String> especialidades
) {}
