package com.clinicaia.backendtccclinicaia.repository;

import com.clinicaia.backendtccclinicaia.model.Prontuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistência de prontuários.
 */
public interface ProntuarioRepository extends JpaRepository<Prontuario, Long> {

    /**
     * Busca o prontuário de um paciente.
     *
     * @param pacienteId identificador do paciente
     * @return prontuário único do paciente, ou vazio
     */
    @EntityGraph(attributePaths = "paciente")
    Optional<Prontuario> findByPacienteId(Long pacienteId);
}
