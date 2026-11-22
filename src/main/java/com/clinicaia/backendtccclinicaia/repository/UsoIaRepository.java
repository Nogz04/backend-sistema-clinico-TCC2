package com.clinicaia.backendtccclinicaia.repository;

import com.clinicaia.backendtccclinicaia.dto.ia.UsoLinha;
import com.clinicaia.backendtccclinicaia.dto.ia.UsoMedicoSoma;
import com.clinicaia.backendtccclinicaia.dto.ia.UsoTotais;
import com.clinicaia.backendtccclinicaia.model.UsoIa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consultas de soma sobre o consumo de tokens da IA. Todas aceitam o instante inicial da janela de tempo.
 */
public interface UsoIaRepository extends JpaRepository<UsoIa, Long> {

    /**
     * Soma o consumo de um médico a partir de um instante.
     *
     * @param medicoId identificador do médico
     * @param desde início da janela de tempo
     * @return totais de tokens e de solicitações do médico a partir de {@code desde}
     */
    @Query("""
            select new com.clinicaia.backendtccclinicaia.dto.ia.UsoTotais(
                coalesce(sum(u.tokensEntrada), 0L), coalesce(sum(u.tokensSaida), 0L),
                coalesce(sum(u.tokensRaciocinio), 0L), coalesce(sum(u.tokensTotal), 0L), count(u),
                coalesce(sum(case when u.sucesso = false then 1L else 0L end), 0L),
                coalesce(sum(case when u.reaproveitada = true then 1L else 0L end), 0L))
            from UsoIa u
            where u.medico.id = :medicoId and u.dataHora >= :desde
            """)
    UsoTotais totaisDoMedico(@Param("medicoId") Long medicoId, @Param("desde") LocalDateTime desde);

    /**
     * Soma o consumo de todos os médicos de uma clínica a partir de um instante.
     *
     * @param clinicaId identificador da clínica
     * @param desde início da janela de tempo
     * @return totais de tokens e de solicitações de todos os médicos da clínica a partir de {@code desde}
     */
    @Query("""
            select new com.clinicaia.backendtccclinicaia.dto.ia.UsoTotais(
                coalesce(sum(u.tokensEntrada), 0L), coalesce(sum(u.tokensSaida), 0L),
                coalesce(sum(u.tokensRaciocinio), 0L), coalesce(sum(u.tokensTotal), 0L), count(u),
                coalesce(sum(case when u.sucesso = false then 1L else 0L end), 0L),
                coalesce(sum(case when u.reaproveitada = true then 1L else 0L end), 0L))
            from UsoIa u
            where u.clinica.id = :clinicaId and u.dataHora >= :desde
            """)
    UsoTotais totaisDaClinica(@Param("clinicaId") Long clinicaId, @Param("desde") LocalDateTime desde);

    /**
     * Agrupa o consumo por médico, para o ranking da clínica.
     *
     * @param clinicaId identificador da clínica
     * @param desde início da janela de tempo
     * @return soma de tokens, quantidade de solicitações e último uso de cada médico que usou a IA na clínica
     */
    @Query("""
            select new com.clinicaia.backendtccclinicaia.dto.ia.UsoMedicoSoma(
                u.medico.id, coalesce(sum(u.tokensTotal), 0L), count(u), max(u.dataHora))
            from UsoIa u
            where u.clinica.id = :clinicaId and u.dataHora >= :desde
            group by u.medico.id
            """)
    List<UsoMedicoSoma> totaisPorMedico(@Param("clinicaId") Long clinicaId, @Param("desde") LocalDateTime desde);

    /**
     * Devolve as solicitações individuais, base da série diária do painel.
     *
     * @param clinicaId identificador da clínica
     * @param desde início da janela de tempo
     * @return uma linha por solicitação, reduzida ao necessário para a série diária do painel
     */
    @Query("""
            select new com.clinicaia.backendtccclinicaia.dto.ia.UsoLinha(u.dataHora, u.tokensTotal, u.medico.id)
            from UsoIa u
            where u.clinica.id = :clinicaId and u.dataHora >= :desde
            """)
    List<UsoLinha> linhasDaClinica(@Param("clinicaId") Long clinicaId, @Param("desde") LocalDateTime desde);
}
