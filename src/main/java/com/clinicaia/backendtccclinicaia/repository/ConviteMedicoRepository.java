package com.clinicaia.backendtccclinicaia.repository;

import com.clinicaia.backendtccclinicaia.model.ConviteMedico;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistência de convites de ativação de médicos.
 */
public interface ConviteMedicoRepository extends JpaRepository<ConviteMedico, Long> {

    /**
     * Busca o convite pelo hash do código, carregando o médico e a clínica na mesma consulta.
     *
     * @param tokenHash hash SHA-256 do código recebido por e-mail
     * @return convite encontrado, ou vazio
     */
    @EntityGraph(attributePaths = {"medico", "medico.clinica"})
    Optional<ConviteMedico> findByTokenHash(String tokenHash);
}
