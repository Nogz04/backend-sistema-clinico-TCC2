package com.clinicaia.backendtccclinicaia.dto.paciente;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Dados de um paciente devolvidos ao cliente.
 *
 * @param id identificador do paciente
 * @param nomeCompleto nome completo
 * @param cpf CPF somente com dígitos
 * @param dataNascimento data de nascimento
 * @param planoSaude convênio ou plano de saúde
 * @param telefone telefone de contato
 * @param email e-mail de contato
 * @param sexo sexo informado
 * @param nomeMae nome da mãe
 * @param naturalidadeMunicipio município de nascimento
 * @param naturalidadeEstado UF de nascimento
 * @param logradouro rua, avenida ou similar
 * @param numero número do endereço
 * @param complemento complemento do endereço
 * @param bairro bairro
 * @param municipio município do endereço
 * @param estado UF do endereço
 * @param cep CEP
 * @param dataCriacao momento do cadastro
 * @param prontuarioId identificador do prontuário único
 * @param medicosResponsaveis médicos que acompanham o paciente
 */
public record PacienteResponse(
        Long id,
        String nomeCompleto,
        String cpf,
        LocalDate dataNascimento,
        String planoSaude,
        String telefone,
        String email,
        String sexo,
        String nomeMae,
        String naturalidadeMunicipio,
        String naturalidadeEstado,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String municipio,
        String estado,
        String cep,
        LocalDateTime dataCriacao,
        Long prontuarioId,
        Set<MedicoResumo> medicosResponsaveis
) {
    /**
     * Identificação resumida de um médico responsável.
     *
     * @param id identificador do médico
     * @param nomeCompleto nome completo
     * @param crm registro profissional
     */
    public record MedicoResumo(Long id, String nomeCompleto, String crm) {}
}
