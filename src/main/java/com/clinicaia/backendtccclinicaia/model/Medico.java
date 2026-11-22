package com.clinicaia.backendtccclinicaia.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Médico, único perfil de acesso ao sistema.
 *
 * <p>Estende {@link Usuario} (herança por tabelas ligadas pelo mesmo identificador) e pertence a exatamente uma
 * clínica. CPF e CRM são únicos.</p>
 */
@Entity
@Table(name = "medicos")
@Getter
@Setter
@NoArgsConstructor
public class Medico extends Usuario {

    /** Registro no Conselho Regional de Medicina, único. */
    @Column(nullable = false, unique = true, length = 30)
    private String crm;

    /** CPF (11 dígitos), único. */
    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    /** Clínica em que o médico atua; define a fronteira de acesso aos dados. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    /**
     * Atalho para exibir a clínica do médico sem expor a entidade inteira.
     *
     * @return nome da clínica em que o médico atua, ou {@code null} se não houver clínica associada
     */
    public String getClinicaAtuacao() {
        return clinica != null ? clinica.getNome() : null;
    }

    /** Especialidades do médico, sem repetição, guardadas em tabela própria. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "medico_especialidades", joinColumns = @JoinColumn(name = "medico_id"))
    @Column(name = "especialidade", nullable = false, length = 100)
    private Set<String> especialidades = new LinkedHashSet<>();

    /** Pacientes acompanhados pelo médico (relação muitos para muitos, tabela medico_paciente). */
    @ManyToMany
    @JoinTable(
            name = "medico_paciente",
            joinColumns = @JoinColumn(name = "medico_id"),
            inverseJoinColumns = @JoinColumn(name = "paciente_id")
    )
    private Set<Paciente> pacientes = new HashSet<>();

    /**
     * Passa a acompanhar o paciente, mantendo os dois lados da relação consistentes.
     *
     * @param paciente paciente a vincular
     */
    public void vincularPaciente(Paciente paciente) {
        pacientes.add(paciente);
        paciente.getMedicosResponsaveis().add(this);
    }
}
