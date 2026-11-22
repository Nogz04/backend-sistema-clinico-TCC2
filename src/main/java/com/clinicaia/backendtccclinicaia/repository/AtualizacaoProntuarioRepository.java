package com.clinicaia.backendtccclinicaia.repository;

import com.clinicaia.backendtccclinicaia.model.AtualizacaoProntuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistência da trilha de auditoria do prontuário. */
public interface AtualizacaoProntuarioRepository extends JpaRepository<AtualizacaoProntuario, Long> {

    /**
     * Lista a trilha de auditoria de um prontuário.
     *
     * @param prontuarioId identificador do prontuário
     * @return alterações do prontuário, da mais recente à mais antiga, com a consulta e o médico carregados
     */
    @EntityGraph(attributePaths = {"consulta", "medico"})
    List<AtualizacaoProntuario> findByProntuarioIdOrderByDataAlteracaoDesc(Long prontuarioId);
}
