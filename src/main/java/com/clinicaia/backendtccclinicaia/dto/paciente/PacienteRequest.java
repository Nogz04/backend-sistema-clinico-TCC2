package com.clinicaia.backendtccclinicaia.dto.paciente;

import com.clinicaia.backendtccclinicaia.dto.prontuario.ConsolidacaoProntuarioRequest;
import com.clinicaia.backendtccclinicaia.validation.CPF;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Dados para cadastrar um paciente na clínica do médico autenticado.
 *
 * <p>CPF, telefone e CEP são normalizados para somente dígitos, e o e-mail para minúsculas, antes de gravar.</p>
 *
 * @param nomeCompleto nome completo
 * @param cpf CPF com ou sem máscara; deve ser válido e único
 * @param dataNascimento data de nascimento, anterior a hoje
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
 * @param prontuarioInicial alergias, medicamentos e histórico familiar já conhecidos (opcional)
 */
public record PacienteRequest(
        @NotBlank @Size(max = 150) String nomeCompleto,
        @NotBlank @CPF String cpf,
        @NotNull @Past LocalDate dataNascimento,
        @Size(max = 100) String planoSaude,
        @Size(max = 20) String telefone,
        @Email @Size(max = 180) String email,
        @Size(max = 30) String sexo,
        @Size(max = 150) String nomeMae,
        @Size(max = 100) String naturalidadeMunicipio,
        @Size(max = 2) String naturalidadeEstado,
        @Size(max = 150) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 100) String bairro,
        @Size(max = 100) String municipio,
        @Size(max = 2) String estado,
        @Size(max = 8) String cep,
        ConsolidacaoProntuarioRequest prontuarioInicial
) {}
