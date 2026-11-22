package com.clinicaia.backendtccclinicaia.repository;

import com.clinicaia.backendtccclinicaia.model.Clinica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistência de clínicas.
 */
@Repository
public interface ClinicaRepository extends JpaRepository<Clinica, Long> {
}
