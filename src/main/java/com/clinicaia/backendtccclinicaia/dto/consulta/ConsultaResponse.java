package com.clinicaia.backendtccclinicaia.dto.consulta;

import com.clinicaia.backendtccclinicaia.dto.prontuario.ConsolidacaoProntuarioRequest;
import com.clinicaia.backendtccclinicaia.model.StatusConsulta;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Visão completa de uma consulta devolvida à interface.
 *
 * @param id identificador da consulta
 * @param pacienteId identificador do paciente atendido
 * @param nomePaciente nome do paciente para contextualização da tela
 * @param prontuarioId prontuário único ao qual a consulta pertence
 * @param medicoId identificador do médico autor
 * @param nomeMedico nome do médico autor
 * @param dataHoraConsulta instante de abertura do atendimento
 * @param dataFinalizacao instante de confirmação, ou {@code null} para rascunhos
 * @param status estado que controla edição e entrada no histórico
 * @param transcricaoOriginal texto-base revisável da consulta
 * @param soap conteúdo estruturado nos quatro eixos SOAP
 * @param dadosGeraisConsulta anotações clínicas complementares
 * @param analisadaPorIa indica se a Gemini foi usada neste rascunho
 * @param dataAnaliseIa instante da última análise por IA
 * @param preDiagnosticosSugeridos hipóteses preliminares consultivas e ordenadas
 * @param examesSugeridos exames sugeridos para revisão médica
 * @param prescricoesSugeridas possíveis prescrições ou condutas para revisão médica
 * @param alergiasConfirmadas alergias aceitas na finalização
 * @param medicamentosUsoContinuoConfirmados medicamentos aceitos na finalização
 * @param historicoFamiliarConfirmado histórico familiar aceito na finalização
 * @param transcricaoIdentificada diálogo com falantes identificados pela IA, separado do relato original
 * @param modeloIa modelo que gerou a última análise, ou {@code null} sem análise
 * @param dadosProntuarioIdentificados dados do prontuário que a IA identificou na última análise, devolvidos
 *                                     apenas para rascunhos analisados, para que a tela reabra o painel de
 *                                     confirmação já preenchido; {@code null} nos demais casos
 */
public record ConsultaResponse(
        Long id,
        Long pacienteId,
        String nomePaciente,
        Long prontuarioId,
        Long medicoId,
        String nomeMedico,
        LocalDateTime dataHoraConsulta,
        LocalDateTime dataFinalizacao,
        StatusConsulta status,
        String transcricaoOriginal,
        SoapDto soap,
        String dadosGeraisConsulta,
        boolean analisadaPorIa,
        LocalDateTime dataAnaliseIa,
        List<String> preDiagnosticosSugeridos,
        List<String> examesSugeridos,
        List<String> prescricoesSugeridas,
        String alergiasConfirmadas,
        String medicamentosUsoContinuoConfirmados,
        String historicoFamiliarConfirmado,
        String transcricaoIdentificada,
        String modeloIa,
        ConsolidacaoProntuarioRequest dadosProntuarioIdentificados
) {
}
