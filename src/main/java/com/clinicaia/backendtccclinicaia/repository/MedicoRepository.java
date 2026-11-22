package com.clinicaia.backendtccclinicaia.repository;

import com.clinicaia.backendtccclinicaia.model.Medico;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Persistência de médicos. As consultas carregam as especialidades junto, para evitar consultas adicionais.
 */
public interface MedicoRepository extends JpaRepository<Medico, Long> {

    /**
     * Busca o médico pelo e-mail, que é o login.
     *
     * @param email e-mail do médico, sem diferenciar maiúsculas de minúsculas
     * @return médico encontrado, ou vazio
     */
    @EntityGraph(attributePaths = "especialidades")
    Optional<Medico> findByEmailIgnoreCase(String email);

    /**
     * Verifica a unicidade do CPF antes de cadastrar.
     *
     * @param cpf CPF somente com dígitos
     * @return {@code true} se já houver médico com o CPF
     */
    boolean existsByCpf(String cpf);

    /**
     * Verifica a unicidade do CRM antes de cadastrar.
     *
     * @param crm registro no conselho, sem diferenciar maiúsculas de minúsculas
     * @return {@code true} se já houver médico com o CRM
     */
    boolean existsByCrmIgnoreCase(String crm);

    /**
     * Lista os médicos de uma clínica.
     *
     * @param clinicaId identificador da clínica
     * @return médicos da clínica em ordem alfabética
     */
    @EntityGraph(attributePaths = "especialidades")
    List<Medico> findByClinicaIdOrderByNomeCompletoAsc(Long clinicaId);

    /**
     * Busca um médico pelo identificador, sem restringir a clínica; use {@link #findByIdAndClinicaId} para isso.
     *
     * @param id identificador do médico
     * @return médico com as especialidades carregadas, ou vazio
     */
    @Override
    @EntityGraph(attributePaths = "especialidades")
    Optional<Medico> findById(Long id);

    /**
     * Busca um médico restrito a uma clínica, o que impede o acesso a médicos de outras clínicas.
     *
     * @param id identificador do médico
     * @param clinicaId identificador da clínica
     * @return médico encontrado na clínica, ou vazio
     */
    @EntityGraph(attributePaths = "especialidades")
    Optional<Medico> findByIdAndClinicaId(Long id, Long clinicaId);
}
