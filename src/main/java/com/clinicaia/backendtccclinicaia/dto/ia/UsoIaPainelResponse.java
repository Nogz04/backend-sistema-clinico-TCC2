package com.clinicaia.backendtccclinicaia.dto.ia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Painel de consumo de tokens do médico autenticado e da clínica a que ele pertence.
 *
 * @param configuracao modelos e limite vigentes
 * @param meu consumo do médico autenticado
 * @param clinica consumo somado de todos os médicos da clínica
 * @param medicos consumo individual de cada médico da clínica
 * @param ultimosDias série diária dos últimos 14 dias
 */
public record UsoIaPainelResponse(
        Configuracao configuracao,
        Periodos meu,
        Periodos clinica,
        List<MedicoUso> medicos,
        List<DiaUso> ultimosDias
) {
    /**
     * Modelos e limite em vigor.
     *
     * @param modeloPrincipal modelo usado na primeira tentativa
     * @param modeloReserva modelo usado quando o principal falha
     * @param limiteDiarioPorMedico tokens por médico por dia; 0 significa sem limite
     */
    public record Configuracao(String modeloPrincipal, String modeloReserva, long limiteDiarioPorMedico) {
    }

    /**
     * Totais de uma mesma pessoa ou grupo em três janelas de tempo.
     *
     * @param hoje desde o início do dia atual
     * @param mes desde o dia 1 do mês corrente
     * @param total todo o período
     */
    public record Periodos(UsoTotais hoje, UsoTotais mes, UsoTotais total) {
    }

    /**
     * Linha do ranking da clínica; médicos sem nenhum uso aparecem com zeros.
     *
     * @param tokensMes tokens consumidos no mês corrente
     * @param tokensTotal tokens consumidos em todo o período
     * @param solicitacoes quantidade de solicitações de análise
     * @param ultimoUso instante da última solicitação, ou {@code null} se nunca usou
     * @param voce {@code true} quando a linha é do próprio médico autenticado
     */
    public record MedicoUso(
            Long id,
            String nome,
            String crm,
            long tokensMes,
            long tokensTotal,
            long solicitacoes,
            LocalDateTime ultimoUso,
            boolean voce
    ) {
    }

    /**
     * Consumo de tokens em um dia.
     *
     * @param data dia da série
     * @param meusTokens tokens do médico autenticado
     * @param tokensClinica tokens de todos os médicos da clínica
     */
    public record DiaUso(LocalDate data, long meusTokens, long tokensClinica) {
    }
}
