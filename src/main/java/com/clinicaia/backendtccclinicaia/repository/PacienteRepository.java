package com.clinicaia.backendtccclinicaia.repository;

import com.clinicaia.backendtccclinicaia.model.Paciente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Persistência de pacientes. Toda consulta por clínica filtra pela clínica do paciente, que é a base do isolamento
 * de dados entre clínicas.
 */
public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    /**
     * Verifica a unicidade do CPF antes de cadastrar.
     *
     * @param cpf CPF somente com dígitos
     * @return {@code true} se já houver paciente com o CPF
     */
    boolean existsByCpf(String cpf);

    /**
     * Lista os pacientes de uma clínica.
     *
     * @param clinicaId identificador da clínica
     * @return pacientes da clínica, em ordem alfabética
     */
    @EntityGraph(attributePaths = {"medicosResponsaveis", "prontuario", "clinica"})
    @Query("select distinct p from Paciente p where p.clinica.id = :clinicaId order by p.nomeCompleto")
    List<Paciente> findTodosDaClinica(@Param("clinicaId") Long clinicaId);

    /**
     * Busca um paciente restrito a uma clínica: é o ponto de apoio do isolamento de dados entre clínicas.
     *
     * @param pacienteId identificador do paciente
     * @param clinicaId identificador da clínica
     * @return paciente da clínica, ou vazio
     */
    @EntityGraph(attributePaths = {"medicosResponsaveis", "prontuario", "clinica"})
    @Query("select distinct p from Paciente p where p.id = :pacienteId and p.clinica.id = :clinicaId")
    Optional<Paciente> findDaClinica(@Param("pacienteId") Long pacienteId, @Param("clinicaId") Long clinicaId);

    /**
     * Busca um paciente pelo identificador, sem restringir a clínica; use {@link #findDaClinica} nos fluxos de acesso.
     *
     * @param id identificador do paciente
     * @return paciente com os médicos responsáveis e o prontuário carregados
     */
    @Override
    @EntityGraph(attributePaths = {"medicosResponsaveis", "prontuario"})
    Optional<Paciente> findById(Long id);
}
