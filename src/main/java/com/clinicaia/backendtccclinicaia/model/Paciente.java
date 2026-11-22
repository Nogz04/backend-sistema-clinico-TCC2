package com.clinicaia.backendtccclinicaia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Paciente atendido pela clínica.
 *
 * <p>Pertence a uma clínica, que é a fronteira de acesso aos seus dados, e pode ter vários médicos responsáveis.
 * Possui um único {@code Prontuario}. O paciente não tem acesso ao sistema. CPF, telefone e CEP são guardados
 * somente com dígitos.</p>
 */
@Entity
@Table(name = "pacientes")
@Getter
@Setter
@NoArgsConstructor
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_completo", nullable = false, length = 150)
    private String nomeCompleto;

    /** CPF (11 dígitos), único no sistema. */
    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(name = "plano_saude", length = 100)
    private String planoSaude;

    /** Telefone de contato, somente dígitos (opcional). */
    @Column(length = 20)
    private String telefone;

    /** E-mail de contato (opcional). Não é credencial de acesso. */
    @Column(length = 180)
    private String email;

    @Column(length = 30)
    private String sexo;

    /** Nome da mãe, usado na identificação do paciente. */
    @Column(name = "nome_mae", length = 150)
    private String nomeMae;

    @Column(name = "naturalidade_municipio", length = 100)
    private String naturalidadeMunicipio;

    @Column(name = "naturalidade_estado", length = 2)
    private String naturalidadeEstado;

    @Column(length = 150)
    private String logradouro;

    @Column(length = 20)
    private String numero;

    @Column(length = 100)
    private String complemento;

    @Column(length = 100)
    private String bairro;

    @Column(length = 100)
    private String municipio;

    @Column(length = 2)
    private String estado;

    /** CEP, somente dígitos (8). */
    @Column(length = 8)
    private String cep;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    /** Clínica proprietária do cadastro, obrigatória, e fronteira de autorização dos dados. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    /** Médicos responsáveis pelo paciente (lado inverso da relação muitos para muitos). */
    @ManyToMany(mappedBy = "pacientes")
    private Set<Medico> medicosResponsaveis = new HashSet<>();

    /** Prontuário único do paciente (lado inverso da relação um para um). */
    @OneToOne(mappedBy = "paciente")
    private Prontuario prontuario;
}
