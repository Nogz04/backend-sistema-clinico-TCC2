package com.clinicaia.backendtccclinicaia.dto.paciente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Dados cadastrais que podem ser alterados. O CPF não é alterável.
 *
 * @param nomeCompleto nome completo
 * @param dataNascimento data de nascimento, anterior a hoje
 * @param planoSaude convênio ou plano de saúde
 * @param telefone telefone com DDD, 10 ou 11 dígitos
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
 * @param cep CEP com 8 dígitos
 */
public record PacienteUpdateRequest(
        @NotBlank @Size(max = 150) String nomeCompleto,
        @NotNull @Past LocalDate dataNascimento,
        @Size(max = 100) String planoSaude,
        @Pattern(
                regexp = "^$|^(?:\\+?55\\s*)?\\(?\\d{2}\\)?\\s*9?\\d{4}[-\\s]?\\d{4}$",
                message = "telefone deve conter DDD e 10 ou 11 dígitos"
        ) String telefone,
        @Email @Size(max = 180) String email,
        @Size(max = 30) String sexo,
        @Size(max = 150) String nomeMae,
        @Size(max = 100) String naturalidadeMunicipio,
        @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "naturalidadeEstado deve ser uma UF") String naturalidadeEstado,
        @Size(max = 150) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 100) String bairro,
        @Size(max = 100) String municipio,
        @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "estado deve ser uma UF") String estado,
        @Pattern(regexp = "^$|^\\d{8}$", message = "CEP deve conter 8 dígitos") String cep
) {}
