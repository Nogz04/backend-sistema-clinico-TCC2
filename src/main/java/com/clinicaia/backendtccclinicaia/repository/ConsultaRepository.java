package com.clinicaia.backendtccclinicaia.repository;

import com.clinicaia.backendtccclinicaia.model.Consulta;
import com.clinicaia.backendtccclinicaia.model.StatusConsulta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Acesso persistente às consultas e ao histórico clínico. */
public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    /**
     * Carrega uma consulta e todas as relações necessárias à resposta REST.
     *
     * @param id identificador da consulta
     * @return consulta completa ou vazio
     */
    @Override
    @EntityGraph(attributePaths = {
            "medico", "prontuario", "prontuario.paciente",
            "preDiagnosticosSugeridos", "examesSugeridos", "prescricoesSugeridas"
    })
    Optional<Consulta> findById(Long id);

    /**
     * Obtém o histórico de um paciente filtrado por estado, do mais recente ao mais antigo.
     *
     * @param pacienteId identificador do paciente
     * @param status estado requerido, normalmente {@code FINALIZADA}
     * @return consultas com relações e sugestões carregadas
     */
    @EntityGraph(attributePaths = {
            "medico", "prontuario", "prontuario.paciente",
            "preDiagnosticosSugeridos", "examesSugeridos", "prescricoesSugeridas"
    })
    List<Consulta> findByProntuarioPacienteIdAndStatusOrderByDataHoraConsultaDesc(
            Long pacienteId, StatusConsulta status
    );

    /**
     * Lista os atendimentos de um médico em determinado estado; com {@code RASCUNHO}, são os rascunhos dele.
     *
     * @param medicoId identificador do médico autor
     * @param status estado requerido
     * @return consultas do médico, do mais recente ao mais antigo
     */
    @EntityGraph(attributePaths = {
            "medico", "prontuario", "prontuario.paciente",
            "preDiagnosticosSugeridos", "examesSugeridos", "prescricoesSugeridas"
    })
    List<Consulta> findByMedicoIdAndStatusOrderByDataHoraConsultaDesc(Long medicoId, StatusConsulta status);

    /**
     * Conta consultas de um prontuário em determinado estado.
     *
     * @param prontuarioId identificador do prontuário
     * @param status estado contado
     * @return quantidade de consultas correspondentes
     */
    long countByProntuarioIdAndStatus(Long prontuarioId, StatusConsulta status);
}
