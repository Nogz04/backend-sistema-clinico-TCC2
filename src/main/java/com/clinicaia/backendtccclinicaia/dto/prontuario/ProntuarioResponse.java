package com.clinicaia.backendtccclinicaia.dto.prontuario;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Visão longitudinal do prontuário único de um paciente.
 *
 * @param id identificador do prontuário
 * @param pacienteId identificador do paciente
 * @param nomePaciente nome do paciente
 * @param dataCriacao momento de criação do prontuário
 * @param dataUltimaAtualizacao última atualização por uma consulta finalizada
 * @param alergiasConhecidas alergias confirmadas
 * @param medicamentosUsoContinuo medicamentos de uso contínuo
 * @param historicoFamiliar doenças relevantes na família
 * @param resumoClinico visão geral do quadro
 * @param antecedentesPessoais doenças e eventos anteriores
 * @param condicoesAtivas problemas de saúde em curso
 * @param cirurgiasInternacoes cirurgias e internações
 * @param imunizacoes vacinas tomadas
 * @param habitosAspectosSociais hábitos e contexto social
 * @param diagnosticosConfirmados diagnósticos confirmados pelo médico
 * @param examesRelevantes exames importantes para o acompanhamento
 * @param tratamentosProcedimentos tratamentos e procedimentos
 * @param planoTerapeuticoAtual conduta vigente
 * @param acompanhamentoPendencias retornos e pendências
 * @param totalConsultasFinalizadas quantidade de consultas finalizadas do paciente
 * @param historicoAtualizacoes trilha de alterações, da mais recente à mais antiga
 */
public record ProntuarioResponse(
        Long id,
        Long pacienteId,
        String nomePaciente,
        LocalDateTime dataCriacao,
        LocalDateTime dataUltimaAtualizacao,
        String alergiasConhecidas,
        String medicamentosUsoContinuo,
        String historicoFamiliar,
        String resumoClinico,
        String antecedentesPessoais,
        String condicoesAtivas,
        String cirurgiasInternacoes,
        String imunizacoes,
        String habitosAspectosSociais,
        String diagnosticosConfirmados,
        String examesRelevantes,
        String tratamentosProcedimentos,
        String planoTerapeuticoAtual,
        String acompanhamentoPendencias,
        long totalConsultasFinalizadas,
        List<AtualizacaoProntuarioResponse> historicoAtualizacoes
) {}
