package com.clinicaia.backendtccclinicaia.dto.medico;

import java.time.LocalDate;
import java.util.Set;

/**
 * Dados de um médico devolvidos ao cliente.
 *
 * @param id identificador do médico
 * @param nomeCompleto nome completo
 * @param email e-mail de login
 * @param crm registro profissional
 * @param cpf CPF somente com dígitos
 * @param dataNascimento data de nascimento
 * @param clinicaAtuacao nome da clínica em que atua
 * @param especialidades especialidades do médico
 */
public record MedicoResponse(
        Long id,
        String nomeCompleto,
        String email,
        String crm,
        String cpf,
        LocalDate dataNascimento,
        String clinicaAtuacao,
        Set<String> especialidades
) {}
