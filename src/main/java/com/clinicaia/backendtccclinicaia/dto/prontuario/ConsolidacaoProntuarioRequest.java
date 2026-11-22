package com.clinicaia.backendtccclinicaia.dto.prontuario;

import jakarta.validation.constraints.Size;

/**
 * Campos longitudinais que o médico pode confirmar ao finalizar uma consulta.
 *
 * <p>Também é o formato dos dados que a IA identifica na transcrição. Campo vazio ou nulo significa "não alterar":
 * só o que vem preenchido e diferente do valor vigente altera o prontuário e gera registro de auditoria.</p>
 */
public record ConsolidacaoProntuarioRequest(
        @Size(max = 20000) String alergiasConhecidas,
        @Size(max = 20000) String medicamentosUsoContinuo,
        @Size(max = 20000) String historicoFamiliar,
        @Size(max = 50000) String resumoClinico,
        @Size(max = 20000) String antecedentesPessoais,
        @Size(max = 20000) String condicoesAtivas,
        @Size(max = 20000) String cirurgiasInternacoes,
        @Size(max = 20000) String imunizacoes,
        @Size(max = 20000) String habitosAspectosSociais,
        @Size(max = 20000) String diagnosticosConfirmados,
        @Size(max = 20000) String examesRelevantes,
        @Size(max = 20000) String tratamentosProcedimentos,
        @Size(max = 20000) String planoTerapeuticoAtual,
        @Size(max = 20000) String acompanhamentoPendencias
) {}
