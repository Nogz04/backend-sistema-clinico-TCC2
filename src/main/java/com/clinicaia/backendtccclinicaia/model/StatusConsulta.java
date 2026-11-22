package com.clinicaia.backendtccclinicaia.model;

/**
 * Representa o ciclo de vida de um atendimento clínico.
 *
 * <p>O rascunho permanece editável pelo autor. A finalização ocorre após revisão
 * clínica, torna a consulta imutável e consolida somente dados confirmados.</p>
 */
public enum StatusConsulta {
    /** Conteúdo temporário, editável apenas pelo médico autor. */
    RASCUNHO,
    /** Conteúdo confirmado e imutável, disponível no histórico do paciente. */
    FINALIZADA
}
